#!/usr/bin/env bash
set -euo pipefail
umask 077
config=$(mktemp)
printf 'db.host=db\ndb.puerto=5432\ndb.base=registro_comprobantes\ndb.usuario=gdi_app\ndb.clave=%s\n' "$GDI_APP_PASSWORD" > "$config"
unset GDI_APP_PASSWORD
export DISPLAY=:99
export XAUTHORITY=$(mktemp)
xauth add "$DISPLAY" . "$(mcookie)"
Xvfb "$DISPLAY" -screen 0 1280x800x24 -nolisten tcp -auth "$XAUTHORITY" &
for ((intento=0; intento<50; intento++)); do
  if xdpyinfo >/dev/null 2>&1; then break; fi
  sleep 0.1
done
xdpyinfo >/dev/null
openbox &
x11vnc -display "$DISPLAY" -localhost -rfbport 5900 -forever -shared -nopw -quiet &
PYTHONPATH=/opt/appgdi websockify --auth-plugin origen_novnc.OrigenLocal --web /usr/share/novnc 0.0.0.0:6080 localhost:5900 &
java -Dcomprobantes.config="$config" -jar /opt/appgdi/app.jar &
trap 'exit 0' TERM INT
trap 'kill $(jobs -pr) 2>/dev/null || true; unlink "$config"; unlink "$XAUTHORITY"' EXIT
# Exiting Swing or losing any display component stops this container.
wait -n
