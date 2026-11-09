package com.udyneos.neontetris;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Seluruh callback dipanggil di main thread. */
final class AuthManager {
    interface Callback<T> {
        void onSuccess(T value);

        void onError(String message);
    }

    static final String DEFAULT_TITLE = "Rookie";
    private static final Pattern NICKNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int MIN_PASSWORD = 8;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

    void signOut() {
        auth.signOut();
    }

    // ---------- Validasi ----------

    static String validateNickname(String nickname) {
        if (nickname == null || !NICKNAME.matcher(nickname).matches())
            return "Nickname 3-16 karakter: huruf, angka, atau garis bawah";
        return null;
    }

    static String validateEmail(String email) {
        if (email == null || !Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return "Format email tidak valid";
        return null;
    }

    static String validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD)
            return "Password minimal " + MIN_PASSWORD + " karakter";
        return null;
    }

    // ---------- Akun ----------

    void register(String nickname, String email, String password, Callback<String> cb) {
        String err = firstNonNull(validateNickname(nickname), validateEmail(email), validatePassword(password));
        if (err != null) {
            cb.onError(err);
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    final FirebaseUser user = result.getUser();
                    if (user == null) {
                        cb.onError("Registrasi gagal, coba lagi");
                        return;
                    }
                    createProfile(user.getUid(), nickname, new Callback<String>() {
                        @Override
                        public void onSuccess(String value) {
                            cb.onSuccess(value);
                        }

                        @Override
                        public void onError(String message) {
                            // Batalkan akun yatim agar email tidak terkunci tanpa profil.
                            user.delete().addOnCompleteListener(t -> cb.onError(message));
                        }
                    });
                })
                .addOnFailureListener(e -> cb.onError(describe(e)));
    }

    void signIn(String email, String password, Callback<Void> cb) {
        String err = firstNonNull(validateEmail(email), password == null || password.isEmpty() ? "Password wajib diisi" : null);
        if (err != null) {
            cb.onError(err);
            return;
        }
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(describe(e)));
    }

    void sendPasswordReset(String email, Callback<Void> cb) {
        String err = validateEmail(email);
        if (err != null) {
            cb.onError(err);
            return;
        }
        // Pesan sukses selalu sama agar tidak membocorkan email mana yang terdaftar.
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) cb.onSuccess(null);
                    else cb.onError(describe(e));
                });
    }

    void signInWithGoogle(Activity activity, Callback<Void> cb) {
        int id = activity.getResources().getIdentifier("default_web_client_id", "string", activity.getPackageName());
        if (id == 0) {
            cb.onError("Google Sign-In belum dikonfigurasi (cek google-services.json dan SHA-1)");
            return;
        }

        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(activity.getString(id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();

        CredentialManager.create(activity).getCredentialAsync(
                activity, request, new CancellationSignal(), ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            cb.onError("Jenis kredensial tidak didukung");
                            return;
                        }
                        try {
                        String idToken = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
                        AuthCredential firebaseCred = GoogleAuthProvider.getCredential(idToken, null);
                        auth.signInWithCredential(firebaseCred)
                                .addOnSuccessListener(r -> cb.onSuccess(null))
                                .addOnFailureListener(e -> cb.onError(describe(e)));
                    } catch (Exception e) { // Changed to catch a generic Exception
                        cb.onError("Token Google tidak valid");
                        }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        cb.onError("Login Google dibatalkan atau tidak ada akun Google di perangkat");
                    }
                });
    }

    // ---------- Profil ----------

    /** onSuccess(null) bila profil belum ada (mis. login Google pertama kali). */
    void loadNickname(String uid, Callback<String> cb) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> cb.onSuccess(doc.exists() ? doc.getString("nickname") : null))
                .addOnFailureListener(e -> cb.onError(describe(e)));
    }

    /**
     * Batch atomik: memesan nicknames/{lower} dan membuat users/{uid}.
     * Bila nickname sudah ada, aturan Firestore menolak (update dilarang) sehingga seluruh batch gagal.
     */
    void createProfile(String uid, String nickname, Callback<String> cb) {
        String err = validateNickname(nickname);
        if (err != null) {
            cb.onError(err);
            return;
        }
        String lower = nickname.toLowerCase(Locale.ROOT);

        Map<String, Object> nick = new HashMap<>();
        nick.put("uid", uid);

        Map<String, Object> user = new HashMap<>();
        user.put("nickname", nickname);
        user.put("nicknameLower", lower);
        user.put("title", DEFAULT_TITLE);
        user.put("coins", 0L);
        user.put("gold", 0L);
        user.put("createdAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(db.collection("nicknames").document(lower), nick);
        batch.set(db.collection("users").document(uid), user);
        batch.commit()
                .addOnSuccessListener(v -> cb.onSuccess(nickname))
                .addOnFailureListener(e -> cb.onError(isPermissionDenied(e)
                        ? "Nickname sudah dipakai, pilih yang lain" : describe(e)));
    }

    // ---------- Util ----------

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }

    private static String describe(Exception e) {
        if (e instanceof FirebaseAuthUserCollisionException) return "Email sudah terdaftar";
        if (e instanceof FirebaseAuthWeakPasswordException) return "Password terlalu lemah";
        if (e instanceof FirebaseAuthInvalidUserException || e instanceof FirebaseAuthInvalidCredentialsException)
            return "Email atau password salah";
        if (e instanceof FirebaseNetworkException) return "Tidak ada koneksi internet";
        if (isPermissionDenied(e)) return "Akses ditolak oleh server";
        return "Terjadi kesalahan, coba lagi";
    }
}
