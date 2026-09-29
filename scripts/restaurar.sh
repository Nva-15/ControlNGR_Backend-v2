#!/bin/sh
# Control NGR - Restaurar un respaldo en Linux
#
# Uso (desde la carpeta ControlNGR_Backend-v2, con el .env ya creado):
#   sh scripts/restaurar.sh respaldos/controlngr_20260927_1830.tar.gz
#   sh scripts/restaurar.sh respaldos/controlngr_20260927_183000_manual.zip   (respaldo del panel admin)
#
# ATENCION: reemplaza la base de datos, fotos y evidencias actuales por las del respaldo.
set -e
cd "$(dirname "$0")/.."

RESPALDO="$1"
[ -f "$RESPALDO" ] || { echo "Uso: sh scripts/restaurar.sh ARCHIVO.tar.gz|ARCHIVO.zip"; exit 1; }
printf "Se reemplazaran TODOS los datos actuales por los del respaldo. Escriba SI para continuar: "
read CONFIRMAR
[ "$CONFIRMAR" = "SI" ] || { echo "Cancelado."; exit 1; }

TEMP=$(mktemp -d)
case "$RESPALDO" in
  *.zip)
    if command -v unzip >/dev/null 2>&1; then unzip -q "$RESPALDO" -d "$TEMP"
    elif command -v python3 >/dev/null 2>&1; then python3 -m zipfile -e "$RESPALDO" "$TEMP"
    else echo "Instale unzip para abrir el respaldo (sudo apt install unzip)"; exit 1; fi ;;
  *) tar -xzf "$RESPALDO" -C "$TEMP" ;;
esac
[ -f "$TEMP/controlngr.sql" ] || { echo "El respaldo no contiene controlngr.sql"; exit 1; }

echo "1/4 Encendiendo el sistema..."
docker compose up -d
docker compose stop backend >/dev/null

echo "2/4 Esperando la base de datos..."
for i in $(seq 1 30); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' controlngr-db)" = "healthy" ] && break
  sleep 3
done
[ "$(docker inspect -f '{{.State.Health.Status}}' controlngr-db)" = "healthy" ] || { echo "La base de datos no esta lista. Revise: docker compose logs db"; exit 1; }

echo "3/4 Restaurando base de datos..."
docker cp -q "$TEMP/controlngr.sql" controlngr-db:/tmp/controlngr.sql
docker exec controlngr-db sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS controlngr" && mysql -u root -p"$MYSQL_ROOT_PASSWORD" < /tmp/controlngr.sql && rm -f /tmp/controlngr.sql' 2>/dev/null \
  || { echo "No se pudo restaurar la base de datos"; exit 1; }

echo "4/4 Restaurando fotos y evidencias..."
[ -d "$TEMP/img" ] && docker cp -q "$TEMP/img/." controlngr-backend:/app/data/img/
[ -d "$TEMP/evidencias" ] && docker cp -q "$TEMP/evidencias/." controlngr-backend:/app/data/evidencias/

docker compose start backend >/dev/null
# Los archivos copiados deben pertenecer al usuario del backend
docker exec -u root controlngr-backend chown -R app:app /app/data >/dev/null 2>&1 || true
rm -rf "$TEMP"
echo ""
echo "Restauracion completa. Espere un minuto y abra la web."
