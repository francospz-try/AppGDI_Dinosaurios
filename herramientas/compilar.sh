#!/usr/bin/env bash
# Compila el aplicativo sin Maven y genera aplicativo/target/registro-comprobantes.jar,
# un único .jar ejecutable que incluye el controlador JDBC de PostgreSQL.
# Requiere un JDK 21 completo (java, javac y jar) y, la primera vez, internet
# para descargar el controlador, cuyo checksum SHA-256 se verifica.
# Todas las rutas se calculan desde la ubicación de este script; la carpeta
# de trabajo de quien lo llama no cambia.
set -euo pipefail
aplicativo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../aplicativo" && pwd)"
command -v javac >/dev/null || { echo 'Instale un JDK 21 completo (incluye javac y jar).'; exit 1; }
VERSION=42.7.7
SHA=157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460
driver="$aplicativo/lib/postgresql-$VERSION.jar"
clases="$aplicativo/target/clases"
dependencias="$aplicativo/target/dependencias"
jar="$aplicativo/target/registro-comprobantes.jar"
mkdir -p "$aplicativo/lib"
if [[ ! -f "$driver" ]]; then
  curl -fL --retry 2 -o "$driver" \
    "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/$VERSION/postgresql-$VERSION.jar"
fi
echo "$SHA  $driver" | sha256sum -c -
rm -rf "$clases" "$dependencias"
mkdir -p "$clases" "$dependencias"
find "$aplicativo/src/main/java" -name '*.java' > "$aplicativo/target/fuentes.txt"
javac --release 21 -encoding UTF-8 -cp "$driver" -d "$clases" @"$aplicativo/target/fuentes.txt"
(cd "$dependencias" && jar xf "$driver" && rm -f META-INF/MANIFEST.MF)
jar --create --file "$jar" --main-class sermed.comprobantes.App -C "$clases" . -C "$dependencias" .
echo "Compilación terminada: $jar"
