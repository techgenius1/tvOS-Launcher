package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Couch-TV style "Home settings" screen (matching docs/screens/home-settings.jpg):
 * - Blurred scene backdrop with dark glass tint
 * - Left sidebar ("Home settings") with icon pills:
 *   Language, Location, Weather, Look and scenes, Apps, People, Quick buttons, About
 * - Right main pane with section header, rounded glass rows, scene thumbnails,
 *   green circular checkboxes, in-place hold-OK scene reordering, and bottom hint bar.
 */
public class SettingsActivity extends Activity {

    static final String ROOT = "root",
            LANGUAGE = "Language",
            LOCATION = "Location",
            WEATHER = "Weather",
            LOOK_SCENES = "Look and scenes",
            APPS = "Apps",
            PROFILES = "Profiles",
            CONTROL_PANEL = "Control Panel",
            BUTTON_MAPPER = "Button mapper",
            QUICK = "Quick buttons",
            ADVANCED = "Advanced",
            UPDATES = "Updates",
            ABOUT = "About",
            SHELF = "Top Shelf",
            PRESENT = "Presentation",
            INPUTS = "Inputs",
            WATCHING = "Continue Watching",
            HOME = "Home",
            PERMS = "Permissions";

    private static final String[][] SIDEBAR_SECTIONS = {
        {LOOK_SCENES, "scene"},
        {WEATHER, "cloud"},
        {APPS, "apps"},
        {PROFILES, "person"},
        {CONTROL_PANEL, "sliders"},
        {BUTTON_MAPPER, "remote"},
        {QUICK, "bolt"},
        {LANGUAGE, "globe"},
        {ADVANCED, "shield"},
        {UPDATES, "update"},
        {ABOUT, "info"}
    };

    private TextView sideTitleView;
    private LinearLayout sideList;
    private TextView mainHeadView;
    private ScrollView scroller;
    private LinearLayout list;
    private TextView msgView;
    private TextView hintView;

    private final List<Runnable> refreshers = new ArrayList<>();
    private static final Map<String, Bitmap> SCENE_THUMB_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService THUMB_EXEC =
            java.util.concurrent.Executors.newFixedThreadPool(2);
    private static volatile Bitmap sCachedBackdropBmp = null;
    private static volatile String sCachedBackdropKey = null;
    private static volatile Bitmap sPlaceholderThumb = null;
    private final Map<String, View> sideRowViews = new HashMap<>();
    private final Map<String, ImageView> sideIcons = new HashMap<>();
    private final Map<String, GradientDrawable> sideIconBgs = new HashMap<>();
    private final Map<String, TextView> sideLabels = new HashMap<>();
    private final Map<String, GradientDrawable> sidePillBgs = new HashMap<>();
    private final Map<String, Integer> sideRowStates = new HashMap<>();
    private boolean pendingSidebarRebuildScheduled = false;
    private final Runnable pendingSidebarRebuild = () -> {
        pendingSidebarRebuildScheduled = false;
        if (!isFinishing()) {
            rebuildMainRows();
        }
    };

    private String currentSection = LOOK_SCENES;
    private boolean inRows = false;
    private boolean rebuilding = false;
    private String liftedSceneId = null;
    private boolean showingHiddenApps = false;
    private String activeSubMenu = null;
    private final List<String> subMenuStack = new ArrayList<>();
    private final List<Integer> subMenuParentFocusIdx = new ArrayList<>();

    // Cached state for async right-side CleanYourTV sub-menus
    private static final int CYT_PAGE_SIZE = 40;
    private Bitmap cachedCheckWhite = null;
    private Bitmap cachedCheckDark = null;
    private boolean cytAutoContinueToDebloat = false;
    private String cytModelFilterQuery = "";
    private int cytAllModelsPage = 0;
    private List<CleanYourTv.ModelMatch> cytScannedMatches = null;
    private int cytScannedCount = 0;
    private boolean cytScanningApps = false;
    private List<CleanYourTv.DebloatItem> cytChecklistItems = null;
    private String cytChecklistSlug = "";
    private boolean cytChecklistLoading = false;
    private boolean cytChecklistIsGoogleTv = false;
    private boolean cytChecklistWifiOwnedBySetupwraith = false;
    private Set<String> cytCachedInstalledPkgs = new HashSet<>();
    private Set<String> cytCachedEnabledPkgs = new HashSet<>();
    private boolean cytLaunchersSyncedAdb = false;
    private List<CleanYourTv.DebloatItem> cytManualItems = null;
    private boolean cytManualLoading = false;
    private String cytManualFilterQuery = "";
    private int cytManualPage = 0;

    private String resolvePageExtra(String pg) {
        if (pg == null || pg.isEmpty()) return LOOK_SCENES;
        if (INPUTS.equals(pg) || "cp".equalsIgnoreCase(pg) || "control_panel".equalsIgnoreCase(pg)) {
            return CONTROL_PANEL;
        } else if (LOCATION.equals(pg) || WEATHER.equals(pg) || "weather".equalsIgnoreCase(pg)) {
            return WEATHER;
        } else if (SHELF.equals(pg) || PRESENT.equals(pg) || WATCHING.equals(pg) || HOME.equals(pg) || PERMS.equals(pg) || "quick".equalsIgnoreCase(pg)) {
            return QUICK;
        } else if ("look".equalsIgnoreCase(pg) || "scenes".equalsIgnoreCase(pg)) {
            return LOOK_SCENES;
        } else if ("bm".equalsIgnoreCase(pg) || "button_mapper".equalsIgnoreCase(pg)) {
            return BUTTON_MAPPER;
        } else if ("advanced".equalsIgnoreCase(pg)) {
            return ADVANCED;
        } else if ("updates".equalsIgnoreCase(pg)) {
            return UPDATES;
        } else if ("about".equalsIgnoreCase(pg)) {
            return ABOUT;
        } else if ("apps".equalsIgnoreCase(pg)) {
            return APPS;
        } else if ("profiles".equalsIgnoreCase(pg) || "people".equalsIgnoreCase(pg)) {
            return PROFILES;
        } else if ("language".equalsIgnoreCase(pg)) {
            return LANGUAGE;
        }
        return pg;
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        String lang = getIntent().getStringExtra("lang");
        if (lang != null && !lang.isEmpty()) {
            I18n.setLang(this, lang);
        }
        String token = getIntent().getStringExtra("tmdb_token");
        if (token != null && !token.trim().isEmpty()) {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, token.trim()).apply();
            Suggestions.changed(this);
            Toast.makeText(this, I18n.resourceString(SettingsActivity.this, R.string.text_tmdb_connected), Toast.LENGTH_SHORT).show();
        }
        String pg = getIntent().getStringExtra("page");
        if (pg != null && !pg.isEmpty()) {
            currentSection = resolvePageExtra(pg);
        }
        buildUi();
        selectSection(currentSection, false, 0);
        String sub = getIntent().getStringExtra("submenu");
        if (sub != null && !sub.isEmpty()) {
            openSubMenu(sub);
        }
        int scrollY = getIntent().getIntExtra("scroll_y", 0);
        if (scrollY > 0 && scroller != null) {
            scroller.postDelayed(() -> scroller.scrollTo(0, u(scrollY)), 200);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String lang = intent.getStringExtra("lang");
        if (lang != null && !lang.isEmpty()) {
            I18n.setLang(this, lang);
            updateSidebarTranslations();
        }
        String pg = intent.getStringExtra("page");
        if (pg != null && !pg.isEmpty()) {
            activeSubMenu = null;
            subMenuStack.clear();
            subMenuParentFocusIdx.clear();
            selectSection(resolvePageExtra(pg), false, 0);
        }
        String sub = intent.getStringExtra("submenu");
        if (sub != null && !sub.isEmpty()) {
            openSubMenu(sub);
        }
        int scrollY = intent.getIntExtra("scroll_y", 0);
        if (scrollY > 0 && scroller != null) {
            scroller.postDelayed(() -> scroller.scrollTo(0, u(scrollY)), 200);
        }
    }

    /** Forward arrow that points the right way for the reading direction. */
    private String chev() {
        return I18n.isRtl(this) ? "\u2039" : "\u203a";
    }

    private int u(float dp) {
        return Ui.px(this, dp);
    }

    private String sectionTitle(String sec) {
        switch (sec) {
            case LOOK_SCENES: return I18n.resourceString(SettingsActivity.this, R.string.text_appearance_and_scenes);
            case LOCATION:
            case WEATHER: return I18n.resourceString(SettingsActivity.this, R.string.text_weather_and_location);
            case APPS: return I18n.resourceString(SettingsActivity.this, R.string.text_apps_825c0a);
            case PROFILES: return I18n.resourceString(SettingsActivity.this, R.string.text_people_3a2eb8);
            case CONTROL_PANEL: return I18n.resourceString(SettingsActivity.this, R.string.text_control_panel);
            case BUTTON_MAPPER: return I18n.resourceString(SettingsActivity.this, R.string.text_button_mapper_bed8af);
            case QUICK: return I18n.resourceString(SettingsActivity.this, R.string.text_quick_settings);
            case LANGUAGE: return I18n.resourceString(SettingsActivity.this, R.string.text_language);
            case ADVANCED: return I18n.resourceString(SettingsActivity.this, R.string.text_advanced_e7d42a);
            case UPDATES: return I18n.resourceString(SettingsActivity.this, R.string.text_updates_3c957c);
            case ABOUT: return I18n.resourceString(SettingsActivity.this, R.string.text_about);
            default: return sec;
        }
    }

    private void buildUi() {
        getWindow().setBackgroundDrawable(null);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A080C);

        // Blurred scene backdrop with baked dark scrim (single full-screen pass instead of 4 layers)
        ImageView bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bindSettingsBackdropAsync(bgView);
        root.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Two-column grid: left sidebar (276dp) + gap (34dp) + right content (1f)
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setClipChildren(false);
        columns.setClipToPadding(false);
        columns.setPaddingRelative(u(44), u(28), u(48), u(22));
        root.addView(columns, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------------- Left Sidebar (.set-side) ----------------
        LinearLayout sideCol = new LinearLayout(this);
        sideCol.setOrientation(LinearLayout.VERTICAL);
        sideCol.setClipChildren(false);
        sideCol.setClipToPadding(false);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(u(288), ViewGroup.LayoutParams.MATCH_PARENT);
        sideLp.setMarginEnd(u(26));
        columns.addView(sideCol, sideLp);

        sideTitleView = new TextView(this);
        sideTitleView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_home_settings_2f49b8));
        sideTitleView.setTextColor(Color.WHITE);
        sideTitleView.setTypeface(Ui.font(this, "display"));
        sideTitleView.setLetterSpacing(-0.022f);
        Ui.textSize(sideTitleView, 24f);
        sideTitleView.setSingleLine(true);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.setMarginStart(u(16));
        stLp.topMargin = u(10);
        stLp.bottomMargin = u(16);
        sideCol.addView(sideTitleView, stLp);

        sideList = new LinearLayout(this);
        sideList.setOrientation(LinearLayout.VERTICAL);
        sideList.setClipChildren(false);
        sideList.setClipToPadding(false);
        sideList.setPaddingRelative(u(8), u(4), u(8), u(8));
        sideCol.addView(sideList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildSidebarRows();

        // ---------------- Right Main Column (.set-main) ----------------
        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        mainCol.setClipChildren(true);
        mainCol.setClipToPadding(true);
        columns.addView(mainCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        mainHeadView = new TextView(this);
        mainHeadView.setTextColor(Color.WHITE);
        mainHeadView.setTypeface(Ui.font(this, "display"));
        mainHeadView.setLetterSpacing(-0.015f);
        Ui.textSize(mainHeadView, 20f);
        mainHeadView.setSingleLine(true);
        LinearLayout.LayoutParams mhLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mhLp.setMarginStart(u(16));
        mhLp.topMargin = u(14);
        mhLp.bottomMargin = u(12);
        mainCol.addView(mainHeadView, mhLp);

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(true);
        scroller.setClipToPadding(true);
        scroller.setPaddingRelative(0, 0, 0, 0);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPaddingRelative(u(18), u(8), u(18), u(28));
        scroller.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mainCol.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        msgView = new TextView(this);
        msgView.setTextColor(0xFF8FD3FF);
        msgView.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(msgView, 11.5f);
        msgView.setSingleLine(true);
        msgView.setVisibility(View.GONE);
        GradientDrawable msgBg = new GradientDrawable();
        msgBg.setCornerRadius(u(11));
        msgBg.setColor(0xD8181A24);
        msgBg.setStroke(Math.max(1, u(1)), 0x338FD3FF);
        msgView.setBackground(msgBg);
        msgView.setPaddingRelative(u(14), u(6), u(14), u(6));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.setMarginStart(u(18));
        msgLp.topMargin = u(6);
        mainCol.addView(msgView, msgLp);

        hintView = new TextView(this);
        hintView.setTextColor(0xB8FFFFFF);
        hintView.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(hintView, 10.5f);
        hintView.setSingleLine(true);
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setCornerRadius(u(12));
        hintBg.setColor(0xD916161F);
        hintBg.setStroke(Math.max(1, u(1)), 0x26FFFFFF);
        hintView.setBackground(hintBg);
        hintView.setPaddingRelative(u(14), u(7), u(14), u(7));
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.setMarginStart(u(18));
        hintLp.topMargin = u(8);
        mainCol.addView(hintView, hintLp);

        I18n.applyDirection(this, root);
        setContentView(root);
    }

    /** Creates or reuses the soft blurred scene + dock glow backdrop behind Home settings without stalling the UI thread. */
    private void bindSettingsBackdropAsync(final ImageView bgView) {
        List<String> enabled = Scenes.getEnabledSceneIds(this);
        final String sceneId = enabled.isEmpty() ? "night" : enabled.get(0);
        final String key = sceneId + ":" + (Scenes.isCustomId(sceneId) ? "custom" : "asset");
        Bitmap cached = sCachedBackdropBmp;
        if (cached != null && !cached.isRecycled() && key.equals(sCachedBackdropKey)) {
            bgView.setImageDrawable(new BitmapDrawable(getResources(), cached));
            return;
        }
        if (cached != null && !cached.isRecycled()) {
            bgView.setImageDrawable(new BitmapDrawable(getResources(), cached));
        }
        THUMB_EXEC.execute(() -> {
            Bitmap blurred = buildSettingsBackdrop(sceneId);
            if (blurred != null) {
                sCachedBackdropBmp = blurred;
                sCachedBackdropKey = key;
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        bgView.setImageDrawable(new BitmapDrawable(getResources(), blurred));
                    }
                });
            }
        });
    }

    private static Bitmap buildSettingsBackdropStatic(android.content.Context ctx, String sceneId) {
        try {
            Scenes.Spec spec = Scenes.byId(sceneId);
            Bitmap src = Scenes.CUSTOM_ID.equals(sceneId)
                    ? CustomMedia.getCustomSceneWallpaper(ctx, 480, 270)
                    : Scenes.loadAssetBitmap(ctx, spec.bgAsset, 480, 270);
            if (src == null) return null;
            int w = 240, h = 135;
            Bitmap base = Bitmap.createScaledBitmap(src, w, h, true);
            if (base != src) src.recycle();
            Canvas c = new Canvas(base);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Subtle dock-like color dots along the bottom before blurring, as seen in home-settings.jpg
            int[] dockCols = {0xFFE50914, 0xFF00C2B8, 0xFF0066E5, 0xFF8E3CF7, 0xFF00A8E1, 0xFFF5C518};
            for (int i = 0; i < dockCols.length; i++) {
                p.setColor(dockCols[i]);
                p.setAlpha(110);
                float cx = w * (0.20f + i * 0.11f);
                c.drawCircle(cx, h * 0.88f, w * 0.035f, p);
            }
            Bitmap blurred = Ui.blur(base, w, h, 8, 3);
            if (blurred != base) base.recycle();
            if (blurred != null) {
                new Canvas(blurred).drawColor(0xCC0A080C);
            }
            return blurred;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap buildSettingsBackdrop(String sceneId) {
        return buildSettingsBackdropStatic(this, sceneId);
    }

    /** Pre-warms the Settings backdrop and scene/background thumbnails in the background so Settings opens in <16ms. */
    public static void prewarmCache(final android.content.Context ctx) {
        if (ctx == null) return;
        THUMB_EXEC.execute(() -> {
            try {
                List<String> enabled = Scenes.getEnabledSceneIds(ctx);
                String sceneId = enabled.isEmpty() ? "night" : enabled.get(0);
                String key = sceneId + ":" + (Scenes.isCustomId(sceneId) ? "custom" : "asset");
                if (sCachedBackdropBmp == null || sCachedBackdropBmp.isRecycled() || !key.equals(sCachedBackdropKey)) {
                    Bitmap blurred = buildSettingsBackdropStatic(ctx, sceneId);
                    if (blurred != null) {
                        sCachedBackdropBmp = blurred;
                        sCachedBackdropKey = key;
                    }
                }
                int reqW = Ui.px(ctx, 46) * 2;
                int reqH = Ui.px(ctx, 26) * 2;
                for (Scenes.Spec spec : Scenes.ALL) {
                    if (spec.bgAsset == null || spec.bgAsset.isEmpty()) continue;
                    String tk = spec.bgAsset + ":" + reqW + "x" + reqH;
                    if (!SCENE_THUMB_CACHE.containsKey(tk)) {
                        Bitmap raw = Scenes.loadAssetBitmap(ctx, spec.bgAsset, reqW, reqH);
                        if (raw != null) {
                            Bitmap scaled = Bitmap.createScaledBitmap(raw, reqW, reqH, true);
                            if (scaled != raw) raw.recycle();
                            SCENE_THUMB_CACHE.put(tk, scaled);
                        }
                    }
                }
                for (int i = 0; i < Scenes.CLASSIC_PHOTOS.length; i++) {
                    String assetPath = "backgrounds/" + Scenes.CLASSIC_PHOTOS[i].id;
                    String tk = assetPath + ":" + reqW + "x" + reqH;
                    if (!SCENE_THUMB_CACHE.containsKey(tk)) {
                        Bitmap raw = Scenes.loadAssetBitmap(ctx, assetPath, reqW, reqH);
                        if (raw != null) {
                            Bitmap scaled = Bitmap.createScaledBitmap(raw, reqW, reqH, true);
                            if (scaled != raw) raw.recycle();
                            SCENE_THUMB_CACHE.put(tk, scaled);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static void clearPrewarmCache() {
        Bitmap backdrop = sCachedBackdropBmp;
        sCachedBackdropBmp = null;
        sCachedBackdropKey = null;
        if (backdrop != null && !backdrop.isRecycled()) {
            backdrop.recycle();
        }
        for (Bitmap b : SCENE_THUMB_CACHE.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        SCENE_THUMB_CACHE.clear();
    }

    private void buildSidebarRows() {
        sideList.removeAllViews();
        sideRowViews.clear();
        sideIcons.clear();
        sideIconBgs.clear();
        sideLabels.clear();
        sidePillBgs.clear();
        sideRowStates.clear();

        int iconBoxSz = u(27);
        final float iconRad = iconBoxSz * 0.26f; // Exact squircle corner ratio used by dock app icons

        for (int i = 0; i < SIDEBAR_SECTIONS.length; i++) {
            final String secKey = SIDEBAR_SECTIONS[i][0];
            final String glyphName = SIDEBAR_SECTIONS[i][1];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setMinimumHeight(u(35));
            row.setPaddingRelative(u(7), u(4), u(14), u(4));

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(u(12));
            pillBg.setColor(Color.TRANSPARENT);
            row.setBackground(pillBg);

            // Squircle icon container matching the dock app icon shape (.ssec i)
            FrameLayout iconBox = new FrameLayout(this);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(iconRad);
            iconBg.setColor(0x1AFFFFFF);
            iconBox.setBackground(iconBg);

            int iconGlyphSz = u(15);
            ImageView iconIv = new ImageView(this);
            iconIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconIv.setImageBitmap(Glyphs.get(glyphName, iconGlyphSz, Color.WHITE));
            iconBox.addView(iconIv, new FrameLayout.LayoutParams(iconGlyphSz, iconGlyphSz, Gravity.CENTER));

            LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(iconBoxSz, iconBoxSz);
            ibLp.setMarginEnd(u(11));
            row.addView(iconBox, ibLp);

            TextView label = new TextView(this);
            label.setText(sectionTitle(secKey));
            label.setTypeface(Ui.font(this, "semibold"));
            Ui.textSize(label, 14.5f);
            label.setTextColor(0xC2FFFFFF);
            label.setSingleLine(true);
            row.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            row.setOnFocusChangeListener((v, has) -> {
                if (rebuilding) return;
                if (has) {
                    inRows = false;
                    liftedSceneId = null;
                    boolean sectionChanged = !secKey.equals(currentSection) || showingHiddenApps || activeSubMenu != null;
                    if (sectionChanged) {
                        showingHiddenApps = false;
                        activeSubMenu = null;
                        subMenuStack.clear();
                        subMenuParentFocusIdx.clear();
                        currentSection = secKey;
                        mainHeadView.setText(sectionTitle(currentSection));
                        scheduleSidebarRebuild();
                    }
                    updateSidebarStyles();
                    updateHintText(false);
                } else {
                    updateSidebarStyles();
                }
            });

            // FORK PATCH (mouse/touch): in touch mode a click never gives the row
            // focus, so the OnFocusChangeListener above doesn't fire and the pane
            // never switches. Select (or reset) the section explicitly here.
            row.setOnClickListener(v -> {
                boolean sectionChanged = !secKey.equals(currentSection)
                        || showingHiddenApps || activeSubMenu != null;
                if (sectionChanged) {
                    showingHiddenApps = false;
                    activeSubMenu = null;
                    subMenuStack.clear();
                    selectSection(secKey, true, ADVANCED.equals(secKey) ? 1 : 0);
                    updateHintText(false);
                } else {
                    focusFirstRow();
                }
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = u(2);
            sideList.addView(row, rlp);

            sideRowViews.put(secKey, row);
            sideIcons.put(secKey, iconIv);
            sideIconBgs.put(secKey, iconBg);
            sideLabels.put(secKey, label);
            sidePillBgs.put(secKey, pillBg);
        }
    }

    private void scheduleSidebarRebuild() {
        if (sideList == null) {
            rebuildMainRows();
            return;
        }
        sideList.removeCallbacks(pendingSidebarRebuild);
        pendingSidebarRebuildScheduled = true;
        sideList.postDelayed(pendingSidebarRebuild, 140);
    }

    private void flushPendingSidebarRebuild() {
        if (pendingSidebarRebuildScheduled) {
            if (sideList != null) sideList.removeCallbacks(pendingSidebarRebuild);
            pendingSidebarRebuildScheduled = false;
            rebuildMainRows();
        }
    }

    private void updateSidebarStyles() {
        int iconGlyphSz = u(15);
        for (String[] pair : SIDEBAR_SECTIONS) {
            String key = pair[0];
            String glyph = pair[1];
            View row = sideRowViews.get(key);
            if (row == null) continue;
            boolean focused = row.isFocused();
            boolean active = key.equals(currentSection);
            int state = focused ? 2 : (active ? 1 : 0);
            Integer prev = sideRowStates.get(key);
            if (prev != null && prev == state) continue;
            sideRowStates.put(key, state);

            GradientDrawable pillBg = sidePillBgs.get(key);
            GradientDrawable iconBg = sideIconBgs.get(key);
            ImageView iconIv = sideIcons.get(key);
            TextView label = sideLabels.get(key);

            if (focused) {
                pillBg.setColor(Color.WHITE);
                iconBg.setColor(0x14000000);
                iconIv.setImageBitmap(Glyphs.get(glyph, iconGlyphSz, 0xFF111114));
                label.setTextColor(0xFF111114);
            } else if (active) {
                pillBg.setColor(0x22FFFFFF); // .ssec.on: rgba(255,255,255,0.13)
                iconBg.setColor(0x1AFFFFFF);
                iconIv.setImageBitmap(Glyphs.get(glyph, iconGlyphSz, Color.WHITE));
                label.setTextColor(Color.WHITE);
            } else {
                pillBg.setColor(Color.TRANSPARENT);
                iconBg.setColor(0x1AFFFFFF);
                iconIv.setImageBitmap(Glyphs.get(glyph, iconGlyphSz, 0xD9FFFFFF));
                label.setTextColor(0xC2FFFFFF);
            }
        }
    }

    private void updateSidebarTranslations() {
        sideTitleView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_home_settings_2f49b8));
        for (String[] pair : SIDEBAR_SECTIONS) {
            String key = pair[0];
            TextView label = sideLabels.get(key);
            if (label != null) label.setText(sectionTitle(key));
        }
    }

    private void selectSection(String sec, boolean focusRightPane, int rowIdx) {
        if (sideList != null) sideList.removeCallbacks(pendingSidebarRebuild);
        pendingSidebarRebuildScheduled = false;
        currentSection = sec;
        inRows = focusRightPane;
        rebuildMainRows();
        updateSidebarStyles();
        if (focusRightPane) {
            focusRowAtIndex(rowIdx);
        } else {
            View sideRow = sideRowViews.get(sec);
            if (sideRow != null) sideRow.requestFocus();
        }
    }

    private void focusFirstRow() {
        focusRowAtIndex(ADVANCED.equals(currentSection) && activeSubMenu == null ? 1 : 0);
    }

    private void focusRowAtIndex(int targetIdx) {
        flushPendingSidebarRebuild();
        View target = null;
        int count = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.isFocusable()) {
                if (target == null || count == targetIdx) {
                    target = child;
                }
                count++;
            }
        }
        if (target != null) {
            inRows = true;
            target.requestFocus();
            updateSidebarStyles();
        }
    }

    private void updateHintText(boolean movableRow) {
        if (hintView == null) return;
        if (liftedSceneId != null) {
            hintView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_up_and_down_to_move_ok_when_done));
        } else if (!inRows) {
            hintView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_up_and_down_to_browse_ok_to_change_back));
        } else if (APPS.equals(currentSection) && showingHiddenApps) {
            hintView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_ok_to_open_hold_ok_to_restore_back_to_go));
        } else if (movableRow) {
            hintView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_ok_to_change_hold_ok_to_move_back_to_go));
        } else {
            hintView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_ok_to_change_back_to_go_back));
        }
    }

    private void showStatusMsg(String text) {
        if (msgView == null) return;
        if (text == null || text.isEmpty()) {
            msgView.setVisibility(View.GONE);
        } else {
            msgView.setText(text);
            msgView.setVisibility(View.VISIBLE);
        }
    }

    private int getFocusedRowIndex() {
        int count = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.isFocusable()) {
                if (child.hasFocus() || child.isFocused()) return count;
                count++;
            }
        }
        return 0;
    }

    public void openSubMenu(String id) {
        if (id == null || id.isEmpty()) return;
        subMenuStack.add(activeSubMenu != null ? activeSubMenu : "");
        subMenuParentFocusIdx.add(getFocusedRowIndex());
        activeSubMenu = id;
        inRows = true;
        rebuildMainRows();
        focusFirstRow();
    }

    public boolean closeSubMenu() {
        if (showingHiddenApps) {
            showingHiddenApps = false;
            rebuildMainRows();
            focusFirstRow();
            return true;
        }
        if (activeSubMenu != null) {
            String prev = "";
            int parentIdx = 0;
            if (!subMenuStack.isEmpty()) {
                prev = subMenuStack.remove(subMenuStack.size() - 1);
            }
            if (!subMenuParentFocusIdx.isEmpty()) {
                parentIdx = subMenuParentFocusIdx.remove(subMenuParentFocusIdx.size() - 1);
            }
            activeSubMenu = (prev == null || prev.isEmpty()) ? null : prev;
            inRows = true;
            rebuildMainRows();
            focusRowAtIndex(parentIdx);
            return true;
        }
        return false;
    }

    public void openDefaultHomeSubMenu() {
        openSubMenu("default_home");
    }

    public void openSystemLaunchersSubMenu() {
        openSubMenu("cyt_launchers");
    }

    public void openButtonKeySubMenu(int keyCode) {
        openSubMenu("bm_key:" + keyCode);
    }

    private void rebuildMainRows() {
        boolean wasInRows = inRows;
        int prevFocusIdx = getFocusedRowIndex();
        rebuilding = true;
        if (sideList != null) {
            sideList.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        list.removeAllViews();
        refreshers.clear();
        showStatusMsg(null);
        mainHeadView.setText(sectionTitle(currentSection));

        try {
            if (activeSubMenu != null) {
                renderSubMenu(activeSubMenu);
            } else {
                switch (currentSection) {
                    case LANGUAGE:
                        pageLanguage();
                        break;
                    case LOCATION:
                    case WEATHER:
                        pageWeather();
                        break;
                    case LOOK_SCENES:
                        pageLookAndScenes();
                        break;
                    case APPS:
                        pageApps();
                        break;
                    case PROFILES:
                        pageProfiles();
                        break;
                    case CONTROL_PANEL:
                        pageControlPanel();
                        break;
                    case BUTTON_MAPPER:
                        pageButtonMapper();
                        break;
                    case QUICK:
                        pageQuickAndHome();
                        break;
                    case ADVANCED:
                        pageAdvanced();
                        break;
                    case UPDATES:
                        pageUpdates();
                        break;
                    case ABOUT:
                    default:
                        pageAbout();
                        break;
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("tvOSLauncher", "Settings section build error (" + currentSection + "): " + t);
        }
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (sideList != null) {
            sideList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        rebuilding = false;
        if (wasInRows) {
            focusRowAtIndex(prevFocusIdx);
        } else {
            scroller.scrollTo(0, 0);
        }
        updateHintText(activeSubMenu == null && LOOK_SCENES.equals(currentSection));
    }

    // ================================================================ Section Pages

    void pageLanguage() {
        String cur = I18n.getLang(this);
        I18n.LanguageOption[] languages = I18n.languageOptions(this);
        for (int i = 0; i < languages.length; i++) {
            final int idx = i;
            final I18n.LanguageOption language = languages[i];
            final String code = language.code;
            choiceRow(
                    language.title,
                    language.description,
                    code.equals(cur),
                    () -> {
                        boolean wasRtl = I18n.isRtl(this);
                        I18n.setLang(this, code);
                        if (wasRtl != I18n.isRtl(this)) {
                            // Reading direction changed: rebuild the screen so the whole layout flips.
                            getIntent().putExtra("page", "language");
                            recreate();
                            return;
                        }
                        updateSidebarTranslations();
                        selectSection(LANGUAGE, true, idx);
                    });
        }
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_changes_the_words_on_the_home_screen_dat));
    }

    void pageWeather() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_weather_a5c8c4));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_weather_on_the_home_screen),
                () -> Weather.enabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, !Weather.enabled(this)).apply();
                    Weather.changed(this);
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_temperature), () -> {
            String u = Weather.units(this);
            return u.equals("f") ? "°F (Fahrenheit)" : "°C (Celsius)";
        }, () -> {
            String cur = Weather.units(this);
            String next = "f".equals(cur) ? "c" : "f";
            Weather.prefs(this).edit().putString(Weather.K_UNITS, next).apply();
            Weather.changed(this);
            Weather.refresh(this, true, null);
        });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_update_every),
                () -> Weather.refreshMinutes(this) + I18n.resourceString(SettingsActivity.this, R.string.text_minutes),
                () -> {
                    int cur = Weather.refreshMinutes(this);
                    int next = cur == 15 ? 30 : cur == 30 ? 60 : 15;
                    Weather.prefs(this).edit().putInt(Weather.K_REFRESH, next).apply();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_weather_source),
                () -> Weather.sourceName(Weather.source(this)),
                () -> openSubMenu("weather_source"));
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_weather_from),
                "Open-Meteo",
                I18n.resourceString(SettingsActivity.this, R.string.text_free_forecast_service_no_account_needed));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_location_873ca7));
        String city = Weather.place(this);
        boolean isManual = Weather.manual(this);
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_current_place),
                city.isEmpty() ? I18n.resourceString(SettingsActivity.this, R.string.text_automatic_from_internet) : city,
                isManual ? I18n.resourceString(SettingsActivity.this, R.string.text_set_manually) : I18n.resourceString(SettingsActivity.this, R.string.text_detected_automatically));

        row(I18n.resourceString(SettingsActivity.this, R.string.text_search_for_a_city_or_town),
                () -> isManual ? city : chev(),
                this::searchCity);

        if (isManual) {
            row(I18n.resourceString(SettingsActivity.this, R.string.text_use_automatic_location), () -> "", () -> {
                Weather.setPlace(this, null);
                Weather.refresh(this, true, this::rebuildMainRows);
                rebuildMainRows();
                focusRowAtIndex(4);
            });
        }

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_used_for_the_weather_on_the_home_screen));
    }

    /**
     * Exact Couch-TV "Look and scenes" layout (matching docs/screens/home-settings.jpg):
     * - DOCK: Dock resize option (-50% to +50%, default 100%)
     * - LOOK: Night scenes vs Classic
     * - SCENES: Night, Golden hour, Waterline, Fog, Rainforest dawn (with 16:9 thumbnails & green circular checkmarks)
     * - Each scene stays: 20 seconds
     * - Clock: 12 hour / 24 hour
     * - CLASSIC BACKGROUND: Couch-TV photos & ambient themes
     */
    void pageLookAndScenes() {
        boolean scenesLook = Scenes.isScenesLook(this);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_look));
        choiceRow(
                I18n.resourceString(SettingsActivity.this, R.string.text_night_scenes_6b95bc),
                I18n.resourceString(SettingsActivity.this, R.string.text_a_big_glass_clock_scenes_taking_turns),
                scenesLook,
                () -> {
                    Scenes.setLook(this, Scenes.LOOK_SCENES);
                    selectSection(LOOK_SCENES, true, 0);
                });
        choiceRow(
                I18n.resourceString(SettingsActivity.this, R.string.text_classic),
                I18n.resourceString(SettingsActivity.this, R.string.text_one_photo_and_a_grid_of_app_banners),
                !scenesLook,
                () -> {
                    Scenes.setLook(this, Scenes.LOOK_CLASSIC);
                    selectSection(LOOK_SCENES, true, 1);
                });

        section(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen_widgets));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen_widget_settings),
                () -> (HomeDashboard.isEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off)) + "  ›",
                () -> openSubMenu("home_dashboard"));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_dock));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_dock_size),
                () -> Scenes.dockScaleLabel(this),
                () -> openSubMenu("dock_size"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_dock_rows),
                () -> Scenes.dockRowsLabel(this),
                () -> {
                    Scenes.cycleDockRows(this);
                    refreshAll();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_show_app_names),
                () -> Scenes.isDockNamesShown(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    Scenes.setDockNamesShown(this, !Scenes.isDockNamesShown(this));
                    refreshAll();
                });

        if (scenesLook) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_night_scenes_clock));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_small_clock),
                    () -> Scenes.isSmallClockEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        Scenes.setSmallClockEnabled(this, !Scenes.isSmallClockEnabled(this));
                        refreshAll();
                    });
            row(I18n.resourceString(SettingsActivity.this, R.string.text_clock),
                    () -> Scenes.isClock24h(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_24_hour) : I18n.resourceString(SettingsActivity.this, R.string.text_12_hour),
                    () -> {
                        Scenes.setClock24h(this, !Scenes.isClock24h(this));
                        refreshAll();
                    });
            row(I18n.resourceString(SettingsActivity.this, R.string.text_clock_font),
                    () -> Scenes.clockStyleLabel(this),
                    () -> {
                        Scenes.cycleClockStyle(this);
                        refreshAll();
                    });
            row(I18n.resourceString(SettingsActivity.this, R.string.text_default_wallpaper_clock_alignment),
                    () -> Scenes.defaultClockPosLabel(this),
                    () -> {
                        Scenes.cycleDefaultClockPos(this);
                        refreshAll();
                    });
            boolean customSceneOn = CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_SCENE);
            if (customSceneOn || Scenes.isAnyCustomSceneEnabled(this)) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_clock_alignment),
                        () -> Scenes.customClockPosLabel(this),
                        () -> {
                            Scenes.cycleCustomClockPos(this);
                            refreshAll();
                        });
            }
            row(I18n.resourceString(SettingsActivity.this, R.string.text_each_scene_stays),
                    () -> Scenes.getStayIntervalLabel(this),
                    () -> {
                        Scenes.cycleStaySeconds(this);
                        refreshAll();
                    });

            section(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_208136));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_mode_f2890b),
                    () -> CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_SCENE)
                            ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        boolean next = !CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_SCENE);
                        CustomMedia.setCustomWallpaperEnabled(this, CustomMedia.SCOPE_SCENE, next);
                        rebuildMainRows();
                    });
            if (customSceneOn) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_settings),
                        () -> CustomMedia.getCustomWallpaperModeLabel(this, CustomMedia.SCOPE_SCENE),
                        () -> openSubMenu("custom_wall:" + CustomMedia.SCOPE_SCENE));
            } else {
                section(I18n.resourceString(SettingsActivity.this, R.string.text_scenes));
                List<String> order = Scenes.getSceneOrder(this);
                for (int i = 0; i < order.size(); i++) {
                    String id = order.get(i);
                    if (Scenes.isCustomId(id)) continue;
                    final int sceneIdx = i;
                    Scenes.Spec spec = Scenes.byId(id);
                    sceneCheckRow(spec, sceneIdx, order);
                }
            }
        } else {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_classic_clock_weather));
            boolean bigClock = Scenes.isClassicBigClockEnabled(this);
            row(I18n.resourceString(SettingsActivity.this, R.string.text_big_clock_night_scene_style),
                    () -> bigClock ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        Scenes.setClassicBigClockEnabled(this, !Scenes.isClassicBigClockEnabled(this));
                        rebuildMainRows();
                    });
            if (bigClock) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_clock_alignment),
                        () -> Scenes.classicClockPosLabel(this),
                        () -> {
                            Scenes.cycleClassicClockPos(this);
                            refreshAll();
                        });
                row(I18n.resourceString(SettingsActivity.this, R.string.text_clock),
                        () -> Scenes.isClock24h(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_24_hour) : I18n.resourceString(SettingsActivity.this, R.string.text_12_hour),
                        () -> {
                            Scenes.setClock24h(this, !Scenes.isClock24h(this));
                            refreshAll();
                        });
                row(I18n.resourceString(SettingsActivity.this, R.string.text_clock_font),
                        () -> Scenes.clockStyleLabel(this),
                        () -> {
                            Scenes.cycleClockStyle(this);
                            refreshAll();
                        });
                row(I18n.resourceString(SettingsActivity.this, R.string.text_top_right_weather_shortcut),
                        () -> SceneHome.isWeatherShortcutEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                        () -> {
                            SceneHome.setWeatherShortcutEnabled(this, !SceneHome.isWeatherShortcutEnabled(this));
                            refreshAll();
                        });
                row(I18n.resourceString(SettingsActivity.this, R.string.text_top_right_search_shortcut),
                        () -> SceneHome.isSearchShortcutEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                        () -> {
                            SceneHome.setSearchShortcutEnabled(this, !SceneHome.isSearchShortcutEnabled(this));
                            refreshAll();
                        });
            } else {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_clock),
                        () -> Scenes.isClock24h(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_24_hour) : I18n.resourceString(SettingsActivity.this, R.string.text_12_hour),
                        () -> {
                            Scenes.setClock24h(this, !Scenes.isClock24h(this));
                            refreshAll();
                        });
            }

            section(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_208136));
            boolean customClassicOn = CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC);
            row(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_mode_f2890b),
                    () -> CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC)
                            ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        boolean next = !CustomMedia.isCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC);
                        CustomMedia.setCustomWallpaperEnabled(this, CustomMedia.SCOPE_CLASSIC, next);
                        rebuildMainRows();
                    });
            if (customClassicOn) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_settings),
                        () -> CustomMedia.getCustomWallpaperModeLabel(this, CustomMedia.SCOPE_CLASSIC),
                        () -> openSubMenu("custom_wall:" + CustomMedia.SCOPE_CLASSIC));
            } else {
                section(I18n.resourceString(SettingsActivity.this, R.string.text_classic_background));
                String curWall = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
                for (int i = 0; i < Scenes.CLASSIC_PHOTOS.length; i++) {
                    final String photoFile = Scenes.CLASSIC_PHOTOS[i].id;
                    final String wallKey = "photo:" + photoFile;
                    final String label = Scenes.CLASSIC_PHOTOS[i].title(this);
                    classicPhotoRow(label, "backgrounds/" + photoFile, wallKey.equals(curWall), () -> {
                        Profiles.homePrefs(this).edit().putString("wallpaper", wallKey).apply();
                        rebuildMainRows();
                    });
                }
                row(I18n.resourceString(SettingsActivity.this, R.string.text_ambient_colour_themes_028289), () -> {
                    String k = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
                    int idx = Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
                    return MainActivity.wallName(this, Math.max(0, idx));
                }, () -> openSubMenu("ambient_themes"));
            }
        }
    }

    void pageApps() {
        if (showingHiddenApps) {
            pageHiddenApps();
            return;
        }
        row(I18n.resourceString(SettingsActivity.this, R.string.text_hidden_apps),
                () -> {
                    Set<String> hidden = Profiles.homePrefs(this).getStringSet(MainActivity.PREF_HIDDEN, Collections.emptySet());
                    int count = hidden != null ? hidden.size() : 0;
                    return (count > 0 ? count + "  " : "") + chev();
                },
                () -> {
                    showingHiddenApps = true;
                    rebuildMainRows();
                    focusFirstRow();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_reset_app_order_unhide_all),
                () -> "",
                () -> {
                    Profiles.homePrefs(this).edit()
                            .remove(MainActivity.PREF_ORDER)
                            .remove(MainActivity.PREF_OTHER)
                            .remove(MainActivity.PREF_HIDDEN)
                            .apply();
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_app_layout_reset_to_default));
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_show_continue_watching_on_home),
                () -> Profiles.homePrefs(this).getBoolean("cw_enabled", false) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean on = !Profiles.homePrefs(this).getBoolean("cw_enabled", false);
                    Profiles.homePrefs(this).edit().putBoolean("cw_enabled", on).apply();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen_weather_shortcut),
                () -> SceneHome.isWeatherShortcutEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !SceneHome.isWeatherShortcutEnabled(this);
                    SceneHome.setWeatherShortcutEnabled(this, next);
                    refreshAll();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen_search_shortcut),
                () -> SceneHome.isSearchShortcutEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !SceneHome.isSearchShortcutEnabled(this);
                    SceneHome.setSearchShortcutEnabled(this, next);
                    refreshAll();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_clear_continue_watching_history),
                () -> "",
                () -> {
                    MediaWatchService.Store.clear(this);
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_continue_watching_cleared));
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_manage_system_apps_in_tv_settings),
                () -> chev(),
                () -> Suggestions.start(this, new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_tip_hold_ok_on_any_app_on_the_home_scree));

        // One section per streaming app with a continue-watching popup on its dock tile.
        for (WatchCloud cloud : new WatchCloud[]{new NuvioCloud(this), new StremioCloud(this)}) {
            section(cloud.name().toUpperCase(java.util.Locale.ROOT));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_continue_watching_on_dock),
                    () -> cloud.popupEnabled() ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        cloud.setPopupEnabled(!cloud.popupEnabled());
                        refreshAll();
                    });
            row(I18n.resourceString(SettingsActivity.this, R.string.text_account),
                    () -> cloud.isSignedIn() ? I18n.resourceString(SettingsActivity.this, R.string.text_signed_in_sign_out)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_not_signed_in),
                    () -> confirmWatchSignOut(cloud));
            if (cloud instanceof NuvioCloud) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_apps_using_nuvio),
                        () -> {
                            int n = NuvioCloud.linkedApps(this).size();
                            return (n > 0 ? "+" + n + "  " : "") + "›";
                        },
                        () -> openSubMenu("nuvio_apps"));
            }
        }
    }

    /**
     * Nuvio forks (other package names, same Nuvio account) the user links to Nuvio's continue
     * watching. The official com.nuvio.* app is built in; nothing else is linked automatically,
     * but apps that look like Nuvio (name or nuvio:// links) are listed first as suggestions.
     */
    private void subMenuNuvioApps() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_apps_using_nuvio));
        backSubMenuRow();
        PackageManager pm = getPackageManager();

        // Launchable apps: package -> label.
        Map<String, String> apps = new HashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> ris;
            try {
                ris = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
            } catch (Throwable t) {
                ris = Collections.emptyList();
            }
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || apps.containsKey(pkg)) continue;
                CharSequence l = ri.activityInfo.applicationInfo != null ? ri.activityInfo.applicationInfo.loadLabel(pm) : null;
                apps.put(pkg, l != null && l.length() > 0 ? l.toString().trim() : pkg);
            }
        }

        // Apps that open nuvio:// links: forks that kept Nuvio's deep links.
        Set<String> nuvioLinks = new HashSet<>();
        try {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("nuvio://detail/movie/tt0111161")), 0)) {
                if (ri.activityInfo != null) nuvioLinks.add(ri.activityInfo.packageName);
            }
        } catch (Throwable ignored) {
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_official_app));
        boolean anyOfficial = false;
        for (Map.Entry<String, String> e : apps.entrySet()) {
            if (!NuvioCloud.isOfficial(e.getKey())) continue;
            anyOfficial = true;
            choiceRow(e.getValue(), I18n.resourceString(SettingsActivity.this, R.string.text_always_on) + " · " + e.getKey(), true, () -> { });
        }
        if (!anyOfficial) {
            choiceRow("Nuvio", I18n.resourceString(SettingsActivity.this, R.string.text_not_installed) + " · " + NuvioCloud.OFFICIAL_PKG, true, () -> { });
        }

        // Linked apps that were uninstalled stay listed, so they can be unlinked.
        Set<String> linked = NuvioCloud.linkedApps(this);
        Map<String, String> others = new HashMap<>();
        for (Map.Entry<String, String> e : apps.entrySet()) {
            String pkg = e.getKey();
            if (NuvioCloud.isOfficial(pkg) || pkg.startsWith("com.stremio.") || pkg.equals(getPackageName())) continue;
            others.put(pkg, e.getValue());
        }
        for (String pkg : linked) if (!others.containsKey(pkg)) others.put(pkg, pkg);

        // Suggestions first, then A-Z. Not sorted by linked state, so rows don't move when toggled.
        List<String[]> rows = new ArrayList<>();  // {pkg, label, "1" = suggested}
        for (Map.Entry<String, String> e : others.entrySet()) {
            String pkg = e.getKey(), label = e.getValue();
            boolean suggested = nuvioLinks.contains(pkg)
                    || pkg.toLowerCase(java.util.Locale.ROOT).contains("nuvio")
                    || label.toLowerCase(java.util.Locale.ROOT).contains("nuvio");
            rows.add(new String[]{pkg, label, suggested ? "1" : "2"});
        }
        Collections.sort(rows, (a, b) -> {
            int cmp = a[2].compareTo(b[2]);
            if (cmp == 0) cmp = a[1].compareToIgnoreCase(b[1]);
            return cmp != 0 ? cmp : a[0].compareTo(b[0]);
        });

        section(I18n.resourceString(SettingsActivity.this, R.string.text_other_apps_5ccac2));
        for (String[] r : rows) {
            final String pkg = r[0];
            boolean on = linked.contains(pkg);
            String sub = (!apps.containsKey(pkg) ? I18n.resourceString(SettingsActivity.this, R.string.text_not_installed) + " · "
                    : r[2].equals("1") ? I18n.resourceString(SettingsActivity.this, R.string.text_suggested) + " · " : "") + pkg;
            choiceRow(r[1], sub, on, () -> {
                NuvioCloud.setLinked(this, pkg, !NuvioCloud.linkedApps(this).contains(pkg));
                rebuildMainRows();
            });
        }
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_link_nuvio_forks_like_nuvio_native_legac));
    }

    static final class HiddenAppItem {
        String key;
        String pkg;
        String label;
        android.graphics.drawable.Drawable icon;
        Intent launch;
    }

    private List<HiddenAppItem> listHiddenAppItems() {
        List<HiddenAppItem> out = new ArrayList<>();
        try {
            Set<String> hidden = Profiles.homePrefs(this).getStringSet(MainActivity.PREF_HIDDEN, Collections.emptySet());
            if (hidden == null || hidden.isEmpty()) return out;
            PackageManager pm = getPackageManager();
            Set<String> seenPkgs = new HashSet<>();
            collectHiddenResolved(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, hidden, seenPkgs, true, out);
            collectHiddenResolved(pm, Intent.CATEGORY_LAUNCHER, hidden, seenPkgs, false, out);
            if (hidden.contains(MainActivity.INPUTS_KEY)) {
                HiddenAppItem it = new HiddenAppItem();
                it.key = MainActivity.INPUTS_KEY;
                it.pkg = getPackageName();
                it.label = MainActivity.getCustomAppName(this, it.key, null, I18n.resourceString(SettingsActivity.this, R.string.text_inputs));
                it.icon = new BitmapDrawable(getResources(), Glyphs.get("input", u(22), Color.WHITE));
                out.add(it);
            }
            if (hidden.contains(MainActivity.PRESENT_KEY)) {
                HiddenAppItem it = new HiddenAppItem();
                it.key = MainActivity.PRESENT_KEY;
                it.pkg = getPackageName();
                it.label = MainActivity.getCustomAppName(this, it.key, null, I18n.resourceString(SettingsActivity.this, R.string.text_presentation));
                it.icon = new BitmapDrawable(getResources(), Glyphs.get("present", u(22), Color.WHITE));
                it.launch = new Intent(this, com.techgenius.tvoslauncher.present.PresentationActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                out.add(it);
            }
            Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void collectHiddenResolved(PackageManager pm, String category, Set<String> hidden,
                                       Set<String> seenPkgs, boolean leanback, List<HiddenAppItem> out) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            if (ri == null || ri.activityInfo == null) continue;
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(getPackageName()) || seenPkgs.contains(ai.packageName)) continue;
            seenPkgs.add(ai.packageName);
            android.content.ComponentName cn = new android.content.ComponentName(ai.packageName, ai.name);
            String key = cn.flattenToShortString();
            if (!hidden.contains(key) && !hidden.contains(ai.packageName)) continue;
            HiddenAppItem it = new HiddenAppItem();
            it.key = key;
            it.pkg = ai.packageName;
            String defLabel = ri.loadLabel(pm).toString();
            it.label = MainActivity.getCustomAppName(this, key, ai.packageName, defLabel);
            it.icon = ri.loadIcon(pm);
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (leanback) launch.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            it.launch = launch;
            out.add(it);
        }
    }

    private void pageHiddenApps() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_hidden_apps));
        List<HiddenAppItem> hiddenList = listHiddenAppItems();
        if (hiddenList.isEmpty()) {
            row((I18n.isRtl(this) ? "\u2192  " : "\u2190  ") + I18n.resourceString(SettingsActivity.this, R.string.text_back_to_apps), () -> "", () -> {
                showingHiddenApps = false;
                rebuildMainRows();
                focusFirstRow();
            });
            infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_no_hidden_apps_hold_ok_on_any_app_on_the));
            return;
        }
        for (int i = 0; i < hiddenList.size(); i++) {
            final HiddenAppItem item = hiddenList.get(i);
            hiddenAppRow(item);
        }
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_ok_to_open_hold_ok_to_restore));
    }

    private void hiddenAppRow(HiddenAppItem item) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int icSz = u(28);
        ImageView iconIv = new ImageView(this);
        iconIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (item.icon != null) iconIv.setImageDrawable(item.icon);
        final float icRad = u(6.5f);
        iconIv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), icRad);
            }
        });
        iconIv.setClipToOutline(true);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(icSz, icSz);
        icLp.setMarginEnd(u(12));
        r.addView(iconIv, icLp);

        TextView l = new TextView(this);
        l.setText(item.label);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(I18n.resourceString(SettingsActivity.this, R.string.text_open_4f0000));
        v.setTextColor(0x9EFFFFFF);
        v.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(v, 13.5f);
        v.setSingleLine(true);
        r.addView(v);

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            v.setTextColor(has ? 0x99000000 : 0x9EFFFFFF);
        });

        r.setOnClickListener(view -> {
            if (MainActivity.INPUTS_KEY.equals(item.key)) {
                ControlPanelOverlay.openInputs(this, null);
                return;
            }
            if (item.launch != null && Suggestions.start(this, item.launch)) {
                overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                return;
            }
            if (item.pkg != null) {
                Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(item.pkg);
                if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(item.pkg);
                if (launch != null && Suggestions.start(this, launch)) {
                    overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                }
            }
        });

        r.setOnLongClickListener(view -> {
            Set<String> hidden = new HashSet<>(Profiles.homePrefs(this).getStringSet(MainActivity.PREF_HIDDEN, Collections.emptySet()));
            Set<String> other = new HashSet<>(Profiles.homePrefs(this).getStringSet(MainActivity.PREF_OTHER, Collections.emptySet()));
            if (item.key != null) {
                hidden.remove(item.key);
                other.remove(item.key);
            }
            if (item.pkg != null) {
                hidden.remove(item.pkg);
                other.remove(item.pkg);
            }
            Profiles.homePrefs(this).edit()
                    .putStringSet(MainActivity.PREF_HIDDEN, hidden)
                    .putStringSet(MainActivity.PREF_OTHER, other)
                    .apply();
            rebuildMainRows();
            showStatusMsg(I18n.resourceString(this, R.string.message_app_restored_home, item.label));
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(42));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    void pageProfiles() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_people_c31d4d));
        List<Profiles.Profile> ps = Profiles.list(this);
        String curId = Profiles.current(this).id;
        for (int i = 0; i < ps.size(); i++) {
            final Profiles.Profile p = ps.get(i);
            boolean isActive = p.id.equals(curId);
            choiceRow(
                    I18n.profileName(this, p.name),
                    isActive ? I18n.resourceString(SettingsActivity.this, R.string.text_watching_now_ok_to_switch_or_rename)
                             : I18n.resourceString(SettingsActivity.this, R.string.text_ok_to_switch_or_edit),
                    isActive,
                    () -> openSubMenu("profile_actions:" + p.id));
        }
        if (Profiles.canAdd(this)) {
            row(I18n.resourceString(SettingsActivity.this, R.string.text_add_a_person_ef7edb), () -> chev(), this::promptAddPerson);
        }
        section(I18n.resourceString(SettingsActivity.this, R.string.text_options));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_ask_who_is_watching_on_wake),
                () -> Profiles.askOnWake(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> Profiles.setAskOnWake(this, !Profiles.askOnWake(this)));
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_each_person_keeps_their_own_keep_watchin));
    }

    private void promptRenamePerson(Profiles.Profile p, int rowIdx) {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setText(I18n.profileName(this, p.name));
        in.setSelection(in.getText().length());
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_rename_person))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_save), (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) {
                        p.name = n;
                        Profiles.update(this, p);
                        rebuildMainRows();
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void promptAddPerson() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(I18n.resourceString(SettingsActivity.this, R.string.text_name));
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_add_a_person_f14c89))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_add), (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) {
                        Profiles.add(this, n);
                        selectSection(PROFILES, true, Profiles.list(this).size() - 1);
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    void pageControlPanel() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_background_mode));
        row(I18n.resourceString(this, R.string.text_cp_bg_mode),
                () -> ControlPanelOverlay.getBgModeLabel(this),
                () -> {
                    ControlPanelOverlay.cycleBgMode(this);
                    refreshAll();
                });
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_tip_blur_can_t_be_shown_while_a_video_is));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_universal_shortcut_actions));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_wi_fi),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_WIFI),
                () -> openSubMenu("cp_app:" + ControlPanelOverlay.ACTION_WIFI));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_bluetooth),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_BLUETOOTH),
                () -> openSubMenu("cp_app:" + ControlPanelOverlay.ACTION_BLUETOOTH));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_sound),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_SOUND),
                () -> openSubMenu("cp_app:" + ControlPanelOverlay.ACTION_SOUND));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_inputs_control_panel_dock),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_INPUTS),
                () -> openSubMenu("cp_app:" + ControlPanelOverlay.ACTION_INPUTS));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_ambient_mode),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_AMBIENT),
                () -> openSubMenu("cp_app:" + ControlPanelOverlay.ACTION_AMBIENT));
        if (ControlPanelOverlay.isAmbientLockScreenMode(this)) {
            row(I18n.resourceString(SettingsActivity.this, R.string.text_lock_screen_settings_8b5317),
                    () -> Scenes.lockClockPosLabel(this) + " · " + Scenes.lockIdleLabel(this),
                    () -> openSubMenu("lock_screen_settings"));
        }
        row(I18n.resourceString(SettingsActivity.this, R.string.text_power_off),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_POWER),
                () -> openSubMenu("cp_app:" + ControlPanelOverlay.ACTION_POWER));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_control_panel_items_show_hide));
        for (ControlPanelOverlay.ControlPanelItem item : ControlPanelOverlay.ALL_CP_ITEMS) {
            final String itemKey = item.key;
            row(ControlPanelOverlay.getItemTitle(this, itemKey),
                    () -> ControlPanelOverlay.isItemEnabled(this, itemKey) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        boolean next = !ControlPanelOverlay.isItemEnabled(this, itemKey);
                        ControlPanelOverlay.setItemEnabled(this, itemKey, next);
                        refreshAll();
                    });
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_inputs_app_on_dock));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_show_inputs_app_on_dock),
                () -> Inputs.showCard(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !Inputs.showCard(this);
                    Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, next).apply();
                    refreshAll();
                });
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_inputs),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_hide_inputs_or_change_name_of_hdmi_tv_in),
                () -> openSubMenu("cp_inputs_mgr"));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_reset_043c3c));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_reset_control_panel_actions_to_default),
                () -> "",
                () -> {
                    ControlPanelOverlay.clearAllCustomActions(this);
                    for (ControlPanelOverlay.ControlPanelItem item : ControlPanelOverlay.ALL_CP_ITEMS) {
                        ControlPanelOverlay.setItemEnabled(this, item.key, true);
                    }
                    rebuildMainRows();
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_control_panel_actions_and_items_reset_to));
                });

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_each_tv_brand_uses_different_package_and));
    }

    void pageButtonMapper() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_accessibility_service_a268b7));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_button_mapper_accessibility_service),
                () -> ButtonMapper.isServiceEnabled(this)
                        ? I18n.resourceString(SettingsActivity.this, R.string.text_active_works_in_all_apps)
                        : I18n.resourceString(SettingsActivity.this, R.string.text_enable_in_accessibility),
                () -> {
                    Intent acc = Perms.accessibilityScreen(this);
                    if (acc != null && Suggestions.start(this, acc)) {
                        return;
                    }
                    dialog(I18n.resourceString(SettingsActivity.this, R.string.text_accessibility_service_bb9182))
                            .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_this_tv_does_not_have_a_built_in_accessi))
                            .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_enable_via_adb), (d, w) -> pairOrRepairAdb())
                            .setNeutralButton(I18n.resourceString(SettingsActivity.this, R.string.text_tv_settings_32ce87), (d, w) -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)))
                            .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                            .show();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_fix_home_button_launch_tvos_launcher),
                () -> ButtonMapper.isHomeFixEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !ButtonMapper.isHomeFixEnabled(this);
                    ButtonMapper.setHomeFixEnabled(this, next);
                    refreshAll();
                    if (next && !ButtonMapper.isServiceEnabled(this)) {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_home_button_fix_enabled_please_also_enab));
                    } else {
                        showStatusMsg(next
                                ? I18n.resourceString(SettingsActivity.this, R.string.text_home_button_fix_enabled_pressing_home_wi)
                                : I18n.resourceString(SettingsActivity.this, R.string.text_home_button_fix_disabled));
                    }
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_long_press_volume_button_fix),
                () -> ButtonMapper.isVolumeHoldFixEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !ButtonMapper.isVolumeHoldFixEnabled(this);
                    ButtonMapper.setVolumeHoldFixEnabled(this, next);
                    refreshAll();
                    showStatusMsg(next
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_long_press_volume_fix_enabled_for_tvs_wh)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_long_press_volume_fix_disabled_using_nat));
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_volume_button_fix_mode_28c643),
                () -> ButtonMapper.getVolumeHoldFixModeLabel(this),
                this::showVolumeFixModeSelectorDialog);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_button_remapping));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_add_new_button_to_remap_e322f7),
                () -> "＋",
                () -> ButtonMapper.showCaptureAndConfigureDialog(this, this::rebuildMainRows));

        List<Integer> mappedKeys = ButtonMapper.getMappedKeys(this);
        if (!mappedKeys.isEmpty()) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_remapped_buttons_1x_2x_3x_tap));
            for (final int keyCode : mappedKeys) {
                twoLineRow(ButtonMapper.keyLabel(this, keyCode),
                        () -> ButtonMapper.keySummaryLabel(this, keyCode),
                        () -> openButtonKeySubMenu(keyCode));
            }
            row(I18n.resourceString(SettingsActivity.this, R.string.text_clear_all_remapped_buttons),
                    () -> "",
                    () -> {
                        ButtonMapper.clearAll(this);
                        rebuildMainRows();
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_all_button_mappings_cleared));
                    });
        }

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_assign_single_tap_1x_double_tap_2x_and_t));
    }

    void pageQuickAndHome() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_home_permissions));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_default_home_screen_57e442),
                () -> Perms.isDefaultHome(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_active_60b543) : I18n.resourceString(SettingsActivity.this, R.string.text_set_as_default),
                () -> Perms.requestDefaultHome(this));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_continue_watching_dnd_permission),
                () -> Perms.hasListener(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_allowed)
                        : Perms.listenerSupported(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_allow) : I18n.resourceString(SettingsActivity.this, R.string.text_not_on_this_tv),
                () -> Perms.requestListener(this));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_navigation_click_sounds),
                () -> Clicks.enabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> Clicks.setEnabled(this, !Clicks.enabled(this)));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_presentation_system));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_presentation_mode),
                () -> com.techgenius.tvoslauncher.present.Presenter.enabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean on = !com.techgenius.tvoslauncher.present.Presenter.enabled(this);
                    com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_ENABLED, on);
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_presentation_address_qr_code),
                () -> com.techgenius.tvoslauncher.present.Presenter.enabled(this)
                        ? com.techgenius.tvoslauncher.present.Presenter.address().replace("http://", "")
                        : I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_first),
                this::showPresentInfo);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_run_first_time_setup_again),
                () -> chev(),
                () -> {
                    getSharedPreferences("setup", MODE_PRIVATE).edit().putInt("cur_step", 0).apply();
                    startActivity(new Intent(this, SetupActivity.class).putExtra("step", 0));
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_open_android_tv_settings),
                () -> chev(),
                () -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_backup_restore));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_backup_settings_to_download),
                () -> "Download/" + CustomMedia.SETTINGS_BACKUP_FILENAME,
                () -> CustomMedia.backupSettingsToDownload(this, this::refreshAll));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_restore_settings_from_folder),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_840d4f),
                () -> CustomMedia.showRestoreSettingsPicker(this, this::refreshAll));
    }

    void pageAdvanced() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_uninstall_f2f89e));
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_uninstall_tvos_launcher),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_restore_debloated_apps_disabled_launcher),
                this::showUninstallLauncherFlow);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_adb_security_mode));
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_adb_operating_mode_01f44c),
                () -> LocalAdb.getAdbModeLabel(this),
                this::showAdbModeSelectorDialog);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_adb_connection_status),
                () -> LocalAdb.getStatusLabel(this),
                this::pairOrRepairAdb);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_pair_repair_adb_connection),
                () -> LocalAdb.isPaired(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_repair) : I18n.resourceString(SettingsActivity.this, R.string.text_pair_now),
                this::pairOrRepairAdb);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_off_adb),
                () -> (LocalAdb.isAdbSettingEnabled(this) || LocalAdb.portReachable)
                        ? I18n.resourceString(SettingsActivity.this, R.string.status_on)
                        : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                this::toggleAdbPort);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_check_permissions),
                () -> advPermSummary != null ? advPermSummary : "…",
                () -> openSubMenu("check_permissions"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_remove_revoke_all_permissions_c108cc),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_revoke),
                this::confirmRevokeAllPermissions);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_startup_keep_alive));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_launch_automatically_on_system_boot),
                () -> com.techgenius.tvoslauncher.present.BootReceiver.isLaunchOnBootEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !com.techgenius.tvoslauncher.present.BootReceiver.isLaunchOnBootEnabled(this);
                    com.techgenius.tvoslauncher.present.BootReceiver.setLaunchOnBootEnabled(this, next);
                    refreshAll();
                    showStatusMsg(next
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_tvos_launcher_will_launch_immediately_on)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_automatic_launch_on_boot_disabled));
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_keep_alive_auto_repair_accessibility),
                () -> LocalAdb.isKeepAliveEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_on_default) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                this::toggleKeepAliveWithWarning);
        row(I18n.resourceString(this, R.string.text_caffeine_title),
                () -> PowerService.isCaffeineEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !PowerService.isCaffeineEnabled(this);
                    PowerService.setCaffeineEnabled(this, next);
                    refreshAll();
                    showStatusMsg(next
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_caffeine_enabled_tv_will_stay_awake_and)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_caffeine_disabled));
                });
        infoNote(I18n.resourceString(this, R.string.text_caffeine_desc));

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_security_note_keep_alive_is_enabled_by_d));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_cleanyourtv_beta));
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_download_cleanyourtv),
                () -> advCytDownloadSummary != null ? advCytDownloadSummary : "…",
                () -> CleanYourTv.downloadAndExtractLatestRelease(this, () -> {
                    loadAdvancedSummariesAsync();
                    rebuildMainRows();
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_cleanyourtv_downloaded_to_download_folde));
                }));
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_choose_tv_model_eb4b40),
                () -> advCytModelSummary != null ? advCytModelSummary : "…",
                () -> {
                    cytAutoContinueToDebloat = false;
                    int count = CleanYourTv.getExtractedModelCountOnDisk(this);
                    if (count <= 1 || (!CleanYourTv.getSelectedModelSlug(this).isEmpty() && !CleanYourTv.hasSelectedModelFileOnDisk(this))) {
                        boolean wasDeleted = !CleanYourTv.getSelectedModelSlug(this).isEmpty()
                                && !CleanYourTv.hasSelectedModelFileOnDisk(this);
                        CleanYourTv.promptDownloadRequired(this, wasDeleted, () -> {
                            loadAdvancedSummariesAsync();
                            openSubMenu("cyt_models");
                        });
                    } else {
                        openSubMenu("cyt_models");
                    }
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_debloat_status),
                () -> advDebloatSummary != null ? advDebloatSummary : "…",
                () -> openSubMenu("cyt_saved"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_debloat_tv_3ec289),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_start),
                () -> {
                    if (!CleanYourTv.hasCleanYourTvFiles(this)) {
                        boolean wasDeleted = !CleanYourTv.getSelectedModelSlug(this).isEmpty();
                        CleanYourTv.promptDownloadRequired(this, wasDeleted, () -> {
                            loadAdvancedSummariesAsync();
                            if (CleanYourTv.getSelectedModelSlug(this).isEmpty()) {
                                cytAutoContinueToDebloat = true;
                                openSubMenu("cyt_models");
                            } else {
                                cytChecklistItems = null;
                                openSubMenu("cyt_debloat");
                            }
                        });
                        return;
                    }
                    if (CleanYourTv.getSelectedModelSlug(this).isEmpty()) {
                        cytAutoContinueToDebloat = true;
                        if (!CleanYourTv.hasFullExamplesExtracted(this)) {
                            CleanYourTv.promptDownloadRequired(this, false, () -> {
                                loadAdvancedSummariesAsync();
                                openSubMenu("cyt_models");
                            });
                        } else {
                            openSubMenu("cyt_models");
                        }
                    } else {
                        cytChecklistItems = null;
                        openSubMenu("cyt_debloat");
                    }
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_disable_system_launchers_e1810e),
                () -> advLaunchersSummary != null ? advLaunchersSummary : "…",
                () -> openSubMenu("cyt_launchers"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_manual_debloat_all_system_apps_99b35c),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_advanced_981d59),
                this::promptManualDebloatWarning);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_disabled_apps_backup),
                () -> advSavedSummary != null ? advSavedSummary : "…",
                () -> openSubMenu("cyt_saved"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_restore_debloated_apps_3d05c1),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_restore),
                () -> openSubMenu("cyt_restore"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_ad_blocking_private_dns_adguard),
                () -> CleanYourTv.isAdguardDnsActive(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> CleanYourTv.toggleAdguardDns(this, this::refreshAll));

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_cleanyourtv_beta_runs_the_cleanyourtv_de));

        // Expensive PackageManager scans (launchers, debloat status) run off the UI thread so the
        // page's first frames aren't blocked; rows show "…" until the values arrive.
        loadAdvancedSummariesAsync();
        if (LocalAdb.getAdbMode(this) == LocalAdb.ADB_MODE_PERSISTENT) {
            LocalAdb.probePortAsync(getApplicationContext(), () -> runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && ADVANCED.equals(currentSection) && activeSubMenu == null) {
                    refreshAll();
                }
            }));
        } else {
            LocalAdb.portReachable = false;
        }
    }

    private volatile String advPermSummary, advCytDownloadSummary, advCytModelSummary,
            advLaunchersSummary, advDebloatSummary, advSavedSummary;
    private volatile boolean advSummariesLoading;

    private void loadAdvancedSummariesAsync() {
        if (advSummariesLoading) return;
        advSummariesLoading = true;
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String perm, cytDl, cytModel, launchers, debloat, saved;
            try {
                perm = permissionsSummaryCount();
                cytDl = CleanYourTv.getDownloadStatusSummary(app);
                cytModel = CleanYourTv.getSelectedModelSummary(app);
                launchers = CleanYourTv.getSystemLaunchersSummary(app);
                debloat = CleanYourTv.getDebloatStatusSummary(app);
                saved = CleanYourTv.getSavedDisabledSummary(app);
            } catch (Throwable t) {
                perm = cytDl = cytModel = launchers = debloat = saved = "";
            }
            final String p = perm != null ? perm : "";
            final String dl = cytDl != null ? cytDl : "";
            final String cm = cytModel != null ? cytModel : "";
            final String l = launchers != null ? launchers : "";
            final String d = debloat != null ? debloat : "";
            final String s = saved != null ? saved : "";
            runOnUiThread(() -> {
                advSummariesLoading = false;
                boolean changed = !java.util.Objects.equals(p, advPermSummary)
                        || !java.util.Objects.equals(dl, advCytDownloadSummary)
                        || !java.util.Objects.equals(cm, advCytModelSummary)
                        || !java.util.Objects.equals(l, advLaunchersSummary)
                        || !java.util.Objects.equals(d, advDebloatSummary)
                        || !java.util.Objects.equals(s, advSavedSummary);
                advPermSummary = p;
                advCytDownloadSummary = dl;
                advCytModelSummary = cm;
                advLaunchersSummary = l;
                advDebloatSummary = d;
                advSavedSummary = s;
                if (changed && !isFinishing() && !isDestroyed()
                        && ADVANCED.equals(currentSection) && activeSubMenu == null) {
                    refreshAll();
                }
            });
        }, "adv-summaries").start();
    }

    private boolean checkingLatestRelease = false;

    void pageUpdates() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_updates_22906d));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_automatic_updates),
                () -> AppUpdater.isAutoUpdateEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    boolean next = !AppUpdater.isAutoUpdateEnabled(this);
                    AppUpdater.setAutoUpdateEnabled(this, next);
                    refreshAll();
                    showStatusMsg(next
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_automatic_updates_enabled)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_automatic_updates_disabled));
                    if (next) {
                        AppUpdater.checkAutoUpdateIfNeeded(this);
                    }
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_check_for_updates),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_check_now),
                () -> AppUpdater.manualCheckAndPrompt(this, this::refreshAll));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_version_info));
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_current_version), "v" + AppUpdater.getCurrentVersion(this), null);
        infoRowDynamic(I18n.resourceString(SettingsActivity.this, R.string.text_github_release_latest_version),
                () -> {
                    String latest = AppUpdater.getCachedLatestVersion(this);
                    if (latest != null && !latest.isEmpty()) {
                        return "v" + latest;
                    }
                    return checkingLatestRelease
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_checking)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_tap_check_for_updates);
                },
                null);

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_checks_github_releases_techgenius1_tvos));

        if (!checkingLatestRelease) {
            checkingLatestRelease = true;
            refreshAll();
            AppUpdater.checkForUpdates(this, (success, latestVersion, downloadUrl, updateAvailable, errorMsg) -> {
                checkingLatestRelease = false;
                if (!isFinishing()) {
                    refreshAll();
                    if (success && updateAvailable) {
                        AppUpdater.showUpdateAvailableDialog(this, latestVersion, downloadUrl,
                                AppUpdater.getCachedChangelog(this), this::refreshAll);
                    }
                }
            });
        }
    }

    private void showVolumeFixModeSelectorDialog() {
        int curMode = ButtonMapper.getVolumeHoldFixMode(this);
        String[] opts = new String[]{
            (curMode == ButtonMapper.VOLUME_FIX_MODE_1 ? "✓ " : "   ")
                    + I18n.resourceString(SettingsActivity.this, R.string.text_mode_1_standard_default_for_most_android),
            (curMode == ButtonMapper.VOLUME_FIX_MODE_2 ? "✓ " : "   ")
                    + I18n.resourceString(SettingsActivity.this, R.string.text_mode_2_hy320_projector_audioservice_ir_p),
            (curMode == ButtonMapper.VOLUME_FIX_MODE_3 ? "✓ " : "   ")
                    + I18n.resourceString(SettingsActivity.this, R.string.text_mode_3_onn_tv_box_fast_release_stop_0_ex)
        };
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_volume_button_fix_mode_87cb31))
                .setItems(opts, (d, which) -> {
                    int nextMode;
                    if (which == 2) {
                        nextMode = ButtonMapper.VOLUME_FIX_MODE_3;
                    } else if (which == 1) {
                        nextMode = ButtonMapper.VOLUME_FIX_MODE_2;
                    } else {
                        nextMode = ButtonMapper.VOLUME_FIX_MODE_1;
                    }
                    ButtonMapper.setVolumeHoldFixMode(this, nextMode);
                    refreshAll();
                    if (nextMode == ButtonMapper.VOLUME_FIX_MODE_1) {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_volume_fix_mode_1_standard_selected));
                    } else if (nextMode == ButtonMapper.VOLUME_FIX_MODE_2) {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_volume_fix_mode_2_hy320_projector_select));
                    } else {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_volume_fix_mode_3_onn_tv_box_selected));
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    void pageAbout() {
        section(I18n.resourceString(SettingsActivity.this, R.string.text_application));
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen), "tvOS Launcher", null);
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_developer), "techgenius", null);
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_version), version(), null);
        infoRow(I18n.resourceString(SettingsActivity.this, R.string.text_settings_kept), I18n.resourceString(SettingsActivity.this, R.string.text_on_this_tv), null);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_backup_restore));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_backup_settings_to_download),
                () -> "Download/" + CustomMedia.SETTINGS_BACKUP_FILENAME,
                () -> CustomMedia.backupSettingsToDownload(this, this::refreshAll));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_restore_settings_from_folder),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_840d4f),
                () -> CustomMedia.showRestoreSettingsPicker(this, this::refreshAll));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_reset_043c3c));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_reset_settings_to_defaults),
                () -> "",
                () -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(I18n.resourceString(SettingsActivity.this, R.string.text_reset_settings))
                        .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_this_resets_look_scenes_clock_and_langua))
                        .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_reset_a1f27d), (d, w) -> resetAllAppDataAndReturnToSetup())
                        .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                        .show());
    }

    /**
     * Signs the dock's continue-watching popup out of Nuvio/Stremio. Only the launcher's session
     * ends; the app itself stays signed in. Signing back in is the QR on the app's dock tile.
     */
    private void confirmWatchSignOut(WatchCloud cloud) {
        if (!cloud.isSignedIn()) {
            showStatusMsg(I18n.resourceString(this, R.string.message_cloud_sign_in_instructions, cloud.name()));
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.resourceString(this, R.string.message_cloud_sign_out_question, cloud.name()))
                .setMessage(I18n.resourceString(this, R.string.message_cloud_sign_out_details, cloud.name()))
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_sign_out), (d, w) -> new Thread(() -> {
                    cloud.signOut();  // tells the server too, so it can take a moment
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        refreshAll();
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.message_cloud_signed_out, cloud.name()));
                    });
                }, "watch-sign-out").start())
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void resetAllAppDataAndReturnToSetup() {
        try {
            java.io.File spDir = new java.io.File(getApplicationInfo().dataDir, "shared_prefs");
            if (spDir.exists() && spDir.isDirectory()) {
                java.io.File[] files = spDir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        String name = f.getName();
                        if (name.endsWith(".xml")) {
                            String prefName = name.substring(0, name.length() - 4);
                            getSharedPreferences(prefName, MODE_PRIVATE).edit().clear().commit();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String[] knownPrefs = {
            "settings", "setup", "profiles", "home", "home_default",
            "app_custom_names", "button_mapper", "control_panel",
            "weather", "suggestions", "inputs", "clicks",
            "media_watch", "cleanyourtv_prefs", "local_adb", "present",
            "custom_media", "app_updater"
        };
        for (String p : knownPrefs) {
            try {
                getSharedPreferences(p, MODE_PRIVATE).edit().clear().commit();
            } catch (Throwable ignored) {
            }
        }
        try {
            MediaWatchService.Store.clear(this);
        } catch (Throwable ignored) {
        }
        I18n.resetCache();
        Intent setup = new Intent(this, SetupActivity.class)
                .putExtra("step", 0)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(setup);
        finish();
    }

    private String permissionsSummaryCount() {
        int granted = 0;
        int total = 4;
        if (Perms.isDefaultHome(this)) granted++;
        if (ButtonMapper.isServiceEnabled(this)) granted++;
        if (Perms.hasListener(this)) granted++;
        if (Perms.hasLocation(this)) granted++;
        return granted + " / " + total + " " + I18n.resourceString(SettingsActivity.this, R.string.text_active_42209d);
    }

    private void showAdbModeSelectorDialog() {
        int curMode = LocalAdb.getAdbMode(this);
        String[] opts = {
            (curMode == LocalAdb.ADB_MODE_SECURE_CLOSE ? "✓ " : "   ")
                    + I18n.resourceString(SettingsActivity.this, R.string.text_secure_mode_grant_write_secure_settings),
            (curMode == LocalAdb.ADB_MODE_PERSISTENT ? "✓ " : "   ")
                    + I18n.resourceString(SettingsActivity.this, R.string.text_persistent_adb_mode_keep_network_adb_ope)
        };
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_adb_operating_mode_1c02d4))
                .setItems(opts, (d, which) -> {
                    int nextMode = (which == 1) ? LocalAdb.ADB_MODE_PERSISTENT : LocalAdb.ADB_MODE_SECURE_CLOSE;
                    if (nextMode == LocalAdb.ADB_MODE_PERSISTENT
                            && !LocalAdb.isAdbSettingEnabled(this) && !LocalAdb.portReachable) {
                        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_enable_adb_for_persistent_mode))
                                .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_adb_debugging_is_currently_turned_off_pe))
                                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_enable_adb_switch), (d2, w2) -> {
                                    AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_turning_on_adb))
                                            .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_enabling_adb_debugging_and_switching_to))
                                            .setCancelable(false)
                                            .create();
                                    progress.show();
                                    LocalAdb.enableAdbPort(this, (success, msg) -> runOnUiThread(() -> {
                                        if (isFinishing()) return;
                                        try {
                                            progress.dismiss();
                                        } catch (Throwable ignored) {
                                        }
                                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_PERSISTENT);
                                        refreshAll();
                                        if (list != null) {
                                            list.postDelayed(this::refreshAll, 500);
                                        }
                                        if (success) {
                                            showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_adb_enabled_and_switched_to_persistent_a));
                                        } else {
                                            dialog(I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_adb_debugging))
                                                    .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_open_developer_options_and_turn_on_usb_d_c22f55))
                                                    .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_open_developer_options), (d3, w3) -> {
                                                        Intent dev = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                                                        if (!Suggestions.start(this, dev)) {
                                                            Intent about = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                                                            if (!Suggestions.start(this, about)) {
                                                                Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                                                            }
                                                        }
                                                    })
                                                    .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                                                    .show();
                                        }
                                    }));
                                })
                                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                                .show();
                        return;
                    }
                    LocalAdb.setAdbMode(this, nextMode);
                    refreshAll();
                    if (nextMode == LocalAdb.ADB_MODE_SECURE_CLOSE) {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_secure_mode_selected_adb_will_grant_writ));
                    } else {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_persistent_adb_mode_selected_port_5555_s));
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void toggleAdbPort() {
        boolean adbOn = LocalAdb.isAdbSettingEnabled(this) || LocalAdb.portReachable;
        if (adbOn) {
            confirmCloseAdbPort();
        } else {
            AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_turning_on_adb))
                    .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_enabling_adb_debugging))
                    .setCancelable(false)
                    .create();
            progress.show();
            LocalAdb.enableAdbPort(this, (success, msg) -> runOnUiThread(() -> {
                if (isFinishing()) return;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                refreshAll();
                if (list != null) {
                    list.postDelayed(this::refreshAll, 500);
                }
                if (success) {
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_adb_debugging_enabled));
                } else {
                    dialog(I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_adb_debugging))
                            .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_open_developer_options_and_turn_on_usb_d_9d4f5e))
                            .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_open_developer_options), (d, w) -> {
                                Intent dev = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                                if (!Suggestions.start(this, dev)) {
                                    Intent about = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                                    if (!Suggestions.start(this, about)) {
                                        Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                                    }
                                }
                            })
                            .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                            .show();
                }
            }));
        }
    }

    private void confirmCloseAdbPort() {
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_close_adb_disable_port_5555))
                .setMessage(I18n.resourceString(SettingsActivity.this, R.string.message_this_runs_settings_put_global_adb_enabled_0_over))
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_close_adb_now), (d, w) -> {
                    if (LocalAdb.getAdbMode(this) != LocalAdb.ADB_MODE_SECURE_CLOSE) {
                        LocalAdb.setAdbMode(this, LocalAdb.ADB_MODE_SECURE_CLOSE);
                    }
                    AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_closing_adb))
                            .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_running_settings_put_global_adb_enabled))
                            .setCancelable(false)
                            .create();
                    progress.show();
                    LocalAdb.closeAdbPort(this, (success, msg) -> runOnUiThread(() -> {
                        if (isFinishing()) return;
                        try {
                            progress.dismiss();
                        } catch (Throwable ignored) {
                        }
                        refreshAll();
                        showStatusMsg(msg);
                    }));
                })
                .setNeutralButton(I18n.resourceString(SettingsActivity.this, R.string.text_developer_options), (d, w) -> {
                    Intent dev = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    if (!Suggestions.start(this, dev)) {
                        Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void toggleKeepAliveWithWarning() {
        boolean currentlyOn = LocalAdb.isKeepAliveEnabled(this);
        if (currentlyOn) {
            LocalAdb.setKeepAliveEnabled(this, false);
            refreshAll();
            showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_keep_alive_disabled));
            return;
        }
        boolean hasSecure = LocalAdb.hasWriteSecureSettings(this);
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_enable_keep_alive_5ae818))
                .setMessage(hasSecure
                        ? I18n.resourceString(SettingsActivity.this, R.string.message_keep_alive_monitors_the_accessibility_service_an)
                        : I18n.resourceString(SettingsActivity.this, R.string.message_security_warning_keep_alive_monitors_and_restore))
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_enable_keep_alive_348093), (d, w) -> {
                    LocalAdb.setKeepAliveEnabled(this, true);
                    LocalAdb.checkAndRepairPermissionsIfNeeded(this, true);
                    refreshAll();
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_keep_alive_enabled));
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void pairOrRepairAdb() {
        AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_pair_repair_adb))
                .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_connecting_to_local_adb_on_port_5555_and))
                .setCancelable(false)
                .create();
        progress.show();

        LocalAdb.grantAllPermissions(this, (success, msg) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            refreshAll();
            if (list != null) {
                list.postDelayed(this::refreshAll, 350);
                list.postDelayed(this::refreshAll, 900);
            }
            if (success) {
                showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_adb_paired_and_all_permissions_granted));
            } else {
                dialog(I18n.resourceString(SettingsActivity.this, R.string.text_adb_debugging_needed))
                        .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_could_not_connect_to_local_adb_on_port_5_2d1be7))
                        .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_open_developer_options), (d, w) -> {
                            Intent dev = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                            if (!Suggestions.start(this, dev)) {
                                Intent about = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                                if (!Suggestions.start(this, about)) {
                                    Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                                }
                            }
                        })
                        .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                        .show();
            }
        }));
    }

    private void confirmRevokeAllPermissions() {
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_remove_revoke_all_permissions_0a9dac))
                .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_this_will_disable_keep_alive_and_remove))
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_revoke_all_permissions), (d, w) -> {
                    AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_revoking_permissions))
                            .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_revoking_all_permissions))
                            .setCancelable(false)
                            .create();
                    progress.show();
                    LocalAdb.revokeAllPermissions(this, (success, msg) -> runOnUiThread(() -> {
                        if (isFinishing()) return;
                        try {
                            progress.dismiss();
                        } catch (Throwable ignored) {
                        }
                        refreshAll();
                        if (list != null) {
                            list.postDelayed(this::refreshAll, 350);
                            list.postDelayed(this::refreshAll, 900);
                        }
                        showStatusMsg(msg);
                    }));
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void showUninstallLauncherFlow() {
        List<String> savedDebloated = CleanYourTv.getSavedDisabledPackages(this);
        List<CleanYourTv.DebloatItem> sysLaunchers = CleanYourTv.getInstalledSystemLaunchers(this, null, null);
        final List<String> toRestore = new ArrayList<>(savedDebloated);
        for (CleanYourTv.DebloatItem it : sysLaunchers) {
            if (!toRestore.contains(it.pkg)) {
                toRestore.add(it.pkg);
            }
        }

        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_uninstall_tvos_launcher))
                .setMessage(I18n.resourceString(this, R.string.uninstall_restore_permissions_explanation))
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_restore_apps_launchers_first), (d, w) -> {
                    if (!toRestore.isEmpty()) {
                        CleanYourTv.enablePackagesList(this, toRestore, true, this::revokeAndRedirectToUninstall);
                    } else {
                        revokeAndRedirectToUninstall();
                    }
                })
                .setNeutralButton(I18n.resourceString(SettingsActivity.this, R.string.text_skip_restore_continue), (d, w) -> revokeAndRedirectToUninstall())
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void revokeAndRedirectToUninstall() {
        LocalAdb.setKeepAliveEnabled(this, false);
        AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_preparing_uninstall))
                .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_revoking_all_permissions_and_opening_and))
                .setCancelable(false)
                .create();
        progress.show();
        LocalAdb.revokeAllPermissions(this, (success, msg) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            try {
                progress.dismiss();
            } catch (Throwable ignored) {
            }
            android.net.Uri pkgUri = android.net.Uri.parse("package:" + getPackageName());
            Intent uninstall = new Intent(Intent.ACTION_DELETE, pkgUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!Suggestions.start(this, uninstall)) {
                Intent uninstallAlt = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, pkgUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!Suggestions.start(this, uninstallAlt)) {
                    Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (!Suggestions.start(this, details)) {
                        Suggestions.start(this, new Intent(Settings.ACTION_APPLICATION_SETTINGS));
                    }
                }
            }
        }));
    }

    private void promptManualDebloatWarning() {
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_manual_debloat_warning))
                .setMessage(I18n.resourceString(SettingsActivity.this, R.string.message_warning_manual_debloat_lists_all_system_apps_ins))
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_i_understand_open_manual_debloat), (d, w) -> {
                    cytManualItems = null;
                    cytManualFilterQuery = "";
                    openSubMenu("cyt_manual");
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    // ================================================================ Native Right-Side Sub-Menus

    private void backSubMenuRow() {
        row((I18n.isRtl(this) ? "\u2192  " : "\u2190  ") + I18n.resourceString(SettingsActivity.this, R.string.text_back), () -> "", this::closeSubMenu);
    }

    private void renderSubMenu(String subId) {
        if (subId == null) return;
        if ("dock_size".equals(subId)) {
            subMenuDockSize();
        } else if ("home_dashboard".equals(subId)) {
            subMenuHomeDashboard();
        } else if ("releases_countries".equals(subId)) {
            subMenuReleasesCountries();
        } else if ("news_country".equals(subId)) {
            subMenuNewsCountry();
        } else if ("prayer_location".equals(subId)) {
            subMenuPrayerLocation();
        } else if ("cp_inputs_mgr".equals(subId)) {
            subMenuControlPanelInputs();
        } else if (subId.startsWith("custom_wall:")) {
            subMenuCustomWallpaper(subId.substring("custom_wall:".length()));
        } else if ("ambient_themes".equals(subId)) {
            subMenuAmbientThemes();
        } else if ("lock_screen_settings".equals(subId)) {
            subMenuLockScreenSettings();
        } else if ("weather_source".equals(subId)) {
            subMenuWeatherSource();
        } else if ("top_shelf".equals(subId)) {
            subMenuTopShelf();
        } else if ("nuvio_apps".equals(subId)) {
            subMenuNuvioApps();
        } else if (subId.startsWith("profile_actions:")) {
            subMenuProfileActions(subId.substring("profile_actions:".length()));
        } else if (subId.startsWith("cp_app:")) {
            subMenuControlPanelApps(subId.substring("cp_app:".length()));
        } else if (subId.startsWith("cp_act:")) {
            String rest = subId.substring("cp_act:".length());
            int colon = rest.indexOf(':');
            if (colon > 0) {
                subMenuControlPanelActivities(rest.substring(0, colon), rest.substring(colon + 1));
            }
        } else if (subId.startsWith("bm_key:")) {
            try {
                int code = Integer.parseInt(subId.substring("bm_key:".length()));
                subMenuButtonKey(code);
            } catch (NumberFormatException ignored) {
                closeSubMenu();
            }
        } else if (subId.startsWith("bm_tap:")) {
            String rest = subId.substring("bm_tap:".length());
            int colon = rest.indexOf(':');
            if (colon > 0) {
                try {
                    int code = Integer.parseInt(rest.substring(0, colon));
                    int tap = Integer.parseInt(rest.substring(colon + 1));
                    subMenuButtonTapTarget(code, tap);
                } catch (NumberFormatException ignored) {
                    closeSubMenu();
                }
            }
        } else if ("default_home".equals(subId)) {
            subMenuDefaultHome();
        } else if ("check_permissions".equals(subId)) {
            subMenuCheckPermissions();
        } else if ("cyt_models".equals(subId)) {
            subMenuCytModels();
        } else if ("cyt_models_all".equals(subId)) {
            subMenuCytAllModels();
        } else if ("cyt_models_scan".equals(subId)) {
            subMenuCytScannedModels();
        } else if ("cyt_debloat".equals(subId)) {
            subMenuCytDebloat();
        } else if ("cyt_launchers".equals(subId)) {
            subMenuCytSystemLaunchers();
        } else if ("cyt_manual".equals(subId)) {
            subMenuCytManualDebloat();
        } else if ("cyt_saved".equals(subId)) {
            subMenuCytSavedDisabled();
        } else if ("cyt_restore".equals(subId)) {
            subMenuCytRestore();
        }
    }

    private void subMenuControlPanelInputs() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_inputs));
        backSubMenuRow();
        List<Inputs.Input> allInputs = Inputs.listAllForSettings(this);
        for (final Inputs.Input in : allInputs) {
            final String key = Inputs.getInputKey(in);
            final String origTitle = in.origLabel != null && !in.origLabel.isEmpty() ? in.origLabel : in.label;
            section(origTitle.toUpperCase(I18n.locale(this)));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_hide_input),
                    () -> Inputs.isInputHidden(this, key) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        boolean next = !Inputs.isInputHidden(this, key);
                        Inputs.setInputHidden(this, key, next);
                        refreshAll();
                    });
            twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_custom_label),
                    () -> {
                        String custom = Inputs.getCustomInputLabel(this, key);
                        return custom.isEmpty()
                                ? I18n.resourceString(SettingsActivity.this, R.string.text_change_name_of_input)
                                : custom + "  ·  " + I18n.resourceString(SettingsActivity.this, R.string.text_change_name_of_input);
                    },
                    () -> promptCustomInputLabelDialog(in));
        }
    }

    private void promptCustomInputLabelDialog(final Inputs.Input in) {
        final String key = Inputs.getInputKey(in);
        final String origTitle = in.origLabel != null && !in.origLabel.isEmpty() ? in.origLabel : in.label;
        android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setHint(origTitle);
        String currentCustom = Inputs.getCustomInputLabel(this, key);
        et.setText(currentCustom);
        et.setSelection(et.getText().length());
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(et);
        AlertDialog dlg = dialog(origTitle + " — " + I18n.resourceString(SettingsActivity.this, R.string.text_custom_label))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_save), (d, w) -> {
                    Inputs.setCustomInputLabel(this, key, et.getText().toString().trim());
                    rebuildMainRows();
                })
                .setNeutralButton(I18n.resourceString(SettingsActivity.this, R.string.text_reset_to_default), (d, w) -> {
                    Inputs.setCustomInputLabel(this, key, "");
                    rebuildMainRows();
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(et, dlg);
        et.requestFocus();
    }

    private void subMenuDockSize() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_dock_size));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_dock_scale_50_to_50));
        int curPct = Scenes.getDockScalePercent(this);
        int[] steps = {50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150};
        for (int i = 0; i < steps.length; i++) {
            final int pct = steps[i];
            int diff = pct - 100;
            String label;
            if (diff == 0) {
                label = "100% (" + I18n.resourceString(SettingsActivity.this, R.string.text_default) + ")";
            } else if (diff > 0) {
                label = pct + "% (+" + diff + "%)";
            } else {
                label = pct + "% (" + diff + "%)";
            }
            String sub = diff > 0
                    ? I18n.resourceString(SettingsActivity.this, R.string.text_larger_dock_extra_apps_automatically_mov)
                    : (diff < 0 ? I18n.resourceString(SettingsActivity.this, R.string.text_smaller_more_compact_dock)
                                : I18n.resourceString(SettingsActivity.this, R.string.text_standard_apple_tvos_dock_size));
            choiceRow(label, sub, curPct == pct, () -> {
                Scenes.setDockScalePercent(this, pct);
                rebuildMainRows();
                showStatusMsg(I18n.resourceString(this, R.string.dock_size_changed, pct));
            });
        }
    }

    private void subMenuHomeDashboard() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen_widget_settings));
        backSubMenuRow();
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_tip_press_and_hold_ok_on_any_widget_on_t));
        section(I18n.resourceString(SettingsActivity.this, R.string.text_home_screen_widgets));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_enable_home_dashboard),
                () -> HomeDashboard.isEnabled(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    HomeDashboard.setEnabled(this, !HomeDashboard.isEnabled(this));
                    rebuildMainRows();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_show_widgets_when_selected_app_has_no_co),
                () -> HomeDashboard.isShowWhenNoCw(this) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                () -> {
                    HomeDashboard.setShowWhenNoCw(this, !HomeDashboard.isShowWhenNoCw(this));
                    refreshAll();
                });

        section(I18n.resourceString(SettingsActivity.this, R.string.text_dashboard_widgets));
        List<String> order = HomeDashboard.getWidgetOrder(this);
        for (int i = 0; i < order.size(); i++) {
            final String wid = order.get(i);
            row(HomeDashboard.getWidgetEmojiById(wid) + HomeDashboard.getWidgetTitleById(this, wid),
                    () -> HomeDashboard.isWidgetEnabledById(this, wid) ? I18n.resourceString(SettingsActivity.this, R.string.status_on) : I18n.resourceString(SettingsActivity.this, R.string.status_off),
                    () -> {
                        HomeDashboard.setWidgetEnabledById(this, wid, !HomeDashboard.isWidgetEnabledById(this, wid));
                        refreshAll();
                    });
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_widget_settings));
        row("🌍  " + I18n.resourceString(this, R.string.text_releases_country_setting) + "…",
                () -> HomeDashboard.getReleasesCountriesSummary(this),
                () -> openSubMenu("releases_countries"));
        row("📰  " + I18n.resourceString(this, R.string.text_news_country_setting) + "…",
                () -> HomeDashboard.getNewsCountrySummary(this),
                () -> openSubMenu("news_country"));
        row("🕌  " + I18n.resourceString(SettingsActivity.this, R.string.text_prayer_times_location_3a3188) + "…",
                () -> HomeDashboard.getPrayerLocationSummary(this),
                () -> openSubMenu("prayer_location"));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_prayer_calculation_method_92d541),
                () -> HomeDashboard.getPrayerMethodName(this),
                this::promptPrayerMethodDialog);
        row(I18n.resourceString(SettingsActivity.this, R.string.text_add_favorite_football_team_0ba5b6),
                () -> "＋",
                this::promptAddFavoriteTeamDialog);
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_displayed_when_no_app_is_focused_when_fo));
    }

    private void subMenuReleasesCountries() {
        mainHeadView.setText(I18n.resourceString(this, R.string.text_releases_country_setting));
        backSubMenuRow();

        boolean autoMode = HomeDashboard.isReleasesCountryAuto(this);
        String autoCode = HomeDashboard.defaultCountryForUiLang(this);
        String autoCountryLabel = autoCode;
        for (int i = 0; i < HomeDashboard.RELEASES_COUNTRIES.length; i++) {
            if (autoCode.equals(HomeDashboard.RELEASES_COUNTRIES[i][0])) {
                autoCountryLabel = HomeDashboard.RELEASES_COUNTRIES[i][1] + " " + HomeDashboard.RELEASES_COUNTRIES[i][2];
                break;
            }
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_region_mode));
        choiceRow(I18n.resourceString(this, R.string.text_releases_country_auto),
                I18n.resourceString(SettingsActivity.this, R.string.text_currently_synced_to_d6cd7b) + autoCountryLabel,
                autoMode,
                () -> {
                    HomeDashboard.setReleasesCountryAuto(this, !HomeDashboard.isReleasesCountryAuto(this));
                    HomeDashboard.fetchLatestReleases(this, true, null);
                    rebuildMainRows();
                });

        section(I18n.resourceString(SettingsActivity.this, R.string.text_countries_select_one_or_multiple));
        for (int i = 0; i < HomeDashboard.RELEASES_COUNTRIES.length; i++) {
            final String code = HomeDashboard.RELEASES_COUNTRIES[i][0];
            final String flag = HomeDashboard.RELEASES_COUNTRIES[i][1];
            final String name = HomeDashboard.RELEASES_COUNTRIES[i][2];
            boolean selected = HomeDashboard.isReleasesCountrySelected(this, code);
            choiceRow(flag + "  " + name + " (" + code + ")",
                    selected ? I18n.resourceString(SettingsActivity.this, R.string.text_selected_press_ok_to_toggle)
                             : I18n.resourceString(SettingsActivity.this, R.string.text_press_ok_to_include_releases_from_this_c),
                    selected,
                    () -> {
                        HomeDashboard.toggleReleasesCountry(this, code);
                        HomeDashboard.fetchLatestReleases(this, true, null);
                        rebuildMainRows();
                    });
        }
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_by_default_most_watched_automatically_sy));
    }

    private void subMenuNewsCountry() {
        mainHeadView.setText(I18n.resourceString(this, R.string.text_news_country_setting));
        backSubMenuRow();

        boolean autoMode = HomeDashboard.isNewsCountryAuto(this);
        String autoCode = HomeDashboard.defaultCountryForUiLang(this);
        String autoLabel = HomeDashboard.getNewsCountryDisplayName(autoCode) + " (" + autoCode + ")";

        section(I18n.resourceString(SettingsActivity.this, R.string.text_region_mode));
        choiceRow(I18n.resourceString(this, R.string.text_news_country_auto),
                I18n.resourceString(SettingsActivity.this, R.string.text_currently_synced_to_d6cd7b) + autoLabel,
                autoMode,
                () -> {
                    HomeDashboard.setNewsCountry(this, null);
                    HomeDashboard.fetchLiveNews(this, true, null);
                    rebuildMainRows();
                });

        section(I18n.resourceString(SettingsActivity.this, R.string.text_country));
        String activeCode = HomeDashboard.getEffectiveNewsCountry(this);
        for (int i = 0; i < HomeDashboard.NEWS_COUNTRIES.length; i++) {
            final String code = HomeDashboard.NEWS_COUNTRIES[i][0];
            final String name = HomeDashboard.NEWS_COUNTRIES[i][1];
            boolean selected = !autoMode && code.equalsIgnoreCase(activeCode);
            choiceRow(name + " (" + code + ")",
                    selected ? I18n.resourceString(SettingsActivity.this, R.string.text_selected)
                             : I18n.resourceString(SettingsActivity.this, R.string.text_press_ok_to_select_this_country),
                    selected,
                    () -> {
                        HomeDashboard.setNewsCountry(this, code);
                        HomeDashboard.fetchLiveNews(this, true, null);
                        rebuildMainRows();
                    });
        }
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_by_default_live_news_automatically_syncs));
    }

    private void subMenuPrayerLocation() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_prayer_times_location_3a3188));
        backSubMenuRow();

        boolean autoMode = HomeDashboard.isPrayerLocationAuto(this);
        String wxCity = Weather.place(this);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_location_mode));
        choiceRow(I18n.resourceString(SettingsActivity.this, R.string.text_sync_with_weather_location),
                (wxCity != null && !wxCity.isEmpty())
                        ? I18n.resourceString(SettingsActivity.this, R.string.text_currently_synced_to_e6a065) + wxCity
                        : I18n.resourceString(SettingsActivity.this, R.string.text_automatically_follows_your_weather_locat),
                autoMode,
                () -> {
                    HomeDashboard.setPrayerLocationAuto(this);
                    HomeDashboard.fetchPrayerTimes(this, true, null);
                    rebuildMainRows();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_search_city_or_country_96234e),
                () -> chev(),
                this::promptPrayerCitySearchDialog);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_popular_cities));
        String curCustom = HomeDashboard.getEffectivePrayerCity(this);
        for (int i = 0; i < HomeDashboard.PRAYER_POPULAR_CITIES.length; i++) {
            final String label = HomeDashboard.PRAYER_POPULAR_CITIES[i][0];
            final double lat = Double.parseDouble(HomeDashboard.PRAYER_POPULAR_CITIES[i][1]);
            final double lon = Double.parseDouble(HomeDashboard.PRAYER_POPULAR_CITIES[i][2]);
            boolean selected = !autoMode && label.equalsIgnoreCase(curCustom);
            choiceRow(label,
                    selected ? I18n.resourceString(SettingsActivity.this, R.string.text_selected)
                             : I18n.resourceString(SettingsActivity.this, R.string.text_press_ok_to_select_this_city),
                    selected,
                    () -> {
                        HomeDashboard.setPrayerLocation(this, label, lat, lon);
                        HomeDashboard.fetchPrayerTimes(this, true, null);
                        rebuildMainRows();
                    });
        }
        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_by_default_prayer_times_syncs_with_the_w));
    }

    private void promptPrayerCitySearchDialog() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(I18n.resourceString(SettingsActivity.this, R.string.text_city_or_country_e_g_istanbul_london));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_search_city_or_country_65bcc6))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_search_946f53), (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_searching));
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showPrayerCityMatches(q, res));
                    }).start();
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void showPrayerCityMatches(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        showStatusMsg(null);
        if (res == null) {
            showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_couldn_t_search_check_the_internet_conne));
            return;
        }
        if (res.isEmpty()) {
            showStatusMsg(I18n.resourceString(this, R.string.message_no_place_found, q));
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < res.size(); i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : (pl.name + "  \u00b7  " + pl.detail);
        }
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_matching_places))
                .setItems(names, (d, which) -> {
                    Weather.Place picked = res.get(which);
                    String label = picked.detail.isEmpty() ? picked.name : (picked.name + ", " + picked.detail);
                    HomeDashboard.setPrayerLocation(this, label, picked.lat, picked.lon);
                    HomeDashboard.fetchPrayerTimes(this, true, null);
                    rebuildMainRows();
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void promptPrayerMethodDialog() {
        int cur = HomeDashboard.getPrayerMethod(this);
        String[] opts = new String[HomeDashboard.PRAYER_METHOD_IDS.length];
        for (int i = 0; i < opts.length; i++) {
            opts[i] = (HomeDashboard.PRAYER_METHOD_IDS[i] == cur ? "✓ " : "   ")
                    + HomeDashboard.PRAYER_METHOD_NAMES[i];
        }
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_prayer_calculation_method_f4f921))
                .setItems(opts, (d, which) -> {
                    if (which >= 0 && which < HomeDashboard.PRAYER_METHOD_IDS.length) {
                        HomeDashboard.setPrayerMethod(this, HomeDashboard.PRAYER_METHOD_IDS[which]);
                        refreshAll();
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
    }

    private void promptAddFavoriteTeamDialog() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("e.g. Qarabağ, Galatasaray, Real Madrid");
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_add_favorite_football_team_56b172))
                .setView(in)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_add), (d, w) -> {
                    String name = in.getText().toString().trim();
                    if (!name.isEmpty()) {
                        HomeDashboard.addCustomFavoriteTeam(this, name);
                        showStatusMsg(I18n.resourceString(this, R.string.football_favorite_team_added, name));
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void subMenuCustomWallpaper(final String scope) {
        boolean isScene = CustomMedia.SCOPE_SCENE.equals(scope);
        mainHeadView.setText(isScene
                ? I18n.resourceString(SettingsActivity.this, R.string.text_night_scenes_custom_wallpaper)
                : I18n.resourceString(SettingsActivity.this, R.string.text_classic_custom_wallpaper));
        backSubMenuRow();

        int subMode = CustomMedia.getCustomWallpaperSubMode(this, scope);
        section(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_mode_9acb13));
        choiceRow(I18n.resourceString(SettingsActivity.this, R.string.text_single_multi_wallpapers),
                I18n.resourceString(SettingsActivity.this, R.string.text_add_as_many_custom_wallpapers_as_you_wan),
                subMode == CustomMedia.CUSTOM_MODE_SINGLE_MULTI,
                () -> {
                    CustomMedia.setCustomWallpaperSubMode(this, scope, CustomMedia.CUSTOM_MODE_SINGLE_MULTI);
                    rebuildMainRows();
                });
        choiceRow(I18n.resourceString(SettingsActivity.this, R.string.text_shuffle_folder_dc8ec3),
                I18n.resourceString(SettingsActivity.this, R.string.text_select_a_folder_and_enable_or_disable_in),
                subMode == CustomMedia.CUSTOM_MODE_FOLDER_SHUFFLE,
                () -> {
                    CustomMedia.setCustomWallpaperSubMode(this, scope, CustomMedia.CUSTOM_MODE_FOLDER_SHUFFLE);
                    rebuildMainRows();
                });
        choiceRow(I18n.resourceString(SettingsActivity.this, R.string.text_reddit_wallpapers),
                I18n.resourceString(SettingsActivity.this, R.string.text_auto_changing_1080p_4k_wallpapers_from_y),
                subMode == CustomMedia.CUSTOM_MODE_REDDIT,
                () -> {
                    CustomMedia.setCustomWallpaperSubMode(this, scope, CustomMedia.CUSTOM_MODE_REDDIT);
                    CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);
                    rebuildMainRows();
                });

        if (subMode == CustomMedia.CUSTOM_MODE_SINGLE_MULTI) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpapers_unlimited));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_auto_change_interval),
                    () -> Scenes.getStayIntervalLabel(this),
                    () -> {
                        Scenes.cycleStaySeconds(this);
                        refreshAll();
                    });
            row("＋  " + I18n.resourceString(SettingsActivity.this, R.string.text_add_new_wallpaper),
                    () -> I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_d3f17f),
                    () -> CustomMedia.showImageFilePicker(
                            this,
                            I18n.resourceString(SettingsActivity.this, R.string.text_select_wallpaper_image),
                            false,
                            1920,
                            1080,
                            null,
                            null,
                            bmp -> {
                                int slot = CustomMedia.addCustomWallpaperSlot(this, scope, bmp);
                                if (isScene) {
                                    Scenes.enableCustomScene(this, slot, true);
                                }
                                rebuildMainRows();
                                showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_added_custom_wallpaper_s, slot));
                            }));

            int totalSlots = CustomMedia.getCustomSlotCount(this, scope);
            int activeSlot = CustomMedia.getActiveCustomSlot(this, scope);
            for (int s = 1; s <= totalSlots; s++) {
                final int slot = s;
                boolean exists = isScene
                        ? CustomMedia.hasCustomSceneWallpaper(this, slot)
                        : CustomMedia.hasCustomClassicWallpaper(this, slot);
                if (!exists) continue;
                final String slotTitle = I18n.resourceString(SettingsActivity.this, R.string.text_custom_wallpaper_1e4315) + slot;
                boolean isSelected = isScene
                        ? Scenes.isSceneEnabled(this, Scenes.customSlotToId(slot))
                        : (activeSlot == slot);
                choiceRow(slotTitle,
                        isScene
                                ? I18n.resourceString(SettingsActivity.this, R.string.text_tap_to_toggle_in_scene_rotation_long_pre)
                                : I18n.resourceString(SettingsActivity.this, R.string.text_tap_to_set_as_active_wallpaper),
                        isSelected,
                        () -> {
                            CustomMedia.setActiveCustomSlot(this, scope, slot);
                            if (isScene) {
                                String cid = Scenes.customSlotToId(slot);
                                Scenes.enableCustomScene(this, slot, !Scenes.isSceneEnabled(this, cid));
                            }
                            rebuildMainRows();
                        });
                row("   ↻  " + I18n.resourceString(SettingsActivity.this, R.string.text_replace_wallpaper) + slot,
                        () -> I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_d3f17f),
                        () -> CustomMedia.showImageFilePicker(
                                this,
                                slotTitle,
                                false,
                                1920,
                                1080,
                                I18n.resourceString(SettingsActivity.this, R.string.text_remove_this_wallpaper),
                                () -> {
                                    CustomMedia.removeCustomWallpaperSlot(this, scope, slot);
                                    rebuildMainRows();
                                },
                                bmp -> {
                                    if (isScene) {
                                        CustomMedia.setCustomSceneWallpaper(this, slot, bmp);
                                    } else {
                                        CustomMedia.setCustomClassicWallpaper(this, slot, bmp);
                                    }
                                    CustomMedia.setActiveCustomSlot(this, scope, slot);
                                    rebuildMainRows();
                                }));
                if (isScene) {
                    final boolean hasFg = CustomMedia.hasCustomSceneForeground(this, slot);
                    row("   ✦  " + I18n.resourceString(SettingsActivity.this, R.string.text_depth_effect_foreground_png) + slot,
                            () -> hasFg ? I18n.resourceString(SettingsActivity.this, R.string.text_set_png_cutout) : I18n.resourceString(SettingsActivity.this, R.string.text_none_choose_png),
                            () -> CustomMedia.showImageFilePicker(
                                    this,
                                    I18n.resourceString(SettingsActivity.this, R.string.text_select_transparent_png_foreground) + " (#" + slot + ")",
                                    false,
                                    1920,
                                    1080,
                                    hasFg ? I18n.resourceString(SettingsActivity.this, R.string.text_remove_foreground_cutout) : null,
                                    hasFg ? () -> {
                                        CustomMedia.clearCustomSceneForeground(this, slot);
                                        rebuildMainRows();
                                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_depth_effect_foreground_removed));
                                    } : null,
                                    bmp -> {
                                        CustomMedia.setCustomSceneForeground(this, slot, bmp);
                                        CustomMedia.setActiveCustomSlot(this, scope, slot);
                                        rebuildMainRows();
                                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_depth_effect_foreground_updated));
                                    }));
                }
                row("   🗑  " + I18n.resourceString(SettingsActivity.this, R.string.text_remove_wallpaper) + slot,
                        () -> "",
                        () -> {
                            CustomMedia.removeCustomWallpaperSlot(this, scope, slot);
                            rebuildMainRows();
                        });
            }
        } else if (subMode == CustomMedia.CUSTOM_MODE_FOLDER_SHUFFLE) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_shuffle_folder_86fcb0));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_auto_change_interval),
                    () -> Scenes.getStayIntervalLabel(this),
                    () -> {
                        Scenes.cycleStaySeconds(this);
                        refreshAll();
                    });
            row("📁  " + I18n.resourceString(SettingsActivity.this, R.string.text_select_wallpaper_folder_9bea6b),
                    () -> CustomMedia.getWallpaperShuffleFolderSummary(this, scope),
                    () -> CustomMedia.showFolderPicker(
                            this,
                            scope,
                            I18n.resourceString(SettingsActivity.this, R.string.text_select_wallpaper_folder_0c14ac),
                            folder -> rebuildMainRows()));

            String folderPath = CustomMedia.getWallpaperShuffleFolder(this, scope);
            if (!folderPath.isEmpty()) {
                java.io.File dir = new java.io.File(folderPath);
                List<java.io.File> files = CustomMedia.listFolderImages(dir);
                if (!files.isEmpty()) {
                    section(I18n.resourceString(SettingsActivity.this, R.string.text_wallpapers_in_folder_tap_to_enable_disab));
                    for (final java.io.File imgFile : files) {
                        final String abs = imgFile.getAbsolutePath();
                        boolean enabled = !CustomMedia.isFolderImageDisabled(this, scope, abs);
                        choiceRow("🖼️  " + imgFile.getName(),
                                enabled ? I18n.resourceString(SettingsActivity.this, R.string.text_enabled_in_shuffle)
                                        : I18n.resourceString(SettingsActivity.this, R.string.text_disabled_ae652f),
                                enabled,
                                () -> {
                                    boolean nowDisabled = !CustomMedia.isFolderImageDisabled(this, scope, abs);
                                    CustomMedia.setFolderImageDisabled(this, scope, abs, nowDisabled);
                                    rebuildMainRows();
                                });
                    }
                }
            }
        } else if (subMode == CustomMedia.CUSTOM_MODE_REDDIT) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_reddit_wallpaper_options));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_auto_change_interval),
                    () -> CustomMedia.getRedditIntervalLabel(this, scope),
                    () -> {
                        CustomMedia.cycleRedditIntervalMinutes(this, scope);
                        refreshAll();
                    });
            row("⏭  " + I18n.resourceString(this, R.string.text_next_wallpaper_f38b4f),
                    () -> I18n.resourceString(SettingsActivity.this, R.string.text_skip_now),
                    () -> {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_fetching_next_reddit_wallpaper));
                        CustomMedia.fetchNextRedditWallpaperAsync(this, scope, () ->
                                showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_reddit_wallpaper_updated)));
                    });
            row("＋  " + I18n.resourceString(SettingsActivity.this, R.string.text_add_custom_subreddit),
                    () -> "r/…",
                    () -> promptAddCustomSubredditDialog(scope));

            section(I18n.resourceString(SettingsActivity.this, R.string.text_subreddits_select_single_or_multiple));
            java.util.Set<String> activeSubs = CustomMedia.getRedditSubreddits(this, scope);
            List<String> allSubs = CustomMedia.getAllKnownRedditSubreddits(this, scope);
            for (final String sub : allSubs) {
                boolean on = false;
                for (String a : activeSubs) {
                    if (a.equalsIgnoreCase(sub)) {
                        on = true;
                        break;
                    }
                }
                choiceRow("r/" + sub,
                        on ? I18n.resourceString(SettingsActivity.this, R.string.text_selected) : I18n.resourceString(SettingsActivity.this, R.string.text_tap_to_include),
                        on,
                        () -> {
                            CustomMedia.toggleRedditSubreddit(this, scope, sub);
                            CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);
                            rebuildMainRows();
                        });
            }
        }
    }

    private void promptAddCustomSubredditDialog(final String scope) {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("e.g. WallpaperDump, ImaginaryLandscapes");
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_add_subreddit))
                .setView(in)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_add), (d, w) -> {
                    String sub = in.getText().toString().trim();
                    if (!sub.isEmpty()) {
                        CustomMedia.addCustomRedditSubreddit(this, scope, sub);
                        CustomMedia.fetchNextRedditWallpaperAsync(this, scope, null);
                        rebuildMainRows();
                    }
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void subMenuAmbientThemes() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_ambient_colour_themes_930057));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_ambient_themes));
        String cur = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
        for (int i = 0; i < MainActivity.WALL_KEYS.length; i++) {
            final String key = MainActivity.WALL_KEYS[i];
            final String name = MainActivity.wallName(this, i);
            choiceRow(name, null, key.equals(cur), () -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", key).apply();
                rebuildMainRows();
            });
        }
    }

    private void subMenuWeatherSource() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_weather_source));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_forecast_provider));
        String cur = Weather.source(this);
        String[] keys = Weather.SOURCES;
        String[] names = {"Open-Meteo", "MET Norway (Yr)", "wttr.in  (" + I18n.resourceString(SettingsActivity.this, R.string.text_3_days) + ")"};
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            final String n = names[i];
            choiceRow(n, null, k.equals(cur), () -> {
                Weather.prefs(this).edit().putString(Weather.K_SOURCE, k).apply();
                Weather.changed(this);
                rebuildMainRows();
            });
        }
    }

    private void subMenuTopShelf() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_classic_top_shelf));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_top_shelf_content));
        String cur = Suggestions.shelf(this);
        String[][] opts = {
            {Suggestions.SHELF_BOTH, I18n.resourceString(SettingsActivity.this, R.string.text_recommendations_news)},
            {Suggestions.SHELF_RECS, I18n.resourceString(SettingsActivity.this, R.string.text_recommendations)},
            {Suggestions.SHELF_NEWS, I18n.resourceString(SettingsActivity.this, R.string.text_news)},
            {Suggestions.SHELF_NONE, I18n.resourceString(SettingsActivity.this, R.string.status_off)}
        };
        for (String[] pair : opts) {
            final String k = pair[0];
            final String n = pair[1];
            choiceRow(n, null, k.equals(cur), () -> {
                Suggestions.setShelf(this, k);
                rebuildMainRows();
            });
        }
    }

    private void subMenuProfileActions(String profileId) {
        Profiles.Profile found = null;
        int idx = 0;
        List<Profiles.Profile> all = Profiles.list(this);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(profileId)) {
                found = all.get(i);
                idx = i;
                break;
            }
        }
        if (found == null) {
            closeSubMenu();
            return;
        }
        final Profiles.Profile p = found;
        final int rowIdx = idx;
        String dispName = I18n.profileName(this, p.name);
        mainHeadView.setText(dispName);
        backSubMenuRow();
        section(dispName);
        row(Profiles.switchLabel(this, dispName),
                () -> Profiles.current(this).id.equals(p.id) ? I18n.resourceString(SettingsActivity.this, R.string.text_active_60b543) : chev(),
                () -> {
                    Profiles.setCurrent(this, p.id);
                    closeSubMenu();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_rename_e8372b),
                () -> chev(),
                () -> promptRenamePerson(p, rowIdx));
        if (all.size() > 1) {
            row(I18n.resourceString(SettingsActivity.this, R.string.text_remove_person),
                    () -> "",
                    () -> {
                        Profiles.delete(this, p.id);
                        closeSubMenu();
                    });
        }
    }

    private String controlPanelActionTitle(String actionKey) {
        if (ControlPanelOverlay.ACTION_WIFI.equals(actionKey)) return I18n.resourceString(SettingsActivity.this, R.string.text_wi_fi);
        if (ControlPanelOverlay.ACTION_BLUETOOTH.equals(actionKey)) return I18n.resourceString(SettingsActivity.this, R.string.text_bluetooth);
        if (ControlPanelOverlay.ACTION_SOUND.equals(actionKey)) return I18n.resourceString(SettingsActivity.this, R.string.text_sound);
        if (ControlPanelOverlay.ACTION_INPUTS.equals(actionKey)) return I18n.resourceString(SettingsActivity.this, R.string.text_inputs);
        if (ControlPanelOverlay.ACTION_AMBIENT.equals(actionKey)) return I18n.resourceString(SettingsActivity.this, R.string.text_ambient_mode);
        if (ControlPanelOverlay.ACTION_POWER.equals(actionKey)) return I18n.resourceString(SettingsActivity.this, R.string.text_power_off);
        return actionKey;
    }

    private void subMenuControlPanelApps(String actionKey) {
        String actionTitle = controlPanelActionTitle(actionKey);
        mainHeadView.setText(actionTitle + " — " + I18n.resourceString(SettingsActivity.this, R.string.text_choose_action));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_target_action_application));

        String curPkg = ControlPanelOverlay.getCustomPkg(this, actionKey);
        boolean isAmbient = ControlPanelOverlay.ACTION_AMBIENT.equals(actionKey);
        boolean isLockMode = isAmbient && ControlPanelOverlay.isAmbientLockScreenMode(this);
        boolean isPower = ControlPanelOverlay.ACTION_POWER.equals(actionKey);
        boolean isPowerNative = isPower && ControlPanelOverlay.isPowerNativeMenuMode(this);

        String defaultTitle = isAmbient
                ? I18n.resourceString(SettingsActivity.this, R.string.text_default_ambient_mode)
                : (isPower
                        ? I18n.resourceString(SettingsActivity.this, R.string.text_default_screen_off)
                        : I18n.resourceString(SettingsActivity.this, R.string.text_default_automatic));
        String defaultSubtitle = isAmbient
                ? I18n.resourceString(SettingsActivity.this, R.string.text_open_tv_ambient_mode_screensaver_or_buil)
                : (isPower
                        ? I18n.resourceString(SettingsActivity.this, R.string.text_turn_off_screen_put_tv_to_sleep)
                        : I18n.resourceString(SettingsActivity.this, R.string.text_use_built_in_android_tv_shortcut));

        choiceRow("↺  " + defaultTitle,
                defaultSubtitle,
                curPkg.isEmpty() && !isLockMode && !isPowerNative,
                () -> {
                    ControlPanelOverlay.clearCustomAction(this, actionKey);
                    closeSubMenu();
                    showStatusMsg(actionTitle + ": " + (isPower
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_default_screen_off)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_default_automatic)));
                });

        if (isAmbient) {
            choiceRow("🔒  " + I18n.resourceString(SettingsActivity.this, R.string.text_lock_screen),
                    I18n.resourceString(SettingsActivity.this, R.string.text_show_clock_date_greeting_over_your_chose),
                    isLockMode,
                    () -> {
                        ControlPanelOverlay.setAmbientLockScreenMode(this, true);
                        closeSubMenu();
                        showStatusMsg(actionTitle + ": " + I18n.resourceString(SettingsActivity.this, R.string.text_lock_screen));
                    });
        }

        if (isPower) {
            choiceRow("⏻  " + I18n.resourceString(SettingsActivity.this, R.string.text_android_power_menu_shutdown_reboot_stand),
                    I18n.resourceString(SettingsActivity.this, R.string.text_show_native_android_shutdown_reboot_and),
                    isPowerNative,
                    () -> {
                        ControlPanelOverlay.setPowerNativeMenuMode(this, true);
                        closeSubMenu();
                        showStatusMsg(actionTitle + ": " + I18n.resourceString(SettingsActivity.this, R.string.text_android_power_menu));
                    });
        }

        PackageManager pm = getPackageManager();
        List<PackageInfo> rawPkgs;
        try {
            rawPkgs = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        } catch (Throwable t) {
            rawPkgs = Collections.emptyList();
        }

        List<String[]> entries = new ArrayList<>();
        for (PackageInfo pi : rawPkgs) {
            if (pi == null || pi.packageName == null) continue;
            int count = (pi.activities != null) ? pi.activities.length : 0;
            if (count == 0) {
                Intent q = new Intent().setPackage(pi.packageName);
                List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
                count = (ris != null) ? ris.size() : 0;
            }
            if (count == 0) continue;
            String label = pi.packageName;
            if (pi.applicationInfo != null) {
                CharSequence cs = pi.applicationInfo.loadLabel(pm);
                if (cs != null && cs.length() > 0) label = cs.toString().trim();
            }
            entries.add(new String[]{pi.packageName, label});
        }
        Collections.sort(entries, (a, b) -> {
            int cmp = a[1].compareToIgnoreCase(b[1]);
            return cmp != 0 ? cmp : a[0].compareToIgnoreCase(b[0]);
        });

        for (String[] entry : entries) {
            final String pkg = entry[0];
            final String label = entry[1];
            choiceRow(label, pkg, pkg.equals(curPkg), () -> openSubMenu("cp_act:" + actionKey + ":" + pkg));
        }
    }

    private void subMenuControlPanelActivities(String actionKey, String pkg) {
        String actionTitle = controlPanelActionTitle(actionKey);
        String appLabel = CleanYourTv.resolvePackageLabel(this, pkg);
        mainHeadView.setText(appLabel + " — " + I18n.resourceString(SettingsActivity.this, R.string.text_choose_activity));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_activities_in) + appLabel);

        PackageManager pm = getPackageManager();
        List<ActivityInfo> actList = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi != null && pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    if (ai != null && ai.name != null && seenNames.add(ai.name)) actList.add(ai);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent q = new Intent().setPackage(pkg);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            if (ris != null) {
                for (ResolveInfo ri : ris) {
                    if (ri.activityInfo != null && ri.activityInfo.name != null && seenNames.add(ri.activityInfo.name)) {
                        actList.add(ri.activityInfo);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        String curPkg = ControlPanelOverlay.getCustomPkg(this, actionKey);
        String curCls = ControlPanelOverlay.getCustomCls(this, actionKey);

        choiceRow("★  " + I18n.resourceString(SettingsActivity.this, R.string.text_default_app_launch_main_activity),
                pkg,
                pkg.equals(curPkg) && curCls.isEmpty(),
                () -> {
                    ControlPanelOverlay.setCustomAction(this, actionKey, pkg, "", appLabel);
                    if (ControlPanelOverlay.ACTION_INPUTS.equals(actionKey)) {
                        Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, true).apply();
                    }
                    closeSubMenu();
                    closeSubMenu();
                    showStatusMsg(actionTitle + " → " + appLabel);
                });

        for (ActivityInfo ai : actList) {
            final String fullCls = ai.name;
            final String shortCls = fullCls.startsWith(pkg + ".") ? fullCls.substring(pkg.length()) : fullCls;
            CharSequence actCs = ai.loadLabel(pm);
            String actTitle = (actCs != null && actCs.length() > 0) ? actCs.toString().trim() : "";
            final String title = (!actTitle.isEmpty() && !actTitle.equalsIgnoreCase(appLabel) && !actTitle.equals(fullCls))
                    ? actTitle : shortCls;
            final String summary = appLabel + " · " + shortCls;
            choiceRow(title, fullCls, pkg.equals(curPkg) && fullCls.equals(curCls), () -> {
                ControlPanelOverlay.setCustomAction(this, actionKey, pkg, fullCls, summary);
                if (ControlPanelOverlay.ACTION_INPUTS.equals(actionKey)) {
                    Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, true).apply();
                }
                closeSubMenu();
                closeSubMenu();
                showStatusMsg(actionTitle + " → " + summary);
            });
        }
    }

    private void subMenuButtonKey(int keyCode) {
        String keyName = ButtonMapper.keyLabel(this, keyCode);
        mainHeadView.setText(keyName);
        backSubMenuRow();
        section(keyName + " — " + I18n.resourceString(SettingsActivity.this, R.string.text_tap_actions));

        int[] taps = {ButtonMapper.TAP_SINGLE, ButtonMapper.TAP_DOUBLE, ButtonMapper.TAP_TRIPLE};
        for (int tap : taps) {
            final int t = tap;
            row(ButtonMapper.tapTypeLabel(this, t),
                    () -> ButtonMapper.targetLabel(this, ButtonMapper.getTarget(this, keyCode, t)),
                    () -> openSubMenu("bm_tap:" + keyCode + ":" + t));
        }

        if (ButtonMapper.hasAnyMapping(this, keyCode)) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_remove_7e2800));
            row("🗑  " + I18n.resourceString(SettingsActivity.this, R.string.text_remove_button_remap),
                    () -> "",
                    () -> {
                        ButtonMapper.removeMapping(this, keyCode);
                        closeSubMenu();
                        showStatusMsg(ButtonMapper.removedMappingMessage(this, keyName));
                    });
        }
    }

    private void subMenuButtonTapTarget(int keyCode, int tapCount) {
        String keyName = ButtonMapper.keyLabel(this, keyCode);
        String tapName = ButtonMapper.tapTypeLabel(this, tapCount);
        String current = ButtonMapper.getTarget(this, keyCode, tapCount);
        mainHeadView.setText(keyName + " · " + tapName);
        backSubMenuRow();

        section(I18n.resourceString(SettingsActivity.this, R.string.text_built_in_actions));
        choiceRow("🚫  " + I18n.resourceString(SettingsActivity.this, R.string.text_none_no_action),
                null,
                current.isEmpty(),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, "");
                    closeSubMenu();
                });
        choiceRow("🎛  " + I18n.resourceString(SettingsActivity.this, R.string.text_control_panel_overlay),
                null,
                ButtonMapper.TARGET_CONTROL_PANEL.equals(current),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, ButtonMapper.TARGET_CONTROL_PANEL);
                    closeSubMenu();
                });
        choiceRow("🗂  " + I18n.resourceString(SettingsActivity.this, R.string.text_recent_apps_switcher_overlay),
                null,
                ButtonMapper.TARGET_RECENT_APPS.equals(current),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, ButtonMapper.TARGET_RECENT_APPS);
                    closeSubMenu();
                });
        choiceRow("📺  " + I18n.resourceString(this, R.string.text_bm_inputs_overlay),
                I18n.resourceString(SettingsActivity.this, R.string.text_switch_tv_hdmi_inputs_on_screen_even_if),
                ButtonMapper.TARGET_INPUTS_OVERLAY.equals(current),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, ButtonMapper.TARGET_INPUTS_OVERLAY);
                    closeSubMenu();
                });
        choiceRow("🌙  " + I18n.resourceString(SettingsActivity.this, R.string.text_ambient_mode_standby),
                null,
                ButtonMapper.TARGET_AMBIENT.equals(current),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, ButtonMapper.TARGET_AMBIENT);
                    closeSubMenu();
                });
        choiceRow("🔒  " + I18n.resourceString(SettingsActivity.this, R.string.text_lock_screen),
                null,
                ButtonMapper.TARGET_LOCK_SCREEN.equals(current),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, ButtonMapper.TARGET_LOCK_SCREEN);
                    closeSubMenu();
                });
        choiceRow("⏻  " + I18n.resourceString(SettingsActivity.this, R.string.text_power_off_screen_off),
                null,
                ButtonMapper.TARGET_POWER_OFF.equals(current),
                () -> {
                    ButtonMapper.setTarget(this, keyCode, tapCount, ButtonMapper.TARGET_POWER_OFF);
                    closeSubMenu();
                });
        choiceRow("⌨  " + I18n.resourceString(SettingsActivity.this, R.string.text_send_key_code_remote_media_system),
                current.startsWith(ButtonMapper.TARGET_KEYCODE_PREFIX) ? ButtonMapper.targetLabel(this, current) : I18n.resourceString(SettingsActivity.this, R.string.text_send_any_android_keyevent_keycode_withou),
                current.startsWith(ButtonMapper.TARGET_KEYCODE_PREFIX),
                () -> ButtonMapper.showSendKeyCodePickerDialog(this, keyCode, tapCount, this::closeSubMenu));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_installed_apps));
        PackageManager pm = getPackageManager();
        Set<String> seenPkgs = new HashSet<>();
        List<String[]> appPairs = new ArrayList<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || seenPkgs.contains(pkg)) continue;
                seenPkgs.add(pkg);
                String appLabel = ri.loadLabel(pm).toString();
                appPairs.add(new String[]{appLabel, ButtonMapper.TARGET_APP_PREFIX + pkg, pkg});
            }
        }
        Collections.sort(appPairs, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        for (String[] pair : appPairs) {
            final String appLabel = pair[0];
            final String targetVal = pair[1];
            final String pkg = pair[2];
            choiceRow(appLabel, pkg, targetVal.equals(current), () -> {
                ButtonMapper.setTarget(this, keyCode, tapCount, targetVal);
                closeSubMenu();
            });
        }
    }

    private void subMenuDefaultHome() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_default_home_screen_57e442));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_default_launcher_setup_tcl_philips_googl));

        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_1_set_tvos_launcher_as_default_via_local),
                () -> Perms.isDefaultHome(this) ? I18n.resourceString(SettingsActivity.this, R.string.text_active_60b543) : I18n.resourceString(SettingsActivity.this, R.string.text_run_role_home_set_home_activity),
                () -> {
                    AlertDialog progress = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_setting_default_launcher))
                            .setMessage(I18n.resourceString(SettingsActivity.this, R.string.text_setting_role_home_and_default_home_activ))
                            .setCancelable(false)
                            .create();
                    progress.show();
                    LocalAdb.setDefaultLauncherViaAdb(this, (success, msg) -> runOnUiThread(() -> {
                        if (isFinishing()) return;
                        try {
                            progress.dismiss();
                        } catch (Throwable ignored) {
                        }
                        refreshAll();
                        showStatusMsg(msg);
                    }));
                });

        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_2_disable_system_launchers_google_tv_and),
                () -> CleanYourTv.getSystemLaunchersSummary(this),
                () -> openSubMenu("cyt_launchers"));

        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_3_fix_home_button_via_accessibility_butt),
                () -> ButtonMapper.isHomeFixEnabled(this)
                        ? I18n.resourceString(SettingsActivity.this, R.string.text_on_pressing_home_launches_tvos_launcher)
                        : I18n.resourceString(SettingsActivity.this, R.string.text_off_tap_to_enable_home_button_remap),
                () -> {
                    boolean next = !ButtonMapper.isHomeFixEnabled(this);
                    ButtonMapper.setHomeFixEnabled(this, next);
                    rebuildMainRows();
                    showStatusMsg(next
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_home_button_fix_enabled_5232e5)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_home_button_fix_disabled));
                });

        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_4_open_android_system_home_settings),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_open_system_selector_if_supported_by_tv),
                () -> Perms.openSystemHomeSettingsUi(this));

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_on_tcl_and_philips_android_11_12_tvs_the));
    }

    private void subMenuCheckPermissions() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_check_permissions));
        backSubMenuRow();
        section(I18n.resourceString(SettingsActivity.this, R.string.text_permission_status));

        row(I18n.resourceString(SettingsActivity.this, R.string.text_grant_all_permissions_via_local_adb),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_grant_all),
                this::pairOrRepairAdb);

        row(I18n.resourceString(SettingsActivity.this, R.string.text_default_home_screen_3e8523),
                () -> Perms.isDefaultHome(this) ? "✓  " + I18n.resourceString(SettingsActivity.this, R.string.text_active_60b543) : "✗  " + I18n.resourceString(SettingsActivity.this, R.string.text_not_set),
                () -> openSubMenu("default_home"));

        row(I18n.resourceString(SettingsActivity.this, R.string.text_button_mapper_control_panel_accessibilit),
                () -> ButtonMapper.isServiceEnabled(this) ? "✓  " + I18n.resourceString(SettingsActivity.this, R.string.text_active_60b543) : "✗  " + I18n.resourceString(SettingsActivity.this, R.string.text_disabled_62ff42),
                () -> {
                    Intent acc = Perms.accessibilityScreen(this);
                    if (acc == null || !Suggestions.start(this, acc)) {
                        pairOrRepairAdb();
                    }
                });

        row(I18n.resourceString(SettingsActivity.this, R.string.text_continue_watching_dnd_notification_acces),
                () -> Perms.hasListener(this) ? "✓  " + I18n.resourceString(SettingsActivity.this, R.string.text_allowed) : "✗  " + I18n.resourceString(SettingsActivity.this, R.string.text_not_allowed),
                () -> Perms.requestListener(this));

        row(I18n.resourceString(SettingsActivity.this, R.string.text_wi_fi_name_location),
                () -> Perms.hasLocation(this) ? "✓  " + I18n.resourceString(SettingsActivity.this, R.string.text_allowed) : "✗  " + I18n.resourceString(SettingsActivity.this, R.string.text_not_allowed),
                () -> Perms.requestLocation(this));
    }

    // ---------------- CleanYourTV Native Right-Side Sub-Menus ----------------

    private void subMenuCytModels() {
        int modelCount = CleanYourTv.loadIndex(this).size();
        CleanYourTv.HardwareMatchResult hw = CleanYourTv.findHardwareMatches(this);
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_choose_tv_model_eb4b40));
        backSubMenuRow();

        section(I18n.resourceString(SettingsActivity.this, R.string.text_actions));
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_scan_installed_system_apps_via_adb),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_pull_full_app_list_from_adb_and_match_mo_795599),
                () -> {
                    cytScannedMatches = null;
                    openSubMenu("cyt_models_scan");
                });
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.message_browse_search_all_1_s_tv_models, modelCount),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_view_or_filter_the_complete_list_of_supp),
                () -> {
                    cytModelFilterQuery = "";
                    openSubMenu("cyt_models_all");
                });

        String currentSlug = CleanYourTv.getSelectedModelSlug(this);
        section(I18n.resourceString(SettingsActivity.this, R.string.text_matching_models_for) + hw.deviceDescription);
        if (hw.candidates.isEmpty()) {
            infoNote(I18n.resourceString(this, hw.deviceDescription.isEmpty()
                    ? R.string.cyt_no_direct_model_empty : R.string.cyt_no_direct_model, hw.deviceDescription));
        } else {
            for (CleanYourTv.ModelMatch m : hw.candidates) {
                final CleanYourTv.ModelEntry entry = m.entry;
                boolean isCurrent = entry.slug.equalsIgnoreCase(currentSlug);
                choiceRow(entry.title,
                        "[" + entry.slug + "]  •  " + m.reason,
                        isCurrent,
                        () -> selectCytModelAndProceed(entry));
            }
        }
    }

    private void selectCytModelAndProceed(CleanYourTv.ModelEntry entry) {
        CleanYourTv.setSelectedModel(this, entry);
        cytChecklistItems = null;
        loadAdvancedSummariesAsync();
        final boolean autoDebloat = cytAutoContinueToDebloat;
        cytAutoContinueToDebloat = false;
        CleanYourTv.promptCleanUpUnnecessaryFiles(this, entry, () -> {
            loadAdvancedSummariesAsync();
            if (autoDebloat) {
                subMenuStack.clear();
                subMenuParentFocusIdx.clear();
                subMenuStack.add("");
                subMenuParentFocusIdx.add(1);
                activeSubMenu = "cyt_debloat";
                rebuildMainRows();
                focusFirstRow();
            } else {
                subMenuStack.clear();
                subMenuParentFocusIdx.clear();
                activeSubMenu = null;
                rebuildMainRows();
                focusFirstRow();
                showStatusMsg(I18n.resourceString(this, entry.title.isEmpty()
                        ? R.string.cyt_selected_model_empty : R.string.cyt_selected_model, entry.title));
            }
        });
    }

    private void subMenuCytScannedModels() {
        int modelCount = CleanYourTv.loadIndex(this).size();
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_models_by_system_app_similarity));
        backSubMenuRow();

        if (cytScannedMatches == null) {
            if (!cytScanningApps) {
                cytScanningApps = true;
                new Thread(() -> {
                    Set<String> installed = CleanYourTv.fetchInstalledPackages(this, null, null);
                    List<CleanYourTv.ModelMatch> matches = CleanYourTv.findPackageSimilarityMatches(this, installed);
                    runOnUiThread(() -> {
                        cytScanningApps = false;
                        cytScannedCount = installed.size();
                        cytScannedMatches = matches;
                        if (!isFinishing() && "cyt_models_scan".equals(activeSubMenu)) {
                            rebuildMainRows();
                        }
                    });
                }, "cyt-submenu-scan").start();
            }
            infoNote(I18n.resourceString(SettingsActivity.this, R.string.message_scanning_installed_system_packages_over_adb_and, modelCount));
            return;
        }

        section(I18n.resourceString(this, R.string.cyt_closest_matches_scanned, cytScannedCount));
        String currentSlug = CleanYourTv.getSelectedModelSlug(this);
        for (CleanYourTv.ModelMatch m : cytScannedMatches) {
            final CleanYourTv.ModelEntry entry = m.entry;
            boolean isCurrent = entry.slug.equalsIgnoreCase(currentSlug);
            choiceRow(entry.title,
                    "[" + entry.slug + "]  •  " + m.reason,
                    isCurrent,
                    () -> selectCytModelAndProceed(entry));
        }
    }

    private void subMenuCytAllModels() {
        int modelCount = CleanYourTv.loadIndex(this).size();
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.message_all_1_s_tv_models, modelCount));
        backSubMenuRow();

        row(I18n.resourceString(SettingsActivity.this, R.string.text_search_filter_by_brand_or_model),
                () -> cytModelFilterQuery.isEmpty()
                        ? I18n.resourceString(this, R.string.cyt_all_count, modelCount)
                        : "\"" + cytModelFilterQuery + "\"",
                this::promptCytModelFilter);
        if (!cytModelFilterQuery.isEmpty()) {
            row(I18n.resourceString(SettingsActivity.this, R.string.text_clear_filter_show_all),
                    () -> "",
                    () -> {
                        cytModelFilterQuery = "";
                        cytAllModelsPage = 0;
                        rebuildMainRows();
                    });
        } else {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_quick_brand_filters));
            String[][] brands = {
                {"TCL / FFALCON / Thomson", "tcl"},
                {"Philips / TPVision", "philips"},
                {"Sony Bravia", "sony"},
                {"Xiaomi / Mi TV / Redmi", "xiaomi"},
                {"Hisense / Toshiba", "hisense"},
                {"Skyworth / Coocaa / Strong", "skyworth"}
            };
            for (String[] b : brands) {
                final String bLabel = b[0];
                final String bQuery = b[1];
                row("🏷  " + bLabel, () -> I18n.resourceString(SettingsActivity.this, R.string.text_filter_bf262c), () -> {
                    cytModelFilterQuery = bQuery;
                    cytAllModelsPage = 0;
                    rebuildMainRows();
                    focusFirstRow();
                });
            }
        }

        List<CleanYourTv.ModelEntry> all = CleanYourTv.loadIndex(this);
        String q = cytModelFilterQuery.trim().toLowerCase(java.util.Locale.US);
        List<CleanYourTv.ModelEntry> filtered = new ArrayList<>();
        for (CleanYourTv.ModelEntry m : all) {
            if (q.isEmpty()
                    || m.slug.toLowerCase(java.util.Locale.US).contains(q)
                    || m.title.toLowerCase(java.util.Locale.US).contains(q)
                    || m.headerMeta.toLowerCase(java.util.Locale.US).contains(q)) {
                filtered.add(m);
            }
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (total + CYT_PAGE_SIZE - 1) / CYT_PAGE_SIZE);
        if (cytAllModelsPage >= totalPages) cytAllModelsPage = totalPages - 1;
        if (cytAllModelsPage < 0) cytAllModelsPage = 0;
        int start = cytAllModelsPage * CYT_PAGE_SIZE;
        int end = Math.min(total, start + CYT_PAGE_SIZE);

        if (totalPages > 1) {
            section(I18n.resourceString(this, R.string.cyt_page_range,
                    cytAllModelsPage + 1, totalPages, start + 1, end, total));
            if (cytAllModelsPage > 0) {
                row("⬅  " + I18n.resourceString(SettingsActivity.this, R.string.text_previous_page),
                        () -> (cytAllModelsPage) + " / " + totalPages,
                        () -> {
                            cytAllModelsPage--;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
            if (cytAllModelsPage + 1 < totalPages) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_next_page) + "  ➡",
                        () -> (cytAllModelsPage + 2) + " / " + totalPages,
                        () -> {
                            cytAllModelsPage++;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
        }

        String currentSlug = CleanYourTv.getSelectedModelSlug(this);
        section(I18n.resourceString(this, R.string.cyt_models_count, total));
        for (int i = start; i < end; i++) {
            final CleanYourTv.ModelEntry entry = filtered.get(i);
            boolean isCurrent = entry.slug.equalsIgnoreCase(currentSlug);
            choiceRow(entry.title,
                    "[" + entry.slug + "]  •  " + entry.packages.size() + " " + I18n.resourceString(SettingsActivity.this, R.string.text_packages),
                    isCurrent,
                    () -> selectCytModelAndProceed(entry));
        }

        if (totalPages > 1) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_page_navigation));
            if (cytAllModelsPage + 1 < totalPages) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_next_page) + "  ➡",
                        () -> I18n.resourceString(this, R.string.cyt_page_number, cytAllModelsPage + 2, totalPages),
                        () -> {
                            cytAllModelsPage++;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
            if (cytAllModelsPage > 0) {
                row("⬅  " + I18n.resourceString(SettingsActivity.this, R.string.text_previous_page),
                        () -> I18n.resourceString(this, R.string.cyt_page_number, cytAllModelsPage, totalPages),
                        () -> {
                            cytAllModelsPage--;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
        }
    }

    private void promptCytModelFilter() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(I18n.resourceString(SettingsActivity.this, R.string.text_e_g_tcl_philips_sony_hisense_xiaomi));
        in.setText(cytModelFilterQuery);
        in.setSelection(in.getText().length());
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_filter_tv_models))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_filter_b3bfd1), (d, w) -> {
                    cytModelFilterQuery = in.getText().toString().trim();
                    cytAllModelsPage = 0;
                    rebuildMainRows();
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void subMenuCytDebloat() {
        String slug = CleanYourTv.getSelectedModelSlug(this);
        final CleanYourTv.ModelEntry model = CleanYourTv.findModelBySlug(this, slug);
        if (model == null) {
            cytAutoContinueToDebloat = true;
            activeSubMenu = "cyt_models";
            subMenuCytModels();
            return;
        }

        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_debloat_tv_af7d50) + model.title);
        backSubMenuRow();

        if (cytChecklistItems == null || !slug.equals(cytChecklistSlug)) {
            if (!cytChecklistLoading) {
                cytChecklistLoading = true;
                new Thread(() -> {
                    Set<String> enabled = new HashSet<>();
                    Set<String> disabled = new HashSet<>();
                    Set<String> installed = CleanYourTv.fetchInstalledPackages(this, enabled, disabled);
                    boolean hasSetupwraith = installed.contains("com.google.android.tungsten.setupwraith");
                    boolean isGoogleTv = hasSetupwraith || installed.contains("com.google.android.apps.tv.launcherx");
                    boolean wifiOwned = false;
                    if (isGoogleTv) {
                        try {
                            String wifiDump = LocalAdb.execShellOutput(this, "dumpsys wifi | grep -o 'cname=[^ ]*'");
                            if (wifiDump != null && wifiDump.contains("com.google.android.tungsten.setupwraith")) {
                                wifiOwned = true;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    List<CleanYourTv.DebloatItem> built = CleanYourTv.buildChecklistForModel(this, slug, installed, false);
                    final boolean fGoogle = isGoogleTv;
                    final boolean fWifi = wifiOwned;
                    runOnUiThread(() -> {
                        cytChecklistLoading = false;
                        cytChecklistSlug = slug;
                        cytCachedEnabledPkgs = enabled;
                        cytChecklistIsGoogleTv = fGoogle;
                        cytChecklistWifiOwnedBySetupwraith = fWifi;
                        cytChecklistItems = built;
                        if (!isFinishing() && "cyt_debloat".equals(activeSubMenu)) {
                            rebuildMainRows();
                        }
                    });
                }, "cyt-submenu-debloat").start();
            }
            infoNote(I18n.resourceString(this, model.title.isEmpty()
                    ? R.string.cyt_checking_model_empty : R.string.cyt_checking_model, model.title));
            return;
        }

        if (cytChecklistItems.isEmpty()) {
            infoNote(I18n.resourceString(this, model.title.isEmpty()
                    ? R.string.cyt_no_model_packages_empty : R.string.cyt_no_model_packages, model.title));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_scan_installed_system_apps_to_find_match),
                    () -> chev(),
                    () -> {
                        cytScannedMatches = null;
                        openSubMenu("cyt_models_scan");
                    });
            return;
        }

        if (cytChecklistIsGoogleTv) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_google_tv_wi_fi_notice));
            infoNote(cytChecklistWifiOwnedBySetupwraith
                    ? I18n.resourceString(SettingsActivity.this, R.string.text_your_wi_fi_network_is_currently_owned_by)
                    : I18n.resourceString(SettingsActivity.this, R.string.text_on_google_tv_if_you_disable_setupwraith));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_open_wi_fi_settings),
                    () -> chev(),
                    () -> {
                        Intent wifi = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        if (!Suggestions.start(this, wifi)) {
                            Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                        }
                    });
        }

        int selectedCount = 0;
        for (CleanYourTv.DebloatItem it : cytChecklistItems) {
            if (it.checked) selectedCount++;
        }
        final int finalSelectedCount = selectedCount;

        section(I18n.resourceString(SettingsActivity.this, R.string.text_actions));
        row(I18n.resourceString(this, R.string.cyt_disable_selected_packages, finalSelectedCount),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_run_via_adb),
                () -> {
                    List<String> toDisable = new ArrayList<>();
                    for (CleanYourTv.DebloatItem it : cytChecklistItems) {
                        if (it.checked) toDisable.add(it.pkg);
                    }
                    if (toDisable.isEmpty()) {
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_select_at_least_one_package_first));
                        return;
                    }
                    CleanYourTv.executeDebloatPackages(this, model.title, toDisable, () -> {
                        cytChecklistItems = null;
                        rebuildMainRows();
                    });
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_select_all_select_none),
                () -> finalSelectedCount + " / " + cytChecklistItems.size(),
                () -> {
                    boolean anyUnchecked = false;
                    for (CleanYourTv.DebloatItem it : cytChecklistItems) {
                        if (!it.checked) {
                            anyUnchecked = true;
                            break;
                        }
                    }
                    for (CleanYourTv.DebloatItem it : cytChecklistItems) {
                        it.checked = anyUnchecked;
                    }
                    rebuildMainRows();
                });

        section(I18n.resourceString(this, R.string.cyt_packages_on_tv, cytChecklistItems.size()));
        for (CleanYourTv.DebloatItem it : cytChecklistItems) {
            final CleanYourTv.DebloatItem item = it;
            boolean alreadyDisabled = !cytCachedEnabledPkgs.isEmpty() && !cytCachedEnabledPkgs.contains(item.pkg);
            String tag = alreadyDisabled
                    ? I18n.resourceString(SettingsActivity.this, R.string.text_already_disabled)
                    : (item.optionalChoice ? I18n.resourceString(SettingsActivity.this, R.string.text_optional) : "");
            String sub = item.pkg + (item.description.isEmpty() ? "" : "  •  " + item.description);
            choiceRow(item.appLabel + tag, sub, item.checked, () -> {
                item.checked = !item.checked;
                rebuildMainRows();
            });
        }
    }

    private void subMenuCytSystemLaunchers() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_disable_system_launchers_13dcb9));
        backSubMenuRow();

        if (!cytLaunchersSyncedAdb) {
            cytLaunchersSyncedAdb = true;
            new Thread(() -> {
                Set<String> enabled = new HashSet<>();
                Set<String> disabled = new HashSet<>();
                Set<String> installed = CleanYourTv.fetchInstalledPackages(this, enabled, disabled);
                runOnUiThread(() -> {
                    cytCachedInstalledPkgs = installed;
                    cytCachedEnabledPkgs = enabled;
                    if (!isFinishing() && "cyt_launchers".equals(activeSubMenu)) {
                        rebuildMainRows();
                    }
                });
            }, "cyt-sync-launchers").start();
        }

        List<CleanYourTv.DebloatItem> launchers = CleanYourTv.getInstalledSystemLaunchers(
                this,
                cytCachedInstalledPkgs.isEmpty() ? null : cytCachedInstalledPkgs,
                cytCachedEnabledPkgs.isEmpty() ? null : cytCachedEnabledPkgs);
        if (launchers.isEmpty()) {
            infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_no_stock_android_tv_google_tv_oem_launch));
            return;
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_one_tap_actions));
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_disable_all_stock_launchers_set_tvos_lau),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_disable_all),
                () -> {
                    List<String> toDisable = new ArrayList<>();
                    for (CleanYourTv.DebloatItem it : launchers) {
                        toDisable.add(it.pkg);
                    }
                    CleanYourTv.executeDebloatPackages(this, I18n.resourceString(SettingsActivity.this, R.string.text_system_launchers_d659b5), toDisable, () -> {
                        cytLaunchersSyncedAdb = false;
                        rebuildMainRows();
                    });
                });
        twoLineRow(I18n.resourceString(SettingsActivity.this, R.string.text_re_enable_all_stock_launchers),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_enable_all),
                () -> {
                    List<String> toEnable = new ArrayList<>();
                    for (CleanYourTv.DebloatItem it : launchers) {
                        toEnable.add(it.pkg);
                    }
                    CleanYourTv.enablePackagesList(this, toEnable, false, () -> {
                        cytLaunchersSyncedAdb = false;
                        rebuildMainRows();
                    });
                });

        section(I18n.resourceString(SettingsActivity.this, R.string.text_installed_system_launchers_on_this_tv_s, launchers.size()));
        for (CleanYourTv.DebloatItem it : launchers) {
            final CleanYourTv.DebloatItem item = it;
            boolean isEnabled = item.checked;
            String badge = isEnabled
                    ? I18n.resourceString(SettingsActivity.this, R.string.text_enabled_tap_to_disable_fff589)
                    : I18n.resourceString(SettingsActivity.this, R.string.text_disabled_tap_to_enable_85233e);
            choiceRow(item.appLabel + badge,
                    item.pkg + "  •  " + item.description,
                    !isEnabled,
                    () -> CleanYourTv.toggleSinglePackage(this, item.pkg, isEnabled, false, () -> {
                        cytLaunchersSyncedAdb = false;
                        rebuildMainRows();
                    }));
        }

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_checked_items_are_currently_disabled_dis));
    }

    private void subMenuCytManualDebloat() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_manual_debloat_all_system_apps_22aced));
        backSubMenuRow();

        // Populate immediately from PackageManager (instant with QUERY_ALL_PACKAGES + cached labels)
        if (cytManualItems == null) {
            cytManualItems = CleanYourTv.getAllSystemAppsForManualDebloat(
                    this,
                    cytCachedInstalledPkgs.isEmpty() ? null : cytCachedInstalledPkgs,
                    cytCachedEnabledPkgs.isEmpty() ? null : cytCachedEnabledPkgs);
            if (!cytManualLoading) {
                cytManualLoading = true;
                new Thread(() -> {
                    Set<String> enabled = new HashSet<>();
                    Set<String> disabled = new HashSet<>();
                    Set<String> installed = CleanYourTv.fetchInstalledPackages(this, enabled, disabled);
                    List<CleanYourTv.DebloatItem> allSys = CleanYourTv.getAllSystemAppsForManualDebloat(this, installed, enabled);
                    runOnUiThread(() -> {
                        cytManualLoading = false;
                        cytCachedInstalledPkgs = installed;
                        cytCachedEnabledPkgs = enabled;
                        cytManualItems = allSys;
                        if (!isFinishing() && "cyt_manual".equals(activeSubMenu)) {
                            rebuildMainRows();
                        }
                    });
                }, "cyt-submenu-manual").start();
            }
        }

        row(I18n.resourceString(SettingsActivity.this, R.string.text_filter_system_apps_b26cf2),
                () -> cytManualFilterQuery.isEmpty() ? I18n.resourceString(SettingsActivity.this, R.string.text_all_s, cytManualItems.size()) : "\"" + cytManualFilterQuery + "\"",
                this::promptCytManualFilter);
        if (!cytManualFilterQuery.isEmpty()) {
            row(I18n.resourceString(SettingsActivity.this, R.string.text_clear_filter),
                    () -> "",
                    () -> {
                        cytManualFilterQuery = "";
                        cytManualPage = 0;
                        rebuildMainRows();
                    });
        }

        row(I18n.resourceString(SettingsActivity.this, R.string.text_refresh_system_app_list),
                () -> "",
                () -> {
                    cytManualItems = null;
                    cytManualPage = 0;
                    rebuildMainRows();
                });

        String q = cytManualFilterQuery.trim().toLowerCase(java.util.Locale.US);
        List<CleanYourTv.DebloatItem> filtered = new ArrayList<>();
        for (CleanYourTv.DebloatItem it : cytManualItems) {
            if (q.isEmpty()
                    || it.appLabel.toLowerCase(java.util.Locale.US).contains(q)
                    || it.pkg.toLowerCase(java.util.Locale.US).contains(q)) {
                filtered.add(it);
            }
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (total + CYT_PAGE_SIZE - 1) / CYT_PAGE_SIZE);
        if (cytManualPage >= totalPages) cytManualPage = totalPages - 1;
        if (cytManualPage < 0) cytManualPage = 0;
        int start = cytManualPage * CYT_PAGE_SIZE;
        int end = Math.min(total, start + CYT_PAGE_SIZE);

        if (totalPages > 1) {
            section(I18n.resourceString(this, R.string.cyt_page_range,
                    cytManualPage + 1, totalPages, start + 1, end, total));
            if (cytManualPage > 0) {
                row("⬅  " + I18n.resourceString(SettingsActivity.this, R.string.text_previous_page),
                        () -> (cytManualPage) + " / " + totalPages,
                        () -> {
                            cytManualPage--;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
            if (cytManualPage + 1 < totalPages) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_next_page) + "  ➡",
                        () -> (cytManualPage + 2) + " / " + totalPages,
                        () -> {
                            cytManualPage++;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
        }

        section(I18n.resourceString(this, R.string.cyt_all_system_apps, total));
        for (int i = start; i < end; i++) {
            final CleanYourTv.DebloatItem item = filtered.get(i);
            final boolean isCurrentlyEnabled = item.checked;
            final boolean isCritical = CleanYourTv.isCriticalProtectedService(item.pkg, item.appLabel);
            String statusTag = isCritical && isCurrentlyEnabled
                    ? I18n.resourceString(SettingsActivity.this, R.string.text_critical_system_service_protected)
                    : (isCurrentlyEnabled
                            ? I18n.resourceString(SettingsActivity.this, R.string.text_enabled)
                            : I18n.resourceString(SettingsActivity.this, R.string.text_disabled_31f3e7));
            String sub = item.pkg + "  •  " + item.description;
            choiceRow(item.appLabel + statusTag, sub, !isCurrentlyEnabled, () -> {
                if (isCurrentlyEnabled && isCritical) {
                    dialog(I18n.resourceString(SettingsActivity.this, R.string.text_critical_system_service))
                            .setMessage(CleanYourTv.labelAndPackageMessage(this, R.string.cyt_critical_service_warning,
                                    R.string.cyt_critical_service_warning_hebrew_fallback, item.appLabel, item.pkg))
                            .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_ok), null)
                            .show();
                } else if (isCurrentlyEnabled && item.optionalChoice) {
                    dialog(I18n.resourceString(SettingsActivity.this, R.string.text_core_system_package))
                            .setMessage(CleanYourTv.labelAndPackageMessage(this, R.string.cyt_core_package_warning,
                                    R.string.cyt_core_package_warning_hebrew_fallback, item.appLabel, item.pkg))
                            .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_disable_anyway), (d, w) -> {
                                CleanYourTv.toggleSinglePackage(this, item.pkg, true, true, () -> {
                                    item.checked = false;
                                    cytCachedEnabledPkgs.remove(item.pkg);
                                    rebuildMainRows();
                                });
                            })
                            .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                            .show();
                } else {
                    CleanYourTv.toggleSinglePackage(this, item.pkg, isCurrentlyEnabled, true, () -> {
                        item.checked = !isCurrentlyEnabled;
                        if (item.checked) {
                            cytCachedEnabledPkgs.add(item.pkg);
                        } else {
                            cytCachedEnabledPkgs.remove(item.pkg);
                        }
                        rebuildMainRows();
                    });
                }
            });
        }

        if (totalPages > 1) {
            section(I18n.resourceString(SettingsActivity.this, R.string.text_page_navigation));
            if (cytManualPage + 1 < totalPages) {
                row(I18n.resourceString(SettingsActivity.this, R.string.text_next_page) + "  ➡",
                        () -> I18n.resourceString(this, R.string.cyt_page_number, cytManualPage + 2, totalPages),
                        () -> {
                            cytManualPage++;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
            if (cytManualPage > 0) {
                row("⬅  " + I18n.resourceString(SettingsActivity.this, R.string.text_previous_page),
                        () -> I18n.resourceString(this, R.string.cyt_page_number, cytManualPage, totalPages),
                        () -> {
                            cytManualPage--;
                            rebuildMainRows();
                            focusFirstRow();
                        });
            }
        }
    }

    private void promptCytManualFilter() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(I18n.resourceString(SettingsActivity.this, R.string.text_app_name_or_package_name));
        in.setText(cytManualFilterQuery);
        in.setSelection(in.getText().length());
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_filter_system_apps_f2c530))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_filter_b3bfd1), (d, w) -> {
                    cytManualFilterQuery = in.getText().toString().trim();
                    cytManualPage = 0;
                    rebuildMainRows();
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void subMenuCytSavedDisabled() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_disabled_apps_backup));
        backSubMenuRow();

        List<String> saved = CleanYourTv.getSavedDisabledPackages(this);
        if (saved.isEmpty()) {
            infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_no_disabled_packages_have_been_recorded));
            return;
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_actions));
        row(I18n.resourceString(this, R.string.cyt_restore_all_saved_packages, saved.size()),
                () -> I18n.resourceString(SettingsActivity.this, R.string.text_restore_all_0aeae4),
                () -> CleanYourTv.enablePackagesList(this, saved, true, this::rebuildMainRows));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_saved_disabled_packages_tap_to_re_enable));
        PackageManager pm = getPackageManager();
        for (String pkg : saved) {
            final String p = pkg;
            String label = CleanYourTv.resolvePackageLabel(this, p);
            boolean enabledNow = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                enabledNow = (ai != null && ai.enabled);
            } catch (Throwable ignored) {
            }
            String st = enabledNow ? I18n.resourceString(SettingsActivity.this, R.string.text_enabled) : I18n.resourceString(SettingsActivity.this, R.string.text_disabled_tap_to_enable_ad587c);
            choiceRow(label + st, p, !enabledNow, () -> {
                CleanYourTv.enablePackagesList(this, Collections.singletonList(p), false, this::rebuildMainRows);
            });
        }
    }

    private void subMenuCytRestore() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_restore_debloated_apps_2f2301));
        backSubMenuRow();

        List<String> saved = CleanYourTv.getSavedDisabledPackages(this);
        String slug = CleanYourTv.getSelectedModelSlug(this);
        CleanYourTv.ModelEntry model = CleanYourTv.findModelBySlug(this, slug);

        section(I18n.resourceString(SettingsActivity.this, R.string.text_restore_options));
        if (!saved.isEmpty()) {
            twoLineRow(I18n.resourceString(this, R.string.cyt_restore_saved_disabled_apps, saved.size()),
                    () -> I18n.resourceString(SettingsActivity.this, R.string.text_re_enable_all_packages_disabled_by_clean),
                    () -> CleanYourTv.enablePackagesList(this, saved, true, this::rebuildMainRows));
        }
        if (model != null && !model.packages.isEmpty()) {
            twoLineRow(I18n.resourceString(this, model.title.isEmpty()
                            ? R.string.cyt_restore_selected_model_empty : R.string.cyt_restore_selected_model,
                            model.title, model.packages.size()),
                    () -> I18n.resourceString(this, model.slug.isEmpty()
                            ? R.string.cyt_reenable_model_packages_empty : R.string.cyt_reenable_model_packages, model.slug),
                    () -> CleanYourTv.enablePackagesList(this, model.packages, true, this::rebuildMainRows));
        }
        if (saved.isEmpty() && model == null) {
            infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_no_saved_disabled_app_backup_was_found_a));
            row(I18n.resourceString(SettingsActivity.this, R.string.text_choose_tv_model_6fead7),
                    () -> chev(),
                    () -> {
                        cytAutoContinueToDebloat = false;
                        openSubMenu("cyt_models");
                    });
        }
    }

    // ================================================================ Row Builders (.shead, .srow)

    private Bitmap getSceneThumb(String assetPath, int reqW, int reqH) {
        String key = assetPath + ":" + reqW + "x" + reqH;
        Bitmap cached = SCENE_THUMB_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        Bitmap raw = Scenes.loadAssetBitmap(this, assetPath, reqW, reqH);
        if (raw != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(raw, reqW, reqH, true);
            if (scaled != raw) raw.recycle();
            SCENE_THUMB_CACHE.put(key, scaled);
            return scaled;
        }
        return null;
    }

    private void bindAssetThumbAsync(final ImageView thumb, final String assetPath, final int reqW, final int reqH) {
        if (assetPath == null || assetPath.isEmpty()) return;
        final String key = assetPath + ":" + reqW + "x" + reqH;
        Bitmap cached = SCENE_THUMB_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            thumb.setImageBitmap(cached);
            return;
        }
        thumb.setImageBitmap(buildPlaceholderThumb(reqW, reqH));
        THUMB_EXEC.execute(() -> {
            Bitmap loaded = getSceneThumb(assetPath, reqW, reqH);
            if (loaded != null) {
                runOnUiThread(() -> {
                    if (!isFinishing()) thumb.setImageBitmap(loaded);
                });
            }
        });
    }

    private void bindCustomThumbAsync(final ImageView thumb, final String cacheKey, final int reqW, final int reqH,
                                      final java.util.concurrent.Callable<Bitmap> loader) {
        Bitmap cached = SCENE_THUMB_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            thumb.setImageBitmap(cached);
            return;
        }
        thumb.setImageBitmap(buildPlaceholderThumb(reqW, reqH));
        THUMB_EXEC.execute(() -> {
            try {
                Bitmap loaded = loader.call();
                if (loaded != null) {
                    SCENE_THUMB_CACHE.put(cacheKey, loaded);
                    runOnUiThread(() -> {
                        if (!isFinishing()) thumb.setImageBitmap(loaded);
                    });
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** Sub-heading (.shead in couch-tv). */
    void section(String name) {
        TextView t = new TextView(this);
        t.setText(name.toUpperCase(I18n.locale(this)));
        t.setTextColor(0x80FFFFFF);
        t.setTypeface(Ui.font(this, "semibold"));
        t.setLetterSpacing(0.06f);
        Ui.textSize(t, 10.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(u(14));
        lp.topMargin = list.getChildCount() == 0 ? u(2) : u(12);
        lp.bottomMargin = u(4);
        list.addView(t, lp);
    }

    /** Choice row (.srow.k-choice in couch-tv): bold title, optional subtitle, and stroke checkmark on the right. */
    private void choiceRow(String titleText, String subText, boolean checked, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPaddingRelative(u(16), u(9), u(16), u(9));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF); // rgba(255, 255, 255, 0.08)
        r.setBackground(bg);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(titleText);
        t.setTextColor(0xEBF4F4F6);
        t.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(t, 14.5f);
        texts.addView(t);

        TextView s = null;
        if (subText != null && !subText.isEmpty()) {
            s = new TextView(this);
            s.setText(subText);
            s.setTextColor(0x99FFFFFF);
            s.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(s, 11f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = u(1);
            texts.addView(s, slp);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(17);
        if (cachedCheckWhite == null) {
            cachedCheckWhite = Glyphs.get("check", cs, Color.WHITE);
        }
        if (cachedCheckDark == null) {
            cachedCheckDark = Glyphs.get("check", cs, 0xFF111114);
        }
        ImageView mark = new ImageView(this);
        if (checked) {
            mark.setImageBitmap(cachedCheckWhite);
        }
        r.addView(mark, new LinearLayout.LayoutParams(cs, cs));

        final TextView sRef = s;
        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            t.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            if (sRef != null) sRef.setTextColor(has ? 0x99000000 : 0x99FFFFFF);
            if (checked) {
                mark.setImageBitmap(has ? cachedCheckDark : cachedCheckWhite);
            }
        });
        r.setOnClickListener(view -> onClick.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    /**
     * Scene row (.srow.k-check in couch-tv):
     * 16:9 rounded thumbnail (.sthumb) + scene name + green circular check badge (.sbox.on).
     * Long-pressing OK lifts the row in-place so Up/Down reorders scenes directly.
     */
    private void sceneCheckRow(Scenes.Spec spec, int sceneIdx, List<String> order) {
        final boolean isLifted = spec.id.equals(liftedSceneId);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setTag("scene:" + spec.id);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        // 16:9 Scene Thumbnail (.sthumb: 5.5rem x 3.125rem -> 46dp x 26dp)
        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bindAssetThumbAsync(thumb, spec.bgAsset, tw * 2, th * 2);
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(spec.title(this));
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(20);
        ImageView check = new ImageView(this);
        Runnable updateCheck = () -> {
            if (spec.id.equals(liftedSceneId)) {
                check.setImageBitmap(Glyphs.get("updown", cs, r.isFocused() ? 0xFF111114 : Color.WHITE));
            } else {
                boolean on = Scenes.isSceneEnabled(this, spec.id);
                if (on) {
                    check.setImageBitmap(Glyphs.get("check_on", cs, Color.WHITE));
                } else {
                    check.setImageBitmap(Glyphs.get(r.isFocused() ? "check_off_dark" : "check_off", cs, Color.WHITE));
                }
            }
        };
        updateCheck.run();
        refreshers.add(updateCheck);
        r.addView(check, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(true);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            updateCheck.run();
            if (isLifted) {
                float scale = has ? 1.04f : 1f;
                float tx = has ? u(6) : 0f;
                view.animate().scaleX(scale).scaleY(scale).translationX(tx)
                        .setDuration(160).start();
            } else {
                view.animate().cancel();
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setTranslationX(0f);
            }
        });

        r.setOnClickListener(view -> {
            if (liftedSceneId != null) {
                liftedSceneId = null;
                updateHintText(true);
                refreshAll();
                view.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(150).start();
                return;
            }
            if (!Scenes.toggleScene(this, spec.id)) {
                showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_one_scene_has_to_stay_on));
            } else {
                showStatusMsg(null);
            }
            refreshAll();
        });

        r.setOnLongClickListener(view -> {
            if (liftedSceneId != null) {
                liftedSceneId = null;
            } else {
                liftedSceneId = spec.id;
            }
            updateHintText(true);
            updateCheck.run();
            boolean nowLifted = spec.id.equals(liftedSceneId);
            view.animate().scaleX(nowLifted ? 1.04f : 1f)
                    .scaleY(nowLifted ? 1.04f : 1f)
                    .translationX(nowLifted ? u(6) : 0f)
                    .setDuration(160).start();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private Bitmap buildPlaceholderThumb(int w, int h) {
        Bitmap cached = sPlaceholderThumb;
        if (cached != null && !cached.isRecycled() && cached.getWidth() == w && cached.getHeight() == h) {
            return cached;
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(
                0, 0, w, h,
                0xFF282B3A, 0xFF151722,
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int gsz = Math.min(w, h) * 3 / 5;
        Bitmap glyph = Glyphs.get("image", gsz, 0xCCFFFFFF);
        if (glyph != null) {
            c.drawBitmap(glyph, (w - gsz) / 2f, (h - gsz) / 2f, null);
        }
        sPlaceholderThumb = bmp;
        return bmp;
    }

    private String customSceneTitle(int slot) {
        if (slot <= 1) return I18n.resourceString(SettingsActivity.this, R.string.text_custom);
        return Scenes.byId(Scenes.customId(slot)).title(this);
    }

    private void pickCustomSceneWallpaper(int slot) {
        CustomMedia.showImageFilePicker(
                this,
                I18n.resourceString(SettingsActivity.this, R.string.text_choose_custom_scene_image) + (slot > 1 ? " (" + slot + ")" : ""),
                false,
                1920,
                1080,
                null,
                null,
                bmp -> {
                    if (CustomMedia.setCustomSceneWallpaper(this, slot, bmp)) {
                        SCENE_THUMB_CACHE.remove("custom_scene:" + slot);
                        sCachedBackdropKey = null;
                        Scenes.enableCustomScene(this, slot, true);
                        rebuildMainRows();
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_custom_scene_wallpaper_updated));
                    } else {
                        Toast.makeText(
                                this,
                                I18n.resourceString(SettingsActivity.this, R.string.text_could_not_load_selected_image),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void customSceneRow(int slot) {
        final String cid = Scenes.customId(slot);
        final String labelText = customSceneTitle(slot);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setTag("scene:" + cid);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (CustomMedia.hasCustomSceneWallpaper(this, slot)) {
            bindCustomThumbAsync(thumb, "custom_scene:" + slot, tw * 2, th * 2,
                    () -> CustomMedia.getCustomSceneWallpaper(this, slot, tw * 2, th * 2));
        } else {
            thumb.setImageBitmap(buildPlaceholderThumb(tw * 2, th * 2));
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(labelText);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(20);
        ImageView check = new ImageView(this);
        Runnable updateCheck = () -> {
            boolean on = Scenes.isSceneEnabled(this, cid);
            if (on) {
                check.setImageBitmap(Glyphs.get("check_on", cs, Color.WHITE));
            } else {
                check.setImageBitmap(Glyphs.get(r.isFocused() ? "check_off_dark" : "check_off", cs, Color.WHITE));
            }
        };
        updateCheck.run();
        refreshers.add(updateCheck);
        r.addView(check, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            updateCheck.run();
        });

        r.setOnClickListener(view -> {
            if (!CustomMedia.hasCustomSceneWallpaper(this, slot)) {
                pickCustomSceneWallpaper(slot);
                return;
            }
            boolean enabled = Scenes.isSceneEnabled(this, cid);
            if (!enabled) {
                String[] opts = {
                    I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_custom_scene),
                    I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_44c803)
                };
                dialog(labelText)
                        .setItems(opts, (d, which) -> {
                            if (which == 0) {
                                Scenes.enableCustomScene(this, slot, true);
                                rebuildMainRows();
                            } else {
                                pickCustomSceneWallpaper(slot);
                            }
                        })
                        .show();
            } else {
                String[] opts = {
                    I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_44c803),
                    I18n.resourceString(SettingsActivity.this, R.string.text_turn_off_custom_scene)
                };
                dialog(labelText)
                        .setItems(opts, (d, which) -> {
                            if (which == 0) {
                                pickCustomSceneWallpaper(slot);
                            } else {
                                if (!Scenes.toggleScene(this, cid)) {
                                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_one_scene_has_to_stay_on));
                                } else {
                                    rebuildMainRows();
                                }
                            }
                        })
                        .show();
            }
        });

        r.setOnLongClickListener(view -> {
            pickCustomSceneWallpaper(slot);
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void pickCustomClassicWallpaper() {
        CustomMedia.showImageFilePicker(
                this,
                I18n.resourceString(SettingsActivity.this, R.string.text_choose_custom_background_image),
                false,
                1920,
                1080,
                null,
                null,
                bmp -> {
                    if (CustomMedia.setCustomClassicWallpaper(this, bmp)) {
                        SCENE_THUMB_CACHE.remove("custom_classic");
                        Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.CUSTOM_WALL_KEY).apply();
                        rebuildMainRows();
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_custom_background_updated));
                    } else {
                        Toast.makeText(
                                this,
                                I18n.resourceString(SettingsActivity.this, R.string.text_could_not_load_selected_image),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void customClassicPhotoRow(boolean checked) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (CustomMedia.hasCustomClassicWallpaper(this)) {
            bindCustomThumbAsync(thumb, "custom_classic", tw * 2, th * 2, () -> {
                Bitmap full = CustomMedia.getCustomClassicWallpaper(this);
                if (full == null) return null;
                Bitmap scaled = Bitmap.createScaledBitmap(full, tw * 2, th * 2, true);
                if (scaled != full) full.recycle();
                return scaled;
            });
        } else {
            thumb.setImageBitmap(buildPlaceholderThumb(tw * 2, th * 2));
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(I18n.resourceString(SettingsActivity.this, R.string.text_custom));
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(17);
        ImageView mark = new ImageView(this);
        if (checked) {
            mark.setImageBitmap(Glyphs.get("check", cs, Color.WHITE));
        }
        r.addView(mark, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            if (checked) {
                mark.setImageBitmap(Glyphs.get("check", cs, has ? 0xFF111114 : Color.WHITE));
            }
        });

        r.setOnClickListener(view -> {
            if (!CustomMedia.hasCustomClassicWallpaper(this)) {
                pickCustomClassicWallpaper();
                return;
            }
            if (!checked) {
                String[] opts = {
                    I18n.resourceString(SettingsActivity.this, R.string.text_use_custom_background),
                    I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_44c803)
                };
                dialog(I18n.resourceString(SettingsActivity.this, R.string.text_custom))
                        .setItems(opts, (d, which) -> {
                            if (which == 0) {
                                Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.CUSTOM_WALL_KEY).apply();
                                rebuildMainRows();
                            } else {
                                pickCustomClassicWallpaper();
                            }
                        })
                        .show();
            } else {
                pickCustomClassicWallpaper();
            }
        });

        r.setOnLongClickListener(view -> {
            pickCustomClassicWallpaper();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void pickFolderForSceneShuffle() {
        CustomMedia.showFolderPicker(
                this,
                CustomMedia.SCOPE_SCENE,
                I18n.resourceString(SettingsActivity.this, R.string.text_choose_wallpaper_folder_to_shuffle),
                dir -> {
                    SCENE_THUMB_CACHE.remove("folder_scene");
                    Scenes.enableFolderShuffleScene(this, true);
                    rebuildMainRows();
                    String name = dir.getName();
                    showStatusMsg(I18n.resourceString(this, name.isEmpty()
                            ? R.string.folder_shuffle_enabled_empty : R.string.folder_shuffle_enabled, name));
                });
    }

    private void folderShuffleSceneRow() {
        final String fid = Scenes.FOLDER_SHUFFLE_ID;
        boolean hasFolder = CustomMedia.hasWallpaperShuffleFolder(this, CustomMedia.SCOPE_SCENE);
        final String baseTitle = I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setTag("scene:" + fid);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (hasFolder) {
            bindCustomThumbAsync(thumb, "folder_scene", tw * 2, th * 2,
                    () -> CustomMedia.getNextShuffledFolderBitmap(this, CustomMedia.SCOPE_SCENE, tw * 2, th * 2, false));
        } else {
            thumb.setImageBitmap(buildPlaceholderThumb(tw * 2, th * 2));
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(baseTitle);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (hasFolder) {
            THUMB_EXEC.execute(() -> {
                String summary = CustomMedia.getWallpaperShuffleFolderSummary(this, CustomMedia.SCOPE_SCENE);
                if (!summary.isEmpty()) {
                    runOnUiThread(() -> {
                        if (!isFinishing()) l.setText(baseTitle + "  ·  " + summary);
                    });
                }
            });
        }

        int cs = u(20);
        ImageView check = new ImageView(this);
        Runnable updateCheck = () -> {
            boolean on = Scenes.isSceneEnabled(this, fid);
            if (on) {
                check.setImageBitmap(Glyphs.get("check_on", cs, Color.WHITE));
            } else {
                check.setImageBitmap(Glyphs.get(r.isFocused() ? "check_off_dark" : "check_off", cs, Color.WHITE));
            }
        };
        updateCheck.run();
        refreshers.add(updateCheck);
        r.addView(check, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            updateCheck.run();
        });

        r.setOnClickListener(view -> {
            if (!CustomMedia.hasWallpaperShuffleFolder(this, CustomMedia.SCOPE_SCENE)) {
                pickFolderForSceneShuffle();
                return;
            }
            boolean enabled = Scenes.isSceneEnabled(this, fid);
            if (!enabled) {
                String[] opts = {
                    I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_folder_shuffle),
                    I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_768e16)
                };
                dialog(I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle))
                        .setItems(opts, (d, which) -> {
                            if (which == 0) {
                                Scenes.enableFolderShuffleScene(this, true);
                                rebuildMainRows();
                            } else {
                                pickFolderForSceneShuffle();
                            }
                        })
                        .show();
            } else {
                String[] opts = {
                    I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_768e16),
                    I18n.resourceString(SettingsActivity.this, R.string.text_turn_off_folder_shuffle)
                };
                dialog(I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle))
                        .setItems(opts, (d, which) -> {
                            if (which == 0) {
                                pickFolderForSceneShuffle();
                            } else {
                                if (!Scenes.toggleScene(this, fid)) {
                                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_one_scene_has_to_stay_on));
                                } else {
                                    rebuildMainRows();
                                }
                            }
                        })
                        .show();
            }
        });

        r.setOnLongClickListener(view -> {
            pickFolderForSceneShuffle();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void pickFolderForClassicShuffle() {
        CustomMedia.showFolderPicker(
                this,
                CustomMedia.SCOPE_CLASSIC,
                I18n.resourceString(SettingsActivity.this, R.string.text_choose_background_folder_to_shuffle),
                dir -> {
                    SCENE_THUMB_CACHE.remove("folder_classic");
                    Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.FOLDER_SHUFFLE_WALL_KEY).apply();
                    rebuildMainRows();
                    String name = dir.getName();
                    showStatusMsg(I18n.resourceString(this, name.isEmpty()
                            ? R.string.folder_shuffle_enabled_empty : R.string.folder_shuffle_enabled, name));
                });
    }

    private void folderShuffleClassicRow(boolean checked) {
        boolean hasFolder = CustomMedia.hasWallpaperShuffleFolder(this, CustomMedia.SCOPE_CLASSIC);
        final String baseTitle = I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (hasFolder) {
            bindCustomThumbAsync(thumb, "folder_classic", tw * 2, th * 2,
                    () -> CustomMedia.getNextShuffledFolderBitmap(this, CustomMedia.SCOPE_CLASSIC, tw * 2, th * 2, false));
        } else {
            thumb.setImageBitmap(buildPlaceholderThumb(tw * 2, th * 2));
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(baseTitle);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (hasFolder) {
            THUMB_EXEC.execute(() -> {
                String summary = CustomMedia.getWallpaperShuffleFolderSummary(this, CustomMedia.SCOPE_CLASSIC);
                if (!summary.isEmpty()) {
                    runOnUiThread(() -> {
                        if (!isFinishing()) l.setText(baseTitle + "  ·  " + summary);
                    });
                }
            });
        }

        int cs = u(17);
        ImageView mark = new ImageView(this);
        if (checked) {
            mark.setImageBitmap(Glyphs.get("check", cs, Color.WHITE));
        }
        r.addView(mark, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            if (checked) {
                mark.setImageBitmap(Glyphs.get("check", cs, has ? 0xFF111114 : Color.WHITE));
            }
        });

        r.setOnClickListener(view -> {
            if (!CustomMedia.hasWallpaperShuffleFolder(this, CustomMedia.SCOPE_CLASSIC)) {
                pickFolderForClassicShuffle();
                return;
            }
            if (!checked) {
                String[] opts = {
                    I18n.resourceString(SettingsActivity.this, R.string.text_use_folder_shuffle_background),
                    I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_768e16)
                };
                dialog(I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle))
                        .setItems(opts, (d, which) -> {
                            if (which == 0) {
                                Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.FOLDER_SHUFFLE_WALL_KEY).apply();
                                rebuildMainRows();
                            } else {
                                pickFolderForClassicShuffle();
                            }
                        })
                        .show();
            } else {
                pickFolderForClassicShuffle();
            }
        });

        r.setOnLongClickListener(view -> {
            pickFolderForClassicShuffle();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void subMenuLockScreenSettings() {
        mainHeadView.setText(I18n.resourceString(SettingsActivity.this, R.string.text_lock_screen_settings_d4db1f));
        backSubMenuRow();

        section(I18n.resourceString(SettingsActivity.this, R.string.text_lock_screen_options));
        row(I18n.resourceString(SettingsActivity.this, R.string.text_clock_position),
                () -> Scenes.lockClockPosLabel(this),
                () -> {
                    Scenes.cycleLockClockPos(this);
                    refreshAll();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_auto_launch_lock_screen_when_idle),
                () -> Scenes.lockIdleLabel(this),
                () -> {
                    Scenes.cycleLockIdleMinutes(this);
                    refreshAll();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_each_wallpaper_stays),
                () -> Scenes.getStaySeconds(this) + I18n.resourceString(SettingsActivity.this, R.string.text_seconds_ee379b),
                () -> {
                    Scenes.cycleStaySeconds(this);
                    refreshAll();
                });
        row(I18n.resourceString(SettingsActivity.this, R.string.text_preview_lock_screen_now),
                () -> chev(),
                () -> MainActivity.triggerLockScreen(this));

        section(I18n.resourceString(SettingsActivity.this, R.string.text_night_scenes_b6104d));
        for (Scenes.Spec spec : Scenes.ALL) {
            lockSceneCheckRow(spec.id, spec.title(this), spec.bgAsset);
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_classic_wallpapers));
        for (int i = 0; i < Scenes.CLASSIC_PHOTOS.length; i++) {
            String photoFile = Scenes.CLASSIC_PHOTOS[i].id;
            String id = "photo:" + photoFile;
            String label = Scenes.CLASSIC_PHOTOS[i].title(this);
            lockSceneCheckRow(id, label, "backgrounds/" + photoFile);
        }
        String[] gradIds = {"aurora", "midnight", "sunset", "ocean", "graphite"};
        for (String gid : gradIds) {
            Scenes.Spec gspec = Scenes.byId(gid);
            lockSceneCheckRow(gid, gspec.title(this), null);
        }

        section(I18n.resourceString(SettingsActivity.this, R.string.text_custom_folder_shuffle));
        lockCustomImageRow();
        lockFolderShuffleRow();

        infoNote(I18n.resourceString(SettingsActivity.this, R.string.text_on_the_lock_screen_only_clock_date_and_g));
    }

    private void lockSceneCheckRow(String id, String titleText, String assetPath) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (assetPath != null && !assetPath.isEmpty()) {
            bindAssetThumbAsync(thumb, assetPath, tw * 2, th * 2);
        } else {
            Bitmap tbmp = MainActivity.makeWallpaperBase(id);
            if (tbmp != null) thumb.setImageBitmap(tbmp);
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(titleText);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(20);
        ImageView check = new ImageView(this);
        Runnable updateCheck = () -> {
            boolean on = Scenes.isLockSceneEnabled(this, id);
            if (on) {
                check.setImageBitmap(Glyphs.get("check_on", cs, Color.WHITE));
            } else {
                check.setImageBitmap(Glyphs.get(r.isFocused() ? "check_off_dark" : "check_off", cs, Color.WHITE));
            }
        };
        updateCheck.run();
        refreshers.add(updateCheck);
        r.addView(check, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            updateCheck.run();
        });

        r.setOnClickListener(view -> {
            if (!Scenes.toggleLockScene(this, id)) {
                showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_one_wallpaper_has_to_stay_on));
            } else {
                showStatusMsg(null);
            }
            refreshAll();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void pickCustomLockWallpaper() {
        CustomMedia.showImageFilePicker(
                this,
                I18n.resourceString(SettingsActivity.this, R.string.text_choose_lock_screen_image),
                false,
                1920,
                1080,
                null,
                null,
                bmp -> {
                    if (CustomMedia.setCustomLockWallpaper(this, bmp)) {
                        SCENE_THUMB_CACHE.remove("custom_lock");
                        Scenes.enableLockScene(this, Scenes.LOCK_CUSTOM_ID, true);
                        rebuildMainRows();
                        showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_custom_lock_screen_image_updated));
                    }
                });
    }

    private void lockCustomImageRow() {
        final String cid = Scenes.LOCK_CUSTOM_ID;
        boolean hasCustom = CustomMedia.hasCustomLockWallpaper(this);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (hasCustom) {
            bindCustomThumbAsync(thumb, "custom_lock", tw * 2, th * 2,
                    () -> CustomMedia.getCustomLockWallpaper(this, tw * 2, th * 2));
        } else {
            thumb.setImageBitmap(buildPlaceholderThumb(tw * 2, th * 2));
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(I18n.resourceString(SettingsActivity.this, R.string.text_custom_image));
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(20);
        ImageView check = new ImageView(this);
        Runnable updateCheck = () -> {
            boolean on = Scenes.isLockSceneEnabled(this, cid);
            if (on) {
                check.setImageBitmap(Glyphs.get("check_on", cs, Color.WHITE));
            } else {
                check.setImageBitmap(Glyphs.get(r.isFocused() ? "check_off_dark" : "check_off", cs, Color.WHITE));
            }
        };
        updateCheck.run();
        refreshers.add(updateCheck);
        r.addView(check, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            updateCheck.run();
        });

        r.setOnClickListener(view -> {
            if (!CustomMedia.hasCustomLockWallpaper(this)) {
                pickCustomLockWallpaper();
                return;
            }
            boolean enabled = Scenes.isLockSceneEnabled(this, cid);
            String[] opts = enabled
                    ? new String[]{I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_44c803), I18n.resourceString(SettingsActivity.this, R.string.text_turn_off_custom_image)}
                    : new String[]{I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_custom_image), I18n.resourceString(SettingsActivity.this, R.string.text_choose_image_44c803)};
            dialog(I18n.resourceString(SettingsActivity.this, R.string.text_custom_image))
                    .setItems(opts, (d, which) -> {
                        if (enabled) {
                            if (which == 0) pickCustomLockWallpaper();
                            else {
                                Scenes.toggleLockScene(this, cid);
                                rebuildMainRows();
                            }
                        } else {
                            if (which == 0) {
                                Scenes.enableLockScene(this, cid, true);
                                rebuildMainRows();
                            } else {
                                pickCustomLockWallpaper();
                            }
                        }
                    })
                    .show();
        });
        r.setOnLongClickListener(view -> {
            pickCustomLockWallpaper();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void pickLockFolderShuffle() {
        CustomMedia.showFolderPicker(
                this,
                CustomMedia.SCOPE_LOCK,
                I18n.resourceString(SettingsActivity.this, R.string.text_choose_lock_screen_folder_to_shuffle),
                dir -> {
                    SCENE_THUMB_CACHE.remove("folder_lock");
                    Scenes.enableLockScene(this, Scenes.LOCK_FOLDER_SHUFFLE_ID, true);
                    rebuildMainRows();
                    String name = dir.getName();
                    showStatusMsg(I18n.resourceString(this, name.isEmpty()
                            ? R.string.lock_folder_shuffle_enabled_empty : R.string.lock_folder_shuffle_enabled, name));
                });
    }

    private void lockFolderShuffleRow() {
        final String fid = Scenes.LOCK_FOLDER_SHUFFLE_ID;
        boolean hasFolder = CustomMedia.hasWallpaperShuffleFolder(this, CustomMedia.SCOPE_LOCK);
        final String baseTitle = I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle);

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (hasFolder) {
            bindCustomThumbAsync(thumb, "folder_lock", tw * 2, th * 2,
                    () -> CustomMedia.getNextShuffledFolderBitmap(this, CustomMedia.SCOPE_LOCK, tw * 2, th * 2, false));
        } else {
            thumb.setImageBitmap(buildPlaceholderThumb(tw * 2, th * 2));
        }
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(baseTitle);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (hasFolder) {
            THUMB_EXEC.execute(() -> {
                String summary = CustomMedia.getWallpaperShuffleFolderSummary(this, CustomMedia.SCOPE_LOCK);
                if (!summary.isEmpty()) {
                    runOnUiThread(() -> {
                        if (!isFinishing()) l.setText(baseTitle + "  ·  " + summary);
                    });
                }
            });
        }

        int cs = u(20);
        ImageView check = new ImageView(this);
        Runnable updateCheck = () -> {
            boolean on = Scenes.isLockSceneEnabled(this, fid);
            if (on) {
                check.setImageBitmap(Glyphs.get("check_on", cs, Color.WHITE));
            } else {
                check.setImageBitmap(Glyphs.get(r.isFocused() ? "check_off_dark" : "check_off", cs, Color.WHITE));
            }
        };
        updateCheck.run();
        refreshers.add(updateCheck);
        r.addView(check, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            updateCheck.run();
        });

        r.setOnClickListener(view -> {
            if (!CustomMedia.hasWallpaperShuffleFolder(this, CustomMedia.SCOPE_LOCK)) {
                pickLockFolderShuffle();
                return;
            }
            boolean enabled = Scenes.isLockSceneEnabled(this, fid);
            String[] opts = enabled
                    ? new String[]{I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_768e16), I18n.resourceString(SettingsActivity.this, R.string.text_turn_off_folder_shuffle)}
                    : new String[]{I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_folder_shuffle), I18n.resourceString(SettingsActivity.this, R.string.text_choose_folder_768e16)};
            dialog(I18n.resourceString(SettingsActivity.this, R.string.text_folder_shuffle))
                    .setItems(opts, (d, which) -> {
                        if (enabled) {
                            if (which == 0) pickLockFolderShuffle();
                            else {
                                Scenes.toggleLockScene(this, fid);
                                rebuildMainRows();
                            }
                        } else {
                            if (which == 0) {
                                Scenes.enableLockScene(this, fid, true);
                                rebuildMainRows();
                            } else {
                                pickLockFolderShuffle();
                            }
                        }
                    })
                    .show();
        });
        r.setOnLongClickListener(view -> {
            pickLockFolderShuffle();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    private void classicPhotoRow(String label, String assetPath, boolean checked, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPaddingRelative(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bindAssetThumbAsync(thumb, assetPath, tw * 2, th * 2);
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.setMarginEnd(u(12));
        r.addView(thumb, thlp);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = u(17);
        ImageView mark = new ImageView(this);
        if (checked) {
            mark.setImageBitmap(Glyphs.get("check", cs, Color.WHITE));
        }
        r.addView(mark, new LinearLayout.LayoutParams(cs, cs));

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            if (checked) {
                mark.setImageBitmap(Glyphs.get("check", cs, has ? 0xFF111114 : Color.WHITE));
            }
        });
        r.setOnClickListener(view -> onClick.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(40));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    interface Value {
        String get();
    }

    /** Standard value/cycle row (.srow in couch-tv): bold label on left, muted value on right. */
    View row(String label, Value value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPaddingRelative(u(16), 0, u(16), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setTextColor(0x9EFFFFFF);
        v.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(v, 14f);
        v.setSingleLine(true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.setMarginStart(u(10));
        r.addView(v, vlp);

        Runnable refresh = () -> {
            try {
                v.setText(value.get());
            } catch (Throwable ignored) {
                v.setText("");
            }
        };
        refresh.run();
        refreshers.add(refresh);

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            v.setTextColor(has ? 0x99000000 : 0x9EFFFFFF);
        });
        r.setOnClickListener(view -> {
            action.run();
            for (Runnable x : refreshers) x.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(39));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
        return r;
    }

    /** Double-line row: 1st line is bold title (e.g. key name), 2nd line is muted subtitle (e.g. remapped functions). */
    View twoLineRow(String label, Value subValue, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPaddingRelative(u(16), u(9), u(16), u(9));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xEBF4F4F6);
        l.setTypeface(Ui.font(this, "semibold"));
        Ui.textSize(l, 14.5f);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(l);

        TextView v = new TextView(this);
        v.setTextColor(0x9EFFFFFF);
        v.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(v, 11.5f);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = u(2);
        texts.addView(v, vlp);

        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chev = new TextView(this);
        chev.setText(chev());
        chev.setTextColor(0x9EFFFFFF);
        chev.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(chev, 15f);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMarginStart(u(10));
        r.addView(chev, clp);

        Runnable refresh = () -> {
            try {
                v.setText(subValue.get());
            } catch (Throwable ignored) {
                v.setText("");
            }
        };
        refresh.run();
        refreshers.add(refresh);

        r.setOnFocusChangeListener((view, has) -> {
            if (has) {
                inRows = true;
                updateSidebarStyles();
                updateHintText(false);
            }
            bg.setColor(has ? Color.WHITE : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            v.setTextColor(has ? 0x99000000 : 0x9EFFFFFF);
            chev.setTextColor(has ? 0x99000000 : 0x9EFFFFFF);
        });
        r.setOnClickListener(view -> {
            action.run();
            for (Runnable x : refreshers) x.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = u(5);
        list.addView(r, lp);
        return r;
    }

    /** Non-focusable info row (.srow.k-info in couch-tv). */
    private void infoRow(String label, String val, String subText) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPaddingRelative(u(14), u(5), u(14), u(5));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xB3FFFFFF);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 12.5f);
        texts.addView(l);

        if (subText != null && !subText.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(subText);
            s.setTextColor(0x80FFFFFF);
            s.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(s, 10.5f);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (val != null && !val.isEmpty()) {
            TextView v = new TextView(this);
            v.setText(val);
            v.setTextColor(0x9EFFFFFF);
            v.setTypeface(Ui.font(this, "medium"));
            Ui.textSize(v, 12.5f);
            r.addView(v);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = u(3);
        list.addView(r, lp);
    }

    private void infoRowDynamic(String label, Value val, String subText) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPaddingRelative(u(14), u(5), u(14), u(5));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xB3FFFFFF);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 12.5f);
        texts.addView(l);

        if (subText != null && !subText.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(subText);
            s.setTextColor(0x80FFFFFF);
            s.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(s, 10.5f);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setTextColor(0x9EFFFFFF);
        v.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(v, 12.5f);
        r.addView(v);

        Runnable refresh = () -> {
            try {
                v.setText(val.get());
            } catch (Throwable ignored) {
                v.setText("");
            }
        };
        refresh.run();
        refreshers.add(refresh);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = u(3);
        list.addView(r, lp);
    }

    private void infoNote(String note) {
        TextView t = new TextView(this);
        t.setText(note);
        t.setTextColor(0x88FFFFFF);
        t.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(t, 11.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(u(14));
        lp.setMarginEnd(u(14));
        lp.topMargin = u(6);
        lp.bottomMargin = u(4);
        list.addView(t, lp);
    }

    // ================================================================ Key Navigation

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        MainActivity.navSound(this, ev);
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;

        // If a scene row is picked up for moving, Up/Down moves it in the scene list
        if (liftedSceneId != null) {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) {
                    moveLiftedScene(k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                if (!down) {
                    liftedSceneId = null;
                    rebuildMainRows();
                    focusSceneById(currentSection);
                }
                return true;
            }
        }

        // Left / Back from the right pane returns focus to the active left sidebar item
        if (inRows) {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) {
                    int dir = (k == KeyEvent.KEYCODE_DPAD_UP) ? -1 : 1;
                    View cur = getCurrentFocus();
                    View next = findAdjacentFocusable(list, cur, dir);
                    if (next != null) {
                        next.requestFocus();
                    } else if (dir > 0 && scroller != null && list != null) {
                        scroller.smoothScrollTo(0, list.getBottom());
                    } else if (dir < 0 && scroller != null) {
                        scroller.smoothScrollTo(0, 0);
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (down) {
                    if (!closeSubMenu()) {
                        View sideRow = sideRowViews.get(currentSection);
                        if (sideRow != null) sideRow.requestFocus();
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                if (!down) {
                    if (!closeSubMenu()) {
                        View sideRow = sideRowViews.get(currentSection);
                        if (sideRow != null) sideRow.requestFocus();
                    }
                }
                return true;
            }
        } else {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) {
                    int dir = (k == KeyEvent.KEYCODE_DPAD_UP) ? -1 : 1;
                    View cur = getCurrentFocus();
                    View next = findAdjacentFocusable(sideList, cur, dir);
                    if (next != null) {
                        next.requestFocus();
                    }
                }
                return true;
            }
            // In left sidebar: Right moves focus to the first focusable row on the right
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) focusFirstRow();
                return true;
            }
        }

        return super.dispatchKeyEvent(ev);
    }

    private View findAdjacentFocusable(ViewGroup container, View focused, int step) {
        if (container == null || focused == null) return null;
        int curIdx = -1;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child == focused || isDescendantOf(focused, child)) {
                curIdx = i;
                break;
            }
        }
        if (curIdx < 0) return null;
        for (int i = curIdx + step; i >= 0 && i < container.getChildCount(); i += step) {
            View v = container.getChildAt(i);
            if (v.getVisibility() == View.VISIBLE && v.isFocusable()) {
                return v;
            }
        }
        return null;
    }

    private boolean isDescendantOf(View target, View potentialParent) {
        if (!(potentialParent instanceof ViewGroup)) return false;
        android.view.ViewParent p = target.getParent();
        while (p != null) {
            if (p == potentialParent) return true;
            p = p.getParent();
        }
        return false;
    }

    private void moveLiftedScene(int delta) {
        if (liftedSceneId == null || Scenes.CUSTOM_ID.equals(liftedSceneId)) return;
        List<String> order = new ArrayList<>();
        for (String id : Scenes.getSceneOrder(this)) {
            if (!Scenes.CUSTOM_ID.equals(id)) order.add(id);
        }
        int idx = order.indexOf(liftedSceneId);
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= order.size()) return;
        String moved = order.remove(idx);
        order.add(target, moved);
        order.add(Scenes.CUSTOM_ID);
        Scenes.setSceneOrder(this, order);
        String keepLifted = liftedSceneId;
        rebuildMainRows();
        liftedSceneId = keepLifted;
        focusSceneById(keepLifted);
    }

    private void focusSceneById(String sceneId) {
        String tag = "scene:" + sceneId;
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (tag.equals(v.getTag())) {
                inRows = true;
                v.requestFocus();
                updateSidebarStyles();
                return;
            }
        }
        focusFirstRow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BUTTON_MAPPER.equals(currentSection)) {
            rebuildMainRows();
        } else {
            refreshAll();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        CustomMedia.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        ButtonMapper.captureListener = null;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refreshAll();
    }

    void refreshAll() {
        for (Runnable x : refreshers) x.run();
    }

    AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title);
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "2.2.5";
        }
    }

    // ================================================================ Dialog Helpers

    void searchCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(I18n.resourceString(SettingsActivity.this, R.string.text_city_name_e_g_istanbul));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(I18n.resourceString(SettingsActivity.this, R.string.text_choose_city))
                .setView(box)
                .setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_search_946f53), (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_searching));
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showCities(q, res));
                    }).start();
                })
                .setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    void showCities(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        showStatusMsg(null);
        if (res == null) {
            showStatusMsg(I18n.resourceString(SettingsActivity.this, R.string.text_couldn_t_search_check_the_internet_conne));
            return;
        }
        if (res.isEmpty()) {
            showStatusMsg(I18n.resourceString(this, R.string.message_no_place_found, q));
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  ·  " + pl.detail;
        }
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_matching_places)).setItems(names, (d, w) -> {
            Weather.Place picked = res.get(w);
            Weather.setPlace(this, picked);
            rebuildMainRows();
            focusFirstRow();
            showStatusMsg(I18n.resourceString(this, R.string.message_location_set, picked.name));
        }).setNegativeButton(I18n.resourceString(SettingsActivity.this, R.string.text_cancel), null).show();
    }

    void showPresentInfo() {
        if (!com.techgenius.tvoslauncher.present.Presenter.enabled(this)) {
            Toast.makeText(this, I18n.resourceString(SettingsActivity.this, R.string.text_turn_on_presentation_mode_first), Toast.LENGTH_SHORT).show();
            return;
        }
        com.techgenius.tvoslauncher.present.Presenter.ensureRunning(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = u(16);
        box.setPaddingRelative(pad, pad, pad, pad / 2);
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(com.techgenius.tvoslauncher.present.Presenter.qr(u(110)));
        qr.setBackgroundColor(Color.WHITE);
        qr.setPaddingRelative(u(6), u(6), u(6), u(6));
        box.addView(qr, new LinearLayout.LayoutParams(u(122), u(122)));
        TextView info = new TextView(this);
        String pin = com.techgenius.tvoslauncher.present.Presenter.pin(this);
        info.setText(I18n.resourceString(SettingsActivity.this, R.string.text_open_on_a_phone_or_computer_on_the_same)
                + com.techgenius.tvoslauncher.present.Presenter.address() + "\n\n"
                + I18n.resourceString(SettingsActivity.this, R.string.text_pairing_code_146fc7)
                + pin.substring(0, 3) + " " + pin.substring(3));
        info.setTextColor(Color.WHITE);
        info.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(info, 14);
        info.setPaddingRelative(u(18), 0, 0, 0);
        box.addView(info);
        dialog(I18n.resourceString(SettingsActivity.this, R.string.text_send_to_this_tv_535246)).setView(box).setPositiveButton(I18n.resourceString(SettingsActivity.this, R.string.text_done), null).show();
    }
}
