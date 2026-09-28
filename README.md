# Control NGR - Backend

Sistema de asistencia con **marcación por reconocimiento facial**, horarios, solicitudes (vacaciones, compensación por feriado, descanso médico y licencias), encuestas y panel de administración.

Spring Boot 3.5 · Java 21 · MySQL 8 · Flyway · Docker

---

## 1. Levantar el sistema con Docker (recomendado)

> **¿Instalación en el equipo de la empresa?** Siga la guía paso a paso: [GUIA_INSTALACION.md](GUIA_INSTALACION.md).

Requisitos: Docker (Engine con el plugin `compose`, o Docker Desktop) y los **dos repositorios clonados en la misma carpeta**:

```
carpeta/
├── ControlNGR_Backend-v2/     ← aquí están docker-compose.yml y .env
└── ControlNGR_Frontend-v2/
```

```bash
cd ControlNGR_Backend-v2
cp .env.example .env        # completar DB_PASSWORD y JWT_SECRET
# Detecta la IP de este equipo, la guarda como SERVIDOR_IP, enciende y muestra el enlace:
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1 -Construir   # Windows
sh scripts/iniciar.sh --construir                                            # Linux
```

El enlace para los usuarios es `https://IP-DEL-EQUIPO` (nunca `localhost` ni `127.0.0.1`). `SERVIDOR_IP` define para qué IP se emite el certificado HTTPS; si queda vacía, el certificado solo sirve en el propio servidor y el log del frontend lo advierte. Con `-Ip 10.x.x.x` (Windows) o `sh scripts/iniciar.sh 10.x.x.x` (Linux) se indica una IP concreta.

> **Equipos con poca memoria para Docker (2 GB):** construya una parte a la vez para que no compitan por la RAM, y luego levante todo:
> ```bash
> docker compose build backend
> docker compose build frontend
> docker compose up -d
> ```
> Ya en funcionamiento, los tres contenedores usan unos 650 MB. La memoria de Java se ajusta con `BACKEND_JAVA_OPTS` en `.env` (por defecto `-Xms128m -Xmx512m -XX:+UseSerialGC`).

- La web queda en `https://IP-DEL-SERVIDOR` (puerto `HTTPS_PORT`, 443 por defecto). `http://` redirige a `https://`.
- **Solo se publica la web (nginx).** El backend no es accesible desde fuera de Docker y MySQL solo escucha en `127.0.0.1:3307` del servidor, para mantenimiento.
- **Las tablas y los datos iniciales se crean solos** la primera vez (Flyway, carpeta `src/main/resources/db/migration`).
- Los datos se guardan en volúmenes de Docker (`db_data`, `img_data` y `evidencias_data`), así que no se pierden al reiniciar ni al actualizar.
- Para llevar el sistema a otro equipo, con sus datos: ver [Respaldo y cambio de equipo](#respaldo-y-cambio-de-equipo).

Actualizar a una versión nueva:

```bash
git pull            # en ambos repositorios
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1 -Construir   # o: sh scripts/iniciar.sh --construir
```

### Respaldo y cambio de equipo

La base de datos, las fotos de perfil y las evidencias viven en volúmenes de Docker, **no en GitHub**. Son datos personales: si el repositorio se viera comprometido, no deben estar ahí. Para respaldarlos o llevarlos a otro equipo se usan dos scripts (Windows, PowerShell):

```powershell
# En el equipo actual (con el sistema encendido): genera respaldos\controlngr_AAAAMMDD_HHMM.zip
powershell -ExecutionPolicy Bypass -File .\scripts\respaldar.ps1

# En el equipo nuevo: clonar ambos repositorios, copiar el .env, y luego
powershell -ExecutionPolicy Bypass -File .\scripts\restaurar.ps1 -Respaldo D:\ruta\controlngr_AAAAMMDD_HHMM.zip
```

El `.zip` contiene la base completa (`controlngr.sql`), las fotos (`img/`) y las evidencias (`evidencias/`). Guárdelo en un lugar seguro; se recomienda generar uno periódicamente.

### HTTPS y certificado (obligatorio para la cámara)

Los navegadores solo permiten usar la cámara en páginas `https://`. Al iniciar, nginx genera automáticamente en la carpeta `certs/`:

| Archivo | Qué es |
|---|---|
| `ca.crt` | Autoridad local **Control NGR CA**. Se importa **una vez en cada PC** (Windows: `certmgr.msc` → *Entidades de certificación raíz de confianza* → Importar) |
| `servidor.crt` / `servidor.key` | Certificado del servidor para `localhost` y la IP de `SERVIDOR_IP` (se regenera solo si cambia la IP) |

- Si TI entrega un certificado propio, copie `servidor.crt` y `servidor.key` en `certs/` (sin el archivo `.autogenerado`) y reinicie: se usa tal cual.
- `certs/` contiene claves privadas: está en `.gitignore` y nunca se sube a git.

### Validación de red y Docker Desktop

La marcación solo se permite desde los segmentos registrados, así que el backend necesita ver la **IP real** de cada equipo:

- En un **servidor Linux** con Docker Engine, la IP del cliente llega intacta.
- En **Docker Desktop (Windows/Mac)**, Docker puede reemplazar la IP de los equipos de la red por una interna (por ejemplo `172.28.0.1`) y todos aparecerían "fuera de red".

Para comprobarlo, entre al panel admin → **Segmentos de red** desde otro equipo de la oficina: la pantalla muestra con qué IP lo ve el servidor. Si aparece una IP interna de Docker, se recomienda instalar el sistema en un servidor Linux.

### Pasar de XAMPP a Docker

Antes el sistema usaba XAMPP (Apache + MariaDB/MySQL + phpMyAdmin). Ahora la base de datos, el backend y la web corren en Docker y **XAMPP ya no es necesario**.

1. **Respaldar la base anterior (opcional, como archivo histórico):** en phpMyAdmin → base `controlngr` → *Exportar* → SQL. El sistema nuevo no importa esa base: arranca con su propia estructura y los datos iniciales (director, gerente, jefe, supervisores, feriados, etc.).
2. **Detener XAMPP:** en el panel de XAMPP detener *Apache* y *MySQL* y desmarcarlos como servicio de Windows. Si Apache sigue activo ocupa el puerto 80 y la web no podrá iniciar (o cambie `APP_PORT` en `.env`, por ejemplo `APP_PORT=8081`). El MySQL de Docker usa el puerto `3307`, así que no choca con el de XAMPP.
3. **Instalar Docker Desktop** (en Windows con WSL 2) y levantar el sistema:
   ```powershell
   cd ControlNGR_Backend-v2
   copy .env.example .env      # completar DB_PASSWORD y JWT_SECRET
   docker compose up -d --build
   ```
4. **Copiar las fotos de perfil** que antes estaban en `D:\ControlNGR\img` al volumen de Docker:
   ```powershell
   docker cp "D:\ControlNGR\img\." controlngr-backend:/app/data/img/
   ```
5. **Revisar la base con un cliente gráfico** (reemplaza a phpMyAdmin): HeidiSQL, DBeaver o MySQL Workbench, conectando a `127.0.0.1`, puerto `3307`, usuario `root` y la contraseña de `DB_PASSWORD`. Solo es accesible desde el mismo servidor.

Comandos útiles:

| Acción | Comando |
|---|---|
| Ver estado | `docker compose ps` |
| Ver logs del backend | `docker compose logs -f backend` |
| Detener | `docker compose stop` |
| Iniciar de nuevo | `docker compose start` |
| Borrar todo y empezar de cero (**elimina los datos**) | `docker compose down -v` |

Los contenedores tienen `restart: unless-stopped`: al reiniciar el equipo vuelven a iniciar solos (con Docker Desktop configurado para iniciar con Windows).

### Ejecutar sin Docker (desarrollo)

1. Levantar solo la base de datos con Docker: `docker compose up -d db` (queda en `127.0.0.1:3307`). La MariaDB de XAMPP no se recomienda: el sistema está hecho y probado para MySQL 8.
2. Crear el `.env` en la raíz del proyecto (Spring lo lee automáticamente) con `DB_HOST=127.0.0.1`, `DB_PORT=3307`, `DB_USER=root`, `DB_PASSWORD` y `JWT_SECRET`.
3. Ejecutar `./gradlew bootRun`. El frontend se ejecuta aparte con `npm start` (puerto 4200).

> El archivo `.env` **nunca** se sube a git. Las contraseñas de la base de datos, del correo y el secreto JWT solo existen en el equipo donde corre el sistema.

### Correo

Para enviar notificaciones por correo, agregue al `.env` los datos SMTP (por ejemplo, una contraseña de aplicación de Gmail) y active `MAIL_ENABLED=true`:

```
MAIL_ENABLED=true
MAIL_USERNAME=cuenta@gmail.com
MAIL_PASSWORD=contraseña_de_aplicacion
MAIL_FROM=cuenta@gmail.com
```

## 2. Primer ingreso

| Usuario | Contraseña inicial | Nota |
|---|---|---|
| `admin` | `$.4dmin2026` | Panel maestro. Debe cambiarla al ingresar |
| DNI de cada empleado | la misma que tenía antes | Deben cambiarla al ingresar |
| DNI de un empleado nuevo | `Soporte26$` | Solo al crearlo. Debe cambiarla en su primer ingreso |

Mientras un usuario tenga el cambio de contraseña pendiente, la API responde `403 {"debeCambiarPassword": true}` a todo excepto `/api/auth/**`. El endpoint `POST /api/auth/cambiar-password` devuelve un token nuevo.

Pasos sugeridos para el admin después del primer ingreso:

1. Revisar los **segmentos de red** (vienen cargados `10.92.104.%` y `192.168.113.%`).
2. Registrar los **saldos iniciales** de vacaciones y días por compensar de cada empleado (carga inicial).
3. Revisar los **feriados** (vienen cargados los nacionales de Perú de 2026 y 2027).
4. Completar los correos de Alfredo Novoa y José Carlos Ruiz (en la base anterior tenían el correo de otra persona).
5. Registrar a Jorge (rol `gestor`) y a los asistentes de almacén (rol `asistente`) en el departamento *Tiendas y Almacén de Sistemas*.

---

## 3. Roles y aprobaciones

| Rol | Descripción | Sus solicitudes las aprueba |
|---|---|---|
| `admin` | Panel maestro. No marca asistencia ni gestiona solicitudes | — |
| `director` | Director de Sistemas. No registra solicitudes | — |
| `gerente` | Gerente de Infraestructura y Soporte | director |
| `jefe` | Jefe de Soporte | gerente |
| `supervisor` | Supervisores de SOP, HD y NOC | jefe |
| `gestor` | Gestor de Tiendas y Almacén de Sistemas | gerente |
| `tecnico`, `hd`, `noc`, `bo` | Personal operativo | cualquier supervisor |
| `asistente` | Asistentes de almacén | gestor |

Quién aprueba a quién se guarda en la tabla `reglas_aprobacion` y se puede cambiar desde el panel admin.

---

## 4. Reglas de negocio

**Asistencia**
- Solo se puede marcar desde los segmentos de red registrados. Si no, se responde `403` con el mensaje *"Está fuera de red"* (`fueraDeRed: true`).
- **Por rol** (panel admin → **Asistencia y horarios**) se configura:
  - *Marca asistencia*: el rol registra entrada y salida.
  - *Horario y reporte de asistencia*: el rol recibe horario (aparece en **Horarios**), aparece en el **reporte de asistencia** (y en su filtro de roles) y se le calculan tardanzas.
  - Un rol que marca **sin** horario tiene horario flexible: marca cualquier día, sin tardanzas. Un rol que no marca tampoco tiene horario.
  - Valores iniciales: director, gerente y jefe marcan con horario flexible; el resto marca con horario; el admin no marca.
- Para los roles con horario, la entrada solo se permite si el día está programado como laboral en el horario semanal activo o, si no hay uno, en el horario base. Siempre con validación de red y reconocimiento facial.
- La fecha y la hora las pone el servidor; los valores que envíe el cliente se ignoran.
- Cada usuario solo puede marcar su propia asistencia.
- **Tolerancia de tardanza: 10 minutos** (parámetro `TOLERANCIA_TARDANZA_MINUTOS`, entero de 0 a 120). Se cambia en el panel admin → **Asistencia y horarios** y se aplica igual al marcar y en el reporte.
- **Salida automática**: si el colaborador no marca su salida, el sistema la registra sola **12 horas después de la entrada** (parámetro `SALIDA_AUTOMATICA_HORAS`, 1 a 23, en el panel admin → **Asistencia y horarios**). Ejemplo: entrada 17:00 → salida 05:00 del día siguiente. Queda marcada como *automática* (en Inicio y en el reporte) y no se puede justificar con mensaje. La revisión corre cada 5 minutos, al iniciar el sistema y antes de cada marcación, así nadie queda con una salida pendiente.
- Un turno que cruza la medianoche (por ejemplo 22:00 a 06:00) se muestra en Inicio como "Desde ayer" para poder marcar la salida.
- La marcación se registra **en cuanto se verifica el rostro**. Después el colaborador ve la hora registrada y puede dejar, si quiere, un **mensaje breve para su supervisor** (hasta 300 caracteres, uno por marcación, dentro de los 30 minutos siguientes). El mensaje aparece en *Reportes de asistencia*.
- **Un empleado inactivo no puede ingresar al sistema**: al desactivarlo también se bloquea su usuario y su sesión abierta deja de funcionar.

**Reconocimiento facial**
- Toda marcación de entrada y salida exige el rostro (parámetro `MARCACION_FACIAL_OBLIGATORIA`, activo por defecto). Si se desactiva, solo quienes tienen rostro registrado marcan con cámara.
- El navegador (face-api.js, modelos locales en `/models`) calcula un descriptor de 128 números; **la comparación la hace el servidor** contra las muestras registradas (distancia máxima `UMBRAL_FACIAL`, 0.5 por defecto). Los descriptores guardados nunca se envían al navegador.
- Antes de capturar se comprueba que sea una persona real (parpadeo). No se guardan fotos.
- Registro: cada colaborador registra su rostro **una sola vez** desde *Mi perfil*, con consentimiento (Ley 29733). Para volver a registrarlo, su jefatura o el admin lo restablecen desde *Empleados*, donde también pueden registrarlo en persona.
- Se rechaza registrar un rostro que ya pertenece a otra cuenta y capturas que no son de la misma persona.
- Cada marcación guarda el método (`facial`/`manual`) y la distancia obtenida.
- En el panel admin → **Reconocimiento facial** se ve quién tiene rostro registrado y se puede **probar la marcación** (a quién reconoce y con qué distancia) y **probar un registro**, sin registrar asistencia ni guardar rostros. Sirve para calibrar `UMBRAL_FACIAL`.

**Eventos**
- Al crear o editar un evento se puede indicar un **enlace** opcional (reunión de Teams/Meet, formulario, documento). Solo se aceptan direcciones `https://` o `http://`.
- Las personas asignadas ven el botón **Ingresar** (abre en otra pestaña) solo si el evento tiene enlace.

**Feriados laborados**
- Si la entrada se marca en un feriado activo, se abonan automáticamente **2 días** de compensación (parámetro `DIAS_POR_FERIADO_LABORADO`), una sola vez por feriado.
- Los días de compensación no vencen. El admin puede revertir un abono.
- En el panel admin → **Feriados**, el botón **Copiar a (año siguiente)** muestra una vista previa y copia los feriados con el mismo día y mes. **Jueves y Viernes Santo se recalculan** según la Pascua de ese año. No duplica: omite los que ya están registrados o las fechas que ya son feriado, y se puede desmarcar cualquiera antes de copiar (`POST /api/admin/feriados/copiar` `{origen, destino, simular, ids?}`).

**Vacaciones**
- Se abonan **30 días** (parámetro `DIAS_VACACIONES_POR_ANIO`) cada vez que el empleado cumple un año desde su fecha de ingreso. El proceso corre cada día a las 00:15 y también al iniciar el sistema.
- Solo se abonan automáticamente los aniversarios a partir de `VACACIONES_ABONO_DESDE` (la fecha de instalación). Lo acumulado antes se registra con la carga inicial.

**Solicitudes**
- Tipos: `vacaciones`, `compensacion`, `descanso_medico` y `licencia` (el tipo `permiso` se eliminó).
- Se cuentan días calendario, incluidos el día de inicio y el de fin.
- La fecha de inicio y la de fin deben ser **hoy o posteriores** (al crear y al editar).
- Vacaciones y compensación se **rechazan al crearlas si no hay saldo disponible**. El disponible es el saldo menos lo que ya está en solicitudes pendientes.
- El saldo se descuenta **al aprobar**. Si una solicitud aprobada se corrige a rechazada, los días se devuelven.
- Descanso médico y licencia exigen adjuntar evidencia (JPG, PNG o PDF, hasta 10 MB). La licencia exige además un motivo (paternidad, fallecimiento de familiar, etc.).
- No se permiten dos solicitudes propias que se crucen en fechas.
- Cada cambio de estado queda en `solicitud_historial`, y cada movimiento de días en `movimientos_saldo`, con el saldo resultante.

---

## 5. Endpoints nuevos o modificados (para el frontend)

### Autenticación
| Método | Ruta | Nota |
|---|---|---|
| POST | `/api/auth/login` | Ahora incluye `usuario` (rol, `esAdmin`, `debeCambiarPassword`). `empleado` es `null` para el admin |
| POST | `/api/auth/cambiar-password` | `{passwordActual, passwordNueva, confirmarPassword}`. Mínimo 8 caracteres, con letras y números. Devuelve un token nuevo |

### Asistencia
| Método | Ruta | Nota |
|---|---|---|
| GET | `/api/asistencia/verificar-red` | `{ip, dentroDeRed, mensaje}` para avisar antes de marcar |
| POST | `/api/asistencia/registrar` | `{tipo: "entrada" \| "salida"}`. Si fue feriado, la respuesta trae `feriado` y `diasCompensacionAbonados` |
| GET | `/api/asistencia/mi-configuracion` | `{marcaAsistencia, conHorario, toleranciaMinutos}` del usuario |
| PUT | `/api/asistencia/{id}/mensaje` | `{tipo, mensaje}`: justificación del propio colaborador sobre su marcación (una vez, hasta 30 min después). El reporte trae `mensajeEntrada` y `mensajeSalida` |

### Solicitudes
| Método | Ruta | Nota |
|---|---|---|
| GET | `/api/solicitudes/tipos` | Catálogo de tipos activos |
| GET | `/api/solicitudes/motivos-licencia` | Catálogo de motivos |
| POST | `/api/solicitudes/crear` (JSON) | `{tipo, fechaInicio, fechaFin, motivo}` |
| POST | `/api/solicitudes/crear` (multipart) | Parte `solicitud` (JSON, incluye `motivoLicenciaId` si es licencia) y parte `archivo` |
| GET | `/api/solicitudes/pendientes-por-aprobar` | Solo las que el usuario puede aprobar |
| GET | `/api/solicitudes/roles-a-cargo` | Roles cuyas solicitudes aprueba el usuario |
| PUT | `/api/solicitudes/gestionar/{id}` | `{estado: "aprobado" \| "rechazado", comentarios}`. El aprobador es el usuario del token |
| GET | `/api/solicitudes/{id}/historial` | Cambios de estado |
| GET | `/api/solicitudes/{id}/evidencias/{evidenciaId}` | Descarga del archivo (solicitante, aprobadores, gerencia y admin) |

### Reconocimiento facial
| Método | Ruta | Nota |
|---|---|---|
| GET | `/api/face/mi-estado` | `{registrado, muestras, registradoEl, consentimientoEl, obligatorio, muestrasRequeridas}` |
| POST | `/api/face/registrar` | `{descriptores: number[][], consentimiento: true}`. El propio colaborador, una sola vez |
| POST | `/api/face/registrar/{empleadoId}` | Registro en persona por su jefatura o el admin |
| DELETE | `/api/face/{empleadoId}` | Restablecer (jefatura o admin) |
| GET | `/api/face/registrados` | Ids con rostro registrado (jefaturas y admin) |
| GET | `/api/face/admin/resumen` | Solo admin: quiénes marcan y si tienen rostro |
| POST | `/api/face/admin/probar-marcacion` | Solo admin: `{descriptor, empleadoId?}` → a quién reconoce y con qué distancia. **No registra asistencia** |
| POST | `/api/face/admin/probar-registro` | Solo admin: `{descriptores}` → calidad de las capturas y si el rostro ya pertenece a alguien. **No guarda nada** |

La marcación (`POST /api/asistencia/registrar`) recibe además `descriptor: number[]`. Los errores traen `codigoFacial`: `NO_REGISTRADO`, `FALTA_ROSTRO`, `NO_COINCIDE`, `DESCRIPTOR_INVALIDO`, `YA_REGISTRADO`, `CONSENTIMIENTO`, `MUESTRAS_INCONSISTENTES`, `ROSTRO_DE_OTRO`.

### Saldos
| Método | Ruta | Nota |
|---|---|---|
| GET | `/api/saldos/mi-saldo` | Saldo, pendiente y disponible, más movimientos, feriados laborados y periodos |
| GET | `/api/saldos/mis-movimientos?tipo=VACACIONES` | Historial de movimientos |
| GET | `/api/saldos/empleado/{id}` | Para jefaturas, supervisores y admin |

### Panel admin (`/api/admin/**`, solo rol `admin`)
`mi-ip` · `parametros` · `segmentos-red` · `feriados` · `departamentos` · `tipos-usuario` · `reglas-aprobacion` · `tipos-solicitud` · `motivos-licencia` · `usuarios` (rol, estado, restablecer contraseña) · `saldos` (listado, `carga-inicial`, `ajuste`, movimientos) · `feriados-laborados` (listado y revertir) · `vacaciones/procesar` · `asistencia/roles` (GET configuración por rol con empleados activos, PUT `/{id}` `{marcaAsistencia, conHorario}`). `GET /api/empleados/roles` también devuelve `marcaAsistencia` y `conHorario`.

Los empleados se registran con `POST /api/empleados` (admin, gerencia y **supervisores**; el supervisor solo registra personal operativo: técnico, HD, NOC y BO). Se crea también su usuario: el DNI como usuario y la contraseña inicial `Soporte26$`, que debe cambiar en su primer ingreso. Solo el admin y la gerencia pueden indicar otro usuario u otra contraseña inicial.

---

## 6. IP del cliente detrás de nginx

En Docker, nginx reemplaza (no agrega) el encabezado `X-Forwarded-For` con la IP que ve, y el backend solo confía en ese encabezado cuando viene de la red interna de Docker (`FORWARD_HEADERS_STRATEGY=native`, `TRUSTED_PROXIES`, ya configurados en `docker-compose.yml`). Así ningún equipo puede falsificar su IP para marcar desde fuera de la red.
