param(
    [switch]$Debug,
    [switch]$AllBoxes
)

$adb = "C:\Users\buick\android-sdk\platform-tools\adb.exe"
$onnBox = "192.168.1.128:5555"
$fireBox = "192.168.1.173:5555"
# The project's stable signing key, kept outside the repo. Release builds must use it or Android
# refuses to install over the boxes' existing installs (INSTALL_FAILED_UPDATE_INCOMPATIBLE), which
# is also what breaks the in-app updater.
$keysDir = "C:\Users\buick\opentv-keys"

function Use-StableSigningKey {
    $envFile = Join-Path $keysDir "PASSWORDS.txt"
    if (-not (Test-Path -LiteralPath $envFile)) {
        Write-Warning "No signing key at $envFile - the release build will fall back to the debug key and the boxes will reject it."
        return
    }
    $lines = Get-Content -LiteralPath $envFile
    $env:KEYSTORE_PATH = (($lines | Where-Object { $_ -like 'path=*' }) -replace '^path=', '')
    $env:KEYSTORE_PASSWORD = (($lines | Where-Object { $_ -like 'storepass=*' }) -replace '^storepass=', '')
    $env:KEY_PASSWORD = (($lines | Where-Object { $_ -like 'keypass=*' }) -replace '^keypass=', '')
    $env:KEY_ALIAS = (($lines | Where-Object { $_ -like 'alias=*' }) -replace '^alias=', '')
}

$targets = if ($AllBoxes) { @($onnBox, $fireBox) } else { @($onnBox) }

if ($Debug) {
    Write-Host "==> Building Debug APK..." -ForegroundColor Cyan
    & .\gradlew.bat :app:assembleDebug -PdevMinSdk=28 --console=plain
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
} else {
    Write-Host "==> Building Release APK (fast & memory-optimized)..." -ForegroundColor Cyan
    Use-StableSigningKey
    & .\gradlew.bat :app:assembleRelease -PdevMinSdk=28 --console=plain
    $apkPath = "app\build\outputs\apk\release\app-release.apk"
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Copy-Item $apkPath "iptv.apk" -Force

Write-Host "==> Pushing to: $($targets -join ', ')..." -ForegroundColor Cyan
$processes = @()
foreach ($target in $targets) {
    $p = Start-Process -FilePath $adb -ArgumentList "-s $target install -r iptv.apk" -PassThru -NoNewWindow
    $processes += $p
}
$processes | Wait-Process

Write-Host "==> Deployment complete. (App was not auto-launched per rules)." -ForegroundColor Green
