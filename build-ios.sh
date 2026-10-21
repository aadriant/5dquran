#!/bin/bash
echo "========================================"
echo " 5D Quran - Sync Web ke iOS (macOS)"
echo "========================================"

echo "[1/3] Menyalin file web ke www/..."
cp index.html www/
cp surah.html www/
cp pelajari.html www/
cp tulis.html www/
cp hafalan.html www/
cp -r css www/
cp -r js www/
cp -r data www/
cp -r assets www/

echo "[2/3] Cek iOS platform..."
if [ ! -d "ios" ]; then
  echo "Menambahkan iOS platform..."
  npx cap add ios
fi

echo "[3/3] Syncing ke Capacitor iOS..."
npx cap sync ios

echo ""
echo "========================================"
echo " SELESAI! Buka Xcode dengan:"
echo " npx cap open ios"
echo " (atau buka folder ios/App di Xcode)"
echo "========================================"
