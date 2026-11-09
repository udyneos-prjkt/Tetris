#include "tetris_core.hpp"

#include <algorithm>
#include <cmath>

namespace nt {
namespace {

// Koordinat (x,y) dalam kotak lokal, y ke bawah.
constexpr int kBase[7][8] = {
    {0, 1, 1, 1, 2, 1, 3, 1},  // I
    {0, 0, 1, 0, 0, 1, 1, 1},  // O
    {1, 0, 0, 1, 1, 1, 2, 1},  // T
    {1, 0, 2, 0, 0, 1, 1, 1},  // S
    {0, 0, 1, 0, 1, 1, 2, 1},  // Z
    {0, 0, 0, 1, 1, 1, 2, 1},  // J
    {2, 0, 0, 1, 1, 1, 2, 1},  // L
};

// SRS wall kick (sumbu y ke atas), indeks [rotasi asal], 5 pasangan (dx,dy).
constexpr int kJlstzCw[4][10] = {
    {0, 0, -1, 0, -1, 1, 0, -2, -1, -2},
    {0, 0, 1, 0, 1, -1, 0, 2, 1, 2},
    {0, 0, 1, 0, 1, 1, 0, -2, 1, -2},
    {0, 0, -1, 0, -1, -1, 0, 2, -1, 2},
};
constexpr int kJlstzCcw[4][10] = {
    {0, 0, 1, 0, 1, 1, 0, -2, 1, -2},
    {0, 0, 1, 0, 1, -1, 0, 2, 1, 2},
    {0, 0, -1, 0, -1, 1, 0, -2, -1, -2},
    {0, 0, -1, 0, -1, -1, 0, 2, -1, 2},
};
constexpr int kICw[4][10] = {
    {0, 0, -2, 0, 1, 0, -2, -1, 1, 2},
    {0, 0, -1, 0, 2, 0, -1, 2, 2, -1},
    {0, 0, 2, 0, -1, 0, 2, 1, -1, -2},
    {0, 0, 1, 0, -2, 0, 1, -2, -2, 1},
};
constexpr int kICcw[4][10] = {
    {0, 0, -1, 0, 2, 0, -1, 2, 2, -1},
    {0, 0, 2, 0, -1, 0, 2, 1, -1, -2},
    {0, 0, 1, 0, -2, 0, 1, -2, -2, 1},
    {0, 0, -2, 0, 1, 0, -2, -1, 1, 2},
};
constexpr int kNoKick[10] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

constexpr float kLockDelay = 0.5f;
constexpr int kMaxLockResets = 15;
constexpr float kMaxDelta = 0.1f;
constexpr int kLineScore[5] = {0, 100, 300, 500, 800};

int boxSize(Piece p) { return p == Piece::I ? 4 : (p == Piece::O ? 2 : 3); }

const int* kicksFor(Piece p, int fromRot, bool cw, int& count) {
    if (p == Piece::O) {
        count = 1;
        return kNoKick;
    }
    count = 5;
    if (p == Piece::I) return cw ? kICw[fromRot] : kICcw[fromRot];
    return cw ? kJlstzCw[fromRot] : kJlstzCcw[fromRot];
}

float gravityInterval(int level) {
    return static_cast<float>(std::pow(0.8 - (level - 1) * 0.007, level - 1));
}

}  // namespace

std::array<Cell, 4> shapeCells(Piece p, int rotation) {
    const int n = boxSize(p);
    const int* b = kBase[static_cast<int>(p)];
    std::array<Cell, 4> out{};
    for (int i = 0; i < 4; ++i) {
        int x = b[i * 2];
        int y = b[i * 2 + 1];
        for (int r = 0; r < rotation; ++r) {
            const int nx = n - 1 - y;  // putar searah jarum jam
            y = x;
            x = nx;
        }
        out[i] = {x, y};
    }
    return out;
}

Game::Game(uint32_t seed) : rng_(seed ? seed : 1u), seed_(seed) {
    for (auto& col : grid_) col.fill(-1);
    for (int i = 0; i < 8; ++i) nextRandom();  // buang keluaran awal yang berkorelasi
    spawn(nextPiece());
}

int Game::level() const {
    return std::min(kMaxLevel, 1 + static_cast<int>(score_ / kScorePerLevel));
}

uint32_t Game::nextRandom() {
    rng_ ^= rng_ << 13;
    rng_ ^= rng_ >> 17;
    rng_ ^= rng_ << 5;
    return rng_;
}

void Game::ensureBag(size_t minCount) {
    while (queue_.size() < std::max<size_t>(minCount, 7)) {
        std::array<Piece, 7> bag = {Piece::I, Piece::O, Piece::T, Piece::S, Piece::Z, Piece::J, Piece::L};
        for (int i = 6; i > 0; --i) {
            const int j = static_cast<int>(nextRandom() % static_cast<uint32_t>(i + 1));
            std::swap(bag[i], bag[j]);
        }
        for (Piece p : bag) queue_.push_back(p);
    }
}

Piece Game::nextPiece() {
    ensureBag(1);
    const Piece p = queue_.front();
    queue_.pop_front();
    return p;
}

std::vector<Piece> Game::peekNext(size_t count) {
    ensureBag(count);
    return std::vector<Piece>(queue_.begin(), queue_.begin() + static_cast<long>(count));
}

std::array<Cell, 4> Game::currentCells() const {
    auto cells = shapeCells(current_, rot_);
    for (auto& c : cells) {
        c.x += x_;
        c.y += y_;
    }
    return cells;
}

bool Game::fits(Piece p, int rot, int px, int py) const {
    for (const Cell& c : shapeCells(p, rot)) {
        const int cx = px + c.x;
        const int cy = py + c.y;
        if (cx < 0 || cx >= kWidth || cy >= kHeight) return false;
        if (cy >= 0 && grid_[cx][cy] >= 0) return false;
    }
    return true;
}

int Game::ghostY() const {
    int gy = y_;
    while (fits(current_, rot_, x_, gy + 1)) ++gy;
    return gy;
}

void Game::tick(float dt) {
    if (over_) return;
    dt = std::min(dt, kMaxDelta);
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
    while (gravityAcc_ >= interval && fits(current_, rot_, x_, y_ + 1)) {
        gravityAcc_ -= interval;
        ++y_;
    }
}

bool Game::moveLeft() {
    if (over_ || !fits(current_, rot_, x_ - 1, y_)) return false;
    --x_;
    registerGroundedMove();
    return true;
}

bool Game::moveRight() {
    if (over_ || !fits(current_, rot_, x_ + 1, y_)) return false;
    ++x_;
    registerGroundedMove();
    return true;
}

bool Game::softDrop() {
    if (over_ || !fits(current_, rot_, x_, y_ + 1)) return false;
    ++y_;
    score_ += 1;
    return true;
}

void Game::hardDrop() {
    if (over_) return;
    const int dist = ghostY() - y_;
    y_ += dist;
    score_ += 2u * static_cast<uint64_t>(dist);
    lockPiece();
}

bool Game::rotate(bool clockwise) {
    if (over_) return false;
    const int to = clockwise ? (rot_ + 1) % 4 : (rot_ + 3) % 4;
    int count = 0;
    const int* kicks = kicksFor(current_, rot_, clockwise, count);
    for (int i = 0; i < count; ++i) {
        const int nx = x_ + kicks[i * 2];
        const int ny = y_ - kicks[i * 2 + 1];  // tabel kick y ke atas
        if (!fits(current_, to, nx, ny)) continue;
        x_ = nx;
        y_ = ny;
        rot_ = to;
        registerGroundedMove();
        return true;
    }
    return false;
}

bool Game::hold() {
    if (over_ || !canHold_) return false;
    const Piece incoming = held_ >= 0 ? static_cast<Piece>(held_) : nextPiece();
    held_ = static_cast<int>(current_);
    spawn(incoming);
    canHold_ = false;
    return true;
}

void Game::registerGroundedMove() {
    if (grounded() && lockResets_ < kMaxLockResets) {
        ++lockResets_;
        lockTimer_ = 0.f;
    }
}

void Game::lockPiece() {
    for (const Cell& c : currentCells())
        if (c.y >= 0) grid_[c.x][c.y] = static_cast<int8_t>(current_);

    ++pieces_;
    const int cleared = clearFullRows();
    if (cleared > 0) {
        score_ += static_cast<uint64_t>(kLineScore[cleared]) * static_cast<uint64_t>(level());
        lines_ += cleared;
        if (onLinesCleared) onLinesCleared(cleared);
    }
    canHold_ = true;
    spawn(nextPiece());
}

int Game::clearFullRows() {
    int cleared = 0;
    for (int y = kHeight - 1; y >= 0; --y) {
        bool full = true;
        for (int x = 0; x < kWidth; ++x) {
            if (grid_[x][y] < 0) {
                full = false;
                break;
            }
        }
        if (!full) continue;
        for (int yy = y; yy > 0; --yy)
            for (int x = 0; x < kWidth; ++x) grid_[x][yy] = grid_[x][yy - 1];
        for (int x = 0; x < kWidth; ++x) grid_[x][0] = -1;
        ++cleared;
        ++y;  // cek ulang baris yang baru turun
    }
    return cleared;
}

void Game::spawn(Piece p) {
    current_ = p;
    rot_ = 0;
    x_ = (kWidth - boxSize(p)) / 2;
    y_ = kHidden - 1;  // tepat di tepi atas area terlihat
    gravityAcc_ = 0.f;
    lockTimer_ = 0.f;
    lockResets_ = 0;
    if (!fits(p, 0, x_, y_)) {
        over_ = true;
        if (onGameOver) onGameOver();
    }
}

}  // namespace nt
