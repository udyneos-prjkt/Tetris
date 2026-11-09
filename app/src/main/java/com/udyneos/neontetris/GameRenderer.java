package com.udyneos.neontetris;

import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class GameRenderer implements GLSurfaceView.Renderer {
    interface StatsListener {
        void onStats(long score, int level, int lines, boolean gameOver);
    }

    private final StatsListener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private long lastNanos;
    private long[] lastStats;
    private boolean started;

    GameRenderer(StatsListener listener) {
        this.listener = listener;
    }

    /** Harus dipanggil dari thread GL. */
    void restart() {
        Native.newGame((int) System.nanoTime());
        lastStats = null;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Native.surfaceCreated();
        if (!started) {
            restart();
            started = true;
        }
        lastNanos = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Native.surfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;

        Native.tick(dt);
        Native.draw();

        final long[] s = Native.stats();
        if (s != null && !Arrays.equals(s, lastStats)) {
            lastStats = s;
            ui.post(() -> listener.onStats(s[0], (int) s[1], (int) s[2], s[3] == 1L));
        }
    }
}
