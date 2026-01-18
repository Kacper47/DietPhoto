# Minimalny skrypt budujący debug APK.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $repoRoot
try {
    Write-Host "Buduję debug APK (assembleDebug)..."
    .\gradlew.bat assembleDebug

    $apkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apkPath)) { throw "Brak APK: $apkPath" }

    $dist = Join-Path $repoRoot "dist"
    if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

    $target = Join-Path $dist "DietPhoto-debug.apk"
    Copy-Item $apkPath $target -Force
    Write-Host "Gotowe: $target"
} finally {
    Pop-Location
}
