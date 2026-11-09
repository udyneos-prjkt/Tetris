package com.udyneos.neontetris;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;

public final class LoginActivity extends Activity {
    private static final int ACCENT = 0xFF00F0FF;
    private static final int BG = 0xFF080A17;

    private AuthManager auth;
    private boolean registerMode;
    private boolean busy;

    private EditText nicknameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private Button primaryButton;
    private Button googleButton;
    private TextView toggleText;
    private TextView forgotText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        if (!AuthManager.isConfigured(this)) {
            statusText.setText("Firebase belum dikonfigurasi (google-services.json tidak ada). Mode offline saja.");
            primaryButton.setEnabled(false);
            googleButton.setEnabled(false);
            return;
        }

        auth = new AuthManager();
        FirebaseUser user = auth.currentUser();
        if (user != null) proceed(user.getUid());
    }

    // ---------- Alur ----------

    private void onPrimaryClicked() {
        if (busy) return;
        final String email = text(emailInput);
        final String password = passwordInput.getText().toString();
        setBusy(true);

        if (registerMode) {
            auth.register(text(nicknameInput), email, password, new AuthManager.Callback<String>() {
                @Override
                public void onSuccess(String nickname) {
                    openGame();
                }

                @Override
                public void onError(String message) {
                    fail(message);
                }
            });
        } else {
            auth.signIn(email, password, new AuthManager.Callback<Void>() {
                @Override
                public void onSuccess(Void v) {
                    afterSignIn();
                }

                @Override
                public void onError(String message) {
                    fail(message);
                }
            });
        }
    }

    private void onGoogleClicked() {
        if (busy) return;
        setBusy(true);
        auth.signInWithGoogle(this, new AuthManager.Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                afterSignIn();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void onForgotClicked() {
        if (busy) return;
        setBusy(true);
        auth.sendPasswordReset(text(emailInput), new AuthManager.Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                setBusy(false);
                statusText.setText("Jika email terdaftar, tautan reset password sudah dikirim.");
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void afterSignIn() {
        FirebaseUser user = auth.currentUser();
        if (user == null) {
            fail("Login gagal, coba lagi");
            return;
        }
        proceed(user.getUid());
    }

    private void proceed(String uid) {
        setBusy(true);
        auth.loadNickname(uid, new AuthManager.Callback<String>() {
            @Override
            public void onSuccess(String nickname) {
                if (nickname != null) openGame();
                else askNickname(uid);
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    /** Dipakai login Google pertama kali: profil belum ada, pemain memilih nickname. */
    private void askNickname(String uid) {
        final EditText input = new EditText(this);
        input.setHint("Nickname (3-16 karakter)");
        input.setSingleLine(true);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Pilih nickname")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Keluar", (d, w) -> {
                    auth.signOut();
                    setBusy(false);
                })
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(false);
            auth.createProfile(uid, text(input), new AuthManager.Callback<String>() {
                @Override
                public void onSuccess(String nickname) {
                    dialog.dismiss();
                    openGame();
                }

                @Override
                public void onError(String message) {
                    save.setEnabled(true);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private void openGame() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void fail(String message) {
        setBusy(false);
        statusText.setText(message);
    }

    private void setBusy(boolean value) {
        busy = value;
        boolean enabled = !value && auth != null;
        primaryButton.setEnabled(enabled);
        googleButton.setEnabled(enabled);
        if (value) statusText.setText("Memproses...");
    }

    private void setMode(boolean register) {
        registerMode = register;
        nicknameInput.setVisibility(register ? View.VISIBLE : View.GONE);
        forgotText.setVisibility(register ? View.GONE : View.VISIBLE);
        primaryButton.setText(register ? "DAFTAR" : "MASUK");
        toggleText.setText(register ? "Sudah punya akun? Masuk" : "Belum punya akun? Daftar");
        statusText.setText("");
    }

    // ---------- UI ----------

    private void buildUi() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(28), dp(48), dp(28), dp(28));

        TextView title = label("NEON TETRIS", 34, ACCENT, true);
        TextView subtitle = label("v1.1 beta", 14, 0x99FFFFFF, false);

        nicknameInput = input("Nickname", InputType.TYPE_CLASS_TEXT);
        emailInput = input("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        passwordInput = input("Password (min. 8 karakter)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        primaryButton = button("MASUK", ACCENT, BG);
        primaryButton.setOnClickListener(v -> onPrimaryClicked());

        googleButton = button("LANJUT DENGAN GOOGLE", 0xFFFFFFFF, BG);
        googleButton.setOnClickListener(v -> onGoogleClicked());

        forgotText = label("Lupa password?", 14, ACCENT, false);
        forgotText.setOnClickListener(v -> onForgotClicked());

        toggleText = label("", 14, 0xFFFFFFFF, false);
        toggleText.setOnClickListener(v -> setMode(!registerMode));

        statusText = label("", 13, 0xFFFF8A95, false);

        Button offline = button("MAIN OFFLINE", 0x33FFFFFF, 0xFFFFFFFF);
        offline.setOnClickListener(v -> openGame());

        column.addView(title);
        column.addView(subtitle);
        column.addView(spacer(28));
        column.addView(nicknameInput, fieldParams());
        column.addView(emailInput, fieldParams());
        column.addView(passwordInput, fieldParams());
        column.addView(primaryButton, buttonParams(8));
        column.addView(forgotText, textParams(12));
        column.addView(googleButton, buttonParams(20));
        column.addView(toggleText, textParams(18));
        column.addView(statusText, textParams(12));
        column.addView(spacer(12));
        column.addView(offline, buttonParams(8));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.addView(column);
        setContentView(scroll);

        setMode(false);
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0x66FFFFFF);
        e.setTextColor(0xFFFFFFFF);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setBackgroundColor(0x22FFFFFF);
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        return e;
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams buttonParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(topDp);
        return lp;
    }

    private LinearLayout.LayoutParams textParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private static String text(EditText e) {
        return e.getText().toString().trim();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
