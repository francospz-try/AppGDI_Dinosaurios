$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Instale un JDK 21 completo y agregue su carpeta bin a PATH.' }
New-Item -ItemType Directory -Force -Path lib,build/classes | Out-Null
$driver = 'lib/postgresql-42.7.7.jar'
if (-not (Test-Path $driver)) {
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar' -OutFile $driver
}
if ((Get-FileHash $driver -Algorithm SHA256).Hash.ToLower() -ne '157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460') { throw 'El controlador JDBC no coincide con el checksum esperado.' }
$sources = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -d build/classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Fallo de compilación Java.' }
& jar --create --file build/gdi.jar --main-class pe.edu.ucsm.gdi.Main -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el JAR.' }
Write-Host 'Compilación terminada: build/gdi.jar'
