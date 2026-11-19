$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectDir
$logDir = Join-Path $env:TEMP 'english-center-demo'
New-Item -ItemType Directory -Force $logDir | Out-Null

function Test-Ready([string]$Url) {
    try { return (Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 }
    catch { return $false }
}
function Wait-Ready([string]$Url, $Process, [int]$Seconds, [string]$LogName) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        if (Test-Ready $Url) { return }
        if ($Process.HasExited) { throw "Process stopped. Check logs: $logDir\$LogName.*.log" }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Startup timed out. Check logs: $logDir\$LogName.*.log"
}
function Assert-PortFree([int]$Port) {
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw "Port $Port is in use by another app. Stop that app before starting demo."
    }
}

try {
    if (!(Test-Ready 'http://127.0.0.1:8085/api/auth/csrf')) {
        Assert-PortFree 8085
        $candidates = @($env:JAVA_HOME)
        $candidates += @(Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName)
        $jdk = $null
        foreach ($candidate in $candidates) {
            if (!$candidate) { continue }
            $compiler = Join-Path $candidate 'bin\javac.exe'
            if (Test-Path -LiteralPath $compiler) {
                $version = & $env:ComSpec /d /c "`"$compiler`" -version 2>&1" | Out-String
                if ($version -match 'javac (\d+)' -and [int]$Matches[1] -ge 21) { $jdk = $candidate; break }
            }
        }
        if (!$jdk) { throw 'Install JDK 21 or newer and set JAVA_HOME before running demo.' }
        $env:JAVA_HOME = $jdk
        $env:PATH = "$jdk\bin;$env:PATH"
        if (!$env:DB_PASSWORD) {
            $secret = Read-Host 'MySQL password (not saved)' -AsSecureString
            $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
        }
        $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/english_center_demo?useSSL=false&allowPublicKeyRetrieval=true'
        $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
        $env:SERVER_ADDRESS = '127.0.0.1'
        $env:SERVER_PORT = '8085'
        $env:SEPAY_DEMO = 'true'
        $env:AUTH_EMAIL_DEMO = 'true'
        Write-Host 'Starting backend on 8085...'
        $backend = Start-Process -FilePath $env:ComSpec -ArgumentList '/d /c mvnw.cmd spring-boot:run' -WorkingDirectory $projectDir -WindowStyle Hidden -RedirectStandardOutput "$logDir\backend.out.log" -RedirectStandardError "$logDir\backend.err.log" -PassThru
        Wait-Ready 'http://127.0.0.1:8085/api/auth/csrf' $backend 180 'backend'
    } else { Write-Host 'Backend is already running on 8085.' }

    if (!(Test-Ready 'http://127.0.0.1:5175/')) {
        Assert-PortFree 5175
        if (!(Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js LTS before running demo.' }
        $frontendDir = Join-Path $projectDir 'frontend'
        if (!(Test-Path (Join-Path $frontendDir 'node_modules'))) {
            Push-Location $frontendDir
            try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' } }
            finally { Pop-Location }
        }
        # Vite uses backend 8085 by default. Avoid an old inherited override.
        Remove-Item Env:BACKEND_URL -ErrorAction SilentlyContinue
        Write-Host 'Starting frontend on 5175...'
        $frontend = Start-Process -FilePath $env:ComSpec -ArgumentList '/d /c npm.cmd run dev -- --host 127.0.0.1 --port 5175 --strictPort' -WorkingDirectory $frontendDir -WindowStyle Hidden -RedirectStandardOutput "$logDir\frontend.out.log" -RedirectStandardError "$logDir\frontend.err.log" -PassThru
        Wait-Ready 'http://127.0.0.1:5175/' $frontend 60 'frontend'
    } else { Write-Host 'Frontend is already running on 5175.' }
    if (!(Test-Ready 'http://127.0.0.1:5175/api/auth/csrf')) { throw 'Frontend cannot reach backend. Restart the existing frontend with the current Vite configuration.' }
    Start-Process 'http://127.0.0.1:5175/'
    Write-Host "Demo ready: http://127.0.0.1:5175/ - logs: $logDir"
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
