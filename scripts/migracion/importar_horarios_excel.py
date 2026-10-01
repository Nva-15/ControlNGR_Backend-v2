#!/usr/bin/env python3
"""
Control NGR - Cargar los horarios de las semanas pasadas desde el Excel de horarios de Soporte.

Uso (en el servidor, desde la carpeta ControlNGR_Backend-v2, con el sistema encendido):

    python3 scripts/migracion/importar_horarios_excel.py HORARIOS.xlsx                     # simulación
    python3 scripts/migracion/importar_horarios_excel.py HORARIOS.xlsx --extra SEMANA.csv  # + semanas sueltas
    python3 scripts/migracion/importar_horarios_excel.py HORARIOS.xlsx --aplicar           # carga real
    python3 scripts/migracion/importar_horarios_excel.py --revertir                        # quita lo cargado

Opciones:
    --aplicar        carga los horarios (sin esta opción solo genera el informe, no cambia nada)
    --extra CSV      semanas que no están en el Excel, separadas por ';' con las columnas:
                     semana;nombre;lunes;martes;miercoles;jueves;viernes;sabado;domingo
                     (semana = fecha del lunes AAAA-MM-DD; las celdas se escriben igual que en el Excel)
    --alias CSV      correcciones de nombres, separadas por ';':  nombre_en_excel;nombre_en_el_sistema
    --bo-desde FECHA desde esta fecha (AAAA-MM-DD) el personal de Back Office tiene 08:30 - 17:30
    --revertir       elimina las semanas cargadas por este script (y solo esas)

Reglas acordadas:
  * Solo se crea horario en los días en que la persona tiene marcación importada del sistema
    anterior: quien no marcó no tiene horario ese día (así el reporte no muestra faltas por los
    huecos del sistema anterior).
  * Se usa el turno del Excel cuando la celda tiene horas. "16:00 - 12:00" se lee 16:00 - 24:00 y
    "15:30 - 12:30" como 15:30 - 00:30. Las etiquetas (SP, LT, CM, BO...) no cambian el cálculo.
  * Si la persona marcó un día que el Excel tenía como Descanso, Vacaciones o RC, o el Excel no la
    tiene esa semana: Back Office desde --bo-desde usa 08:30 - 17:30; el resto (HD, NOC, etc.) usa
    un turno estimado de 8 h que empieza en la media hora más cercana a su entrada real.
  * Personal de Soporte Técnico, HD, NOC y Back Office (Almacén no). Solo empleados activos.
  * Si dos tablas de la misma semana se contradicen, vale el turno más cercano a la entrada real.
  * Las semanas que ya existen en el sistema (creadas a mano) no se tocan.
  * Las semanas se crean publicadas (activo) para que el reporte de asistencia las use. Llevan
    "(importado)" en el nombre y cada día el origen importado_*, para poder revertirlas.
  * Volver a ejecutarlo no duplica: solo agrega los días que falten.

El informe queda en migracion/horarios_FECHA/ (contiene datos personales: no se sube a git).
Antes de --aplicar, genere un respaldo desde el panel (Respaldos -> Crear respaldo ahora).
"""
import argparse
import collections
import csv
import datetime as dt
import difflib
import os
import re
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
import zipfile

# ---------------- Reglas acordadas ----------------
DEPARTAMENTOS_INCLUIDOS = {'soporte tecnico', 'hd', 'noc', 'back office'}
DEPARTAMENTO_BO = 'back office'
HORARIO_BO = ('08:30', '17:30')
HORAS_ESTIMADO = 8
MARCA_SEMANA = ' (importado)'
ORIGEN_EXCEL = 'importado_excel'
ORIGEN_BO = 'importado_fijo_bo'
ORIGEN_ESTIMADO = 'importado_estimado'
DURACION_MIN_H, DURACION_MAX_H = 3, 14   # fuera de este rango la celda se considera un error de tipeo
SIMILITUD_NOMBRE = 0.8                   # para errores de tipeo en nombres (una letra de más o de menos)
DIAS = ['lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado', 'domingo']

CONTENEDOR_BD = 'controlngr-db'


# ---------------- Utilidades ----------------
def ejecutar(cmd, entrada=None):
    r = subprocess.run(cmd, input=entrada, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"Falló: {' '.join(cmd[:4])}...\n{r.stderr.strip()}")
    return r.stdout


def mysql(sql, filas=True):
    opciones = '-N -B ' if filas else ''
    salida = ejecutar(['docker', 'exec', '-i', CONTENEDOR_BD, 'sh', '-c',
                       f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" {opciones}--default-character-set=utf8mb4 '
                       '"${MYSQL_DATABASE:-controlngr}" 2>/dev/null'], entrada=sql)
    return [linea.split('\t') for linea in salida.splitlines() if linea]


def sql_valor(v):
    if v is None:
        return 'NULL'
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace('\\', '\\\\').replace("'", "''") + "'"


def normalizar(texto):
    t = unicodedata.normalize('NFKD', str(texto)).encode('ascii', 'ignore').decode().lower()
    return re.sub(r'\s+', ' ', t).strip()


def minutos(hhmm):
    h, m = hhmm.split(':')[:2]
    return int(h) * 60 + int(m)


def hhmm(total):
    total %= 1440
    return f'{total // 60:02d}:{total % 60:02d}'


# ---------------- Lectura del Excel (sin dependencias externas) ----------------
NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
REL = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id'


def leer_xlsx(ruta):
    """Devuelve [(nombre_hoja, {(fila, columna): texto})] con los valores guardados en el archivo."""
    z = zipfile.ZipFile(ruta)
    compartidos = []
    if 'xl/sharedStrings.xml' in z.namelist():
        for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si', NS):
            partes = si.findall('m:t', NS) + si.findall('m:r/m:t', NS)
            compartidos.append(''.join(p.text or '' for p in partes))
    rutas = {}
    for rel in ET.fromstring(z.read('xl/_rels/workbook.xml.rels')):
        destino = rel.get('Target').lstrip('/')
        rutas[rel.get('Id')] = destino if destino.startswith('xl/') else 'xl/' + destino
    hojas = []
    for hoja in ET.fromstring(z.read('xl/workbook.xml')).find('m:sheets', NS):
        celdas = {}
        for c in ET.fromstring(z.read(rutas[hoja.get(REL)])).iter('{%s}c' % NS['m']):
            ref = re.match(r'([A-Z]+)(\d+)', c.get('r', ''))
            if not ref:
                continue
            col = 0
            for letra in ref.group(1):
                col = col * 26 + ord(letra) - 64
            tipo, v = c.get('t'), c.find('m:v', NS)
            if tipo == 'inlineStr':
                valor = ''.join(t.text or '' for t in c.iter('{%s}t' % NS['m']))
            elif v is None:
                continue
            elif tipo == 's':
                valor = compartidos[int(v.text)]
            else:
                valor = v.text or ''
            if valor.strip():
                celdas[(int(ref.group(2)), col)] = valor
        hojas.append((hoja.get('name'), celdas))
    return hojas


CABECERA = re.compile(r'lunes\s*(\d{1,2})\s*[-/]\s*(\d{1,2})', re.I)


def anio_del_lunes(dia, mes, hoy):
    """El Excel no trae el año: es el año en que esa fecha cae lunes (el más reciente)."""
    candidatos = []
    for anio in range(2022, hoy.year + 2):
        try:
            f = dt.date(anio, mes, dia)
        except ValueError:
            continue
        if f.weekday() == 0 and f <= hoy + dt.timedelta(days=60):
            candidatos.append(f)
    return max(candidatos) if candidatos else None


def tablas_del_excel(hojas, hoy):
    """Recorre cada tabla semanal: cabecera 'Lunes dd-mm', a su izquierda la columna de nombres."""
    filas = []   # (lunes, nombre_excel, [7 celdas], origen)
    for nombre_hoja, celdas in hojas:
        for (fila, col), texto in sorted(celdas.items()):
            m = CABECERA.search(texto)
            if not m or col < 2:
                continue
            lunes = anio_del_lunes(int(m.group(1)), int(m.group(2)), hoy)
            if not lunes:
                continue
            vacias, f = 0, fila + 1
            while vacias < 3 and f <= fila + 80:
                nombre = celdas.get((f, col - 1), '').strip()
                izquierda = celdas.get((f, col - 2), '')
                if 'almuerzo' in normalizar(nombre + ' ' + izquierda) or CABECERA.search(celdas.get((f, col), '')):
                    break
                if not nombre:
                    vacias += 1
                else:
                    vacias = 0
                    filas.append((lunes, nombre, [celdas.get((f, col + k), '') for k in range(7)],
                                  f'hoja "{nombre_hoja}" fila {f}'))
                f += 1
    return filas


def tablas_extra(ruta):
    filas = []
    with open(ruta, encoding='utf-8-sig', newline='') as fh:
        for i, r in enumerate(csv.DictReader(fh, delimiter=';'), start=2):
            lunes = dt.date.fromisoformat(r['semana'].strip())
            if lunes.weekday() != 0:
                raise RuntimeError(f'{ruta} línea {i}: {lunes} no es lunes')
            filas.append((lunes, r['nombre'], [r.get(d) or '' for d in DIAS], f'{os.path.basename(ruta)} línea {i}'))
    return filas


# ---------------- Interpretación de celdas ----------------
HORAS = re.compile(r'(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})')


def interpretar(celda):
    """('turno', entrada, salida) | ('libre', motivo) | ('vacio',) | ('error', texto)"""
    texto = re.sub(r'\s+', ' ', celda).strip()
    if not texto:
        return ('vacio',)
    m = HORAS.search(texto)
    if m:
        ini = int(m.group(1)) * 60 + int(m.group(2))
        fin = int(m.group(3)) * 60 + int(m.group(4))
        if fin // 60 == 12 and ini >= 14 * 60:      # 16:00 - 12:00 -> 24:00 ; 15:30 - 12:30 -> 00:30
            fin -= 12 * 60
        fin %= 1440
        duracion = (fin - ini) % 1440
        if not (DURACION_MIN_H * 60 <= duracion <= DURACION_MAX_H * 60):
            return ('error', texto)
        return ('turno', hhmm(ini), hhmm(fin))
    n = normalizar(texto)
    if 'vaca' in n:
        return ('libre', 'Vacaciones')
    if 'desca' in n or 'feriado' in n or re.search(r'\brc\b', n):
        return ('libre', 'Descanso')
    return ('error', texto)


# ---------------- Nombres ----------------
def tokens(nombre):
    n = normalizar(re.sub(r'\(.*?\)|\(.*', ' ', nombre))
    return re.sub(r'[^a-z ]', ' ', n).split()


def token_coincide(t, del_sistema):
    for s in del_sistema:
        if t == s or (len(t) >= 4 and difflib.SequenceMatcher(None, t, s).ratio() >= SIMILITUD_NOMBRE):
            return True
    return False


def emparejar(nombre_excel, empleados, alias):
    """Empleado del sistema que corresponde al nombre del Excel: todas las palabras del Excel
    deben estar en el nombre del sistema (admite pequeños errores de tipeo)."""
    clave = ' '.join(tokens(nombre_excel))
    if clave in alias:
        objetivo = alias[clave]
        hallados = [e for e in empleados if ' '.join(tokens(e['nombre'])) == objetivo]
        return (hallados[0], 'alias') if len(hallados) == 1 else (None, 'alias sin coincidencia')
    ts = tokens(nombre_excel)
    if len(ts) < 2 or len(nombre_excel) > 60:
        return None, 'no es un nombre completo'
    candidatos = [e for e in empleados if all(token_coincide(t, e['tokens']) for t in ts)]
    if len(candidatos) == 1:
        return candidatos[0], 'automático'
    if candidatos:
        return None, 'ambiguo: ' + ' / '.join(e['nombre'] for e in candidatos)
    return None, 'no está en el sistema (o está inactivo)'


# ---------------- Programa ----------------
def revertir():
    filas = mysql(f"SELECT COUNT(*), COALESCE(SUM((SELECT COUNT(*) FROM horarios_semanales_detalle d "
                  f"WHERE d.horario_semanal_id = s.id)), 0) FROM horarios_semanales s "
                  f"WHERE s.nombre LIKE {sql_valor('%' + MARCA_SEMANA)};")
    semanas, dias = filas[0]
    print(f'Se eliminarán {semanas} semanas importadas ({dias} días de horario). Las semanas creadas a mano no se tocan.')
    if semanas == '0':
        return
    if input('Escriba SI para continuar: ').strip().upper() != 'SI':
        sys.exit('Cancelado.')
    mysql(f"DELETE FROM horarios_semanales WHERE nombre LIKE {sql_valor('%' + MARCA_SEMANA)};", filas=False)
    print('Listo.')


def main():
    ap = argparse.ArgumentParser(description='Cargar horarios de semanas pasadas desde el Excel de Soporte')
    ap.add_argument('excel', nargs='?', help='Archivo .xlsx de horarios')
    ap.add_argument('--extra', action='append', default=[], help='CSV con semanas adicionales (puede repetirse)')
    ap.add_argument('--alias', help='CSV nombre_en_excel;nombre_en_el_sistema')
    ap.add_argument('--bo-desde', default='2026-05-15', help='Fecha desde la que Back Office es 08:30 - 17:30')
    ap.add_argument('--aplicar', action='store_true', help='Cargar los horarios (sin esto solo simula)')
    ap.add_argument('--revertir', action='store_true', help='Eliminar las semanas cargadas por este script')
    a = ap.parse_args()

    rutas_extra = [os.path.abspath(r) for r in a.extra]
    ruta_alias = os.path.abspath(a.alias) if a.alias else None
    ruta_excel = os.path.abspath(a.excel) if a.excel else None
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
    if a.revertir:
        return revertir()
    if not ruta_excel or not os.path.isfile(ruta_excel):
        sys.exit('Indique el archivo .xlsx de horarios')
    bo_desde = dt.date.fromisoformat(a.bo_desde)
    hoy = dt.date.today()

    salida_dir = os.path.join('migracion', 'horarios_' + dt.datetime.now().strftime('%Y%m%d_%H%M%S'))
    os.makedirs(salida_dir, exist_ok=True)
    informe = []

    def linea(t=''):
        print(t)
        informe.append(t)

    print('1/4 Leyendo el Excel...')
    filas = tablas_del_excel(leer_xlsx(ruta_excel), hoy)
    for r in rutas_extra:
        filas += tablas_extra(r)
    alias = {}
    if ruta_alias:
        with open(ruta_alias, encoding='utf-8-sig', newline='') as fh:
            for r in csv.reader(fh, delimiter=';'):
                if len(r) >= 2 and r[0].strip():
                    alias[' '.join(tokens(r[0]))] = ' '.join(tokens(r[1]))

    print('2/4 Leyendo el sistema...')
    empleados = []
    for eid, nombre, depto in mysql(
            "SELECT e.id, e.nombre, COALESCE(d.nombre, '') FROM empleados e "
            "LEFT JOIN departamentos d ON d.id = e.departamento_id WHERE e.activo = 1;"):
        empleados.append({'id': int(eid), 'nombre': nombre, 'depto': normalizar(depto), 'depto_txt': depto,
                          'tokens': tokens(nombre)})
    por_id = {e['id']: e for e in empleados}
    marcas = {}
    for eid, fecha, entrada in mysql(
            "SELECT empleado_id, fecha, hora_entrada FROM asistencia "
            "WHERE metodo_entrada = 'importado' AND hora_entrada IS NOT NULL;"):
        if int(eid) in por_id:
            marcas[(int(eid), dt.date.fromisoformat(fecha))] = entrada[:5]
    semanas_bd = [(int(i), dt.date.fromisoformat(ini), dt.date.fromisoformat(fin), nom)
                  for i, ini, fin, nom in mysql("SELECT id, fecha_inicio, fecha_fin, COALESCE(nombre, '') "
                                                "FROM horarios_semanales;")]
    con_detalle = {(int(e), dt.date.fromisoformat(f)) for e, f in mysql(
        "SELECT empleado_id, fecha FROM horarios_semanales_detalle;")}
    tol = mysql("SELECT valor FROM parametros_sistema WHERE clave = 'TOLERANCIA_TARDANZA_MINUTOS';")
    tolerancia = int(tol[0][0]) if tol and tol[0][0].isdigit() else 10
    con_base = {int(r[0]) for r in mysql("SELECT DISTINCT empleado_id FROM horarios;")}

    print('3/4 Cruzando horarios con marcaciones...')
    emparejados, plan, conflictos, no_reconocidas = {}, {}, [], collections.Counter()
    for lunes, nombre, celdas, origen in filas:
        if nombre not in emparejados:
            emparejados[nombre] = emparejar(nombre, empleados, alias)
        emp, _ = emparejados[nombre]
        if not emp:
            continue
        for k, celda in enumerate(celdas):
            fecha = lunes + dt.timedelta(days=k)
            valor = interpretar(celda)
            if valor[0] == 'error':
                no_reconocidas[valor[1]] += 1
            if valor[0] in ('vacio', 'error'):
                continue
            opciones = plan.setdefault((emp['id'], fecha), [])
            if all(o[0] != valor for o in opciones):
                opciones.append((valor, origen, celda.strip()))

    dias, resumen, omitidos = [], collections.defaultdict(collections.Counter), collections.Counter()
    for (eid, fecha), entrada in sorted(marcas.items(), key=lambda x: (x[0][1], x[0][0])):
        emp = por_id[eid]
        if emp['depto'] not in DEPARTAMENTOS_INCLUIDOS:
            omitidos['departamento no incluido (' + emp['depto_txt'] + ')'] += 1
            continue
        if (eid, fecha) in con_detalle:
            omitidos['ya tiene horario en el sistema'] += 1
            continue
        opciones = plan.get((eid, fecha)) or [(('vacio',), '', '')]
        # Tablas repetidas que se contradicen: vale el turno más cercano a su entrada real
        turnos = [o for o in opciones if o[0][0] == 'turno']
        if turnos:
            valor, _, celda = min(turnos, key=lambda o: abs(minutos(entrada) - minutos(o[0][1])))
        else:
            valor, _, celda = opciones[-1]
        if len(opciones) > 1:
            conflictos.append(f"{fecha} {emp['nombre']}: " + ' / '.join(f'{o[2]!r} ({o[1]})' for o in opciones)
                              + f' -> se usa {celda!r} (entrada real {entrada})')
        if valor[0] == 'turno':
            origen, ini, fin = ORIGEN_EXCEL, valor[1], valor[2]
        elif emp['depto'] == DEPARTAMENTO_BO and fecha >= bo_desde:
            origen, (ini, fin) = ORIGEN_BO, HORARIO_BO
        else:
            redondeo = (minutos(entrada) + 15) // 30 * 30
            origen, ini, fin = ORIGEN_ESTIMADO, hhmm(redondeo), hhmm(redondeo + HORAS_ESTIMADO * 60)
        if valor[0] == 'libre':
            resumen[eid]['marcó en día de ' + valor[1].lower() + ' del Excel'] += 1
        retraso = minutos(entrada) - minutos(ini)    # igual que el reporte: misma fecha, sin cruzar medianoche
        estado = 'Tardanza' if retraso > tolerancia else 'A tiempo'
        resumen[eid][origen] += 1
        resumen[eid][estado] += 1
        dias.append({'empleado_id': eid, 'fecha': fecha, 'entrada': ini, 'salida': fin, 'origen': origen,
                     'excel': celda, 'real': entrada, 'estado': estado, 'retraso': max(retraso, 0)})

    # Semanas: crear las que falten; reutilizar las importadas; no tocar las creadas a mano
    semanas = collections.OrderedDict()
    for d in dias:
        semanas.setdefault(d['fecha'] - dt.timedelta(days=d['fecha'].weekday()), []).append(d)
    nuevas, reutilizadas, saltadas = {}, {}, {}
    for lunes, lista in semanas.items():
        domingo = lunes + dt.timedelta(days=6)
        cruzan = [s for s in semanas_bd if s[1] <= domingo and s[2] >= lunes]
        manuales = [s for s in cruzan if not s[3].endswith(MARCA_SEMANA)]
        if manuales:
            saltadas[lunes] = (manuales[0][3], len(lista))
        elif cruzan:
            reutilizadas[lunes] = cruzan[0][0]
        else:
            nuevas[lunes] = f"Semana del {lunes:%d/%m} al {domingo:%d/%m/%Y}{MARCA_SEMANA}"

    # ---------------- Informe ----------------
    linea('CONTROL NGR - CARGA DE HORARIOS DESDE EXCEL ' + ('(APLICADO)' if a.aplicar else '(SIMULACIÓN: no se cambió nada)'))
    linea(f'Fecha: {dt.datetime.now():%Y-%m-%d %H:%M}    Excel: {os.path.basename(ruta_excel)}'
          + (f"    Extra: {', '.join(os.path.basename(r) for r in rutas_extra)}" if rutas_extra else ''))
    lunes_excel = sorted({f[0] for f in filas})
    linea(f'Tablas semanales leídas: {len(lunes_excel)} semanas ({lunes_excel[0]} a {lunes_excel[-1]})' if lunes_excel
          else 'No se encontraron tablas semanales')
    linea(f'Tolerancia de tardanza del sistema: {tolerancia} min    Back Office 08:30 - 17:30 desde {bo_desde}')
    linea()
    linea('NOMBRES DEL EXCEL -> SISTEMA (revise que sean correctos; se corrigen con --alias)')
    variantes, ignorados = collections.defaultdict(set), collections.defaultdict(set)
    for nombre, (emp, como) in emparejados.items():
        limpio = ' '.join(re.sub(r'\(.*?\)|\(.*', ' ', nombre).split())
        if emp:
            variantes[emp['nombre']].add(limpio + (' [alias]' if como == 'alias' else ''))
        else:
            ignorados[como].add(limpio[:60])
    for nombre in sorted(variantes, key=normalizar):
        linea(f"  {nombre:34s} <- {', '.join(sorted(variantes[nombre], key=normalizar))}")
    for como, nombres in ignorados.items():
        linea(f"  Se ignoran ({como}): {', '.join(sorted(nombres, key=normalizar))}")
    linea()
    linea('HORARIO A CARGAR POR COLABORADOR (solo días con marcación importada)')
    linea(f"  {'Colaborador':34s} {'Depto':14s} {'Excel':>6s} {'BO fijo':>8s} {'Estim.':>7s} {'A tiempo':>9s} {'Tardanza':>9s}")
    for eid in sorted(resumen, key=lambda i: (por_id[i]['depto'], por_id[i]['nombre'])):
        c = resumen[eid]
        linea(f"  {por_id[eid]['nombre'][:34]:34s} {por_id[eid]['depto_txt'][:14]:14s} {c[ORIGEN_EXCEL]:6d} "
              f"{c[ORIGEN_BO]:8d} {c[ORIGEN_ESTIMADO]:7d} {c['A tiempo']:9d} {c['Tardanza']:9d}")
        extras = [f'{k}: {v}' for k, v in c.items() if k.startswith('marcó')]
        if extras:
            linea('      ' + '; '.join(extras) + ' (se usó su marcación)')
    total = collections.Counter(d['origen'] for d in dias)
    linea(f"  TOTAL días: {len(dias)}  (Excel {total[ORIGEN_EXCEL]}, BO fijo {total[ORIGEN_BO]}, "
          f"estimado {total[ORIGEN_ESTIMADO]})")
    for motivo, n in omitidos.items():
        linea(f'  No se cargan {n} días: {motivo}')
    linea()
    linea(f'SEMANAS: {len(nuevas)} nuevas, {len(reutilizadas)} ya importadas antes (solo se agregan días que falten), '
          f'{len(saltadas)} existen en el sistema y no se tocan')
    for lunes, (nombre, n) in saltadas.items():
        linea(f'  {lunes}: ya existe "{nombre}" -> se omiten {n} días')
    if conflictos:
        linea()
        linea(f'CELDAS QUE SE CONTRADICEN ENTRE TABLAS REPETIDAS ({len(conflictos)} días con marcación):')
        for c in conflictos:
            linea('  ' + c)
    if no_reconocidas:
        linea()
        linea('CELDAS NO RECONOCIDAS (se ignoran):')
        for texto, n in no_reconocidas.most_common():
            linea(f'  {n:4d}  {texto!r}')
    avisos = sorted(por_id[i]['nombre'] for i in {d['empleado_id'] for d in dias} & con_base)
    if avisos:
        linea()
        linea('AVISO: tienen horario base (Horarios > horario fijo); en los días sin horario semanal el reporte usa ese '
              'horario base: ' + ', '.join(avisos))

    with open(os.path.join(salida_dir, 'detalle.csv'), 'w', encoding='utf-8-sig', newline='') as fh:
        w = csv.writer(fh, delimiter=';')
        w.writerow(['fecha', 'colaborador', 'departamento', 'origen', 'entrada', 'salida', 'celda_excel',
                    'entrada_real', 'estado', 'minutos_tarde'])
        for d in dias:
            e = por_id[d['empleado_id']]
            w.writerow([d['fecha'], e['nombre'], e['depto_txt'], d['origen'], d['entrada'], d['salida'], d['excel'],
                        d['real'], d['estado'], d['retraso'] if d['estado'] == 'Tardanza' else 0])

    a_cargar = [d for d in dias if (d['fecha'] - dt.timedelta(days=d['fecha'].weekday())) not in saltadas]
    if not a.aplicar:
        linea()
        linea(f'Simulación: se cargarían {len(a_cargar)} días en {len(nuevas) + len(reutilizadas)} semanas. '
              'Para cargarlos, repita el comando con --aplicar.')
        return guardar(salida_dir, informe)

    if not a_cargar:
        linea()
        linea('No hay días nuevos para cargar.')
        return guardar(salida_dir, informe)
    print('4/4 Cargando...')
    if input(f'Se cargarán {len(a_cargar)} días de horario. Escriba SI para continuar: ').strip().upper() != 'SI':
        sys.exit('Cancelado.')
    sql = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
    ahora = dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    for lunes, lista in semanas.items():
        if lunes in saltadas:
            continue
        if lunes in nuevas:
            sql.append('INSERT INTO horarios_semanales (fecha_inicio, fecha_fin, nombre, estado, creado_por_id, '
                       f'fecha_creacion) VALUES ({sql_valor(lunes.isoformat())}, '
                       f'{sql_valor((lunes + dt.timedelta(days=6)).isoformat())}, {sql_valor(nuevas[lunes])}, '
                       f"'activo', NULL, {sql_valor(ahora)});")
            sql.append('SET @semana = LAST_INSERT_ID();')
        else:
            sql.append(f'SET @semana = {reutilizadas[lunes]};')
        valores = []
        for d in lista:
            turno = 'manana' if minutos(d['entrada']) < 12 * 60 else 'tarde'
            valores.append('(@semana, ' + ', '.join(sql_valor(v) for v in [
                d['empleado_id'], d['fecha'].isoformat(), DIAS[d['fecha'].weekday()], d['entrada'] + ':00',
                d['salida'] + ':00', 'normal', turno, d['origen']]) + ')')
        sql.append('INSERT INTO horarios_semanales_detalle (horario_semanal_id, empleado_id, fecha, dia_semana, '
                   'hora_entrada, hora_salida, tipo_dia, turno, origen_tipo_dia) VALUES\n' + ',\n'.join(valores) + ';')
    sql.append('COMMIT;')
    texto = '\n'.join(sql) + '\n'
    with open(os.path.join(salida_dir, 'horarios.sql'), 'w', encoding='utf-8') as fh:
        fh.write(texto)
    mysql(texto, filas=False)
    linea()
    linea(f'Resultado: {len(a_cargar)} días de horario cargados en {len(nuevas)} semanas nuevas y '
          f'{len(reutilizadas)} existentes importadas.')
    guardar(salida_dir, informe)


def guardar(carpeta, informe):
    ruta = os.path.join(carpeta, 'informe.txt')
    with open(ruta, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(informe) + '\n')
    print(f'\nInforme guardado en {ruta} y detalle día por día en {carpeta}/detalle.csv '
          '(contienen datos personales; no los comparta).')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, KeyboardInterrupt) as e:
        sys.exit(f'\nERROR: {e}')
