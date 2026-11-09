package com.udyneos.neontetris;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;

import java.io.File;
import java.util.Locale;

/** Gerbang update: bila ada versi baru, pemain wajib memperbarui (atau keluar) sebelum masuk game. */
final class UpdateUi {
    private UpdateUi() {}

    /** proceed dijalankan bila tidak ada update atau pengecekan gagal (fail-open). */
    static void run(Activity activity, Runnable proceed) {
        UpdateInstaller.cleanup(activity);
        UpdateChecker.check(activity, update -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (update == null) proceed.run();
            else showDialog(activity, update);
        });
    }

    private static void showDialog(Activity activity, UpdateChecker.Update update) {
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Update wajib " + update.tag)
                .setMessage(describe(update))
                .setCancelable(false)
                .setPositiveButton("UPDATE SEKARANG", null)
                .setNegativeButton("KELUAR", (d, w) -> activity.finishAffinity())
                .create();

        dialog.setOnShowListener(d -> {
            final Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> startDownload(activity, dialog, button, update));
        });
        dialog.show();
    }

    private static void startDownload(Activity activity, AlertDialog dialog, Button button,
                                      UpdateChecker.Update update) {
        button.setEnabled(false);
        dialog.setMessage("Mengunduh... 0%");

        UpdateInstaller.download(activity, update, new UpdateInstaller.Listener() {
            @Override
            public void onProgress(int percent) {
                dialog.setMessage("Mengunduh... " + percent + "%");
            }

            @Override
            public void onDownloaded(File apk) {
                button.setEnabled(true);
                if (UpdateInstaller.install(activity, apk)) {
                    dialog.setMessage("Ikuti petunjuk pemasangan. Bila dibatalkan, tekan UPDATE lagi.");
                } else {
                    dialog.setMessage("Izinkan instal dari sumber ini di pengaturan, lalu kembali dan tekan UPDATE lagi.");
                }
            }

            @Override
            public void onError(String message) {
                button.setEnabled(true);
                dialog.setMessage(message + "\n\nTekan UPDATE untuk mencoba lagi.");
            }
        });
    }

    private static String describe(UpdateChecker.Update update) {
        StringBuilder sb = new StringBuilder();
        String notes = update.notes == null ? "" : update.notes.trim();
        if (!notes.isEmpty()) {
            sb.append(notes.length() > 600 ? notes.substring(0, 600) + "..." : notes).append("\n\n");
        }
        if (update.size > 0) {
            sb.append(String.format(Locale.ROOT, "Ukuran: %.1f MB", update.size / 1048576.0));
        }
        return sb.toString().trim();
    }
}
