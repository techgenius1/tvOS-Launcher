#!/bin/bash
# Builds and signs build/tvos-home.apk with plain SDK tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"

# JDK 17+ and the Android SDK (build-tools 34.0.0, platform android-34). Override with the usual
# variables: JAVA_HOME, ANDROID_HOME (or ANDROID_SDK_ROOT).
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/.local/jdk-17.0.20.1+1/Contents/Home" ]; then
    JAVA_HOME="$HOME/.local/jdk-17.0.20.1+1/Contents/Home"
fi
if [ -n "${JAVA_HOME:-}" ]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
[ -d "$SDK" ] || SDK="$HOME/Android/Sdk"  # Linux default
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
for f in "$BT/aapt2" "$BT/d8" "$JAR"; do
    [ -e "$f" ] || { echo "Missing $f — install build-tools 34.0.0 and platform android-34 (sdkmanager), or set ANDROID_HOME." >&2; exit 1; }
done
command -v javac >/dev/null || { echo "javac not found — install JDK 17+ or set JAVA_HOME." >&2; exit 1; }

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
    -A assets --java build/gen --min-sdk-version 24 --target-sdk-version 34 \
    --version-code "${VERSION_CODE:-5}" --version-name "${VERSION_NAME:-2.0.3}" build/res.zip

javac -encoding UTF-8 --release 11 -classpath "$JAR" \
    -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Signing. Release builds: RELEASE_KEYSTORE + RELEASE_KEYSTORE_PASS (the private key lives in the
# separate, private tvOS-Launcher-signing repo). Development builds: a local debug key, created on
# first use and never committed (it's in .gitignore).
if [ -n "${RELEASE_KEYSTORE:-}" ]; then
    [ -n "${RELEASE_KEYSTORE_PASS:-}" ] || { echo "Set RELEASE_KEYSTORE_PASS for $RELEASE_KEYSTORE" >&2; exit 1; }
    "$BT/apksigner" sign --ks "$RELEASE_KEYSTORE" --ks-pass env:RELEASE_KEYSTORE_PASS \
        --ks-key-alias "${RELEASE_KEY_ALIAS:-tvos-release}" --out build/tvos-home.apk build/aligned.apk
    echo "Signed with the release key"
else
    KS=debug.keystore
    if [ ! -f "$KS" ]; then
        keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias debug \
            -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvOS Launcher debug" >/dev/null
    fi
    "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
        --out build/tvos-home.apk build/aligned.apk
    echo "Signed with the local debug key (development build)"
fi

echo "Built build/tvos-home.apk"
