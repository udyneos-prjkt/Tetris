package com.udyneos.neontetris;

import android.app.Activity;
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

import java.text.NumberFormat;

public final class MainActivity extends Activity {
    private static final int ACCENT = 0xFF00F0FF;

    private GameView gameView;
    private TextView scoreText;
    private TextView infoText;
    private TextView overScore;
    private View overPanel;

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

    private void onStats(long score, int level, int lines, boolean gameOver) {
        NumberFormat nf = NumberFormat.getIntegerInstance();
        scoreText.setText("SKOR  " + nf.format(score));
        infoText.setText("LEVEL " + level + "    LINES " + lines + "    v1.1 beta");
        overScore.setText("SKOR  " + nf.format(score));
        overPanel.setVisibility(gameOver ? View.VISIBLE : View.GONE);
    }

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

        Button retry = new Button(this);
        retry.setText("MAIN LAGI");
        retry.setTextColor(0xFF080A17);
        retry.setBackgroundColor(ACCENT);
        retry.setOnClickListener(v -> {
            panel.setVisibility(View.GONE);
            gameView.restart();
        });

        LinearLayout.LayoutParams btn = new LinearLayout.LayoutParams(dp(220), dp(56));
        btn.topMargin = dp(28);
        panel.addView(title);
        panel.addView(overScore);
        panel.addView(retry, btn);
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
