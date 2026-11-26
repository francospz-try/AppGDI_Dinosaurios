#!/usr/bin/env bash
set -euo pipefail
umask 077
if (( EUID == 0 )); then
  echo 'Ejecute el instalador como su usuario. Se solicitará sudo para Docker cuando haga falta.'
  exit 1
fi
paquete=$1
if [[ $(uname -m) != x86_64 ]]; then
  echo 'Este instalador requiere Linux x86-64.'
  exit 1
fi
config_dir=${XDG_CONFIG_HOME:-$HOME/.config}/AppGDI-Dinosaurios
mkdir -p -- "$config_dir"
chmod 700 "$config_dir"
config="$config_dir/config.ini"

docker_cmd=(docker)
if ! docker compose version >/dev/null 2>&1; then
  . /etc/os-release
  if [[ "$ID" != ubuntu || "${VERSION_ID:-}" != 24.04 ]]; then
    echo 'La instalación automática requiere Ubuntu 24.04. En otra distribución, instale Docker Engine y Compose v2 primero.'
    exit 1
  fi
  sudo apt-get update
  if command -v docker >/dev/null; then
    if apt-cache show docker-compose-plugin >/dev/null 2>&1; then
      sudo apt-get install -y docker-compose-plugin
    else
      sudo apt-get install -y docker-compose-v2
    fi
  else
    sudo apt-get install -y docker.io docker-compose-v2
  fi
fi
if ! docker info >/dev/null 2>&1; then
  echo 'Docker requiere acceso al servicio local. Se usará sudo.'
  docker_cmd=(sudo docker)
  if ! "${docker_cmd[@]}" info >/dev/null; then
    sudo systemctl start docker
    "${docker_cmd[@]}" info >/dev/null
  fi
fi

exec 9>"$config_dir/inicio.lock"
flock 9
if [[ ! -f "$config" ]]; then
  if "${docker_cmd[@]}" volume inspect appgdi-dinosaurios_datos >/dev/null 2>&1; then
    echo "Ya existen datos de AppGDI. Restaure su config.ini en $config antes de continuar."
    exit 1
  fi
  generar() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
  clave_app=$(generar)
  clave_admin=$(generar)
  read -r -p '¿Quieres cambiar la clave generada de gdi_app? [s/N] ' respuesta
  if [[ "$respuesta" =~ ^[sS]$ ]]; then
    while true; do
      read -r -s -p 'Nueva clave (16 a 128 letras ASCII, números, guiones o guiones bajos): ' clave
      printf '\n'
      read -r -s -p 'Confirma la clave: ' confirmacion
      printf '\n'
      if [[ "$clave" =~ ^[A-Za-z0-9_-]{16,128}$ && "$clave" == "$confirmacion" ]]; then
        clave_app=$clave
        break
      fi
      echo 'La clave no cumple el formato o no coincide.'
    done
  fi
  temporal=$(mktemp "$config_dir/config.XXXXXX")
  printf 'POSTGRES_PASSWORD=%s\nGDI_APP_PASSWORD=%s\n' "$clave_admin" "$clave_app" > "$temporal"
  mv -- "$temporal" "$config"
  unset clave_admin clave_app clave confirmacion
fi
chmod 600 "$config"
unset POSTGRES_PASSWORD GDI_APP_PASSWORD
version=$(cat "$paquete/version.txt")
if ! "${docker_cmd[@]}" image inspect "appgdi-dinosaurios-app:$version" "appgdi-dinosaurios-db:$version" >/dev/null 2>&1; then
  "${docker_cmd[@]}" load --input "$paquete/imagenes.tar.gz"
fi
compose=("${docker_cmd[@]}" compose --env-file "$config" -f "$paquete/compose.yaml")
"${compose[@]}" up -d --wait --wait-timeout 240
direccion=$("${compose[@]}" port app 6080)
url="http://$direccion/vnc.html?autoconnect=1&resize=scale"
echo "AppGDI está disponible en $url"
if command -v xdg-open >/dev/null && [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]; then
  xdg-open "$url" >/dev/null 2>&1 9>&- &
fi
