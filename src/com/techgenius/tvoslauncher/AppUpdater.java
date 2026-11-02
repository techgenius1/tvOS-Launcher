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
    private static final String KEY_LATEST_CHANGELOG = "latest_changelog";
    private static final String KEY_LAST_CHECK = "last_check_ms";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean checking = false;
    private static volatile boolean downloading = false;
    private static volatile java.lang.ref.WeakReference<android.app.Dialog> activeUpdateDialogRef;

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
        if (c == null) return "2.0.6";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (pi != null && pi.versionName != null && !pi.versionName.isEmpty()) {
                return pi.versionName;
            }
        } catch (Throwable ignored) {
        }
        return "2.0.6";
    }

    public static String getCachedLatestVersion(Context c) {
        return prefs(c).getString(KEY_LATEST_VER, "");
    }

    public static String getCachedDownloadUrl(Context c) {
        return prefs(c).getString(KEY_LATEST_URL, GITHUB_FALLBACK_APK);
    }

    public static String getCachedChangelog(Context c) {
        return prefs(c).getString(KEY_LATEST_CHANGELOG, "");
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
                        sb.append(line).append('\n');
                    }
                }
                JSONObject json = new JSONObject(sb.toString());
                String tag = json.optString("tag_name", "").trim();
                if (tag.isEmpty()) {
                    tag = json.optString("name", "").trim();
                }
                String cleanTag = cleanVersion(tag);
                String changelog = json.optString("body", "").trim();
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
                        .putString(KEY_LATEST_CHANGELOG, changelog)
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
     * in the background and shows the Update Available popup (with Changelog & Install buttons)
     * when a newer release is found.
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
                showUpdateAvailableDialog(
                        activity,
                        latestVersion,
                        downloadUrl,
                        getCachedChangelog(activity),
                        null);
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
                showUpdateAvailableDialog(
                        activity,
                        latestVersion,
                        downloadUrl,
                        getCachedChangelog(activity),
                        onUiRefresh);
            } else {
                Toast.makeText(activity,
                        I18n.s(activity,
                                "You are up to date (v" + cur + ")",
                                "Sürümünüz güncel (v" + cur + ")"),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Shows the Update Available popup with 2 buttons:
     * 1) Show Changelog (opens the GitHub Release changelog)
     * 2) Install (selected/focused by default: auto-updates via ADB if ADB is active, or launches system PackageInstaller if ADB is off)
     */
    public static void showUpdateAvailableDialog(
            Activity activity,
            String latestVersion,
            String downloadUrl,
            String changelog,
            Runnable onUiRefresh) {
        if (activity == null || activity.isFinishing()) return;
        try {
            if (activeUpdateDialogRef != null) {
                android.app.Dialog prev = activeUpdateDialogRef.get();
                if (prev != null && prev.isShowing()) {
                    prev.dismiss();
                }
            }
        } catch (Throwable ignored) {
        }

        String cur = getCurrentVersion(activity);
        boolean adbOn = LocalAdb.isAdbSettingEnabled(activity) || LocalAdb.portReachable;

        android.app.Dialog dlg = new android.app.Dialog(activity);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        float d = activity.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padH = Math.round(28 * d);
        int padV = Math.round(24 * d);
        card.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(0xF2181A22);
        cardBg.setCornerRadius(22f * d);
        cardBg.setStroke(Math.max(1, Math.round(1.2f * d)), 0x38FFFFFF);
        card.setBackground(cardBg);

        android.widget.TextView titleTv = new android.widget.TextView(activity);
        titleTv.setText(I18n.s(activity, "Update Available", "Güncelleme Mevcut") + " — v" + latestVersion);
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        titleTv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        card.addView(titleTv);

        android.widget.TextView msgTv = new android.widget.TextView(activity);
        String modeNote = adbOn
                ? I18n.s(activity,
                        "ADB is active — selecting Install will download and install the update automatically.",
                        "ADB aktif — Yükle'yi seçtiğinizde güncelleme otomatik olarak indirilip kurulacaktır.")
                : I18n.s(activity,
                        "ADB is turned off (Secure Mode) — selecting Install will download the update and open the system package installer.",
                        "ADB kapalı (Güvenli Mod) — Yükle'yi seçtiğinizde güncelleme indirilecek ve sistem paket yükleyicisi açılacaktır.");
        msgTv.setText(I18n.s(activity,
                "A new version of tvOS Launcher is available on GitHub.\n"
                        + "Current version: v" + cur + "  →  New version: v" + latestVersion + "\n\n"
                        + modeNote,
                "GitHub'da yeni bir tvOS Launcher sürümü mevcut.\n"
                        + "Mevcut sürüm: v" + cur + "  →  Yeni sürüm: v" + latestVersion + "\n\n"
                        + modeNote));
        msgTv.setTextColor(0xCCFFFFFF);
        msgTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        msgTv.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        msgTv.setLineSpacing(0f, 1.18f);
        android.widget.LinearLayout.LayoutParams msgLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = Math.round(12 * d);
        msgLp.bottomMargin = Math.round(22 * d);
        card.addView(msgTv, msgLp);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(activity);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.END);

        // Button 1: Show Changelog
        android.widget.TextView changelogBtn = createDialogActionButton(
                activity,
                I18n.s(activity, "Show Changelog", "Değişiklik Günlüğü"),
                false,
                () -> {
                    dlg.dismiss();
                    showChangelogDialog(activity, latestVersion, downloadUrl, changelog, onUiRefresh);
                });
        android.widget.LinearLayout.LayoutParams b1Lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        b1Lp.rightMargin = Math.round(12 * d);
        btnRow.addView(changelogBtn, b1Lp);

        // Button 2: Install (Default Selected / Focused)
        android.widget.TextView installBtn = createDialogActionButton(
                activity,
                I18n.s(activity, "Install", "Yükle"),
                true,
                () -> {
                    dlg.dismiss();
                    downloadAndInstall(activity, downloadUrl, latestVersion, false, onUiRefresh);
                });
        btnRow.addView(installBtn);

        card.addView(btnRow, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(activity);
        android.widget.FrameLayout.LayoutParams cardLp = new android.widget.FrameLayout.LayoutParams(
                Math.round(520 * d),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = android.view.Gravity.CENTER;
        wrapper.addView(card, cardLp);

        dlg.setContentView(wrapper);
        activeUpdateDialogRef = new java.lang.ref.WeakReference<>(dlg);
        dlg.show();

        // Keep Install button selected by default
        installBtn.post(installBtn::requestFocus);
    }

    private static void showChangelogDialog(
            Activity activity,
            String latestVersion,
            String downloadUrl,
            String changelog,
            Runnable onUiRefresh) {
        if (activity == null || activity.isFinishing()) return;

        android.app.Dialog dlg = new android.app.Dialog(activity);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        float d = activity.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padH = Math.round(26 * d);
        int padV = Math.round(22 * d);
        card.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(0xF2181A22);
        cardBg.setCornerRadius(22f * d);
        cardBg.setStroke(Math.max(1, Math.round(1.2f * d)), 0x38FFFFFF);
        card.setBackground(cardBg);

        android.widget.TextView titleTv = new android.widget.TextView(activity);
        titleTv.setText(I18n.s(activity, "Changelog — v" + latestVersion, "Değişiklik Günlüğü — v" + latestVersion));
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 19);
        titleTv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        card.addView(titleTv);

        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setFocusable(true);
        android.widget.TextView bodyTv = new android.widget.TextView(activity);
        String bodyText = (changelog != null && !changelog.trim().isEmpty())
                ? changelog.trim()
                : I18n.s(activity,
                        "No changelog text provided for this release.",
                        "Bu sürüm için değişiklik günlüğü metni bulunamadı.");
        bodyTv.setText(bodyText);
        bodyTv.setTextColor(0xDDFFFFFF);
        bodyTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        bodyTv.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        bodyTv.setLineSpacing(0f, 1.18f);
        int sPad = Math.round(12 * d);
        bodyTv.setPadding(sPad, sPad, sPad, sPad);
        scroll.addView(bodyTv);

        android.graphics.drawable.GradientDrawable scrollBg = new android.graphics.drawable.GradientDrawable();
        scrollBg.setColor(0x18FFFFFF);
        scrollBg.setCornerRadius(12f * d);
        scroll.setBackground(scrollBg);

        android.widget.LinearLayout.LayoutParams scrollLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(280 * d));
        scrollLp.topMargin = Math.round(12 * d);
        scrollLp.bottomMargin = Math.round(18 * d);
        card.addView(scroll, scrollLp);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(activity);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.END);

        android.widget.TextView backBtn = createDialogActionButton(
                activity,
                I18n.s(activity, "Back", "Geri"),
                false,
                () -> {
                    dlg.dismiss();
                    showUpdateAvailableDialog(activity, latestVersion, downloadUrl, changelog, onUiRefresh);
                });
        android.widget.LinearLayout.LayoutParams backLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.rightMargin = Math.round(12 * d);
        btnRow.addView(backBtn, backLp);

        android.widget.TextView installBtn = createDialogActionButton(
                activity,
                I18n.s(activity, "Install", "Yükle"),
                true,
                () -> {
                    dlg.dismiss();
                    downloadAndInstall(activity, downloadUrl, latestVersion, false, onUiRefresh);
                });
        btnRow.addView(installBtn);

        card.addView(btnRow, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(activity);
        android.widget.FrameLayout.LayoutParams cardLp = new android.widget.FrameLayout.LayoutParams(
                Math.round(580 * d),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = android.view.Gravity.CENTER;
        wrapper.addView(card, cardLp);

        dlg.setContentView(wrapper);
        dlg.show();
        installBtn.post(installBtn::requestFocus);
    }

    private static android.widget.TextView createDialogActionButton(
            Context c, String label, boolean primary, Runnable onClick) {
        float d = c.getResources().getDisplayMetrics().density;
        android.widget.TextView btn = new android.widget.TextView(c);
        btn.setText(label);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding(Math.round(22 * d), Math.round(11 * d), Math.round(22 * d), Math.round(11 * d));
        btn.setFocusable(true);
        btn.setClickable(true);

        Runnable applyStyle = () -> {
            boolean f = btn.isFocused();
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(12f * d);
            if (f) {
                bg.setColor(0xFFFFFFFF);
                btn.setTextColor(0xFF11131A);
            } else if (primary) {
                bg.setColor(0x38FFFFFF);
                bg.setStroke(Math.max(1, Math.round(1f * d)), 0x55FFFFFF);
                btn.setTextColor(0xFFFFFFFF);
            } else {
                bg.setColor(0x1FFFFFFF);
                bg.setStroke(Math.max(1, Math.round(1f * d)), 0x33FFFFFF);
                btn.setTextColor(0xDDFFFFFF);
            }
            btn.setBackground(bg);
            btn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        };
        applyStyle.run();
        btn.setOnFocusChangeListener((v, hasFocus) -> applyStyle.run());
        btn.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        return btn;
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

        Toast.makeText(activity,
                I18n.s(activity, "Downloading update v" + version + "…", "Güncelleme indiriliyor v" + version + "…"),
                Toast.LENGTH_SHORT).show();

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

                // 1. If ADB is currently active (port 5555 open), install automatically via LocalAdb
                if (LocalAdb.isAdbPortCurrentlyOpen()) {
                    MAIN.post(() -> Toast.makeText(appCtx,
                            I18n.s(appCtx,
                                    "Installing update v" + version + " automatically via ADB…",
                                    "v" + version + " güncellemesi ADB ile otomatik kuruluyor…"),
                            Toast.LENGTH_LONG).show());
                    if (LocalAdb.installApkViaAdb(appCtx, apkFile)) {
                        downloading = false;
                        if (onUiRefresh != null) MAIN.post(onUiRefresh);
                        return;
                    }
                }

                // 2. If ADB is disabled (Secure Mode) or ADB install was unavailable, use system PackageInstaller
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
