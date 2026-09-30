# Control NGR en Windows con la IP real de cada PC (Docker dentro de WSL2)

**Para qué:** con Docker Desktop, el servidor ve a todas las PCs con la IP `172.28.0.1`. Como ninguna coincide con los *Segmentos de red*, todos quedan "fuera de red". Esta guía cambia **Docker Desktop** por **Docker instalado dentro de Ubuntu (WSL2) en modo de red "mirrored"**. En ese modo, Ubuntu comparte la red de Windows y el sistema ve la IP real de cada PC. Se hace en el mismo equipo y sin perder datos.

| | |
|---|---|
| Requisitos | Windows 11 **22H2 o posterior**, usuario administrador, internet, 45 minutos |
| Se conserva | Datos (vía respaldo), el archivo `.env` y el certificado (no hay que reimportarlo en las PCs) |
| Vuelta atrás | Siempre posible: Docker Desktop y sus datos no se borran (ver el final) |

> Nunca tenga encendidos a la vez el sistema en Docker Desktop y el sistema en Ubuntu: los dos usan los puertos 80 y 443.

---

## 1. Respaldo y apagado del sistema actual (Docker Desktop)

1. Entre como `admin` → **Panel maestro → Respaldos** → marque *Incluir fotos y evidencias* → **Crear respaldo ahora**. Espere a que quede *Completado*.
2. En PowerShell, descargue los scripts nuevos y apague el sistema de Docker Desktop, **sin `-v`**: con `-v` se borran los datos de Docker Desktop, que son su respaldo para volver atrás.

    ```powershell
    cd D:\Control_NGR\ControlNGR_Frontend-v2
    git pull
    cd D:\Control_NGR\ControlNGR_Backend-v2
    git pull
    docker compose down
    ```

3. En Docker Desktop → ⚙️ **Settings**:
    - **General**: desmarque *Start Docker Desktop when you sign in*.
    - **Resources → WSL integration**: desactive todas las distribuciones → *Apply & restart*.
4. Cierre Docker Desktop: clic derecho en la ballena de la barra de tareas → **Quit Docker Desktop**.

## 2. Preparar Windows

En **PowerShell como administrador**:

```powershell
cd D:\Control_NGR\ControlNGR_Backend-v2
powershell -ExecutionPolicy Bypass -File .\scripts\wsl\1-preparar-windows.ps1
wsl --install -d Ubuntu-24.04
```

- El script activa la red *mirrored* (`%USERPROFILE%\.wslconfig`), abre los puertos 80, 443 y 8099 en el firewall de Windows y de Hyper-V, y crea la tarea **Control NGR - WSL**, que mantiene Ubuntu encendido al iniciar sesión.
- `wsl --install` abre Ubuntu y pide **crear un usuario y una contraseña de Linux**. Anótelos: la contraseña se pide al usar `sudo`.
- Si Ubuntu ya estaba instalado, `wsl --install` lo indica. Ábralo desde el menú Inicio (**Ubuntu 24.04**).

## 3. Instalar Docker y copiar el sistema a Ubuntu

En la ventana de **Ubuntu**:

```bash
sudo sh /mnt/d/Control_NGR/ControlNGR_Backend-v2/scripts/wsl/2-instalar-docker.sh
```

- Si responde *"Se activó systemd"*: en PowerShell ejecute `wsl --shutdown`, vuelva a abrir Ubuntu y repita el comando.
- Al terminar, **cierre Ubuntu y ábralo de nuevo**, para usar `docker` sin `sudo`.

Luego, **sin `sudo`**:

```bash
sh /mnt/d/Control_NGR/ControlNGR_Backend-v2/scripts/wsl/3-copiar-sistema.sh
cd ~/Control_NGR/ControlNGR_Backend-v2
```

El script copia los dos repositorios, con `.env`, `certs` y `respaldos`, al disco de Linux (`~/Control_NGR`), que es más rápido. También los ajusta para que `git pull` funcione en Linux. La carpeta de Windows no se modifica.

- Las claves del certificado (`certs/*.key`) se copian con `sudo`, así que el script pide la contraseña de Ubuntu.
- Si la copia se cortó a medias, termínela con `sh /mnt/d/Control_NGR/ControlNGR_Backend-v2/scripts/wsl/3-copiar-sistema.sh --ajustar`.
- Si `docker` responde *permission denied*, falta cerrar Ubuntu y volver a abrirlo después de instalar Docker. También puede ejecutar `newgrp docker` en la misma ventana.

Compruebe:

```bash
wslinfo --networking-mode    # debe decir: mirrored
ip -4 addr | grep inet       # debe aparecer la IP de la oficina, ej. 10.92.104.14
docker compose version
```

## 4. Prueba de la IP (5 minutos, antes de migrar)

En Ubuntu:

```bash
docker run -d --rm --name prueba-ip -p 8099:80 traefik/whoami
```

Desde **otra PC** (por ejemplo, la suya con VPN), abra en el navegador `http://10.92.104.14:8099` (use la IP del paso 3). Busque la línea **`RemoteAddr:`**:

| Resultado | Qué significa |
|---|---|
| `RemoteAddr: 192.168.113.7:54321` (la IP real de esa PC) | ✅ Funciona. Continúe con el paso 5 |
| `RemoteAddr: 172.x.x.x` o la página no abre | ❌ No funciona en este equipo. Vaya a *Volver atrás* y avise |

Luego, en Ubuntu: `docker stop prueba-ip`

## 5. Encender el sistema y restaurar los datos

En Ubuntu:

```bash
cd ~/Control_NGR/ControlNGR_Backend-v2
sh scripts/iniciar.sh --construir
ls respaldos/
sh scripts/restaurar.sh respaldos/NOMBRE_DEL_RESPALDO.zip
```

- `iniciar.sh --construir` tarda de 10 a 20 minutos la primera vez y muestra el enlace, por ejemplo `https://10.92.104.14`.
- `ls respaldos/` muestra el respaldo del paso 1, por ejemplo `controlngr_20260929_081500_manual.zip`. `restaurar.sh` pide escribir `SI`.
- Las PCs **no** necesitan reimportar el certificado: la carpeta `certs` se copió con su autoridad `ca.crt`.
- Si aparece `failed to bind host port 0.0.0.0:80/tcp: address already in use`, otro programa de Windows usa el puerto 80, por ejemplo IIS o Reporting Services. Con la red *mirrored*, Ubuntu comparte los puertos con Windows. Como el puerto 80 solo redirige a `https://`, muévalo a otro con `sed -i 's/^APP_PORT=.*/APP_PORT=8088/' .env` y ejecute `docker compose up -d`. Si el puerto 443 también está ocupado, use `HTTPS_PORT=8443`: los usuarios entrarán por `https://IP:8443`. El script de Windows ya abre 8088 y 8443 en el firewall.

## 6. Comprobar

1. Desde otra PC abra `https://10.92.104.14` → entre como admin → **Segmentos de red**. Debe mostrar **la IP real de esa PC** y *Dentro de red*.
2. Si antes desactivó `VALIDAR_IP_MARCACION`, vuelva a ponerlo en **true** en *Parámetros*.
3. Con un colaborador: *Inicio* → *Marcar entrada* debe estar habilitado dentro de la red o la VPN.
4. **Reinicio:** reinicie Windows, inicie sesión y espere 1 a 2 minutos. El sistema debe volver solo: la tarea *Control NGR - WSL* mantiene Ubuntu encendido y Docker arranca los contenedores.

## Uso diario (en Ubuntu)

| Para… | Comando |
|---|---|
| Abrir la consola | Menú Inicio → **Ubuntu 24.04**, luego `cd ~/Control_NGR/ControlNGR_Backend-v2` |
| Actualizar | `cd ../ControlNGR_Frontend-v2 && git pull && cd ../ControlNGR_Backend-v2 && git pull && sh scripts/iniciar.sh --construir` |
| Ver estado | `docker compose ps` |
| Ver errores | `docker compose logs backend --tail 50` |
| Apagar | `docker compose down` (**nunca** `-v`) |
| Ver los respaldos desde Windows | Explorador → `\\wsl.localhost\Ubuntu-24.04\home\SU_USUARIO\Control_NGR\ControlNGR_Backend-v2\respaldos` |

- **Git sin volver a escribir credenciales**: use las de Windows ejecutando una vez en Ubuntu:
  `git config --global credential.helper "/mnt/c/Program\ Files/Git/mingw64/bin/git-credential-manager.exe"`
- **Power BI / Workbench**: MySQL sigue en `127.0.0.1:3307` desde el propio servidor. La red *mirrored* comparte `localhost` entre Windows y Ubuntu.
- **Equipo encendido:** el equipo debe quedar encendido y con la sesión de Windows iniciada, igual que con Docker Desktop.

## Volver atrás (Docker Desktop)

En PowerShell:

```powershell
wsl --shutdown
Disable-ScheduledTask -TaskName 'Control NGR - WSL'
```

Abra Docker Desktop, espere a que diga *Engine running* y:

```powershell
cd D:\Control_NGR\ControlNGR_Backend-v2
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

Vuelven los datos que tenía Docker Desktop el día del cambio. Los datos registrados después, en Ubuntu, se traen con un respaldo: créelo desde el panel antes de apagar Ubuntu y restáurelo con `restaurar.ps1`. Para volver a la red normal de WSL, borre la línea `networkingMode=mirrored` de `%USERPROFILE%\.wslconfig`.
