[CmdletBinding()]
param(
    [string]$JavaPath = "java",
    [switch]$Background,
    [switch]$Check
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$localConfigPath = Join-Path $repositoryRoot "backend/.env.local"
$backendJarPath = Join-Path $repositoryRoot "backend/target/deadlinr-backend-0.0.1-SNAPSHOT.jar"
$allowedNames = @(
    "APP_OAUTH_ENABLED", "APP_PUBLIC_URL", "APP_COOKIE_SECURE",
    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GITHUB_CLIENT_ID", "GITHUB_CLIENT_SECRET"
)

if (!(Test-Path -LiteralPath $localConfigPath -PathType Leaf)) {
    throw "Missing backend/.env.local. See README.md for local credentials."
}
if (!(Test-Path -LiteralPath $backendJarPath -PathType Leaf)) {
    throw "Build the backend JAR first: run mvn package in backend/."
}

$localValues = @{}
$lineNumber = 0
foreach ($configLine in [System.IO.File]::ReadAllLines($localConfigPath)) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($configLine) -or $configLine.TrimStart().StartsWith("#")) {
        continue
    }
    if ($configLine -notmatch '^([A-Z_]+)=(.*)$') {
        throw "Invalid local configuration at line $lineNumber. Use NAME=value without shell commands."
    }
    $configName = $matches[1]
    $configValue = $matches[2]
    if ($configName -notin $allowedNames -or $localValues.ContainsKey($configName)) {
        throw "Unsupported or repeated local configuration name at line $lineNumber."
    }
    $localValues[$configName] = $configValue
}

if ($localValues["APP_OAUTH_ENABLED"] -ne "true" -or
    $localValues["APP_PUBLIC_URL"] -ne "http://localhost:3000") {
    throw "This local launcher requires OAuth enabled and APP_PUBLIC_URL=http://localhost:3000."
}
foreach ($provider in @("GOOGLE", "GITHUB")) {
    $hasId = ![string]::IsNullOrWhiteSpace($localValues["${provider}_CLIENT_ID"])
    $hasSecret = ![string]::IsNullOrWhiteSpace($localValues["${provider}_CLIENT_SECRET"])
    if ($hasId -ne $hasSecret) {
        throw "$provider requires both a client ID and a client secret."
    }
}
if ($Check) {
    Write-Output "Local OAuth configuration and backend JAR are present. No server was started."
    return
}
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
    throw "Port 8080 is already in use. Stop your existing backend before starting this launcher."
}

$previousValues = @{}
try {
    foreach ($configName in $localValues.Keys) {
        $previousValues[$configName] = [Environment]::GetEnvironmentVariable($configName, "Process")
        [Environment]::SetEnvironmentVariable($configName, $localValues[$configName], "Process")
    }
    if ($Background) {
        $stdoutPath = Join-Path $repositoryRoot "backend/target/local-backend.log"
        $stderrPath = Join-Path $repositoryRoot "backend/target/local-backend-error.log"
        $backendProcess = Start-Process -FilePath $JavaPath -ArgumentList @("-jar", ('"{0}"' -f $backendJarPath)) `
            -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
        Write-Output "Backend started as process $($backendProcess.Id). Log: backend/target/local-backend.log"
    } else {
        Push-Location $repositoryRoot
        try {
            & $JavaPath -jar $backendJarPath
            if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
        } finally {
            Pop-Location
        }
    }
} finally {
    foreach ($configName in $previousValues.Keys) {
        if ($null -eq $previousValues[$configName]) {
            Remove-Item -LiteralPath "Env:\$configName" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($configName, $previousValues[$configName], "Process")
        }
    }
}
