[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$Test,
    [switch]$Run,
    [switch]$RefreshDependencies,
    [switch]$NoDaemon
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

$validJdkHome = $null

if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $checkBin = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $checkBin -PathType Leaf) {
        $validJdkHome = (Resolve-Path -LiteralPath $env:JAVA_HOME).Path
    }
}

if ($null -eq $validJdkHome) {
    $candidatePatterns = @(
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\BellSoft\LibericaJDK-21*",
        "C:\Program Files\Amazon Corretto\jdk21*",
        "$env:USERPROFILE\.jdks\*21*"
    )
    foreach ($pattern in $candidatePatterns) {
        $found = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object Name -Descending
        foreach ($dir in $found) {
            $testExe = Join-Path $dir.FullName "bin\java.exe"
            if (Test-Path -LiteralPath $testExe -PathType Leaf) {
                $validJdkHome = $dir.FullName
                break
            }
        }
        if ($null -ne $validJdkHome) { break }
    }
}

# 3. Thu tim qua lenh java.exe trong PATH neu nam trong thu muc bin
if ($null -eq $validJdkHome) {
    $javaCommand = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $parent = Split-Path -Parent $javaCommand.Source
        if ((Split-Path -Leaf $parent) -ieq "bin") {
            $validJdkHome = Split-Path -Parent $parent
        }
    }
}

if ($null -eq $validJdkHome) {
    throw "Khong tim thay JDK 21 hop le. Vui long cai JDK 21 hoac dat bien moi truong JAVA_HOME."
}

$env:JAVA_HOME = $validJdkHome
$javaExecutable = Join-Path $validJdkHome "bin\java.exe"

$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = $javaExecutable
$pinfo.Arguments = "-version"
$pinfo.RedirectStandardError = $true
$pinfo.RedirectStandardOutput = $true
$pinfo.UseShellExecute = $false
$p = [System.Diagnostics.Process]::Start($pinfo)
$javaVersionOutput = $p.StandardError.ReadToEnd() + [Environment]::NewLine + $p.StandardOutput.ReadToEnd()
$p.WaitForExit()

if ($p.ExitCode -ne 0) {
    throw "Khong the chay Java tai: $javaExecutable"
}

if ($javaVersionOutput -match 'version\s+"(?<major>\d+)') {
    $javaMajorVersion = [int]$Matches.major
    if ($javaMajorVersion -lt 21) {
        throw "Can JDK 21 tro len de build (dang dung Java $javaMajorVersion tai: $javaExecutable)."
    }
}

$gradleArguments = [System.Collections.Generic.List[string]]::new()

if ($NoDaemon) {
    $gradleArguments.Add("--no-daemon")
}
else {
    $gradleArguments.Add("--daemon")
}

if ($Clean) {
    $gradleArguments.Add("clean")
}

if ($Test) {
    $gradleArguments.Add("build")
    $gradleArguments.Add("pricingRegressionTest")
}
else {
    $gradleArguments.Add("assemble")
}

if ($RefreshDependencies) {
    $gradleArguments.Add("--refresh-dependencies")
}

if ($Run) {
    $gradleArguments.Add("runClient")
}

Push-Location $projectRoot
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
try {
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host " [Veloria] Dang build mod (Fast Incremental)..." -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan

    & $gradleWrapper $gradleArguments

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build that bai (exit code: $LASTEXITCODE)."
    }

    $stopwatch.Stop()
    $duration = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2)

    $jar = Get-ChildItem -LiteralPath $libsDirectory -Filter "*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -notmatch '-(sources|javadoc|dev|all-dev)\.jar$'
    } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host " Build thanh cong trong ${duration}s!" -ForegroundColor Green
    if ($null -ne $jar) {
        Write-Host " File JAR  : $($jar.Name)" -ForegroundColor Yellow
        Write-Host " Duong dan : $($jar.FullName)" -ForegroundColor Gray
        Write-Host " Dung luong: $([math]::Round($jar.Length / 1MB, 2)) MB" -ForegroundColor Yellow
    }
    Write-Host "==========================================" -ForegroundColor Green
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
