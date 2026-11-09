#!/usr/bin/env bash
# Pemakaian: scripts/build.sh [debug|release]   (hasil: build/NeonTetris-<mode>.apk)
set -euo pipefail

MODE="${1:-debug}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
GRADLE="$HOME/tools/gradle-8.7/bin/gradle"
. "$ROOT/scripts/use-jdk17.sh"

[ -x "$GRADLE" ] || { echo "Jalankan scripts/setup-android.sh dulu."; exit 1; }
bash "$ROOT/scripts/test-core.sh"

cd "$ROOT"
case "$MODE" in
  debug)   "$GRADLE" --no-daemon :app:assembleDebug;   SRC="app/build/outputs/apk/debug/app-debug.apk" ;;
  release) "$GRADLE" --no-daemon :app:assembleRelease; SRC="app/build/outputs/apk/release/app-release-unsigned.apk" ;;
  *) echo "Mode: debug | release"; exit 1 ;;
esac

mkdir -p build
cp "$SRC" "build/NeonTetris-$MODE.apk"
ls -l "build/NeonTetris-$MODE.apk"
