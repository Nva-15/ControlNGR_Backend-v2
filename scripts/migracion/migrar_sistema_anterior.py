#!/usr/bin/env python3
"""
Control NGR - Migrar empleados y marcaciones del sistema anterior (control_asistencia, MariaDB).

Uso (en el servidor, desde la carpeta ControlNGR_Backend-v2, con el sistema encendido):

    python3 scripts/migracion/migrar_sistema_anterior.py RESPALDO_ANTERIOR.sql            # simulación
    python3 scripts/migracion/migrar_sistema_anterior.py RESPALDO_ANTERIOR.sql --aplicar  # carga real

Opciones:
    --aplicar            carga los datos (sin esta opción solo genera el informe, no cambia nada)
    --incluir-inactivos  migra también a los usuarios inactivos (quedan inactivos y sin acceso)
    --url URL            dirección del sistema (por defecto https://localhost:HTTPS_PORT del .env)
    --admin USUARIO      usuario administrador (por defecto admin; la contraseña se pide al aplicar)

Qué hace:
  1. Carga el respaldo anterior en una base temporal aislada (contenedor sin red) y lee sus datos.
  2. Empleados: los crea mediante la API del sistema (igual que desde el panel): usuario = DNI,
     contraseña inicial Soporte26$ con cambio obligatorio. Los DNI que ya existen no se modifican.
  3. Marcaciones: empareja cada entrada con su salida y guarda una jornada por persona y día
     (los turnos que cruzan la medianoche quedan en el día de la entrada). No toca días que ya
     tengan marcación en el sistema nuevo.
  4. Deja el informe y el SQL de marcaciones en migracion/migracion_FECHA/ (contienen datos
     personales: esa carpeta no se sube a git).

Antes de --aplicar, genere un respaldo desde el panel (Respaldos -> Crear respaldo ahora).
"""
import argparse
import collections
import datetime as dt
import getpass
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

# ---------------- Reglas acordadas ----------------
# Departamento anterior -> departamento del sistema nuevo
DEPARTAMENTOS = {
    'Equipo NOC': 'NOC',
    'Equipo Help Desk': 'HD',
    'Equipo Soporte': 'Soporte Técnico',
    'Equipo AlmacenTI': 'Tiendas y Almacén de Sistemas',
}
# Rol del personal técnico según su departamento anterior
ROL_POR_DEPARTAMENTO = {
    'Equipo NOC': 'noc',
    'Equipo Help Desk': 'hd',
    'Equipo Soporte': 'tecnico',
    'Equipo AlmacenTI': 'asistente',
}
# Tipos de usuario anteriores con rol propio (el resto toma el rol de su departamento)
ROL_POR_TIPO = {
    'Administrador': 'supervisor',
    'Comité': 'bo',
}
# Departamento para roles que no dependen del departamento anterior
DEPARTAMENTO_POR_ROL = {'bo': 'Back Office'}
CARGO_POR_ROL = {
    'supervisor': 'Supervisor', 'tecnico': 'Técnico de Soporte', 'hd': 'Help Desk', 'noc': 'Operador NOC',
    'bo': 'Back Office', 'asistente': 'Asistente de Almacén',
}
NIVEL_POR_ROL = {'supervisor': 'supervisor', 'tecnico': 'tecnico', 'hd': 'hd', 'noc': 'noc', 'bo': 'bo',
                 'asistente': 'asistente'}
# Códigos que no son personal real (pruebas)
DESCARTAR_CODIGOS = {'202503'}
# Emparejamiento de marcas
JORNADA_MINIMA_MIN = 15        # menos: prueba o doble clic, se descarta
JORNADA_MAXIMA_H = 18          # más: se asume salida no registrada
REPETIDA_SEG = 300             # misma marca repetida en menos de 5 min
# El sistema anterior, si alguien no marcaba su salida, registraba una "Salida" segundos antes de
# su siguiente "Entrada". Esa salida no es real: la jornada anterior queda sin hora de salida.
CIERRE_AUTOMATICO_SEG = 120
OBS_IMPORTADO = 'Importado del sistema anterior'

CONTENEDOR_TEMP = 'controlngr-migracion'
CONTENEDOR_BD = 'controlngr-db'


# ---------------- Utilidades ----------------
def ejecutar(cmd, entrada=None, ok_codes=(0,)):
    r = subprocess.run(cmd, input=entrada, capture_output=True, text=True)
    if r.returncode not in ok_codes:
        raise RuntimeError(f"Falló: {' '.join(cmd[:4])}...\n{r.stderr.strip()}")
    return r.stdout


def consulta_temp(sql):
    salida = ejecutar(['docker', 'exec', CONTENEDOR_TEMP, 'mysql', '-uroot', '-pmigracion', '-N', '-B',
                       '--default-character-set=utf8mb4', 'control_asistencia', '-e', sql])
    return [linea.split('\t') for linea in salida.splitlines() if linea]


def consulta_bd(sql):
    salida = ejecutar(['docker', 'exec', '-i', CONTENEDOR_BD, 'sh', '-c',
                       'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B --default-character-set=utf8mb4 controlngr 2>/dev/null'],
                      entrada=sql)
    return [linea.split('\t') for linea in salida.splitlines() if linea]


def nulo(v):
    return None if v in (None, 'NULL', '') else v


def sql_valor(v):
    if v is None:
        return 'NULL'
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"


def leer_env(clave, defecto=''):
    try:
        for linea in open('.env', encoding='utf-8'):
            linea = linea.strip()
            if linea.startswith(clave + '='):
                return linea.split('=', 1)[1].strip() or defecto
    except FileNotFoundError:
        pass
    return defecto


# ---------------- 1. Leer el sistema anterior ----------------
def cargar_respaldo(ruta):
    print('1/5 Cargando el respaldo anterior en una base temporal aislada...')
    subprocess.run(['docker', 'rm', '-f', CONTENEDOR_TEMP], capture_output=True)
    ejecutar(['docker', 'run', '-d', '--name', CONTENEDOR_TEMP, '--network', 'none',
              '-e', 'MYSQL_ROOT_PASSWORD=migracion', 'mysql:8.0', '--character-set-server=utf8mb4'])
    for _ in range(90):
        r = subprocess.run(['docker', 'exec', CONTENEDOR_TEMP, 'mysql', '-uroot', '-pmigracion', '-e', 'SELECT 1'],
                           capture_output=True)
        if r.returncode == 0:
            break
        time.sleep(2)
    else:
        raise RuntimeError('La base temporal no inició')
    ejecutar(['docker', 'cp', ruta, f'{CONTENEDOR_TEMP}:/tmp/anterior.sql'])
    ejecutar(['docker', 'exec', CONTENEDOR_TEMP, 'sh', '-c',
              'mysql -uroot -pmigracion < /tmp/anterior.sql && rm -f /tmp/anterior.sql'])


def leer_anterior():
    usuarios = []
    for f in consulta_temp(
            "SELECT CONVERT(u.codigo_persona USING utf8mb4), CONVERT(u.nombre USING utf8mb4), "
            "CONVERT(u.apellidos USING utf8mb4), CONVERT(u.email USING utf8mb4), u.fecha_nacimiento, u.estado, "
            "CONVERT(t.nombre USING utf8mb4), CONVERT(d.nombre USING utf8mb4) "
            "FROM usuarios u JOIN tipousuario t ON t.idtipousuario = u.idtipousuario "
            "JOIN departamento d ON d.iddepartamento = u.iddepartamento"):
        codigo, nombre, apellidos, email, nacimiento, estado, tipo, depto = f
        usuarios.append({'codigo': codigo.strip(), 'nombre': ' '.join(f'{nombre} {apellidos}'.split()),
                         'email': (nulo(email) or '').strip().lower() or None, 'nacimiento': nulo(nacimiento),
                         'activo': estado == '1', 'tipo': tipo, 'depto': depto})
    marcas = collections.defaultdict(list)
    for codigo, fecha_hora, tipo, ip in consulta_temp(
            "SELECT CONVERT(codigo_persona USING utf8mb4), DATE_FORMAT(fecha_hora, '%Y-%m-%d %H:%i:%s'), "
            "CONVERT(tipo USING utf8mb4), CONVERT(ip USING utf8mb4) FROM asistencia"):
        marcas[codigo.strip()].append((dt.datetime.strptime(fecha_hora, '%Y-%m-%d %H:%M:%S'), tipo, nulo(ip)))
    return usuarios, marcas


# ---------------- 2. Plan de empleados ----------------
def planificar_empleados(usuarios, incluir_inactivos, existentes, departamentos):
    plan, omitidos = [], []
    for u in usuarios:
        if u['codigo'] in DESCARTAR_CODIGOS:
            omitidos.append((u, 'código de pruebas'))
            continue
        if not u['activo'] and not incluir_inactivos:
            omitidos.append((u, 'inactivo'))
            continue
        rol = ROL_POR_TIPO.get(u['tipo']) or ROL_POR_DEPARTAMENTO.get(u['depto'])
        depto = DEPARTAMENTO_POR_ROL.get(rol) or DEPARTAMENTOS.get(u['depto'])
        if not rol or depto not in departamentos:
            omitidos.append((u, f"sin equivalencia de rol o departamento ({u['tipo']} / {u['depto']})"))
            continue
        plan.append(dict(u, rol=rol, departamento=depto, departamentoId=departamentos[depto],
                         existente=u['codigo'] in existentes, empleadoId=existentes.get(u['codigo'])))
    return plan, omitidos


# ---------------- 3. Marcaciones -> jornadas ----------------
def armar_jornadas(eventos):
    """Empareja entradas y salidas. Devuelve (jornadas, contadores)."""
    c = collections.Counter()
    ev = []
    for t, tipo, ip in sorted(eventos):
        if ev and ev[-1][1] == tipo and (t - ev[-1][0]).total_seconds() < REPETIDA_SEG:
            c['repetidas'] += 1
            if tipo == 'Salida':
                ev[-1] = (t, tipo, ip)          # de dos salidas seguidas vale la última
            continue
        ev.append((t, tipo, ip))
    jornadas, pendiente = [], None
    for i, (t, tipo, ip) in enumerate(ev):
        siguiente = ev[i + 1] if i + 1 < len(ev) else None
        if (tipo == 'Salida' and siguiente and siguiente[1] == 'Entrada'
                and (siguiente[0] - t).total_seconds() < CIERRE_AUTOMATICO_SEG):
            c['cierres_automaticos'] += 1
            if pendiente and (siguiente[0] - pendiente[0]).total_seconds() < JORNADA_MINIMA_MIN * 60:
                continue                                  # la siguiente entrada es la misma: se ignora abajo
            if pendiente:
                jornadas.append({'entrada': pendiente[0], 'salida': None, 'ip_e': pendiente[1], 'ip_s': None,
                                 'nota': 'Salida no registrada en el sistema anterior'})
                c['entradas_sin_salida'] += 1
                pendiente = None
            continue
        if tipo == 'Entrada':
            if pendiente and (t - pendiente[0]).total_seconds() < JORNADA_MINIMA_MIN * 60:
                c['entradas_repetidas'] += 1                  # entrada repetida: vale la primera
                continue
            if pendiente:
                jornadas.append({'entrada': pendiente[0], 'salida': None, 'ip_e': pendiente[1], 'ip_s': None,
                                 'nota': 'Salida no registrada en el sistema anterior'})
                c['entradas_sin_salida'] += 1
            pendiente = (t, ip)
        else:
            if not pendiente:
                c['salidas_sin_entrada'] += 1
                continue
            horas = (t - pendiente[0]).total_seconds() / 3600
            if horas * 60 < JORNADA_MINIMA_MIN:
                c['jornadas_cortas_descartadas'] += 1
            elif horas > JORNADA_MAXIMA_H:
                jornadas.append({'entrada': pendiente[0], 'salida': None, 'ip_e': pendiente[1], 'ip_s': None,
                                 'nota': 'Salida no registrada en el sistema anterior'})
                c['jornadas_largas_sin_salida'] += 1
                c['salidas_sin_entrada'] += 1
            else:
                jornadas.append({'entrada': pendiente[0], 'salida': t, 'ip_e': pendiente[1], 'ip_s': ip, 'nota': None})
                c['jornadas_completas'] += 1
                if t.date() > pendiente[0].date():
                    c['cruzan_medianoche'] += 1
            pendiente = None
    if pendiente:
        jornadas.append({'entrada': pendiente[0], 'salida': None, 'ip_e': pendiente[1], 'ip_s': None,
                         'nota': 'Salida no registrada en el sistema anterior'})
        c['entradas_sin_salida'] += 1
    # Una fila por día (el de la entrada): primera entrada y última salida
    por_dia = collections.OrderedDict()
    for j in jornadas:
        por_dia.setdefault(j['entrada'].date(), []).append(j)
    filas = []
    for fecha, js in por_dia.items():
        primera, ultima = js[0], js[-1]
        notas = [OBS_IMPORTADO]
        if len(js) > 1:
            c['dias_con_varias_jornadas'] += 1
            detalle = ', '.join(f"{j['entrada']:%H:%M}-{j['salida']:%H:%M}" if j['salida'] else f"{j['entrada']:%H:%M}-sin salida"
                                for j in js)
            notas.append(f'{len(js)} jornadas ese día: {detalle}')
        elif primera['nota']:
            notas.append(primera['nota'])
        filas.append({'fecha': fecha, 'entrada': primera['entrada'], 'salida': ultima['salida'],
                      'ip_e': primera['ip_e'], 'ip_s': ultima['ip_s'], 'obs': '. '.join(notas)})
    return filas, c


# ---------------- 4. API del sistema ----------------
class Api:
    def __init__(self, url, ca):
        self.url = url.rstrip('/') + '/api'
        self.ctx = ssl.create_default_context(cafile=ca) if ca and os.path.exists(ca) else ssl.create_default_context()
        self.token = None

    def llamar(self, metodo, ruta, cuerpo=None):
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(self.url + ruta, data=datos, method=metodo)
        req.add_header('Content-Type', 'application/json')
        if self.token:
            req.add_header('Authorization', 'Bearer ' + self.token)
        try:
            with urllib.request.urlopen(req, context=self.ctx, timeout=60) as r:
                texto = r.read().decode()
                return r.status, (json.loads(texto) if texto else {})
        except urllib.error.HTTPError as e:
            texto = e.read().decode()
            try:
                return e.code, json.loads(texto)
            except ValueError:
                return e.code, {'error': texto[:200]}

    def ingresar(self, usuario, clave):
        s, r = self.llamar('POST', '/auth/login', {'username': usuario, 'password': clave})
        if s != 200 or not r.get('token'):
            raise RuntimeError(f"No se pudo ingresar como {usuario}: {r.get('error') or r.get('message') or s}")
        if r.get('debeCambiarPassword') or (r.get('usuario') or {}).get('debeCambiarPassword'):
            raise RuntimeError('El usuario admin debe cambiar su contraseña primero (ingrese una vez a la web).')
        self.token = r['token']


def mensaje(r):
    return str(r.get('error') or r.get('message') or r)[:150]


# ---------------- Programa ----------------
def main():
    ap = argparse.ArgumentParser(description='Migrar empleados y marcaciones del sistema anterior a Control NGR')
    ap.add_argument('respaldo', help='Archivo .sql del sistema anterior (control_asistencia)')
    ap.add_argument('--aplicar', action='store_true', help='Cargar los datos (sin esto solo simula)')
    ap.add_argument('--incluir-inactivos', action='store_true', help='Migrar también a los usuarios inactivos')
    ap.add_argument('--url', help='Dirección del sistema, por defecto https://localhost:HTTPS_PORT')
    ap.add_argument('--admin', default='admin', help='Usuario administrador')
    a = ap.parse_args()

    ruta = os.path.abspath(a.respaldo)
    if not os.path.isfile(ruta):
        sys.exit(f'No existe el archivo {a.respaldo}')
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))

    salida_dir = os.path.join('migracion', 'migracion_' + dt.datetime.now().strftime('%Y%m%d_%H%M%S'))
    os.makedirs(salida_dir, exist_ok=True)
    informe = []

    def linea(t=''):
        print(t)
        informe.append(t)

    try:
        cargar_respaldo(ruta)
        usuarios, marcas = leer_anterior()
    finally:
        subprocess.run(['docker', 'rm', '-f', CONTENEDOR_TEMP], capture_output=True)

    print('2/5 Leyendo el sistema nuevo...')
    departamentos = {n: int(i) for i, n in consulta_bd('SELECT id, nombre FROM departamentos;')}
    existentes = {d: int(i) for i, d in consulta_bd('SELECT id, dni FROM empleados;')}
    plan, omitidos = planificar_empleados(usuarios, a.incluir_inactivos, existentes, departamentos)

    print('3/5 Armando las jornadas...')
    total = collections.Counter()
    jornadas_por_dni = {}
    for p in plan:
        filas, c = armar_jornadas(marcas.get(p['codigo'], []))
        jornadas_por_dni[p['codigo']] = filas
        total.update(c)
    omitidas_marcas = sum(len(marcas.get(u['codigo'], [])) for u, _ in omitidos)
    sin_usuario = sum(len(v) for k, v in marcas.items() if k not in {u['codigo'] for u in usuarios})

    nuevos = [p for p in plan if not p['existente']]
    linea('=' * 72)
    linea(f"Migración del sistema anterior · {'APLICADA' if a.aplicar else 'SIMULACIÓN (no se cambió nada)'}")
    linea(f"Respaldo: {os.path.basename(ruta)}   Fecha: {dt.datetime.now():%d/%m/%Y %H:%M}")
    linea('=' * 72)
    linea(f"Usuarios en el sistema anterior: {len(usuarios)}  (activos {sum(u['activo'] for u in usuarios)})")
    linea(f"A migrar: {len(plan)}   nuevos: {len(nuevos)}   ya existían (no se modifican): {len(plan) - len(nuevos)}")
    linea(f"No migrados: {len(omitidos)}  (sus {omitidas_marcas} marcas tampoco)")
    linea(f"Marcas de códigos sin usuario (se descartan): {sin_usuario}")
    linea()
    linea('Marcaciones de las personas migradas:')
    for k, t in [('jornadas_completas', 'Jornadas completas (entrada y salida)'),
                 ('cruzan_medianoche', '  de ellas, turnos que cruzan la medianoche'),
                 ('entradas_sin_salida', 'Entradas sin salida (se cargan sin hora de salida)'),
                 ('cierres_automaticos', '  "Salidas" automáticas del sistema anterior (no son reales)'),
                 ('jornadas_largas_sin_salida', f'Jornadas de más de {JORNADA_MAXIMA_H} h (salida no registrada)'),
                 ('salidas_sin_entrada', 'Salidas sin entrada (no se cargan)'),
                 ('jornadas_cortas_descartadas', f'Jornadas de menos de {JORNADA_MINIMA_MIN} min (descartadas)'),
                 ('repetidas', 'Marcas repetidas en menos de 5 min (descartadas)'),
                 ('entradas_repetidas', f'Entradas repetidas en menos de {JORNADA_MINIMA_MIN} min (vale la primera)'),
                 ('dias_con_varias_jornadas', 'Días con varias jornadas (se guarda la primera entrada y la última salida)')]:
        linea(f"  {t}: {total[k]}")
    linea(f"  Días a cargar: {sum(len(v) for v in jornadas_por_dni.values())}")
    linea()
    linea('Empleados a migrar:')
    linea(f"  {'DNI':<11}{'Nombre':<38}{'Rol':<11}{'Departamento':<30}{'Días':>5}  Estado")
    for p in sorted(plan, key=lambda x: (x['departamento'], x['nombre'])):
        linea(f"  {p['codigo']:<11}{p['nombre'][:37]:<38}{p['rol']:<11}{p['departamento'][:29]:<30}"
              f"{len(jornadas_por_dni[p['codigo']]):>5}  {'ya existe' if p['existente'] else 'nuevo'}"
              f"{'' if p['activo'] else ' (inactivo)'}")
    if omitidos:
        linea()
        linea('No migrados:')
        for u, motivo in omitidos:
            linea(f"  {u['codigo']:<11}{u['nombre'][:37]:<38}{motivo}")

    if not a.aplicar:
        linea()
        linea('Para cargar los datos, vuelva a ejecutar con --aplicar (antes, cree un respaldo desde el panel).')
        guardar(salida_dir, informe)
        return

    # ---------------- Aplicar ----------------
    url = a.url or f"https://localhost:{leer_env('HTTPS_PORT', '443')}"
    api = Api(url, os.path.join('certs', 'ca.crt'))
    clave = getpass.getpass(f'Contraseña de {a.admin} en {url}: ')
    api.ingresar(a.admin, clave)

    print('4/5 Creando empleados con la API del sistema...')
    errores = []
    for p in nuevos:
        cuerpo = {'dni': p['codigo'], 'nombre': p['nombre'], 'cargo': CARGO_POR_ROL.get(p['rol'], ''),
                  'nivel': NIVEL_POR_ROL.get(p['rol'], 'tecnico'), 'departamentoId': p['departamentoId'],
                  'rol': p['rol'], 'email': p['email'], 'cumpleanos': p['nacimiento'], 'activo': True,
                  'usuarioActivo': True}
        s, r = api.llamar('POST', '/empleados', cuerpo)
        if s not in (200, 201) and p['email'] and 'mail' in mensaje(r).lower():
            cuerpo['email'] = None                       # correo repetido: se crea sin correo
            s, r = api.llamar('POST', '/empleados', cuerpo)
        if s not in (200, 201):
            errores.append(f"{p['codigo']} {p['nombre']}: {mensaje(r)}")
            continue
        if not p['activo']:
            api.llamar('PATCH', f"/empleados/{r.get('id')}/estado", {'activo': False, 'usuarioActivo': False})
    existentes = {d: int(i) for i, d in consulta_bd('SELECT id, dni FROM empleados;')}

    print('5/5 Cargando marcaciones...')
    ids = [existentes[p['codigo']] for p in plan if p['codigo'] in existentes]
    ocupados = set()
    if ids:
        for eid, fecha in consulta_bd(f"SELECT empleado_id, fecha FROM asistencia WHERE empleado_id IN ({','.join(map(str, ids))});"):
            ocupados.add((int(eid), fecha))
    sentencias, cargadas, ya = [], 0, 0
    for p in plan:
        eid = existentes.get(p['codigo'])
        if not eid:
            continue
        for f in jornadas_por_dni[p['codigo']]:
            if (eid, f['fecha'].isoformat()) in ocupados:
                ya += 1
                continue
            sentencias.append('(' + ', '.join(sql_valor(v) for v in [
                eid, f['fecha'].isoformat(), f['entrada'].strftime('%H:%M:%S'),
                f['salida'].strftime('%H:%M:%S') if f['salida'] else None, 'presente', f['obs'], 0,
                f['ip_e'], f['ip_s'], 'importado', 'importado' if f['salida'] else None]) + ')')
            cargadas += 1
    sql = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
    for i in range(0, len(sentencias), 500):
        sql.append('INSERT INTO asistencia (empleado_id, fecha, hora_entrada, hora_salida, estado, observaciones, '
                   'salida_automatica, ip_entrada, ip_salida, metodo_entrada, metodo_salida) VALUES\n'
                   + ',\n'.join(sentencias[i:i + 500]) + ';')
    sql.append('COMMIT;')
    texto_sql = '\n'.join(sql) + '\n'
    with open(os.path.join(salida_dir, 'marcaciones.sql'), 'w', encoding='utf-8') as fh:
        fh.write(texto_sql)
    ejecutar(['docker', 'exec', '-i', CONTENEDOR_BD, 'sh', '-c',
              'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 controlngr'], entrada=texto_sql)

    linea()
    linea('Resultado:')
    linea(f"  Empleados creados: {len(nuevos) - len(errores)} de {len(nuevos)}")
    linea(f"  Días de marcación cargados: {cargadas}   ya existían en el sistema nuevo (no se tocaron): {ya}")
    for e in errores:
        linea('  ERROR ' + e)
    linea('  Los empleados nuevos ingresan con su DNI y la contraseña Soporte26$ (deberán cambiarla).')
    guardar(salida_dir, informe)


def guardar(carpeta, informe):
    ruta = os.path.join(carpeta, 'informe.txt')
    with open(ruta, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(informe) + '\n')
    print(f'\nInforme guardado en {ruta} (contiene datos personales; no lo comparta).')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, KeyboardInterrupt) as e:
        sys.exit(f'\nERROR: {e}')
