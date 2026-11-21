# Compila el aplicativo sin Maven y genera aplicativo\target\registro-comprobantes.jar,
# un único .jar ejecutable que incluye el controlador JDBC de PostgreSQL.
# Requiere un JDK 21 completo (java, javac y jar en PATH) y, la primera vez,
# internet para descargar el controlador, cuyo checksum SHA-256 se verifica.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'aplicativo')
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Instale un JDK 21 completo y agregue su carpeta bin a PATH.' }
$version = '42.7.7'
$sha = '157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460'
$driver = "lib/postgresql-$version.jar"
New-Item -ItemType Directory -Force -Path lib | Out-Null
if (-not (Test-Path $driver)) {
    Invoke-WebRequest "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/$version/postgresql-$version.jar" -OutFile $driver
}
if ((Get-FileHash $driver -Algorithm SHA256).Hash.ToLower() -ne $sha) { throw 'El controlador JDBC no coincide con el checksum esperado.' }
Remove-Item -Recurse -Force target/clases, target/dependencias -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path target/clases, target/dependencias | Out-Null
$fuentes = @(Get-ChildItem src/main/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp $driver -d target/clases $fuentes
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación de Java.' }
Push-Location target/dependencias
& jar xf "../../$driver"
Remove-Item -Force META-INF/MANIFEST.MF -ErrorAction SilentlyContinue
Pop-Location
& jar --create --file target/registro-comprobantes.jar --main-class sermed.comprobantes.App -C target/clases . -C target/dependencias .
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el .jar.' }
Write-Host 'Compilación terminada: aplicativo\target\registro-comprobantes.jar'
