# Control NGR - Backend

Sistema de asistencia, horarios, solicitudes (vacaciones, compensación por feriado, descanso médico y licencias), encuestas y panel de administración.

Spring Boot 3.5 · Java 21 · MySQL 8 · Flyway · Docker

---

## 1. Levantar el sistema con Docker (recomendado)

Requisitos: Docker (Engine con el plugin `compose`, o Docker Desktop) y los **dos repositorios clonados en la misma carpeta**:

```
carpeta/
├── ControlNGR_Backend-v2/     ← aquí están docker-compose.yml y .env
└── ControlNGR_Frontend-v2/
```

```bash
cd ControlNGR_Backend-v2
cp .env.example .env        # completar DB_PASSWORD (usuario root de MySQL) y JWT_SECRET
docker compose up -d --build
```

- La web queda en `http://IP-DEL-SERVIDOR` (puerto `APP_PORT`, 80 por defecto).
- **Solo se publica la web (nginx).** El backend no es accesible desde fuera de Docker y MySQL solo escucha en `127.0.0.1:3307` del servidor, para mantenimiento.
- **Las tablas y los datos iniciales se crean solos** la primera vez (Flyway, carpeta `src/main/resources/db/migration`).
- Los datos se guardan en volúmenes de Docker (`db_data`, `img_data` y `evidencias_data`), así que no se pierden al reiniciar ni al actualizar.
- Para llevar el sistema a otro equipo: clonar ambos repositorios, crear el `.env` y ejecutar el mismo comando.

Actualizar a una versión nueva:

```bash
git pull            # en ambos repositorios
docker compose up -d --build
```

Respaldo de la base de datos:

```bash
docker exec controlngr-db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" controlngr' > respaldo.sql
```

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
- La entrada solo se permite si el día está programado como laboral en el horario semanal activo o, si no hay uno, en el horario base.
- La fecha y la hora las pone el servidor; los valores que envíe el cliente se ignoran.
- Cada usuario solo puede marcar su propia asistencia.

**Feriados laborados**
- Si la entrada se marca en un feriado activo, se abonan automáticamente **2 días** de compensación (parámetro `DIAS_POR_FERIADO_LABORADO`), una sola vez por feriado.
- Los días de compensación no vencen. El admin puede revertir un abono.

**Vacaciones**
- Se abonan **30 días** (parámetro `DIAS_VACACIONES_POR_ANIO`) cada vez que el empleado cumple un año desde su fecha de ingreso. El proceso corre cada día a las 00:15 y también al iniciar el sistema.
- Solo se abonan automáticamente los aniversarios a partir de `VACACIONES_ABONO_DESDE` (la fecha de instalación). Lo acumulado antes se registra con la carga inicial.

**Solicitudes**
- Tipos: `vacaciones`, `compensacion`, `descanso_medico` y `licencia` (el tipo `permiso` se eliminó).
- Se cuentan días calendario, incluidos el día de inicio y el de fin.
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

### Saldos
| Método | Ruta | Nota |
|---|---|---|
| GET | `/api/saldos/mi-saldo` | Saldo, pendiente y disponible, más movimientos, feriados laborados y periodos |
| GET | `/api/saldos/mis-movimientos?tipo=VACACIONES` | Historial de movimientos |
| GET | `/api/saldos/empleado/{id}` | Para jefaturas, supervisores y admin |

### Panel admin (`/api/admin/**`, solo rol `admin`)
`mi-ip` · `parametros` · `segmentos-red` · `feriados` · `departamentos` · `tipos-usuario` · `reglas-aprobacion` · `tipos-solicitud` · `motivos-licencia` · `usuarios` (rol, estado, restablecer contraseña) · `saldos` (listado, `carga-inicial`, `ajuste`, movimientos) · `feriados-laborados` (listado y revertir) · `vacaciones/procesar`.

El admin registra empleados con `POST /api/empleados`: se crea también su usuario, con el DNI como usuario y contraseña inicial.

---

## 6. IP del cliente detrás de nginx

En Docker, nginx reemplaza (no agrega) el encabezado `X-Forwarded-For` con la IP que ve, y el backend solo confía en ese encabezado cuando viene de la red interna de Docker (`FORWARD_HEADERS_STRATEGY=native`, `TRUSTED_PROXIES`, ya configurados en `docker-compose.yml`). Así ningún equipo puede falsificar su IP para marcar desde fuera de la red.
