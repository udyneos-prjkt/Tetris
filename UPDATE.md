# Update otomatis dari GitHub

Saat aplikasi dibuka, game memeriksa https://github.com/udyneos-prjkt/Tetris/releases.
Bila ada rilis dengan asset `NeonTetris-vc<N>.apk` yang N-nya lebih besar dari versionCode terpasang,
muncul dialog **Update wajib**: unduh (dengan progres, cek ukuran dan SHA-256), lalu installer Android terbuka.
Bila pengecekan gagal (offline, rate limit), pemain tetap bisa masuk.

## Syarat
- Repo harus **publik** (token tidak boleh disimpan di APK). Repo privat tidak akan terbaca.
- Semua APK **harus ditandatangani keystore yang sama**, termasuk instalan pertama. Beda kunci = Android menolak update.
- Android tidak mengizinkan instal senyap: pemain tetap menekan Install, dan pertama kali harus mengizinkan
  "instal dari sumber ini" untuk Neon Tetris.

## Rilis versi baru
    bash scripts/make-keystore.sh                 # sekali saja; CADANGKAN release.keystore
    VERSION_CODE=1 bash scripts/build.sh release  # instalan pertama (build/NeonTetris-vc1.apk)

    VERSION_CODE=2 bash scripts/build.sh release  # versi berikutnya: naikkan VERSION_CODE
    gh release create v1.1.0-beta.2 build/NeonTetris-vc2.apk --prerelease --title "v1.1.0 beta 2" --notes "Perbaikan ..."

Catatan rilis (`--notes`) tampil di dialog update (maks. 600 karakter).
Nama asset harus persis `NeonTetris-vc<angka>.apk`; asset lain diabaikan.
