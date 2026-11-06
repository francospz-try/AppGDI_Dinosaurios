param([string]$ConfigFile = 'config/database.properties')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path build/gdi.jar)) { throw 'Ejecute scripts/build.ps1 primero.' }
& java -cp 'build/gdi.jar;lib/postgresql-42.7.7.jar' pe.edu.ucsm.gdi.Main $ConfigFile
if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar el aplicativo.' }
