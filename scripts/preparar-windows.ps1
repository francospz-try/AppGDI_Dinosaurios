param([string]$Root = 'C:\Franco\2026\GDI')
$ErrorActionPreference = 'Stop'
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Instale Git para Windows primero.' }
New-Item -ItemType Directory -Force -Path $Root | Out-Null
function Clone-IfMissing([string]$Url, [string]$Destination) {
    if (-not (Test-Path $Destination)) {
        & git clone $Url $Destination
        if ($LASTEXITCODE -ne 0) { throw "No se pudo clonar $Url" }
    } elseif (-not (Test-Path (Join-Path $Destination '.git'))) {
        throw "La carpeta $Destination existe y no es un repositorio. No se sobrescribió."
    }
}
Clone-IfMissing 'https://github.com/francospz-try/AppGDI_Dinosaurios.git' (Join-Path $Root 'AppGDI_Dinosaurios')
Clone-IfMissing 'https://github.com/obra/superpowers.git' (Join-Path $Root 'superpowers')
Clone-IfMissing 'https://github.com/mattpocock/skills.git' (Join-Path $Root 'mattpocock-skills')
# Codex descubre las habilidades mediante .agents/skills. Una junction no exige
# habilitar modo desarrollador ni ejecutar PowerShell como administrador.
$discovery = Join-Path $env:USERPROFILE '.agents\skills'
New-Item -ItemType Directory -Force -Path $discovery | Out-Null
$collections = @(
    @{ Prefix='superpowers'; Source=(Join-Path $Root 'superpowers\skills') },
    @{ Prefix='matt'; Source=(Join-Path $Root 'mattpocock-skills\skills\engineering') },
    @{ Prefix='matt'; Source=(Join-Path $Root 'mattpocock-skills\skills\productivity') }
)
foreach ($collection in $collections) {
    Get-ChildItem $collection.Source -Recurse -Filter SKILL.md | ForEach-Object {
        $destination = Join-Path $discovery ($collection.Prefix + '-' + $_.Directory.Name)
        if (-not (Test-Path $destination)) {
            New-Item -ItemType Junction -Path $destination -Target $_.Directory.FullName | Out-Null
        } elseif ((Get-Item $destination).Target -notcontains $_.Directory.FullName) {
            Write-Warning "Ya existe otra habilidad en $destination; no se reemplazó."
        }
    }
}
$app = Join-Path $Root 'AppGDI_Dinosaurios'
if (-not (Test-Path (Join-Path $app 'config\database.properties'))) {
    Copy-Item (Join-Path $app 'config\database.properties.example') (Join-Path $app 'config\database.properties')
}
Write-Host "Repositorios preparados en $Root. Habilidades estables enlazadas para Codex."
Write-Host "Siguiente: configure PostgreSQL y ejecute $app\scripts\build.ps1"
