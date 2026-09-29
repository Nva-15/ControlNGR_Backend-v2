#!/bin/sh
# Control NGR - Instalar Docker Engine dentro de Ubuntu (WSL2)
#
# Uso (dentro de Ubuntu, directamente desde la carpeta de Windows):
#   sudo sh /mnt/d/Control_NGR/ControlNGR_Backend-v2/scripts/wsl/2-instalar-docker.sh
#
# Requiere haber ejecutado antes scripts/wsl/1-preparar-windows.ps1 en Windows.
set -e

[ "$(id -u)" = 0 ] || { echo "Ejecute con sudo: sudo sh $0"; exit 1; }
USUARIO="${SUDO_USER:-}"

# ---------- systemd (para que Docker arranque solo) ----------
if [ ! -d /run/systemd/system ]; then
    if [ -f /etc/wsl.conf ] && grep -q '^\[boot\]' /etc/wsl.conf; then
        grep -q '^systemd=true' /etc/wsl.conf || sed -i '/^\[boot\]/a systemd=true' /etc/wsl.conf
    else
        printf '\n[boot]\nsystemd=true\n' >> /etc/wsl.conf
    fi
    echo "Se activo systemd. En PowerShell de Windows ejecute:  wsl --shutdown"
    echo "Luego abra Ubuntu de nuevo y vuelva a ejecutar este script."
    exit 0
fi

# ---------- Modo de red ----------
if command -v wslinfo >/dev/null 2>&1; then
    MODO=$(wslinfo --networking-mode 2>/dev/null || true)
    if [ -n "$MODO" ] && [ "$MODO" != "mirrored" ]; then
        echo "AVISO: WSL esta en modo de red '$MODO', no 'mirrored'."
        printf '%s\n' '       Ejecute scripts\wsl\1-preparar-windows.ps1 en Windows y luego  wsl --shutdown.'
        exit 1
    fi
fi

# ---------- Docker Engine ----------
echo "1/3 Instalando herramientas..."
apt-get update -qq
apt-get install -y -qq ca-certificates curl git unzip >/dev/null

if docker version 2>/dev/null | grep -qi 'docker desktop'; then
    echo "AVISO: este Ubuntu usa el Docker de Docker Desktop (integracion WSL)."
    echo "       En Docker Desktop -> Settings -> Resources -> WSL integration, desactive este Ubuntu y cierre Docker Desktop."
    exit 1
fi

if ! command -v dockerd >/dev/null 2>&1; then
    echo "2/3 Instalando Docker Engine desde el repositorio oficial de Docker..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null
else
    echo "2/3 Docker Engine ya estaba instalado."
fi

echo "3/3 Activando Docker al iniciar..."
systemctl enable --now docker >/dev/null 2>&1
[ -n "$USUARIO" ] && usermod -aG docker "$USUARIO"

docker version --format 'Docker {{.Server.Version}} listo.'
docker compose version
echo ""
echo "Listo. Cierre esta ventana de Ubuntu y vuelva a abrirla para usar docker sin sudo."
echo "Siguiente paso (sin sudo):  sh $(dirname "$0")/3-copiar-sistema.sh"
