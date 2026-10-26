# Control NGR - Conectarse y actualizar el servidor de planta por SSH desde otra PC (Windows)
#
# Uso (PowerShell normal, NO hace falta administrador; desde la carpeta ControlNGR_Backend-v2 de su PC):
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1                       actualizar (git pull + construir)
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -Conectar             abrir la consola de Ubuntu
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -Estado               ver contenedores y ultimas lineas del backend
#   powershell -ExecutionPolicy Bypass -File .\scripts\remoto\planta.ps1 -CrearAccesosDirectos iconos en el escritorio
#
# La primera vez crea una clave SSH en esta PC y la instala en el servidor (pide UNA vez la
# contrasena de Ubuntu). Desde entonces se conecta solo, sin contrasena.
# La clave privada queda en %USERPROFILE%\.ssh\controlngr_ed25519 de esta PC: no la copie ni la suba a git.

param(
    [string]$Servidor = '10.92.104.14',
    [string]$Usuario = 'administrador',
    [string]$Ruta = '~/Control_NGR',
    [switch]$Conectar,
    [switch]$Estado,
    [switch]$CrearAccesosDirectos,
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
        @{ Nombre = 'Control NGR - Estado planta'; Extra = ' -Estado' }
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

# ---------- 1. El servidor responde ----------
$tcp = New-Object Net.Sockets.TcpClient
try {
    $intento = $tcp.BeginConnect($Servidor, 22, $null, $null)
    if (-not $intento.AsyncWaitHandle.WaitOne(5000) -or -not $tcp.Connected) {
        throw "El servidor $Servidor no responde en el puerto 22. Revise que este conectado a la red o la VPN y que el equipo de planta este encendido (tras un reinicio tarda 2 a 3 minutos)."
    }
} finally { $tcp.Close() }

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
