#!/usr/bin/env bash
set -euo pipefail
sistema=$1
salida=$2
version_pg=17.12.0
case "$sistema" in
  linux) sha=7a8951dc117490f2d4161600836c8b1055306aff86e0a472aad55457651e5cbd ;;
  windows) sha=bebadbe868ed53adb1c12033a381e41005915205ea9fedaa2349f5467fa7016b ;;
  *) exit 2 ;;
esac
repo=$(cd "$(dirname "$0")/../.." && pwd)
if [[ "$sistema" == windows ]]; then
  RUNNER_TEMP=$(cygpath -u "$RUNNER_TEMP")
fi
mkdir -p "$salida/postgresql"
salida=$(cd "$salida" && pwd)
descarga=$(mktemp -d "${RUNNER_TEMP:-$salida}/appgdi-postgresql.XXXXXX")
trap 'find "$descarga" -depth -delete' EXIT
artifact="embedded-postgres-binaries-$sistema-amd64"
curl -fsSL --retry 3 "https://repo.maven.apache.org/maven2/io/zonky/test/postgres/$artifact/$version_pg/$artifact-$version_pg.jar" -o "$descarga/postgresql.jar"
printf '%s  %s\n' "$sha" "$descarga/postgresql.jar" | sha256sum -c -
(
  cd "$descarga"
  jar xf postgresql.jar "postgres-$sistema-x86_64.txz"
)
archivo="$descarga/postgres-$sistema-x86_64.txz"
if [[ "$sistema" == windows ]]; then
  sistema_windows=$(cygpath -u "${SYSTEMROOT:-${SystemRoot:-C:/Windows}}")
  "$sistema_windows/System32/tar.exe" -xf "$(cygpath -w "$archivo")" -C "$(cygpath -w "$salida/postgresql")"
else
  tar -xJf "$archivo" -C "$salida/postgresql"
fi
cp "$repo/distribucion/nativa/PostgreSQL-COPYRIGHT.txt" "$salida/postgresql/COPYRIGHT"
cp "$repo/distribucion/nativa/Zonky-LICENSE.txt" "$salida/postgresql/Zonky-LICENSE"
cp "$repo/scripts/registro_comprobantes_completo.sql" "$salida/base.sql"
cp "$repo/aplicativo/target/registro-comprobantes.jar" "$salida/app.jar"
mkdir -p "$descarga/clases"
javac --release 21 -encoding UTF-8 -cp "$salida/app.jar" -d "$descarga/clases" "$repo/distribucion/nativa/InicioNativo.java"
jar --create --file "$salida/inicio.jar" -C "$descarga/clases" .
jlink --add-modules java.desktop,java.sql,java.naming,java.management,java.security.jgss,jdk.crypto.ec,jdk.localedata \
  --include-locales es,en --strip-debug --no-header-files --no-man-pages --output "$salida/java"
case "$sistema" in
  linux)
    cp "$repo/distribucion/nativa/iniciar.sh" "$salida/"
    chmod +x "$salida/iniciar.sh"
    ;;
  windows)
    cp "$repo/distribucion/nativa/"*.cmd "$salida/"
    ;;
esac
