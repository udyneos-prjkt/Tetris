package com.udyneos.neontetris;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/**
 * Gestur: geser horizontal = gerak, geser pelan ke bawah = soft drop,
 * flick cepat ke bawah = hard drop, tap = rotasi (kanan CW, kiri CCW), swipe atas = hold.
 */
final class GameView extends GLSurfaceView {
    private static final float CELL_DP = 28f;
    private static final long TAP_MAX_MS = 200;
    private static final float TAP_MAX_DP = 10f;
    private static final float FLICK_MIN_DP = 40f;
    private static final float FLICK_MIN_DP_PER_SEC = 1400f;
    private static final float HOLD_SWIPE_DP = 80f;

    private final GameRenderer renderer;
    private final float density;

    private boolean tracking;
    private boolean moved;
    private boolean holdUsed;
    private float startX, startY, lastX, lastY, accX, accY;
    private long startTime;

    GameView(Context context, GameRenderer.StatsListener listener) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        renderer = new GameRenderer(listener);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    void restart(int seed) {
        queueEvent(() -> renderer.restart(seed));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float cellPx = CELL_DP * density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true;
                moved = false;
                holdUsed = false;
                startX = lastX = e.getX();
                startY = lastY = e.getY();
                accX = accY = 0f;
                startTime = e.getEventTime();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!tracking) return true;
                accX += e.getX() - lastX;
                accY = Math.max(0f, accY + (e.getY() - lastY)); // y layar ke bawah = positif
                lastX = e.getX();
                lastY = e.getY();

                while (accX >= cellPx) { queueEvent(Native::moveRight); accX -= cellPx; moved = true; }
                while (accX <= -cellPx) { queueEvent(Native::moveLeft); accX += cellPx; moved = true; }
                while (accY >= cellPx) { queueEvent(Native::softDrop); accY -= cellPx; moved = true; }

                if (!holdUsed && startY - e.getY() >= HOLD_SWIPE_DP * density) {
                    queueEvent(Native::hold);
                    holdUsed = true;
                    moved = true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (tracking) finishGesture(e);
                tracking = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                return true;

            default:
                return true;
        }
    }

    private void finishGesture(MotionEvent e) {
        final long durationMs = Math.max(e.getEventTime() - startTime, 1L);
        final float dx = e.getX() - startX;
        final float dy = e.getY() - startY;

        final boolean isTap = !moved && durationMs <= TAP_MAX_MS
                && Math.hypot(dx, dy) <= TAP_MAX_DP * density;
        if (isTap) {
            final boolean clockwise = startX >= getWidth() / 2f;
            queueEvent(() -> Native.rotate(clockwise));
            return;
        }

        final boolean isFlick = dy >= FLICK_MIN_DP * density
                && dy > Math.abs(dx)
                && dy / (durationMs / 1000f) >= FLICK_MIN_DP_PER_SEC * density;
        if (isFlick) queueEvent(Native::hardDrop);
    }
}
