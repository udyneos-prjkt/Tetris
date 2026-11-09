#include <cmath>
#include <cstdio>
#include <set>

#include "tetris_core.h"

using neon::PieceType;
using neon::TetrisGame;

static int g_failures = 0;
#define CHECK(cond)                                                          \
    do {                                                                     \
        if (!(cond)) {                                                       \
            std::printf("GAGAL %s:%d  %s\n", __FILE__, __LINE__, #cond);     \
            ++g_failures;                                                    \
        }                                                                    \
    } while (0)

static void sameSeedSameSequence() {
    TetrisGame a(42), b(42);
    CHECK(a.current() == b.current());
    CHECK(a.peekNext(14) == b.peekNext(14));
}

static void bagFirstSevenDistinct() {
    TetrisGame g(7);
    std::set<int> seen{static_cast<int>(g.current())};
    for (PieceType p : g.peekNext(6)) seen.insert(static_cast<int>(p));
    CHECK(seen.size() == 7);
}

static void newGameState() {
    TetrisGame g(1);
    CHECK(g.level() == 1);
    CHECK(g.score() == 0);
    CHECK(!g.gameOver());
}

static void hardDropLocksAndScores() {
    TetrisGame g(1);
    g.hardDrop();
    CHECK(g.pieces() == 1);
    CHECK(g.score() > 0);
}

static void moveLeftStopsAtWall() {
    TetrisGame g(3);
    for (int i = 0; i < 20; ++i) g.moveLeft();
    CHECK(!g.moveLeft());
    for (const auto& p : g.currentCells()) CHECK(p.x >= 0);
}

static void holdOncePerPiece() {
    TetrisGame g(5);
    CHECK(g.hold());
    CHECK(!g.hold());
    g.hardDrop();
    CHECK(g.hold());
}

static void stackingEndsGame() {
    TetrisGame g(9);
    for (int i = 0; i < 100 && !g.gameOver(); ++i) g.hardDrop();
    CHECK(g.gameOver());
}

static void tickCapsDelta() {
    TetrisGame g(2);
    g.tick(5.f);
    CHECK(g.elapsed() <= 0.1f + 1e-4f);
}

static void rotateWorksOnEmptyBoard() {
    for (uint32_t seed = 1; seed <= 30; ++seed) {
        TetrisGame g(seed);
        CHECK(g.rotate(true));
        CHECK(g.rotate(false));
    }
}

static void lineClear() {
    uint32_t seed = 1;
    for (; seed < 500; ++seed)
        if (TetrisGame(seed).current() == PieceType::I) break;
    TetrisGame g(seed);
    CHECK(g.current() == PieceType::I);
    for (int x = 0; x < TetrisGame::kWidth; ++x)
        if (x < 3 || x > 6) g.debugSetCell(x, TetrisGame::kHeight - 1, 0);
    g.hardDrop();
    CHECK(g.lines() == 1);
    CHECK(g.cell(0, TetrisGame::kHeight - 1) == -1);
    CHECK(g.score() > 100);
}

static void ghostNeverAboveCurrent() {
    TetrisGame g(11);
    CHECK(g.ghostY() >= g.y());
}

int main() {
    sameSeedSameSequence();
    bagFirstSevenDistinct();
    newGameState();
    hardDropLocksAndScores();
    moveLeftStopsAtWall();
    holdOncePerPiece();
    stackingEndsGame();
    tickCapsDelta();
    rotateWorksOnEmptyBoard();
    lineClear();
    ghostNeverAboveCurrent();
    std::printf(g_failures == 0 ? "SEMUA TEST LULUS\n" : "ADA %d KEGAGALAN\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
