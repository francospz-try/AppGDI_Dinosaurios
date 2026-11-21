# Ejecuta todas las pruebas sobre una base DESCARTABLE recién creada con
# scripts\registro_comprobantes_completo.sql: las pruebas registran,
# modifican y eliminan datos, y no deben repetirse sin volver a crearla.
# Uso: $env:COMPROBANTES_BD_DESCARTABLE = 'si'
#      powershell -ExecutionPolicy Bypass -File herramientas\probar.ps1 <config gdi_app> <config administrador>
param([Parameter(Mandatory)][string]$ConfigApp, [Parameter(Mandatory)][string]$ConfigAdmin)
$ErrorActionPreference = 'Stop'
if ($env:COMPROBANTES_BD_DESCARTABLE -ne 'si') { throw 'Las pruebas modifican la base. Use una base descartable recién creada y defina COMPROBANTES_BD_DESCARTABLE=si.' }
$app = (Resolve-Path $ConfigApp).Path
$admin = (Resolve-Path $ConfigAdmin).Path
Set-Location (Split-Path $PSScriptRoot -Parent)
$jar = 'aplicativo/target/registro-comprobantes.jar'
if (-not (Test-Path $jar)) { & "$PSScriptRoot/compilar.ps1" }
foreach ($suite in @('PruebaConexion', 'PruebasHito30', 'PruebasHito70', 'PruebasHito100', 'PruebaInterfaz')) {
    & java "-Dcomprobantes.config=$app" -cp $jar "sermed.comprobantes.pruebas.$suite"
    if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas de $suite." }
}
& java -cp $jar sermed.comprobantes.pruebas.PruebaIntegridad $admin
if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de integridad.' }
Write-Host 'Todas las pruebas pasaron.'
