$ErrorActionPreference = "Stop"

# 1. Determine Project Directory Dynamically (Zero Hardcoded User Paths)
$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = (Get-Location).Path
}

# 2. Locate Android SDK Dynamically
$PossibleSdkPaths = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    "$env:LOCALAPPDATA\Android\Sdk",
    "C:\Android\Sdk"
)

$SdkPath = $null
foreach ($path in $PossibleSdkPaths) {
    if (![string]::IsNullOrEmpty($path) -and (Test-Path $path)) {
        $SdkPath = $path
        break
    }
}

if ($null -eq $SdkPath) {
    Write-Error "Android SDK not found. Please set the ANDROID_HOME environment variable or install Android SDK."
    exit 1
}

# Locate Highest Build-Tools Version
$BuildToolsDir = Get-ChildItem -Path "$SdkPath\build-tools" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $BuildToolsDir) {
    Write-Error "No Android build-tools found inside $SdkPath\build-tools."
    exit 1
}
$BuildTools = $BuildToolsDir.FullName

# Locate Highest Android Platform
$PlatformDir = Get-ChildItem -Path "$SdkPath\platforms" -Directory -Filter "android-*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $PlatformDir) {
    Write-Error "No Android platforms found inside $SdkPath\platforms."
    exit 1
}
$AndroidJar = Join-Path $PlatformDir.FullName "android.jar"

# Build Tool Executables
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$ZipAlign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"

# Locate Jar Executable
$Jar = "jar"
if (![string]::IsNullOrEmpty($env:JAVA_HOME) -and (Test-Path "$env:JAVA_HOME\bin\jar.exe")) {
    $Jar = "$env:JAVA_HOME\bin\jar.exe"
}

# Project Paths
$AppDir = Join-Path $ProjectDir "app\src\main"
$Manifest = Join-Path $AppDir "AndroidManifest.xml"
$ResDir = Join-Path $AppDir "res"
$JavaDir = Join-Path $AppDir "java"
$AssetsDir = Join-Path $AppDir "assets"

$BinDir = Join-Path $ProjectDir "bin"
$ObjDir = Join-Path $ProjectDir "obj"
$CompiledRes = Join-Path $ObjDir "compiled_res.zip"
$ClassesDir = Join-Path $ObjDir "classes"
$DexDir = Join-Path $ObjDir "dex"
$UnsignedApk = Join-Path $BinDir "openprop-unsigned.apk"
$AlignedApk = Join-Path $BinDir "openprop-aligned.apk"
$FinalApk = Join-Path $BinDir "openprop.apk"

# Ensure Output Folders
if (Test-Path $BinDir) { Remove-Item -Recurse -Force $BinDir }
if (Test-Path $ObjDir) { Remove-Item -Recurse -Force $ObjDir }
New-Item -ItemType Directory -Force -Path $BinDir | Out-Null
New-Item -ItemType Directory -Force -Path $ObjDir | Out-Null
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Force -Path $DexDir | Out-Null

Write-Host "==> 1. Compiling Android Resources with AAPT2..."
& $Aapt2 compile --dir $ResDir -o $CompiledRes

& $Aapt2 link -I $AndroidJar `
    --manifest $Manifest `
    --min-sdk-version 24 `
    --target-sdk-version 34 `
    --version-code 1 `
    --version-name "1.0.0" `
    -o $UnsignedApk `
    $CompiledRes

Write-Host "==> 2. Adding Assets with Jar..."
if (Test-Path $AssetsDir) {
    Push-Location $AppDir
    & $Jar -uf $UnsignedApk assets
    Pop-Location
}

Write-Host "==> 3. Compiling Java Sources with javac (UTF-8)..."
$JavaFiles = Get-ChildItem -Path $JavaDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -cp $AndroidJar -d $ClassesDir $JavaFiles

Write-Host "==> 4. Converting to DEX bytecode with D8..."
$ClassFiles = Get-ChildItem -Path $ClassesDir -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
& cmd.exe /c "$D8 --min-api 24 --output $DexDir $($ClassFiles -join ' ')"

Write-Host "==> 5. Packaging classes.dex into APK..."
& $Jar -uf $UnsignedApk -C $DexDir classes.dex

Write-Host "==> 6. ZipAligning APK..."
& $ZipAlign -f -p 4 $UnsignedApk $AlignedApk

Write-Host "==> 7. Signing APK with Build Keystore (V1, V2, V3 scheme)..."
$Keystore = Join-Path $ObjDir "build.keystore"
if (!(Test-Path $Keystore)) {
    & keytool -genkeypair -v -keystore $Keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" | Out-Null
}

& cmd.exe /c "$ApkSigner sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --ks $Keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $FinalApk $AlignedApk"

Write-Host "==> SUCCESS! Output APK generated at: $FinalApk"
