# Neon Tetris v1.1 (C++ native, tanpa Unity)

Arsitektur: core C++17 (`tetris_core`) + renderer OpenGL ES 3 (C++) + shell Java (Activity, gestur, HUD).
Java dipertahankan karena Firebase, AdMob, dan Play Billing adalah SDK Java.

## Build di Codespace (tanpa lisensi, tanpa Docker)
    bash scripts/setup-android.sh   # sekali saja (SDK, NDK, CMake, Gradle)
    bash scripts/build.sh           # test core lalu build -> build/NeonTetris-debug.apk
    bash scripts/test-core.sh       # hanya unit test core (detik)

## Kontrol
Geser: gerak | Tap kanan/kiri: putar CW/CCW | Geser pelan ke bawah: soft drop | Flick bawah: hard drop | Swipe atas: hold

## Catatan
- Package `com.udyneos.neontetris` (ubah di `app/build.gradle` dan nama package Java/JNI bila perlu).
- Min SDK 23 (Android 6), ABI armeabi-v7a + arm64-v8a, GLES 3.0.
- Folder `app/src/main/cpp/nt/` bukan bagian build (tidak direferensikan CMake).
