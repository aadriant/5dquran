@echo off
setlocal enabledelayedexpansion
title Install APK Langsung ke HP Android

echo ========================================================
echo   INSTALL LANGSUNG 5D QURAN KE HP ANDROID (ADB)
echo ========================================================
echo.

:: 1. Cek keberadaan adb
set ADB_CMD=adb
where adb >nul 2>nul
if %errorlevel% neq 0 (
    if exist "platform-tools\adb.exe" (
        set ADB_CMD=platform-tools\adb.exe
    ) else (
        echo [1/3] Menyiapkan Android Platform-Tools (ADB) dari Google...
        powershell -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile 'platform-tools.zip'"
        powershell -Command "Expand-Archive -Path 'platform-tools.zip' -DestinationPath '.' -Force"
        del platform-tools.zip
        set ADB_CMD=platform-tools\adb.exe
        echo [V] ADB berhasil diunduh!
    )
)

:: 2. Cek perangkat terhubung
echo [2/3] Memeriksa koneksi HP Android...
%ADB_CMD% devices

echo.
echo Pastikan di HP Anda:
echo 1. Opsi Pengembang (Developer Options) sudah AKTIF.
echo 2. "Debugging USB" (USB Debugging) sudah AKTIF.
echo 3. Izinkan popup "Allow USB Debugging?" jika muncul di layar HP.
echo.

:: 3. Cari file APK terbaru
set APK_FILE=
for /f "delims=" %%a in ('dir /b /o-d *Quran*.apk 2^>nul') do (
    if not defined APK_FILE set APK_FILE=%%a
)

if not defined APK_FILE (
    if exist "android\app\build\outputs\apk\debug\app-debug.apk" (
        set APK_FILE=android\app\build\outputs\apk\debug\app-debug.apk
    ) else (
        echo [X] File APK tidak ditemukan. Harap build terlebih dahulu.
        pause
        exit /b 1
    )
)

echo [3/3] Menginstall %APK_FILE% langsung ke HP...
%ADB_CMD% install -r "%APK_FILE%"

if %errorlevel% equ 0 (
    echo.
    echo ========================================================
    echo [V] SUKSES! Aplikasi terinstall di HP.
    echo Membuka aplikasi di HP secara otomatis...
    echo ========================================================
    %ADB_CMD% shell am start -n com.dquran.app5d/.MainActivity
) else (
    echo.
    echo [!] Gagal install otomatis.
    echo Pastikan HP terhubung dengan kabel USB dan USB Debugging aktif,
    echo atau gunakan cara Web Server: jalankan 'python share-apk.py'.
)

echo.
pause
