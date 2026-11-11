#!/bin/sh
# ==============================================================================
# build-and-install.sh untuk Crb Launcher (Project: CrbLauncher | Package: com.crb.tv)
# ==============================================================================
set -e

SCRIPT_DIR=$( cd "${0%/*}" && pwd -P )
if [ -f "$SCRIPT_DIR/settings.gradle" ]; then
    ROOT_DIR="$SCRIPT_DIR"
else
    ROOT_DIR=$( cd "$SCRIPT_DIR/.." && pwd -P )
fi

cd "$ROOT_DIR"

# Otomatis mendeteksi lokasi APK (baik dari root app/ AndroidIDE maupun launcher/app/)
find_apk_path() {
    if [ -f "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    elif [ -f "$ROOT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
        echo "$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
    elif [ -f "$ROOT_DIR/launcher/app/build/outputs/apk/release/app-release.apk" ]; then
        echo "$ROOT_DIR/launcher/app/build/outputs/apk/release/app-release.apk"
    elif [ -f "$ROOT_DIR/launcher/app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo "$ROOT_DIR/launcher/app/build/outputs/apk/debug/app-debug.apk"
    else
        echo ""
    fi
}

if [ "${SKIP_BUILD:-0}" != "1" ]; then
    echo "[Crb Launcher] Memulai build APK CrbLauncher (com.crb.tv)..."
    chmod +x ./gradlew 2>/dev/null || true
    ./gradlew assembleDebug assembleRelease --no-daemon
fi

APK_PATH=$(find_apk_path)
if [ -z "$APK_PATH" ]; then
    echo "[ERROR] File APK Crb Launcher tidak ditemukan setelah build."
    exit 1
fi

mkdir -p "$ROOT_DIR/hasil-apk"
cp "$APK_PATH" "$ROOT_DIR/hasil-apk/CrbLauncher-com.crb.tv.apk"
echo "[OK] APK Crb Launcher tersedia di: $ROOT_DIR/hasil-apk/CrbLauncher-com.crb.tv.apk"

if [ -z "$TV_HOST" ]; then
    echo "[INFO] Build selesai. Untuk langsung menginstal ke Android TV via Wi-Fi ADB, jalankan:"
    echo "       TV_HOST=192.168.1.50 ./build-and-install.sh"
    exit 0
fi

echo "[Crb Launcher] Menghubungkan dan menginstal $APK_PATH ke Android TV ($TV_HOST:5555)..."
adb connect "$TV_HOST:5555"
adb -s "$TV_HOST:5555" install -r "$APK_PATH"

# Set Crb Launcher (com.crb.tv) sebagai Default Home Activity & Daydream Screen Saver
adb -s "$TV_HOST:5555" shell cmd package set-home-activity --user 0 com.crb.tv/.MainActivity || true
adb -s "$TV_HOST:5555" shell settings put secure screensaver_components com.crb.tv/.ClockDream || true
adb -s "$TV_HOST:5555" shell settings put secure screensaver_enabled 1 || true

# Jalankan Crb Launcher di layar TV
adb -s "$TV_HOST:5555" shell am start -n com.crb.tv/.MainActivity
echo "[SELESAI] Crb Launcher (com.crb.tv) sudah aktif di Android TV $TV_HOST!"
