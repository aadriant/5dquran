@echo off
setlocal enabledelayedexpansion
echo ===================================================
echo  5D Quran - Build Pure Native Android APK
echo ===================================================

echo [1/3] Menyiapkan aset dan data offline...
if not exist "android\app\src\main\assets" mkdir "android\app\src\main\assets"
copy /Y "quran_complete.json" "android\app\src\main\assets\quran.json" >nul 2>&1
copy /Y "surahs_meta.json" "android\app\src\main\assets\surahs.json" >nul 2>&1
copy /Y "data\tafsir-jalalayn.json" "android\app\src\main\assets\tafsir_jalalayn.json" >nul 2>&1
copy /Y "data\hadis\hadis-ayat.json" "android\app\src\main\assets\hadis_ayat.json" >nul 2>&1

echo [2/3] Mengompilasi Pure Native Android APK...
set BUILD_SUCCESS=0

REM Gunakan Docker Android Builder container jika tersedia
docker --version >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo Menggunakan Docker Android Builder container...
    docker run --rm -v "gradle_cache:/root/.gradle" -v "%cd%\android:/project" -w /project -e ANDROID_HOME=/opt/android-sdk -e ANDROID_SDK_ROOT=/opt/android-sdk t2p-android-builder:latest sh -c "./gradlew assembleDebug"
    if !ERRORLEVEL! equ 0 set BUILD_SUCCESS=1
)

if %BUILD_SUCCESS% neq 1 (
    echo Mencoba compile dengan Gradle host...
    cd android
    call gradlew.bat assembleDebug
    if !ERRORLEVEL! equ 0 set BUILD_SUCCESS=1
    cd ..
)

if exist "android\app\build\outputs\apk\debug\app-debug.apk" (
    echo [3/3] Menyalin APK ke folder utama...
    copy /Y "android\app\build\outputs\apk\debug\app-debug.apk" "5D-Quran-v1.0.1-Native.apk" >nul
    copy /Y "android\app\build\outputs\apk\debug\app-debug.apk" "5D-Quran-v1.0.0-beta-Native.apk" >nul
    copy /Y "android\app\build\outputs\apk\debug\app-debug.apk" "5D-Quran-v1.0.0-beta.apk" >nul
    copy /Y "android\app\build\outputs\apk\debug\app-debug.apk" "5dquran-debug.apk" >nul
    echo.
    echo ===================================================
    echo  BERHASIL! PURE NATIVE APK v1.0.1 SIAP DIGUNAKAN
    echo ===================================================
    echo  File APK: %cd%\5D-Quran-v1.0.1-Native.apk
    echo ===================================================
) else (
    echo.
    echo [ERROR] Build APK gagal. Periksa pesan error di atas.
)

pause
