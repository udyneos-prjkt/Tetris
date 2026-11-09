package com.udyneos.neontetris;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateInstaller {
    interface Listener {
        void onProgress(int percent);

        void onDownloaded(File apk);

        void onError(String message);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateInstaller() {}

    static File updateDir(Context context) {
        return new File(context.getCacheDir(), "updates");
    }

    /** Hapus APK sisa unduhan sebelumnya (dipanggil saat aplikasi mulai). */
    static void cleanup(Context context) {
        File[] files = updateDir(context).listFiles();
        if (files == null) return;
        for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    static void download(Context context, UpdateChecker.Update update, Listener listener) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            File dir = updateDir(app);
            File target = new File(dir, "update.apk");
            File temp = new File(dir, "update.apk.part");
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Folder unduhan gagal dibuat");
                download(update, temp, listener);
                if (target.exists() && !target.delete()) throw new IOException("File lama tidak bisa dihapus");
                if (!temp.renameTo(target)) throw new IOException("Gagal menyimpan file");
                MAIN.post(() -> listener.onDownloaded(target));
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                final String message = e instanceof SecurityException || e instanceof IOException
                        ? e.getMessage() : "Unduhan gagal";
                MAIN.post(() -> listener.onError(message == null ? "Unduhan gagal" : message));
            }
        });
    }

    private static void download(UpdateChecker.Update update, File out, Listener listener) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(update.url).openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "NeonTetris-Updater");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server menjawab HTTP " + conn.getResponseCode());
            }

            // Setelah redirect, host akhir harus milik GitHub.
            String host = conn.getURL().getHost().toLowerCase(Locale.ROOT);
            boolean trusted = "github.com".equals(host) || host.endsWith(".githubusercontent.com");
            if (!trusted || !"https".equals(conn.getURL().getProtocol())) {
                throw new SecurityException("Sumber unduhan tidak tepercaya");
            }

            long total = conn.getContentLengthLong() > 0 ? conn.getContentLengthLong() : update.size;
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long read = 0;
            int lastPercent = -1;

            try (InputStream in = conn.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    read += n;
                    if (total > 0) {
                        int percent = (int) (read * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final int p = percent;
                            MAIN.post(() -> listener.onProgress(p));
                        }
                    }
                }
            }

            if (update.size > 0 && read != update.size) throw new IOException("Ukuran file tidak sesuai");
            if (update.sha256 != null && !update.sha256.equals(hex(sha.digest()))) {
                throw new SecurityException("Checksum tidak cocok, unduhan dibatalkan");
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Membuka installer sistem. Mengembalikan false bila izin "instal dari sumber ini" belum diberikan
     * (halaman pengaturan dibuka; pemain menekan UPDATE lagi setelah mengizinkan).
     */
    static boolean install(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return false;
        }

        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        return true;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
