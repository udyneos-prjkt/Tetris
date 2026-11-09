#include "tetris_core.h"

#include <algorithm>
#include <cmath>

namespace neon {
namespace {

constexpr float kLockDelay = 0.5f;
constexpr int kMaxLockResets = 15;
constexpr float kMaxDeltaTime = 0.1f;
constexpr int kLineScore[5] = {0, 100, 300, 500, 800};

// Koordinat (x,y) dalam kotak lokal, y ke bawah. Urutan: I O T S Z J L.
constexpr int kBase[7][8] = {
    {0, 1, 1, 1, 2, 1, 3, 1}, {0, 0, 1, 0, 0, 1, 1, 1}, {1, 0, 0, 1, 1, 1, 2, 1},
    {1, 0, 2, 0, 0, 1, 1, 1}, {0, 0, 1, 0, 1, 1, 2, 1}, {0, 0, 0, 1, 1, 1, 2, 1},
    {2, 0, 0, 1, 1, 1, 2, 1}};

// SRS wall kick (y ke atas): [rotasiAsal][5 pasangan dx,dy].
constexpr int kJlstzCw[4][10] = {{0, 0, -1, 0, -1, 1, 0, -2, -1, -2},
                                 {0, 0, 1, 0, 1, -1, 0, 2, 1, 2},
                                 {0, 0, 1, 0, 1, 1, 0, -2, 1, -2},
                                 {0, 0, -1, 0, -1, -1, 0, 2, -1, 2}};
constexpr int kJlstzCcw[4][10] = {{0, 0, 1, 0, 1, 1, 0, -2, 1, -2},
                                  {0, 0, 1, 0, 1, -1, 0, 2, 1, 2},
                                  {0, 0, -1, 0, -1, 1, 0, -2, -1, -2},
                                  {0, 0, -1, 0, -1, -1, 0, 2, -1, 2}};
constexpr int kICw[4][10] = {{0, 0, -2, 0, 1, 0, -2, -1, 1, 2},
                             {0, 0, -1, 0, 2, 0, -1, 2, 2, -1},
                             {0, 0, 2, 0, -1, 0, 2, 1, -1, -2},
                             {0, 0, 1, 0, -2, 0, 1, -2, -2, 1}};
constexpr int kICcw[4][10] = {{0, 0, -1, 0, 2, 0, -1, 2, 2, -1},
                              {0, 0, 2, 0, -1, 0, 2, 1, -1, -2},
                              {0, 0, 1, 0, -2, 0, 1, -2, -2, 1},
                              {0, 0, -2, 0, 1, 0, -2, -1, 1, 2}};
constexpr int kNoKick[10] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

int boxSize(PieceType t) {
    return t == PieceType::I ? 4 : (t == PieceType::O ? 2 : 3);
}

Shape cellsFor(PieceType t, int rot) {
    const int n = boxSize(t);
    const int* base = kBase[static_cast<int>(t)];
    Shape out{};
    for (int i = 0; i < 4; ++i) {
        int x = base[i * 2];
        int y = base[i * 2 + 1];
        for (int r = 0; r < rot; ++r) {
            const int nx = n - 1 - y;
            y = x;
            x = nx;
        }
        out[i] = {x, y};
    }
    return out;
}

const int* kicksFor(PieceType t, int fromRot, bool cw, int& count) {
    if (t == PieceType::O) {
        count = 1;
        return kNoKick;
    }
    count = 5;
    if (t == PieceType::I) return cw ? kICw[fromRot] : kICcw[fromRot];
    return cw ? kJlstzCw[fromRot] : kJlstzCcw[fromRot];
}

float gravityInterval(int level) {
    return std::pow(0.8f - (level - 1) * 0.007f, static_cast<float>(level - 1));
}

}  // namespace

TetrisGame::TetrisGame(uint32_t seed) : seed_(seed), rng_(seed) {
    for (auto& col : grid_) col.fill(-1);
    spawn(nextPiece());
}

int TetrisGame::level() const {
    return std::min(kMaxLevel, 1 + static_cast<int>(score_ / kScorePerLevel));
}

Shape TetrisGame::shapeCells(PieceType type) { return cellsFor(type, 0); }

Shape TetrisGame::currentCells() const {
    Shape s = cellsFor(current_, rotation_);
    for (auto& p : s) {
        p.x += x_;
        p.y += y_;
    }
    return s;
}

int TetrisGame::ghostY() const {
    int gy = y_;
    while (fits(current_, rotation_, x_, gy + 1)) ++gy;
    return gy;
}

std::vector<PieceType> TetrisGame::peekNext(int count) {
    ensureBag(static_cast<size_t>(count));
    return std::vector<PieceType>(queue_.begin(), queue_.begin() + count);
}

void TetrisGame::tick(float dt) {
    if (gameOver_) return;
    dt = std::min(std::max(dt, 0.f), kMaxDeltaTime);
    elapsed_ += dt;

    if (grounded()) {
        gravityAcc_ = 0.f;
        lockTimer_ += dt;
        if (lockTimer_ >= kLockDelay) lockPiece();
        return;
    }

    lockTimer_ = 0.f;
    gravityAcc_ += dt;
    const float interval = gravityInterval(level());
    while (gravityAcc_ >= interval && fits(current_, rotation_, x_, y_ + 1)) {
        gravityAcc_ -= interval;
        ++y_;
    }
}

bool TetrisGame::moveLeft() { return tryShift(-1); }
bool TetrisGame::moveRight() { return tryShift(1); }

bool TetrisGame::softDrop() {
    if (gameOver_ || !fits(current_, rotation_, x_, y_ + 1)) return false;
    ++y_;
    score_ += 1;
    return true;
}

void TetrisGame::hardDrop() {
    if (gameOver_) return;
    const int dist = ghostY() - y_;
    y_ += dist;
    score_ += 2LL * dist;
    lockPiece();
}

bool TetrisGame::rotate(bool clockwise) {
    if (gameOver_) return false;
    const int to = clockwise ? (rotation_ + 1) % 4 : (rotation_ + 3) % 4;
    int count = 0;
    const int* kicks = kicksFor(current_, rotation_, clockwise, count);
    for (int i = 0; i < count; ++i) {
        const int nx = x_ + kicks[i * 2];
        const int ny = y_ - kicks[i * 2 + 1];  // tabel kick: y ke atas
        if (!fits(current_, to, nx, ny)) continue;
        x_ = nx;
        y_ = ny;
        rotation_ = to;
        registerGroundedMove();
        return true;
    }
    return false;
}

bool TetrisGame::hold() {
    if (gameOver_ || !canHold_) return false;
    const int incoming = held_ >= 0 ? held_ : static_cast<int>(nextPiece());
    held_ = static_cast<int>(current_);
    spawn(static_cast<PieceType>(incoming));
    canHold_ = false;
    return true;
}

bool TetrisGame::tryShift(int dx) {
    if (gameOver_ || !fits(current_, rotation_, x_ + dx, y_)) return false;
    x_ += dx;
    registerGroundedMove();
    return true;
}

void TetrisGame::registerGroundedMove() {
    if (grounded() && lockResets_ < kMaxLockResets) {
        ++lockResets_;
        lockTimer_ = 0.f;
    }
}

bool TetrisGame::fits(PieceType type, int rot, int px, int py) const {
    for (const Point& c : cellsFor(type, rot)) {
        const int cx = px + c.x;
        const int cy = py + c.y;
        if (cx < 0 || cx >= kWidth || cy >= kHeight) return false;
        if (cy >= 0 && grid_[cx][cy] >= 0) return false;
    }
    return true;
}

void TetrisGame::lockPiece() {
    for (const Point& p : currentCells())
        if (p.y >= 0) grid_[p.x][p.y] = static_cast<int8_t>(current_);

    ++pieces_;
    const int cleared = clearFullRows();
    if (cleared > 0) {
        score_ += static_cast<int64_t>(kLineScore[cleared]) * level();
        lines_ += cleared;
    }
    canHold_ = true;
    spawn(nextPiece());
}

int TetrisGame::clearFullRows() {
    int cleared = 0;
    for (int row = kHeight - 1; row >= 0; --row) {
        if (!rowFull(row)) continue;
        for (int yy = row; yy > 0; --yy)
            for (int x = 0; x < kWidth; ++x) grid_[x][yy] = grid_[x][yy - 1];
        for (int x = 0; x < kWidth; ++x) grid_[x][0] = -1;
        ++cleared;
        ++row;  // cek ulang baris yang baru turun
    }
    return cleared;
}

bool TetrisGame::rowFull(int row) const {
    for (int x = 0; x < kWidth; ++x)
        if (grid_[x][row] < 0) return false;
    return true;
}

void TetrisGame::spawn(PieceType type) {
    current_ = type;
    rotation_ = 0;
    x_ = (kWidth - boxSize(type)) / 2;
    y_ = kHiddenRows - 1;  // spawn di tepi atas area terlihat
    gravityAcc_ = 0.f;
    lockTimer_ = 0.f;
    lockResets_ = 0;
    if (!fits(type, 0, x_, y_)) gameOver_ = true;
}

PieceType TetrisGame::nextPiece() {
    ensureBag(1);
    const PieceType t = queue_.front();
    queue_.pop_front();
    return t;
}

// 7-bag deterministik. Memakai modulo (bukan std::uniform_int_distribution)
// agar urutan identik di semua platform/kompiler.
void TetrisGame::ensureBag(size_t minCount) {
    while (queue_.size() < std::max<size_t>(minCount, 7)) {
        std::array<PieceType, 7> bag = {PieceType::I, PieceType::O, PieceType::T, PieceType::S,
                                        PieceType::Z, PieceType::J, PieceType::L};
        for (int i = 6; i > 0; --i) {
            const int j = static_cast<int>(rng_() % static_cast<uint32_t>(i + 1));
            std::swap(bag[i], bag[j]);
        }
        for (PieceType p : bag) queue_.push_back(p);
    }
}

}  // namespace neon
