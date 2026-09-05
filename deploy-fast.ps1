param(
    [switch]$Debug,
    [switch]$AllBoxes
)

$adb = "C:\Users\buick\android-sdk\platform-tools\adb.exe"
$onnBox = "192.168.1.128:5555"
$fireBox = "192.168.1.173:5555"

$targets = if ($AllBoxes) { @($onnBox, $fireBox) } else { @($onnBox) }

if ($Debug) {
    Write-Host "==> Building Debug APK..." -ForegroundColor Cyan
    & .\gradlew.bat :app:assembleDebug -PdevMinSdk=28 --console=plain
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
} else {
    Write-Host "==> Building Release APK (fast & memory-optimized)..." -ForegroundColor Cyan
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
