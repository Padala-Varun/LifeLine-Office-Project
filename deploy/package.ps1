# Builds everything, runs the server tests, and packs deploy\lifeline-deploy.tar.gz for Google Cloud Shell.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot

function Assert-Success($step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed (exit code $LASTEXITCODE)." }
}

$jdk = Get-ChildItem (Join-Path $root '.tools\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }

Push-Location (Join-Path $root 'frontend')
try {
    if (-not (Test-Path 'node_modules')) { npm.cmd ci --no-audit --no-fund; Assert-Success 'npm ci' }
    npm.cmd run build; Assert-Success 'Building the websites'
} finally { Pop-Location }

Push-Location (Join-Path $root 'backend')
try { & .\mvnw.cmd -q -B clean package; Assert-Success 'Building and testing the server' } finally { Pop-Location }

$stage = Join-Path $root 'backend\target\lifeline'
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item (Join-Path $root 'backend\target\battery-monitor-0.1.0.jar') (Join-Path $stage 'lifeline.jar')
foreach ($name in 'deploy.sh', 'install.sh') {
    # Linux needs LF line endings.
    $text = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $name)) -replace "`r`n", "`n"
    [IO.File]::WriteAllText((Join-Path $stage $name), $text)
}
$bundle = Join-Path $PSScriptRoot 'lifeline-deploy.tar.gz'
& "$env:SystemRoot\System32\tar.exe" -czf $bundle -C (Split-Path $stage) lifeline; Assert-Success 'Packing'
Write-Host "Ready: $bundle"
