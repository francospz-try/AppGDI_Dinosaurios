#!/usr/bin/env bash
set -euo pipefail
paquete=$1
version=$2
modo=$3
salida=$4
printf '#!/usr/bin/env bash\nversion=%q\nmodo=%q\n' "$version" "$modo" > "$salida"
cat >> "$salida" <<'STUB'
set -euo pipefail
umask 077
if (( EUID == 0 )) || [[ $(uname -m) != x86_64 ]]; then
  echo 'Ejecuta este instalador en Linux x86-64 como usuario normal.'
  exit 1
fi
raiz=${XDG_DATA_HOME:-$HOME/.local/share}/AppGDI-Dinosaurios/programa/$modo
mkdir -p -- "$raiz"
instalado="$raiz/$version"
exec 9>"$raiz/instalar.lock"
flock 9
if [[ ! -x "$instalado/iniciar.sh" ]]; then
  temporal=$(mktemp -d "$raiz/instalar.XXXXXX")
  trap 'find "$temporal" -depth -delete' EXIT
  linea=$(awk '/^__APPGDI_ARCHIVO__$/{print NR+1; exit}' "$0")
  tail -n +"$linea" "$0" | tar -xmz -C "$temporal"
  mv -T -- "$temporal" "$instalado"
  trap - EXIT
fi
exec 9>&-
echo "AppGDI se instaló en $instalado"
exec "$instalado/iniciar.sh" "$@"
__APPGDI_ARCHIVO__
STUB
printf '%s\n' "$modo" > "$paquete/modo.txt"
tar -C "$paquete" -czf - . >> "$salida"
chmod +x "$salida"
