@echo off
rem Abre el aplicativo de registro de comprobantes de pago desde esta carpeta.
rem Compile antes con herramientas\compilar.ps1 (o mvn package en aplicativo).
setlocal
pushd "%~dp0aplicativo"
if not exist target\registro-comprobantes.jar (
  echo Falta aplicativo\target\registro-comprobantes.jar. Compile con herramientas\compilar.ps1.
  pause
  popd
  exit /b 1
)
start "" javaw -jar target\registro-comprobantes.jar
popd
