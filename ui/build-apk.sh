#!/usr/bin/env bash
# Builds the signed Android APK and publishes it where the dev server serves it
# (backend/downloads/libero-shop.apk). See docs/mobile-android.md, sections 4 and 5.
# Run from IntelliJ: configuration "APK Android (release)".
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f "android/keystore.properties" ]; then
    echo "[ERREUR] android/keystore.properties est absent : l'APK ne serait pas signee."
    echo "         Voir docs/mobile-android.md, section 3.3."
    exit 1
fi

# Gradle needs a JDK 21: the one IntelliJ downloaded, unless JAVA_HOME says otherwise.
if [ -z "${JAVA_HOME:-}" ]; then
    for jdk in "$HOME"/.jdks/ms-21*; do
        [ -d "$jdk" ] && JAVA_HOME="$jdk"
    done
    export JAVA_HOME="${JAVA_HOME:-}"
fi
if [ -z "${JAVA_HOME:-}" ]; then
    echo "[ERREUR] JDK 21 introuvable : definissez JAVA_HOME."
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

echo "=== 1/3 Ecrans Angular + synchronisation Capacitor"
npm run mobile:sync

echo "=== 2/3 APK signee"
(
    cd android
    ./gradlew assembleRelease
)

APK="android/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
    echo "[ERREUR] $APK n'a pas ete produite."
    exit 1
fi

echo "=== 3/3 Publication"
mkdir -p ../backend/downloads
cp -f "$APK" ../backend/downloads/libero-shop.apk
echo "APK publiee : backend/downloads/libero-shop.apk"
