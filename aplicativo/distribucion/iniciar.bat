@echo off
rem Abre el aplicativo de registro de comprobantes de pago.
rem Debe estar en la misma carpeta que registro-comprobantes.jar y la carpeta config.
cd /d "%~dp0"
start "" javaw -jar registro-comprobantes.jar
