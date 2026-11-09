package com.udyneos.neontetris;

/** Jembatan JNI ke core C++. Semua method harus dipanggil dari thread GL. */
final class Native {
    static {
        System.loadLibrary("neontetris");
    }

    private Native() {}

    static native void newGame(int seed);
    static native void tick(float dt);
    static native void moveLeft();
    static native void moveRight();
    static native void softDrop();
    static native void hardDrop();
    static native void rotate(boolean clockwise);
    static native void hold();
    static native long[] stats();
    static native void surfaceCreated();
    static native void surfaceChanged(int width, int height);
    static native void draw();
}
