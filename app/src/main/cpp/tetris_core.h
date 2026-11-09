#pragma once
#include <array>
#include <cstdint>
#include <deque>
#include <random>
#include <vector>

namespace neon {

enum class PieceType : int { I = 0, O, T, S, Z, J, L };

struct Point {
    int x;
    int y;
};
using Shape = std::array<Point, 4>;

// Logika murni Tetris (tanpa dependensi Android/GL): deterministik dengan seed.
class TetrisGame {
public:
    static constexpr int kWidth = 10;
    static constexpr int kHeight = 22;  // 20 terlihat + 2 baris tersembunyi
    static constexpr int kHiddenRows = 2;
    static constexpr int kScorePerLevel = 2500;
    static constexpr int kMaxLevel = 20;

    explicit TetrisGame(uint32_t seed);

    void tick(float dt);
    bool moveLeft();
    bool moveRight();
    bool softDrop();
    void hardDrop();
    bool rotate(bool clockwise);
    bool hold();

    PieceType current() const { return current_; }
    int held() const { return held_; }  // -1 bila kosong
    int rotation() const { return rotation_; }
    int x() const { return x_; }
    int y() const { return y_; }
    int cell(int cx, int cy) const { return grid_[cx][cy]; }
    Shape currentCells() const;
    int ghostY() const;
    std::vector<PieceType> peekNext(int count);
    static Shape shapeCells(PieceType type);

    int64_t score() const { return score_; }
    int level() const;
    int lines() const { return lines_; }
    int pieces() const { return pieces_; }
    float elapsed() const { return elapsed_; }
    bool gameOver() const { return gameOver_; }
    uint32_t seed() const { return seed_; }

    void debugSetCell(int cx, int cy, int value) { grid_[cx][cy] = static_cast<int8_t>(value); }

private:
    bool fits(PieceType type, int rot, int px, int py) const;
    bool grounded() const { return !fits(current_, rotation_, x_, y_ + 1); }
    bool tryShift(int dx);
    void registerGroundedMove();
    void lockPiece();
    int clearFullRows();
    bool rowFull(int row) const;
    void spawn(PieceType type);
    PieceType nextPiece();
    void ensureBag(size_t minCount);

    uint32_t seed_;
    std::mt19937 rng_;
    std::deque<PieceType> queue_;
    std::array<std::array<int8_t, kHeight>, kWidth> grid_{};

    PieceType current_ = PieceType::I;
    int held_ = -1;
    int rotation_ = 0;
    int x_ = 0;
    int y_ = 0;
    int64_t score_ = 0;
    int lines_ = 0;
    int pieces_ = 0;
    float elapsed_ = 0.f;
    float gravityAcc_ = 0.f;
    float lockTimer_ = 0.f;
    int lockResets_ = 0;
    bool canHold_ = true;
    bool gameOver_ = false;
};

}  // namespace neon
