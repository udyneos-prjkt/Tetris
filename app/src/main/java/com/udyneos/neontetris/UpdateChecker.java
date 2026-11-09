package com.udyneos.neontetris;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mencari rilis GitHub yang memiliki asset bernama NeonTetris-vc<versionCode>.apk
 * dengan versionCode lebih besar dari versi terpasang. Repo harus publik.
 * Bila gagal (offline, rate limit), hasil = null agar pemain tetap bisa masuk (fail-open).
 */
final class UpdateChecker {
    static final String OWNER = "udyneos-prjkt";
    static final String REPO = "Tetris";

    private static final String TAG = "UpdateChecker";
    private static final String API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases?per_page=5";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + OWNER + "/" + REPO + "/releases/download/";
    private static final Pattern ASSET_NAME = Pattern.compile("^NeonTetris-vc(\\d{1,9})\\.apk$");
    private static final int MAX_BODY_BYTES = 512 * 1024;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Update {
        final long versionCode;
        final String tag;
        final String notes;
        final String url;
        final long size;
        final String sha256; // huruf kecil, atau null bila GitHub tidak menyediakan digest

        Update(long versionCode, String tag, String notes, String url, long size, String sha256) {
            this.versionCode = versionCode;
            this.tag = tag;
            this.notes = notes;
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    interface Listener {
        /** update == null: tidak ada pembaruan, atau pengecekan gagal. Dipanggil di main thread. */
        void onResult(Update update);
    }

    private UpdateChecker() {}

    static void check(Context context, Listener listener) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            Update found = null;
            try {
                found = fetchNewest(currentVersionCode(app));
            } catch (Exception e) {
                Log.w(TAG, "Cek update gagal: " + e);
            }
            final Update result = found;
            MAIN.post(() -> listener.onResult(result));
        });
    }

    @SuppressWarnings("deprecation")
    static long currentVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return Long.MAX_VALUE; // tidak mungkin; cegah update salah
        }
    }

    private static Update fetchNewest(long current) throws Exception {
        JSONArray releases = new JSONArray(httpGet(API));
        Update best = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.optBoolean("draft", false)) continue;

            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;

            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.getJSONObject(j);
                Matcher m = ASSET_NAME.matcher(asset.optString("name"));
                if (!m.matches()) continue;

                long code = Long.parseLong(m.group(1));
                String url = asset.optString("browser_download_url");
                if (code <= current || !url.startsWith(DOWNLOAD_PREFIX)) continue;
                if (best != null && code <= best.versionCode) continue;

                best = new Update(code, release.optString("tag_name", "v" + code),
                        release.optString("body", ""), url, asset.optLong("size", -1), parseDigest(asset.optString("digest")));
            }
        }
        return best;
    }

    private static String parseDigest(String digest) {
        if (digest != null && digest.startsWith("sha256:") && digest.length() == 7 + 64) {
            return digest.substring(7).toLowerCase(java.util.Locale.ROOT);
        }
        return null;
    }

    private static String httpGet(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.setRequestProperty("User-Agent", "NeonTetris-Updater");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BODY_BYTES) throw new IOException("Respons terlalu besar");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }
}
