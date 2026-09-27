# Control NGR - Restaurar un respaldo en este equipo
#
# Uso (desde la carpeta ControlNGR_Backend-v2, con el .env ya creado):
#   powershell -ExecutionPolicy Bypass -File .\scripts\restaurar.ps1 -Respaldo D:\respaldos\controlngr_20260927_1830.zip
#
# ATENCION: reemplaza la base de datos, fotos y evidencias actuales por las del respaldo.

param(
    [Parameter(Mandatory = $true)][string]$Respaldo
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Test-Path $Respaldo)) { throw "No existe el archivo $Respaldo" }
$confirmar = Read-Host 'Se reemplazaran TODOS los datos actuales por los del respaldo. Escriba SI para continuar'
if ($confirmar -ne 'SI') { Write-Host 'Cancelado.'; exit 1 }

$temp = Join-Path $env:TEMP ("controlngr_restaurar_" + (Get-Random))
Expand-Archive -Path $Respaldo -DestinationPath $temp -Force
if (-not (Test-Path "$temp\controlngr.sql")) { throw 'El respaldo no contiene controlngr.sql' }

Write-Host '1/4 Encendiendo el sistema...'
docker compose up -d
docker compose stop backend | Out-Null

Write-Host '2/4 Esperando la base de datos...'
for ($i = 0; $i -lt 30; $i++) {
    $estado = docker inspect -f '{{.State.Health.Status}}' controlngr-db
    if ($estado -eq 'healthy') { break }
    Start-Sleep -Seconds 3
}
if ($estado -ne 'healthy') { throw 'La base de datos no esta lista. Revise: docker compose logs db' }

Write-Host '3/4 Restaurando base de datos...'
docker cp "$temp\controlngr.sql" controlngr-db:/tmp/controlngr.sql
docker exec controlngr-db sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS controlngr" && mysql -u root -p"$MYSQL_ROOT_PASSWORD" < /tmp/controlngr.sql && rm -f /tmp/controlngr.sql'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar la base de datos' }

Write-Host '4/4 Restaurando fotos y evidencias...'
if (Test-Path "$temp\img")        { docker cp "$temp\img\." controlngr-backend:/app/data/img/ }
if (Test-Path "$temp\evidencias") { docker cp "$temp\evidencias\." controlngr-backend:/app/data/evidencias/ }

docker compose start backend | Out-Null
Remove-Item -Recurse -Force $temp

Write-Host ''
Write-Host 'Restauracion completa. Espere un minuto y abra http://localhost' -ForegroundColor Green
