param([string]$ConfigFile = 'config/database.properties')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($env:GDI_TEST_DATABASE -ne 'disposable') { throw 'Configure una base descartable y GDI_TEST_DATABASE=disposable. Las pruebas modifican datos.' }
& "$PSScriptRoot/build.ps1"
New-Item -ItemType Directory -Force -Path build/test-classes | Out-Null
$sources = @(Get-ChildItem src/test/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Fallo al compilar pruebas.' }
& java -ea -cp 'build/classes;build/test-classes;lib/postgresql-42.7.7.jar' pe.edu.ucsm.gdi.IntegrationTest $ConfigFile
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas fallaron.' }
