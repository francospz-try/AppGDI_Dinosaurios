@echo off
setlocal
set "APPGDI_DIR=%~dp0."
"%~dp0java\bin\javaw.exe" -cp "%~dp0inicio.jar;%~dp0app.jar" sermed.comprobantes.distribucion.InicioNativo "%APPGDI_DIR%" %*
exit /b %ERRORLEVEL%
