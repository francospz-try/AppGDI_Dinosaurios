#!/usr/bin/env bash
set -euo pipefail
paquete=$1
salida=$2
cat > "$salida" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
umask 077
cache=${XDG_CACHE_HOME:-$HOME/.cache}/AppGDI-Dinosaurios
mkdir -p -- "$cache"
paquete=$(mktemp -d "$cache/paquete.XXXXXX")
trap 'find "$paquete" -depth -delete' EXIT
linea=$(awk '/^__APPGDI_ARCHIVO__$/{print NR+1; exit}' "$0")
tail -n +"$linea" "$0" | tar -xz -C "$paquete"
bash "$paquete/iniciar.sh" "$paquete"
exit
__APPGDI_ARCHIVO__
STUB
tar -C "$paquete" -czf - compose.yaml version.txt imagenes.tar.gz iniciar.sh >> "$salida"
chmod +x "$salida"
