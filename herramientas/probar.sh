#!/usr/bin/env bash
# Ejecuta todas las pruebas sobre una base DESCARTABLE recién creada con
# scripts/registro_comprobantes_completo.sql: las pruebas registran,
# modifican y eliminan datos, y no deben repetirse sin volver a crearla.
# Uso: COMPROBANTES_BD_DESCARTABLE=si herramientas/probar.sh <config gdi_app> <config administrador>
# Todas las rutas se calculan desde la ubicación de este script; la carpeta
# de trabajo no cambia.
set -euo pipefail
if [[ "${COMPROBANTES_BD_DESCARTABLE:-}" != "si" ]]; then
  echo 'Las pruebas modifican la base. Úselas solo con una base descartable recién creada'
  echo 'y confírmelo con COMPROBANTES_BD_DESCARTABLE=si.'
  exit 1
fi
[[ $# -eq 2 ]] || { echo 'Uso: herramientas/probar.sh <configuración de gdi_app> <configuración de un administrador>'; exit 1; }
app=$(realpath "$1"); admin=$(realpath "$2")
herramientas="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
jar="$herramientas/../aplicativo/target/registro-comprobantes.jar"
[[ -f "$jar" ]] || bash "$herramientas/compilar.sh"
java -cp "$jar" sermed.comprobantes.pruebas.PruebaConfiguracion
for suite in PruebaConexion PruebasHito30 PruebasHito70 PruebasHito100 PruebaInterfaz; do
  java -Dcomprobantes.config="$app" -cp "$jar" "sermed.comprobantes.pruebas.$suite"
done
java -cp "$jar" sermed.comprobantes.pruebas.PruebaIntegridad "$admin"
echo 'Todas las pruebas pasaron.'
