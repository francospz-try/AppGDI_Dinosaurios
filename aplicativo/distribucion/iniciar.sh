#!/bin/sh
# Abre el aplicativo de registro de comprobantes de pago.
# Debe estar en la misma carpeta que registro-comprobantes.jar y la carpeta config.
cd "$(dirname "$0")" && exec java -jar registro-comprobantes.jar
