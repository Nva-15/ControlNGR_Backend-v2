# Control NGR - Conectarse y actualizar el servidor de planta por SSH desde otra PC (Windows)
#
# Uso (PowerShell normal, NO hace falta administrador; desde la carpeta ControlNGR_Backend-v2 de su PC):
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1                       actualizar (git pull + construir)
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -Conectar             abrir la consola de Ubuntu
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -Estado               ver contenedores y ultimas lineas del backend
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -Levantar             encender el sistema si no responde (tras un reinicio)
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -RepararArranque      volver a crear la tarea que enciende Ubuntu al arrancar
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -CrearAccesosDirectos iconos en el escritorio
#
# La primera vez crea una clave SSH en esta PC y la instala en el servidor (pide UNA vez la
# contrasena de Ubuntu). Desde entonces se conecta solo, sin contrasena.
# La clave privada queda en %USERPROFILE%\.ssh\controlngr_ed25519 de esta PC: no la copie ni la suba a git.
#
# -Levantar: si Ubuntu (WSL) no esta encendido en el servidor, lanza la tarea "Control NGR - WSL al encender"
# (si falta o falla, la vuelve a crear: pide la contrasena de Windows de -UsuarioWindows), espera el SSH,
# levanta los contenedores y espera la web. Requiere que su usuario de Windows sea administrador del servidor
# (si no, use -CuentaRemota DOMINIO\usuario y le pedira esa contrasena).

param(
    [string]$Servidor = '10.92.104.14',
    [string]$Usuario = 'administrador',
    [string]$Ruta = '~/Control_NGR',
    [switch]$Conectar,
    [switch]$Estado,
    [switch]$CrearAccesosDirectos,
    [switch]$Levantar,
    [switch]$RepararArranque,
    [string]$UsuarioWindows = 'Administrador',
    [string]$CuentaRemota = '',
    [string]$Distro = 'Ubuntu-24.04',
    [switch]$Si
)

$ErrorActionPreference = 'Stop'
$destino = "$Usuario@$Servidor"
# Clave propia de Control NGR: no toca otras claves SSH que ya tenga esta PC
$carpeta = Join-Path $env:USERPROFILE '.ssh'
$clave = Join-Path $carpeta 'controlngr_ed25519'
$opciones = @('-i', $clave, '-o', 'IdentitiesOnly=yes', '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=10', '-o', 'ServerAliveInterval=30')

if (-not (Get-Command ssh.exe -ErrorAction SilentlyContinue)) {
    throw 'Falta el cliente SSH de Windows. Instalelo (PowerShell como administrador): Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0'
}

if ($CrearAccesosDirectos) {
    $escritorio = [Environment]::GetFolderPath('Desktop')
    $shell = New-Object -ComObject WScript.Shell
    $accesos = @(
        @{ Nombre = 'Control NGR - Actualizar planta'; Extra = '' },
        @{ Nombre = 'Control NGR - Consola planta'; Extra = ' -Conectar' },
        @{ Nombre = 'Control NGR - Estado planta'; Extra = ' -Estado' },
        @{ Nombre = 'Control NGR - Levantar planta'; Extra = ' -Levantar' }
    )
    foreach ($a in $accesos) {
        $lnk = $shell.CreateShortcut((Join-Path $escritorio "$($a.Nombre).lnk"))
        $lnk.TargetPath = 'powershell.exe'
        $lnk.Arguments = "-NoExit -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Servidor $Servidor -Usuario $Usuario$($a.Extra)"
        $lnk.WorkingDirectory = $PSScriptRoot
        $lnk.IconLocation = 'powershell.exe,0'
        $lnk.Save()
        Write-Host "Creado en el escritorio: $($a.Nombre)"
    }
    exit 0
}

function Puerto-Abierto([int]$Puerto, [int]$Ms = 3000) {
    $tcp = New-Object Net.Sockets.TcpClient
    try {
        $intento = $tcp.BeginConnect($Servidor, $Puerto, $null, $null)
        return ($intento.AsyncWaitHandle.WaitOne($Ms) -and $tcp.Connected)
    } catch { return $false } finally { $tcp.Close() }
}

function Esperar-Puerto([int]$Puerto, [int]$Segundos, [string]$Que) {
    Write-Host "   Esperando $Que (hasta $Segundos s)..." -NoNewline
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        if (Puerto-Abierto $Puerto 2000) { Write-Host ' listo.' -ForegroundColor Green; return $true }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 5
    }
    Write-Host ' sin respuesta.' -ForegroundColor Yellow
    return $false
}

# ---------- Tarea de Windows que enciende Ubuntu al arrancar el servidor ----------
$tareaArranque = 'Control NGR - WSL al encender'
$cuenta = @()
if ($CuentaRemota -and ($Levantar -or $RepararArranque)) {
    # schtasks pediria la contrasena en cada llamada (y con la salida capturada no se veria): se pide una vez
    $segura = Read-Host "Contrasena de Windows de $CuentaRemota (administrador del servidor)" -AsSecureString
    $plano = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura))
    $cuenta = @('/u', $CuentaRemota, '/p', $plano)
}

function Tareas([string[]]$Argumentos) {
    $ErrorActionPreference = 'Continue'
    $salida = & schtasks.exe @Argumentos 2>&1 | Out-String
    return @{ Codigo = $LASTEXITCODE; Texto = $salida }
}

function Estado-Tarea {
    $r = Tareas (@('/query', '/s', $Servidor) + $cuenta + @('/tn', $tareaArranque, '/v', '/fo', 'list'))
    if ($r.Codigo -ne 0) {
        if ($r.Texto -match 'denegado|denied') {
            throw "Sin permiso para administrar las tareas de $Servidor. Use -CuentaRemota DOMINIO\usuario (un administrador del servidor)."
        }
        return 'no existe'
    }
    if ($r.Texto -match '(?m)^(Estado|Status):\s+.*(ejecuci|Running)') { return 'en ejecucion' }
    return 'detenida'
}

function Crear-TareaArranque {
    Write-Host "   Creando la tarea '$tareaArranque' (corre al encender, sin iniciar sesion, y reintenta si WSL falla)."
    Write-Host "   Escriba la contrasena de Windows de '$UsuarioWindows' en el servidor:"
    # Se pasa codificado para evitar problemas de comillas al crearla desde otra PC; si WSL falla, anota el error en C:\ngr-wsl.log
    $cmd = "for(;;){wsl -d $Distro -e sleep infinity *>> C:\ngr-wsl.log;sleep 20}"
    $enc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($cmd))
    $ErrorActionPreference = 'Continue'
    & schtasks.exe /create /s $Servidor @cuenta /tn $tareaArranque /ru $UsuarioWindows /rp * /sc onstart /delay 0001:00 /rl HIGHEST /f /tr "powershell -NoP -W Hidden -Enc $enc"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la tarea (contrasena o usuario incorrectos: use -UsuarioWindows).' }
}

function Encender-Ubuntu {
    if (-not (Puerto-Abierto 445)) {
        throw "Windows del servidor $Servidor no responde (puerto 445). Puede estar apagado, reiniciandose (espere 3 a 5 minutos) o fuera de la red/VPN."
    }
    $estado = Estado-Tarea
    Write-Host "   Tarea '$tareaArranque': $estado"
    if ($estado -eq 'no existe') { Crear-TareaArranque }
    if ($estado -eq 'en ejecucion') {
        # Corre pero no hay SSH: se reinicia la tarea
        Tareas (@('/end', '/s', $Servidor) + $cuenta + @('/tn', $tareaArranque)) | Out-Null
        Start-Sleep -Seconds 3
    }
    Tareas (@('/run', '/s', $Servidor) + $cuenta + @('/tn', $tareaArranque)) | Out-Null
    if (Esperar-Puerto 22 180 'que Ubuntu encienda el SSH') { return }
    if ($estado -ne 'no existe') {
        Write-Host '   No encendio: se vuelve a crear la tarea por si cambio la contrasena de Windows.' -ForegroundColor Yellow
        Crear-TareaArranque
        Tareas (@('/run', '/s', $Servidor) + $cuenta + @('/tn', $tareaArranque)) | Out-Null
        if (Esperar-Puerto 22 180 'que Ubuntu encienda el SSH') { return }
    }
    $log = "\\$Servidor\c$\ngr-wsl.log"
    if (Test-Path $log) {
        Write-Host 'Ultimos errores de WSL en el servidor:' -ForegroundColor Yellow
        Get-Content $log -Tail 15
    }
    throw "Ubuntu no encendio en $Servidor. Copie el mensaje de arriba al soporte."
}

if ($RepararArranque) {
    if (-not (Puerto-Abierto 445)) { throw "Windows del servidor $Servidor no responde (puerto 445)." }
    Crear-TareaArranque
    Tareas (@('/run', '/s', $Servidor) + $cuenta + @('/tn', $tareaArranque)) | Out-Null
    Write-Host 'Tarea de arranque creada y lanzada.' -ForegroundColor Green
    if (-not (Esperar-Puerto 22 180 'el SSH')) { exit 1 }
    if (-not $Levantar) { exit 0 }
}

# ---------- 1. El servidor responde ----------
if (-not (Puerto-Abierto 22 5000)) {
    if ($Levantar) {
        Write-Host "1/3 Ubuntu no responde en $Servidor`: se enciende." -ForegroundColor Cyan
        Encender-Ubuntu
    } else {
        throw "El servidor $Servidor no responde en el puerto 22. Si se reinicio, use -Levantar (o espere 2 a 3 minutos). Revise tambien la red o la VPN."
    }
} elseif ($Levantar) {
    Write-Host '1/3 Ubuntu ya esta encendido.' -ForegroundColor Green
}

# ---------- 2. Clave SSH (solo la primera vez) ----------
function Acceso-SinClave {
    # En PowerShell 5.1 redirigir el error de un programa con ErrorActionPreference=Stop lanzaria una excepcion
    $ErrorActionPreference = 'Continue'
    & ssh.exe @opciones -o BatchMode=yes $destino exit 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}
if (-not (Acceso-SinClave)) {
    Write-Host 'Primera conexion desde esta PC: se configura el acceso sin contrasena.' -ForegroundColor Cyan
    if (-not (Test-Path "$clave.pub")) {
        New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
        # cmd pasa bien la frase vacia ("") en cualquier version de PowerShell
        cmd /c "ssh-keygen -q -t ed25519 -N `"`" -C controlngr-$env:COMPUTERNAME -f `"$clave`""
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la clave SSH.' }
    }
    Write-Host "Escriba la contrasena de Ubuntu de '$Usuario' (solo esta vez):"
    $OutputEncoding = New-Object Text.ASCIIEncoding
    Get-Content "$clave.pub" | & ssh.exe @opciones $destino 'umask 077; mkdir -p ~/.ssh; tr -d ''\r'' >> ~/.ssh/authorized_keys; sort -u -o ~/.ssh/authorized_keys ~/.ssh/authorized_keys'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la clave en el servidor (contrasena incorrecta o usuario distinto: use -Usuario).' }
    if (-not (Acceso-SinClave)) { throw 'La clave se copio pero el servidor no la acepta. Revise los permisos de ~/.ssh en el servidor (chmod 700 ~/.ssh; chmod 600 ~/.ssh/authorized_keys).' }
    Write-Host 'Listo: las proximas veces se conecta sin contrasena.' -ForegroundColor Green
}

$backend = "$Ruta/ControlNGR_Backend-v2"
$frontend = "$Ruta/ControlNGR_Frontend-v2"

# ---------- 3. Accion ----------
if ($Levantar) {
    Write-Host '2/3 Levantando los contenedores...' -ForegroundColor Cyan
    # Recien encendido, Docker puede tardar unos segundos en aceptar ordenes
    & ssh.exe @opciones $destino "cd $backend && for i in `$(seq 1 24); do docker info >/dev/null 2>&1 && break; sleep 5; done; docker compose up -d && docker compose ps"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron levantar los contenedores (vea el mensaje de arriba).' }
    $ErrorActionPreference = 'Continue'
    $puerto = (& ssh.exe @opciones $destino "cd $backend && sed -n 's/^APP_PORT=//p' .env" 2>$null | Select-Object -First 1)
    $ErrorActionPreference = 'Stop'
    if (-not "$puerto".Trim()) { $puerto = '80' }
    $puerto = [int]"$puerto".Trim()
    Write-Host '3/3 Comprobando la web...' -ForegroundColor Cyan
    if (Esperar-Puerto $puerto 120 "la web en el puerto $puerto") {
        $url = if ($puerto -eq 80) { "http://$Servidor" } else { "http://$Servidor`:$puerto" }
        Write-Host "Sistema arriba: $url (en el navegador use Ctrl + F5)." -ForegroundColor Green
        exit 0
    }
    Write-Host 'Los contenedores estan encendidos pero la web aun no responde. Ultimas lineas del backend:' -ForegroundColor Yellow
    & ssh.exe @opciones $destino "cd $backend && docker compose logs backend --tail 20"
    exit 1
}

if ($Conectar) {
    & ssh.exe @opciones -t $destino "cd $backend && exec bash -l"
    exit $LASTEXITCODE
}
if ($Estado) {
    & ssh.exe @opciones $destino "cd $backend && docker compose ps && echo && docker compose logs backend --tail 15"
    exit $LASTEXITCODE
}

if (-not $Si) {
    Write-Host "Se actualizara Control NGR en $Servidor (git pull de ambos repositorios y reconstruccion)."
    Write-Host 'El sistema se reinicia y deja de responder 1 a 2 minutos: evite las horas de marcacion.' -ForegroundColor Yellow
    if ((Read-Host 'Continuar? (S/N)') -notmatch '^[sS]') { Write-Host 'Cancelado.'; exit 0 }
}
$inicio = Get-Date
& ssh.exe @opciones -t $destino "set -e; cd $frontend && echo '== Frontend ==' && git pull --ff-only && cd $backend && echo '== Backend ==' && git pull --ff-only && sh scripts/iniciar.sh --construir"
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'La actualizacion fallo (vea el mensaje de arriba). El sistema sigue con la version anterior si no llego a construir.' -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host ''
Write-Host "Actualizacion terminada en $([math]::Round(((Get-Date) - $inicio).TotalMinutes, 1)) min. En el navegador use Ctrl + F5." -ForegroundColor Green
