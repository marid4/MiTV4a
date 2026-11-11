#!/bin/sh
# ==============================================================================
# Script Build & Install APK Crb Launcher (com.crb.tv) Langsung di HP Android
# Mendukung Termux & Hasil Build AndroidIDE (/storage/emulated/0/AndroidIDEProjects/CrbLauncher)
# ==============================================================================
set -e

echo "================================================================"
echo "  Crb Launcher (com.crb.tv) - Builder & Installer HP (Termux)   "
echo "================================================================"

# 1. Cek apakah dijalankan di dalam Termux Android
if [ -n "$TERMUX_VERSION" ] || [ -d "/data/data/com.termux/files/usr" ]; then
    echo "[1/4] Lingkungan Termux terdeteksi. Memastikan paket JDK 17, Gradle & Android Tools terpasang..."
    pkg update -y
    pkg install -y openjdk-17 gradle android-tools wget unzip git
else
    echo "[1/4] Menjalankan di lingkungan Linux/POSIX standar..."
fi

# 2. Pastikan Android SDK Platform 34 tersedia
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

if [ ! -f "$SDK_DIR/platforms/android-34/android.jar" ]; then
    echo "[2/4] Menyiapkan Android SDK Platform 34 di $SDK_DIR..."
    mkdir -p "$SDK_DIR/platforms/android-34"
    mkdir -p "$SDK_DIR/cmdline-tools"

    if command -v sdkmanager >/dev/null 2>&1; then
        yes | sdkmanager --sdk_root="$SDK_DIR" --licenses || true
        sdkmanager --sdk_root="$SDK_DIR" "platforms;android-34" "build-tools;34.0.0"
    else
        echo "Mengunduh Command-Line Tools Android..."
        wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O "$SDK_DIR/cmdline.zip"
        unzip -q -o "$SDK_DIR/cmdline.zip" -d "$SDK_DIR/cmdline-tools"
        mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest" 2>/dev/null || true
        rm -f "$SDK_DIR/cmdline.zip"
        yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" --licenses || true
        "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" "platforms;android-34" "build-tools;34.0.0"
    fi
fi

echo "sdk.dir=$SDK_DIR" > local.properties
if [ -d "launcher" ]; then
    echo "sdk.dir=$SDK_DIR" > launcher/local.properties
fi

# 3. Jalankan Build APK Crb Launcher menggunakan Gradle
echo "[3/4] Memulai kompilasi APK Crb Launcher (com.crb.tv)..."
chmod +x gradlew 2>/dev/null || true
chmod +x launcher/gradlew 2>/dev/null || true
./gradlew assembleDebug assembleRelease --no-daemon

APK_RELEASE="app/build/outputs/apk/release/app-release.apk"
APK_DEBUG="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_RELEASE" ] && [ -f "launcher/app/build/outputs/apk/release/app-release.apk" ]; then
    APK_RELEASE="launcher/app/build/outputs/apk/release/app-release.apk"
fi
if [ ! -f "$APK_DEBUG" ] && [ -f "launcher/app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_DEBUG="launcher/app/build/outputs/apk/debug/app-debug.apk"
fi

mkdir -p hasil-apk
[ -f "$APK_RELEASE" ] && cp "$APK_RELEASE" "hasil-apk/CrbLauncher-release.apk"
[ -f "$APK_DEBUG" ] && cp "$APK_DEBUG" "hasil-apk/CrbLauncher-debug.apk"

# Salin juga ke folder Download HP jika ada akses storage
if [ -d "$HOME/storage/downloads" ] && [ -f "$APK_DEBUG" ]; then
    cp "$APK_DEBUG" "$HOME/storage/downloads/CrbLauncher-debug.apk"
    echo "[OK] APK disalin ke folder Download HP: ~/storage/downloads/CrbLauncher-debug.apk"
elif [ -d "/sdcard/Download" ] && [ -f "$APK_DEBUG" ]; then
    cp "$APK_DEBUG" "/sdcard/Download/CrbLauncher-debug.apk" 2>/dev/null || true
fi

echo "================================================================"
echo "[OK] BUILD CRB LAUNCHER SELESAI!"
echo "     File APK Release: hasil-apk/CrbLauncher-release.apk"
echo "     File APK Debug  : hasil-apk/CrbLauncher-debug.apk"
echo "================================================================"

# 4. Opsional: Langsung pasang ke Android TV lewat Wi-Fi ADB dari HP jika TV_HOST diisi
if [ -n "$TV_HOST" ]; then
    TARGET_APK="hasil-apk/CrbLauncher-debug.apk"
    [ -f "hasil-apk/CrbLauncher-release.apk" ] && TARGET_APK="hasil-apk/CrbLauncher-release.apk"
    echo "[4/4] Menghubungkan ke Android TV di $TV_HOST:5555..."
    adb connect "$TV_HOST:5555"
    adb -s "$TV_HOST:5555" install -r "$TARGET_APK"
    adb -s "$TV_HOST:5555" shell cmd package set-home-activity --user 0 com.crb.tv/.MainActivity || true
    adb -s "$TV_HOST:5555" shell settings put secure screensaver_components com.crb.tv/.ClockDream || true
    adb -s "$TV_HOST:5555" shell am start -n com.crb.tv/.MainActivity
    echo "[OK] Crb Launcher (com.crb.tv) berhasil dipasang dan diaktifkan di TV ($TV_HOST)!"
else
    echo "Tips: Untuk langsung install ke TV dari HP lewat Wi-Fi, jalankan:"
    echo "      TV_HOST=192.168.1.50 ./build-di-hp-termux.sh"
fi
