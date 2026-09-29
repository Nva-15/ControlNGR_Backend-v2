#!/bin/sh
# Control NGR - Iniciar el sistema con la IP de este equipo (servidor Linux)
#
# Uso (desde la carpeta ControlNGR_Backend-v2):
#   sh scripts/iniciar.sh                    detecta la IP, la guarda en .env y levanta el sistema
#   sh scripts/iniciar.sh --construir        igual, construyendo antes las imagenes (despues de un git pull)
#   sh scripts/iniciar.sh 10.92.104.20       forzar una IP
set -e
cd "$(dirname "$0")/.."

CONSTRUIR=0
IP=""
for arg in "$@"; do
    case "$arg" in
        --construir) CONSTRUIR=1 ;;
        *) IP="$arg" ;;
    esac
done

ip_valida() {
    echo "$1" | grep -Eq '^([0-9]{1,3}\.){3}[0-9]{1,3}$' || return 1
    for n in $(echo "$1" | tr '.' ' '); do [ "$n" -le 255 ] || return 1; done
    case "$1" in 127.*|169.254.*|0.0.0.0) return 1 ;; esac
}

if [ -z "$IP" ]; then
    # IP de la interfaz con la ruta por defecto (la que sale a la red)
    IP=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}' | head -n 1)
    [ -n "$IP" ] || IP=$(hostname -I 2>/dev/null | awk '{print $1}')
    ip_valida "$IP" || { echo "No se pudo detectar la IP de red de este equipo. Indiquela: sh scripts/iniciar.sh 10.x.x.x"; exit 1; }
    echo "IP de este equipo: $IP"
else
    ip_valida "$IP" || { echo "La IP '$IP' no es valida (use una IPv4 de la red, no 127.0.0.1)"; exit 1; }
    echo "IP indicada: $IP"
fi

[ -f .env ] || { echo "No existe el archivo .env. Copie .env.example como .env y complete las claves (ver GUIA_INSTALACION.md)"; exit 1; }
ANTERIOR=$(grep -E '^[[:space:]]*SERVIDOR_IP[[:space:]]*=' .env | head -n 1 | cut -d= -f2- | tr -d '[:space:]')
if [ "$ANTERIOR" != "$IP" ]; then
    TMP=$(mktemp)
    grep -vE '^[[:space:]]*SERVIDOR_IP[[:space:]]*=' .env > "$TMP" || true
    echo "SERVIDOR_IP=$IP" >> "$TMP"
    cat "$TMP" > .env && rm -f "$TMP"
    if [ -n "$ANTERIOR" ]; then echo "SERVIDOR_IP cambio de $ANTERIOR a $IP (el certificado HTTPS se renueva solo)"
    else echo "SERVIDOR_IP=$IP guardada en .env"; fi
else
    echo "SERVIDOR_IP ya estaba configurada con esta IP"
fi

if [ "$CONSTRUIR" = 1 ]; then
    docker compose build backend
    docker compose build frontend
fi
docker compose up -d
# La carpeta de respaldos debe poder escribirla el usuario del backend
docker exec -u root controlngr-backend chown app:app /app/data/respaldos >/dev/null 2>&1 || true

PUERTO=$(grep -E '^[[:space:]]*HTTPS_PORT[[:space:]]*=' .env | head -n 1 | cut -d= -f2- | tr -d '[:space:]')
[ -n "$PUERTO" ] || PUERTO=443
if [ "$PUERTO" = 443 ]; then ENLACE="https://$IP"; else ENLACE="https://$IP:$PUERTO"; fi
echo ""
echo "Sistema disponible en:  $ENLACE"
echo "Comparta ese enlace con los usuarios (no localhost ni 127.0.0.1)."
echo "Cada PC debe tener importado una vez certs/ca.crt (ver GUIA_INSTALACION.md)."
