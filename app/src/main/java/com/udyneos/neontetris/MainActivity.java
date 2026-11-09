package com.udyneos.neontetris;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;

public final class MainActivity extends Activity {
    private static final int ACCENT = 0xFF00F0FF;

    private GameView gameView;
    private TextView scoreText;
    private TextView infoText;
    private TextView overScore;
    private TextView overStatus;
    private Button rankingButton;
    private View overPanel;

    private RunService runService;
    private String runId;   // null = mode offline
    private String season;
    private boolean submitted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF080A17);

        gameView = new GameView(this, this::onStats);
        root.addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildHud());
        root.addView(buildGameOverPanel(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (isOnline()) runService = new RunService();
        startNewRun();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.onResume();
    }

    @Override
    protected void onPause() {
        gameView.onPause();
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    // ---------- Sesi game ----------

    private boolean isOnline() {
        return AuthManager.isConfigured(this) && FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    private void startNewRun() {
        submitted = false;
        runId = null;
        overPanel.setVisibility(View.GONE);

        if (runService == null) {
            startOffline();
            return;
        }

        runService.startRun(new AuthManager.Callback<RunService.Run>() {
            @Override
            public void onSuccess(RunService.Run run) {
                runId = run.runId;
                season = run.season;
                rankingButton.setEnabled(true);
                gameView.restart(run.seed);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message + ". Main offline.", Toast.LENGTH_LONG).show();
                startOffline();
            }
        });
    }

    private void startOffline() {
        runId = null;
        rankingButton.setEnabled(season != null);
        gameView.restart((int) (System.nanoTime() & 0x7fffffff));
    }

    private void onStats(long score, int level, int lines, int pieces, boolean gameOver) {
        NumberFormat nf = NumberFormat.getIntegerInstance();
        scoreText.setText("SKOR  " + nf.format(score));
        infoText.setText("LEVEL " + level + "    LINES " + lines + "    v1.1 beta");
        overScore.setText("SKOR  " + nf.format(score));

        if (!gameOver) {
            overPanel.setVisibility(View.GONE);
            return;
        }
        overPanel.setVisibility(View.VISIBLE);
        if (submitted) return;
        submitted = true;

        if (runId == null || runService == null) {
            overStatus.setText("Mode offline: skor tidak masuk ranking");
            return;
        }

        overStatus.setText("Mengirim skor...");
        runService.submitScore(runId, score, lines, pieces, new AuthManager.Callback<RunService.Result>() {
            @Override
            public void onSuccess(RunService.Result r) {
                if (r.accepted) {
                    overStatus.setText("+" + r.coinsGranted + " coin   |   Julukan: " + r.title);
                } else {
                    overStatus.setText("Skor ditolak server (" + r.reason + ")");
                }
            }

            @Override
            public void onError(String message) {
                overStatus.setText(message);
            }
        });
    }

    private void showRanking() {
        if (season == null) return;
        FirebaseFirestore.getInstance()
                .collection("leaderboards").document(season).collection("entries")
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    NumberFormat nf = NumberFormat.getIntegerInstance();
                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Long s = d.getLong("score");
                        sb.append(rank++).append(". ")
                                .append(d.getString("nickname"))
                                .append("  [").append(d.getString("title")).append("]  ")
                                .append(nf.format(s == null ? 0L : s)).append('\n');
                    }
                    if (sb.length() == 0) sb.append("Belum ada skor minggu ini.");
                    new AlertDialog.Builder(this)
                            .setTitle("Ranking " + season)
                            .setMessage(sb.toString())
                            .setPositiveButton("Tutup", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Ranking gagal dimuat", Toast.LENGTH_SHORT).show());
    }

    // ---------- UI ----------

    private View buildHud() {
        LinearLayout hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setGravity(Gravity.CENTER_HORIZONTAL);
        hud.setPadding(0, 0, 0, dp(10));

        scoreText = makeText("SKOR  0", 26, 0xFFFFFFFF, true);
        infoText = makeText("LEVEL 1    LINES 0    v1.1 beta", 13, ACCENT, false);
        hud.addView(scoreText);
        hud.addView(infoText);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        hud.setLayoutParams(lp);
        return hud;
    }

    private View buildGameOverPanel() {
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xD9000000);
        panel.setClickable(true); // blok sentuhan ke papan saat game over
        panel.setVisibility(View.GONE);

        TextView title = makeText("GAME OVER", 40, 0xFFFF4059, true);
        overScore = makeText("SKOR  0", 26, 0xFFFFFFFF, false);
        overStatus = makeText("", 15, ACCENT, false);

        Button retry = new Button(this);
        retry.setText("MAIN LAGI");
        retry.setTextColor(0xFF080A17);
        retry.setBackgroundColor(ACCENT);
        retry.setOnClickListener(v -> startNewRun());

        rankingButton = new Button(this);
        rankingButton.setText("RANKING");
        rankingButton.setTextColor(0xFFFFFFFF);
        rankingButton.setBackgroundColor(0x33FFFFFF);
        rankingButton.setEnabled(false);
        rankingButton.setOnClickListener(v -> showRanking());

        LinearLayout.LayoutParams status = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        status.topMargin = dp(12);
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(dp(220), dp(56));
        retryLp.topMargin = dp(28);
        LinearLayout.LayoutParams rankLp = new LinearLayout.LayoutParams(dp(220), dp(48));
        rankLp.topMargin = dp(12);

        panel.addView(title);
        panel.addView(overScore);
        panel.addView(overStatus, status);
        panel.addView(retry, retryLp);
        panel.addView(rankingButton, rankLp);
        overPanel = panel;
        return panel;
    }

    private TextView makeText(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
