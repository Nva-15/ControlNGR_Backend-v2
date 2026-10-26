# Control NGR - Arranque sin iniciar sesion y acceso remoto por SSH (equipo de planta con Ubuntu en WSL)
#
# Problema que resuelve: Ubuntu (y con el Docker y Control NGR) solo arrancaba cuando alguien iniciaba
# sesion en Windows, y no habia forma de entrar al equipo desde la red para actualizar.
#
# Uso (PowerShell como ADMINISTRADOR, con el mismo usuario de Windows que instalo Ubuntu):
#   powershell -ExecutionPolicy Bypass -File .\scripts\wsl\4-acceso-remoto.ps1
#   (agregue -EscritorioRemoto para activar tambien el Escritorio remoto de Windows)
#
# Hace:
#   1. Instala y activa el servidor SSH dentro de Ubuntu (arranca solo con Ubuntu).
#   2. Permite el puerto 22 en el firewall de Windows y en el de WSL (Hyper-V).
#   3. Cambia la tarea "Control NGR - WSL" para que Ubuntu arranque al ENCENDER el equipo, aunque
#      nadie inicie sesion (pide la contrasena de Windows de este usuario una sola vez).
#   4. Opcional: activa el Escritorio remoto de Windows.
# No toca Docker, la base de datos ni el sistema.

param(
    [string]$Distro = 'Ubuntu-24.04',
    [switch]$EscritorioRemoto
)

$ErrorActionPreference = 'Stop'

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Ejecute este script en PowerShell como administrador (clic derecho en Inicio -> Terminal (Administrador)).'
}
$distros = (wsl.exe --list --quiet) -replace "`0", '' | ForEach-Object { $_.Trim() } | Where-Object { $_ }
if ($distros -notcontains $Distro) {
    throw "No se encontro la distribucion $Distro de WSL. Distribuciones instaladas: $($distros -join ', ')"
}

# ---------- 1. SSH dentro de Ubuntu ----------
Write-Host '1/4 Instalando el servidor SSH en Ubuntu...'
$ssh = Get-Service sshd -ErrorAction SilentlyContinue
if ($ssh -and $ssh.Status -eq 'Running') {
    Write-Host '   AVISO: Windows ya tiene su propio servidor SSH (sshd) en el puerto 22.' -ForegroundColor Yellow
    Write-Host '   Con la red mirrored ambos usarian el mismo puerto. Detengalo (Stop-Service sshd; Set-Service sshd -StartupType Disabled)' -ForegroundColor Yellow
    Write-Host '   o pida configurar SSH de Ubuntu en otro puerto. El resto del script continua.' -ForegroundColor Yellow
}
wsl.exe -d $Distro -u root -- sh -c 'apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openssh-server && systemctl enable --now ssh'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar o activar SSH en Ubuntu (revise la conexion a internet del equipo).' }
$usuarioLinux = (wsl.exe -d $Distro -- whoami).Trim()

# ---------- 2. Firewall ----------
Write-Host '2/4 Permitiendo el puerto 22 en el firewall...'
$wslVm = '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}'
Remove-NetFirewallHyperVRule -Name 'ControlNGR-SSH' -ErrorAction SilentlyContinue
New-NetFirewallHyperVRule -Name 'ControlNGR-SSH' -DisplayName 'Control NGR (SSH)' -Direction Inbound `
    -VMCreatorId $wslVm -Protocol TCP -LocalPorts 22 | Out-Null
Remove-NetFirewallRule -DisplayName 'Control NGR (SSH)' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Control NGR (SSH)' -Direction Inbound -Protocol TCP -LocalPort 22 -Action Allow | Out-Null

# ---------- 3. Arranque al encender el equipo ----------
Write-Host '3/4 Configurando el arranque de Ubuntu al encender el equipo (sin iniciar sesion)...'
$usuario = "$env:USERDOMAIN\$env:USERNAME"
$credencial = Get-Credential -UserName $usuario -Message 'Contrasena de Windows de este usuario (para que la tarea corra sin iniciar sesion)'
$clave = $credencial.GetNetworkCredential().Password
$accion = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -WindowStyle Hidden -Command `"wsl.exe -d $Distro --exec sleep infinity`""
$disparadores = @(
    (New-ScheduledTaskTrigger -AtStartup),
    (New-ScheduledTaskTrigger -AtLogOn -User $usuario)
)
$ajustes = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName 'Control NGR - WSL' -Action $accion -Trigger $disparadores -Settings $ajustes `
    -User $usuario -Password $clave `
    -Description 'Mantiene encendido Ubuntu (WSL) para que Docker, Control NGR y SSH funcionen, aunque nadie inicie sesion.' -Force | Out-Null
$clave = $null

# ---------- 4. Escritorio remoto (opcional) ----------
if ($EscritorioRemoto) {
    Write-Host '4/4 Activando el Escritorio remoto de Windows...'
    Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server' -Name fDenyTSConnections -Value 0
    Enable-NetFirewallRule -DisplayGroup 'Escritorio remoto' -ErrorAction SilentlyContinue
    Enable-NetFirewallRule -DisplayGroup 'Remote Desktop' -ErrorAction SilentlyContinue
} else {
    Write-Host '4/4 Escritorio remoto: sin cambios (use -EscritorioRemoto para activarlo).'
}

# IP de la tarjeta con salida a la red (la que usan las demas PCs)
$ip = (Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway } | Select-Object -First 1).IPv4Address.IPAddress
Write-Host ''
Write-Host 'Listo.' -ForegroundColor Green
Write-Host "Desde otra PC de la red o la VPN:   ssh $usuarioLinux@$ip"
Write-Host '(pide la contrasena de Ubuntu). Ubuntu, Docker y Control NGR arrancan ahora al encender el equipo.'
Write-Host 'Si cambia la contrasena de Windows de este usuario, vuelva a ejecutar este script.'
