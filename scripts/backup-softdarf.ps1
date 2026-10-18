param(
    [string]$Name = ""
)

$ErrorActionPreference = "Stop"

$rootPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$backupDir = Join-Path $rootPath "backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

if ([string]::IsNullOrWhiteSpace($Name)) {
    $Name = "softdarf-checkpoint-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}

if (-not $Name.EndsWith(".zip", [System.StringComparison]::OrdinalIgnoreCase)) {
    $Name = "$Name.zip"
}

$zipPath = Join-Path $backupDir $Name
if (Test-Path -LiteralPath $zipPath) {
    throw "Backup ja existe: $zipPath"
}

$excludedPrefixes = @(
    ".git\",
    "node_modules\",
    "dist\",
    "dist-fixed\",
    "backups\",
    ".codex_spreadsheet\",
    "outputs\",
    "wheel\target\"
)

$excludedFiles = @(
    "darf.html"
)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$files = Get-ChildItem -LiteralPath $rootPath -File -Recurse -Force | Where-Object {
    $relative = $_.FullName.Substring($rootPath.Length).TrimStart("\", "/")
    $normalized = $relative -replace "/", "\"
    $skip = $false

    foreach ($prefix in $excludedPrefixes) {
        if ($normalized.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $skip = $true
            break
        }
    }

    if ($normalized -match '^(?i)dist-[^\\]+\\') {
        $skip = $true
    }

    -not $skip -and $excludedFiles -notcontains $normalized
}

if (-not $files -or $files.Count -eq 0) {
    throw "Nenhum arquivo encontrado para backup."
}

$zip = $null
try {
    $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)

    foreach ($file in $files) {
        $relative = $file.FullName.Substring($rootPath.Length).TrimStart("\", "/")
        $entryName = $relative -replace "\\", "/"
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip,
            $file.FullName,
            $entryName,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
catch {
    if ($zip) {
        $zip.Dispose()
        $zip = $null
    }

    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }

    throw "Falha ao criar backup. Feche o SoftDarf se o banco estiver em uso. Detalhe: $($_.Exception.Message)"
}
finally {
    if ($zip) {
        $zip.Dispose()
    }
}

Get-Item -LiteralPath $zipPath | Select-Object FullName, Length, LastWriteTime
