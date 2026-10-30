package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Checks GitHub Releases (https://api.github.com/repos/techgenius1/tvOS-Launcher/releases/latest)
 * and downloads/installs the latest APK either automatically or manually from Settings -> Updates.
 */
public final class AppUpdater {
    public static final String GITHUB_API_LATEST =
            "https://api.github.com/repos/techgenius1/tvOS-Launcher/releases/latest";
    public static final String GITHUB_FALLBACK_APK =
            "https://github.com/techgenius1/tvOS-Launcher/releases/latest/download/tvos-home.apk";

    private static final String PREF_NAME = "updates";
    private static final String KEY_AUTO_UPDATE = "auto_update";
    private static final String KEY_LATEST_VER = "latest_version";
    private static final String KEY_LATEST_URL = "latest_apk_url";
    private static final String KEY_LAST_CHECK = "last_check_ms";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean checking = false;
    private static volatile boolean downloading = false;

    public interface CheckCallback {
        void onResult(boolean success, String latestVersion, String downloadUrl, boolean updateAvailable, String errorMsg);
    }

    private AppUpdater() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAutoUpdateEnabled(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_UPDATE, true);
    }

    public static void setAutoUpdateEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply();
    }

    public static String getCurrentVersion(Context c) {
        if (c == null) return "2.0.3";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (pi != null && pi.versionName != null && !pi.versionName.isEmpty()) {
                return pi.versionName;
            }
        } catch (Throwable ignored) {
        }
        return "2.0.3";
    }

    public static String getCachedLatestVersion(Context c) {
        return prefs(c).getString(KEY_LATEST_VER, "");
    }

    public static String getCachedDownloadUrl(Context c) {
        return prefs(c).getString(KEY_LATEST_URL, GITHUB_FALLBACK_APK);
    }

    public static boolean isChecking() {
        return checking;
    }

    public static boolean isDownloading() {
        return downloading;
    }

    public static String cleanVersion(String v) {
        if (v == null) return "";
        String s = v.trim();
        while (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    /** Returns true if remoteVer is strictly newer than currentVer (e.g. "2.0.4" > "2.0.3"). */
    public static boolean isNewerVersion(String currentVer, String remoteVer) {
        String c = cleanVersion(currentVer);
        String r = cleanVersion(remoteVer);
        if (r.isEmpty()) return false;
        if (c.isEmpty()) return true;
        if (c.equals(r)) return false;
        String[] cp = c.split("[^0-9]+");
        String[] rp = r.split("[^0-9]+");
        int len = Math.max(cp.length, rp.length);
        for (int i = 0; i < len; i++) {
            int cv = 0;
            int rv = 0;
            try {
                if (i < cp.length && !cp[i].isEmpty()) cv = Integer.parseInt(cp[i]);
            } catch (NumberFormatException ignored) {
            }
            try {
                if (i < rp.length && !rp[i].isEmpty()) rv = Integer.parseInt(rp[i]);
            } catch (NumberFormatException ignored) {
            }
            if (rv > cv) return true;
            if (rv < cv) return false;
        }
        return false;
    }

    /**
     * Queries GitHub Releases API for the latest release version and APK download URL.
     */
    public static void checkForUpdates(Context c, CheckCallback cb) {
        if (c == null) return;
        final Context appCtx = c.getApplicationContext();
        checking = true;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_API_LATEST);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "tvOS-Launcher-Updater");

                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new Exception("HTTP " + code);
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject json = new JSONObject(sb.toString());
                String tag = json.optString("tag_name", "").trim();
                if (tag.isEmpty()) {
                    tag = json.optString("name", "").trim();
                }
                String cleanTag = cleanVersion(tag);
                String apkUrl = GITHUB_FALLBACK_APK;
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        String name = asset.optString("name", "").toLowerCase();
                        String dl = asset.optString("browser_download_url", "");
                        if (name.endsWith(".apk") && !dl.isEmpty()) {
                            apkUrl = dl;
                            break;
                        }
                    }
                }
                prefs(appCtx).edit()
                        .putString(KEY_LATEST_VER, cleanTag)
                        .putString(KEY_LATEST_URL, apkUrl)
                        .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                        .apply();
                final String finalVer = cleanTag;
                final String finalUrl = apkUrl;
                final boolean newer = isNewerVersion(getCurrentVersion(appCtx), cleanTag);
                checking = false;
                if (cb != null) {
                    MAIN.post(() -> cb.onResult(true, finalVer, finalUrl, newer, null));
                }
            } catch (Throwable t) {
                checking = false;
                final String err = t.getMessage() != null ? t.getMessage() : "Network error";
                if (cb != null) {
                    MAIN.post(() -> cb.onResult(false, getCachedLatestVersion(appCtx), getCachedDownloadUrl(appCtx), false, err));
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "github-update-check").start();
    }

    /**
     * Called on MainActivity startup / network online: if Automatic Updates is ON, checks GitHub
     * in the background and automatically pulls & installs if a newer release is found.
     */
    public static void checkAutoUpdateIfNeeded(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!isAutoUpdateEnabled(activity)) return;
        if (checking || downloading) return;
        long last = prefs(activity).getLong(KEY_LAST_CHECK, 0L);
        long now = System.currentTimeMillis();
        // Throttle automatic background checks to once every 15 minutes
        if (now - last < 15 * 60 * 1000L) return;

        checkForUpdates(activity, (success, latestVersion, downloadUrl, updateAvailable, errorMsg) -> {
            if (success && updateAvailable && !activity.isFinishing()) {
                Toast.makeText(activity,
                        I18n.s(activity,
                                "Updating tvOS Launcher to v" + latestVersion + "…",
                                "tvOS Launcher v" + latestVersion + " sürümüne güncelleniyor…"),
                        Toast.LENGTH_SHORT).show();
                downloadAndInstall(activity, downloadUrl, latestVersion, true, null);
            }
        });
    }

    /**
     * Shows interactive update check / prompt from Settings -> Updates.
     */
    public static void manualCheckAndPrompt(Activity activity, Runnable onUiRefresh) {
        if (activity == null || activity.isFinishing()) return;
        if (checking || downloading) return;
        if (onUiRefresh != null) onUiRefresh.run();
        checkForUpdates(activity, (success, latestVersion, downloadUrl, updateAvailable, errorMsg) -> {
            if (onUiRefresh != null) onUiRefresh.run();
            if (activity.isFinishing()) return;
            if (!success) {
                Toast.makeText(activity,
                        I18n.s(activity, "Could not check for updates.", "Güncellemeler kontrol edilemedi."),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String cur = getCurrentVersion(activity);
            if (updateAvailable) {
                new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(I18n.s(activity, "Update Available", "Güncelleme Mevcut") + " (v" + latestVersion + ")")
                        .setMessage(I18n.s(activity,
                                "A new version of tvOS Launcher (v" + latestVersion + ") is available on GitHub.\nCurrent version: v" + cur,
                                "GitHub'da yeni bir tvOS Launcher sürümü (v" + latestVersion + ") mevcut.\nMevcut sürüm: v" + cur))
                        .setPositiveButton(I18n.s(activity, "Update Now", "Şimdi Güncelle"), (d, w) -> {
                            downloadAndInstall(activity, downloadUrl, latestVersion, false, onUiRefresh);
                        })
                        .setNegativeButton(I18n.s(activity, "Cancel", "İptal"), null)
                        .show();
            } else {
                Toast.makeText(activity,
                        I18n.s(activity,
                                "You are up to date (v" + cur + ")",
                                "Sürümünüz güncel (v" + cur + ")"),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void downloadAndInstall(
            Activity activity,
            String apkUrl,
            String version,
            boolean autoSilent,
            Runnable onUiRefresh) {
        if (activity == null || downloading) return;
        downloading = true;
        if (onUiRefresh != null) MAIN.post(onUiRefresh);
        final Context appCtx = activity.getApplicationContext();

        if (!autoSilent) {
            Toast.makeText(activity,
                    I18n.s(activity, "Downloading update v" + version + "…", "Güncelleme indiriliyor v" + version + "…"),
                    Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            File apkFile = new File(appCtx.getCacheDir(), "tvos-update.apk");
            try {
                if (apkFile.exists()) apkFile.delete();
                String currentUrl = (apkUrl != null && !apkUrl.isEmpty()) ? apkUrl : GITHUB_FALLBACK_APK;
                for (int redirects = 0; redirects < 6; redirects++) {
                    conn = (HttpURLConnection) new URL(currentUrl).openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "tvOS-Launcher-Updater");
                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == HttpURLConnection.HTTP_MOVED_PERM
                            || status == HttpURLConnection.HTTP_SEE_OTHER
                            || status == 307 || status == 308) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (loc == null || loc.isEmpty()) break;
                        currentUrl = loc;
                        continue;
                    }
                    break;
                }
                if (conn == null || conn.getResponseCode() != 200) {
                    throw new Exception("Download failed");
                }
                try (InputStream in = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(apkFile)) {
                    byte[] buf = new byte[32768];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        fos.write(buf, 0, r);
                    }
                    fos.flush();
                }
                if (!apkFile.exists() || apkFile.length() < 10_000) {
                    throw new Exception("Downloaded APK invalid");
                }
                // Make readable for shell/system installer if needed
                try {
                    appCtx.getCacheDir().setExecutable(true, false);
                    apkFile.setReadable(true, false);
                } catch (Throwable ignored) {
                }

                // 1. Try silent install via LocalAdb if paired
                if (LocalAdb.isPaired(appCtx)) {
                    try {
                        String pkg = appCtx.getPackageName();
                        LocalAdb.execShellOutput(appCtx,
                                "appops set " + pkg + " REQUEST_INSTALL_PACKAGES allow 2>/dev/null");
                        // Also try copying to /data/local/tmp and running pm install -r
                        File extDir = appCtx.getExternalCacheDir();
                        if (extDir != null) {
                            File extApk = new File(extDir, "tvos-update.apk");
                            copyFile(apkFile, extApk);
                            extApk.setReadable(true, false);
                            String out = LocalAdb.execShellOutput(appCtx,
                                    "cp \"" + extApk.getAbsolutePath() + "\" /data/local/tmp/tvos-update.apk 2>/dev/null && "
                                    + "pm install -r /data/local/tmp/tvos-update.apk");
                            if (out != null && out.contains("Success")) {
                                downloading = false;
                                if (onUiRefresh != null) MAIN.post(onUiRefresh);
                                return;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                // 2. Install via Android PackageInstaller Session
                downloading = false;
                if (onUiRefresh != null) MAIN.post(onUiRefresh);
                MAIN.post(() -> installWithPackageInstaller(activity, apkFile));
            } catch (Throwable t) {
                downloading = false;
                if (onUiRefresh != null) MAIN.post(onUiRefresh);
                MAIN.post(() -> Toast.makeText(appCtx,
                        I18n.s(appCtx, "Update download failed.", "Güncelleme indirilemedi."),
                        Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "github-apk-download").start();
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[32768];
            int r;
            while ((r = fis.read(buf)) > 0) {
                fos.write(buf, 0, r);
            }
            fos.flush();
        }
    }

    private static void installWithPackageInstaller(Activity activity, File apkFile) {
        if (activity == null || !apkFile.exists()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity,
                        I18n.s(activity, "Please allow 'Install unknown apps' for tvOS Launcher.",
                                "Lütfen tvOS Launcher için 'Bilinmeyen uygulamaları yükle' iznini verin."),
                        Toast.LENGTH_LONG).show();
                try {
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(permIntent);
                } catch (Throwable ignored) {
                }
                return;
            }

            PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());
            int sessionId = pi.createSession(params);
            try (PackageInstaller.Session session = pi.openSession(sessionId)) {
                try (OutputStream out = session.openWrite("tvos-update.apk", 0, apkFile.length());
                     FileInputStream in = new FileInputStream(apkFile)) {
                    byte[] buf = new byte[32768];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        out.write(buf, 0, r);
                    }
                    session.fsync(out);
                }
                Intent callbackIntent = new Intent(activity, InstallStatusReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent piCallback = PendingIntent.getBroadcast(activity, sessionId, callbackIntent, flags);
                session.commit(piCallback.getIntentSender());
            }
        } catch (Throwable t) {
            Toast.makeText(activity,
                    I18n.s(activity, "Could not start installer.", "Yükleyici başlatılamadı."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receives PackageInstaller status callbacks and launches the user confirmation prompt if required.
     */
    public static final class InstallStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirm);
                    } catch (Throwable ignored) {
                    }
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(context,
                        I18n.s(context, "Update installed!", "Güncelleme yüklendi!"),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
