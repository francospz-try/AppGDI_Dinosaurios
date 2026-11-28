#!/usr/bin/env bash
# Firma con Authenticode los instaladores de Windows usando jsign, que corre en
# Linux, macOS o Windows con Java 11 o superior. No necesita signtool ni Windows.
#
# Uso:
#   FIRMA_ALMACEN=certificado.p12 FIRMA_CLAVE=... distribucion/nativa/firmar.sh salida/*.exe
#
# Variables (solo FIRMA_ALMACEN es obligatoria):
#   FIRMA_TIPO      Tipo de almacén de jsign (por defecto PKCS12). Otros valores útiles:
#                   PKCS11 o ETOKEN (token USB del proveedor), TRUSTEDSIGNING (Azure
#                   Artifact Signing), ESIGNER (SSL.com), DIGICERTONE, AZUREKEYVAULT.
#   FIRMA_ALMACEN   Archivo .p12/.pfx, archivo de configuración PKCS#11 o, en la nube,
#                   el punto de acceso del servicio (por ejemplo eus.codesigning.azure.net).
#   FIRMA_CLAVE     Contraseña del almacén o credencial/token del servicio en la nube.
#   FIRMA_ALIAS     Alias del certificado (obligatorio en algunos almacenes; en
#                   TRUSTEDSIGNING es <cuenta>/<perfil>).
#   FIRMA_CADENA    Archivo con la cadena de certificados, si el almacén no la incluye.
#   FIRMA_TSA       Autoridad de sellado de tiempo (por defecto la de DigiCert). Con el
#                   sello, la firma sigue siendo válida cuando el certificado vence.
#
# Las credenciales se leen del entorno para que no queden en el historial ni en los
# registros de CI. La firma debe hacerse antes de calcular SHA256SUMS.txt.
set -euo pipefail
[[ $# -ge 1 ]] || { echo 'Uso: firmar.sh <instalador.exe>...'; exit 1; }
: "${FIRMA_ALMACEN:?Indica el certificado o servicio en FIRMA_ALMACEN.}"
command -v java >/dev/null || { echo 'Se necesita Java 11 o superior.'; exit 1; }

VERSION=7.6
SHA=6cf58aa080e7ff82656f320cff8d4a3bff30408df8766806dcba3740dd839fa5
cache="${XDG_CACHE_HOME:-$HOME/.cache}/appgdi"
jsign="$cache/jsign-$VERSION.jar"
mkdir -p "$cache"
if [[ ! -f "$jsign" ]]; then
  curl -fsSL --retry 3 -o "$jsign.tmp" \
    "https://repo.maven.apache.org/maven2/net/jsign/jsign/$VERSION/jsign-$VERSION.jar"
  mv "$jsign.tmp" "$jsign"
fi
echo "$SHA  $jsign" | sha256sum -c --quiet - || { rm -f "$jsign"; echo 'jsign no coincide con el checksum.'; exit 1; }

argumentos=(--storetype "${FIRMA_TIPO:-PKCS12}" --keystore "$FIRMA_ALMACEN"
  --alg SHA-256 --tsaurl "${FIRMA_TSA:-http://timestamp.digicert.com}" --tsretries 3
  --name 'AppGDI Dinosaurios' --url 'https://github.com/francospz-try/AppGDI_Dinosaurios'
  --replace)
[[ -n "${FIRMA_CLAVE:-}" ]] && argumentos+=(--storepass "env:FIRMA_CLAVE")
[[ -n "${FIRMA_ALIAS:-}" ]] && argumentos+=(--alias "$FIRMA_ALIAS")
[[ -n "${FIRMA_CADENA:-}" ]] && argumentos+=(--certfile "$FIRMA_CADENA")

for instalador in "$@"; do
  java -jar "$jsign" "${argumentos[@]}" "$instalador"
  echo "Firmado: $instalador"
done
