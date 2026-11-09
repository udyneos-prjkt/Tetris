#pragma once
#include <array>
#include <cstdint>
#include <deque>
#include <functional>
#include <vector>

namespace nt {

enum class Piece : int { I = 0, O, T, S, Z, J, L };

struct Cell {
    int x;
    int y;
};

constexpr int kWidth = 10;
constexpr int kHeight = 22;  // 20 terlihat + 2 baris tersembunyi
constexpr int kHidden = 2;
constexpr int kVisible = kHeight - kHidden;
constexpr int kScorePerLevel = 2500;
constexpr int kMaxLevel = 20;

std::array<Cell, 4> shapeCells(Piece p, int rotation = 0);

// Logika murni (tanpa dependensi platform): deterministik untuk seed yang sama.
class Game {
public:
    explicit Game(uint32_t seed);

    void tick(float dt);
    bool moveLeft();
    bool moveRight();
    bool softDrop();
    void hardDrop();
    bool rotate(bool clockwise);
    bool hold();

    int cellAt(int x, int y) const { return grid_[x][y]; }
    std::array<Cell, 4> currentCells() const;
    int ghostY() const;
    std::vector<Piece> peekNext(size_t count);

    Piece current() const { return current_; }
    int held() const { return held_; }  // -1 = kosong
    int rotation() const { return rot_; }
    int x() const { return x_; }
    int y() const { return y_; }
    uint64_t score() const { return score_; }
    int level() const;
    int lines() const { return lines_; }
    int pieces() const { return pieces_; }
    float elapsed() const { return elapsed_; }
    bool isOver() const { return over_; }
    uint32_t seed() const { return seed_; }

    std::function<void(int)> onLinesCleared;
    std::function<void()> onGameOver;

private:
    friend struct TestAccess;

    bool fits(Piece p, int rot, int px, int py) const;
    bool grounded() const { return !fits(current_, rot_, x_, y_ + 1); }
    void registerGroundedMove();
    void lockPiece();
    int clearFullRows();
    void spawn(Piece p);
    Piece nextPiece();
    void ensureBag(size_t minCount);
    uint32_t nextRandom();

    std::array<std::array<int8_t, kHeight>, kWidth> grid_;
    std::deque<Piece> queue_;
    uint32_t rng_;
    uint32_t seed_;

    Piece current_ = Piece::I;
    int rot_ = 0;
    int x_ = 0;
    int y_ = 0;
    int held_ = -1;
    bool canHold_ = true;
    bool over_ = false;

    uint64_t score_ = 0;
    int lines_ = 0;
    int pieces_ = 0;
    float elapsed_ = 0.f;
    float gravityAcc_ = 0.f;
    float lockTimer_ = 0.f;
    int lockResets_ = 0;
};

}  // namespace nt
