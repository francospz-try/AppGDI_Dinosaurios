# Compila el aplicativo sin Maven y genera aplicativo\target\registro-comprobantes.jar,
# un único .jar ejecutable que incluye el controlador JDBC de PostgreSQL.
# Requiere un JDK 21 completo (java, javac y jar en PATH) y, la primera vez,
# internet para descargar el controlador, cuyo checksum SHA-256 se verifica.
# Todas las rutas se calculan desde la ubicación de este script, y la carpeta
# de trabajo de quien lo llama se restaura al terminar.
$ErrorActionPreference = 'Stop'
$aplicativo = Join-Path (Split-Path $PSScriptRoot -Parent) 'aplicativo'
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Instale un JDK 21 completo y agregue su carpeta bin a PATH.' }
$version = '42.7.7'
$sha = '157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460'
$lib = Join-Path $aplicativo 'lib'
$driver = Join-Path $lib "postgresql-$version.jar"
$clases = Join-Path $aplicativo 'target/clases'
$dependencias = Join-Path $aplicativo 'target/dependencias'
$jar = Join-Path $aplicativo 'target/registro-comprobantes.jar'
New-Item -ItemType Directory -Force -Path $lib | Out-Null
if (-not (Test-Path $driver)) {
    Invoke-WebRequest "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/$version/postgresql-$version.jar" -OutFile $driver
}
if ((Get-FileHash $driver -Algorithm SHA256).Hash.ToLower() -ne $sha) { throw 'El controlador JDBC no coincide con el checksum esperado.' }
Remove-Item -Recurse -Force $clases, $dependencias -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $clases, $dependencias | Out-Null
$fuentes = @(Get-ChildItem (Join-Path $aplicativo 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp $driver -d $clases $fuentes
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación de Java.' }
Push-Location $dependencias
try {
    & jar xf $driver
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo extraer el controlador JDBC.' }
    Remove-Item -Force (Join-Path $dependencias 'META-INF/MANIFEST.MF') -ErrorAction SilentlyContinue
} finally {
    Pop-Location
}
& jar --create --file $jar --main-class sermed.comprobantes.App -C $clases . -C $dependencias .
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el .jar.' }
Write-Host "Compilación terminada: $jar"
