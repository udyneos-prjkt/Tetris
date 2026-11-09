#include <jni.h>

#include <memory>

#include "renderer.h"
#include "tetris_core.h"

// Semua pemanggilan datang dari thread GL (GLSurfaceView.queueEvent / onDrawFrame),
// sehingga state global ini tidak butuh mutex.
namespace {
std::unique_ptr<neon::TetrisGame> g_game;
neon::Renderer g_renderer;
}  // namespace


extern "C" {

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_newGame(JNIEnv*, jclass, jint seed) {
    g_game = std::make_unique<neon::TetrisGame>(static_cast<uint32_t>(seed));
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_tick(JNIEnv*, jclass, jfloat dt) {
    if (g_game) g_game->tick(dt);
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_moveLeft(JNIEnv*, jclass) {
    if (g_game) g_game->moveLeft();
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_moveRight(JNIEnv*, jclass) {
    if (g_game) g_game->moveRight();
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_softDrop(JNIEnv*, jclass) {
    if (g_game) g_game->softDrop();
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_hardDrop(JNIEnv*, jclass) {
    if (g_game) g_game->hardDrop();
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_rotate(JNIEnv*, jclass, jboolean cw) {
    if (g_game) g_game->rotate(cw == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_hold(JNIEnv*, jclass) {
    if (g_game) g_game->hold();
}

// {skor, level, lines, gameOver(0/1), pieces}
JNIEXPORT jlongArray JNICALL Java_com_udyneos_neontetris_Native_stats(JNIEnv* env, jclass) {
    jlong v[5] = {0, 1, 0, 0, 0};
    if (g_game) {
        v[0] = g_game->score();
        v[1] = g_game->level();
        v[2] = g_game->lines();
        v[3] = g_game->gameOver() ? 1 : 0;
        v[4] = g_game->pieces();
    }
    jlongArray arr = env->NewLongArray(5);
    if (arr != nullptr) env->SetLongArrayRegion(arr, 0, 5, v);
    return arr;
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_surfaceCreated(JNIEnv*, jclass) {
    g_renderer.onSurfaceCreated();
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_surfaceChanged(JNIEnv*, jclass, jint w, jint h) {
    g_renderer.onSurfaceChanged(w, h);
}

JNIEXPORT void JNICALL Java_com_udyneos_neontetris_Native_draw(JNIEnv*, jclass) {
    if (g_game) g_renderer.draw(*g_game);
}

}  // extern "C"
