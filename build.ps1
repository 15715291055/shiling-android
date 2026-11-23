$ErrorActionPreference = "Stop"

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    Write-Host "未检测到 ANDROID_HOME / ANDROID_SDK_ROOT。请先在 Android Studio SDK Manager 安装 Android SDK Platform 35。" -ForegroundColor Yellow
    exit 1
}

$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
$env:TEMP = Join-Path $PSScriptRoot '.tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force $env:TEMP, (Join-Path $PSScriptRoot 'signing') | Out-Null
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path '.\signing\debug.keystore')) {
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore '.\signing\debug.keystore' -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' -storepass android -keypass android
    if ($LASTEXITCODE -ne 0) { throw 'Debug key creation failed' }
}
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
Write-Host "Debug APK 已生成：app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
