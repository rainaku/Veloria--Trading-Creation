[CmdletBinding()]
param(
    [switch]$RefreshDependencies
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
$libsDirectory = Join-Path $projectRoot "build\libs"
$originalJavaHome = $env:JAVA_HOME
$hadJavaHome = Test-Path Env:JAVA_HOME

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Khong tim thay Gradle Wrapper: $gradleWrapper"
}

$javaExecutable = $null
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaFromHome = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $javaFromHome -PathType Leaf) {
        $javaExecutable = (Resolve-Path -LiteralPath $javaFromHome).Path
    }
}

if ($null -eq $javaExecutable) {
    $javaCommand = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $javaExecutable = $javaCommand.Source
    }
}

if ($null -eq $javaExecutable) {
    throw "Khong tim thay Java. Hay cai JDK 21, dat JAVA_HOME, roi chay lai .\r.ps1."
}

$javaVersionOutput = (& $javaExecutable -version 2>&1) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0) {
    throw "Khong the chay Java tai: $javaExecutable"
}

if ($javaVersionOutput -match 'version\s+"(?<major>\d+)') {
    $javaMajorVersion = [int]$Matches.major
    if ($javaMajorVersion -lt 21) {
        throw "Can JDK 21 tro len de build (dang dung Java $javaMajorVersion tai: $javaExecutable)."
    }
}

# Gradle Wrapper uu tien JAVA_HOME. Dat lai trong rieng tien trinh nay neu
# JAVA_HOME cua may dang sai nhung java.exe van co san trong PATH.
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExecutable)

$gradleArguments = @("--no-daemon", "clean", "build")
if ($RefreshDependencies) {
    $gradleArguments += "--refresh-dependencies"
}

Push-Location $projectRoot
try {
    Write-Host "Dang build JAR tu source hien tai..." -ForegroundColor Cyan
    & $gradleWrapper @gradleArguments

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build that bai (exit code: $LASTEXITCODE)."
    }

    $jar = Get-ChildItem -LiteralPath $libsDirectory -Filter "*.jar" -File |
        Where-Object {
            $_.Name -notmatch '-(sources|javadoc|dev|all-dev)\.jar$'
        } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1

    if ($null -eq $jar) {
        throw "Build thanh cong nhung khong tim thay JAR phat hanh trong: $libsDirectory"
    }

    Write-Host ""
    Write-Host "Build thanh cong!" -ForegroundColor Green
    Write-Host "JAR: $($jar.FullName)"
    Write-Host "Kich thuoc: $([math]::Round($jar.Length / 1MB, 2)) MB"
}
finally {
    Pop-Location
    if ($hadJavaHome) {
        $env:JAVA_HOME = $originalJavaHome
    }
    else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
}
