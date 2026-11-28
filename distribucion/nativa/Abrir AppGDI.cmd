@echo off
rem Abre AppGDI desde esta carpeta (instalada o portatil) sin dejar abierta
rem una ventana de consola. Los datos se guardan en %LOCALAPPDATA%\AppGDI-Dinosaurios.
start "" "%~dp0java\bin\javaw.exe" -cp "%~dp0inicio.jar;%~dp0app.jar" sermed.comprobantes.distribucion.InicioNativo "%~dp0."
