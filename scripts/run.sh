#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f build/gdi.jar && -f lib/postgresql-42.7.7.jar ]] || { echo 'Ejecute scripts/build.sh primero.'; exit 1; }
exec java -cp 'build/gdi.jar:lib/postgresql-42.7.7.jar' pe.edu.ucsm.gdi.Main "$@"
