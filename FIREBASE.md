# Setup Firebase (sekali)

1. https://console.firebase.google.com > Add project.
2. Add app > Android, package `com.udyneos.neontetris` > unduh `google-services.json` > taruh di `app/`.
3. Build > Authentication > Sign-in method: aktifkan **Email/Password** dan **Google**.
4. Build > Firestore Database > Create (production mode).
5. Deploy aturan keamanan:
       cd firebase && npx firebase-tools login --no-localhost && npx firebase-tools deploy --only firestore:rules --project <ID_PROYEK>
6. Google Sign-In butuh SHA-1 kunci penanda tangan APK. Ambil SHA-1 debug (dari Codespace yang membangun APK):
       keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
   Tambahkan di Project settings > app Android > SHA certificate fingerprints, lalu unduh ulang `google-services.json`.
7. `bash scripts/build.sh` lalu instal APK.

Tanpa `google-services.json` aplikasi tetap terbuild dan hanya menyediakan tombol MAIN OFFLINE.
Rilis Play Store butuh SHA-1 dari keystore rilis (dan SHA-1 App Signing Play Console).
