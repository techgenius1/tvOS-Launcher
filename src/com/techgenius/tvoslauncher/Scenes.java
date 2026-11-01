package com.techgenius.tvoslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.View;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Couch-TV multi-layer background scenes with giant refractive glass clock,
 * foreground depth cutouts (mountains, volcano, fog hills, rainforest canopy),
 * waterline reflection, date/weather header, and Look & Scenes settings state.
 */
public final class Scenes {

    public static final String LOOK_SCENES = "scenes";
    public static final String LOOK_CLASSIC = "classic";
    public static final String CUSTOM_ID = "custom";
    public static final int MAX_CUSTOM_SCENES = 5;
    public static final String[] CUSTOM_IDS = {"custom", "custom_2", "custom_3", "custom_4", "custom_5"};
    public static final String FOLDER_SHUFFLE_ID = "folder_shuffle";
    public static final String LOCK_CUSTOM_ID = "lock_custom";
    public static final String LOCK_FOLDER_SHUFFLE_ID = "lock_folder_shuffle";

    public static final String CLOCK_POS_LEFT = "left";
    public static final String CLOCK_POS_CENTER = "center";
    public static final String CLOCK_POS_RIGHT = "right";
    public static final String[] CLOCK_POSITIONS = {CLOCK_POS_LEFT, CLOCK_POS_CENTER, CLOCK_POS_RIGHT};

    public static final String CLOCK_STYLE_DUO = "duo";
    public static final String CLOCK_STYLE_ROUNDED = "rounded";
    public static final String CLOCK_STYLE_TALL = "tall";
    public static final String[] CLOCK_STYLES = {CLOCK_STYLE_DUO, CLOCK_STYLE_ROUNDED, CLOCK_STYLE_TALL};

    public static final int[] STAY_OPTIONS = {10, 15, 20, 30, 45, 60, 120};
    public static final int[] LOCK_IDLE_OPTIONS = {0, 2, 5, 10, 15, 30, 60};

    public static final class Spec {
        public final String id;
        public final String nameEn;
        public final String nameTr;
        public final String bgAsset;
        public final String glassAsset;
        public final String fgAsset; // null for water (uses waterline reflection instead)
        public final boolean waterSplit;
        public final boolean leftAlign;
        public final boolean darkClock;
        public final float clockTopU;
        public final float clockSizeU;
        public final float clockLeftU;
        public final float dateTopU;
        public final float dateLeftU;
        public final boolean darkDate;

        Spec(String id, String nameEn, String nameTr,
             String bgAsset, String glassAsset, String fgAsset,
             boolean waterSplit, boolean leftAlign, boolean darkClock,
             float clockTopU, float clockSizeU, float clockLeftU,
             float dateTopU, float dateLeftU, boolean darkDate) {
            this.id = id;
            this.nameEn = nameEn;
            this.nameTr = nameTr;
            this.bgAsset = bgAsset;
            this.glassAsset = glassAsset;
            this.fgAsset = fgAsset;
            this.waterSplit = waterSplit;
            this.leftAlign = leftAlign;
            this.darkClock = darkClock;
            this.clockTopU = clockTopU;
            this.clockSizeU = clockSizeU;
            this.clockLeftU = clockLeftU;
            this.dateTopU = dateTopU;
            this.dateLeftU = dateLeftU;
            this.darkDate = darkDate;
        }

        public String title(Context c) {
            return I18n.s(c, nameEn, nameTr);
        }
    }

    public static final Spec[] ALL = {
        new Spec("night", "Night", "Gece",
                "backgrounds/scene-night.jpg", "backgrounds/scene-night-glass.jpg", "backgrounds/scene-night-fg.webp",
                false, false, false,
                62f, 178f, 0f,
                42f, 0f, false),
        new Spec("golden", "Golden hour", "Altın saat",
                "backgrounds/scene-golden.jpg", "backgrounds/scene-golden-glass.jpg", "backgrounds/scene-golden-fg.webp",
                false, false, false,
                76f, 195f, 0f,
                53f, 0f, false),
        new Spec("water", "Waterline", "Su çizgisi",
                "backgrounds/scene-water.jpg", "backgrounds/scene-water-glass.jpg", null,
                true, false, false,
                134f, 210f, 0f,
                108f, 0f, false),
        new Spec("fog", "Fog", "Sis",
                "backgrounds/scene-fog.jpg", "backgrounds/scene-fog-glass.jpg", "backgrounds/scene-fog-fg.webp",
                false, true, true,
                86f, 235f, 48f,
                58f, 52f, true),
        new Spec("dawn", "Rainforest dawn", "Yağmur ormanı şafağı",
                "backgrounds/scene-dawn.jpg", "backgrounds/scene-dawn-glass.jpg", "backgrounds/scene-dawn-fg.webp",
                false, false, false,
                115f, 200f, 0f,
                88f, 0f, false)
    };

    public static final Spec CUSTOM_SPEC = new Spec(
            CUSTOM_ID, "Custom", "Özel",
            null, null, null,
            false, false, false,
            68f, 190f, 48f,
            45f, 52f, false);

    public static final Spec[] CUSTOM_SPECS = {
        CUSTOM_SPEC,
        new Spec("custom_2", "Custom 2", "Özel 2", null, null, null, false, false, false, 68f, 190f, 48f, 45f, 52f, false),
        new Spec("custom_3", "Custom 3", "Özel 3", null, null, null, false, false, false, 68f, 190f, 48f, 45f, 52f, false),
        new Spec("custom_4", "Custom 4", "Özel 4", null, null, null, false, false, false, 68f, 190f, 48f, 45f, 52f, false),
        new Spec("custom_5", "Custom 5", "Özel 5", null, null, null, false, false, false, 68f, 190f, 48f, 45f, 52f, false)
    };

    public static final Spec FOLDER_SHUFFLE_SPEC = new Spec(
            FOLDER_SHUFFLE_ID, "Folder Shuffle", "Klasör Karıştırma",
            null, null, null,
            false, false, false,
            68f, 190f, 48f,
            45f, 52f, false);

    public static final Spec LOCK_CUSTOM_SPEC = new Spec(
            LOCK_CUSTOM_ID, "Custom Lock Image", "Özel Kilit Görseli",
            null, null, null,
            false, false, false,
            68f, 190f, 48f,
            45f, 52f, false);

    public static final Spec LOCK_FOLDER_SHUFFLE_SPEC = new Spec(
            LOCK_FOLDER_SHUFFLE_ID, "Folder Shuffle", "Klasör Karıştırma",
            null, null, null,
            false, false, false,
            68f, 190f, 48f,
            45f, 52f, false);

    public static final String[][] CLASSIC_GRADIENTS = {
        {"grad:aurora", "Aurora", "Kuzey Işığı"},
        {"grad:midnight", "Midnight", "Gece Yarısı"},
        {"grad:ocean", "Ocean", "Okyanus"},
        {"grad:sunset", "Sunset", "Gün Batımı"},
        {"grad:graphite", "Graphite", "Grafit"}
    };

    public static String customId(int slot) {
        int s = Math.max(1, Math.min(MAX_CUSTOM_SCENES, slot));
        return CUSTOM_IDS[s - 1];
    }

    public static int customSlotFromId(String id) {
        if (id == null) return 0;
        for (int i = 0; i < CUSTOM_IDS.length; i++) {
            if (CUSTOM_IDS[i].equals(id)) return i + 1;
        }
        return 0;
    }

    public static boolean isCustomId(String id) {
        if (id == null) return false;
        String base = baseSceneId(id);
        return customSlotFromId(base) > 0
                || FOLDER_SHUFFLE_ID.equals(base)
                || LOCK_CUSTOM_ID.equals(base)
                || LOCK_FOLDER_SHUFFLE_ID.equals(base)
                || base.startsWith("photo:")
                || base.startsWith("grad:");
    }

    public static String baseSceneId(String id) {
        if (id == null) return "";
        int hash = id.indexOf('#');
        return hash >= 0 ? id.substring(0, hash) : id;
    }

    public static final String[][] CLASSIC_PHOTOS = {
        {"tessellated-pavement.jpg", "Tessellated Pavement", "Mozaik Kıyı"},
        {"grose-valley.jpg", "Grose Valley", "Grose Vadisi"},
        {"albury-storm.jpg", "Albury Storm", "Albury Fırtınası"},
        {"aresquiers-beach.jpg", "Aresquiers Beach", "Aresquiers Plajı"},
        {"vang-vieng-sunset.jpg", "Vang Vieng Sunset", "Vang Vieng Gün Batımı"}
    };

    private Scenes() { }

    public static Spec byId(String rawId) {
        String id = baseSceneId(rawId);
        int cSlot = customSlotFromId(id);
        if (cSlot > 0) return CUSTOM_SPECS[cSlot - 1];
        if (FOLDER_SHUFFLE_ID.equals(id)) return FOLDER_SHUFFLE_SPEC;
        if (LOCK_CUSTOM_ID.equals(id)) return LOCK_CUSTOM_SPEC;
        if (LOCK_FOLDER_SHUFFLE_ID.equals(id)) return LOCK_FOLDER_SHUFFLE_SPEC;
        if (id.startsWith("photo:")) {
            String file = id.substring("photo:".length());
            for (String[] row : CLASSIC_PHOTOS) {
                if (row[0].equals(file)) {
                    return new Spec(id, row[1], row[2], "backgrounds/" + file, null, null,
                            false, false, false, 68f, 190f, 48f, 45f, 52f, false);
                }
            }
        }
        if (id.startsWith("grad:")) {
            for (String[] row : CLASSIC_GRADIENTS) {
                if (row[0].equals(id)) {
                    return new Spec(id, row[1], row[2], null, null, null,
                            false, false, false, 68f, 190f, 48f, 45f, 52f, false);
                }
            }
        }
        for (Spec s : ALL) {
            if (s.id.equals(id)) return s;
        }
        return ALL[0];
    }

    public static Bitmap createGradientBitmap(String gradId, int w, int h) {
        int[] cols;
        if ("grad:midnight".equals(gradId)) {
            cols = new int[]{0xFF07080F, 0xFF0E1326, 0xFF06070B};
        } else if ("grad:ocean".equals(gradId)) {
            cols = new int[]{0xFF051824, 0xFF0B354D, 0xFF071B2B};
        } else if ("grad:sunset".equals(gradId)) {
            cols = new int[]{0xFF230E24, 0xFF3E1828, 0xFF150E1E};
        } else if ("grad:graphite".equals(gradId)) {
            cols = new int[]{0xFF141519, 0xFF22242B, 0xFF0D0E11};
        } else {
            cols = new int[]{0xFF0E1629, 0xFF1E1535, 0xFF0B1C28};
        }
        Bitmap bmp = Bitmap.createBitmap(Math.max(64, w), Math.max(64, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, cols, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        return bmp;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static String getLook(Context c) {
        return prefs(c).getString("look_mode", LOOK_SCENES);
    }

    public static boolean isScenesLook(Context c) {
        return LOOK_SCENES.equals(getLook(c));
    }

    public static void setLook(Context c, String look) {
        prefs(c).edit().putString("look_mode", LOOK_CLASSIC.equals(look) ? LOOK_CLASSIC : LOOK_SCENES).apply();
    }

    public static int getDockScalePercent(Context c) {
        if (c == null) return 100;
        int pct = prefs(c).getInt("dock_scale_pct", 100);
        return Math.max(50, Math.min(150, pct));
    }

    public static void setDockScalePercent(Context c, int pct) {
        if (c == null) return;
        int clamped = Math.max(50, Math.min(150, pct));
        prefs(c).edit().putInt("dock_scale_pct", clamped).apply();
    }

    public static float getDockScaleFactor(Context c) {
        return getDockScalePercent(c) / 100f;
    }

    public static String dockScaleLabel(Context c) {
        int pct = getDockScalePercent(c);
        int diff = pct - 100;
        if (diff == 0) {
            return "100% (" + I18n.s(c, "Default", "Varsayılan") + ")";
        } else if (diff > 0) {
            return pct + "% (+" + diff + "%)";
        } else {
            return pct + "% (" + diff + "%)";
        }
    }

    public static List<String> getSceneOrder(Context c) {
        String raw = prefs(c).getString("scene_order", "night,golden,water,fog,dawn,custom,custom_2,custom_3,custom_4,custom_5,folder_shuffle");
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (!p.isEmpty() && byId(p).id.equals(p) && !out.contains(p)) {
                out.add(p);
            }
        }
        for (Spec s : ALL) {
            if (!out.contains(s.id)) out.add(s.id);
        }
        for (String cid : CUSTOM_IDS) {
            if (!out.contains(cid)) out.add(cid);
        }
        if (!out.contains(FOLDER_SHUFFLE_ID)) {
            out.add(FOLDER_SHUFFLE_ID);
        }
        return out;
    }

    public static void setSceneOrder(Context c, List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i));
        }
        prefs(c).edit().putString("scene_order", sb.toString()).apply();
    }

    public static List<String> getEnabledSceneIds(Context c) {
        String raw = prefs(c).getString("scene_enabled", "night,golden,water,fog,dawn");
        List<String> enabled = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            int cSlot = customSlotFromId(p);
            if (cSlot > 0 && !CustomMedia.hasCustomSceneWallpaper(c, cSlot)) {
                continue;
            }
            if (FOLDER_SHUFFLE_ID.equals(p) && !CustomMedia.hasWallpaperShuffleFolder(c, CustomMedia.SCOPE_SCENE)) {
                continue;
            }
            if (!p.isEmpty() && byId(p).id.equals(p) && !enabled.contains(p)) {
                enabled.add(p);
            }
        }
        if (enabled.isEmpty()) enabled.add("night");
        // Order according to scene_order
        List<String> ordered = new ArrayList<>();
        for (String id : getSceneOrder(c)) {
            if (enabled.contains(id)) ordered.add(id);
        }
        if (ordered.isEmpty()) ordered.add("night");
        return ordered;
    }

    public static boolean isSceneEnabled(Context c, String id) {
        int cSlot = customSlotFromId(id);
        if (cSlot > 0 && !CustomMedia.hasCustomSceneWallpaper(c, cSlot)) return false;
        if (FOLDER_SHUFFLE_ID.equals(id) && !CustomMedia.hasWallpaperShuffleFolder(c, CustomMedia.SCOPE_SCENE)) return false;
        return getEnabledSceneIds(c).contains(id);
    }

    public static boolean isAnyCustomSceneEnabled(Context c) {
        List<String> enabled = getEnabledSceneIds(c);
        for (String cid : CUSTOM_IDS) {
            if (enabled.contains(cid)) return true;
        }
        return enabled.contains(FOLDER_SHUFFLE_ID);
    }

    public static void enableFolderShuffleScene(Context c, boolean enable) {
        List<String> enabled = new ArrayList<>(getEnabledSceneIds(c));
        if (enable) {
            if (!enabled.contains(FOLDER_SHUFFLE_ID)) enabled.add(FOLDER_SHUFFLE_ID);
            prefs(c).edit().putString("prefer_scene_id", FOLDER_SHUFFLE_ID).apply();
        } else {
            if (enabled.size() <= 1 && enabled.contains(FOLDER_SHUFFLE_ID)) return;
            enabled.remove(FOLDER_SHUFFLE_ID);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enabled.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(enabled.get(i));
        }
        prefs(c).edit().putString("scene_enabled", sb.toString()).apply();
    }

    public static void enableCustomScene(Context c, boolean enable) {
        enableCustomScene(c, 1, enable);
    }

    public static void enableCustomScene(Context c, int slot, boolean enable) {
        String cid = customId(slot);
        List<String> enabled = new ArrayList<>(getEnabledSceneIds(c));
        if (enable) {
            if (!enabled.contains(cid)) enabled.add(cid);
            prefs(c).edit().putString("prefer_scene_id", cid).apply();
        } else {
            if (enabled.size() <= 1 && enabled.contains(cid)) return;
            enabled.remove(cid);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enabled.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(enabled.get(i));
        }
        prefs(c).edit().putString("scene_enabled", sb.toString()).apply();
    }

    public static String getCustomClockPos(Context c) {
        String pos = prefs(c).getString("custom_clock_pos", CLOCK_POS_CENTER);
        if (CLOCK_POS_LEFT.equals(pos) || CLOCK_POS_RIGHT.equals(pos)) return pos;
        return CLOCK_POS_CENTER;
    }

    public static void setCustomClockPos(Context c, String pos) {
        prefs(c).edit().putString("custom_clock_pos", pos).apply();
    }

    public static String cycleCustomClockPos(Context c) {
        String cur = getCustomClockPos(c);
        String next = CLOCK_POS_LEFT;
        for (int i = 0; i < CLOCK_POSITIONS.length; i++) {
            if (CLOCK_POSITIONS[i].equals(cur)) {
                next = CLOCK_POSITIONS[(i + 1) % CLOCK_POSITIONS.length];
                break;
            }
        }
        setCustomClockPos(c, next);
        return next;
    }

    public static String customClockPosLabel(Context c) {
        String pos = getCustomClockPos(c);
        if (CLOCK_POS_LEFT.equals(pos)) return I18n.s(c, "Left", "Sol");
        if (CLOCK_POS_RIGHT.equals(pos)) return I18n.s(c, "Right", "Sağ");
        return I18n.s(c, "Center", "Orta");
    }

    /** Returns false if trying to disable the last remaining active scene. */
    public static boolean toggleScene(Context c, String id) {
        List<String> enabled = new ArrayList<>(getEnabledSceneIds(c));
        if (enabled.contains(id)) {
            if (enabled.size() <= 1) return false;
            enabled.remove(id);
        } else {
            enabled.add(id);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enabled.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(enabled.get(i));
        }
        prefs(c).edit().putString("scene_enabled", sb.toString()).apply();
        return true;
    }

    // ─── Lock Screen Settings (Wallpapers, Clock Position, Idle Timer) ──────

    public static List<String> getAllLockSceneCandidates() {
        List<String> all = new ArrayList<>();
        for (Spec s : ALL) all.add(s.id);
        for (String[] row : CLASSIC_PHOTOS) all.add("photo:" + row[0]);
        for (String[] row : CLASSIC_GRADIENTS) all.add(row[0]);
        for (String cid : CUSTOM_IDS) all.add(cid);
        all.add(LOCK_CUSTOM_ID);
        all.add(LOCK_FOLDER_SHUFFLE_ID);
        return all;
    }

    public static List<String> getEnabledLockSceneIds(Context c) {
        String defaultLock = "night,golden,water,fog,dawn,photo:tessellated-pavement.jpg,photo:grose-valley.jpg,photo:albury-storm.jpg,photo:aresquiers-beach.jpg,photo:vang-vieng-sunset.jpg";
        String raw = prefs(c).getString("lock_scene_enabled", defaultLock);
        List<String> enabled = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int cSlot = customSlotFromId(p);
            if (cSlot > 0 && !CustomMedia.hasCustomSceneWallpaper(c, cSlot)) continue;
            if (LOCK_CUSTOM_ID.equals(p) && !CustomMedia.hasCustomLockWallpaper(c)) continue;
            if (LOCK_FOLDER_SHUFFLE_ID.equals(p)
                    && !CustomMedia.hasWallpaperShuffleFolder(c, CustomMedia.SCOPE_LOCK)) continue;
            if (!enabled.contains(p)) {
                enabled.add(p);
            }
        }
        if (enabled.isEmpty()) enabled.add("night");
        return enabled;
    }

    public static boolean isLockSceneEnabled(Context c, String id) {
        int cSlot = customSlotFromId(id);
        if (cSlot > 0 && !CustomMedia.hasCustomSceneWallpaper(c, cSlot)) return false;
        if (LOCK_CUSTOM_ID.equals(id) && !CustomMedia.hasCustomLockWallpaper(c)) return false;
        if (LOCK_FOLDER_SHUFFLE_ID.equals(id)
                && !CustomMedia.hasWallpaperShuffleFolder(c, CustomMedia.SCOPE_LOCK)) return false;
        return getEnabledLockSceneIds(c).contains(id);
    }

    public static boolean toggleLockScene(Context c, String id) {
        List<String> enabled = new ArrayList<>(getEnabledLockSceneIds(c));
        if (enabled.contains(id)) {
            if (enabled.size() <= 1) return false;
            enabled.remove(id);
        } else {
            enabled.add(id);
        }
        saveLockScenes(c, enabled);
        return true;
    }

    public static void enableLockScene(Context c, String id, boolean enable) {
        List<String> enabled = new ArrayList<>(getEnabledLockSceneIds(c));
        if (enable) {
            if (!enabled.contains(id)) enabled.add(id);
            prefs(c).edit().putString("prefer_lock_scene_id", id).apply();
        } else {
            if (enabled.size() <= 1 && enabled.contains(id)) return;
            enabled.remove(id);
        }
        saveLockScenes(c, enabled);
    }

    private static void saveLockScenes(Context c, List<String> enabled) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enabled.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(enabled.get(i));
        }
        prefs(c).edit().putString("lock_scene_enabled", sb.toString()).apply();
    }

    public static String getLockClockPos(Context c) {
        String pos = prefs(c).getString("lock_clock_pos", CLOCK_POS_CENTER);
        if (CLOCK_POS_LEFT.equals(pos) || CLOCK_POS_RIGHT.equals(pos)) return pos;
        return CLOCK_POS_CENTER;
    }

    public static void setLockClockPos(Context c, String pos) {
        prefs(c).edit().putString("lock_clock_pos", pos).apply();
    }

    public static String cycleLockClockPos(Context c) {
        String cur = getLockClockPos(c);
        String next = CLOCK_POS_LEFT;
        for (int i = 0; i < CLOCK_POSITIONS.length; i++) {
            if (CLOCK_POSITIONS[i].equals(cur)) {
                next = CLOCK_POSITIONS[(i + 1) % CLOCK_POSITIONS.length];
                break;
            }
        }
        setLockClockPos(c, next);
        return next;
    }

    public static String lockClockPosLabel(Context c) {
        String pos = getLockClockPos(c);
        if (CLOCK_POS_LEFT.equals(pos)) return I18n.s(c, "Left", "Sol");
        if (CLOCK_POS_RIGHT.equals(pos)) return I18n.s(c, "Right", "Sağ");
        return I18n.s(c, "Center", "Orta");
    }

    public static int getLockIdleMinutes(Context c) {
        return Math.max(0, prefs(c).getInt("lock_idle_minutes", 0));
    }

    public static void setLockIdleMinutes(Context c, int minutes) {
        prefs(c).edit().putInt("lock_idle_minutes", Math.max(0, minutes)).apply();
    }

    public static int cycleLockIdleMinutes(Context c) {
        int cur = getLockIdleMinutes(c);
        int next = LOCK_IDLE_OPTIONS[0];
        for (int i = 0; i < LOCK_IDLE_OPTIONS.length; i++) {
            if (LOCK_IDLE_OPTIONS[i] == cur) {
                next = LOCK_IDLE_OPTIONS[(i + 1) % LOCK_IDLE_OPTIONS.length];
                break;
            }
        }
        setLockIdleMinutes(c, next);
        return next;
    }

    public static String lockIdleLabel(Context c) {
        int m = getLockIdleMinutes(c);
        if (m <= 0) return I18n.s(c, "Off", "Kapalı");
        return m + " " + I18n.s(c, "min", "dk");
    }

    public static int getStaySeconds(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("scene_seconds_default_60", false)) {
            SharedPreferences.Editor ed = p.edit().putBoolean("scene_seconds_default_60", true);
            if (!p.contains("scene_seconds") || p.getInt("scene_seconds", 20) == 20) {
                ed.putInt("scene_seconds", 60);
            }
            ed.apply();
        }
        int s = p.getInt("scene_seconds", 60);
        return s >= 5 ? s : 60;
    }

    public static int cycleStaySeconds(Context c) {
        int cur = getStaySeconds(c);
        int next = STAY_OPTIONS[0];
        for (int i = 0; i < STAY_OPTIONS.length; i++) {
            if (STAY_OPTIONS[i] == cur) {
                next = STAY_OPTIONS[(i + 1) % STAY_OPTIONS.length];
                break;
            }
        }
        prefs(c).edit().putInt("scene_seconds", next).apply();
        return next;
    }

    public static boolean isClock24h(Context c) {
        return prefs(c).getBoolean("clock_24h", false);
    }

    public static void setClock24h(Context c, boolean is24h) {
        prefs(c).edit().putBoolean("clock_24h", is24h).apply();
    }

    public static String getClockStyle(Context c) {
        return prefs(c).getString("clock_style", CLOCK_STYLE_DUO);
    }

    public static void setClockStyle(Context c, String style) {
        prefs(c).edit().putString("clock_style", style).apply();
    }

    public static String cycleClockStyle(Context c) {
        String cur = getClockStyle(c);
        String next = CLOCK_STYLES[0];
        for (int i = 0; i < CLOCK_STYLES.length; i++) {
            if (CLOCK_STYLES[i].equals(cur)) {
                next = CLOCK_STYLES[(i + 1) % CLOCK_STYLES.length];
                break;
            }
        }
        setClockStyle(c, next);
        return next;
    }

    public static String clockStyleLabel(Context c) {
        String st = getClockStyle(c);
        if (CLOCK_STYLE_ROUNDED.equals(st)) return I18n.s(c, "iPhone Rounded", "iPhone Yuvarlak");
        if (CLOCK_STYLE_TALL.equals(st)) return I18n.s(c, "Tall Glass", "Uzun Cam");
        return I18n.s(c, "iPhone Duo", "iPhone Duo");
    }

    private static InputStream openAssetStream(Context c, String assetPath) throws java.io.IOException {
        try {
            return c.getAssets().open(assetPath);
        } catch (java.io.IOException e) {
            return c.getAssets().open(assetPath.replace('/', '\\'));
        }
    }

    public static Bitmap loadAssetBitmap(Context c, String assetPath, int reqW, int reqH) {
        if (assetPath == null) return null;
        try (InputStream isBounds = openAssetStream(c, assetPath)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(isBounds, null, opts);
            opts.inSampleSize = 1;
            while (opts.outWidth / (opts.inSampleSize * 2) >= reqW
                    && opts.outHeight / (opts.inSampleSize * 2) >= reqH) {
                opts.inSampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream is = openAssetStream(c, assetPath)) {
                return BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Multi-layer Scene View that renders the active scene (and crossfades to the next scene),
     * including background, shade gradients, refractive glass clock, waterline reflection,
     * foreground mountain/tree cutouts, and the date + weather line.
     */
    public static final class SceneView extends View {
        public interface OnSceneChangedListener {
            void onSceneChanged(Bitmap currentBgBitmap);
        }

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Map<String, LoadedScene> cache = new HashMap<>();
        private List<String> activeIds = new ArrayList<>();
        private int activeIdx = 0;
        private String currentId = "night";
        private String prevId = null;
        private float crossfade = 1f;
        private ValueAnimator fadeAnim;
        private OnSceneChangedListener listener;

        private String weatherTemp;
        private String weatherDesc;
        private int weatherCode = -1;
        private boolean lockScreenMode = false;
        private int folderFlipCounter = 0;

        private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint clockFillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint clockWetPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint clockFrostPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint clockStrokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint clockShadowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint datePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint greetPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Rect srcRect = new Rect();
        private final Rect digitBounds = new Rect();
        private final RectF dstRect = new RectF();
        private final Matrix shaderMatrix = new Matrix();

        private Bitmap lastNotifiedBg;
        private Bitmap cachedFrameBmp;
        private String cachedFrameKey;
        private String lastTickSignature;

        private final Runnable rotateTick = new Runnable() {
            @Override
            public void run() {
                advanceScene();
            }
        };

        private final Runnable clockTick = new Runnable() {
            @Override
            public void run() {
                Context ctx = getContext();
                String sig = formatTime(ctx) + "|" + buildGreeting(ctx);
                if (!sig.equals(lastTickSignature)) {
                    lastTickSignature = sig;
                    invalidate();
                }
                long now = System.currentTimeMillis();
                long delay = 15000L - (now % 15000L);
                handler.postDelayed(this, Math.max(1000L, delay));
            }
        };

        private static final class LoadedScene {
            final Spec spec;
            long customTs;
            boolean ownsBg = true;
            Bitmap bg;
            Bitmap fg;
            Bitmap glassBaked;
            Bitmap glassWetBaked;
            BitmapShader glassShader;
            BitmapShader glassWetShader;

            LoadedScene(Spec spec) {
                this.spec = spec;
            }

            void recycle() {
                if (ownsBg && bg != null && !bg.isRecycled()) bg.recycle();
                if (fg != null && !fg.isRecycled()) fg.recycle();
                if (glassBaked != null && !glassBaked.isRecycled()) glassBaked.recycle();
                if (glassWetBaked != null && !glassWetBaked.isRecycled()) glassWetBaked.recycle();
                bg = null;
                fg = null;
                glassBaked = null;
                glassWetBaked = null;
                glassShader = null;
                glassWetShader = null;
            }
        }

        public SceneView(Context c) {
            super(c);
            bmpPaint.setDither(true);
            clockFillPaint.setStyle(Paint.Style.FILL);
            clockWetPaint.setStyle(Paint.Style.FILL);
            clockFrostPaint.setStyle(Paint.Style.FILL);

            clockStrokePaint.setStyle(Paint.Style.STROKE);
            clockStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            clockStrokePaint.setStrokeJoin(Paint.Join.ROUND);

            clockShadowPaint.setStyle(Paint.Style.FILL);

            datePaint.setTypeface(Ui.font(c, "semibold"));
            greetPaint.setTypeface(Ui.font(c, "semibold"));
            if (isScenesLook(c)) {
                syncSettings();
            }
        }

        public void setOnSceneChangedListener(OnSceneChangedListener l) {
            this.listener = l;
        }

        public void setWeather(String temp, String desc, int code) {
            boolean changed = !java.util.Objects.equals(this.weatherTemp, temp)
                    || !java.util.Objects.equals(this.weatherDesc, desc)
                    || this.weatherCode != code;
            this.weatherTemp = temp;
            this.weatherDesc = desc;
            this.weatherCode = code;
            if (changed) invalidate();
        }

        public void setLockScreenMode(boolean lockMode) {
            if (this.lockScreenMode != lockMode) {
                this.lockScreenMode = lockMode;
                this.cachedFrameKey = null;
                syncSettings();
            }
        }

        public boolean isLockScreenMode() {
            return lockScreenMode;
        }

        public void syncSettings() {
            Context c = getContext();
            List<String> enabled = lockScreenMode ? getEnabledLockSceneIds(c) : getEnabledSceneIds(c);
            activeIds = enabled;
            String preferKey = lockScreenMode ? "prefer_lock_scene_id" : "prefer_scene_id";
            String prefer = prefs(c).getString(preferKey, null);
            if (prefer != null) {
                prefs(c).edit().remove(preferKey).apply();
                if (activeIds.contains(prefer)) {
                    currentId = prefer;
                    prevId = null;
                    crossfade = 1f;
                }
            }
            String curBase = baseSceneId(currentId);
            if (!activeIds.contains(curBase)) {
                currentId = activeIds.get(0);
                activeIdx = 0;
                prevId = null;
                crossfade = 1f;
            } else {
                activeIdx = activeIds.indexOf(curBase);
            }
            if (FOLDER_SHUFFLE_ID.equals(baseSceneId(currentId))
                    || LOCK_FOLDER_SHUFFLE_ID.equals(baseSceneId(currentId))) {
                LoadedScene old = cache.remove(currentId);
                if (old != null) old.recycle();
            }
            ensureLoaded(currentId);
            evictUnusedScenes();
            notifySceneBackdrop();
            scheduleNextRotation();
            invalidate();
        }

        private void evictUnusedScenes() {
            java.util.Iterator<Map.Entry<String, LoadedScene>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, LoadedScene> e = it.next();
                String k = e.getKey();
                if (!k.equals(currentId) && !k.equals(prevId)) {
                    LoadedScene ls = e.getValue();
                    if (ls != null) ls.recycle();
                    it.remove();
                }
            }
        }

        public void releaseCache() {
            stop();
            for (LoadedScene ls : cache.values()) {
                if (ls != null) ls.recycle();
            }
            cache.clear();
            if (cachedFrameBmp != null && !cachedFrameBmp.isRecycled()) {
                cachedFrameBmp.recycle();
            }
            cachedFrameBmp = null;
            cachedFrameKey = null;
            lastNotifiedBg = null;
        }

        public Bitmap getCurrentBgBitmap() {
            LoadedScene ls = ensureLoaded(currentId);
            return ls != null ? ls.bg : null;
        }

        private void notifySceneBackdrop() {
            if (listener != null) {
                LoadedScene ls = ensureLoaded(currentId);
                if (ls != null && ls.bg != null && ls.bg != lastNotifiedBg) {
                    lastNotifiedBg = ls.bg;
                    listener.onSceneChanged(ls.bg);
                }
            }
        }

        public void start() {
            handler.removeCallbacks(clockTick);
            handler.post(clockTick);
            scheduleNextRotation();
        }

        public void stop() {
            handler.removeCallbacks(clockTick);
            handler.removeCallbacks(rotateTick);
            if (fadeAnim != null) fadeAnim.cancel();
        }

        private boolean canRotate() {
            if (activeIds.size() > 1) return true;
            for (String id : activeIds) {
                String b = baseSceneId(id);
                if (FOLDER_SHUFFLE_ID.equals(b) || LOCK_FOLDER_SHUFFLE_ID.equals(b)) return true;
            }
            return false;
        }

        private void scheduleNextRotation() {
            handler.removeCallbacks(rotateTick);
            if (canRotate()) {
                handler.postDelayed(rotateTick, getStaySeconds(getContext()) * 1000L);
            }
        }

        private void advanceScene() {
            if (!canRotate()) return;
            prevId = currentId;
            activeIdx = (activeIdx + 1) % activeIds.size();
            String nextBase = activeIds.get(activeIdx);
            if (FOLDER_SHUFFLE_ID.equals(nextBase) || LOCK_FOLDER_SHUFFLE_ID.equals(nextBase)) {
                folderFlipCounter = (folderFlipCounter + 1) % 2;
                currentId = nextBase + "#" + folderFlipCounter;
                LoadedScene stale = cache.remove(currentId);
                if (stale != null) stale.recycle();
            } else {
                currentId = nextBase;
            }
            ensureLoaded(currentId);
            notifySceneBackdrop();

            if (fadeAnim != null) fadeAnim.cancel();
            crossfade = 0f;
            fadeAnim = ValueAnimator.ofFloat(0f, 1f);
            fadeAnim.setDuration(1600L);
            fadeAnim.addUpdateListener(a -> {
                crossfade = (Float) a.getAnimatedValue();
                if (crossfade >= 1f) {
                    prevId = null;
                    evictUnusedScenes();
                }
                invalidate();
            });
            fadeAnim.start();
            scheduleNextRotation();
        }

        private LoadedScene ensureLoaded(String id) {
            Context c = getContext();
            LoadedScene ls = cache.get(id);
            String base = baseSceneId(id);
            int cSlot = customSlotFromId(base);
            if (cSlot > 0) {
                long ts = CustomMedia.getCustomSceneTimestamp(c, cSlot);
                if (ls != null && ls.bg != null && !ls.bg.isRecycled() && ls.customTs == ts) {
                    return ls;
                }
                if (ls != null) ls.recycle();
                ls = new LoadedScene(CUSTOM_SPECS[cSlot - 1]);
                ls.customTs = ts;
                ls.ownsBg = false;
                ls.bg = CustomMedia.getCustomSceneWallpaper(c, cSlot, 1920, 1080);
                if (ls.bg == null) {
                    ls.ownsBg = true;
                    ls.bg = loadAssetBitmap(c, ALL[0].bgAsset, 1920, 1080);
                }
                bakeLoadedGlassFromBg(ls, false);
                cache.put(id, ls);
                return ls;
            }
            if (LOCK_CUSTOM_ID.equals(base)) {
                long ts = CustomMedia.getCustomLockTimestamp(c);
                if (ls != null && ls.bg != null && !ls.bg.isRecycled() && ls.customTs == ts) {
                    return ls;
                }
                if (ls != null) ls.recycle();
                ls = new LoadedScene(LOCK_CUSTOM_SPEC);
                ls.customTs = ts;
                ls.ownsBg = false;
                ls.bg = CustomMedia.getCustomLockWallpaper(c, 1920, 1080);
                if (ls.bg == null) {
                    ls.ownsBg = true;
                    ls.bg = loadAssetBitmap(c, ALL[0].bgAsset, 1920, 1080);
                }
                bakeLoadedGlassFromBg(ls, false);
                cache.put(id, ls);
                return ls;
            }
            if (FOLDER_SHUFFLE_ID.equals(base) || LOCK_FOLDER_SHUFFLE_ID.equals(base)) {
                if (ls != null && ls.bg != null && !ls.bg.isRecycled()) {
                    return ls;
                }
                String scope = LOCK_FOLDER_SHUFFLE_ID.equals(base)
                        ? CustomMedia.SCOPE_LOCK
                        : CustomMedia.SCOPE_SCENE;
                Spec spec = LOCK_FOLDER_SHUFFLE_ID.equals(base) ? LOCK_FOLDER_SHUFFLE_SPEC : FOLDER_SHUFFLE_SPEC;
                ls = new LoadedScene(spec);
                ls.customTs = System.currentTimeMillis();
                ls.ownsBg = true;
                ls.bg = CustomMedia.getNextShuffledFolderBitmap(c, scope, 1920, 1080, true);
                if (ls.bg == null) {
                    ls.bg = loadAssetBitmap(c, ALL[0].bgAsset, 1920, 1080);
                }
                bakeLoadedGlassFromBg(ls, false);
                cache.put(id, ls);
                return ls;
            }
            if (ls != null && ls.bg != null && !ls.bg.isRecycled()) return ls;
            Spec spec = byId(base);
            ls = new LoadedScene(spec);
            if (base.startsWith("grad:")) {
                ls.bg = createGradientBitmap(base, 1920, 1080);
            } else {
                ls.bg = loadAssetBitmap(c, spec.bgAsset, 1920, 1080);
            }
            if (spec.fgAsset != null) {
                ls.fg = loadAssetBitmap(c, spec.fgAsset, 1920, 1080);
            }
            if (spec.glassAsset != null) {
                Bitmap rawGlass = loadAssetBitmap(c, spec.glassAsset, 480, 270);
                if (rawGlass != null) {
                    ls.glassBaked = bakeGlassTexture(rawGlass, spec.darkClock, false);
                    ls.glassShader = new BitmapShader(ls.glassBaked, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    if (spec.waterSplit) {
                        ls.glassWetBaked = bakeGlassTexture(rawGlass, false, true);
                        ls.glassWetShader = new BitmapShader(ls.glassWetBaked, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    }
                    rawGlass.recycle();
                }
            } else {
                bakeLoadedGlassFromBg(ls, spec.darkClock);
            }
            cache.put(id, ls);
            return ls;
        }

        private static void bakeLoadedGlassFromBg(LoadedScene ls, boolean darkClock) {
            if (ls.bg != null) {
                Bitmap rawGlass = Bitmap.createScaledBitmap(ls.bg, 480, 270, true);
                ls.glassBaked = bakeGlassTexture(rawGlass, darkClock, false);
                ls.glassShader = new BitmapShader(ls.glassBaked, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (rawGlass != ls.bg) rawGlass.recycle();
            }
        }

        private static Bitmap bakeGlassTexture(Bitmap src, boolean darkClock, boolean wetBlur) {
            int w = 480, h = 270;
            Bitmap base = Ui.blur(src, w, h, wetBlur ? 4 : 2, 2);
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);

            ColorMatrix cm = new ColorMatrix();
            if (darkClock) {
                // Fog scene: brightness(0.46) saturate(0.9)
                cm.setSaturation(0.9f);
                ColorMatrix scale = new ColorMatrix(new float[]{
                    0.46f, 0, 0, 0, 0,
                    0, 0.46f, 0, 0, 0,
                    0, 0, 0.46f, 0, 0,
                    0, 0, 0, 1f, 0
                });
                cm.postConcat(scale);
            } else {
                // Standard glass clock: brightness(1.08) saturate(1.25)
                cm.setSaturation(1.25f);
                ColorMatrix scale = new ColorMatrix(new float[]{
                    1.08f, 0, 0, 0, 10,
                    0, 1.08f, 0, 0, 8,
                    0, 0, 1.08f, 0, 10,
                    0, 0, 0, 1f, 0
                });
                cm.postConcat(scale);
            }
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            cv.drawBitmap(base, 0, 0, p);
            if (base != src) base.recycle();

            // Specular diagonal glass sheen (160deg)
            Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
            int[] colors = darkClock ? new int[]{
                Color.argb(65, 255, 255, 255),
                Color.argb(28, 255, 255, 255),
                Color.argb(10, 255, 255, 255),
                Color.argb(25, 255, 255, 255)
            } : new int[]{
                Color.argb(68, 255, 255, 255),
                Color.argb(28, 255, 255, 255),
                Color.argb(10, 255, 255, 255),
                Color.argb(26, 255, 255, 255)
            };
            sheen.setShader(new LinearGradient(
                    0, 0, w * 0.45f, h,
                    colors, new float[]{0f, 0.35f, 0.65f, 1f},
                    Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, sheen);
            return out;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            if (prevId != null && crossfade < 1f) {
                LoadedScene prev = ensureLoaded(prevId);
                if (prev != null) {
                    drawScene(canvas, prev, w, h, 1f);
                }
                LoadedScene cur = ensureLoaded(currentId);
                if (cur != null) {
                    drawScene(canvas, cur, w, h, crossfade);
                }
            } else {
                LoadedScene cur = ensureLoaded(currentId);
                if (cur != null) {
                    Context ctx = getContext();
                    int cSlot = Math.max(1, customSlotFromId(baseSceneId(currentId)));
                    String frameKey = currentId + "|" + lockScreenMode + "|" + formatTime(ctx) + "|"
                            + I18n.formatSceneDate(ctx, new Date()) + "|"
                            + (lockScreenMode ? "" : (weatherTemp != null ? weatherTemp : "")) + "|"
                            + (lockScreenMode ? "" : (weatherDesc != null ? weatherDesc : "")) + "|"
                            + (lockScreenMode ? -1 : weatherCode) + "|" + getClockStyle(ctx) + "|"
                            + getCustomClockPos(ctx) + "|" + getLockClockPos(ctx) + "|"
                            + CustomMedia.getCustomSceneTimestamp(ctx, cSlot) + "|"
                            + CustomMedia.getCustomLockTimestamp(ctx) + "|"
                            + buildGreeting(ctx) + "|" + w + "x" + h;
                    if (!frameKey.equals(cachedFrameKey) || cachedFrameBmp == null
                            || cachedFrameBmp.getWidth() != w || cachedFrameBmp.getHeight() != h) {
                        if (cachedFrameBmp == null || cachedFrameBmp.getWidth() != w || cachedFrameBmp.getHeight() != h) {
                            if (cachedFrameBmp != null) cachedFrameBmp.recycle();
                            cachedFrameBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        } else {
                            cachedFrameBmp.eraseColor(Color.TRANSPARENT);
                        }
                        Canvas off = new Canvas(cachedFrameBmp);
                        drawScene(off, cur, w, h, 1f);
                        cachedFrameKey = frameKey;
                    }
                    canvas.drawBitmap(cachedFrameBmp, 0, 0, null);
                }
            }
        }

        private void drawScene(Canvas canvas, LoadedScene ls, int w, int h, float alpha) {
            if (ls.bg == null) return;
            int alphaInt = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            boolean layerSave = alphaInt < 255;
            if (layerSave) {
                canvas.saveLayerAlpha(0, 0, w, h, alphaInt);
            }

            // 1. Background image (center-crop)
            drawCenterCrop(canvas, ls.bg, w, h);

            // 2. Scene shade gradient
            Spec sp = ls.spec;
            if ("water".equals(sp.id)) {
                shadePaint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{Color.argb(56, 14, 22, 40), Color.argb(15, 14, 22, 40), Color.argb(128, 0, 0, 0)},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            } else if ("dawn".equals(sp.id)) {
                shadePaint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{Color.argb(66, 30, 18, 12), Color.argb(20, 30, 18, 12), Color.argb(128, 0, 0, 0)},
                        new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
            } else {
                shadePaint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(128, 0, 0, 0)},
                        new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, w, h, shadePaint);

            // 3. Giant Glass Clock (iPhone Duo / Rounded / Tall Glass)
            float u = Math.min(w / 960f, h / 540f);
            Context ctx = getContext();
            String style = getClockStyle(ctx);
            boolean isDuo = CLOCK_STYLE_DUO.equals(style);
            boolean isRounded = CLOCK_STYLE_ROUNDED.equals(style);

            // Duo uses Bahnschrift Condensed ('wght' 345, 'wdth' 75) with rounded corners matching screen_duo12.png
            android.graphics.Typeface tf = isDuo
                    ? Ui.font(ctx, "clock_duo")
                    : isRounded
                    ? Ui.font(ctx, "clock_rounded")
                    : Ui.font(ctx, "clock");
            clockFillPaint.setTypeface(tf);
            clockWetPaint.setTypeface(tf);
            clockFrostPaint.setTypeface(tf);
            clockStrokePaint.setTypeface(tf);
            clockShadowPaint.setTypeface(tf);

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                String varSettings = isDuo ? "'wght' 345, 'wdth' 75" : null;
                clockFillPaint.setFontVariationSettings(varSettings);
                clockWetPaint.setFontVariationSettings(varSettings);
                clockFrostPaint.setFontVariationSettings(varSettings);
                clockStrokePaint.setFontVariationSettings(varSettings);
                clockShadowPaint.setFontVariationSettings(varSettings);
            }

            android.graphics.PathEffect cornerFx = isDuo
                    ? new android.graphics.CornerPathEffect(3.8f * u)
                    : null;
            clockFillPaint.setPathEffect(cornerFx);
            clockWetPaint.setPathEffect(cornerFx);
            clockFrostPaint.setPathEffect(cornerFx);
            clockStrokePaint.setPathEffect(cornerFx);
            clockShadowPaint.setPathEffect(cornerFx);

            String timeStr = formatTime(ctx);
            float clockPx = (isRounded ? sp.clockSizeU * 0.78f : isDuo ? sp.clockSizeU * 0.84f : sp.clockSizeU) * u;
            float scaleY = isRounded ? 1.24f : isDuo ? 1.24f : 1.0f;
            float letterSp = isDuo ? 0.01f : isRounded ? -0.01f : -0.02f;

            clockFillPaint.setTextSize(clockPx);
            clockFillPaint.setLetterSpacing(letterSp);
            clockWetPaint.setTextSize(clockPx);
            clockWetPaint.setLetterSpacing(letterSp);
            clockFrostPaint.setTextSize(clockPx);
            clockFrostPaint.setLetterSpacing(letterSp);
            clockStrokePaint.setTextSize(clockPx);
            clockStrokePaint.setLetterSpacing(letterSp);
            clockShadowPaint.setTextSize(clockPx);
            clockShadowPaint.setLetterSpacing(letterSp);

            // Compute exact 3-line vertical layout with 1 extra line spacing above Greeting
            // and shifted down slightly so the bottom of the clock sits slightly behind the mountain ridge:
            // Line 1: Greeting ("Good morning, Name")
            // Line 2: Date & Weather
            // Line 3: Clock
            float greetPx = 12.5f * u;
            float datePx = 13.5f * u;
            boolean useUserClockPos = lockScreenMode || isCustomId(sp.id);
            String activeClockPos = lockScreenMode ? getLockClockPos(ctx) : getCustomClockPos(ctx);
            boolean isCustomRight = !lockScreenMode && isCustomId(sp.id) && CLOCK_POS_RIGHT.equals(activeClockPos);
            float topOffsetU = isCustomRight ? 62f : 19f;
            float dateBaselineY = Math.max((46f + topOffsetU) * u, (sp.dateTopU + topOffsetU) * u);
            float greetBaselineY = dateBaselineY - (18.5f * u);

            clockFillPaint.getTextBounds("0847", 0, 4, digitBounds);
            float rawCapH = Math.max(clockPx * 0.68f, -digitBounds.top);
            float visualCapH = rawCapH * scaleY;
            float minClockTopY = dateBaselineY + (14f * u);
            float clockVisualTopY = Math.max((sp.clockTopU + topOffsetU) * u, minClockTopY);
            float baselineY = clockVisualTopY + visualCapH;

            float textW = measureClockWidth(timeStr, clockFillPaint, u, isDuo || isRounded);
            float clockX;
            if (useUserClockPos) {
                if (CLOCK_POS_LEFT.equals(activeClockPos)) {
                    clockX = 48f * u;
                } else if (CLOCK_POS_RIGHT.equals(activeClockPos)) {
                    clockX = w - textW - (48f * u);
                } else {
                    clockX = (w - textW) * 0.5f;
                }
            } else {
                clockX = sp.leftAlign ? (sp.clockLeftU * u) : ((w - textW) * 0.5f);
            }

            if (ls.glassShader != null && ls.glassBaked != null) {
                shaderMatrix.reset();
                shaderMatrix.setScale((float) w / ls.glassBaked.getWidth(), (float) h / ls.glassBaked.getHeight());
                ls.glassShader.setLocalMatrix(shaderMatrix);
                clockFillPaint.setShader(ls.glassShader);
            }

            // Frosted vertical gradient fill + crisp outer rim matching duo.png
            float topGradY = baselineY - visualCapH;
            clockFrostPaint.setShader(new LinearGradient(
                    0, topGradY, 0, baselineY,
                    sp.darkClock
                            ? new int[]{Color.argb(38, 255, 255, 255), Color.argb(68, 255, 255, 255), Color.argb(118, 255, 255, 255)}
                            : new int[]{Color.argb(52, 218, 224, 255), Color.argb(86, 236, 232, 255), Color.argb(138, 255, 242, 252)},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP));

            clockStrokePaint.setStrokeWidth(Math.max(1.7f, (isDuo ? 2.1f : 1.2f) * u));
            clockStrokePaint.setShader(new LinearGradient(
                    0, topGradY, 0, baselineY,
                    sp.darkClock
                            ? new int[]{Color.argb(210, 255, 255, 255), Color.argb(160, 255, 255, 255), Color.argb(230, 255, 255, 255)}
                            : new int[]{Color.argb(228, 244, 248, 255), Color.argb(185, 232, 236, 255), Color.argb(250, 255, 255, 255)},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
            clockShadowPaint.setColor(Color.argb(42, 0, 0, 0));

            if (sp.waterSplit) {
                float waterY = h * 0.55f;
                // Above waterline: sharp glass clock
                canvas.save();
                canvas.clipRect(0, 0, w, waterY);
                drawStylizedClock(canvas, timeStr, clockX, baselineY, scaleY, rawCapH, clockPx, u,
                        isDuo, isRounded, false, ls, sp.darkClock);
                canvas.restore();

                // Below waterline: blurred wet reflection clock
                canvas.save();
                canvas.clipRect(0, waterY, w, h);
                if (ls.glassWetShader != null && ls.glassWetBaked != null) {
                    shaderMatrix.reset();
                    shaderMatrix.setScale((float) w / ls.glassWetBaked.getWidth(), (float) h / ls.glassWetBaked.getHeight());
                    ls.glassWetShader.setLocalMatrix(shaderMatrix);
                    clockWetPaint.setShader(ls.glassWetShader);
                }
                drawStylizedClock(canvas, timeStr, clockX, baselineY, scaleY, rawCapH, clockPx, u,
                        isDuo, isRounded, true, ls, sp.darkClock);
                canvas.restore();
            } else {
                drawStylizedClock(canvas, timeStr, clockX, baselineY, scaleY, rawCapH, clockPx, u,
                        isDuo, isRounded, false, ls, sp.darkClock);
            }

            // 4. Foreground cutout (mountain ridge / volcano / fog hills / rainforest canopy)
            if (ls.fg != null) {
                drawCenterCrop(canvas, ls.fg, w, h);
            }

            // 5. Line 1 (Greeting with person name) + Line 2 (Date & Weather) above the clock
            String greeting = buildGreeting(ctx);
            greetPaint.setTextSize(greetPx);
            greetPaint.setLetterSpacing(0.015f);
            if (sp.darkDate) {
                greetPaint.setColor(Color.argb(185, 22, 32, 29));
                greetPaint.clearShadowLayer();
            } else {
                greetPaint.setColor(Color.argb(215, 240, 242, 248));
                greetPaint.setShadowLayer(7f * u, 0f, 1.5f * u, Color.argb(135, 0, 0, 0));
            }
            float greetW = greetPaint.measureText(greeting);
            float greetX;
            if (useUserClockPos) {
                if (CLOCK_POS_LEFT.equals(activeClockPos)) {
                    greetX = 52f * u;
                } else if (CLOCK_POS_RIGHT.equals(activeClockPos)) {
                    greetX = w - greetW - (52f * u);
                } else {
                    greetX = (w - greetW) * 0.5f;
                }
            } else {
                greetX = sp.leftAlign ? (sp.dateLeftU * u) : ((w - greetW) * 0.5f);
            }
            canvas.drawText(greeting, greetX, greetBaselineY, greetPaint);

            String dateStr = I18n.formatSceneDate(ctx, new Date());
            String fullHeader = dateStr;
            if (weatherTemp != null && !weatherTemp.isEmpty()) {
                String wxDesc = weatherDesc != null && !weatherDesc.isEmpty()
                        ? " " + I18n.t(ctx, weatherDesc) : "";
                fullHeader = dateStr + "   ·   " + weatherTemp + wxDesc;
            }
            datePaint.setTextSize(datePx);
            datePaint.setLetterSpacing(0.01f);
            if (sp.darkDate) {
                datePaint.setColor(Color.argb(225, 22, 32, 29));
                datePaint.clearShadowLayer();
            } else {
                datePaint.setColor(Color.argb(246, 248, 248, 252));
                datePaint.setShadowLayer(8f * u, 0f, 1.5f * u, Color.argb(145, 0, 0, 0));
            }
            float dateW = datePaint.measureText(fullHeader);
            float dateX;
            if (useUserClockPos) {
                if (CLOCK_POS_LEFT.equals(activeClockPos)) {
                    dateX = 52f * u;
                } else if (CLOCK_POS_RIGHT.equals(activeClockPos)) {
                    dateX = w - dateW - (52f * u);
                } else {
                    dateX = (w - dateW) * 0.5f;
                }
            } else {
                dateX = sp.leftAlign ? (sp.dateLeftU * u) : ((w - dateW) * 0.5f);
            }
            canvas.drawText(fullHeader, dateX, dateBaselineY, datePaint);

            if (layerSave) {
                canvas.restore();
            }
        }

        private static String buildGreeting(Context c) {
            int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String greet;
            if (hr >= 5 && hr < 12) {
                greet = I18n.s(c, "Good morning", "Günaydın");
            } else if (hr >= 12 && hr < 18) {
                greet = I18n.s(c, "Good afternoon", "İyi günler");
            } else if (hr >= 18 && hr < 22) {
                greet = I18n.s(c, "Good evening", "İyi akşamlar");
            } else {
                greet = I18n.s(c, "Good night", "İyi geceler");
            }
            try {
                Profiles.Profile cur = Profiles.current(c);
                if (cur != null && cur.name != null) {
                    String name = I18n.profileName(c, cur.name).trim();
                    if (!name.isEmpty()) {
                        return greet + ", " + name;
                    }
                }
            } catch (Exception ignored) { }
            return greet;
        }

        private final android.graphics.Path duoClockPath = new android.graphics.Path();
        private final android.graphics.Path duoSubPath = new android.graphics.Path();
        private final android.graphics.Path duoCharPath = new android.graphics.Path();
        private final Matrix duoScaleMatrix = new Matrix();

        private static final float DUO_SCALE_X = 0.86f;

        private void appendDuoTextPath(TextPaint paint, String str, float destX, float baselineY,
                                       float scaleY, android.graphics.Path outPath) {
            duoSubPath.reset();
            int len = str.length();
            RectF cBounds = new RectF();
            for (int i = 0; i < len; i++) {
                float advX = i == 0 ? 0f : paint.measureText(str, 0, i);
                char ch = str.charAt(i);
                duoCharPath.reset();
                paint.getTextPath(str, i, i + 1, advX, baselineY, duoCharPath);
                if (ch != '0' && ch != '8') {
                    try {
                        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(duoCharPath, false);
                        List<android.graphics.Path> contours = new ArrayList<>();
                        int smallestIdx = -1;
                        float smallestArea = Float.MAX_VALUE;
                        do {
                            android.graphics.Path cp = new android.graphics.Path();
                            if (pm.getSegment(0f, pm.getLength(), cp, true)) {
                                cp.close();
                                cp.computeBounds(cBounds, true);
                                float area = cBounds.width() * cBounds.height();
                                if (area > 0f && area < smallestArea) {
                                    smallestArea = area;
                                    smallestIdx = contours.size();
                                }
                                contours.add(cp);
                            }
                        } while (pm.nextContour());

                        if (contours.size() > 1) {
                            boolean hasHole = (ch == '6' || ch == '9');
                            android.graphics.Path merged = new android.graphics.Path();
                            boolean first = true;
                            for (int c = 0; c < contours.size(); c++) {
                                if (hasHole && c == smallestIdx) continue;
                                if (first) {
                                    merged.set(contours.get(c));
                                    first = false;
                                } else {
                                    merged.op(contours.get(c), android.graphics.Path.Op.UNION);
                                }
                            }
                            if (hasHole && smallestIdx >= 0) {
                                merged.op(contours.get(smallestIdx), android.graphics.Path.Op.DIFFERENCE);
                            }
                            if (!first) {
                                duoCharPath.set(merged);
                            }
                        }
                    } catch (Exception ignored) { }
                }
                duoSubPath.addPath(duoCharPath);
            }
            duoScaleMatrix.reset();
            duoScaleMatrix.setScale(DUO_SCALE_X, scaleY, 0f, baselineY);
            duoScaleMatrix.postTranslate(destX, 0f);
            duoSubPath.transform(duoScaleMatrix);
            outPath.addPath(duoSubPath);
        }

        private float measureClockWidth(String timeStr, TextPaint paint, float u, boolean customColon) {
            if (!customColon) return paint.measureText(timeStr);
            int colonIdx = timeStr.indexOf(':');
            if (colonIdx < 0) return paint.measureText(timeStr);
            String hrs = timeStr.substring(0, colonIdx);
            String mins = timeStr.substring(colonIdx + 1);
            boolean isDuo = paint.getPathEffect() != null;
            float sx = isDuo ? DUO_SCALE_X : 1f;
            float colonSpace = (isDuo ? 20f : 22f) * u;
            return (paint.measureText(hrs) + paint.measureText(mins)) * sx + colonSpace;
        }

        /**
         * Renders the clock in iPhone Duo (frosted translucent glass slab + crisp specular rim matching duo.png),
         * iPhone Rounded, or Tall Glass style, with solid circular frosted-glass colon dots.
         */
        private void drawStylizedClock(Canvas canvas, String timeStr, float startX, float baselineY,
                                       float scaleY, float rawCapH, float clockPx, float u,
                                       boolean isDuo, boolean isRounded, boolean wet,
                                       LoadedScene ls, boolean darkClock) {
            TextPaint fillP = wet ? clockWetPaint : clockFillPaint;
            int colonIdx = (isDuo || isRounded) ? timeStr.indexOf(':') : -1;

            if (isDuo && colonIdx >= 0) {
                // iPhone Duo Lock Screen Clock (matching duo.png 12:08):
                // Bahnschrift Condensed ('wght' 345, 'wdth' 75) unified via Path.Op.UNION, scaled to tall narrow
                // iOS Duo proportions, with rounded corners and true-circle colon dots.
                String hrs = timeStr.substring(0, colonIdx);
                String mins = timeStr.substring(colonIdx + 1);
                float hrsW = fillP.measureText(hrs) * DUO_SCALE_X;
                float colonSpace = 20f * u;
                float colonCx = startX + hrsW + colonSpace * 0.5f;
                float minsX = startX + hrsW + colonSpace;

                duoClockPath.reset();
                appendDuoTextPath(fillP, hrs, startX, baselineY, scaleY, duoClockPath);
                appendDuoTextPath(fillP, mins, minsX, baselineY, scaleY, duoClockPath);

                float visualCapH = rawCapH * scaleY;
                float dotR = visualCapH * 0.048f;
                float dotY1 = baselineY - visualCapH * 0.65f;
                float dotY2 = baselineY - visualCapH * 0.36f;
                duoClockPath.addCircle(colonCx, dotY1, dotR, android.graphics.Path.Direction.CW);
                duoClockPath.addCircle(colonCx, dotY2, dotR, android.graphics.Path.Direction.CW);

                if (!wet) {
                    clockShadowPaint.setAlpha(38);
                    canvas.save();
                    canvas.translate(0f, 3f * u);
                    canvas.drawPath(duoClockPath, clockShadowPaint);
                    canvas.restore();
                }

                // 1. Refractive blurred background glass fill inside the numerals & colon dots
                fillP.setAlpha(wet ? 155 : 215);
                canvas.drawPath(duoClockPath, fillP);

                // 2. Frosted translucent vertical gradient fill (milky near bottom, airy near top, matching screen_duo12.png)
                //    with white outer outline removed for cleaner iPhone Duo similarity.
                clockFrostPaint.setAlpha(wet ? 120 : 235);
                canvas.drawPath(duoClockPath, clockFrostPaint);
                return;
            }

            canvas.save();
            if (scaleY != 1f) {
                canvas.scale(1f, scaleY, startX, baselineY);
            }

            if (colonIdx < 0) {
                // Tall Glass mode
                if (!wet) {
                    canvas.drawText(timeStr, startX, baselineY + 4f * u, clockShadowPaint);
                }
                fillP.setAlpha(wet ? 158 : 255);
                canvas.drawText(timeStr, startX, baselineY, fillP);
                clockStrokePaint.setAlpha(wet ? 60 : 140);
                canvas.drawText(timeStr, startX, baselineY, clockStrokePaint);
                canvas.restore();
                return;
            }

            String hrs = timeStr.substring(0, colonIdx);
            String mins = timeStr.substring(colonIdx + 1);
            float hrsW = fillP.measureText(hrs);
            float colonSpace = 24f * u;
            float colonCx = startX + hrsW + colonSpace * 0.5f;
            float minsX = startX + hrsW + colonSpace;

            float dotR = clockPx * 0.048f;
            float dotY1 = baselineY - rawCapH * 0.68f;
            float dotY2 = baselineY - rawCapH * 0.42f;

            // iPhone Rounded Lock Screen Clock
            if (!wet) {
                clockShadowPaint.setAlpha(46);
                canvas.drawText(hrs, startX, baselineY + 3.5f * u, clockShadowPaint);
                canvas.drawText(mins, minsX, baselineY + 3.5f * u, clockShadowPaint);
            }
            fillP.setAlpha(wet ? 158 : 255);
            canvas.drawText(hrs, startX, baselineY, fillP);
            canvas.drawText(mins, minsX, baselineY, fillP);
            canvas.drawCircle(colonCx, dotY1, dotR, fillP);
            canvas.drawCircle(colonCx, dotY2, dotR, fillP);

            clockStrokePaint.setStrokeWidth(Math.max(1.5f, 1.4f * u));
            clockStrokePaint.setAlpha(wet ? 65 : 165);
            canvas.drawText(hrs, startX, baselineY, clockStrokePaint);
            canvas.drawText(mins, minsX, baselineY, clockStrokePaint);
            canvas.drawCircle(colonCx, dotY1, dotR, clockStrokePaint);
            canvas.drawCircle(colonCx, dotY2, dotR, clockStrokePaint);

            canvas.restore();
        }

        private void drawCenterCrop(Canvas canvas, Bitmap bmp, int viewW, int viewH) {
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            float scale = Math.max((float) viewW / bw, (float) viewH / bh);
            float sw = viewW / scale, sh = viewH / scale;
            int sx = Math.max(0, Math.round((bw - sw) * 0.5f));
            int sy = Math.max(0, Math.round((bh - sh) * 0.5f));
            srcRect.set(sx, sy, Math.min(bw, Math.round(sx + sw)), Math.min(bh, Math.round(sy + sh)));
            dstRect.set(0, 0, viewW, viewH);
            bmpPaint.setAlpha(255);
            canvas.drawBitmap(bmp, srcRect, dstRect, bmpPaint);
        }

        private static String formatTime(Context c) {
            String pattern = isClock24h(c) ? "HH:mm" : "h:mm";
            return new SimpleDateFormat(pattern, Locale.US).format(new Date());
        }
    }
}
