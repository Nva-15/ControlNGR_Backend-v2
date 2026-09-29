#!/bin/sh
# Control NGR - Copiar el sistema de Windows (D:\Control_NGR) al disco de Ubuntu (WSL2)
#
# Uso (dentro de Ubuntu, SIN sudo, despues de 2-instalar-docker.sh):
#   sh /mnt/d/Control_NGR/ControlNGR_Backend-v2/scripts/wsl/3-copiar-sistema.sh
#   sh .../3-copiar-sistema.sh /mnt/c/otra/ruta/Control_NGR      (si el sistema esta en otra carpeta)
#
# Copia ambos repositorios con .env, certs y respaldos a ~/Control_NGR (mas rapido que /mnt/d) y
# los ajusta para Linux: sin esto git veria todos los archivos como modificados (finales de linea
# de Windows y permisos) y "git pull" fallaria. No modifica la carpeta original de Windows.
set -e

ORIGEN="${1:-/mnt/d/Control_NGR}"
DESTINO="$HOME/Control_NGR"

[ "$(id -u)" != 0 ] || { echo "Ejecute este script SIN sudo, con su usuario de Ubuntu."; exit 1; }
command -v git >/dev/null 2>&1 || { echo "Falta git. Ejecute antes: sudo sh $(dirname "$0")/2-instalar-docker.sh"; exit 1; }
for r in ControlNGR_Backend-v2 ControlNGR_Frontend-v2; do
    [ -d "$ORIGEN/$r/.git" ] || { echo "No se encontro $ORIGEN/$r (repositorio git). Indique la carpeta: sh $0 /mnt/d/Control_NGR"; exit 1; }
done
[ ! -e "$DESTINO" ] || { echo "Ya existe $DESTINO. Si quiere copiar de nuevo, renombrelo antes: mv ~/Control_NGR ~/Control_NGR_anterior"; exit 1; }

echo "1/3 Copiando $ORIGEN a $DESTINO..."
cp -r "$ORIGEN" "$DESTINO"

echo "2/3 Ajustando los repositorios para Linux..."
for r in ControlNGR_Backend-v2 ControlNGR_Frontend-v2; do
    git -C "$DESTINO/$r" config core.fileMode false
    git -C "$DESTINO/$r" config core.autocrlf false
    # Vuelve a escribir los archivos del repositorio con los finales de linea de .gitattributes.
    # No toca .env, certs ni respaldos (no estan en git).
    git -C "$DESTINO/$r" rm -rq --cached .
    git -C "$DESTINO/$r" reset -q --hard
done

echo "3/3 Revisando .env..."
ENV="$DESTINO/ControlNGR_Backend-v2/.env"
if [ -f "$ENV" ]; then
    sed -i 's/\r$//' "$ENV"
    chmod 600 "$ENV"
else
    echo "AVISO: no existe $ENV. Copie el .env del sistema antes de encenderlo."
fi

echo ""
echo "Listo: $DESTINO"
echo "Siguiente paso:  cd ~/Control_NGR/ControlNGR_Backend-v2"
