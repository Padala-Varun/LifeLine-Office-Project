# Builds both websites and the server, then starts everything. Run start.cmd.
# Use "start.cmd -SkipBuild" to start again without rebuilding.
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$backend = Join-Path $root 'backend'
$local = Join-Path $backend 'local'

function Assert-Success($step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed (exit code $LASTEXITCODE)." }
}

function New-Secret {
    $bytes = New-Object byte[] 12
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return ([Convert]::ToBase64String($bytes) -replace '[+/=]', 'x')
}

# --- Java and Node -----------------------------------------------------------
$jdk = Get-ChildItem (Join-Path $root '.tools\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Java was not found. Install JDK 21 or newer and set JAVA_HOME.'
}
$javaBin = Join-Path $env:JAVA_HOME 'bin'

# --- Build -------------------------------------------------------------------
if (-not $SkipBuild) {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js was not found. Install Node 24 or newer.' }
    Push-Location (Join-Path $root 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) { npm.cmd ci --no-audit --no-fund; Assert-Success 'npm ci' }
        npm.cmd run build; Assert-Success 'Building the websites'
    } finally { Pop-Location }

    Push-Location $backend
    try {
        & .\mvnw.cmd -q -B clean package -DskipTests; Assert-Success 'Building the server'
    } finally { Pop-Location }
}

# --- Certificate password (created once, kept out of git) ---------------------
New-Item -ItemType Directory -Force $local | Out-Null
$secretsFile = Join-Path $local 'secrets.json'
if (-not (Test-Path $secretsFile)) {
    @{ keystorePassword = New-Secret } | ConvertTo-Json | Set-Content $secretsFile -Encoding ascii
}
$secrets = Get-Content $secretsFile -Raw | ConvertFrom-Json
$env:LIFELINE_KEYSTORE_PASSWORD = $secrets.keystorePassword

# --- HTTPS certificate for this computer's network address --------------------
$ip = Get-NetIPConfiguration |
    Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
    ForEach-Object { $_.IPv4Address.IPAddress } | Select-Object -First 1
if (-not $ip) { $ip = '127.0.0.1' }

$keystore = Join-Path $local 'keystore.p12'
$certIpFile = Join-Path $local 'certificate-ip.txt'
$certIp = if (Test-Path $certIpFile) { (Get-Content $certIpFile -Raw).Trim() } else { '' }
if (-not (Test-Path $keystore) -or $certIp -ne $ip) {
    if (Test-Path $keystore) { Remove-Item $keystore -Confirm:$false }
    & "$javaBin\keytool.exe" -genkeypair -alias lifeline -keyalg RSA -keysize 2048 -validity 397 -storetype PKCS12 `
        -keystore $keystore -storepass:env LIFELINE_KEYSTORE_PASSWORD -dname 'CN=LifeLine' `
        -ext "SAN=ip:$ip,ip:127.0.0.1,dns:localhost"
    Assert-Success 'Creating the HTTPS certificate'
    Set-Content $certIpFile $ip -Encoding ascii
}

# --- Run ---------------------------------------------------------------------
$settings = Get-Content (Join-Path $backend 'src/main/resources/application.properties') -Raw | ConvertFrom-StringData
Write-Host ''
Write-Host '  Dashboard (this computer)   http://localhost:8080/dashboard/'
Write-Host "  Device page (phones etc.)   https://${ip}:8443/device/"
Write-Host '  Device page (this computer) http://localhost:8080/device/'
Write-Host ''
Write-Host "  Dashboard sign-in           $($settings['lifeline.dashboard.username']) / $($settings['lifeline.dashboard.password'])"
Write-Host ''
Write-Host '  KEEP THIS WINDOW OPEN. Pressing Ctrl+C stops the server.'
Write-Host '  To copy the password, select it and right-click.'
Write-Host ''
Push-Location $backend
try {
    & "$javaBin\java.exe" -jar 'target\battery-monitor-0.1.0.jar' '--spring.profiles.active=lan'
} finally { Pop-Location }
