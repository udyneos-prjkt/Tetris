#include "renderer.h"

#include <android/log.h>

#include <algorithm>

#define LOG_TAG "NeonTetris"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace neon {
namespace {

const float kPieceColors[7][3] = {
    {0.00f, 0.94f, 1.00f}, {1.00f, 0.90f, 0.20f}, {0.72f, 0.32f, 1.00f}, {0.30f, 1.00f, 0.50f},
    {1.00f, 0.25f, 0.35f}, {0.25f, 0.50f, 1.00f}, {1.00f, 0.60f, 0.15f}};
const float kAccent[3] = {0.00f, 0.94f, 1.00f};
const float kWhite[3] = {1.f, 1.f, 1.f};
const float kWell[3] = {0.02f, 0.03f, 0.07f};

const char* kVertexSrc = R"(#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec4 aColor;
uniform vec2 uRes;
out vec4 vColor;
void main() {
    gl_Position = vec4(aPos.x / uRes.x * 2.0 - 1.0, 1.0 - aPos.y / uRes.y * 2.0, 0.0, 1.0);
    vColor = aColor;
}
)";

const char* kFragmentSrc = R"(#version 300 es
precision mediump float;
in vec4 vColor;
out vec4 outColor;
void main() { outColor = vColor; }
)";

GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = GL_FALSE;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        LOGE("shader gagal: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

}  // namespace

void Renderer::onSurfaceCreated() {
    GLuint vs = compile(GL_VERTEX_SHADER, kVertexSrc);
    GLuint fs = compile(GL_FRAGMENT_SHADER, kFragmentSrc);
    program_ = glCreateProgram();
    glAttachShader(program_, vs);
    glAttachShader(program_, fs);
    glLinkProgram(program_);
    GLint ok = GL_FALSE;
    glGetProgramiv(program_, GL_LINK_STATUS, &ok);
    if (!ok) LOGE("link program gagal");
    glDeleteShader(vs);
    glDeleteShader(fs);
    resLoc_ = glGetUniformLocation(program_, "uRes");

    glGenVertexArrays(1, &vao_);
    glGenBuffers(1, &vbo_);
    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    const GLsizei stride = 6 * sizeof(float);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, stride, nullptr);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, stride, reinterpret_cast<void*>(2 * sizeof(float)));
    glBindVertexArray(0);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DEPTH_TEST);
}

void Renderer::onSurfaceChanged(int width, int height) {
    width_ = std::max(width, 1);
    height_ = std::max(height, 1);
    glViewport(0, 0, width_, height_);
}

void Renderer::rect(float x, float y, float w, float h, const float* rgb, float a) {
    const float x2 = x + w, y2 = y + h;
    const float quad[6][2] = {{x, y}, {x2, y}, {x, y2}, {x2, y}, {x2, y2}, {x, y2}};
    for (const auto& p : quad) {
        verts_.insert(verts_.end(), {p[0], p[1], rgb[0], rgb[1], rgb[2], a});
    }
}

void Renderer::mini(float cx, float cy, float m, int pieceType) {
    rect(cx - 2.3f * m, cy - 1.5f * m, 4.6f * m, 3.0f * m, kWhite, 0.06f);
    if (pieceType < 0) return;

    const Shape cells = TetrisGame::shapeCells(static_cast<PieceType>(pieceType));
    int minX = 99, maxX = -99, minY = 99, maxY = -99;
    for (const Point& p : cells) {
        minX = std::min(minX, p.x);
        maxX = std::max(maxX, p.x);
        minY = std::min(minY, p.y);
        maxY = std::max(maxY, p.y);
    }
    const float midX = (minX + maxX) / 2.f;
    const float midY = (minY + maxY) / 2.f;
    const float pad = m * 0.06f;
    for (const Point& p : cells) {
        const float px = cx + (p.x - midX) * m - m / 2.f;
        const float py = cy + (p.y - midY) * m - m / 2.f;
        rect(px + pad, py + pad, m - 2 * pad, m - 2 * pad, kPieceColors[pieceType], 1.f);
    }
}

void Renderer::draw(TetrisGame& game) {
    glClearColor(0.03f, 0.04f, 0.09f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
    verts_.clear();

    const float w = static_cast<float>(width_);
    const float h = static_cast<float>(height_);
    constexpr int kVisible = TetrisGame::kHeight - TetrisGame::kHiddenRows;

    // Area: atas 16% = hold/next, tengah 74% = papan, bawah 10% = HUD (View Android).
    const float cell = std::min(w * 0.94f / TetrisGame::kWidth, h * 0.74f / kVisible);
    const float bw = cell * TetrisGame::kWidth;
    const float bh = cell * kVisible;
    const float bx = (w - bw) / 2.f;
    const float by = h * 0.16f + (h * 0.74f - bh) / 2.f;
    const float gap = cell * 0.05f;

    rect(bx - 8.f, by - 8.f, bw + 16.f, bh + 16.f, kAccent, 0.35f);
    rect(bx - 4.f, by - 4.f, bw + 8.f, bh + 8.f, kWell, 1.f);

    auto paint = [&](int gx, int gy, const float* rgb, float a) {
        const int row = gy - TetrisGame::kHiddenRows;
        if (gx < 0 || gx >= TetrisGame::kWidth || row < 0 || row >= kVisible) return;
        rect(bx + gx * cell + gap, by + row * cell + gap, cell - 2 * gap, cell - 2 * gap, rgb, a);
    };

    for (int x = 0; x < TetrisGame::kWidth; ++x) {
        for (int gy = TetrisGame::kHiddenRows; gy < TetrisGame::kHeight; ++gy) {
            const int v = game.cell(x, gy);
            if (v >= 0) paint(x, gy, kPieceColors[v], 1.f);
            else paint(x, gy, kWhite, 0.05f);
        }
    }

    if (!game.gameOver()) {
        const float* color = kPieceColors[static_cast<int>(game.current())];
        const int ghostOffset = game.ghostY() - game.y();
        for (const Point& p : game.currentCells()) paint(p.x, p.y + ghostOffset, color, 0.25f);
        for (const Point& p : game.currentCells()) paint(p.x, p.y, color, 1.f);
    }

    const float m = std::min(w * 0.045f, h * 0.02f);
    const float topY = h * 0.09f;
    mini(w * 0.14f, topY, m, game.held());
    const auto upcoming = game.peekNext(3);
    for (int i = 0; i < 3; ++i) mini(w * (0.42f + 0.2f * i), topY, m, static_cast<int>(upcoming[i]));

    flush();
}

void Renderer::flush() {
    if (verts_.empty() || program_ == 0) return;
    glUseProgram(program_);
    glUniform2f(resLoc_, static_cast<float>(width_), static_cast<float>(height_));
    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, static_cast<GLsizeiptr>(verts_.size() * sizeof(float)), verts_.data(),
                 GL_STREAM_DRAW);
    glDrawArrays(GL_TRIANGLES, 0, static_cast<GLsizei>(verts_.size() / 6));
    glBindVertexArray(0);
}

}  // namespace neon
