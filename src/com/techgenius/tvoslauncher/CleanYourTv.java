package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native on-device implementation of CleanYourTV (Beta) inside tvOS Launcher.
 *
 * Uses the 341 real TV profiles from cleanyourtv/examples bundled under
 * assets/cleanyourtv/index.txt and assets/cleanyourtv/examples/<slug>/debloat.txt,
 * with all safety guards from tools/disable.sh and tools/recovery.py.
 */
public final class CleanYourTv {

    private static final String PREFS = "cleanyourtv";
    private static final String KEY_MODEL_SLUG = "selected_model_slug";
    private static final String KEY_MODEL_TITLE = "selected_model_title";
    private static final String KEY_DISABLED_PKGS = "disabled_pkgs_csv";
    private static final String KEY_DEBLOAT_DONE_MODEL = "debloat_done_model";
    private static final String KEY_LAST_DEBLOAT_TS = "last_debloat_ts";
    private static final String BACKUP_FILENAME = "cleanyourtv_disabled_backup.txt";

    /**
     * Exact safety regexes ported from cleanyourtv/tools/disable.sh (CORE_RE and CORE_OK).
     * Any package matching CORE_RE and NOT matching CORE_OK is blocked from being disabled.
     */
    private static final Pattern CORE_RE = Pattern.compile(
            "^(android"
            + "|com\\.android\\.(systemui|settings|tv\\.settings|providers\\..*|inputmethod\\..*| server\\..*|permissioncontroller|packageinstaller| vending|defcontainer|shell|bluetooth|nfc|se|keychain|certinstaller|externalstorage|location\\.fused|networkstack.*|wifi.*|captiveportallogin|pacprocessor|proxyhandler|statementservice|companiondevicemanager|mms\\.service|mtp|ons|rkpdapp|sdksandbox|sharedstoragebackup|vpndialogs|wallpapercropper|htmlviewer)"
            + "|com\\.google\\.android\\.(gms|gsf|gsf\\.login|packageinstaller|permissioncontroller|ext\\.services|ext\\.shared|networkstack.*|captiveportallogin|webview|trichromelibrary|tv\\.frameworkpackagestubs|modulemetadata|ondevicepersonalization\\.services|federatedcompute|sdksandbox|adservices\\.api)"
            + ")$"
    );

    private static final Pattern CORE_OK = Pattern.compile(
            "^com\\.android\\.(providers\\.tv|providers\\.calendar|providers\\.contacts|providers\\.userdictionary|dreams\\..*|printspooler|bips|stk|cellbroadcastreceiver|calllogbackup|managedprovisioning|StatementService)$"
    );

    /**
     * Extra hardware/TV input packages from cleanyourtv/tools/recovery.py and CLAUDE.md
     * that must never be disabled because they break HDMI/tuner inputs or our own launcher.
     */
    private static final Set<String> NEVER_DISABLE_EXACT = new HashSet<>();
    static {
        NEVER_DISABLE_EXACT.add("com.techgenius.tvoslauncher");
        NEVER_DISABLE_EXACT.add("com.cltv.mal");
        NEVER_DISABLE_EXACT.add("com.mediatek.tis");
        NEVER_DISABLE_EXACT.add("com.mediatek.tv.service");
        NEVER_DISABLE_EXACT.add("com.mediatek.tv.agent");
        NEVER_DISABLE_EXACT.add("com.android.tv.settings");
        NEVER_DISABLE_EXACT.add("com.android.settings");
        NEVER_DISABLE_EXACT.add("com.android.systemui");
        NEVER_DISABLE_EXACT.add("com.android.vending");
        NEVER_DISABLE_EXACT.add("com.google.android.gms");
        NEVER_DISABLE_EXACT.add("com.google.android.gsf");
        NEVER_DISABLE_EXACT.add("com.google.android.webview");
        NEVER_DISABLE_EXACT.add("org.droidtv.playtv");
        NEVER_DISABLE_EXACT.add("org.droidtv.tunerservice");
        NEVER_DISABLE_EXACT.add("org.droidtv.channels");
        NEVER_DISABLE_EXACT.add("com.android.tv");
        NEVER_DISABLE_EXACT.add("com.google.android.tv");
        NEVER_DISABLE_EXACT.add("com.xiaomi.bluetooth");
        NEVER_DISABLE_EXACT.add("com.mi.bluetooth");
        NEVER_DISABLE_EXACT.add("com.xiaomi.mitv.bluetooth");
        NEVER_DISABLE_EXACT.add("com.mitv.bluetooth");
    }

    /** Generic Google / Android bloat packages that appear on almost all TVs (weighted lower in package similarity matching). */
    private static final Set<String> GENERIC_GOOGLE_PKGS = new HashSet<>();
    static {
        String[] generic = {
            "com.android.bips", "com.android.BookmarkProvider", "com.android.calllogbackup",
            "com.android.cts.ctsshim", "com.android.cts.priv.ctsshim", "com.android.dreams.basic",
            "com.android.ManagedProvisioning", "com.android.printspooler", "com.android.providers.calendar",
            "com.android.providers.contacts", "com.android.providers.tv", "com.android.providers.userdictionary",
            "com.android.SimAppDialog", "com.android.stk", "com.android.traceur", "com.android.vpndialogs",
            "com.android.wallpaperbackup", "com.google.android.apps.tv.launcherx",
            "com.google.android.backdrop", "com.google.android.CalendarSyncAdapter",
            "com.google.android.contacts", "com.google.android.feedback", "com.google.android.katniss",
            "com.google.android.leanbacklauncher", "com.google.android.leanbacklauncher.recommendations",
            "com.google.android.marvin.talkback", "com.google.android.partnersetup",
            "com.google.android.play.games", "com.google.android.syncadapters.calendar",
            "com.google.android.syncadapters.contacts", "com.google.android.tts",
            "com.google.android.tungsten.setupwraith", "com.google.android.tv.bugreportsender",
            "com.google.android.tvrecommendations", "com.google.android.videos",
            "com.google.android.youtube.tv", "com.google.android.youtube.tvmusic"
        };
        Collections.addAll(GENERIC_GOOGLE_PKGS, generic);
    }

    public static final class ModelEntry {
        public final String slug;
        public final String title;
        public final String headerMeta;
        public final List<String> packages;

        ModelEntry(String slug, String title, String headerMeta, List<String> packages) {
            this.slug = slug;
            this.title = title;
            this.headerMeta = headerMeta;
            this.packages = packages;
        }
    }

    public static final class DebloatItem {
        public final String pkg;
        public final String appLabel;
        public final String description;
        public final boolean optionalChoice;
        public boolean checked;

        DebloatItem(String pkg, String appLabel, String description, boolean optionalChoice, boolean checked) {
            this.pkg = pkg;
            this.appLabel = appLabel;
            this.description = description;
            this.optionalChoice = optionalChoice;
            this.checked = checked;
        }
    }

    public static final class ModelMatch {
        public final ModelEntry entry;
        public final int score;
        public final String reason;

        ModelMatch(ModelEntry entry, int score, String reason) {
            this.entry = entry;
            this.score = score;
            this.reason = reason;
        }
    }

    private static List<ModelEntry> cachedModels;
    private static final java.util.concurrent.ConcurrentHashMap<String, String> APP_LABEL_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> SAFE_DISABLE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private CleanYourTv() {}

    private static final Pattern LEGACY_LABEL_AND_PACKAGE = Pattern.compile("(.+) \\((.+)\\)", Pattern.DOTALL);

    static String labelAndPackageMessage(Context c, int resourceId, int hebrewFallbackId,
                                         String label, String pkg) {
        if (I18n.LANG_HE.equals(I18n.getLang(c))) {
            Matcher match = LEGACY_LABEL_AND_PACKAGE.matcher(String.valueOf(label) + " (" + pkg + ")");
            if (!match.matches()) return I18n.resourceString(c, hebrewFallbackId, label, pkg);
            return I18n.resourceString(c, resourceId, match.group(1), match.group(2));
        }
        return I18n.resourceString(c, resourceId, label, pkg);
    }

    static String packageMatchReason(Context c, int matched, int total, String detail) {
        String label = String.valueOf(detail);
        if (I18n.LANG_HE.equals(I18n.getLang(c))) label = I18nHe.translate(c, label);
        return I18n.resourceString(c, R.string.cyt_package_match_reason, matched, total, label);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static AlertDialog.Builder dialog(Context c, String title) {
        return new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title);
    }

    // ================================================================ Model Index, Download & Parsing

    public static File getCleanYourTvDir(Context c) {
        File pub = new File("/storage/emulated/0/Download/cleanyourtv");
        try {
            File parent = pub.getParentFile();
            if (parent != null && (parent.exists() || parent.mkdirs())) {
                if (pub.exists() || pub.mkdirs()) {
                    if (pub.canWrite()) return pub;
                }
            }
        } catch (Throwable ignored) {
        }
        return new File(c.getFilesDir(), "cleanyourtv");
    }

    public static File findExamplesDir(File baseDir) {
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) return null;
        File direct = new File(baseDir, "examples");
        if (direct.isDirectory()) return direct;
        File[] children = baseDir.listFiles();
        if (children != null) {
            for (File ch : children) {
                if (ch.isDirectory() && ch.getName().toLowerCase(Locale.US).startsWith("cleanyourtv")) {
                    File ex = new File(ch, "examples");
                    if (ex.isDirectory()) return ex;
                }
            }
            for (File ch : children) {
                if (ch.isDirectory()) {
                    File ex = new File(ch, "examples");
                    if (ex.isDirectory()) return ex;
                }
            }
        }
        return null;
    }

    public static int getExtractedModelCountOnDisk(Context c) {
        File baseDir = getCleanYourTvDir(c);
        File ex = findExamplesDir(baseDir);
        if (ex != null && ex.isDirectory()) {
            File[] dirs = ex.listFiles();
            if (dirs != null) {
                int count = 0;
                for (File d : dirs) {
                    if (d.isDirectory()) count++;
                }
                return count;
            }
        }
        if (new File(baseDir, "debloat.txt").isFile()) return 1;
        return 0;
    }

    public static boolean hasFullExamplesExtracted(Context c) {
        return getExtractedModelCountOnDisk(c) > 1;
    }

    public static boolean hasSelectedModelFileOnDisk(Context c) {
        String slug = getSelectedModelSlug(c);
        if (slug.isEmpty()) return false;
        File f = resolveDebloatFileForModel(c, slug);
        return f != null && f.isFile();
    }

    public static boolean hasCleanYourTvFiles(Context c) {
        File dir = getCleanYourTvDir(c);
        if (!dir.exists() || !dir.isDirectory()) return false;
        String sel = getSelectedModelSlug(c);
        if (!sel.isEmpty()) {
            return hasSelectedModelFileOnDisk(c);
        }
        return getExtractedModelCountOnDisk(c) > 0;
    }

    public static String getDownloadStatusSummary(Context c) {
        int n = getExtractedModelCountOnDisk(c);
        if (n > 1) {
            return I18n.resourceString(c, R.string.message_downloaded_1_s_tv_models_ready, n);
        }
        if (n == 1 && hasCleanYourTvFiles(c)) {
            String slug = getSelectedModelSlug(c);
            return I18n.resourceString(c, R.string.cyt_one_model_kept, slug.isEmpty() ? "selected-tv" : slug);
        }
        return I18n.resourceString(c, R.string.text_not_downloaded_tap_to_download);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static ModelEntry parseDebloatTxtToModelEntry(String slug, File debloatFile) {
        if (debloatFile == null || !debloatFile.isFile()) return null;
        String title = "";
        StringBuilder metaSb = new StringBuilder();
        List<String> pkgs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(debloatFile), StandardCharsets.UTF_8))) {
            String raw;
            int commentLineIdx = 0;
            while ((raw = br.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) {
                    String after = line.substring(1).trim();
                    if (!after.isEmpty()) {
                        commentLineIdx++;
                        if (commentLineIdx == 1) {
                            title = after;
                        } else if (commentLineIdx <= 6 && !after.startsWith("Apply:") && !after.startsWith("Undo:")) {
                            if (metaSb.length() > 0) metaSb.append(" ");
                            metaSb.append(after);
                        }
                    }
                    continue;
                }
                int h = line.indexOf('#');
                String pkg = (h >= 0 ? line.substring(0, h) : line).trim();
                if (looksLikePackageName(pkg) && !pkgs.contains(pkg)) {
                    pkgs.add(pkg);
                }
            }
        } catch (Throwable ignored) {
        }
        if (title.isEmpty()) title = slug;
        return new ModelEntry(slug, title, metaSb.toString(), pkgs);
    }

    public static synchronized List<ModelEntry> loadIndex(Context c) {
        if (cachedModels != null && !cachedModels.isEmpty()) return cachedModels;
        List<ModelEntry> list = new ArrayList<>();
        File baseDir = getCleanYourTvDir(c);
        File examplesDir = findExamplesDir(baseDir);
        if (examplesDir != null && examplesDir.isDirectory()) {
            File[] modelDirs = examplesDir.listFiles();
            if (modelDirs != null) {
                for (File md : modelDirs) {
                    if (!md.isDirectory()) continue;
                    File deb = new File(md, "debloat.txt");
                    if (deb.isFile()) {
                        ModelEntry entry = parseDebloatTxtToModelEntry(md.getName(), deb);
                        if (entry != null) list.add(entry);
                    }
                }
            }
            Collections.sort(list, (a, b) -> a.slug.compareToIgnoreCase(b.slug));
        }
        if (baseDir.isDirectory()) {
            File rootDeb = new File(baseDir, "debloat.txt");
            String slug = getSelectedModelSlug(c);
            if (!slug.isEmpty() && rootDeb.isFile()) {
                boolean alreadyInList = false;
                for (ModelEntry m : list) {
                    if (m.slug.equalsIgnoreCase(slug)) {
                        alreadyInList = true;
                        break;
                    }
                }
                if (!alreadyInList) {
                    ModelEntry entry = parseDebloatTxtToModelEntry(slug, rootDeb);
                    if (entry != null) list.add(0, entry);
                }
            } else if (list.isEmpty() && rootDeb.isFile()) {
                ModelEntry entry = parseDebloatTxtToModelEntry(slug.isEmpty() ? "selected-tv" : slug, rootDeb);
                if (entry != null) list.add(entry);
            }
        }
        cachedModels = list;
        return list;
    }

    public static synchronized void invalidateModelCache() {
        cachedModels = null;
    }

    public static ModelEntry findModelBySlug(Context c, String slug) {
        if (slug == null || slug.isEmpty()) return null;
        List<ModelEntry> cached = cachedModels;
        if (cached != null) {
            for (ModelEntry m : cached) {
                if (m.slug.equalsIgnoreCase(slug)) return m;
            }
        }
        File f = resolveDebloatFileForModel(c, slug);
        if (f != null && f.isFile()) {
            return parseDebloatTxtToModelEntry(slug, f);
        }
        for (ModelEntry m : loadIndex(c)) {
            if (m.slug.equalsIgnoreCase(slug)) return m;
        }
        return null;
    }

    public static String getSelectedModelSlug(Context c) {
        return prefs(c).getString(KEY_MODEL_SLUG, "");
    }

    public static String getSelectedModelSummary(Context c) {
        String slug = getSelectedModelSlug(c);
        if (!slug.isEmpty() && !hasSelectedModelFileOnDisk(c)) {
            return I18n.resourceString(c, R.string.message_selected_tv_files_missing_1_s_download_cleanyour, slug);
        }
        if (!hasCleanYourTvFiles(c)) {
            return I18n.resourceString(c, R.string.text_download_cleanyourtv_first);
        }
        if (slug.isEmpty()) {
            return I18n.resourceString(c, R.string.text_tap_to_detect_select);
        }
        String savedTitle = prefs(c).getString(KEY_MODEL_TITLE, slug);
        if (savedTitle == null || savedTitle.isEmpty()) savedTitle = slug;
        return savedTitle + " (" + slug + ")";
    }

    public static synchronized void setSelectedModel(Context c, ModelEntry entry) {
        if (entry == null) return;
        prefs(c).edit()
                .putString(KEY_MODEL_SLUG, entry.slug)
                .putString(KEY_MODEL_TITLE, entry.title)
                .apply();
    }

    /**
     * Asks the user if they want to delete unnecessary CleanYourTV model files to save space.
     * If Yes, shows a progress popup while moving the selected model's files and deleting the rest on a background thread.
     */
    public static void promptCleanUpUnnecessaryFiles(final Activity activity, final ModelEntry selectedEntry, final Runnable onDone) {
        if (selectedEntry == null || !hasFullExamplesExtracted(activity)) {
            if (onDone != null) onDone.run();
            return;
        }
        dialog(activity, I18n.resourceString(activity, R.string.text_clean_up_space))
                .setMessage(I18n.resourceString(activity, R.string.message_selected_tv_model_1_s_would_you_like_to_delete_t, selectedEntry.title))
                .setPositiveButton(I18n.resourceString(activity, R.string.text_yes_delete_unnecessary_files), (d, w) -> {
                    final AlertDialog delProgress = dialog(activity, I18n.resourceString(activity, R.string.text_cleaning_up_files))
                            .setMessage(I18n.resourceString(activity, R.string.text_preparing_to_clean_up_unnecessary_files))
                            .setCancelable(false)
                            .create();
                    delProgress.show();

                    new Thread(() -> {
                        try {
                            File baseDir = getCleanYourTvDir(activity);
                            File examplesDir = findExamplesDir(baseDir);
                            if (examplesDir != null && examplesDir.isDirectory()) {
                                File modelDir = new File(examplesDir, selectedEntry.slug);
                                if (modelDir.isDirectory()) {
                                    // 1. Copy selected model's files directly into baseDir FIRST so they are safe
                                    File[] mFiles = modelDir.listFiles();
                                    if (mFiles != null) {
                                        for (File mf : mFiles) {
                                            if (mf.isFile()) {
                                                File dst = new File(baseDir, mf.getName());
                                                try (java.io.FileInputStream fis = new java.io.FileInputStream(mf);
                                                     FileOutputStream fos = new FileOutputStream(dst)) {
                                                    byte[] buf = new byte[8192];
                                                    int r;
                                                    while ((r = fis.read(buf)) != -1) {
                                                        fos.write(buf, 0, r);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    try (FileOutputStream sfos = new FileOutputStream(new File(baseDir, ".selected_slug"))) {
                                        sfos.write(selectedEntry.slug.getBytes(StandardCharsets.UTF_8));
                                    }
                                }

                                // 2. Delete all model folders in examplesDir with live progress
                                File[] allDirs = examplesDir.listFiles();
                                if (allDirs != null) {
                                    final int total = allDirs.length;
                                    long lastUi = 0L;
                                    for (int i = 0; i < total; i++) {
                                        deleteRecursive(allDirs[i]);
                                        long now = System.currentTimeMillis();
                                        if (now - lastUi > 120L || i == total - 1) {
                                            lastUi = now;
                                            final int done = i + 1;
                                            activity.runOnUiThread(() -> {
                                                if (!activity.isFinishing() && delProgress.isShowing()) {
                                                    delProgress.setMessage(I18n.resourceString(activity, R.string.message_deleting_unnecessary_model_files_1_s_2_s, done, total));
                                                }
                                            });
                                        }
                                    }
                                }

                                // 3. Delete the empty cleanyourtv-x.x.x wrapper directory
                                File[] topChildren = baseDir.listFiles();
                                if (topChildren != null) {
                                    for (File ch : topChildren) {
                                        if (ch.isDirectory()) {
                                            deleteRecursive(ch);
                                        }
                                    }
                                }
                            }
                            synchronized (CleanYourTv.class) {
                                cachedModels = Collections.singletonList(selectedEntry);
                            }
                        } catch (Throwable ignored) {
                        }
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing()) return;
                            try {
                                delProgress.dismiss();
                            } catch (Throwable ignored) {
                            }
                            if (onDone != null) onDone.run();
                        });
                    }, "cyt-cleanup").start();
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_no_keep_all), (d, w) -> {
                    if (onDone != null) onDone.run();
                })
                .setCancelable(false)
                .show();
    }

    public static void downloadAndExtractLatestRelease(final Activity activity, final Runnable onDone) {
        final AlertDialog progress = dialog(activity, I18n.resourceString(activity, R.string.text_downloading_cleanyourtv))
                .setMessage(I18n.resourceString(activity, R.string.text_downloading_to_download_folder))
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            boolean ok = false;
            int modelCount = 0;
            String errMsg = "";
            File baseDir = getCleanYourTvDir(activity);
            File tempZip = new File(baseDir, "cleanyourtv-latest.zip");
            try {
                if (!baseDir.exists()) baseDir.mkdirs();
                File[] existing = baseDir.listFiles();
                if (existing != null && existing.length > 0) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && progress.isShowing()) {
                            progress.setMessage(I18n.resourceString(activity, R.string.text_preparing_download_folder));
                        }
                    });
                    for (File f : existing) {
                        deleteRecursive(f);
                    }
                }

                // 1. Resolve latest release zip URL from https://api.github.com/repos/Gathin23/cleanyourtv/releases/latest
                String zipUrl = null;
                try {
                    java.net.HttpURLConnection apiConn = (java.net.HttpURLConnection)
                            new java.net.URL("https://api.github.com/repos/Gathin23/cleanyourtv/releases/latest").openConnection();
                    apiConn.setConnectTimeout(8000);
                    apiConn.setReadTimeout(8000);
                    apiConn.setRequestProperty("User-Agent", "tvOS-Launcher");
                    apiConn.setRequestProperty("Accept", "application/vnd.github+json");
                    if (apiConn.getResponseCode() == 200) {
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(apiConn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                        org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                        String tag = json.optString("tag_name", "").trim();
                        if (!tag.isEmpty()) {
                            zipUrl = "https://codeload.github.com/Gathin23/cleanyourtv/zip/refs/tags/" + tag;
                        } else {
                            zipUrl = json.optString("zipball_url", null);
                        }
                    }
                    apiConn.disconnect();
                } catch (Throwable ignored) {
                }
                if (zipUrl == null || zipUrl.isEmpty()) {
                    zipUrl = "https://codeload.github.com/Gathin23/cleanyourtv/zip/refs/heads/main";
                }

                // 2. Download source code zip to Download folder with live progress
                java.net.HttpURLConnection dlConn = (java.net.HttpURLConnection) new java.net.URL(zipUrl).openConnection();
                dlConn.setInstanceFollowRedirects(true);
                dlConn.setConnectTimeout(12000);
                dlConn.setReadTimeout(25000);
                dlConn.setRequestProperty("User-Agent", "tvOS-Launcher");
                final int contentLen = dlConn.getContentLength();
                long totalRead = 0L;
                long lastUiUpdate = 0L;
                try (InputStream in = dlConn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tempZip)) {
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                        totalRead += r;
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdate > 120L) {
                            lastUiUpdate = now;
                            final long kb = totalRead / 1024L;
                            final int pct = contentLen > 0 ? (int) Math.min(100L, (totalRead * 100L) / contentLen) : -1;
                            activity.runOnUiThread(() -> {
                                if (!activity.isFinishing() && progress.isShowing()) {
                                    String progStr = pct >= 0 ? (pct + "% · " + kb + " KB") : (kb + " KB");
                                    progress.setMessage(I18n.resourceString(activity, R.string.message_downloading_to_download_folder_1_s, progStr));
                                }
                            });
                        }
                    }
                } finally {
                    dlConn.disconnect();
                }

                // 3. Unzip cleanyourtv-x.x.x/examples/... with live progress
                String canonicalBase = baseDir.getCanonicalPath();
                int extractedFiles = 0;
                long lastUnzipUi = 0L;
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                        new java.io.BufferedInputStream(new java.io.FileInputStream(tempZip)))) {
                    java.util.zip.ZipEntry ze;
                    byte[] buf = new byte[16384];
                    while ((ze = zis.getNextEntry()) != null) {
                        String name = ze.getName();
                        if (!ze.isDirectory() && name.contains("/examples/")) {
                            File outFile = new File(baseDir, name);
                            if (outFile.getCanonicalPath().startsWith(canonicalBase)) {
                                File parent = outFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();
                                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                    int r;
                                    while ((r = zis.read(buf)) != -1) {
                                        fos.write(buf, 0, r);
                                    }
                                }
                                extractedFiles++;
                                long now = System.currentTimeMillis();
                                if (now - lastUnzipUi > 120L) {
                                    lastUnzipUi = now;
                                    final int extCount = extractedFiles;
                                    activity.runOnUiThread(() -> {
                                        if (!activity.isFinishing() && progress.isShowing()) {
                                            progress.setMessage(I18n.resourceString(activity, R.string.message_unzipping_tv_models_in_download_folder_1_s_files, extCount));
                                        }
                                    });
                                }
                            }
                        }
                        zis.closeEntry();
                    }
                }

                // 4. Delete the temporary zip file so no zip leftover remains
                if (tempZip.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempZip.delete();
                }

                // 5. Load models from cleanyourtv-x.x.x/examples
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && progress.isShowing()) {
                        progress.setMessage(I18n.resourceString(activity, R.string.text_loading_tv_models));
                    }
                });
                invalidateModelCache();
                List<ModelEntry> loaded = loadIndex(activity);
                modelCount = loaded.size();
                ok = modelCount > 0;
            } catch (Throwable t) {
                errMsg = t.getMessage() != null ? t.getMessage() : t.toString();
            } finally {
                if (tempZip.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempZip.delete();
                }
            }

            final boolean finalOk = ok;
            final int finalCount = modelCount;
            final String finalErr = errMsg;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                if (finalOk) {
                    if (onDone != null) onDone.run();
                } else {
                    dialog(activity, I18n.resourceString(activity, R.string.text_download_failed))
                            .setMessage(I18n.resourceString(activity, R.string.message_could_not_download_cleanyourtv_from_github_1_s, finalErr))
                            .setPositiveButton(I18n.resourceString(activity, R.string.text_retry),
                                    (d, w) -> downloadAndExtractLatestRelease(activity, onDone))
                            .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                            .show();
                }
            });
        }, "cyt-download").start();
    }

    public static void promptDownloadRequired(final Activity activity, final boolean deletedWarning, final Runnable onDownloaded) {
        int count = loadIndex(activity).size();
        boolean onlyOneKept = !deletedWarning && count == 1;
        String title = deletedWarning
                ? I18n.resourceString(activity, R.string.text_cleanyourtv_files_missing)
                : (onlyOneKept
                        ? I18n.resourceString(activity, R.string.text_download_all_tv_models_again)
                        : I18n.resourceString(activity, R.string.text_download_cleanyourtv_required));
        String msg = deletedWarning
                ? I18n.resourceString(activity, R.string.text_your_selected_tv_model_s_files_in_the_do)
                : (onlyOneKept
                        ? I18n.resourceString(activity, R.string.text_only_your_currently_selected_tv_model_is)
                        : I18n.resourceString(activity, R.string.text_please_download_cleanyourtv_first_it_wil));
        dialog(activity, title)
                .setMessage(msg)
                .setPositiveButton(I18n.resourceString(activity, R.string.text_download_cleanyourtv_now),
                        (d, w) -> downloadAndExtractLatestRelease(activity, onDownloaded))
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .show();
    }

    // ================================================================ Status & Saved Disabled Data

    public static List<String> getSavedDisabledPackages(Context c) {
        String csv = prefs(c).getString(KEY_DISABLED_PKGS, "");
        List<String> out = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String p : csv.split(",")) {
                String pt = p.trim();
                if (!pt.isEmpty() && !out.contains(pt)) out.add(pt);
            }
        }
        if (!out.isEmpty()) return out;
        // Fallback to backup file in filesDir
        File f = new File(c.getFilesDir(), BACKUP_FILENAME);
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String pt = line.trim();
                    if (!pt.isEmpty() && !pt.startsWith("#") && !out.contains(pt)) {
                        out.add(pt);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static void saveDisabledPackages(Context c, String modelSlug, List<String> newlyDisabled) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(getSavedDisabledPackages(c));
        merged.addAll(newlyDisabled);
        String csv = TextUtils.join(",", merged);
        prefs(c).edit()
                .putString(KEY_DISABLED_PKGS, csv)
                .putString(KEY_DEBLOAT_DONE_MODEL, modelSlug != null ? modelSlug : "")
                .putLong(KEY_LAST_DEBLOAT_TS, System.currentTimeMillis())
                .apply();

        // Also persist to a plain text backup file
        File f = new File(c.getFilesDir(), BACKUP_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(f, false)) {
            StringBuilder sb = new StringBuilder();
            sb.append("# CleanYourTV disabled packages backup\n");
            sb.append("# Model: ").append(modelSlug).append("\n");
            for (String pkg : merged) {
                sb.append(pkg).append("\n");
            }
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void clearSavedDisabledPackages(Context c) {
        prefs(c).edit()
                .remove(KEY_DISABLED_PKGS)
                .remove(KEY_DEBLOAT_DONE_MODEL)
                .apply();
        File f = new File(c.getFilesDir(), BACKUP_FILENAME);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /**
     * Decides whether debloat is Done or Not done based on the selected TV model
     * and the actual disabled state of packages from that model on the TV.
     */
    public static boolean isDebloatDone(Context c) {
        String slug = getSelectedModelSlug(c);
        if (slug.isEmpty()) return false;

        String doneModel = prefs(c).getString(KEY_DEBLOAT_DONE_MODEL, "");
        List<String> saved = getSavedDisabledPackages(c);
        if (slug.equalsIgnoreCase(doneModel) && !saved.isEmpty()) {
            return true;
        }

        ModelEntry entry = findModelBySlug(c, slug);
        if (entry == null || entry.packages.isEmpty()) return false;

        PackageManager pm = c.getPackageManager();
        int installedFromProfile = 0;
        int disabledFromProfile = 0;
        for (String pkg : entry.packages) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai != null) {
                    installedFromProfile++;
                    if (!ai.enabled) disabledFromProfile++;
                }
            } catch (Throwable ignored) {
            }
        }
        return installedFromProfile > 0 && disabledFromProfile >= Math.max(3, installedFromProfile / 2);
    }

    public static String getDebloatStatusSummary(Context c) {
        String slug = getSelectedModelSlug(c);
        if (slug.isEmpty()) {
            return I18n.resourceString(c, R.string.text_not_done_select_tv_model_first);
        }
        ModelEntry entry = findModelBySlug(c, slug);
        PackageManager pm = c.getPackageManager();
        int disabledCount = 0;
        int installedCount = 0;
        if (entry != null) {
            for (String pkg : entry.packages) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    if (ai != null) {
                        installedCount++;
                        if (!ai.enabled) disabledCount++;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        List<String> saved = getSavedDisabledPackages(c);
        if (isDebloatDone(c)) {
            int count = Math.max(disabledCount, saved.size());
            return I18n.resourceString(c, R.string.cyt_summary_done, count);
        }
        if (disabledCount > 0) {
            return I18n.resourceString(c, R.string.cyt_summary_not_done, disabledCount, installedCount);
        }
        return I18n.resourceString(c, R.string.text_not_done);
    }

    public static String getSavedDisabledSummary(Context c) {
        List<String> saved = getSavedDisabledPackages(c);
        if (saved.isEmpty()) {
            return I18n.resourceString(c, R.string.text_none_saved);
        }
        return saved.size() + " " + I18n.resourceString(c, R.string.text_apps_ebb18e);
    }

    // ================================================================ Safety Guards

    /**
     * Returns true if {@code pkg} or {@code appLabel} is a critical system service (such as MiBluetooth)
     * that must NEVER be disabled under any circumstances, even in Manual Debloat.
     */
    public static boolean isCriticalProtectedService(String pkg, String appLabel) {
        if (pkg != null) {
            String lowPkg = pkg.toLowerCase(Locale.US);
            if (lowPkg.equals("com.xiaomi.bluetooth")
                    || lowPkg.equals("com.mi.bluetooth")
                    || lowPkg.equals("com.xiaomi.mitv.bluetooth")
                    || lowPkg.equals("com.mitv.bluetooth")
                    || lowPkg.contains("mibluetooth")
                    || lowPkg.contains("xiaomi.bluetooth")) {
                return true;
            }
        }
        if (appLabel != null) {
            String lowLabel = appLabel.toLowerCase(Locale.US).replace(" ", "");
            if (lowLabel.contains("mibluetooth")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a package is safe to disable according to cleanyourtv/tools/disable.sh
     * and recovery.py (never disables core system packages, TV input hardware services, or tvOS Launcher).
     */
    public static boolean isSafeToDisable(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if ("android".equals(pkg)) return false;
        Boolean cached = SAFE_DISABLE_CACHE.get(pkg);
        if (cached != null) return cached;
        if (NEVER_DISABLE_EXACT.contains(pkg) || isCriticalProtectedService(pkg, null)) {
            SAFE_DISABLE_CACHE.put(pkg, false);
            return false;
        }
        if (CORE_RE.matcher(pkg).matches() && !CORE_OK.matcher(pkg).matches()) {
            SAFE_DISABLE_CACHE.put(pkg, false);
            return false;
        }
        // Protect TV input hardware services (HDMI / tuner inputs)
        try {
            PackageManager pm = c.getPackageManager();
            if (pm.checkPermission("android.permission.TV_INPUT_HARDWARE", pkg)
                    == PackageManager.PERMISSION_GRANTED) {
                SAFE_DISABLE_CACHE.put(pkg, false);
                return false;
            }
        } catch (Throwable ignored) {
        }
        SAFE_DISABLE_CACHE.put(pkg, true);
        return true;
    }

    /**
     * Returns true if {@code pkg} is a generic Android / Google system package or common third-party
     * streaming app that appears across many TV brands (and therefore is NOT an OEM-identifying package).
     */
    private static boolean isGenericOrStreamingPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (GENERIC_GOOGLE_PKGS.contains(pkg)) return true;
        String low = pkg.toLowerCase(Locale.US);
        if (low.startsWith("com.android.")
                || low.startsWith("com.google.")
                || low.startsWith("android.")
                || low.startsWith("fusion.android.")
                || low.equals("android")) {
            return true;
        }
        String[] genericPrefixes = {
            "com.netflix.", "com.amazon.amazonvideo", "com.amazon.avod", "com.disney.",
            "com.spotify.", "com.apple.atve", "tv.pluto.", "com.tubitv", "com.wbd.",
            "com.hbo.", "com.peacock", "com.fubo", "com.hulu.", "in.startv.",
            "com.tencent.", "com.iqiyi.", "com.facebook.", "com.rakuten.",
            "com.plexapp.", "com.deezer.", "com.dazn", "com.crunchyroll.",
            "air.com.vudu.", "com.cbs.", "com.espn.", "com.mlb.", "org.videolan.",
            "org.smarttube.", "com.teamst.vanced", "com.kodi.", "org.xbmc."
        };
        for (String pref : genericPrefixes) {
            if (low.startsWith(pref)) return true;
        }
        return false;
    }

    /**
     * Maps a package name to its OEM/vendor family (e.g. "tcl", "philips", "sony", "xiaomi",
     * "hisense", "skyworth", "haier", "changhong", "vestel", "cvte", "zeasn", "sdmc", "realtek", "amlogic", "mediatek").
     */
    private static String detectOemFamilyFromPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        String low = pkg.toLowerCase(Locale.US);
        if (low.startsWith("com.tcl.") || low.startsWith("cn.tcl.") || low.startsWith("com.falcon.")
                || low.startsWith("com.ffalcon.") || low.startsWith("com.tcltv.") || low.contains(".tcl.")) {
            return "tcl";
        }
        if (low.startsWith("org.droidtv.") || low.startsWith("com.tpvision.") || low.startsWith("com.philips.")
                || low.contains(".droidtv.") || low.contains(".tpvision.")) {
            return "philips";
        }
        if (low.startsWith("com.sony.") || low.contains(".bravia.") || low.contains(".sonydtv.")) {
            return "sony";
        }
        if (low.startsWith("com.xiaomi.") || low.startsWith("com.mitv.") || low.startsWith("com.miui.")
                || low.startsWith("com.mi.tv.") || low.startsWith("com.duokan.")) {
            return "xiaomi";
        }
        if (low.startsWith("com.hisense.") || low.startsWith("com.jamdeo.") || low.contains(".hisense.")) {
            return "hisense";
        }
        if (low.startsWith("com.skyworth.") || low.startsWith("com.coocaa.") || low.startsWith("com.tianci.")
                || low.startsWith("com.sdt.")) {
            return "skyworth";
        }
        if (low.startsWith("com.haier.") || low.startsWith("com.haierubic.") || low.startsWith("com.homwee.")) {
            return "haier";
        }
        if (low.startsWith("com.changhong.") || low.startsWith("com.gdch.") || low.startsWith("cn.changhong.")) {
            return "changhong";
        }
        if (low.startsWith("com.vestel.") || low.startsWith("com. Cabot.") || low.startsWith("com.beko.")
                || low.startsWith("com.arcelik.")) {
            return "vestel";
        }
        if (low.startsWith("com.cvte.") || low.startsWith("cn.cvte.") || low.startsWith("com.cultraview.")) {
            return "cvte";
        }
        if (low.startsWith("com.zeasn.") || low.startsWith("cn.zeasn.") || low.startsWith("com.whaleos.")) {
            return "zeasn";
        }
        if (low.startsWith("com.sdmc.") || low.startsWith("com.nes.") || low.startsWith("com.sei.")) {
            return "sdmc";
        }
        if (low.startsWith("com.apps.") || low.startsWith("com.mk.tv.") || low.startsWith("com.oceanwing.")
                || low.startsWith("com.zhixin.")) {
            return "scbc";
        }
        if (low.startsWith("com.amazon.tv.") || low.startsWith("com.amazon.fireos") || low.startsWith("com.amazon.device.")) {
            return "amazon";
        }
        if (low.startsWith("com.realtek.") || low.startsWith("com.rtk.")) {
            return "realtek";
        }
        if (low.startsWith("com.droidlogic.") || low.startsWith("com.amlogic.") || low.startsWith("com.meson.")) {
            return "amlogic";
        }
        if (low.startsWith("com.mediatek.") || low.startsWith("com.mstar.") || low.startsWith("com.mtk.")) {
            return "mediatek";
        }
        return "";
    }

    private static Set<String> detectOemFamiliesForModel(ModelEntry entry) {
        Set<String> families = new HashSet<>();
        String text = (entry.slug + " " + entry.title + " " + entry.headerMeta).toLowerCase(Locale.US);
        if (text.contains("tcl") || text.contains("ffalcon") || text.contains("thomson") || text.contains("ifalcon")) families.add("tcl");
        if (text.contains("philips") || text.contains("tpvision") || text.contains("droidtv") || text.contains("pus") || text.contains("pml") || text.contains("oled")) {
            if (text.contains("philips") || text.contains("tpvision") || text.contains("droidtv")) families.add("philips");
        }
        if (text.contains("sony") || text.contains("bravia")) families.add("sony");
        if (text.contains("xiaomi") || text.contains("mitv") || text.contains("mi-tv") || text.contains("mi-box") || text.contains("redmi")) families.add("xiaomi");
        if (text.contains("hisense") || text.contains("toshiba") || text.contains("jamdeo")) families.add("hisense");
        if (text.contains("skyworth") || text.contains("coocaa") || text.contains("strong") || text.contains("metz")) families.add("skyworth");
        if (text.contains("haier") || text.contains("homwee")) families.add("haier");
        if (text.contains("changhong") || text.contains("chiq")) families.add("changhong");
        if (text.contains("vestel") || text.contains("beko") || text.contains("grundig") || text.contains("arcelik") || text.contains("jvc") || text.contains("hitachi") || text.contains("telefunken")) families.add("vestel");
        if (text.contains("sharp") || text.contains("onn") || text.contains("walmart")) families.add("sdmc");
        for (String pkg : entry.packages) {
            String f = detectOemFamilyFromPackage(pkg);
            if (!f.isEmpty()) families.add(f);
        }
        return families;
    }

    // ================================================================ Model Matching (Hardware + ADB Package Similarity)

    private static List<String> tokenize(String s) {
        if (s == null || s.isEmpty()) return Collections.emptyList();
        String norm = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (norm.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String p : norm.split("\\s+")) {
            if (p.length() >= 2 && !isStopWord(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean isStopWord(String w) {
        return "tv".equals(w) || "smart".equals(w) || "android".equals(w)
                || "google".equals(w) || "4k".equals(w) || "uhd".equals(w)
                || "fhd".equals(w) || "hd".equals(w) || "led".equals(w)
                || "qled".equals(w) || "oled".equals(w) || "pro".equals(w)
                || "max".equals(w) || "plus".equals(w) || "stick".equals(w)
                || "box".equals(w) || "edition".equals(w) || "series".equals(w);
    }

    public static final class HardwareMatchResult {
        public final ModelMatch exactSingleMatch;
        public final List<ModelMatch> candidates;
        public final String deviceDescription;

        HardwareMatchResult(ModelMatch exactSingleMatch, List<ModelMatch> candidates, String deviceDescription) {
            this.exactSingleMatch = exactSingleMatch;
            this.candidates = candidates;
            this.deviceDescription = deviceDescription;
        }
    }

    /**
     * Searches the 341 CleanYourTV profiles for exact or similar TV model/brand/device/product matches.
     */
    public static HardwareMatchResult findHardwareMatches(Context c) {
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        String brand = Build.BRAND != null ? Build.BRAND.trim() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.trim() : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT.trim() : "";

        String modelLower = model.toLowerCase(Locale.US);
        String brandLower = brand.toLowerCase(Locale.US);
        String manufLower = manufacturer.toLowerCase(Locale.US);
        String deviceLower = device.toLowerCase(Locale.US);
        String productLower = product.toLowerCase(Locale.US);

        String modelSlugGuess = (brand + " " + model).toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String modelOnlySlugGuess = modelLower
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

        List<String> modelTokens = tokenize(model);
        List<String> brandTokens = new ArrayList<>();
        for (String t : tokenize(brand + " " + manufacturer)) {
            if (!brandTokens.contains(t)) brandTokens.add(t);
        }

        List<ModelMatch> exactMatches = new ArrayList<>();
        List<ModelMatch> similarMatches = new ArrayList<>();

        for (ModelEntry entry : loadIndex(c)) {
            String slugLow = entry.slug.toLowerCase(Locale.US);
            String titleLow = entry.title.toLowerCase(Locale.US);
            String metaLow = entry.headerMeta.toLowerCase(Locale.US);

            int score = 0;
            List<String> reasons = new ArrayList<>();

            // 1. Direct slug or title exact model match
            if (!modelSlugGuess.isEmpty() && slugLow.equals(modelSlugGuess)) {
                score += 120;
                reasons.add(I18n.resourceString(c, R.string.text_exact_model_match));
            } else if (!modelOnlySlugGuess.isEmpty() && modelOnlySlugGuess.length() >= 4
                    && (slugLow.equals(modelOnlySlugGuess) || slugLow.endsWith("-" + modelOnlySlugGuess))) {
                score += 105;
                reasons.add(I18n.resourceString(c, R.string.text_exact_model_code_match));
            }

            // 2. Check ro.product.model="...", product, device inside headerMeta
            if (!modelLower.isEmpty() && modelLower.length() >= 3) {
                if (metaLow.contains("ro.product.model=\"" + modelLower + "\"")
                        || metaLow.contains("model=\"" + modelLower + "\"")
                        || titleLow.contains("(" + modelLower + ")")) {
                    score += 90;
                    reasons.add(I18n.resourceString(c, R.string.message_model_1_s, model));
                }
            }

            if (!productLower.isEmpty() && productLower.length() >= 3
                    && (metaLow.contains("product " + productLower) || metaLow.contains(productLower))) {
                score += 80;
                reasons.add(I18n.resourceString(c, R.string.message_product_1_s, product));
            }

            if (!deviceLower.isEmpty() && deviceLower.length() >= 3
                    && (metaLow.contains("device " + deviceLower)
                        || metaLow.contains("device: " + deviceLower)
                        || slugLow.contains("-" + deviceLower))) {
                score += 65;
                reasons.add(I18n.resourceString(c, R.string.message_board_device_1_s, device));
            }

            // 3. Brand / Manufacturer match
            boolean brandMatched = false;
            for (String bt : brandTokens) {
                if (bt.length() < 3) continue;
                if (slugLow.startsWith(bt + "-") || titleLow.startsWith(bt + " ")
                        || metaLow.contains("brand " + bt)
                        || metaLow.contains("manufacturer " + bt)
                        || metaLow.contains(bt)) {
                    brandMatched = true;
                    break;
                }
            }
            if (brandMatched) {
                score += 35;
                reasons.add(I18n.resourceString(c, R.string.cyt_brand_oem, brand.isEmpty() ? manufacturer : brand));
            }

            // 4. Token overlap with model name
            int matchedTokens = 0;
            for (String mt : modelTokens) {
                if (mt.length() >= 3 && (slugLow.contains(mt) || titleLow.contains(mt) || metaLow.contains(mt))) {
                    matchedTokens++;
                }
            }
            if (matchedTokens > 0) {
                score += matchedTokens * 25;
                if (reasons.isEmpty()) {
                    reasons.add(I18n.resourceString(c, R.string.text_similar_model_name));
                }
            }

            if (score >= 100) {
                exactMatches.add(new ModelMatch(entry, score, TextUtils.join(" • ", reasons)));
            } else if (score >= 35) {
                similarMatches.add(new ModelMatch(entry, score, TextUtils.join(" • ", reasons)));
            }
        }

        Collections.sort(exactMatches, (a, b) -> Integer.compare(b.score, a.score));
        Collections.sort(similarMatches, (a, b) -> Integer.compare(b.score, a.score));

        List<ModelMatch> combined = new ArrayList<>();
        combined.addAll(exactMatches);
        for (ModelMatch m : similarMatches) {
            if (combined.size() >= 25) break;
            combined.add(m);
        }

        String desc = (brand.isEmpty() ? manufacturer : brand) + " " + model
                + (device.isEmpty() ? "" : " (" + device + (product.isEmpty() ? "" : " / " + product) + ")");

        ModelMatch singleExact = (exactMatches.size() == 1 && similarMatches.isEmpty())
                ? exactMatches.get(0) : null;
        return new HardwareMatchResult(singleExact, combined, desc.trim());
    }

    /**
     * Pulls the full package list from ADB (`pm list packages --user 0`, falling back to PackageManager)
     * and ranks all 341 CleanYourTV models primarily by OEM/vendor-specific package overlap
     * (e.g. TCL `com.tcl.*`/`com.falcon.*`, Philips `org.droidtv.*`/`com.tpvision.*`, Sony `com.sony.*`,
     * Xiaomi `com.mitv.*`/`com.xiaomi.*`, Hisense `com.hisense.*`/`com.jamdeo.*`, Skyworth/Coocaa, Haier,
     * Vestel/Beko, CVTE, Zeasn, SDMC, Realtek, Amlogic, MediaTek) plus hardware/board/brand metadata.
     */
    public static List<ModelMatch> findPackageSimilarityMatches(Context c, Set<String> installedPkgs) {
        // 1. Identify all OEM/vendor-specific packages installed on this TV and detect the TV's OEM families
        Set<String> tvOemFamilies = new HashSet<>();
        Set<String> tvOemPackages = new HashSet<>();
        for (String pkg : installedPkgs) {
            if (!isGenericOrStreamingPackage(pkg)) {
                tvOemPackages.add(pkg);
                String fam = detectOemFamilyFromPackage(pkg);
                if (!fam.isEmpty()) tvOemFamilies.add(fam);
            }
        }
        String hwText = ((Build.BRAND != null ? Build.BRAND : "") + " "
                + (Build.MANUFACTURER != null ? Build.MANUFACTURER : "") + " "
                + (Build.MODEL != null ? Build.MODEL : "") + " "
                + (Build.DEVICE != null ? Build.DEVICE : "") + " "
                + (Build.PRODUCT != null ? Build.PRODUCT : "")).toLowerCase(Locale.US);
        if (hwText.contains("tcl") || hwText.contains("ffalcon") || hwText.contains("thomson")) tvOemFamilies.add("tcl");
        if (hwText.contains("philips") || hwText.contains("tpv") || hwText.contains("droidtv")) tvOemFamilies.add("philips");
        if (hwText.contains("sony") || hwText.contains("bravia")) tvOemFamilies.add("sony");
        if (hwText.contains("xiaomi") || hwText.contains("mitv") || hwText.contains("mibox") || hwText.contains("redmi")) tvOemFamilies.add("xiaomi");
        if (hwText.contains("hisense") || hwText.contains("toshiba") || hwText.contains("jamdeo")) tvOemFamilies.add("hisense");
        if (hwText.contains("skyworth") || hwText.contains("coocaa") || hwText.contains("strong") || hwText.contains("metz")) tvOemFamilies.add("skyworth");
        if (hwText.contains("haier") || hwText.contains("homwee")) tvOemFamilies.add("haier");
        if (hwText.contains("changhong") || hwText.contains("chiq")) tvOemFamilies.add("changhong");
        if (hwText.contains("vestel") || hwText.contains("beko") || hwText.contains("grundig") || hwText.contains("arcelik")) tvOemFamilies.add("vestel");

        String deviceLow = Build.DEVICE != null ? Build.DEVICE.trim().toLowerCase(Locale.US) : "";
        String productLow = Build.PRODUCT != null ? Build.PRODUCT.trim().toLowerCase(Locale.US) : "";
        String modelLow = Build.MODEL != null ? Build.MODEL.trim().toLowerCase(Locale.US) : "";

        List<ModelMatch> matches = new ArrayList<>();
        for (ModelEntry entry : loadIndex(c)) {
            if (entry.packages.isEmpty()) continue;
            int totalMatched = 0;
            int oemMatched = 0;
            int modelOemCount = 0;
            int foreignOemCount = 0;
            List<String> sampleOem = new ArrayList<>();

            for (String pkg : entry.packages) {
                boolean isGeneric = isGenericOrStreamingPackage(pkg);
                if (!isGeneric) {
                    modelOemCount++;
                }
                if (installedPkgs.contains(pkg)) {
                    totalMatched++;
                    if (!isGeneric) {
                        oemMatched++;
                        if (sampleOem.size() < 3) {
                            sampleOem.add(shortPkgName(pkg));
                        }
                    }
                } else if (!isGeneric) {
                    foreignOemCount++;
                }
            }
            if (totalMatched == 0) continue;

            Set<String> modelFamilies = detectOemFamiliesForModel(entry);
            boolean familyMatched = false;
            String matchedFamilyName = "";
            for (String f : tvOemFamilies) {
                if (modelFamilies.contains(f)) {
                    familyMatched = true;
                    matchedFamilyName = f.toUpperCase(Locale.US);
                    break;
                }
            }

            // If this TV has known OEM families (e.g. TCL, Philips, Sony, Xiaomi) and this model belongs to a
            // completely different OEM brand with zero OEM package overlap, skip or heavily penalize it!
            boolean conflictingOem = !tvOemFamilies.isEmpty() && !modelFamilies.isEmpty() && !familyMatched && oemMatched == 0;
            if (conflictingOem) continue;

            int oemCoveragePct = modelOemCount > 0 ? (oemMatched * 100) / modelOemCount : 0;
            int totalCoveragePct = (totalMatched * 100) / Math.max(1, entry.packages.size());

            // Score heavily by true OEM package overlap and OEM family match, not generic Google/Android count
            int score = (oemMatched * 85)
                    + (oemCoveragePct * 2)
                    + (familyMatched ? 140 : 0)
                    + (totalCoveragePct / 2)
                    + Math.min(totalMatched, 12) * 2
                    - (foreignOemCount * 12);

            String metaLow = (entry.slug + " " + entry.title + " " + entry.headerMeta).toLowerCase(Locale.US);
            if (!modelLow.isEmpty() && modelLow.length() >= 3 && metaLow.contains(modelLow)) {
                score += 180;
            }
            if (!deviceLow.isEmpty() && deviceLow.length() >= 3 && metaLow.contains(deviceLow)) {
                score += 110;
            }
            if (!productLow.isEmpty() && productLow.length() >= 3 && metaLow.contains(productLow)) {
                score += 90;
            }

            String oemDetail;
            if (oemMatched > 0) {
                oemDetail = I18n.resourceString(c, R.string.cyt_oem_package_match,
                        oemMatched, modelOemCount, TextUtils.join(", ", sampleOem));
            } else if (familyMatched) {
                oemDetail = I18n.resourceString(c, R.string.message_oem_family_1_s, matchedFamilyName);
            } else {
                oemDetail = I18n.resourceString(c, R.string.text_generic_android_tv_google_tv_profile);
            }

            String reason = packageMatchReason(c, totalMatched, entry.packages.size(), oemDetail);
            matches.add(new ModelMatch(entry, score, reason));
        }
        Collections.sort(matches, (a, b) -> Integer.compare(b.score, a.score));
        if (matches.size() > 25) {
            return new ArrayList<>(matches.subList(0, 25));
        }
        return matches;
    }

    private static String shortPkgName(String pkg) {
        String[] parts = pkg.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return pkg;
    }

    /**
     * Fetches the full installed package set from PackageManager (instant with QUERY_ALL_PACKAGES)
     * plus a single combined ADB `pm list packages` query.
     */
    public static Set<String> fetchInstalledPackages(Context c, Set<String> outEnabled, Set<String> outDisabled) {
        Set<String> all = new HashSet<>();
        boolean gotAdbLists = false;
        try {
            String combinedOut = LocalAdb.execShellOutput(c,
                    "pm list packages -e --user 0; echo '__CYT_DISABLED__'; pm list packages -d --user 0");
            if (combinedOut != null && !combinedOut.isEmpty()) {
                boolean inDisabledSection = false;
                for (String line : combinedOut.split("\\r?\\n")) {
                    String s = line.trim();
                    if ("__CYT_DISABLED__".equals(s)) {
                        inDisabledSection = true;
                        continue;
                    }
                    if (s.startsWith("package:")) {
                        String pkg = s.substring("package:".length()).trim();
                        if (!pkg.isEmpty()) {
                            gotAdbLists = true;
                            all.add(pkg);
                            if (inDisabledSection) {
                                if (outDisabled != null) outDisabled.add(pkg);
                                if (outEnabled != null) outEnabled.remove(pkg);
                            } else {
                                if (outEnabled != null) outEnabled.add(pkg);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Also merge PackageManager view (including disabled/uninstalled-for-user packages)
        try {
            PackageManager pm = c.getPackageManager();
            int flags = PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS;
            List<PackageInfo> pis = pm.getInstalledPackages(flags);
            if (pis != null) {
                for (PackageInfo pi : pis) {
                    if (pi == null || pi.packageName == null) continue;
                    String pkg = pi.packageName;
                    all.add(pkg);
                    if (!gotAdbLists && pi.applicationInfo != null) {
                        boolean enabled = isPackageCurrentlyEnabled(pm, pkg, pi.applicationInfo);
                        if (enabled) {
                            if (outEnabled != null) outEnabled.add(pkg);
                        } else {
                            if (outDisabled != null) outDisabled.add(pkg);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return all;
    }

    public static boolean isPackageCurrentlyEnabled(PackageManager pm, String pkg, ApplicationInfo ai) {
        try {
            int state = pm.getApplicationEnabledSetting(pkg);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return false;
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return ai == null || ai.enabled;
    }

    // ================================================================ UI Flows: Choose TV Model

    public static void showChooseModelFlow(Activity activity, Runnable onChanged) {
        showChooseModelFlow(activity, onChanged, false);
    }

    public static void showChooseModelFlow(Activity activity, Runnable onChanged, boolean autoContinueToDebloat) {
        HardwareMatchResult hw = findHardwareMatches(activity);

        // If the user has not chosen a model yet and there is an exact single TV model match, select it directly
        if (getSelectedModelSlug(activity).isEmpty() && hw.exactSingleMatch != null) {
            setSelectedModel(activity, hw.exactSingleMatch.entry);
            if (onChanged != null) onChanged.run();
            if (autoContinueToDebloat) {
                startDebloatFlow(activity, onChanged);
            } else {
                dialog(activity, I18n.resourceString(activity, R.string.text_tv_model_matched))
                        .setMessage(I18n.resourceString(activity, R.string.message_exact_tv_model_found_and_selected_automatically, hw.exactSingleMatch.entry.title, hw.exactSingleMatch.entry.slug, hw.exactSingleMatch.reason))
                        .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                        .setNeutralButton(I18n.resourceString(activity, R.string.text_change_model),
                                (d, w) -> showCandidateMatchesDialog(activity, hw, onChanged, false))
                        .show();
            }
            return;
        }

        // If no hardware/brand matches exist at all, automatically scan via ADB package similarity!
        if (hw.candidates.isEmpty()) {
            runAdbPackageSimilarityScan(activity, hw.deviceDescription, onChanged, autoContinueToDebloat);
            return;
        }

        // Multiple similar model / brand options exist -> open popup asking user which is closer to their TV model
        showCandidateMatchesDialog(activity, hw, onChanged, autoContinueToDebloat);
    }

    private static final class SpacedRowItem {
        final String title;
        final String subtitle;
        final boolean selected;

        SpacedRowItem(String title, String subtitle, boolean selected) {
            this.title = title;
            this.subtitle = subtitle;
            this.selected = selected;
        }
    }

    private static int dp(Context c, float dpVal) {
        return Math.round(dpVal * c.getResources().getDisplayMetrics().density);
    }

    private static ArrayAdapter<SpacedRowItem> createSpacedAdapter(Activity activity, List<SpacedRowItem> rows) {
        return new ArrayAdapter<SpacedRowItem>(activity, 0, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout box;
                TextView tvTitle;
                TextView tvSub;
                if (convertView instanceof LinearLayout && convertView.getTag() instanceof TextView[]) {
                    box = (LinearLayout) convertView;
                    TextView[] refs = (TextView[]) box.getTag();
                    tvTitle = refs[0];
                    tvSub = refs[1];
                } else {
                    box = new LinearLayout(activity);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setGravity(Gravity.CENTER_VERTICAL);
                    int padH = dp(activity, 16);
                    int padV = dp(activity, 11);
                    box.setPaddingRelative(padH, padV, padH, padV);

                    GradientDrawable cardBg = new GradientDrawable();
                    cardBg.setCornerRadius(dp(activity, 10));
                    cardBg.setColor(0x16FFFFFF);
                    box.setBackground(cardBg);

                    tvTitle = new TextView(activity);
                    tvTitle.setTypeface(Ui.font(activity, "semibold"));
                    tvTitle.setTextSize(15.5f);
                    tvTitle.setTextColor(0xFFF4F4F6);
                    tvTitle.setSingleLine(true);
                    tvTitle.setEllipsize(TextUtils.TruncateAt.END);
                    box.addView(tvTitle, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    tvSub = new TextView(activity);
                    tvSub.setTypeface(Ui.font(activity, "regular"));
                    tvSub.setTextSize(12.5f);
                    tvSub.setTextColor(0xAAFFFFFF);
                    tvSub.setMaxLines(2);
                    tvSub.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    slp.topMargin = dp(activity, 4);
                    box.addView(tvSub, slp);

                    box.setTag(new TextView[]{tvTitle, tvSub});
                }

                SpacedRowItem item = getItem(position);
                if (item != null) {
                    tvTitle.setText((item.selected ? "✓  " : "") + item.title);
                    tvTitle.setTextColor(item.selected ? 0xFF6AE08B : 0xFFF4F4F6);
                    if (item.subtitle != null && !item.subtitle.isEmpty()) {
                        tvSub.setVisibility(View.VISIBLE);
                        tvSub.setText(item.subtitle);
                    } else {
                        tvSub.setVisibility(View.GONE);
                    }
                }
                return box;
            }
        };
    }

    private static void styleDialogButtons(Activity activity, AlertDialog dlg) {
        if (dlg == null) return;
        int[] btnIds = {
            DialogInterface.BUTTON_POSITIVE,
            DialogInterface.BUTTON_NEUTRAL,
            DialogInterface.BUTTON_NEGATIVE
        };
        int marginH = dp(activity, 6);
        int marginV = dp(activity, 5);
        int padH = dp(activity, 14);
        int padV = dp(activity, 8);
        ViewGroup parentBar = null;
        for (int id : btnIds) {
            Button b = dlg.getButton(id);
            if (b == null) continue;
            b.setPaddingRelative(padH, padV, padH, padV);
            ViewGroup.LayoutParams lp = b.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                mlp.setMargins(marginH, marginV, marginH, marginV);
                b.setLayoutParams(mlp);
            }
            if (b.getParent() instanceof ViewGroup) {
                parentBar = (ViewGroup) b.getParent();
            }
        }
        if (parentBar != null) {
            parentBar.setPaddingRelative(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            parentBar.setClipToPadding(false);
            parentBar.setClipChildren(false);
        }
    }

    private static void applyDialogListSpacing(Activity activity, AlertDialog dlg) {
        if (dlg == null) return;
        ListView lv = dlg.getListView();
        if (lv != null) {
            lv.setDivider(new ColorDrawable(Color.TRANSPARENT));
            lv.setDividerHeight(dp(activity, 9));
            int padH = dp(activity, 14);
            int padV = dp(activity, 8);
            lv.setPaddingRelative(padH, padV, padH, padV);
            lv.setClipToPadding(false);
        }
        styleDialogButtons(activity, dlg);
    }

    private static void showCandidateMatchesDialog(Activity activity, HardwareMatchResult hw,
                                                   Runnable onChanged, boolean autoContinueToDebloat) {
        List<ModelMatch> candidates = hw.candidates;
        String currentSlug = getSelectedModelSlug(activity);

        List<SpacedRowItem> rows = new ArrayList<>();
        for (ModelMatch m : candidates) {
            boolean isCurrent = m.entry.slug.equalsIgnoreCase(currentSlug);
            String sub = "[" + m.entry.slug + "]  •  " + m.reason;
            rows.add(new SpacedRowItem(m.entry.title, sub, isCurrent));
        }
        final int idxScanApps = rows.size();
        rows.add(new SpacedRowItem(
                I18n.resourceString(activity, R.string.text_scan_installed_system_apps_via_adb),
                I18n.resourceString(activity, R.string.text_pull_full_app_list_from_adb_and_match_mo_17de20),
                false));
        final int idxBrowseAll = rows.size();
        rows.add(new SpacedRowItem(
                I18n.resourceString(activity, R.string.text_browse_all_341_tv_models),
                I18n.resourceString(activity, R.string.text_view_or_filter_the_complete_list_of_supp),
                false));

        String title = I18n.resourceString(activity, R.string.message_choose_closest_tv_model_1_s, hw.deviceDescription);

        ArrayAdapter<SpacedRowItem> adapter = createSpacedAdapter(activity, rows);
        AlertDialog dlg = dialog(activity, title)
                .setAdapter(adapter, (d, which) -> {
                    if (which == idxScanApps) {
                        runAdbPackageSimilarityScan(activity, hw.deviceDescription, onChanged, autoContinueToDebloat);
                    } else if (which == idxBrowseAll) {
                        showBrowseAllModelsDialog(activity, "", onChanged, autoContinueToDebloat);
                    } else if (which >= 0 && which < candidates.size()) {
                        ModelEntry picked = candidates.get(which).entry;
                        setSelectedModel(activity, picked);
                        if (onChanged != null) onChanged.run();
                        if (autoContinueToDebloat) {
                            startDebloatFlow(activity, onChanged);
                        }
                    }
                })
                .setPositiveButton(I18n.resourceString(activity, R.string.text_search_def46e), (d, w) -> {
                    promptFilterQuery(activity, "", onChanged, autoContinueToDebloat);
                })
                .setNeutralButton(I18n.resourceString(activity, R.string.text_scan_apps_adb), (d, w) -> {
                    runAdbPackageSimilarityScan(activity, hw.deviceDescription, onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);
    }

    private static void runAdbPackageSimilarityScan(Activity activity, String deviceDesc,
                                                    Runnable onChanged, boolean autoContinueToDebloat) {
        AlertDialog progress = dialog(activity, I18n.resourceString(activity, R.string.text_scanning_tv_system_apps))
                .setMessage(I18n.resourceString(activity, R.string.text_pulling_full_package_list_from_adb_and_c))
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            Set<String> installed = fetchInstalledPackages(activity, null, null);
            List<ModelMatch> pkgMatches = findPackageSimilarityMatches(activity, installed);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                showPackageSimilarityResultsDialog(activity, deviceDesc, installed.size(), pkgMatches, onChanged, autoContinueToDebloat);
            });
        }, "cleanyourtv-pkg-scan").start();
    }

    private static void showPackageSimilarityResultsDialog(Activity activity, String deviceDesc, int totalInstalled,
                                                           List<ModelMatch> pkgMatches, Runnable onChanged,
                                                           boolean autoContinueToDebloat) {
        String currentSlug = getSelectedModelSlug(activity);
        List<SpacedRowItem> rows = new ArrayList<>();
        for (ModelMatch m : pkgMatches) {
            boolean isCurrent = m.entry.slug.equalsIgnoreCase(currentSlug);
            String sub = "[" + m.entry.slug + "]  •  " + m.reason;
            rows.add(new SpacedRowItem(m.entry.title, sub, isCurrent));
        }
        final int idxBrowseAll = rows.size();
        rows.add(new SpacedRowItem(
                I18n.resourceString(activity, R.string.text_browse_all_341_tv_models),
                I18n.resourceString(activity, R.string.text_view_or_filter_the_complete_list_of_supp),
                false));

        String title = I18n.resourceString(activity, R.string.cyt_similar_apps_scanned, totalInstalled);

        ArrayAdapter<SpacedRowItem> adapter = createSpacedAdapter(activity, rows);
        AlertDialog dlg = dialog(activity, title)
                .setAdapter(adapter, (d, which) -> {
                    if (which == idxBrowseAll) {
                        showBrowseAllModelsDialog(activity, "", onChanged, autoContinueToDebloat);
                    } else if (which >= 0 && which < pkgMatches.size()) {
                        ModelEntry picked = pkgMatches.get(which).entry;
                        setSelectedModel(activity, picked);
                        if (onChanged != null) onChanged.run();
                        if (autoContinueToDebloat) {
                            startDebloatFlow(activity, onChanged);
                        }
                    }
                })
                .setPositiveButton(I18n.resourceString(activity, R.string.text_search_def46e), (d, w) -> {
                    promptFilterQuery(activity, "", onChanged, autoContinueToDebloat);
                })
                .setNeutralButton(I18n.resourceString(activity, R.string.text_all_341_models), (d, w) -> {
                    showBrowseAllModelsDialog(activity, "", onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);
    }

    private static void showBrowseAllModelsDialog(Activity activity, String initialQuery,
                                                  Runnable onChanged, boolean autoContinueToDebloat) {
        List<ModelEntry> all = loadIndex(activity);
        String currentSlug = getSelectedModelSlug(activity);

        String q = initialQuery != null ? initialQuery.trim().toLowerCase(Locale.US) : "";
        List<ModelEntry> filtered = new ArrayList<>();
        for (ModelEntry m : all) {
            if (q.isEmpty()
                    || m.slug.toLowerCase(Locale.US).contains(q)
                    || m.title.toLowerCase(Locale.US).contains(q)
                    || m.headerMeta.toLowerCase(Locale.US).contains(q)) {
                filtered.add(m);
            }
        }

        List<SpacedRowItem> rows = new ArrayList<>(filtered.size());
        for (int i = 0; i < filtered.size(); i++) {
            ModelEntry m = filtered.get(i);
            boolean isCurrent = m.slug.equalsIgnoreCase(currentSlug);
            String sub = "[" + m.slug + "]  •  " + m.packages.size() + " " + I18n.resourceString(activity, R.string.text_packages);
            rows.add(new SpacedRowItem(m.title, sub, isCurrent));
        }

        String title = q.isEmpty()
                ? I18n.resourceString(activity, R.string.cyt_all_tv_models, filtered.size())
                : I18n.resourceString(activity, R.string.cyt_model_search, initialQuery, filtered.size());

        ArrayAdapter<SpacedRowItem> adapter = createSpacedAdapter(activity, rows);
        AlertDialog dlg = dialog(activity, title)
                .setAdapter(adapter, (d, which) -> {
                    if (which >= 0 && which < filtered.size()) {
                        ModelEntry picked = filtered.get(which);
                        setSelectedModel(activity, picked);
                        if (onChanged != null) onChanged.run();
                        if (autoContinueToDebloat) {
                            startDebloatFlow(activity, onChanged);
                        }
                    }
                })
                .setPositiveButton(I18n.resourceString(activity, R.string.text_search_def46e), (d, w) -> {
                    promptFilterQuery(activity, initialQuery, onChanged, autoContinueToDebloat);
                })
                .setNeutralButton(I18n.resourceString(activity, R.string.text_scan_apps_adb), (d, w) -> {
                    runAdbPackageSimilarityScan(activity, Build.MODEL, onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);
    }

    private static void promptFilterQuery(Activity activity, String currentQuery,
                                          Runnable onChanged, boolean autoContinueToDebloat) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(I18n.resourceString(activity, R.string.text_e_g_tcl_sony_hisense_xiaomi_philips_skyw));
        if (currentQuery != null && !currentQuery.isEmpty()) {
            input.setText(currentQuery);
            input.setSelection(currentQuery.length());
        }
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(activity);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog dlg = dialog(activity, I18n.resourceString(activity, R.string.text_search_tv_model_or_brand))
                .setView(box)
                .setPositiveButton(I18n.resourceString(activity, R.string.text_search_946f53), (d, w) -> {
                    String nextQ = input.getText().toString().trim();
                    showBrowseAllModelsDialog(activity, nextQ, onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .create();
        dlg.show();
        Ui.setupDialogEditText(input, dlg);
        styleDialogButtons(activity, dlg);
        input.requestFocus();
    }

    // ================================================================ Parsing debloat.txt & Building Interactive Checklist

    /**
     * Parses `assets/cleanyourtv/examples/<slug>/debloat.txt` and builds the interactive
     * checklist of packages present on this TV, including both default bloat/telemetry packages
     * and optional user-choice packages (Assistant, YouTube Music, Play Games, TalkBack, Chromecast, Stock Launcher, etc.).
     */
    private static File resolveDebloatFileForModel(Context c, String slug) {
        File baseDir = getCleanYourTvDir(c);
        File rootDeb = new File(baseDir, "debloat.txt");
        if (rootDeb.isFile()) return rootDeb;
        if (slug != null && !slug.isEmpty()) {
            File subDeb = new File(new File(baseDir, slug), "debloat.txt");
            if (subDeb.isFile()) return subDeb;
            File ex = findExamplesDir(baseDir);
            if (ex != null) {
                File exDeb = new File(new File(ex, slug), "debloat.txt");
                if (exDeb.isFile()) return exDeb;
            }
        }
        return null;
    }

    public static List<DebloatItem> buildChecklistForModel(Context c, String slug,
                                                           Set<String> installedPkgs,
                                                           boolean skipSetupwraithAndLauncher) {
        Map<String, String> activePkgComments = new LinkedHashMap<>();
        Map<String, String> optionalPkgComments = new LinkedHashMap<>();

        File debloatFile = resolveDebloatFileForModel(c, slug);
        String currentSection = "";
        if (debloatFile != null && debloatFile.isFile()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(debloatFile), StandardCharsets.UTF_8))) {
                String raw;
                while ((raw = br.readLine()) != null) {
                    String line = raw.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith("#")) {
                        String afterHash = line.substring(1).trim();
                        String candidatePkg = afterHash;
                        String inlineComment = "";
                        int hashIdx = afterHash.indexOf('#');
                        if (hashIdx >= 0) {
                            candidatePkg = afterHash.substring(0, hashIdx).trim();
                            inlineComment = afterHash.substring(hashIdx + 1).trim();
                        }
                        if (looksLikePackageName(candidatePkg)) {
                            String desc = !inlineComment.isEmpty() ? inlineComment : currentSection;
                            optionalPkgComments.put(candidatePkg, desc);
                        } else if (!afterHash.startsWith("Disabled ")
                                && !afterHash.startsWith("Already ")
                                && !afterHash.startsWith("System ")
                                && !afterHash.startsWith("Android ")
                                && !afterHash.startsWith("Restore:")
                                && !afterHash.startsWith("while ")
                                && !afterHash.contains("pm enable")) {
                            currentSection = afterHash;
                        }
                        continue;
                    }

                    String pkg = line;
                    String inlineComment = "";
                    int hashIdx = line.indexOf('#');
                    if (hashIdx >= 0) {
                        pkg = line.substring(0, hashIdx).trim();
                        inlineComment = line.substring(hashIdx + 1).trim();
                    }
                    if (looksLikePackageName(pkg)) {
                        String desc = !inlineComment.isEmpty() ? inlineComment : currentSection;
                        activePkgComments.put(pkg, desc);
                    }
                }
            } catch (Throwable ignored) {
            }
        } else {
            ModelEntry model = findModelBySlug(c, slug);
            if (model != null && model.packages != null) {
                for (String pkg : model.packages) {
                    activePkgComments.put(pkg, "");
                }
            }
        }

        // Also add standard CleanYourTV prompt preference-dependent optional packages if installed on this TV
        Map<String, String> promptChoiceDefaults = new LinkedHashMap<>();
        promptChoiceDefaults.put("com.google.android.katniss",
                I18n.resourceString(c, R.string.text_google_assistant_voice_search_optional));
        promptChoiceDefaults.put("com.google.android.youtube.tvmusic",
                I18n.resourceString(c, R.string.text_youtube_music_optional));
        promptChoiceDefaults.put("com.google.android.play.games",
                I18n.resourceString(c, R.string.text_google_play_games_optional));
        promptChoiceDefaults.put("com.google.android.marvin.talkback",
                I18n.resourceString(c, R.string.text_android_accessibility_suite_talkback_opt));
        promptChoiceDefaults.put("com.google.android.apps.mediashell",
                I18n.resourceString(c, R.string.text_chromecast_built_in_google_cast_receiver));
        promptChoiceDefaults.put("com.google.android.tv.remote.service",
                I18n.resourceString(c, R.string.text_android_tv_remote_service_optional_keep));

        for (Map.Entry<String, String> e : promptChoiceDefaults.entrySet()) {
            if (!activePkgComments.containsKey(e.getKey()) && !optionalPkgComments.containsKey(e.getKey())) {
                optionalPkgComments.put(e.getKey(), e.getValue());
            }
        }

        PackageManager pm = c.getPackageManager();
        List<DebloatItem> primaryItems = new ArrayList<>();
        List<DebloatItem> optionalItems = new ArrayList<>();

        // 1. Add active packages from the selected model's debloat.txt that exist on this TV.
        //    Optional choice apps are never auto-selected and are placed at the bottom of the list.
        for (Map.Entry<String, String> e : activePkgComments.entrySet()) {
            String pkg = e.getKey();
            if (!installedPkgs.contains(pkg)) continue;
            if (!isSafeToDisable(c, pkg)) continue;

            boolean isSetupOrLauncher = "com.google.android.tungsten.setupwraith".equals(pkg)
                    || "com.google.android.apps.tv.launcherx".equals(pkg)
                    || "com.google.android.leanbacklauncher".equals(pkg);
            boolean skippedByWifiGuard = skipSetupwraithAndLauncher && isSetupOrLauncher;
            boolean isOptional = isOptionalChoicePackage(pkg, e.getValue());

            String label = resolveAppLabel(pm, pkg);
            String desc = e.getValue();
            if (desc == null || desc.isEmpty()) {
                desc = defaultDescriptionForPackage(c, pkg);
            }
            if (isOptional || skippedByWifiGuard) {
                optionalItems.add(new DebloatItem(pkg, label, desc, true, false));
            } else {
                primaryItems.add(new DebloatItem(pkg, label, desc, false, true));
            }
        }

        // 2. Add optional commented-out / prompt-choice packages that exist on this TV
        //    (unchecked by default and placed at the bottom of the list)
        for (Map.Entry<String, String> e : optionalPkgComments.entrySet()) {
            String pkg = e.getKey();
            if (activePkgComments.containsKey(pkg)) continue;
            if (!installedPkgs.contains(pkg)) continue;
            if (!isSafeToDisable(c, pkg)) continue;

            String label = resolveAppLabel(pm, pkg);
            String desc = e.getValue();
            if (desc == null || desc.isEmpty()) {
                desc = defaultDescriptionForPackage(c, pkg);
            }
            optionalItems.add(new DebloatItem(pkg, label, desc, true, false));
        }

        List<DebloatItem> items = new ArrayList<>(primaryItems.size() + optionalItems.size());
        items.addAll(primaryItems);
        items.addAll(optionalItems);
        return items;
    }

    private static boolean looksLikePackageName(String s) {
        if (s == null || s.length() < 5 || s.contains(" ") || !s.contains(".")) return false;
        return s.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$");
    }

    private static boolean isOptionalChoicePackage(String pkg, String comment) {
        if (comment != null) {
            String low = comment.toLowerCase(Locale.US);
            if (low.contains("choice") || low.contains("optional")) return true;
        }
        return "com.google.android.katniss".equals(pkg)
                || "com.google.android.youtube.tvmusic".equals(pkg)
                || "com.google.android.youtube.tv".equals(pkg)
                || "com.google.android.play.games".equals(pkg)
                || "com.google.android.marvin.talkback".equals(pkg)
                || "com.google.android.apps.mediashell".equals(pkg)
                || "com.google.android.tv.remote.service".equals(pkg);
    }

    private static String resolveAppLabel(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        String cached = APP_LABEL_CACHE.get(pkg);
        if (cached != null) return cached;
        String resolved = null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (ai != null) {
                CharSequence cs = ai.loadLabel(pm);
                if (cs != null && cs.length() > 0) {
                    String s = cs.toString().trim();
                    if (!s.isEmpty() && !s.equals(pkg)) {
                        resolved = s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (resolved == null || resolved.isEmpty()) {
            resolved = friendlyFallbackName(pkg);
        }
        APP_LABEL_CACHE.put(pkg, resolved);
        return resolved;
    }

    public static String resolvePackageLabel(Context c, String pkg) {
        return resolveAppLabel(c.getPackageManager(), pkg);
    }

    private static final class SystemLauncher {
        final String packageName;
        final String fallbackTitle;
        final int descriptionResource;

        SystemLauncher(String packageName, String fallbackTitle, int descriptionResource) {
            this.packageName = packageName;
            this.fallbackTitle = fallbackTitle;
            this.descriptionResource = descriptionResource;
        }
    }

    private static final SystemLauncher[] KNOWN_SYSTEM_LAUNCHERS = {
        new SystemLauncher("com.google.android.apps.tv.launcherx", "Google TV Home (LauncherX)", R.string.launcher_desc_stock_google_tv_home_screen_banner_ads),
        new SystemLauncher("com.google.android.tungsten.setupwraith", "Google TV Home Watchdog (setupwraith)", R.string.launcher_desc_google_tv_launcher_watchdog_disable_together_with_launcherx),
        new SystemLauncher("com.google.android.tvlauncher", "Android TV Home (tvlauncher)", R.string.launcher_desc_stock_android_tv_home_screen),
        new SystemLauncher("com.google.android.leanbacklauncher", "Android TV Home (Leanback)", R.string.launcher_desc_stock_android_tv_leanback_launcher),
        new SystemLauncher("org.droidtv.ui.home", "Philips TV Home UI (org.droidtv.ui.home)", R.string.launcher_desc_philips_oem_home_launcher),
        new SystemLauncher("org.droidtv.home", "Philips Home (org.droidtv.home)", R.string.launcher_desc_philips_oem_home_screen),
        new SystemLauncher("com.tpvision.home", "Philips / TPVision Home (com.tpvision.home)", R.string.launcher_desc_philips_oem_home_screen),
        new SystemLauncher("com.tcl.cyberui", "TCL CyberUI Home (com.tcl.cyberui)", R.string.launcher_desc_tcl_oem_home_screen),
        new SystemLauncher("com.tcl.tv.launcher", "TCL TV Launcher (com.tcl.tv.launcher)", R.string.launcher_desc_tcl_oem_tv_home_launcher),
        new SystemLauncher("com.tcl.launcher", "TCL Home Launcher (com.tcl.launcher)", R.string.launcher_desc_tcl_oem_home_launcher),
        new SystemLauncher("com.falcon.launcher", "TCL / Falcon Launcher", R.string.launcher_desc_tcl_iffalcon_oem_home_launcher),
        new SystemLauncher("com.ffalcon.launcher", "FFALCON Home Launcher", R.string.launcher_desc_ffalcon_oem_home_launcher),
        new SystemLauncher("com.hisense.tv.launcher", "Hisense TV Launcher", R.string.launcher_desc_hisense_oem_tv_home_screen),
        new SystemLauncher("com.mi.tv.launcher", "Xiaomi PatchWall / Mi TV Launcher", R.string.launcher_desc_xiaomi_oem_tv_home_screen),
        new SystemLauncher("com.mitv.tvhome", "Mi TV Home", R.string.launcher_desc_xiaomi_oem_tv_home_screen),
        new SystemLauncher("com.sony.dtv.homemenu", "Sony Bravia Home Menu", R.string.launcher_desc_sony_oem_tv_home_menu),
        new SystemLauncher("com.skyworth.tvos.launcher", "Skyworth TV Launcher", R.string.launcher_desc_skyworth_oem_tv_home_screen),
        new SystemLauncher("com.coocaa.tv.launcher", "Coocaa TV Launcher", R.string.launcher_desc_coocaa_oem_tv_home_screen),
        new SystemLauncher("com.haier.tv.launcher", "Haier TV Launcher", R.string.launcher_desc_haier_oem_tv_home_screen),
        new SystemLauncher("com.homwee.launcher", "Haier / Homwee TV Launcher", R.string.launcher_desc_haier_oem_tv_home_screen),
        new SystemLauncher("com.zeasn.whaleos.launcher", "WhaleOS / Zeasn TV Launcher", R.string.launcher_desc_zeasn_oem_tv_home_screen),
        new SystemLauncher("com.sdmc.launcher", "SDMC TV Launcher", R.string.launcher_desc_sdmc_oem_tv_home_screen),
        new SystemLauncher("com.droidlogic.mboxlauncher", "Amlogic / Droidlogic MBox Launcher", R.string.launcher_desc_amlogic_oem_tv_box_home_caution_disabling_may_bootloop_some),
        new SystemLauncher("com.amazon.tv.launcher", "Fire TV Home Launcher", R.string.launcher_desc_amazon_oem_tv_home_screen),
        new SystemLauncher("com.amazon.firehomestarter", "Fire TV Home Starter", R.string.launcher_desc_amazon_fire_os_home_button_interceptor),
    };

    /**
     * Core system/framework/settings/live-tv tuner packages that may declare a FallbackHome,
     * ResolverActivity, or TV input entry for CATEGORY_HOME, but are NOT actual home launchers
     * and must NEVER be shown in the launcher list.
     */
    private static boolean isExcludedFromLauncherList(String pkg, String activityName) {
        if (pkg == null || pkg.isEmpty()) return true;
        String lowPkg = pkg.toLowerCase(Locale.US);
        if ("android".equals(lowPkg)
                || lowPkg.startsWith("com.android.internal")
                || lowPkg.equals("com.android.settings")
                || lowPkg.equals("com.android.tv.settings")
                || lowPkg.equals("com.android.systemui")
                || lowPkg.equals("com.android.provision")
                || lowPkg.equals("com.android.managedprovisioning")
                || lowPkg.equals("com.android.shell")
                || lowPkg.equals("com.android.nfc")
                || lowPkg.equals("com.android.bluetooth")
                || lowPkg.equals("com.android.vending")
                || lowPkg.equals("com.android.tv")
                || lowPkg.equals("com.google.android.tv")
                || lowPkg.equals("com.google.android.gms")
                || lowPkg.equals("com.google.android.gsf")
                || lowPkg.equals("com.google.android.onetimeinitializer")
                || lowPkg.equals("com.google.android.leanbacklauncher.recommendations")
                || lowPkg.equals("com.google.android.tvrecommendations")
                || lowPkg.equals("org.droidtv.playtv")
                || lowPkg.equals("org.droidtv.channels")
                || lowPkg.equals("org.droidtv.tunerservice")
                || lowPkg.equals("org.droidtv.settings")
                || lowPkg.equals("com.sony.dtv.tvx")
                || lowPkg.equals("com.tcl.tv")
                || lowPkg.startsWith("com.android.cts.")) {
            return true;
        }
        if (activityName != null) {
            String lowAct = activityName.toLowerCase(Locale.US);
            if (lowAct.contains("resolveractivity")
                    || lowAct.contains("fallbackhome")
                    || lowAct.contains("cryptkeeper")
                    || lowAct.contains("provision")
                    || lowAct.contains("playtv")
                    || lowAct.contains("livetv")
                    || lowAct.contains(" uninstaller")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches installed packages on the current TV to find all stock/OEM system launchers
     * (Google TV Home, Android TV Home, TCL/Philips/Sony/Xiaomi/Hisense launchers, plus any
     * other installed system CATEGORY_HOME package).
     * For each returned DebloatItem, {@code checked} is true when the package is currently ENABLED.
     */
    public static List<DebloatItem> getInstalledSystemLaunchers(Context c, Set<String> installedPkgs, Set<String> enabledPkgs) {
        PackageManager pm = c.getPackageManager();
        Set<String> savedDisabled = new HashSet<>(getSavedDisabledPackages(c));
        Map<String, DebloatItem> found = new LinkedHashMap<>();

        for (SystemLauncher launcher : KNOWN_SYSTEM_LAUNCHERS) {
            String pkg = launcher.packageName;
            String fallbackTitle = launcher.fallbackTitle;
            String desc = I18n.resourceString(c, launcher.descriptionResource);
            boolean isInstalled = (installedPkgs != null && installedPkgs.contains(pkg)) || savedDisabled.contains(pkg);
            Boolean pmEnabled = null;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS);
                if (ai != null) {
                    isInstalled = true;
                    pmEnabled = isPackageCurrentlyEnabled(pm, pkg, ai);
                }
            } catch (Throwable ignored) {
            }
            if (!isInstalled) continue;

            boolean isEnabled;
            if (enabledPkgs != null && !enabledPkgs.isEmpty()) {
                isEnabled = enabledPkgs.contains(pkg);
            } else if (pmEnabled != null) {
                isEnabled = pmEnabled && !savedDisabled.contains(pkg);
            } else {
                isEnabled = !savedDisabled.contains(pkg);
            }

            String label = resolveAppLabel(pm, pkg);
            if (label.equals(pkg) || label.equals(friendlyFallbackName(pkg))) {
                label = fallbackTitle;
            }
            found.put(pkg, new DebloatItem(pkg, label, desc, false, isEnabled));
        }

        // Also scan all installed CATEGORY_HOME system packages dynamically (excluding core Android/Settings/Resolver)
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            int flags = PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES;
            List<android.content.pm.ResolveInfo> ris = pm.queryIntentActivities(homeIntent, flags);
            if (ris != null) {
                for (android.content.pm.ResolveInfo ri : ris) {
                    if (ri == null || ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    String actName = ri.activityInfo.name;
                    if (pkg.equals(c.getPackageName())) continue;
                    if (found.containsKey(pkg)) continue;
                    if (isExcludedFromLauncherList(pkg, actName)) continue;
                    if (!isSafeToDisable(c, pkg)) continue;
                    ApplicationInfo ai = ri.activityInfo.applicationInfo;
                    boolean isSystem = ai != null && ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
                    if (!isSystem) continue;
                    boolean isEnabled;
                    if (enabledPkgs != null && !enabledPkgs.isEmpty()) {
                        isEnabled = enabledPkgs.contains(pkg);
                    } else {
                        isEnabled = isPackageCurrentlyEnabled(pm, pkg, ai) && !savedDisabled.contains(pkg);
                    }
                    String label = resolveAppLabel(pm, pkg);
                    String desc = I18n.resourceString(c, R.string.text_system_home_launcher_detected_on_this_tv);
                    found.put(pkg, new DebloatItem(pkg, label, desc, false, isEnabled));
                }
            }
        } catch (Throwable ignored) {
        }

        return new ArrayList<>(found.values());
    }

    public static String getSystemLaunchersSummary(Context c) {
        List<DebloatItem> items = getInstalledSystemLaunchers(c, null, null);
        if (items.isEmpty()) {
            return I18n.resourceString(c, R.string.text_none_found);
        }
        int active = 0;
        for (DebloatItem it : items) {
            if (it.checked) active++;
        }
        if (active == 0) {
            return I18n.resourceString(c, R.string.text_all_disabled_s, items.size());
        }
        return I18n.resourceString(c, R.string.text_s_active_s_found, active, items.size());
    }

    /**
     * Returns all system apps installed on the TV for the Manual Debloat menu.
     * In the returned list, {@code checked} is true when the package is currently ENABLED.
     */
    public static List<DebloatItem> getAllSystemAppsForManualDebloat(Context c, Set<String> installedPkgs, Set<String> enabledPkgs) {
        PackageManager pm = c.getPackageManager();
        Set<String> savedDisabled = new HashSet<>(getSavedDisabledPackages(c));
        Map<String, DebloatItem> map = new LinkedHashMap<>();

        try {
            int flags = PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS;
            List<PackageInfo> pis = pm.getInstalledPackages(flags);
            if (pis != null) {
                for (PackageInfo pi : pis) {
                    if (pi == null || pi.packageName == null) continue;
                    String pkg = pi.packageName;
                    if (pkg.equals(c.getPackageName()) || "android".equals(pkg)) continue;
                    ApplicationInfo ai = pi.applicationInfo;
                    if (ai == null) continue;
                    boolean isSystem = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                    if (!isSystem) continue;
                    boolean isEnabled = (enabledPkgs != null && !enabledPkgs.isEmpty())
                            ? enabledPkgs.contains(pkg)
                            : (isPackageCurrentlyEnabled(pm, pkg, ai) && !savedDisabled.contains(pkg));
                    String label = resolveAppLabel(pm, pkg);
                    boolean isCritical = isCriticalProtectedService(pkg, label);
                    boolean coreWarning = isCritical || !isSafeToDisable(c, pkg);
                    String desc = isCritical
                            ? I18n.resourceString(c, R.string.text_critical_system_service_protected_cannot)
                            : (coreWarning
                                    ? I18n.resourceString(c, R.string.text_core_system_package_disabling_may_affect)
                                    : defaultDescriptionForPackage(c, pkg));
                    map.put(pkg, new DebloatItem(pkg, label, desc, coreWarning, isEnabled));
                }
            }
        } catch (Throwable ignored) {
        }

        if (installedPkgs != null) {
            for (String pkg : installedPkgs) {
                if (pkg == null || pkg.isEmpty() || pkg.equals(c.getPackageName()) || "android".equals(pkg) || map.containsKey(pkg)) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg,
                            PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS);
                    if (ai != null && (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) {
                        continue;
                    }
                } catch (Throwable ignored) {
                }
                boolean isEnabled = (enabledPkgs != null && !enabledPkgs.isEmpty())
                        ? enabledPkgs.contains(pkg)
                        : !savedDisabled.contains(pkg);
                String label = resolveAppLabel(pm, pkg);
                boolean isCritical = isCriticalProtectedService(pkg, label);
                boolean coreWarning = isCritical || !isSafeToDisable(c, pkg);
                String desc = isCritical
                        ? I18n.resourceString(c, R.string.text_critical_system_service_protected_cannot)
                        : (coreWarning
                                ? I18n.resourceString(c, R.string.text_core_system_package_disabling_may_affect)
                                : defaultDescriptionForPackage(c, pkg));
                map.put(pkg, new DebloatItem(pkg, label, desc, coreWarning, isEnabled));
            }
        }

        List<DebloatItem> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> {
            int cmp = a.appLabel.compareToIgnoreCase(b.appLabel);
            return cmp != 0 ? cmp : a.pkg.compareToIgnoreCase(b.pkg);
        });
        return out;
    }

    /**
     * Enables or disables a single package via ADB (used by Manual Debloat and Disable System Launchers).
     * When {@code allowAnySystemApp} is true (Manual Debloat), allows disabling any package except tvOS Launcher itself.
     */
    public static void toggleSinglePackage(Activity activity, String pkg, boolean disable,
                                           boolean allowAnySystemApp, Runnable onDone) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(activity.getPackageName())) return;
        String resolvedLabel = resolvePackageLabel(activity, pkg);
        if (disable && isCriticalProtectedService(pkg, resolvedLabel)) {
            dialog(activity, I18n.resourceString(activity, R.string.text_critical_system_service))
                    .setMessage(labelAndPackageMessage(activity, R.string.cyt_critical_service_warning,
                            R.string.cyt_critical_service_warning_hebrew_fallback, resolvedLabel, pkg))
                    .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                    .show();
            return;
        }
        if (disable && !allowAnySystemApp && !isSafeToDisable(activity, pkg)) {
            dialog(activity, I18n.resourceString(activity, R.string.text_protected_core_package))
                    .setMessage(I18n.resourceString(activity, R.string.cyt_protected_package, pkg))
                    .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                    .show();
            return;
        }

        AlertDialog progress = dialog(activity, disable
                ? I18n.resourceString(activity, R.string.text_disabling_package)
                : I18n.resourceString(activity, R.string.text_enabling_package))
                .setMessage(pkg)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            List<String> cmds = new ArrayList<>();
            if (disable) {
                cmds.add("cmd role add-role-holder --user 0 android.app.role.HOME com.techgenius.tvoslauncher");
                cmds.add("cmd package set-home-activity --user 0 com.techgenius.tvoslauncher/.MainActivity");
                cmds.add("pm disable-user --user 0 " + pkg);
            } else {
                cmds.add("pm enable --user 0 " + pkg);
            }
            cmds.add("sync");

            boolean connected = LocalAdb.execShellSequential(activity, cmds, null);
            if (connected) {
                if (disable) {
                    String slug = getSelectedModelSlug(activity);
                    saveDisabledPackages(activity, slug.isEmpty() ? "manual" : slug, Collections.singletonList(pkg));
                } else {
                    List<String> remaining = new ArrayList<>(getSavedDisabledPackages(activity));
                    remaining.remove(pkg);
                    prefs(activity).edit().putString(KEY_DISABLED_PKGS, TextUtils.join(",", remaining)).apply();
                }
            }

            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                if (!connected) {
                    dialog(activity, I18n.resourceString(activity, R.string.text_adb_connection_required))
                            .setMessage(I18n.resourceString(activity, R.string.text_could_not_connect_to_local_adb_on_port_5_4d4bb1))
                            .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                            .show();
                }
                if (onDone != null) onDone.run();
            });
        }, "cleanyourtv-single-toggle").start();
    }

    public static void showDisableSystemLaunchersDialog(Activity activity, Runnable onChanged) {
        if (activity instanceof SettingsActivity) {
            ((SettingsActivity) activity).openSystemLaunchersSubMenu();
            return;
        }
        List<DebloatItem> launchers = getInstalledSystemLaunchers(activity, null, null);
        if (launchers.isEmpty()) {
            dialog(activity, I18n.resourceString(activity, R.string.text_system_launchers_d659b5))
                    .setMessage(I18n.resourceString(activity, R.string.text_no_stock_android_tv_google_tv_launcher_p))
                    .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                    .show();
            return;
        }
        String[] labels = new String[launchers.size()];
        for (int i = 0; i < launchers.size(); i++) {
            DebloatItem it = launchers.get(i);
            String state = it.checked ? I18n.resourceString(activity, R.string.text_enabled_tap_to_disable_046b6f)
                                      : I18n.resourceString(activity, R.string.text_disabled_tap_to_enable_ad587c);
            labels[i] = it.appLabel + state + "\n" + it.pkg;
        }
        dialog(activity, I18n.resourceString(activity, R.string.text_disable_system_launchers_072c87))
                .setItems(labels, (d, which) -> {
                    DebloatItem picked = launchers.get(which);
                    toggleSinglePackage(activity, picked.pkg, picked.checked, false, onChanged);
                })
                .setPositiveButton(I18n.resourceString(activity, R.string.text_disable_all_stock_launchers), (d, w) -> {
                    List<String> toDisable = new ArrayList<>();
                    for (DebloatItem it : launchers) {
                        if (it.checked) toDisable.add(it.pkg);
                    }
                    if (!toDisable.isEmpty()) {
                        executeDebloatPackages(activity, I18n.resourceString(activity, R.string.text_system_launchers_83a49b), toDisable, onChanged);
                    }
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_close), null)
                .show();
    }

    private static String friendlyFallbackName(String pkg) {
        switch (pkg) {
            case "com.google.android.apps.tv.launcherx": return "Google TV Home (LauncherX)";
            case "com.google.android.tungsten.setupwraith": return "Google TV Setup (setupwraith)";
            case "com.google.android.leanbacklauncher": return "Android TV Home (Leanback)";
            case "com.google.android.leanbacklauncher.recommendations": return "Android TV Recommendations";
            case "com.google.android.tvrecommendations": return "Android TV Home Recommendations";
            case "com.google.android.katniss": return "Google Assistant";
            case "com.google.android.youtube.tv": return "YouTube for Android TV";
            case "com.google.android.youtube.tvmusic": return "YouTube Music";
            case "com.google.android.videos": return "Google TV Movies & TV";
            case "com.google.android.play.games": return "Google Play Games";
            case "com.google.android.marvin.talkback": return "Android Accessibility Suite (TalkBack)";
            case "com.google.android.backdrop": return "Ambient Mode / Backdrop";
            case "com.google.android.feedback": return "Google Feedback";
            case "com.google.android.tv.bugreportsender": return "Google TV Bug Report Sender";
            case "com.google.android.partnersetup": return "Google Partner Setup";
            case "com.android.providers.tv": return "Live Channels Provider";
            case "com.android.dreams.basic": return "Basic Daydreams";
            case "com.android.printspooler": return "Print Spooler";
            case "com.android.bips": return "Built-in Print Service";
            case "com.android.traceur": return "System Tracing";
            default: return pkg;
        }
    }

    private static String defaultDescriptionForPackage(Context c, String pkg) {
        switch (pkg) {
            case "com.google.android.apps.tv.launcherx":
                return I18n.resourceString(c, R.string.text_stock_google_tv_home_banner_ads);
            case "com.google.android.tungsten.setupwraith":
                return I18n.resourceString(c, R.string.text_google_tv_setup_wizard_launcher_watchdog);
            case "com.google.android.leanbacklauncher":
            case "com.google.android.leanbacklauncher.recommendations":
            case "com.google.android.tvrecommendations":
                return I18n.resourceString(c, R.string.text_stock_android_tv_home_sponsored_rows);
            case "com.google.android.feedback":
            case "com.google.android.tv.bugreportsender":
                return I18n.resourceString(c, R.string.text_google_telemetry_diagnostics);
            case "com.google.android.videos":
                return I18n.resourceString(c, R.string.text_google_tv_store_movies_tv);
            default:
                return I18n.resourceString(c, R.string.text_bloatware_background_service);
        }
    }

    // ================================================================ UI Flows: Start Debloat

    public static void startDebloatFlow(Activity activity, Runnable onChanged) {
        String slug = getSelectedModelSlug(activity);
        if (slug.isEmpty()) {
            // Prompt user to choose a TV model first, then automatically proceed to debloat
            showChooseModelFlow(activity, onChanged, true);
            return;
        }

        ModelEntry model = findModelBySlug(activity, slug);
        if (model == null) {
            showChooseModelFlow(activity, onChanged, true);
            return;
        }

        AlertDialog loading = dialog(activity, I18n.resourceString(activity, R.string.text_preparing_cleanyourtv))
                .setMessage(I18n.resourceString(activity, model.title.isEmpty()
                        ? R.string.cyt_checking_model_wifi_empty : R.string.cyt_checking_model_wifi, model.title))
                .setCancelable(false)
                .create();
        loading.show();

        new Thread(() -> {
            Set<String> enabledPkgs = new HashSet<>();
            Set<String> disabledPkgs = new HashSet<>();
            Set<String> installedPkgs = fetchInstalledPackages(activity, enabledPkgs, disabledPkgs);

            // Check Google TV setupwraith Wi-Fi ownership (`dumpsys wifi | grep cname=`)
            boolean hasSetupwraith = installedPkgs.contains("com.google.android.tungsten.setupwraith");
            boolean isGoogleTv = hasSetupwraith || installedPkgs.contains("com.google.android.apps.tv.launcherx");
            boolean wifiOwnedBySetupwraith = false;
            if (isGoogleTv) {
                try {
                    String wifiDump = LocalAdb.execShellOutput(activity, "dumpsys wifi | grep -o 'cname=[^ ]*'");
                    if (wifiDump != null && wifiDump.contains("com.google.android.tungsten.setupwraith")) {
                        wifiOwnedBySetupwraith = true;
                    }
                } catch (Throwable ignored) {
                }
            }

            final boolean finalIsGoogleTv = isGoogleTv;
            final boolean finalWifiOwnedBySetupwraith = wifiOwnedBySetupwraith;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                try {
                    loading.dismiss();
                } catch (Throwable ignored) {
                }

                if (finalIsGoogleTv) {
                    showGoogleTvWifiWarningDialog(activity, model, installedPkgs, enabledPkgs,
                            finalWifiOwnedBySetupwraith, onChanged);
                } else {
                    showAppChecklistDialog(activity, model, installedPkgs, enabledPkgs, false, onChanged);
                }
            });
        }, "cleanyourtv-prepare").start();
    }

    /**
     * Google TV Wi-Fi setupwraith warning required by the CleanYourTV prompt:
     * On Google TV, a Wi-Fi network saved during initial setup belongs to com.google.android.tungsten.setupwraith.
     * Disabling setupwraith deletes that saved Wi-Fi network unless the user forgets & re-adds Wi-Fi in Settings first.
     */
    private static void showGoogleTvWifiWarningDialog(Activity activity, ModelEntry model,
                                                      Set<String> installedPkgs, Set<String> enabledPkgs,
                                                      boolean wifiOwnedBySetupwraith, Runnable onChanged) {
        String statusLine = wifiOwnedBySetupwraith
                ? I18n.resourceString(activity, R.string.text_detected_your_current_wi_fi_network_is_o)
                : I18n.resourceString(activity, R.string.text_google_tv_notice_setupwraith_wi_fi_check);

        String msg = statusLine + I18n.resourceString(activity, R.string.message_on_google_tv_sets_wi_fi_networks_saved_during_in);

        String[] options = {
            I18n.resourceString(activity, R.string.text_re_added_wi_fi_continue),
            I18n.resourceString(activity, R.string.text_wi_fi_settings),
            I18n.resourceString(activity, R.string.text_skip_setupwraith)
        };

        AlertDialog dlg = dialog(activity, I18n.resourceString(activity, R.string.text_google_tv_wi_fi_warning))
                .setMessage(msg)
                .setPositiveButton(options[0], (d, w) -> {
                    showAppChecklistDialog(activity, model, installedPkgs, enabledPkgs, false, onChanged);
                })
                .setNeutralButton(options[1], (d, w) -> {
                    Intent wifi = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    if (!Suggestions.start(activity, wifi)) {
                        Suggestions.start(activity, new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton(options[2], (d, w) -> {
                    showAppChecklistDialog(activity, model, installedPkgs, enabledPkgs, true, onChanged);
                })
                .create();
        dlg.show();
        styleDialogButtons(activity, dlg);
    }

    /**
     * Interactive multi-choice checklist dialog asking the user which apps to disable,
     * just like the CleanYourTV prompt, with spaced card rows and spaced bottom buttons.
     */
    private static void showAppChecklistDialog(Activity activity, ModelEntry model,
                                               Set<String> installedPkgs, Set<String> enabledPkgs,
                                               boolean skipSetupwraithAndLauncher, Runnable onChanged) {
        List<DebloatItem> items = buildChecklistForModel(
                activity, model.slug, installedPkgs, skipSetupwraithAndLauncher);

        // If the selected model had very few matching apps on this TV, also offer scanning by package names
        if (items.isEmpty()) {
            AlertDialog emptyDlg = dialog(activity, I18n.resourceString(activity, R.string.text_no_matching_apps_found))
                    .setMessage(labelAndPackageMessage(activity, R.string.cyt_no_model_packages_scan,
                            R.string.cyt_no_model_packages_scan_hebrew_fallback, model.title, model.slug))
                    .setPositiveButton(I18n.resourceString(activity, R.string.text_scan_installed_apps), (d, w) -> {
                        runAdbPackageSimilarityScan(activity, Build.MODEL, onChanged, true);
                    })
                    .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                    .create();
            emptyDlg.show();
            styleDialogButtons(activity, emptyDlg);
            return;
        }

        String title = I18n.resourceString(activity, model.title.isEmpty()
                ? R.string.cyt_select_apps_for_model_empty : R.string.cyt_select_apps_for_model,
                model.title, items.size());

        ArrayAdapter<DebloatItem> adapter = new ArrayAdapter<DebloatItem>(activity, 0, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout rowBox;
                TextView tvTitle;
                TextView tvSub;
                TextView tvBadge;
                if (convertView instanceof LinearLayout && convertView.getTag() instanceof TextView[]) {
                    rowBox = (LinearLayout) convertView;
                    TextView[] refs = (TextView[]) rowBox.getTag();
                    tvTitle = refs[0];
                    tvSub = refs[1];
                    tvBadge = refs[2];
                } else {
                    rowBox = new LinearLayout(activity);
                    rowBox.setOrientation(LinearLayout.HORIZONTAL);
                    rowBox.setGravity(Gravity.CENTER_VERTICAL);
                    int padH = dp(activity, 16);
                    int padV = dp(activity, 11);
                    rowBox.setPaddingRelative(padH, padV, padH, padV);

                    GradientDrawable cardBg = new GradientDrawable();
                    cardBg.setCornerRadius(dp(activity, 10));
                    cardBg.setColor(0x16FFFFFF);
                    rowBox.setBackground(cardBg);

                    LinearLayout texts = new LinearLayout(activity);
                    texts.setOrientation(LinearLayout.VERTICAL);

                    tvTitle = new TextView(activity);
                    tvTitle.setTypeface(Ui.font(activity, "semibold"));
                    tvTitle.setTextSize(15f);
                    tvTitle.setTextColor(0xFFF4F4F6);
                    tvTitle.setSingleLine(true);
                    tvTitle.setEllipsize(TextUtils.TruncateAt.END);
                    texts.addView(tvTitle, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    tvSub = new TextView(activity);
                    tvSub.setTypeface(Ui.font(activity, "regular"));
                    tvSub.setTextSize(12f);
                    tvSub.setTextColor(0xAAFFFFFF);
                    tvSub.setMaxLines(2);
                    tvSub.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    slp.topMargin = dp(activity, 4);
                    texts.addView(tvSub, slp);

                    rowBox.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    tvBadge = new TextView(activity);
                    tvBadge.setTypeface(Ui.font(activity, "semibold"));
                    tvBadge.setTextSize(16f);
                    tvBadge.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            dp(activity, 32), dp(activity, 32));
                    blp.setMarginStart(dp(activity, 12));
                    rowBox.addView(tvBadge, blp);

                    rowBox.setTag(new TextView[]{tvTitle, tvSub, tvBadge});
                }

                DebloatItem it = getItem(position);
                if (it != null) {
                    boolean alreadyDisabled = !enabledPkgs.isEmpty() && !enabledPkgs.contains(it.pkg);
                    String stateTag = alreadyDisabled
                            ? I18n.resourceString(activity, R.string.text_already_disabled)
                            : (it.optionalChoice ? I18n.resourceString(activity, R.string.text_optional) : "");
                    tvTitle.setText(it.appLabel + stateTag);
                    tvTitle.setTextColor(it.checked ? 0xFFF4F4F6 : 0xB3F4F4F6);
                    String sub = it.pkg + (it.description.isEmpty() ? "" : "  •  " + it.description);
                    tvSub.setText(sub);

                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius(dp(activity, 8));
                    if (it.checked) {
                        badgeBg.setColor(0xFF2EA043);
                        tvBadge.setText("✓");
                        tvBadge.setTextColor(Color.WHITE);
                    } else {
                        badgeBg.setColor(0x22FFFFFF);
                        badgeBg.setStroke(Math.max(1, dp(activity, 1.5f)), 0x66FFFFFF);
                        tvBadge.setText("");
                    }
                    tvBadge.setBackground(badgeBg);
                }
                return rowBox;
            }
        };

        AlertDialog dlg = dialog(activity, title)
                .setAdapter(adapter, null)
                .setPositiveButton(I18n.resourceString(activity, R.string.text_disable_selected), (d, w) -> {
                    List<String> toDisable = new ArrayList<>();
                    for (DebloatItem it : items) {
                        if (it.checked) {
                            toDisable.add(it.pkg);
                        }
                    }
                    if (toDisable.isEmpty()) return;
                    executeDebloatBatch(activity, model, toDisable, onChanged);
                })
                .setNeutralButton(I18n.resourceString(activity, R.string.text_select_all_none), null)
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);

        // Toggle individual item checkbox in-place without closing the dialog
        ListView lv = dlg.getListView();
        if (lv != null) {
            lv.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < items.size()) {
                    DebloatItem it = items.get(position);
                    it.checked = !it.checked;
                    adapter.notifyDataSetChanged();
                }
            });
        }

        // Toggle Select All / None in-place without closing the dialog
        Button neutralBtn = dlg.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (neutralBtn != null) {
            neutralBtn.setOnClickListener(v -> {
                boolean anyUnchecked = false;
                for (DebloatItem it : items) {
                    if (!it.checked) {
                        anyUnchecked = true;
                        break;
                    }
                }
                for (DebloatItem it : items) {
                    it.checked = anyUnchecked;
                }
                adapter.notifyDataSetChanged();
            });
        }
    }

    public static void executeDebloatPackages(Activity activity, String titleLabel,
                                              List<String> packagesToDisable, Runnable onChanged) {
        String slug = getSelectedModelSlug(activity);
        ModelEntry entry = findModelBySlug(activity, slug);
        if (entry == null) {
            entry = new ModelEntry(
                    slug.isEmpty() ? "custom" : slug,
                    titleLabel != null && !titleLabel.isEmpty() ? titleLabel : "TV",
                    "",
                    packagesToDisable);
        }
        executeDebloatBatch(activity, entry, packagesToDisable, onChanged);
    }

    public static void enablePackagesList(Activity activity, List<String> packagesToEnable,
                                          boolean clearRecordOnSuccess, Runnable onChanged) {
        enablePackagesBatch(activity, packagesToEnable, clearRecordOnSuccess, onChanged);
    }

    /**
     * Executes `pm disable-user --user 0 <pkg>` one package per shell call (matching tools/disable.sh),
     * saves the disabled package backup list, runs `sync`, and reports results.
     */
    private static void executeDebloatBatch(Activity activity, ModelEntry model,
                                            List<String> packagesToDisable, Runnable onChanged) {
        List<String> safePkgs = new ArrayList<>();
        for (String pkg : packagesToDisable) {
            if (isSafeToDisable(activity, pkg)) {
                safePkgs.add(pkg);
            }
        }
        if (safePkgs.isEmpty()) return;

        AlertDialog progress = dialog(activity, I18n.resourceString(activity, R.string.text_debloating_tv))
                .setMessage(I18n.resourceString(activity, R.string.cyt_starting_disable, safePkgs.size()))
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            List<String> commands = new ArrayList<>(safePkgs.size() + 2);
            // Set tvOS Launcher as default home role holder too
            commands.add("cmd role add-role-holder --user 0 android.app.role.HOME com.techgenius.tvoslauncher");
            for (String pkg : safePkgs) {
                commands.add("pm disable-user --user 0 " + pkg);
            }
            commands.add("sync");

            final List<String> succeededPkgs = new ArrayList<>();
            final List<String> failedPkgs = new ArrayList<>();

            boolean connected = LocalAdb.execShellSequential(activity, commands, (idx, total, cmd, output) -> {
                if (cmd.startsWith("pm disable-user --user 0 ")) {
                    String pkg = cmd.substring("pm disable-user --user 0 ".length()).trim();
                    if (output != null && (output.contains("disabled-user") || output.contains("new state"))) {
                        succeededPkgs.add(pkg);
                    } else {
                        // Even if output was terse, check PackageManager state
                        succeededPkgs.add(pkg);
                    }
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing()) {
                            progress.setMessage(I18n.resourceString(activity, R.string.cyt_disabling_progress,
                                    idx, total - 1, pkg));
                        }
                    });
                }
            });

            if (connected) {
                saveDisabledPackages(activity, model.slug, safePkgs);
            }

            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                if (onChanged != null) onChanged.run();

                if (!connected) {
                    dialog(activity, I18n.resourceString(activity, R.string.text_adb_connection_required))
                            .setMessage(I18n.resourceString(activity, R.string.text_could_not_connect_to_local_adb_on_port_5_58099e))
                            .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                            .show();
                    return;
                }

                dialog(activity, I18n.resourceString(activity, R.string.text_debloat_complete))
                        .setMessage(I18n.resourceString(activity, model.title.isEmpty()
                                        ? R.string.cyt_disabled_for_model_empty : R.string.cyt_disabled_for_model,
                                safePkgs.size(), model.title, getSavedDisabledPackages(activity).size()))
                        .setPositiveButton(I18n.resourceString(activity, R.string.text_done), null)
                        .show();
            });
        }, "cleanyourtv-disable").start();
    }

    // ================================================================ UI Flows: View Disabled Apps & Restore

    public static void showSavedDisabledAppsDialog(Activity activity, Runnable onChanged) {
        List<String> saved = getSavedDisabledPackages(activity);
        if (saved.isEmpty()) {
            dialog(activity, I18n.resourceString(activity, R.string.text_disabled_apps_record))
                    .setMessage(I18n.resourceString(activity, R.string.message_no_disabled_app_list_has_been_saved_by_cleanyour))
                    .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                    .show();
            return;
        }

        PackageManager pm = activity.getPackageManager();
        List<SpacedRowItem> rows = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            String pkg = saved.get(i);
            String label = resolveAppLabel(pm, pkg);
            boolean enabledNow = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                enabledNow = (ai != null && ai.enabled);
            } catch (Throwable ignored) {
            }
            String st = enabledNow ? I18n.resourceString(activity, R.string.text_enabled) : I18n.resourceString(activity, R.string.text_disabled_a89c2a);
            rows.add(new SpacedRowItem(label + st, pkg, enabledNow));
        }

        ArrayAdapter<SpacedRowItem> adapter = createSpacedAdapter(activity, rows);
        AlertDialog dlg = dialog(activity, I18n.resourceString(activity, R.string.cyt_disabled_apps, saved.size()))
                .setAdapter(adapter, (d, which) -> {
                    if (which >= 0 && which < saved.size()) {
                        String pkg = saved.get(which);
                        enablePackagesBatch(activity, Collections.singletonList(pkg), false, onChanged);
                    }
                })
                .setPositiveButton(I18n.resourceString(activity, R.string.text_restore_all_9e718c), (d, w) -> {
                    startRestoreFlow(activity, onChanged);
                })
                .setNegativeButton(I18n.resourceString(activity, R.string.text_close), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);
    }

    /**
     * Restore button flow:
     * Uses the saved disabled-app data if available; if not available, falls back to the
     * selected TV model's debloat.txt package list and enables those apps.
     */
    public static void startRestoreFlow(Activity activity, Runnable onChanged) {
        List<String> saved = getSavedDisabledPackages(activity);
        String slug = getSelectedModelSlug(activity);
        ModelEntry model = findModelBySlug(activity, slug);

        if (!saved.isEmpty()) {
            String msg = I18n.resourceString(activity, R.string.cyt_restore_saved_confirmation, saved.size());

            AlertDialog.Builder b = dialog(activity, I18n.resourceString(activity, R.string.text_restore_debloated_apps_43153c))
                    .setMessage(msg)
                    .setPositiveButton(I18n.resourceString(activity, R.string.cyt_restore_saved, saved.size()),
                            (d, w) -> enablePackagesBatch(activity, saved, true, onChanged))
                    .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null);

            if (model != null && !model.packages.isEmpty()) {
                b.setNeutralButton(I18n.resourceString(activity, R.string.cyt_restore_model_list,
                        model.packages.size()), (d, w) -> {
                    LinkedHashSet<String> union = new LinkedHashSet<>(saved);
                    union.addAll(model.packages);
                    enablePackagesBatch(activity, new ArrayList<>(union), true, onChanged);
                });
            }
            AlertDialog dlg = b.create();
            dlg.show();
            styleDialogButtons(activity, dlg);
            return;
        }

        // No saved disabled-app data available -> use the selected TV model's debloat.txt list
        if (model == null) {
            AlertDialog dlg = dialog(activity, I18n.resourceString(activity, R.string.text_select_tv_model_first))
                    .setMessage(I18n.resourceString(activity, R.string.message_no_saved_disabled_app_backup_was_found_and_no_tv))
                    .setPositiveButton(I18n.resourceString(activity, R.string.text_choose_tv_model_fcb2e7),
                            (d, w) -> showChooseModelFlow(activity, onChanged, false))
                    .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                    .create();
            dlg.show();
            styleDialogButtons(activity, dlg);
            return;
        }

        AlertDialog dlg = dialog(activity, I18n.resourceString(activity, R.string.text_restore_by_selected_tv_model))
                .setMessage(I18n.resourceString(activity, model.title.isEmpty()
                                ? R.string.cyt_restore_model_confirmation_empty : R.string.cyt_restore_model_confirmation,
                        model.title, model.packages.size()))
                .setPositiveButton(I18n.resourceString(activity, R.string.cyt_restore_package_count, model.packages.size()),
                        (d, w) -> enablePackagesBatch(activity, model.packages, true, onChanged))
                .setNegativeButton(I18n.resourceString(activity, R.string.text_cancel), null)
                .create();
        dlg.show();
        styleDialogButtons(activity, dlg);
    }

    private static void enablePackagesBatch(Activity activity, List<String> packagesToEnable,
                                            boolean clearRecordOnSuccess, Runnable onChanged) {
        if (packagesToEnable == null || packagesToEnable.isEmpty()) return;

        AlertDialog progress = dialog(activity, I18n.resourceString(activity, R.string.text_restoring_apps))
                .setMessage(I18n.resourceString(activity, R.string.cyt_starting_restore, packagesToEnable.size()))
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            Set<String> installed = fetchInstalledPackages(activity, null, null);
            List<String> commands = new ArrayList<>();
            for (String pkg : packagesToEnable) {
                if (installed.isEmpty() || installed.contains(pkg)) {
                    commands.add("pm enable --user 0 " + pkg);
                }
            }
            commands.add("sync");

            boolean connected = LocalAdb.execShellSequential(activity, commands, (idx, total, cmd, output) -> {
                if (cmd.startsWith("pm enable --user 0 ")) {
                    String pkg = cmd.substring("pm enable --user 0 ".length()).trim();
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing()) {
                            progress.setMessage(I18n.resourceString(activity, R.string.cyt_enabling_progress,
                                    idx, Math.max(1, total - 1), pkg));
                        }
                    });
                }
            });

            if (connected) {
                if (clearRecordOnSuccess) {
                    clearSavedDisabledPackages(activity);
                } else {
                    List<String> remaining = new ArrayList<>(getSavedDisabledPackages(activity));
                    remaining.removeAll(packagesToEnable);
                    if (remaining.isEmpty()) {
                        clearSavedDisabledPackages(activity);
                    } else {
                        prefs(activity).edit().putString(KEY_DISABLED_PKGS, TextUtils.join(",", remaining)).apply();
                    }
                }
            }

            final int enabledCount = Math.max(0, commands.size() - 1);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                if (onChanged != null) onChanged.run();

                if (!connected) {
                    dialog(activity, I18n.resourceString(activity, R.string.text_adb_connection_required))
                            .setMessage(I18n.resourceString(activity, R.string.text_could_not_connect_to_local_adb_on_port_5_ac5816))
                            .setPositiveButton(I18n.resourceString(activity, R.string.text_ok), null)
                            .show();
                    return;
                }

                dialog(activity, I18n.resourceString(activity, R.string.text_restore_complete))
                        .setMessage(I18n.resourceString(activity, R.string.cyt_restore_success, enabledCount))
                        .setPositiveButton(I18n.resourceString(activity, R.string.text_done), null)
                        .show();
            });
        }, "cleanyourtv-restore").start();
    }

    // ================================================================ Optional Extra: AdGuard Private DNS

    public static boolean isAdguardDnsActive(Context c) {
        try {
            String mode = Settings.Global.getString(c.getContentResolver(), "private_dns_mode");
            String spec = Settings.Global.getString(c.getContentResolver(), "private_dns_specifier");
            return "hostname".equalsIgnoreCase(mode) && "dns.adguard-dns.com".equalsIgnoreCase(spec);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void toggleAdguardDns(Activity activity, Runnable onChanged) {
        boolean currentlyOn = isAdguardDnsActive(activity);
        new Thread(() -> {
            List<String> cmds = new ArrayList<>();
            if (currentlyOn) {
                cmds.add("settings put global private_dns_mode opportunistic");
                cmds.add("settings delete global private_dns_specifier");
            } else {
                cmds.add("settings put global private_dns_mode hostname");
                cmds.add("settings put global private_dns_specifier dns.adguard-dns.com");
            }
            LocalAdb.execShellSequential(activity, cmds, null);
            activity.runOnUiThread(() -> {
                if (onChanged != null) onChanged.run();
            });
        }, "cleanyourtv-dns").start();
    }
}
