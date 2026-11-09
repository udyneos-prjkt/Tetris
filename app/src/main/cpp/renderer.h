#pragma once
#include <GLES3/gl3.h>

#include <vector>

#include "tetris_core.h"

namespace neon {

// Renderer GLES3 berbasis batch kotak berwarna (satu draw call per frame).
class Renderer {
public:
    void onSurfaceCreated();
    void onSurfaceChanged(int width, int height);
    void draw(TetrisGame& game);

private:
    void rect(float x, float y, float w, float h, const float* rgb, float a);
    void mini(float cx, float cy, float m, int pieceType);
    void flush();

    GLuint program_ = 0;
    GLuint vao_ = 0;
    GLuint vbo_ = 0;
    GLint resLoc_ = -1;
    int width_ = 1;
    int height_ = 1;
    std::vector<float> verts_;
};

}  // namespace neon
