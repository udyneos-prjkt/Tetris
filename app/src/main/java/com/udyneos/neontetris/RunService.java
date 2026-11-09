package com.udyneos.neontetris;

import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/** Klien Cloud Functions (region harus sama dengan di firebase/functions/index.js). */
final class RunService {
    private static final String REGION = "asia-southeast2";

    static final class Run {
        final String runId;
        final int seed;
        final String season;

        Run(String runId, int seed, String season) {
            this.runId = runId;
            this.seed = seed;
            this.season = season;
        }
    }

    static final class Result {
        final boolean accepted;
        final long coinsGranted;
        final String title;
        final String reason;

        Result(boolean accepted, long coinsGranted, String title, String reason) {
            this.accepted = accepted;
            this.coinsGranted = coinsGranted;
            this.title = title;
            this.reason = reason;
        }
    }

    private final FirebaseFunctions functions = FirebaseFunctions.getInstance(REGION);

    @SuppressWarnings("unchecked")
    void startRun(AuthManager.Callback<Run> cb) {
        functions.getHttpsCallable("startRun").call()
                .addOnSuccessListener(r -> {
                    try {
                        Map<String, Object> d = (Map<String, Object>) r.getData();
                        cb.onSuccess(new Run(
                                (String) d.get("runId"),
                                ((Number) d.get("seed")).intValue(),
                                (String) d.get("season")));
                    } catch (RuntimeException e) {
                        cb.onError("Respons server tidak valid");
                    }
                })
                .addOnFailureListener(e -> cb.onError("Tidak bisa memulai sesi online"));
    }

    @SuppressWarnings("unchecked")
    void submitScore(String runId, long score, int lines, int pieces, AuthManager.Callback<Result> cb) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("score", score);
        payload.put("lines", lines);
        payload.put("pieces", pieces);

        functions.getHttpsCallable("submitScore").call(payload)
                .addOnSuccessListener(r -> {
                    try {
                        Map<String, Object> d = (Map<String, Object>) r.getData();
                        boolean accepted = Boolean.TRUE.equals(d.get("accepted"));
                        Number coins = (Number) d.get("coinsGranted");
                        cb.onSuccess(new Result(
                                accepted,
                                coins == null ? 0L : coins.longValue(),
                                (String) d.get("title"),
                                (String) d.get("reason")));
                    } catch (RuntimeException e) {
                        cb.onError("Respons server tidak valid");
                    }
                })
                .addOnFailureListener(e -> cb.onError("Skor gagal dikirim"));
    }
}
