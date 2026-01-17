# Simple helper to build an installable APK.
# Usage:
#   pwsh ./build-apk.ps1          # builds debug (signed with debug keystore)
#   pwsh ./build-apk.ps1 -Release # builds release (requires signing config)
param(
    [switch]$Release
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $repoRoot
try {
    $variant = if ($Release) { "Release" } else { "Debug" }
    $task = "assemble$variant"
    Write-Host "Building variant '$variant' via Gradle task '$task'..."

    $gradleCmd = if ($IsWindows) { ".\\gradlew.bat" } else { "./gradlew" }
    & $gradleCmd $task

    $apkName = "app-{0}.apk" -f $variant.ToLower()
    $apkPath = Join-Path $repoRoot "app\\build\\outputs\\apk\\$($variant.ToLower())\\$apkName"
    if (-not (Test-Path $apkPath)) {
        throw "APK not found at $apkPath. Check Gradle output for errors."
    }

    $distDir = Join-Path $repoRoot "dist"
    if (-not (Test-Path $distDir)) {
        New-Item -ItemType Directory -Path $distDir | Out-Null
    }

    $targetName = "DietPhoto-$($variant.ToLower()).apk"
    $targetPath = Join-Path $distDir $targetName
    Copy-Item $apkPath $targetPath -Force

    Write-Host "Done. APK copied to $targetPath"
    Write-Host "Share that file or install with: adb install -r `"$targetPath`""
} finally {
    Pop-Location
}
