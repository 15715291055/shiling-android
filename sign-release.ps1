param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'D:\Codex\android-build-tools\jdk17')) { $env:JAVA_HOME = 'D:\Codex\android-build-tools\jdk17' }
if (-not $env:ANDROID_HOME -and (Test-Path 'D:\Codex\android-sdk')) { $env:ANDROID_HOME = 'D:\Codex\android-sdk' }
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) { throw 'Set JAVA_HOME (JDK 17) and ANDROID_HOME (SDK 35) first.' }
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
$env:TEMP = Join-Path $PSScriptRoot '.tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force '.tmp', 'signing', 'deliverables', 'verification' | Out-Null
if (-not (Test-Path '.\signing\debug.keystore')) {
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore '.\signing\debug.keystore' -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' -storepass android -keypass android
    if ($LASTEXITCODE -ne 0) { throw 'Debug key creation failed' }
}
if (-not $SkipBuild) {
    & '.\gradlew.bat' assembleDebug assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
}
$passwordPath = Join-Path $PSScriptRoot 'signing\release-password.txt'
$keyPath = Join-Path $PSScriptRoot 'signing\fuguang-release.p12'
if (-not (Test-Path -LiteralPath $keyPath)) {
    if (-not (Test-Path -LiteralPath $passwordPath)) {
        $randomBytes = [byte[]]::new(32)
        [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
        [IO.File]::WriteAllText($passwordPath, [Convert]::ToBase64String($randomBytes))
    }
    $env:FUGUANG_STORE_PASSWORD = [IO.File]::ReadAllText($passwordPath).Trim()
    try {
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keyPath -storetype PKCS12 -alias fuguang -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Fuguang Local Diary' -storepass:env FUGUANG_STORE_PASSWORD -keypass:env FUGUANG_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key creation failed' }
    } finally { Remove-Item Env:FUGUANG_STORE_PASSWORD -ErrorAction SilentlyContinue }
}
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0'
$aligned = Join-Path $PSScriptRoot '.tmp\release-aligned.apk'
$release = Join-Path $PSScriptRoot 'deliverables\Shiling-1.2.3-release.apk'
& "$buildTools\zipalign.exe" -f -P 16 4 '.\app\build\outputs\apk\release\app-release-unsigned.apk' $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& "$buildTools\apksigner.bat" sign --ks $keyPath --ks-key-alias fuguang --ks-pass "file:$passwordPath" --out $release $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& "$buildTools\apksigner.bat" verify --verbose --print-certs $release | Tee-Object '.\verification\release-signature.txt'
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Copy-Item '.\app\build\outputs\apk\debug\app-debug.apk' '.\deliverables\Shiling-1.2.3-debug.apk' -Force
Get-FileHash '.\deliverables\*.apk' -Algorithm SHA256 | Format-List | Out-File '.\verification\apk-sha256.txt'
Write-Host "Signed APK: $release"
