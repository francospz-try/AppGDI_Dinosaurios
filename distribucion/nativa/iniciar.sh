#!/usr/bin/env bash
set -euo pipefail
dir=$(cd "$(dirname "$0")" && pwd)
exec "$dir/java/bin/java" -cp "$dir/inicio.jar:$dir/app.jar" \
  sermed.comprobantes.distribucion.InicioNativo "$dir" "$@"
