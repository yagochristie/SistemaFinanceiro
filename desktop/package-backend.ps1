$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$wheel = Join-Path $root "wheel"
$mvnw = Join-Path $wheel "mvnw.cmd"

Push-Location $wheel
try {
  if (Test-Path $mvnw) {
    & $mvnw -q -DskipTests package
    if ($LASTEXITCODE -eq 0) { exit 0 }
  }

  $mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName

  if (!$mvn) {
    throw "Nao encontrei o Maven. Rode .\wheel\mvnw.cmd package uma vez ou instale o Maven/Node normalmente."
  }

  & $mvn -q -DskipTests package
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
  Pop-Location
}
