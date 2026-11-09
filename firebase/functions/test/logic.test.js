"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { titleFor, seasonId, checkPlausible, coinsFor, LIMITS } = require("../logic");

test("titleFor memilih julukan sesuai ambang", () => {
  assert.equal(titleFor(0), "Rookie");
  assert.equal(titleFor(4999), "Rookie");
  assert.equal(titleFor(5000), "Bronze");
  assert.equal(titleFor(900000), "Legend");
});

test("seasonId memakai minggu ISO (UTC)", () => {
  assert.equal(seasonId(new Date("2026-11-09T00:00:00Z")), "2026-W46");
  assert.equal(seasonId(new Date("2026-11-08T23:59:59Z")), "2026-W45");
  assert.equal(seasonId(new Date("2026-01-01T12:00:00Z")), "2026-W01");
  assert.equal(seasonId(new Date("2027-01-01T12:00:00Z")), "2026-W53");
});

test("run wajar diterima", () => {
  assert.equal(checkPlausible({ score: 4000, lines: 20, pieces: 90, elapsedSec: 120 }), null);
});

test("terlalu cepat ditolak", () => {
  assert.equal(checkPlausible({ score: 100, lines: 0, pieces: 500, elapsedSec: 10 }), "too_fast");
});

test("baris mustahil ditolak", () => {
  assert.equal(checkPlausible({ score: 100, lines: 100, pieces: 50, elapsedSec: 100 }), "impossible_lines");
});

test("skor mustahil ditolak", () => {
  assert.equal(checkPlausible({ score: 1e7, lines: 10, pieces: 40, elapsedSec: 100 }), "impossible_score");
});

test("input tidak valid ditolak", () => {
  assert.equal(checkPlausible({ score: -1, lines: 0, pieces: 0, elapsedSec: 1 }), "invalid_input");
  assert.equal(checkPlausible({ score: 1.5, lines: 0, pieces: 0, elapsedSec: 1 }), "invalid_input");
  assert.equal(checkPlausible({ score: "9", lines: 0, pieces: 0, elapsedSec: 1 }), "invalid_input");
});

test("run kedaluwarsa ditolak", () => {
  assert.equal(
    checkPlausible({ score: 0, lines: 0, pieces: 0, elapsedSec: LIMITS.maxRunSeconds + 1 }),
    "run_expired"
  );
});

test("koin dibatasi per run dan per hari", () => {
  assert.deepEqual(coinsFor({ score: 1e6, dailyDay: "d", dailyAmount: 0, today: "d" }), {
    granted: 200,
    newDailyAmount: 200,
  });
  assert.deepEqual(coinsFor({ score: 1e6, dailyDay: "d", dailyAmount: 1900, today: "d" }), {
    granted: 100,
    newDailyAmount: 2000,
  });
  assert.deepEqual(coinsFor({ score: 1e6, dailyDay: "kemarin", dailyAmount: 2000, today: "d" }), {
    granted: 200,
    newDailyAmount: 200,
  });
});
