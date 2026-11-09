"use strict";

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");
const { randomInt } = require("crypto");
const { LIMITS, titleFor, seasonId, dayId, checkPlausible, coinsFor } = require("./logic");

admin.initializeApp();
setGlobalOptions({ region: "asia-southeast2", maxInstances: 10 });

const db = admin.firestore();
const { FieldValue, Timestamp } = admin.firestore;

function requireUid(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login diperlukan");
  return request.auth.uid;
}

/** Membuka sesi game: seed dibuat server agar klien tidak bisa memilih urutan blok. */
exports.startRun = onCall(async (request) => {
  const uid = requireUid(request);
  const userRef = db.doc("users/" + uid);
  const userSnap = await userRef.get();
  if (!userSnap.exists) throw new HttpsError("failed-precondition", "Profil belum dibuat");

  const now = Date.now();
  const last = userSnap.get("lastRunStartedAt");
  if (last && now - last.toMillis() < LIMITS.startRunCooldownMs) {
    throw new HttpsError("resource-exhausted", "Terlalu cepat, coba lagi sebentar");
  }

  const runRef = db.collection("runs").doc();
  const seed = randomInt(1, 2 ** 31 - 1);
  const batch = db.batch();
  batch.set(runRef, {
    uid,
    seed,
    status: "open",
    startedAt: FieldValue.serverTimestamp(),
    expireAt: Timestamp.fromMillis(now + LIMITS.runTtlMs), // aktifkan TTL policy pada runs.expireAt
  });
  batch.update(userRef, { lastRunStartedAt: FieldValue.serverTimestamp() });
  await batch.commit();

  return { runId: runRef.id, seed, season: seasonId() };
});

/** Menutup sesi: validasi kewajaran, update leaderboard season, koin, dan julukan. */
exports.submitScore = onCall(async (request) => {
  const uid = requireUid(request);
  const { runId, score, lines, pieces } = request.data || {};
  if (typeof runId !== "string" || runId.length === 0 || runId.length > 64) {
    throw new HttpsError("invalid-argument", "runId tidak valid");
  }

  const runRef = db.doc("runs/" + runId);
  const userRef = db.doc("users/" + uid);
  const season = seasonId();
  const entryRef = db.doc("leaderboards/" + season + "/entries/" + uid);

  return db.runTransaction(async (tx) => {
    const [runSnap, userSnap, entrySnap] = await Promise.all([
      tx.get(runRef),
      tx.get(userRef),
      tx.get(entryRef),
    ]);

    if (!runSnap.exists || runSnap.get("uid") !== uid) {
      throw new HttpsError("not-found", "Run tidak ditemukan");
    }
    if (runSnap.get("status") !== "open") {
      throw new HttpsError("failed-precondition", "Run sudah diselesaikan");
    }
    if (!userSnap.exists) throw new HttpsError("failed-precondition", "Profil belum dibuat");

    const elapsedSec = (Date.now() - runSnap.get("startedAt").toMillis()) / 1000;
    const reason = checkPlausible({ score, lines, pieces, elapsedSec });
    if (reason) {
      tx.update(runRef, { status: "rejected", reason, score, lines, pieces, closedAt: FieldValue.serverTimestamp() });
      tx.update(userRef, { strikes: FieldValue.increment(1) });
      return { accepted: false, reason };
    }

    const today = dayId();
    const daily = userSnap.get("dailyCoins") || {};
    const { granted, newDailyAmount } = coinsFor({
      score,
      dailyDay: daily.day,
      dailyAmount: daily.amount || 0,
      today,
    });

    const previousBest = entrySnap.exists ? entrySnap.get("score") || 0 : 0;
    const seasonBest = Math.max(previousBest, score);
    const bestEver = Math.max(userSnap.get("bestScore") || 0, score);
    const title = titleFor(bestEver);

    if (score > previousBest) {
      tx.set(entryRef, {
        nickname: userSnap.get("nickname"),
        title,
        score,
        lines,
        pieces,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    tx.update(runRef, { status: "accepted", score, lines, pieces, closedAt: FieldValue.serverTimestamp() });
    tx.update(userRef, {
      bestScore: bestEver,
      title,
      coins: FieldValue.increment(granted),
      dailyCoins: { day: today, amount: newDailyAmount },
    });

    return {
      accepted: true,
      coinsGranted: granted,
      title,
      season,
      seasonBest,
      newSeasonBest: score > previousBest,
    };
  });
});
