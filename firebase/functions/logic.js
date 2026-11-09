"use strict";

const LIMITS = Object.freeze({
  maxPiecesPerSecond: 8,
  maxRunSeconds: 3 * 3600,
  startRunCooldownMs: 2000,
  runTtlMs: 24 * 3600 * 1000,
  maxCoinsPerRun: 200,
  maxCoinsPerDay: 2000,
  maxHardDropPointsPerPiece: 42, // hard drop 2 poin x maks 21 baris
  maxLinePointsPerLine: 4000, // 800 x level maks 20, dibagi 4 baris -> 200 x 20
  maxValue: 1e9,
});

// Julukan berdasarkan skor terbaik sepanjang masa.
const TITLES = Object.freeze([
  [0, "Rookie"],
  [5000, "Bronze"],
  [20000, "Silver"],
  [50000, "Gold"],
  [100000, "Platinum"],
  [200000, "Diamond"],
  [400000, "Master"],
  [800000, "Legend"],
]);

function titleFor(bestScore) {
  let title = TITLES[0][1];
  for (const [min, name] of TITLES) if (bestScore >= min) title = name;
  return title;
}

/** ID season mingguan ISO, mis. "2026-W45" (UTC). */
function seasonId(date = new Date()) {
  const t = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((t - yearStart) / 86400000 + 1) / 7);
  return t.getUTCFullYear() + "-W" + String(week).padStart(2, "0");
}

function dayId(date = new Date()) {
  return date.toISOString().slice(0, 10);
}

function isCount(v) {
  return Number.isInteger(v) && v >= 0 && v <= LIMITS.maxValue;
}

/** Mengembalikan alasan penolakan, atau null bila masuk akal. */
function checkPlausible({ score, lines, pieces, elapsedSec }) {
  if (![score, lines, pieces].every(isCount)) return "invalid_input";
  if (elapsedSec > LIMITS.maxRunSeconds) return "run_expired";
  if (pieces > Math.ceil(elapsedSec * LIMITS.maxPiecesPerSecond) + 1) return "too_fast";
  if (lines * 10 > pieces * 4) return "impossible_lines";
  if (score > pieces * LIMITS.maxHardDropPointsPerPiece + lines * LIMITS.maxLinePointsPerLine) {
    return "impossible_score";
  }
  return null;
}

/** Koin dari satu run, dibatasi per run dan per hari. */
function coinsFor({ score, dailyDay, dailyAmount, today }) {
  const base = Math.min(LIMITS.maxCoinsPerRun, Math.floor(score / 100));
  const usedToday = dailyDay === today ? dailyAmount : 0;
  const remaining = Math.max(0, LIMITS.maxCoinsPerDay - usedToday);
  const granted = Math.min(base, remaining);
  return { granted, newDailyAmount: usedToday + granted };
}

module.exports = { LIMITS, TITLES, titleFor, seasonId, dayId, checkPlausible, coinsFor };
