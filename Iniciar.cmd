@echo off
cd /d "%~dp0"
java -cp "build\gdi.jar;lib\postgresql-42.7.7.jar" pe.edu.ucsm.gdi.Main
if errorlevel 1 pause
