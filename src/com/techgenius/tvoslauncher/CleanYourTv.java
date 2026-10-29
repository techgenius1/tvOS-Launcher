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

    private CleanYourTv() {}

    private static String tr(Context c, String en, String tr) {
        return I18n.s(c, en, tr);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static AlertDialog.Builder dialog(Context c, String title) {
        return new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title);
    }

    // ================================================================ Model Index & Parsing

    public static synchronized List<ModelEntry> loadIndex(Context c) {
        if (cachedModels != null) return cachedModels;
        List<ModelEntry> list = new ArrayList<>();
        try (InputStream is = c.getAssets().open("cleanyourtv/index.txt");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 4) continue;
                String slug = parts[0].trim();
                String title = parts[1].trim();
                String meta = parts[2].trim();
                String[] pkgArr = parts[3].split(",");
                List<String> pkgs = new ArrayList<>(pkgArr.length);
                for (String p : pkgArr) {
                    String pt = p.trim();
                    if (!pt.isEmpty()) pkgs.add(pt);
                }
                list.add(new ModelEntry(slug, title.isEmpty() ? slug : title, meta, pkgs));
            }
        } catch (Throwable ignored) {
        }
        cachedModels = list;
        return list;
    }

    public static ModelEntry findModelBySlug(Context c, String slug) {
        if (slug == null || slug.isEmpty()) return null;
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
        if (slug.isEmpty()) {
            return tr(c, "Tap to detect / select…", "Algılamak / seçmek için dokunun…");
        }
        ModelEntry entry = findModelBySlug(c, slug);
        if (entry != null) {
            return entry.title + " (" + entry.slug + ")";
        }
        String savedTitle = prefs(c).getString(KEY_MODEL_TITLE, slug);
        return savedTitle + " (" + slug + ")";
    }

    public static void setSelectedModel(Context c, ModelEntry entry) {
        if (entry == null) return;
        prefs(c).edit()
                .putString(KEY_MODEL_SLUG, entry.slug)
                .putString(KEY_MODEL_TITLE, entry.title)
                .apply();
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
            return tr(c, "Not done (Select TV model first)", "Yapılmadı (Önce TV modelini seçin)");
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
            return tr(c, "Done (" + count + " apps disabled)", "Tamamlandı (" + count + " uygulama devre dışı)");
        }
        if (disabledCount > 0) {
            return tr(c, "Not done (" + disabledCount + "/" + installedCount + " disabled)",
                    "Yapılmadı (" + disabledCount + "/" + installedCount + " devre dışı)");
        }
        return tr(c, "Not done", "Yapılmadı");
    }

    public static String getSavedDisabledSummary(Context c) {
        List<String> saved = getSavedDisabledPackages(c);
        if (saved.isEmpty()) {
            return tr(c, "None saved", "Kayıt yok");
        }
        return saved.size() + " " + tr(c, "apps", "uygulama");
    }

    // ================================================================ Safety Guards

    /**
     * Checks whether a package is safe to disable according to cleanyourtv/tools/disable.sh
     * and recovery.py (never disables core system packages, TV input hardware services, or tvOS Launcher).
     */
    public static boolean isSafeToDisable(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (NEVER_DISABLE_EXACT.contains(pkg)) return false;
        if (CORE_RE.matcher(pkg).matches() && !CORE_OK.matcher(pkg).matches()) {
            return false;
        }
        // Protect TV input hardware services (HDMI / tuner inputs)
        try {
            PackageManager pm = c.getPackageManager();
            if (pm.checkPermission("android.permission.TV_INPUT_HARDWARE", pkg)
                    == PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
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
                reasons.add(tr(c, "Exact model match", "Tam model eşleşmesi"));
            } else if (!modelOnlySlugGuess.isEmpty() && modelOnlySlugGuess.length() >= 4
                    && (slugLow.equals(modelOnlySlugGuess) || slugLow.endsWith("-" + modelOnlySlugGuess))) {
                score += 105;
                reasons.add(tr(c, "Exact model code match", "Tam model kodu eşleşmesi"));
            }

            // 2. Check ro.product.model="...", product, device inside headerMeta
            if (!modelLower.isEmpty() && modelLower.length() >= 3) {
                if (metaLow.contains("ro.product.model=\"" + modelLower + "\"")
                        || metaLow.contains("model=\"" + modelLower + "\"")
                        || titleLow.contains("(" + modelLower + ")")) {
                    score += 90;
                    reasons.add(tr(c, "Model: " + model, "Model: " + model));
                }
            }

            if (!productLower.isEmpty() && productLower.length() >= 3
                    && (metaLow.contains("product " + productLower) || metaLow.contains(productLower))) {
                score += 80;
                reasons.add(tr(c, "Product: " + product, "Ürün kodu: " + product));
            }

            if (!deviceLower.isEmpty() && deviceLower.length() >= 3
                    && (metaLow.contains("device " + deviceLower)
                        || metaLow.contains("device: " + deviceLower)
                        || slugLow.contains("-" + deviceLower))) {
                score += 65;
                reasons.add(tr(c, "Board/Device: " + device, "Anakart/Cihaz: " + device));
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
                reasons.add(tr(c, "Brand/OEM: " + (brand.isEmpty() ? manufacturer : brand),
                        "Marka/Üretici: " + (brand.isEmpty() ? manufacturer : brand)));
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
                    reasons.add(tr(c, "Similar model name", "Benzer model adı"));
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
     * and ranks all 341 CleanYourTV models by system/OEM package overlap.
     */
    public static List<ModelMatch> findPackageSimilarityMatches(Context c, Set<String> installedPkgs) {
        List<ModelMatch> matches = new ArrayList<>();
        for (ModelEntry entry : loadIndex(c)) {
            if (entry.packages.isEmpty()) continue;
            int totalMatched = 0;
            int oemMatched = 0;
            List<String> sampleOem = new ArrayList<>();
            for (String pkg : entry.packages) {
                if (installedPkgs.contains(pkg)) {
                    totalMatched++;
                    if (!GENERIC_GOOGLE_PKGS.contains(pkg)) {
                        oemMatched++;
                        if (sampleOem.size() < 2) {
                            sampleOem.add(shortPkgName(pkg));
                        }
                    }
                }
            }
            if (totalMatched == 0) continue;
            // Weight OEM/vendor-specific packages 5x higher than generic Google TV packages
            int pct = (totalMatched * 100) / Math.max(1, entry.packages.size());
            int score = (oemMatched * 25) + (totalMatched * 4) + (pct / 4);
            String reason = tr(c,
                    totalMatched + "/" + entry.packages.size() + " matching apps"
                            + (oemMatched > 0 ? " (" + oemMatched + " OEM/system: " + TextUtils.join(", ", sampleOem) + ")" : ""),
                    totalMatched + "/" + entry.packages.size() + " eşleşen uygulama"
                            + (oemMatched > 0 ? " (" + oemMatched + " sistem/üretici: " + TextUtils.join(", ", sampleOem) + ")" : ""));
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
     * Fetches the full installed package set from ADB (`pm list packages --user 0`) plus PackageManager.
     */
    public static Set<String> fetchInstalledPackages(Context c, Set<String> outEnabled, Set<String> outDisabled) {
        Set<String> all = new HashSet<>();
        try {
            String enabledOut = LocalAdb.execShellOutput(c, "pm list packages -e --user 0");
            if (enabledOut != null && !enabledOut.isEmpty()) {
                for (String line : enabledOut.split("\\r?\\n")) {
                    String s = line.trim();
                    if (s.startsWith("package:")) {
                        String pkg = s.substring("package:".length()).trim();
                        if (!pkg.isEmpty()) {
                            all.add(pkg);
                            if (outEnabled != null) outEnabled.add(pkg);
                        }
                    }
                }
            }
            String disabledOut = LocalAdb.execShellOutput(c, "pm list packages -d --user 0");
            if (disabledOut != null && !disabledOut.isEmpty()) {
                for (String line : disabledOut.split("\\r?\\n")) {
                    String s = line.trim();
                    if (s.startsWith("package:")) {
                        String pkg = s.substring("package:".length()).trim();
                        if (!pkg.isEmpty()) {
                            all.add(pkg);
                            if (outDisabled != null) outDisabled.add(pkg);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Also merge PackageManager view as fallback / supplement
        try {
            PackageManager pm = c.getPackageManager();
            List<PackageInfo> pis = pm.getInstalledPackages(0);
            if (pis != null) {
                for (PackageInfo pi : pis) {
                    if (pi == null || pi.packageName == null) continue;
                    all.add(pi.packageName);
                    if (pi.applicationInfo != null) {
                        if (pi.applicationInfo.enabled) {
                            if (outEnabled != null && (outDisabled == null || !outDisabled.contains(pi.packageName))) {
                                outEnabled.add(pi.packageName);
                            }
                        } else {
                            if (outDisabled != null) outDisabled.add(pi.packageName);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return all;
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
                dialog(activity, tr(activity, "TV Model Matched", "TV Modeli Eşleşti"))
                        .setMessage(tr(activity,
                                "Exact TV model found and selected automatically:\n\n• "
                                        + hw.exactSingleMatch.entry.title + " (" + hw.exactSingleMatch.entry.slug + ")\n"
                                        + hw.exactSingleMatch.reason,
                                "Tam TV modeli bulundu ve otomatik olarak seçildi:\n\n• "
                                        + hw.exactSingleMatch.entry.title + " (" + hw.exactSingleMatch.entry.slug + ")\n"
                                        + hw.exactSingleMatch.reason))
                        .setPositiveButton(tr(activity, "OK", "Tamam"), null)
                        .setNeutralButton(tr(activity, "Change Model…", "Modeli Değiştir…"),
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
                    box.setPadding(padH, padV, padH, padV);

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
            b.setPadding(padH, padV, padH, padV);
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
            parentBar.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
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
            lv.setPadding(padH, padV, padH, padV);
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
                tr(activity, "🔍 Scan Installed System Apps via ADB", "🔍 ADB ile Yüklü Sistem Uygulamalarını Tara"),
                tr(activity, "Pull full app list from ADB and match models by system/OEM package names",
                        "ADB'den tam uygulama listesini çek ve sistem/üretici paket adlarına göre eşleştir"),
                false));
        final int idxBrowseAll = rows.size();
        rows.add(new SpacedRowItem(
                tr(activity, "📋 Browse All 341 TV Models", "📋 Tüm 341 TV Modeline Göz At"),
                tr(activity, "View or filter the complete list of supported TV models",
                        "Desteklenen tüm TV modellerinin tam listesini görüntüle veya filtrele"),
                false));

        String title = tr(activity,
                "Choose Closest TV Model — " + hw.deviceDescription,
                "En Yakın TV Modelini Seçin — " + hw.deviceDescription);

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
                .setPositiveButton(tr(activity, "🔍 Search…", "🔍 Model Ara…"), (d, w) -> {
                    promptFilterQuery(activity, "", onChanged, autoContinueToDebloat);
                })
                .setNeutralButton(tr(activity, "Scan Apps (ADB)", "Uygulamaları Tara"), (d, w) -> {
                    runAdbPackageSimilarityScan(activity, hw.deviceDescription, onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);
    }

    private static void runAdbPackageSimilarityScan(Activity activity, String deviceDesc,
                                                    Runnable onChanged, boolean autoContinueToDebloat) {
        AlertDialog progress = dialog(activity, tr(activity, "Scanning TV System Apps…", "TV Sistem Uygulamaları Taranıyor…"))
                .setMessage(tr(activity,
                        "Pulling full package list from ADB and comparing system/OEM package names across 341 TV models…",
                        "ADB üzerinden tam uygulama listesi çekiliyor ve 341 TV modelinin sistem/üretici paket adlarıyla karşılaştırılıyor…"))
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
                tr(activity, "📋 Browse All 341 TV Models", "📋 Tüm 341 TV Modeline Göz At"),
                tr(activity, "View or filter the complete list of supported TV models",
                        "Desteklenen tüm TV modellerinin tam listesini görüntüle veya filtrele"),
                false));

        String title = tr(activity,
                "Models with Similar System Apps (" + totalInstalled + " apps scanned)",
                "Benzer Sistem Uygulamalarına Sahip Modeller (" + totalInstalled + " uygulama tarandı)");

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
                .setPositiveButton(tr(activity, "🔍 Search…", "🔍 Model Ara…"), (d, w) -> {
                    promptFilterQuery(activity, "", onChanged, autoContinueToDebloat);
                })
                .setNeutralButton(tr(activity, "All 341 Models", "Tüm 341 Model"), (d, w) -> {
                    showBrowseAllModelsDialog(activity, "", onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
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
            String sub = "[" + m.slug + "]  •  " + m.packages.size() + " " + tr(activity, "packages", "paket");
            rows.add(new SpacedRowItem(m.title, sub, isCurrent));
        }

        String title = q.isEmpty()
                ? tr(activity, "All TV Models (" + filtered.size() + ")", "Tüm TV Modelleri (" + filtered.size() + ")")
                : tr(activity, "Search: \"" + initialQuery + "\" (" + filtered.size() + ")",
                     "Arama: \"" + initialQuery + "\" (" + filtered.size() + ")");

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
                .setPositiveButton(tr(activity, "🔍 Search…", "🔍 Model Ara…"), (d, w) -> {
                    promptFilterQuery(activity, initialQuery, onChanged, autoContinueToDebloat);
                })
                .setNeutralButton(tr(activity, "Scan Apps (ADB)", "Uygulamaları Tara"), (d, w) -> {
                    runAdbPackageSimilarityScan(activity, Build.MODEL, onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
                .create();
        dlg.show();
        applyDialogListSpacing(activity, dlg);
    }

    private static void promptFilterQuery(Activity activity, String currentQuery,
                                          Runnable onChanged, boolean autoContinueToDebloat) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(tr(activity, "e.g. tcl, sony, hisense, xiaomi, philips, skyworth…",
                "örn. tcl, sony, hisense, xiaomi, philips, skyworth…"));
        if (currentQuery != null && !currentQuery.isEmpty()) {
            input.setText(currentQuery);
            input.setSelection(currentQuery.length());
        }
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(activity);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog dlg = dialog(activity, tr(activity, "Search TV Model or Brand", "TV Modeli veya Markası Ara"))
                .setView(box)
                .setPositiveButton(tr(activity, "Search", "Ara"), (d, w) -> {
                    String nextQ = input.getText().toString().trim();
                    showBrowseAllModelsDialog(activity, nextQ, onChanged, autoContinueToDebloat);
                })
                .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
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
    public static List<DebloatItem> buildChecklistForModel(Context c, String slug,
                                                           Set<String> installedPkgs,
                                                           boolean skipSetupwraithAndLauncher) {
        Map<String, String> activePkgComments = new LinkedHashMap<>();
        Map<String, String> optionalPkgComments = new LinkedHashMap<>();

        String assetPath = "cleanyourtv/examples/" + slug + "/debloat.txt";
        String currentSection = "";
        try (InputStream is = c.getAssets().open(assetPath);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#")) {
                    String afterHash = line.substring(1).trim();
                    // Check if this is a commented-out package line like:
                    // # com.google.android.katniss  # Google Assistant (kept by user choice)
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

        // Also add standard CleanYourTV prompt preference-dependent optional packages if installed on this TV
        Map<String, String> promptChoiceDefaults = new LinkedHashMap<>();
        promptChoiceDefaults.put("com.google.android.katniss",
                tr(c, "Google Assistant / Voice Search (optional)", "Google Asistan / Sesli Arama (isteğe bağlı)"));
        promptChoiceDefaults.put("com.google.android.youtube.tvmusic",
                tr(c, "YouTube Music (optional)", "YouTube Music (isteğe bağlı)"));
        promptChoiceDefaults.put("com.google.android.play.games",
                tr(c, "Google Play Games (optional)", "Google Play Oyunlar (isteğe bağlı)"));
        promptChoiceDefaults.put("com.google.android.marvin.talkback",
                tr(c, "Android Accessibility Suite / TalkBack (optional)", "Erişilebilirlik Paketi / TalkBack (isteğe bağlı)"));
        promptChoiceDefaults.put("com.google.android.apps.mediashell",
                tr(c, "Chromecast built-in / Google Cast receiver (optional — keep if you Cast from phone)",
                        "Chromecast built-in / Google Cast alıcısı (telefondan ekran yansıtıyorsanız koruyun)"));
        promptChoiceDefaults.put("com.google.android.tv.remote.service",
                tr(c, "Android TV Remote Service (optional — keep if you use Google TV phone remote)",
                        "Android TV Uzaktan Kumanda Servisi (telefon kumandası kullanıyorsanız koruyun)"));

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
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (ai != null) {
                CharSequence cs = ai.loadLabel(pm);
                if (cs != null && cs.length() > 0) {
                    String s = cs.toString().trim();
                    if (!s.isEmpty() && !s.equals(pkg)) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return friendlyFallbackName(pkg);
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
                return tr(c, "Stock Google TV Home & banner ads", "Stok Google TV Ana Ekranı ve reklamlar");
            case "com.google.android.tungsten.setupwraith":
                return tr(c, "Google TV setup wizard & launcher watchdog", "Google TV kurulum sihirbazı ve başlatıcı bekçisi");
            case "com.google.android.leanbacklauncher":
            case "com.google.android.leanbacklauncher.recommendations":
            case "com.google.android.tvrecommendations":
                return tr(c, "Stock Android TV Home & sponsored rows", "Stok Android TV Ana Ekranı ve sponsorlu satırlar");
            case "com.google.android.feedback":
            case "com.google.android.tv.bugreportsender":
                return tr(c, "Google telemetry & diagnostics", "Google telemetri ve hata raporlama");
            case "com.google.android.videos":
                return tr(c, "Google TV store / Movies & TV", "Google TV mağazası / Filmler ve TV");
            default:
                return tr(c, "Bloatware / background service", "Gereksiz sistem uygulaması / arka plan servisi");
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

        AlertDialog loading = dialog(activity, tr(activity, "Preparing CleanYourTV…", "CleanYourTV Hazırlanıyor…"))
                .setMessage(tr(activity,
                        "Checking installed packages and Wi-Fi status over ADB for " + model.title + "…",
                        model.title + " için ADB üzerinden yüklü paketler ve Wi-Fi durumu kontrol ediliyor…"))
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
                ? tr(activity,
                        "⚠️ Detected: Your current Wi-Fi network IS owned by setupwraith (cname=com.google.android.tungsten.setupwraith).\n\n",
                        "⚠️ Tespit edildi: Mevcut Wi-Fi ağınız setupwraith tarafından kaydedilmiş (cname=com.google.android.tungsten.setupwraith).\n\n")
                : tr(activity,
                        "ℹ️ Google TV Notice (setupwraith Wi-Fi check):\n\n",
                        "ℹ️ Google TV Uyarısı (setupwraith Wi-Fi kontrolü):\n\n");

        String msg = statusLine + tr(activity,
                "On Google TV sets, Wi-Fi networks saved during initial setup belong to com.google.android.tungsten.setupwraith. "
                + "Disabling setupwraith so the stock Google TV launcher stays dead will delete a Wi-Fi network owned by setupwraith.\n\n"
                + "Before disabling setupwraith:\n"
                + "1. Open Wi-Fi Settings → select your Wi-Fi network → Forget network.\n"
                + "2. Re-connect to your Wi-Fi network (it will now belong to com.android.tv.settings).\n"
                + "3. Return here and tap \"I Forgot & Re-added Wi-Fi — Continue\".",
                "Google TV cihazlarda ilk kurulum sırasında kaydedilen Wi-Fi ağı com.google.android.tungsten.setupwraith paketine aittir. "
                + "Stok Google TV başlatıcısının kapanması için setupwraith devre dışı bırakıldığında bu Wi-Fi ağı silinir.\n\n"
                + "Devam etmeden önce:\n"
                + "1. Wi-Fi Ayarları'nı açın → Wi-Fi ağınızı seçip Ağı Unut deyin.\n"
                + "2. Wi-Fi ağınıza yeniden bağlanın (artık com.android.tv.settings adına kaydedilir).\n"
                + "3. Buraya dönüp \"Wi-Fi'yi Unutup Yeniden Ekledim — Devam Et\"e basın.");

        String[] options = {
            tr(activity, "✅ Re-added Wi-Fi (Continue)",
                    "✅ Wi-Fi Yenilendi (Devam)"),
            tr(activity, "📶 Wi-Fi Settings…",
                    "📶 Wi-Fi Ayarları…"),
            tr(activity, "🛡️ Skip setupwraith",
                    "🛡️ setupwraith'i Atla")
        };

        AlertDialog dlg = dialog(activity, tr(activity, "Google TV Wi-Fi Warning", "Google TV Wi-Fi Uyarısı"))
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
            AlertDialog emptyDlg = dialog(activity, tr(activity, "No Matching Apps Found", "Eşleşen Uygulama Bulunamadı"))
                    .setMessage(tr(activity,
                            "None of the packages listed in " + model.title + " (" + model.slug + ") are installed on this TV.\n\n"
                            + "Would you like to scan your TV's installed apps over ADB to find the closest matching TV model?",
                            model.title + " (" + model.slug + ") listesindeki paketlerin hiçbiri bu TV'de yüklü değil.\n\n"
                            + "En yakın TV modelini bulmak için ADB üzerinden yüklü uygulamaları taramak ister misiniz?"))
                    .setPositiveButton(tr(activity, "Scan Installed Apps", "Yüklü Uygulamaları Tara"), (d, w) -> {
                        runAdbPackageSimilarityScan(activity, Build.MODEL, onChanged, true);
                    })
                    .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
                    .create();
            emptyDlg.show();
            styleDialogButtons(activity, emptyDlg);
            return;
        }

        String title = tr(activity,
                "Select Apps to Disable — " + model.title + " (" + items.size() + " found)",
                "Devre Dışı Bırakılacak Uygulamaları Seçin — " + model.title + " (" + items.size() + " bulundu)");

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
                    rowBox.setPadding(padH, padV, padH, padV);

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
                    blp.leftMargin = dp(activity, 12);
                    rowBox.addView(tvBadge, blp);

                    rowBox.setTag(new TextView[]{tvTitle, tvSub, tvBadge});
                }

                DebloatItem it = getItem(position);
                if (it != null) {
                    boolean alreadyDisabled = !enabledPkgs.isEmpty() && !enabledPkgs.contains(it.pkg);
                    String stateTag = alreadyDisabled
                            ? tr(activity, " [Already disabled]", " [Zaten devre dışı]")
                            : (it.optionalChoice ? tr(activity, " [Optional]", " [İsteğe bağlı]") : "");
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
                .setPositiveButton(tr(activity, "Disable Selected", "Seçilenleri Devre Dışı Bırak"), (d, w) -> {
                    List<String> toDisable = new ArrayList<>();
                    for (DebloatItem it : items) {
                        if (it.checked) {
                            toDisable.add(it.pkg);
                        }
                    }
                    if (toDisable.isEmpty()) return;
                    executeDebloatBatch(activity, model, toDisable, onChanged);
                })
                .setNeutralButton(tr(activity, "Select All / None", "Tümünü Seç / Kaldır"), null)
                .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
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

        AlertDialog progress = dialog(activity, tr(activity, "Debloating TV…", "TV Temizleniyor (Debloat)…"))
                .setMessage(tr(activity,
                        "Starting ADB debloat for " + safePkgs.size() + " packages…",
                        safePkgs.size() + " paket için ADB debloat işlemi başlatılıyor…"))
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
                            progress.setMessage(tr(activity,
                                    "Disabling (" + idx + "/" + (total - 1) + "):\n" + pkg,
                                    "Devre dışı bırakılıyor (" + idx + "/" + (total - 1) + "):\n" + pkg));
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
                    dialog(activity, tr(activity, "ADB Connection Required", "ADB Bağlantısı Gerekli"))
                            .setMessage(tr(activity,
                                    "Could not connect to local ADB on port 5555.\nPlease make sure USB / Network Debugging is enabled in Developer Options and tap Pair / Repair ADB first.",
                                    "Port 5555 üzerinden yerel ADB'ye bağlanılamadı.\nLütfen Geliştirici Seçenekleri'nden USB / Ağ Hata Ayıklamanın açık olduğundan emin olun ve önce ADB Eşleştir / Onar'a dokunun."))
                            .setPositiveButton(tr(activity, "OK", "Tamam"), null)
                            .show();
                    return;
                }

                dialog(activity, tr(activity, "Debloat Complete", "Debloat Tamamlandı"))
                        .setMessage(tr(activity,
                                "Successfully disabled " + safePkgs.size() + " packages for " + model.title + ".\n\n"
                                + "• Saved disabled app list (" + getSavedDisabledPackages(activity).size() + " apps) for one-tap Restore.\n"
                                + "• Flushed package state to disk (sync).",
                                model.title + " için " + safePkgs.size() + " paket başarıyla devre dışı bırakıldı.\n\n"
                                + "• Tek dokunuşla Geri Yükleme için devre dışı bırakılan uygulama listesi ("
                                + getSavedDisabledPackages(activity).size() + " uygulama) kaydedildi.\n"
                                + "• Paket durumu diske yazıldı (sync)."))
                        .setPositiveButton(tr(activity, "Done", "Tamam"), null)
                        .show();
            });
        }, "cleanyourtv-disable").start();
    }

    // ================================================================ UI Flows: View Disabled Apps & Restore

    public static void showSavedDisabledAppsDialog(Activity activity, Runnable onChanged) {
        List<String> saved = getSavedDisabledPackages(activity);
        if (saved.isEmpty()) {
            dialog(activity, tr(activity, "Disabled Apps Record", "Devre Dışı Bırakılan Uygulamalar Kaydı"))
                    .setMessage(tr(activity,
                            "No disabled app list has been saved by CleanYourTV yet.\n\n"
                            + "When you run Start Debloat, every disabled package is recorded here so you can inspect or re-enable individual apps or restore all of them.",
                            "CleanYourTV tarafından henüz kaydedilmiş devre dışı uygulama listesi yok.\n\n"
                            + "Debloat Başlat'ı çalıştırdığınızda devre dışı bırakılan her paket buraya kaydedilir; böylece uygulamaları tek tek veya topluca geri yükleyebilirsiniz."))
                    .setPositiveButton(tr(activity, "OK", "Tamam"), null)
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
            String st = enabledNow ? tr(activity, " [Enabled]", " [Etkin]") : tr(activity, " [Disabled]", " [Devre dışı]");
            rows.add(new SpacedRowItem(label + st, pkg, enabledNow));
        }

        ArrayAdapter<SpacedRowItem> adapter = createSpacedAdapter(activity, rows);
        AlertDialog dlg = dialog(activity, tr(activity,
                "Disabled Apps (" + saved.size() + " — Tap an app to re-enable)",
                "Devre Dışı Uygulamalar (" + saved.size() + " — Etkinleştirmek için dokunun)"))
                .setAdapter(adapter, (d, which) -> {
                    if (which >= 0 && which < saved.size()) {
                        String pkg = saved.get(which);
                        enablePackagesBatch(activity, Collections.singletonList(pkg), false, onChanged);
                    }
                })
                .setPositiveButton(tr(activity, "Restore All", "Tümünü Geri Yükle"), (d, w) -> {
                    startRestoreFlow(activity, onChanged);
                })
                .setNegativeButton(tr(activity, "Close", "Kapat"), null)
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
            String msg = tr(activity,
                    "Found saved disabled-app data (" + saved.size() + " packages).\n\n"
                    + "Re-enable all " + saved.size() + " saved packages over ADB?",
                    "Kaydedilmiş devre dışı uygulama verisi bulundu (" + saved.size() + " paket).\n\n"
                    + "Kaydedilen " + saved.size() + " paketin tamamı ADB üzerinden yeniden etkinleştirilsin mi?");

            AlertDialog.Builder b = dialog(activity, tr(activity, "Restore Debloated Apps", "Uygulamaları Geri Yükle"))
                    .setMessage(msg)
                    .setPositiveButton(tr(activity, "Restore Saved (" + saved.size() + ")", "Kaydedilenleri Yükle (" + saved.size() + ")"),
                            (d, w) -> enablePackagesBatch(activity, saved, true, onChanged))
                    .setNegativeButton(tr(activity, "Cancel", "İptal"), null);

            if (model != null && !model.packages.isEmpty()) {
                b.setNeutralButton(tr(activity, "Restore Model List (" + model.packages.size() + ")",
                        "Model Listesini Yükle (" + model.packages.size() + ")"), (d, w) -> {
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
            AlertDialog dlg = dialog(activity, tr(activity, "Select TV Model First", "Önce TV Modelini Seçin"))
                    .setMessage(tr(activity,
                            "No saved disabled-app backup was found, and no TV model is selected.\n\n"
                            + "Please select your TV model first so Restore knows which packages to re-enable.",
                            "Kaydedilmiş devre dışı uygulama yedeği bulunamadı ve henüz bir TV modeli seçilmedi.\n\n"
                            + "Hangi paketlerin yeniden etkinleştirileceğini belirlemek için lütfen önce TV modelinizi seçin."))
                    .setPositiveButton(tr(activity, "Choose TV Model…", "TV Modeli Seç…"),
                            (d, w) -> showChooseModelFlow(activity, onChanged, false))
                    .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
                    .create();
            dlg.show();
            styleDialogButtons(activity, dlg);
            return;
        }

        AlertDialog dlg = dialog(activity, tr(activity, "Restore by Selected TV Model", "Seçili TV Modeline Göre Geri Yükle"))
                .setMessage(tr(activity,
                        "No saved disabled-app list was found.\n\n"
                        + "Restore will use your selected TV model (" + model.title + ") and re-enable its "
                        + model.packages.size() + " packages over ADB.",
                        "Kaydedilmiş devre dışı uygulama listesi bulunamadı.\n\n"
                        + "Geri yükleme işlemi seçili TV modelinizi (" + model.title + ") kullanacak ve "
                        + model.packages.size() + " paketi ADB üzerinden yeniden etkinleştirecek."))
                .setPositiveButton(tr(activity, "Restore (" + model.packages.size() + " pkgs)",
                        "Geri Yükle (" + model.packages.size() + " paket)"),
                        (d, w) -> enablePackagesBatch(activity, model.packages, true, onChanged))
                .setNegativeButton(tr(activity, "Cancel", "İptal"), null)
                .create();
        dlg.show();
        styleDialogButtons(activity, dlg);
    }

    private static void enablePackagesBatch(Activity activity, List<String> packagesToEnable,
                                            boolean clearRecordOnSuccess, Runnable onChanged) {
        if (packagesToEnable == null || packagesToEnable.isEmpty()) return;

        AlertDialog progress = dialog(activity, tr(activity, "Restoring Apps…", "Uygulamalar Geri Yükleniyor…"))
                .setMessage(tr(activity,
                        "Re-enabling " + packagesToEnable.size() + " packages over ADB…",
                        packagesToEnable.size() + " paket ADB üzerinden yeniden etkinleştiriliyor…"))
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
                            progress.setMessage(tr(activity,
                                    "Enabling (" + idx + "/" + Math.max(1, total - 1) + "):\n" + pkg,
                                    "Etkinleştiriliyor (" + idx + "/" + Math.max(1, total - 1) + "):\n" + pkg));
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
                    dialog(activity, tr(activity, "ADB Connection Required", "ADB Bağlantısı Gerekli"))
                            .setMessage(tr(activity,
                                    "Could not connect to local ADB on port 5555.",
                                    "Port 5555 üzerinden yerel ADB'ye bağlanılamadı."))
                            .setPositiveButton(tr(activity, "OK", "Tamam"), null)
                            .show();
                    return;
                }

                dialog(activity, tr(activity, "Restore Complete", "Geri Yükleme Tamamlandı"))
                        .setMessage(tr(activity,
                                "Re-enabled " + enabledCount + " packages on this TV and flushed state to disk (sync).",
                                "Bu TV'de " + enabledCount + " paket yeniden etkinleştirildi ve durum diske yazıldı (sync)."))
                        .setPositiveButton(tr(activity, "Done", "Tamam"), null)
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
