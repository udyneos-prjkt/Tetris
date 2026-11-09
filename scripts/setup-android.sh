#!/usr/bin/env bash
# Pasang Android SDK + NDK + CMake + Gradle 8.7 di Linux/Codespace (butuh JDK 17+).
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
GRADLE_VER="8.7"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if ! ls -d /usr/lib/jvm/java-17-openjdk-* >/dev/null 2>&1; then
  sudo apt-get update && sudo apt-get install -y openjdk-17-jdk
fi
. "$ROOT/scripts/use-jdk17.sh"
java -version
command -v unzip >/dev/null || { echo "Pasang unzip: sudo apt-get install -y unzip"; exit 1; }

mkdir -p "$SDK/cmdline-tools" "$HOME/tools"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fsSL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  rm -rf /tmp/cmdtools && unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
  rm -rf "$SDK/cmdline-tools/latest"
  mv /tmp/cmdtools/cmdline-tools "$SDK/cmdline-tools/latest"
fi

if [ ! -x "$HOME/tools/gradle-$GRADLE_VER/bin/gradle" ]; then
  curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip"
  unzip -q /tmp/gradle.zip -d "$HOME/tools"
fi

yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125" "cmake;3.22.1"

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "Setup selesai."
