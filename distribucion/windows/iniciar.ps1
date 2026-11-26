$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location -LiteralPath $PSScriptRoot

function Find-Docker {
    $command = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($path in @(
        "$env:ProgramFiles\Docker\Docker\resources\bin\docker.exe",
        "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin\docker.exe"
    )) {
        if (Test-Path -LiteralPath $path) { return $path }
    }
}

function Test-Docker {
    if (-not $script:Docker) { return $false }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $os = & $script:Docker info --format '{{.OSType}}' 2>$null
        return ($LASTEXITCODE -eq 0 -and $os -eq 'linux')
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Invoke-Docker {
    & $script:Docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker terminó con código $LASTEXITCODE." }
}

function New-Password {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

$mutex = New-Object Threading.Mutex($false, 'Local\AppGDI-Dinosaurios-Inicio')
$locked = $false
try {
    try { $locked = $mutex.WaitOne() } catch [Threading.AbandonedMutexException] { $locked = $true }
    $script:Docker = Find-Docker
    if (-not $script:Docker) {
        if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
            throw 'Instala App Installer de Microsoft para habilitar winget y vuelve a abrir AppGDI.'
        }
        & winget.exe install --id Docker.DockerDesktop --exact --source winget --accept-source-agreements
        if ($LASTEXITCODE -ne 0) { throw 'La instalación de Docker Desktop no terminó correctamente.' }
        $script:Docker = Find-Docker
        if (-not $script:Docker) { throw 'Reinicia Windows si Docker lo solicita y vuelve a abrir AppGDI.' }
    }
    if (-not (Test-Docker)) {
        $desktop = Join-Path (Split-Path (Split-Path (Split-Path $script:Docker))) 'Docker Desktop.exe'
        if (Test-Path -LiteralPath $desktop) { Start-Process -FilePath $desktop }
        Write-Host 'Esperando a que Docker Desktop habilite los contenedores Linux...'
        for ($i = 0; $i -lt 60; $i++) {
            if (Test-Docker) { break }
            Start-Sleep -Seconds 3
        }
        if (-not (Test-Docker)) {
            throw 'Abre Docker Desktop, completa la configuración de WSL 2 y selecciona contenedores Linux. Luego vuelve a abrir AppGDI.'
        }
    }
    Invoke-Docker compose version

    $directory = Join-Path $env:LOCALAPPDATA 'AppGDI-Dinosaurios'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $config = Join-Path $directory 'config.ini'
    if (-not (Test-Path -LiteralPath $config)) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $script:Docker volume inspect appgdi-dinosaurios_datos 2>$null | Out-Null
            $existingData = $LASTEXITCODE -eq 0
        } finally {
            $ErrorActionPreference = $previous
        }
        if ($existingData) { throw "Ya existen datos de AppGDI. Restaura config.ini en $config." }
        $admin = New-Password
        $app = New-Password
        if ((Read-Host '¿Quieres cambiar la clave generada de gdi_app? [s/N]') -match '^s$') {
            do {
                $secure = Read-Host 'Nueva clave (16 a 128 letras ASCII, números, guiones o guiones bajos)' -AsSecureString
                $confirm = Read-Host 'Confirma la clave' -AsSecureString
                $proposed = [Net.NetworkCredential]::new('', $secure).Password
                $confirmation = [Net.NetworkCredential]::new('', $confirm).Password
                $secure.Dispose()
                $confirm.Dispose()
                $valid = $proposed -cmatch '^[A-Za-z0-9_-]{16,128}$' -and $proposed -ceq $confirmation
                if (-not $valid) { Write-Host 'La clave no cumple el formato o no coincide.' }
            } until ($valid)
            $app = $proposed
        }
        $temporary = Join-Path $directory ([IO.Path]::GetRandomFileName())
        [IO.File]::WriteAllLines($temporary, @("POSTGRES_PASSWORD=$admin", "GDI_APP_PASSWORD=$app"))
        [IO.File]::Move($temporary, $config)
    }
    [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', $null, 'Process')
    [Environment]::SetEnvironmentVariable('GDI_APP_PASSWORD', $null, 'Process')

    $version = (Get-Content -Raw -LiteralPath 'version.txt').Trim()
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $script:Docker image inspect "appgdi-dinosaurios-app:${version}" "appgdi-dinosaurios-db:${version}" 2>$null | Out-Null
        $load = $LASTEXITCODE -ne 0
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($load) { Invoke-Docker load --input (Join-Path $PSScriptRoot 'imagenes.tar.gz') }
    $compose = @('compose', '--env-file', $config, '-f', (Join-Path $PSScriptRoot 'compose.yaml'))
    Invoke-Docker @compose up -d --wait --wait-timeout 240
    $address = Invoke-Docker @compose port app 6080
    $url = "http://$address/vnc.html?autoconnect=1&resize=scale"
    Write-Host "AppGDI está disponible en $url"
    Start-Process $url
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($locked) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
