#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v javac >/dev/null || { echo 'Instale un JDK 21 completo (incluye javac).'; exit 1; }
mkdir -p lib build/classes
driver=lib/postgresql-42.7.7.jar
if [[ ! -f "$driver" ]]; then
  curl -fL --retry 2 https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar -o "$driver"
fi
echo '157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460  lib/postgresql-42.7.7.jar' | sha256sum -c -
find src/main/java -name '*.java' > build/main-sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/main-sources.txt
jar --create --file build/gdi.jar --main-class pe.edu.ucsm.gdi.Main -C build/classes .
echo 'Compilación terminada: build/gdi.jar'
