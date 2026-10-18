param(
    [Parameter(Mandatory = $true)]
    [string]$BackupZip,

    [switch]$Apply
)

$ErrorActionPreference = "Stop"

$rootPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$backupDir = Join-Path $rootPath "backups"

foreach ($required in @("package.json", "teste.html", "wheel\pom.xml")) {
    if (-not (Test-Path -LiteralPath (Join-Path $rootPath $required))) {
        throw "Este script precisa ser executado dentro do projeto SoftDarf. Arquivo ausente: $required"
    }
}

$candidates = @()
if ([System.IO.Path]::IsPathRooted($BackupZip)) {
    $candidates += $BackupZip
}
else {
    $candidates += (Join-Path (Get-Location).Path $BackupZip)
    $candidates += (Join-Path $rootPath $BackupZip)
}

$zipPath = $null
foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate) {
        $zipPath = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}

if (-not $zipPath) {
    throw "Backup nao encontrado: $BackupZip"
}

Write-Host "Projeto: $rootPath"
Write-Host "Backup:  $zipPath"

if (-not $Apply) {
    Write-Host ""
    Write-Host "Simulacao concluida. Nenhum arquivo foi alterado."
    Write-Host "Para restaurar de verdade, rode o mesmo comando com -Apply."
    return
}

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$preRestoreName = "antes-de-restaurar-$timestamp.zip"
$backupScript = Join-Path $PSScriptRoot "backup-softdarf.ps1"
& $backupScript -Name $preRestoreName | Out-Host

$tmpDir = Join-Path $backupDir "restore-tmp-$timestamp"
if (Test-Path -LiteralPath $tmpDir) {
    Remove-Item -LiteralPath $tmpDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
Expand-Archive -LiteralPath $zipPath -DestinationPath $tmpDir -Force

$excludedTopLevel = @(".git", "node_modules", "dist", "backups")
Get-ChildItem -LiteralPath $rootPath -Force | Where-Object {
    $excludedTopLevel -notcontains $_.Name
} | Remove-Item -Recurse -Force

Get-ChildItem -LiteralPath $tmpDir -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $rootPath -Recurse -Force
}

Remove-Item -LiteralPath $tmpDir -Recurse -Force

Write-Host ""
Write-Host "Restauracao concluida."
Write-Host "Um backup do estado anterior foi salvo em: $(Join-Path $backupDir $preRestoreName)"
