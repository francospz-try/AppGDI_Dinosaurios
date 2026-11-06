#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${GDI_TEST_DATABASE:-}" != "disposable" ]]; then
  echo 'Use una base descartable: estas pruebas crean y dan de baja un cliente ficticio.'
  echo 'Confirme el destino con GDI_TEST_DATABASE=disposable y un archivo de configuración de prueba.'
  exit 1
fi
bash scripts/build.sh
mkdir -p build/test-classes
find src/test/java -name '*.java' > build/test-sources.txt
javac --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
java -ea -cp 'build/classes:build/test-classes:lib/postgresql-42.7.7.jar' pe.edu.ucsm.gdi.IntegrationTest "${1:-config/database.properties}"
