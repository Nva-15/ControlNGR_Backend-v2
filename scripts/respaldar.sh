#!/bin/sh
# Control NGR - Respaldo completo (base de datos + fotos + evidencias) en Linux
#
# Uso (desde la carpeta ControlNGR_Backend-v2, con el sistema encendido):
#   sh scripts/respaldar.sh
#
# Genera respaldos/controlngr_AAAAMMDD_HHMM.tar.gz. Contiene datos personales:
# guardelo en un lugar seguro y NUNCA lo suba a GitHub.
set -e
cd "$(dirname "$0")/.."

FECHA=$(date +%Y%m%d_%H%M)
DESTINO="respaldos/controlngr_$FECHA"
mkdir -p "$DESTINO"

echo "1/3 Exportando base de datos..."
docker exec controlngr-db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --databases controlngr > /tmp/controlngr.sql' 2>/dev/null \
  || { echo "No se pudo exportar la base de datos. Esta encendido el sistema? (docker compose up -d)"; rm -rf "$DESTINO"; exit 1; }
docker cp -q controlngr-db:/tmp/controlngr.sql "$DESTINO/controlngr.sql"
docker exec controlngr-db rm -f /tmp/controlngr.sql

echo "2/3 Copiando fotos de perfil..."
docker cp -q controlngr-backend:/app/data/img "$DESTINO/img"

echo "3/3 Copiando evidencias..."
docker cp -q controlngr-backend:/app/data/evidencias "$DESTINO/evidencias"

tar -czf "$DESTINO.tar.gz" -C "$DESTINO" .
rm -rf "$DESTINO"
chmod 600 "$DESTINO.tar.gz"

echo ""
echo "Respaldo listo: $(pwd)/$DESTINO.tar.gz"
