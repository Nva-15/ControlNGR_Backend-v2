# Control NGR - Iniciar el sistema con la IP de este equipo
#
# Uso (desde la carpeta ControlNGR_Backend-v2):
#   powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1 -Construir          (despues de un git pull)
#   powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1 -Ip 10.92.104.20    (forzar una IP)
#
# 1. Detecta la IP de red de este equipo (la de la conexion con salida a la red, no 127.0.0.1).
# 2. La guarda como SERVIDOR_IP en .env: el certificado HTTPS se emite para esa IP.
# 3. Levanta el sistema y muestra el enlace que se comparte con los usuarios: https://IP-DEL-EQUIPO
#
# Si la IP cambia, el certificado se renueva solo; no hace falta volver a importar ca.crt en las PCs.
# Se recomienda que TI fije la IP del equipo (IP estatica o reserva DHCP) para que el enlace no cambie.

param(
    [string]$Ip = '',
    [switch]$Construir,
    [switch]$SoloDetectar
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

function Test-IpValida([string]$valor) {
    # Cuatro numeros de 0 a 255 (IPAddress.TryParse acepta formas abreviadas como "10.92.104")
    if ($valor -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { return $false }
    $ip = $null
    if (-not [System.Net.IPAddress]::TryParse($valor, [ref]$ip)) { return $false }
    if ($ip.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { return $false }
    return -not ($valor -like '127.*' -or $valor -like '169.254.*' -or $valor -eq '0.0.0.0')
}

# IP de la interfaz por la que este equipo sale a la red (la ruta por defecto de menor metrica).
# Las interfaces internas de Docker/WSL (vEthernet) no tienen ruta por defecto y quedan fuera.
function Get-IpDelEquipo {
    if (Get-Command Get-NetRoute -ErrorAction SilentlyContinue) {
        $rutas = @(Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
                   Sort-Object { [int]$_.RouteMetric + [int]$_.InterfaceMetric })
        foreach ($ruta in $rutas) {
            $direcciones = @(Get-NetIPAddress -InterfaceIndex $ruta.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue)
            foreach ($d in $direcciones) {
                if (Test-IpValida $d.IPAddress) { return $d.IPAddress }
            }
        }
    }
    # Alternativa: la IP de origen que usaria el equipo para salir a la red (no se envia ningun dato)
    try {
        $socket = New-Object System.Net.Sockets.Socket([System.Net.Sockets.AddressFamily]::InterNetwork,
                                                       [System.Net.Sockets.SocketType]::Dgram,
                                                       [System.Net.Sockets.ProtocolType]::Udp)
        $socket.Connect('8.8.8.8', 53)
        $ip = $socket.LocalEndPoint.Address.ToString()
        $socket.Close()
        if (Test-IpValida $ip) { return $ip }
    } catch { }
    return $null
}

# Otras IPv4 del equipo (solo informativo: por si hay mas de una red o una VPN)
function Get-OtrasIp([string]$principal) {
    if (-not (Get-Command Get-NetIPAddress -ErrorAction SilentlyContinue)) { return @() }
    return @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
             Where-Object { (Test-IpValida $_.IPAddress) -and $_.IPAddress -ne $principal -and $_.InterfaceAlias -notlike 'vEthernet*' } |
             ForEach-Object { "$($_.IPAddress)  ($($_.InterfaceAlias))" })
}

function Get-ValorEnv([string[]]$lineas, [string]$clave) {
    foreach ($l in $lineas) {
        if ($l -match "^\s*$clave\s*=(.*)$") { return $Matches[1].Trim() }
    }
    return ''
}

# ---------- 1. IP del equipo ----------
if ($Ip) {
    if (-not (Test-IpValida $Ip)) { throw "La IP '$Ip' no es valida (use una IPv4 de la red, no 127.0.0.1)" }
    $ipEquipo = $Ip
    Write-Host "IP indicada: $ipEquipo"
} else {
    $ipEquipo = Get-IpDelEquipo
    if (-not $ipEquipo) {
        throw 'No se pudo detectar la IP de red de este equipo. Revise que este conectado a la red o indiquela con -Ip 10.x.x.x'
    }
    Write-Host "IP de este equipo: $ipEquipo" -ForegroundColor Cyan
    $otras = Get-OtrasIp $ipEquipo
    if ($otras.Count -gt 0) {
        Write-Host 'Otras IP de este equipo (si la correcta es otra, use -Ip):'
        $otras | ForEach-Object { Write-Host "   $_" }
    }
}
if ($SoloDetectar) { return }

# ---------- 2. Guardar SERVIDOR_IP en .env ----------
$rutaEnv = Join-Path (Get-Location) '.env'
if (-not (Test-Path $rutaEnv)) { throw 'No existe el archivo .env. Copie .env.example como .env y complete las claves (ver GUIA_INSTALACION.md)' }

$lineas = [System.IO.File]::ReadAllLines($rutaEnv)
$anterior = Get-ValorEnv $lineas 'SERVIDOR_IP'
if ($anterior -ne $ipEquipo) {
    $nuevas = New-Object System.Collections.Generic.List[string]
    $reemplazada = $false
    foreach ($l in $lineas) {
        if ($l -match '^\s*SERVIDOR_IP\s*=') {
            if (-not $reemplazada) { $nuevas.Add("SERVIDOR_IP=$ipEquipo"); $reemplazada = $true }
        } else {
            $nuevas.Add($l)
        }
    }
    if (-not $reemplazada) { $nuevas.Add("SERVIDOR_IP=$ipEquipo") }
    # UTF-8 sin BOM: Docker Compose no reconoce la primera linea si el archivo tiene BOM
    [System.IO.File]::WriteAllLines($rutaEnv, $nuevas, (New-Object System.Text.UTF8Encoding($false)))
    if ($anterior) {
        Write-Host "SERVIDOR_IP cambio de $anterior a $ipEquipo (el certificado HTTPS se renueva solo)" -ForegroundColor Yellow
    } else {
        Write-Host "SERVIDOR_IP=$ipEquipo guardada en .env"
    }
} else {
    Write-Host 'SERVIDOR_IP ya estaba configurada con esta IP'
}

# ---------- 3. Levantar el sistema ----------
if ($Construir) {
    # Una parte a la vez: menos memoria en equipos con poca RAM para Docker
    Write-Host 'Construyendo el backend...'
    docker compose build backend
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la construccion del backend' }
    Write-Host 'Construyendo el frontend...'
    docker compose build frontend
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la construccion del frontend' }
}
Write-Host 'Levantando el sistema...'
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'No se pudo levantar el sistema. Esta abierto Docker Desktop?' }
# La carpeta de respaldos debe poder escribirla el usuario del backend
$ErrorActionPreference = 'Continue'
try { docker exec -u root controlngr-backend chown app:app /app/data/respaldos 2>&1 | Out-Null } catch { }
$ErrorActionPreference = 'Stop'

# ---------- 4. Comprobar y mostrar el enlace ----------
$puerto = Get-ValorEnv ([System.IO.File]::ReadAllLines($rutaEnv)) 'HTTPS_PORT'
if (-not $puerto) { $puerto = '443' }
$enlace = if ($puerto -eq '443') { "https://$ipEquipo" } else { "https://${ipEquipo}:$puerto" }

$responde = $false
for ($i = 0; $i -lt 30 -and -not $responde; $i++) {
    try {
        $cliente = New-Object System.Net.Sockets.TcpClient
        $intento = $cliente.BeginConnect($ipEquipo, [int]$puerto, $null, $null)
        if ($intento.AsyncWaitHandle.WaitOne(1000) -and $cliente.Connected) { $responde = $true }
        $cliente.Close()
    } catch { }
    if (-not $responde) { Start-Sleep -Seconds 2 }
}

Write-Host ''
if ($responde) {
    Write-Host "Sistema disponible en:  $enlace" -ForegroundColor Green
} else {
    Write-Host "El sistema aun no responde en $enlace. Espere un minuto y revise: docker compose logs frontend" -ForegroundColor Yellow
}
Write-Host 'Comparta ese enlace con los usuarios (no localhost ni 127.0.0.1).'
Write-Host 'Cada PC debe tener importado una vez certs\ca.crt (ver GUIA_INSTALACION.md, paso 4.5).'
Write-Host "Si otra PC no abre el enlace, permita el puerto $puerto en el Firewall de Windows de este equipo."
