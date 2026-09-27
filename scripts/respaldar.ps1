# Control NGR - Respaldo completo (base de datos + fotos + evidencias)
#
# Uso (desde la carpeta ControlNGR_Backend-v2, con el sistema encendido):
#   powershell -ExecutionPolicy Bypass -File .\scripts\respaldar.ps1
#
# Genera respaldos\controlngr_AAAAMMDD_HHMM.zip. Ese archivo contiene datos personales:
# guardelo en un lugar seguro y NUNCA lo suba a GitHub.

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

$fecha   = Get-Date -Format 'yyyyMMdd_HHmm'
$destino = Join-Path (Get-Location) "respaldos\controlngr_$fecha"
New-Item -ItemType Directory -Force -Path $destino | Out-Null

Write-Host '1/3 Exportando base de datos...'
docker exec controlngr-db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --databases controlngr > /tmp/controlngr.sql'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo exportar la base de datos. Esta encendido el sistema? (docker compose up -d)' }
docker cp controlngr-db:/tmp/controlngr.sql "$destino\controlngr.sql"
docker exec controlngr-db rm -f /tmp/controlngr.sql | Out-Null

Write-Host '2/3 Copiando fotos de perfil...'
docker cp controlngr-backend:/app/data/img "$destino\img"
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron copiar las fotos' }

Write-Host '3/3 Copiando evidencias...'
docker cp controlngr-backend:/app/data/evidencias "$destino\evidencias"
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron copiar las evidencias' }

$zip = "$destino.zip"
Compress-Archive -Path "$destino\*" -DestinationPath $zip -Force
Remove-Item -Recurse -Force $destino

Write-Host ''
Write-Host "Respaldo listo: $zip" -ForegroundColor Green
Write-Host 'Para otro equipo copie este .zip y tambien su archivo .env (no estan en GitHub).'
