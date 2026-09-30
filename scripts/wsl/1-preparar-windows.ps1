# Control NGR - Preparar Windows para ejecutar Docker dentro de WSL2 con red "mirrored"
#
# Con Docker Desktop, el servidor ve a todas las PCs con la IP 172.28.0.1 y la validacion
# de red para marcar no funciona. Con Docker instalado dentro de Ubuntu (WSL2) en modo de red
# "mirrored", Ubuntu comparte la red de Windows y el sistema ve la IP real de cada PC.
#
# Uso (PowerShell como ADMINISTRADOR, con el mismo usuario de Windows que dejara la sesion iniciada):
#   powershell -ExecutionPolicy Bypass -File .\scripts\wsl\1-preparar-windows.ps1
#
# Hace:
#   1. Actualiza WSL.
#   2. Activa networkingMode=mirrored en %USERPROFILE%\.wslconfig (guarda copia .wslconfig.bak).
#   3. Permite en el firewall (Windows y Hyper-V) los puertos 80 y 443 (o 8088 y 8443 si otro programa
#      de Windows usa 80/443) y 8099 para la prueba de IP.
#   4. Crea la tarea programada "Control NGR - WSL" que mantiene Ubuntu encendido al iniciar sesion.
#   5. Reinicia WSL para aplicar el cambio.
# No borra nada ni toca Docker Desktop ni sus datos.

param(
    [string]$Distro = 'Ubuntu-24.04'
)

$ErrorActionPreference = 'Stop'

# ---------- Requisitos ----------
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Ejecute este script en PowerShell como administrador (clic derecho en Inicio -> Terminal (Administrador)).'
}
$build = [Environment]::OSVersion.Version.Build
if ($build -lt 22621) {
    throw "La red mirrored de WSL requiere Windows 11 22H2 o posterior (compilacion 22621+). Este equipo tiene la compilacion $build."
}
if (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue) {
    Write-Host 'AVISO: Docker Desktop esta abierto. Antes de encender el sistema en Ubuntu, apague el sistema en Docker Desktop' -ForegroundColor Yellow
    Write-Host '       (docker compose down, SIN -v) y cierre Docker Desktop: ambos usan los puertos 80 y 443.' -ForegroundColor Yellow
}

# ---------- 1. WSL ----------
Write-Host '1/5 Actualizando WSL...'
wsl --update
if ($LASTEXITCODE -ne 0) { Write-Host 'No se pudo actualizar WSL (se continua con la version instalada).' -ForegroundColor Yellow }

# ---------- 2. .wslconfig ----------
Write-Host '2/5 Configurando la red mirrored...'
$rutaConfig = Join-Path $env:USERPROFILE '.wslconfig'
$lineas = @()
if (Test-Path $rutaConfig) {
    Copy-Item $rutaConfig "$rutaConfig.bak" -Force
    $lineas = @(Get-Content $rutaConfig)
}
$lineas = @($lineas | Where-Object { $_ -notmatch '^\s*networkingMode\s*=' })
$seccion = [Array]::FindIndex([string[]]$lineas, [Predicate[string]]{ param($l) $l -match '^\s*\[wsl2\]\s*$' })
if ($seccion -ge 0) {
    $nuevas = New-Object System.Collections.Generic.List[string]
    $nuevas.AddRange([string[]]$lineas[0..$seccion])
    $nuevas.Add('networkingMode=mirrored')
    if ($seccion + 1 -lt $lineas.Count) { $nuevas.AddRange([string[]]$lineas[($seccion + 1)..($lineas.Count - 1)]) }
    $lineas = $nuevas.ToArray()
} else {
    $lineas = @('[wsl2]', 'networkingMode=mirrored') + $lineas
}
[System.IO.File]::WriteAllLines($rutaConfig, [string[]]$lineas)
Write-Host "   $rutaConfig"

# ---------- 3. Firewall ----------
Write-Host '3/5 Abriendo los puertos en el firewall...'
# Identificador fijo de WSL para el firewall de Hyper-V
$wslVm = '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}'
# Se vuelve a crear para que una instalacion anterior tambien quede con todos los puertos
Remove-NetFirewallHyperVRule -Name 'ControlNGR-WSL' -ErrorAction SilentlyContinue
New-NetFirewallHyperVRule -Name 'ControlNGR-WSL' -DisplayName 'Control NGR (WSL)' -Direction Inbound `
    -VMCreatorId $wslVm -Protocol TCP -LocalPorts 80, 443, 8088, 8443, 8099 | Out-Null
Remove-NetFirewallRule -DisplayName 'Control NGR (web)' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Control NGR (web)' -Direction Inbound -Protocol TCP -LocalPort 80, 443, 8088, 8443 -Action Allow | Out-Null
if (-not (Get-NetFirewallRule -DisplayName 'Control NGR (prueba de IP)' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName 'Control NGR (prueba de IP)' -Direction Inbound -Protocol TCP -LocalPort 8099 -Action Allow | Out-Null
}

# ---------- 4. Tarea programada ----------
Write-Host '4/5 Creando la tarea que mantiene Ubuntu encendido al iniciar sesion...'
$usuario = "$env:USERDOMAIN\$env:USERNAME"
$accion = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -WindowStyle Hidden -Command `"wsl.exe -d $Distro --exec sleep infinity`""
$disparador = New-ScheduledTaskTrigger -AtLogOn -User $usuario
$ajustes = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName 'Control NGR - WSL' -Action $accion -Trigger $disparador -Settings $ajustes `
    -User $usuario -Description 'Mantiene encendido Ubuntu (WSL) para que Docker y Control NGR funcionen.' -Force | Out-Null

# ---------- 5. Aplicar ----------
Write-Host '5/5 Reiniciando WSL para aplicar la red mirrored...'
wsl --shutdown

$distros = (wsl.exe --list --quiet) -replace "`0", '' | Where-Object { $_.Trim() }
Write-Host ''
Write-Host 'Windows listo.' -ForegroundColor Green
if ($distros -notcontains $Distro) {
    Write-Host "Siguiente paso: instalar Ubuntu con   wsl --install -d $Distro   (le pedira crear un usuario y contrasena de Linux)."
} else {
    Write-Host "Siguiente paso: abrir Ubuntu ($Distro) y seguir la guia (instalar Docker con scripts/wsl/2-instalar-docker.sh)."
}
