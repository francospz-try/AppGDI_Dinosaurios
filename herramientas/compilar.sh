#!/usr/bin/env bash
# Compila el aplicativo sin Maven y genera aplicativo/target/registro-comprobantes.jar,
# un único .jar ejecutable que incluye el controlador JDBC de PostgreSQL.
# Requiere un JDK 21 completo (java, javac y jar) y, la primera vez, internet
# para descargar el controlador, cuyo checksum SHA-256 se verifica.
set -euo pipefail
cd "$(dirname "$0")/../aplicativo"
command -v javac >/dev/null || { echo 'Instale un JDK 21 completo (incluye javac y jar).'; exit 1; }
VERSION=42.7.7
SHA=157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460
driver=lib/postgresql-$VERSION.jar
mkdir -p lib
if [[ ! -f "$driver" ]]; then
  curl -fL --retry 2 -o "$driver" \
    "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/$VERSION/postgresql-$VERSION.jar"
fi
echo "$SHA  $driver" | sha256sum -c -
rm -rf target/clases target/dependencias
mkdir -p target/clases target/dependencias
find src/main/java -name '*.java' > target/fuentes.txt
javac --release 21 -encoding UTF-8 -cp "$driver" -d target/clases @target/fuentes.txt
(cd target/dependencias && jar xf "../../$driver" && rm -f META-INF/MANIFEST.MF)
jar --create --file target/registro-comprobantes.jar --main-class sermed.comprobantes.App \
  -C target/clases . -C target/dependencias .
echo 'Compilación terminada: aplicativo/target/registro-comprobantes.jar'
