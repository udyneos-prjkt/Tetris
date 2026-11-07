# Neon Tetris v1.1 beta

## Setup sekali jalan
1. Push folder ini ke repo GitHub (root repo = folder ini).
2. Dapatkan lisensi Unity Personal (.ulf) lewat https://github.com/mackysoft/Unity-ManualActivation (Unity 2022.3.20f1).
3. Repo > Settings > Secrets and variables > Actions > tambahkan `UNITY_LICENSE` (isi file .ulf).
4. Push ke `main`: job `test` lalu `build` berjalan, APK ada di Artifacts.

## Rilis (AAB bertanda tangan)
Tambahkan secrets: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `ANDROID_KEYSTORE_PASS`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_ALIAS_PASS` (hanya huruf/angka). Jalankan workflow manual dengan `release = true`.

## Catatan
- Package ID default `com.udyneos.neontetris`; ubah di `.github/workflows/build.yml` (`-packageId`) sebelum rilis.
- Min SDK 23 (Android 6), IL2CPP ARMv7 + ARM64, Portrait.
- Scene dibuat otomatis oleh `BuildScript`; seluruh UI dibangun lewat kode (`GameBootstrap`).
- Jika membuka di Unity Editor, commit file `.meta` yang dihasilkan.
