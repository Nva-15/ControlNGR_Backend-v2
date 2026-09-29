# Guía de instalación de Control NGR en un equipo de la empresa

Guía paso a paso para instalar el sistema en el equipo (servidor) donde quedará funcionando para todos.
Solo se necesita saber abrir una terminal y copiar comandos.

---

## 0. Antes de empezar: elegir el equipo

| | **Opción A: Servidor Linux (Ubuntu)** ✅ recomendada | **Opción B: PC o servidor con Windows** |
|---|---|---|
| Programa | Docker Engine (gratuito) | Docker Desktop |
| Licencia | Gratis | Gratis solo para empresas pequeñas (menos de 250 empleados **y** menos de US$ 10 millones de facturación anual). Para empresas más grandes **requiere suscripción de pago**: consulte con TI |
| Validación de red para marcar | Funciona siempre | Puede fallar: Windows a veces oculta la IP real de los equipos y todos aparecerían "fuera de red". Hay que comprobarlo (paso 6) |
| Memoria RAM | 4 GB mínimo | 8 GB mínimo |

**Requisitos del equipo (ambas opciones):**
- 2 núcleos de CPU, 4 GB de RAM libres para Docker y 20 GB de disco libre.
- **IP fija** dentro de la red de la oficina (pedirla al área de redes). Ejemplo: `10.92.104.20`.
- Acceso a internet durante la instalación (para descargar Docker y los programas).
- Acceso a los dos repositorios de GitHub (`ControlNGR_Backend-v2` y `ControlNGR_Frontend-v2`).
- Que el equipo quede encendido siempre (es el servidor).
- Las PCs donde se marca asistencia necesitan **cámara web** (la marcación es con reconocimiento facial).

**Qué debe tener a mano:**
- El archivo **`.env`** (contraseñas del sistema). No está en GitHub; cópielo por USB o créelo en el paso 3.
- Si viene de otro equipo: el **respaldo** (`.zip` o `.tar.gz`) generado con los scripts de respaldo.

---

## OPCIÓN A: Servidor Linux (Ubuntu 22.04 o 24.04)

### A1. Instalar Docker y Git

```bash
sudo apt update
sudo apt install -y git curl
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```

Cierre la sesión y vuelva a entrar (para que el usuario pueda usar Docker sin `sudo`). Compruebe:

```bash
docker --version
docker compose version
```

### A2. Descargar el sistema

```bash
sudo mkdir -p /opt/controlngr
sudo chown $USER /opt/controlngr
cd /opt/controlngr
git clone https://github.com/Nva-15/ControlNGR_Backend-v2.git
git clone https://github.com/Nva-15/ControlNGR_Frontend-v2.git
```

GitHub pedirá usuario y un *token* de acceso (no la contraseña normal).

### A3. Crear el archivo `.env`

Si trae el `.env` de otro equipo, cópielo a `/opt/controlngr/ControlNGR_Backend-v2/.env` y pase al paso A4.

Si no, créelo:

```bash
cd /opt/controlngr/ControlNGR_Backend-v2
cp .env.example .env
openssl rand -hex 48        # copie el texto que aparece: es su JWT_SECRET
nano .env
```

Complete como mínimo estas líneas (sin comillas), guarde con `Ctrl+O`, `Enter` y salga con `Ctrl+X`:

```
DB_PASSWORD=una_contraseña_segura
JWT_SECRET=el_texto_largo_que_generó
SERVIDOR_IP=10.92.104.20
```

`SERVIDOR_IP` es la IP del servidor en la red: con ella se genera el certificado HTTPS y es el enlace que usarán todos (`https://10.92.104.20`). **Puede dejarla vacía**: el script de encendido (paso siguiente) detecta la IP de este equipo y la completa sola. Pida a TI que la IP del servidor sea **fija** (IP estática o reserva DHCP) para que el enlace no cambie.

Proteja el archivo para que solo su usuario lo pueda leer:

```bash
chmod 600 .env
```

### A4. Encender el sistema

```bash
cd /opt/controlngr/ControlNGR_Backend-v2
sh scripts/iniciar.sh --construir
```

El script detecta la IP de este equipo, la guarda como `SERVIDOR_IP` en `.env`, enciende el sistema y muestra el enlace para los usuarios (por ejemplo `Sistema disponible en: https://10.92.104.20`). Si el equipo tiene más de una red, puede indicar la IP: `sh scripts/iniciar.sh --construir 10.92.104.20`.

La primera vez tarda entre 5 y 15 minutos. Al terminar:

```bash
docker compose ps
```

Deben aparecer `controlngr-db` (healthy), `controlngr-backend` y `controlngr-frontend`.

### A5. Abrir los puertos de la web (si el servidor tiene firewall)

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

Continúe en el **paso 5 (común)**.

---

## OPCIÓN B: Windows 10/11 o Windows Server

### B1. Instalar WSL, Docker Desktop y Git

1. Abrir **PowerShell como administrador** y ejecutar:
   ```powershell
   wsl --install
   ```
   Reiniciar el equipo.
2. Descargar e instalar **Docker Desktop**: https://www.docker.com/products/docker-desktop/ (opción *Use WSL 2*).
3. Abrir Docker Desktop → ⚙️ **Settings** → **General** → marcar **Start Docker Desktop when you sign in**.
4. Descargar e instalar **Git**: https://git-scm.com/download/win (opciones por defecto).
5. Si en ese equipo hay **XAMPP**, detener Apache y MySQL y quitarlos del inicio automático.

Cierre y vuelva a abrir PowerShell. Compruebe:

```powershell
docker --version
git --version
```

### B2. Descargar el sistema

```powershell
mkdir C:\ControlNGR
cd C:\ControlNGR
git clone https://github.com/Nva-15/ControlNGR_Backend-v2.git
git clone https://github.com/Nva-15/ControlNGR_Frontend-v2.git
```

### B3. Crear el archivo `.env`

Si trae el `.env` de otro equipo, cópielo a `C:\ControlNGR\ControlNGR_Backend-v2\.env` y pase al paso B4.

Si no:

```powershell
cd C:\ControlNGR\ControlNGR_Backend-v2
copy .env.example .env
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); ($b | % { $_.ToString('x2') }) -join ''
notepad .env
```

El comando largo muestra un texto: es su `JWT_SECRET`. Complete (sin comillas) y guarde:

```
DB_PASSWORD=una_contraseña_segura
JWT_SECRET=el_texto_largo_que_generó
SERVIDOR_IP=10.92.104.20
```

`SERVIDOR_IP` es la IP del servidor en la red: con ella se genera el certificado HTTPS y es el enlace que usarán todos (`https://10.92.104.20`). **Puede dejarla vacía**: el script de encendido (paso siguiente) detecta la IP de este equipo y la completa sola. Pida a TI que la IP del servidor sea **fija** (IP estática o reserva DHCP) para que el enlace no cambie.

Compruebe que el archivo se llame `.env` y no `.env.txt`:

```powershell
dir -Force .env*
```

### B4. Encender el sistema

Con poca memoria es mejor construir una parte a la vez:

```powershell
cd C:\ControlNGR\ControlNGR_Backend-v2
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1 -Construir
```

El script:
1. Detecta la **IP de red de este equipo** (la de la conexión a la oficina; no `localhost` ni `127.0.0.1`) y la guarda como `SERVIDOR_IP` en `.env`.
2. Construye el sistema (una parte a la vez, para usar menos memoria) y lo enciende.
3. Muestra el enlace para compartir con los usuarios, por ejemplo `Sistema disponible en: https://10.92.104.20`.

Si el equipo tiene más de una red (por ejemplo Wi‑Fi y VPN), el script muestra las demás IP; si la correcta es otra, indíquela: `.\scripts\iniciar.ps1 -Construir -Ip 10.92.104.20`.

Compruebe los contenedores con `docker compose ps`: deben aparecer `controlngr-db` (healthy), `controlngr-backend` y `controlngr-frontend`.

### B5. Abrir los puertos de la web en el firewall de Windows

En **PowerShell como administrador**:

```powershell
New-NetFirewallRule -DisplayName "Control NGR (web)" -Direction Inbound -Protocol TCP -LocalPort 80,443 -Action Allow
```

---

## 4.5 Certificado HTTPS en cada PC (una sola vez)

La web funciona con `https://` (el navegador exige conexión segura para usar la cámara). Al encender el sistema se crea la carpeta `certs` con el archivo **`ca.crt`** (autoridad "Control NGR CA").

En **cada PC** que usará el sistema (incluido el servidor):

1. Copie `ca.crt` (de `ControlNGR_Backend-v2\certs\`) a la PC, por USB o carpeta compartida.
2. Doble clic en `ca.crt` → **Instalar certificado** → *Equipo local* → *Colocar todos los certificados en el siguiente almacén* → **Entidades de certificación raíz de confianza** → Finalizar.
3. Cierre y vuelva a abrir el navegador.

Con muchas PCs, TI puede distribuir `ca.crt` por directiva de grupo (GPO). Si falta este paso, el navegador mostrará "La conexión no es privada".

> Si cambia la IP del servidor, vuelva a ejecutar el script de encendido (`.\scripts\iniciar.ps1` en Windows, `sh scripts/iniciar.sh` en Linux): actualiza `SERVIDOR_IP`, el certificado del servidor se renueva solo y **no** hace falta volver a importar `ca.crt`. Eso sí, el enlace cambia: por eso conviene que la IP sea fija.

## 5. Primer ingreso (ambas opciones)

1. En el mismo servidor abrir el navegador en `https://localhost`.
2. Ingresar con usuario **`admin`** y contraseña **`$.4dmin2026`**. El sistema obligará a cambiarla: use una contraseña segura y guárdela.
3. En el **Panel maestro** revisar:
   - **Segmentos de red**: `10.92.104.%` (oficina) y `192.168.113.%` (VPN).
   - **Feriados** del año.
   - **Saldos y carga inicial** de vacaciones y días por compensar.
   - **Asistencia y horarios**: qué roles marcan asistencia, cuáles trabajan con horario (aparecen en Horarios y en el reporte de asistencia) y la tolerancia de tardanza (10 minutos por defecto).

> Los empleados nuevos ingresan con su **DNI** y la contraseña inicial **`Soporte26$`**; el sistema les pide cambiarla la primera vez.

> Si está **restaurando datos de otro equipo**, haga primero el paso 8 (restaurar) y luego ingrese con la contraseña de admin que ya tenía.

## 6. Prueba desde otra PC de la oficina (muy importante)

1. Desde otra PC abrir `https://IP-DEL-SERVIDOR` (ejemplo: `https://10.92.104.20`).
2. Ingresar como admin → **Segmentos de red**. La pantalla muestra **con qué IP ve el servidor a esa PC**.
   - Si muestra la IP real de la PC (por ejemplo `10.92.104.57`): ✅ todo bien.
   - Si muestra una IP como `172.x.x.x`: ❌ Docker está ocultando las IPs (pasa en Windows) y nadie podrá marcar. Solución: instalar en un servidor Linux (Opción A).
3. Ingresar con un colaborador y comprobar que el botón **Marcar entrada** esté habilitado dentro de la red y deshabilitado fuera de ella.

Comparta con el personal la dirección que mostró el script de encendido, `https://IP-DEL-SERVIDOR`. **Nunca** `https://localhost` ni `127.0.0.1`: esas direcciones solo funcionan dentro del propio servidor.

## 6.5 Registro de rostros

La marcación de asistencia es con **reconocimiento facial**. Antes de su primera marcación, cada colaborador:

1. Ingresa al sistema → **Mi perfil** → sección *Reconocimiento facial*.
2. Marca la casilla de **consentimiento** y pulsa **Registrar mi rostro**.
3. Mira a la cámara con buena iluminación y parpadea con normalidad; se toman 5 capturas en unos segundos.

Luego, en **Inicio**, los botones *Marcar entrada / salida* abren la cámara y verifican su identidad.

- Cada persona registra su rostro **una sola vez**. Para repetirlo (cambio de apariencia, mala captura), su supervisor, jefatura o el admin lo **restablece** en **Empleados** (botón con ícono de persona tachada). Ahí también pueden registrarlo en persona.
- En el panel admin → **Reconocimiento facial** puede ver quién ya registró su rostro y **probar** la marcación o un registro con la cámara, sin registrar asistencia ni guardar nada. Úselo con varias personas para calibrar el umbral.
- En el panel admin → **Parámetros** se puede ajustar `UMBRAL_FACIAL` (0.4 más estricto, 0.6 más permisivo) o desactivar `MARCACION_FACIAL_OBLIGATORIA`.

---

## 7. Uso diario

Todos los comandos se ejecutan **dentro de la carpeta `ControlNGR_Backend-v2`**:
- Linux: `cd /opt/controlngr/ControlNGR_Backend-v2`
- Windows: `cd C:\ControlNGR\ControlNGR_Backend-v2`

| Quiero… | Comando |
|---|---|
| Ver si está funcionando | `docker compose ps` |
| Encender (y ver el enlace) | Windows: `powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1` · Linux: `sh scripts/iniciar.sh` |
| Apagar | `docker compose stop` |
| Reiniciar | `docker compose restart` |
| Ver errores del sistema | `docker compose logs --tail 100 backend` |
| Actualizar a una versión nueva | `git pull` en **las dos** carpetas y luego el script de encendido con `-Construir` (Windows) o `--construir` (Linux) |

El sistema **se enciende solo** al prender el equipo (Linux: automático; Windows: si Docker Desktop inicia con la sesión).

> ⚠️ **Nunca** use `docker compose down -v`: la opción `-v` **borra la base de datos, fotos y evidencias**.

---

## 8. Respaldos (hacerlo cada semana)

Los datos (base de datos, fotos y evidencias) **no están en GitHub**. Solo existen en el servidor y en los respaldos.

**Respaldo automático desde el panel (recomendado):** entre como `admin` → **Panel maestro → Respaldos**.

- **Respaldo automático:** elija *Diario*, *Semanal* (y el día) o *Mensual* (y el día del mes), la hora (de Lima) y cuántos respaldos automáticos conservar. Al superar esa cantidad se borra el más antiguo.
- **Respaldo ahora:** genera uno en el momento (por ejemplo, antes de actualizar o de cambiar de equipo).
- Marque **Incluir fotos y evidencias** para poder restaurar todo en otro equipo.
- Los archivos quedan en la carpeta **`respaldos`** dentro de `ControlNGR_Backend-v2` del servidor, y se pueden **descargar** desde la tabla del panel. Cada `.zip` trae un `LEEME.txt` con los pasos para restaurarlo.
- Si el sistema estaba apagado a la hora programada, el respaldo se hace apenas vuelva a encenderse.
- Si el panel avisa *"No se puede escribir en la carpeta de respaldos"*, vuelva a encender el sistema con el script `iniciar` (paso A4/B4): le da permiso a esa carpeta.

**Copie los respaldos a otro lugar** de vez en cuando (servidor de archivos, disco externo): si el disco del servidor falla, los respaldos de la carpeta `respaldos` se pierden con él.

**Sacar un respaldo con script** (alternativa sin entrar al panel):

| Linux | Windows |
|---|---|
| `sh scripts/respaldar.sh` | `powershell -ExecutionPolicy Bypass -File .\scripts\respaldar.ps1` |

Se crea un archivo en la carpeta `respaldos` (por ejemplo `controlngr_20260927_1830.tar.gz` o `.zip`). **Cópielo a otro lugar** (servidor de archivos, disco externo). Contiene datos personales: nunca lo suba a GitHub ni lo envíe por correo.

**Restaurar un respaldo** (por ejemplo en un equipo nuevo, después de los pasos 1 a 4):

| Linux | Windows |
|---|---|
| `sh scripts/restaurar.sh respaldos/controlngr_20260927_1830.tar.gz` | `powershell -ExecutionPolicy Bypass -File .\scripts\restaurar.ps1 -Respaldo D:\ruta\controlngr_20260927_1830.zip` |

El script pide escribir `SI` para confirmar, porque reemplaza todos los datos actuales. Sirve igual para los respaldos del panel (`controlngr_AAAAMMDD_HHMMSS_manual.zip` o `..._programado.zip`) y para los de `respaldar`.

**Respaldo automático en Linux con cron** (alternativa al panel; todos los días a las 11 p. m.):

```bash
crontab -e
```

Agregue al final esta línea y guarde:

```
0 23 * * * cd /opt/controlngr/ControlNGR_Backend-v2 && sh scripts/respaldar.sh >> respaldos/respaldo.log 2>&1
```

---

## 9. Problemas frecuentes

| Problema | Solución |
|---|---|
| `no configuration file provided` | No está en la carpeta correcta. Entre a `ControlNGR_Backend-v2` |
| `port is already allocated` (puerto 80 o 443 ocupado) | Otro programa usa el puerto (Apache/XAMPP, IIS). Deténgalo, o cambie `APP_PORT`/`HTTPS_PORT` en `.env` (ej. `HTTPS_PORT=8443`) y entre por `https://IP:8443` |
| `required variable DB_PASSWORD is missing` | Falta el archivo `.env` o la línea `DB_PASSWORD` |
| La construcción se queda detenida o falla con `rpc error ... EOF` | Falta memoria. En Windows asigne más memoria a Docker o construya una parte a la vez (paso B4) |
| La página no carga (`ERR_EMPTY_RESPONSE`) | Ejecute `docker compose down` y luego `docker compose up -d` (sin `-v`) |
| Todos aparecen "fuera de red" | Ver paso 6. Si la IP es `172.x.x.x`, el servidor debe ser Linux |
| Cambié `DB_PASSWORD` y ahora el backend no inicia | La base guarda la contraseña de la primera instalación. Vuelva a poner la contraseña original en `.env` |
| "La conexión no es privada" / no enciende la cámara | Falta importar `ca.crt` en esa PC (paso 4.5), o se entró por `http://` o por una IP distinta a `SERVIDOR_IP`. Ejecute el script de encendido: muestra la IP correcta y renueva el certificado |
| Otra PC no abre el enlace del servidor | Revise el firewall de Windows (paso B5) y que la PC esté en la misma red. `docker compose logs frontend` muestra el enlace configurado |
| "Permiso de cámara denegado" | Clic en el candado de la barra de direcciones → Cámara → Permitir, y recargar |
| "El rostro no coincide" | Mejorar la iluminación y mirar de frente. Si persiste, restablecer el rostro en Empleados y registrarlo de nuevo |
| "Este rostro ya está registrado para otro colaborador" | El mismo rostro no puede estar en dos cuentas. Revise en Empleados a quién pertenece |
| Olvidé la contraseña de admin | Ver abajo "Restablecer la contraseña de admin" |

### Restablecer la contraseña de admin

Vuelve a dejarla en `$.4dmin2026` y el sistema pedirá cambiarla al ingresar. Desde la carpeta `ControlNGR_Backend-v2` (igual en Linux y Windows):

```
docker cp scripts/restablecer-admin.sql controlngr-db:/tmp/restablecer-admin.sql
docker exec controlngr-db sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" controlngr < /tmp/restablecer-admin.sql && rm /tmp/restablecer-admin.sql'
```

Para más detalle técnico, ver el `README.md` del repositorio.
