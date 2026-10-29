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
    private final Map<String, Bitmap> sceneThumbCache = new HashMap<>();
    private final Map<String, View> sideRowViews = new HashMap<>();
    private final Map<String, ImageView> sideIcons = new HashMap<>();
    private final Map<String, GradientDrawable> sideIconBgs = new HashMap<>();
    private final Map<String, TextView> sideLabels = new HashMap<>();
    private final Map<String, GradientDrawable> sidePillBgs = new HashMap<>();

    private String currentSection = LOOK_SCENES;
    private boolean inRows = false;
    private boolean rebuilding = false;
    private String liftedSceneId = null;
    private boolean showingHiddenApps = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        String token = getIntent().getStringExtra("tmdb_token");
        if (token != null && !token.trim().isEmpty()) {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, token.trim()).apply();
            Suggestions.changed(this);
            Toast.makeText(this, tr("TMDB connected", "TMDB bağlandı"), Toast.LENGTH_SHORT).show();
        }
        String pg = getIntent().getStringExtra("page");
        if (pg != null && !pg.isEmpty()) {
            if (INPUTS.equals(pg)) {
                currentSection = CONTROL_PANEL;
            } else if (LOCATION.equals(pg) || WEATHER.equals(pg)) {
                currentSection = WEATHER;
            } else if (SHELF.equals(pg) || PRESENT.equals(pg) || WATCHING.equals(pg) || HOME.equals(pg) || PERMS.equals(pg)) {
                currentSection = QUICK;
            } else {
                currentSection = pg;
            }
        }
        buildUi();
        selectSection(currentSection, false, 0);
    }

    private String tr(String en, String trStr) {
        return I18n.s(this, en, trStr);
    }

    private int u(float dp) {
        return Ui.px(this, dp);
    }

    private String sectionTitle(String sec) {
        switch (sec) {
            case LOOK_SCENES: return tr("Appearance and scenes", "Görünüm ve sahneler");
            case LOCATION:
            case WEATHER: return tr("Weather and location", "Hava durumu ve konum");
            case APPS: return tr("Apps", "Uygulamalar");
            case PROFILES: return tr("People", "Kişiler");
            case CONTROL_PANEL: return tr("Control Panel", "Denetim Paneli");
            case BUTTON_MAPPER: return tr("Button mapper", "Düğme eşleyici");
            case QUICK: return tr("Quick buttons", "Hızlı düğmeler");
            case LANGUAGE: return tr("Language", "Dil");
            case ADVANCED: return tr("Advanced", "Gelişmiş");
            case ABOUT: return tr("About", "Hakkında");
            default: return sec;
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A080C);

        // Blurred scene backdrop matching couch-tv's Home settings screen
        ImageView bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap blurredBg = buildSettingsBackdrop();
        if (blurredBg != null) {
            bgView.setImageDrawable(new BitmapDrawable(getResources(), blurredBg));
        }
        root.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Dark translucent scrim: rgba(10, 8, 12, 0.80)
        View scrim = new View(this);
        scrim.setBackgroundColor(0xCC0A080C);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Two-column grid: left sidebar (276dp) + gap (34dp) + right content (1f)
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setClipChildren(false);
        columns.setClipToPadding(false);
        columns.setPadding(u(44), u(28), u(48), u(22));
        root.addView(columns, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------------- Left Sidebar (.set-side) ----------------
        LinearLayout sideCol = new LinearLayout(this);
        sideCol.setOrientation(LinearLayout.VERTICAL);
        sideCol.setClipChildren(false);
        sideCol.setClipToPadding(false);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(u(288), ViewGroup.LayoutParams.MATCH_PARENT);
        sideLp.rightMargin = u(26);
        columns.addView(sideCol, sideLp);

        sideTitleView = new TextView(this);
        sideTitleView.setText(tr("Home settings", "Ana ekran ayarları"));
        sideTitleView.setTextColor(Color.WHITE);
        sideTitleView.setTypeface(Ui.font(this, "display"));
        sideTitleView.setLetterSpacing(-0.022f);
        Ui.textSize(sideTitleView, 24f);
        sideTitleView.setSingleLine(true);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.leftMargin = u(16);
        stLp.topMargin = u(10);
        stLp.bottomMargin = u(16);
        sideCol.addView(sideTitleView, stLp);

        sideList = new LinearLayout(this);
        sideList.setOrientation(LinearLayout.VERTICAL);
        sideList.setClipChildren(false);
        sideList.setClipToPadding(false);
        sideList.setPadding(u(8), u(4), u(8), u(8));
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
        mhLp.leftMargin = u(16);
        mhLp.topMargin = u(14);
        mhLp.bottomMargin = u(12);
        mainCol.addView(mainHeadView, mhLp);

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setVerticalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(u(24));
        scroller.setClipChildren(true);
        scroller.setClipToPadding(true);
        scroller.setPadding(0, 0, 0, 0);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(u(18), u(8), u(18), u(28));
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
        msgView.setPadding(u(14), u(6), u(14), u(6));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.leftMargin = u(18);
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
        hintView.setPadding(u(14), u(7), u(14), u(7));
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.leftMargin = u(18);
        hintLp.topMargin = u(8);
        mainCol.addView(hintView, hintLp);

        setContentView(root);
    }

    /** Creates the soft blurred scene + dock glow backdrop behind Home settings (matching home-settings.jpg). */
    private Bitmap buildSettingsBackdrop() {
        try {
            List<String> enabled = Scenes.getEnabledSceneIds(this);
            String sceneId = enabled.isEmpty() ? "night" : enabled.get(0);
            Scenes.Spec spec = Scenes.byId(sceneId);
            Bitmap src = Scenes.loadAssetBitmap(this, spec.bgAsset, 480, 270);
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
            return blurred;
        } catch (Exception e) {
            return null;
        }
    }

    private void buildSidebarRows() {
        sideList.removeAllViews();
        sideRowViews.clear();
        sideIcons.clear();
        sideIconBgs.clear();
        sideLabels.clear();
        sidePillBgs.clear();

        for (int i = 0; i < SIDEBAR_SECTIONS.length; i++) {
            final String secKey = SIDEBAR_SECTIONS[i][0];
            final String glyphName = SIDEBAR_SECTIONS[i][1];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(u(8), u(5), u(14), u(5));

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(u(12));
            pillBg.setColor(Color.TRANSPARENT);
            row.setBackground(pillBg);

            // Rounded icon container (.ssec i)
            FrameLayout iconBox = new FrameLayout(this);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(u(8));
            iconBg.setColor(0x1AFFFFFF);
            iconBox.setBackground(iconBg);

            int iconGlyphSz = u(15);
            ImageView iconIv = new ImageView(this);
            iconIv.setImageBitmap(Glyphs.get(glyphName, iconGlyphSz, Color.WHITE));
            iconBox.addView(iconIv, new FrameLayout.LayoutParams(iconGlyphSz, iconGlyphSz, Gravity.CENTER));

            LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(u(27), u(27));
            ibLp.rightMargin = u(11);
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
                    if (!secKey.equals(currentSection) || showingHiddenApps) {
                        showingHiddenApps = false;
                        currentSection = secKey;
                        rebuildMainRows();
                    }
                    updateSidebarStyles();
                    updateHintText(false);
                } else {
                    updateSidebarStyles();
                }
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                        .translationZ(has ? u(10) : 0f).setDuration(180).start();
            });

            row.setOnClickListener(v -> focusFirstRow());

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, u(35));
            rlp.bottomMargin = u(2);
            sideList.addView(row, rlp);

            sideRowViews.put(secKey, row);
            sideIcons.put(secKey, iconIv);
            sideIconBgs.put(secKey, iconBg);
            sideLabels.put(secKey, label);
            sidePillBgs.put(secKey, pillBg);
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
        sideTitleView.setText(tr("Home settings", "Ana ekran ayarları"));
        for (String[] pair : SIDEBAR_SECTIONS) {
            String key = pair[0];
            TextView label = sideLabels.get(key);
            if (label != null) label.setText(sectionTitle(key));
        }
    }

    private void selectSection(String sec, boolean focusRightPane, int rowIdx) {
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
        focusRowAtIndex(0);
    }

    private void focusRowAtIndex(int targetIdx) {
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
            hintView.setText(tr("Up and down to move, OK when done",
                    "Taşımak için Yukarı ve Aşağı, bitince OK"));
        } else if (!inRows) {
            hintView.setText(tr("Up and down to browse, OK to change, Back to close",
                    "Gezinmek için Yukarı ve Aşağı, değiştirmek için OK, kapatmak için Geri"));
        } else if (APPS.equals(currentSection) && showingHiddenApps) {
            hintView.setText(tr("OK to open  ·  Hold OK to restore  ·  Back to go back",
                    "Açmak için OK  ·  Geri yüklemek için OK'e basılı tutun  ·  Dönmek için Geri"));
        } else if (movableRow) {
            hintView.setText(tr("OK to change, hold OK to move, Back to go back",
                    "Değiştirmek için OK, taşımak için OK'e basılı tutun, dönmek için Geri"));
        } else {
            hintView.setText(tr("OK to change, Back to go back",
                    "Değiştirmek için OK, dönmek için Geri"));
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
                case ABOUT:
                default:
                    pageAbout();
                    break;
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
        updateHintText(LOOK_SCENES.equals(currentSection));
    }

    // ================================================================ Section Pages

    void pageLanguage() {
        String cur = I18n.getLang(this);
        for (int i = 0; i < I18n.LANGUAGES.length; i++) {
            final int idx = i;
            final String code = I18n.LANGUAGES[i][0];
            final String title = I18n.LANGUAGES[i][1];
            final String sub = I18n.LANGUAGES[i][2];
            choiceRow(
                    title,
                    sub,
                    code.equals(cur),
                    () -> {
                        I18n.setLang(this, code);
                        updateSidebarTranslations();
                        selectSection(LANGUAGE, true, idx);
                    });
        }
        infoNote(tr("Changes the words on the home screen, date, weather and settings.",
                "Ana ekrandaki, tarihteki, hava durumundaki ve ayarlardaki dili değiştirir."));
    }

    void pageWeather() {
        section(tr("WEATHER", "HAVA DURUMU"));
        row(tr("Weather on the home screen", "Ana ekranda hava durumu"),
                () -> Weather.enabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> {
                    Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, !Weather.enabled(this)).apply();
                    Weather.changed(this);
                });
        row(tr("Temperature", "Sıcaklık birimi"), () -> {
            String u = Weather.prefs(this).getString(Weather.K_UNITS, "c");
            return u.equals("f") ? "°F (Fahrenheit)" : "°C (Celsius)";
        }, () -> {
            String cur = Weather.prefs(this).getString(Weather.K_UNITS, "c");
            String next = "f".equals(cur) ? "c" : "f";
            Weather.prefs(this).edit().putString(Weather.K_UNITS, next).apply();
            Weather.changed(this);
        });
        row(tr("Update every", "Güncelleme sıklığı"),
                () -> Weather.refreshMinutes(this) + tr(" minutes", " dakika"),
                () -> {
                    int cur = Weather.refreshMinutes(this);
                    int next = cur == 15 ? 30 : cur == 30 ? 60 : 15;
                    Weather.prefs(this).edit().putInt(Weather.K_REFRESH, next).apply();
                });
        row(tr("Weather source", "Hava durumu kaynağı"),
                () -> Weather.sourceName(Weather.source(this)),
                () -> {
                    String[] keys = Weather.SOURCES;
                    String[] names = {"Open-Meteo", "MET Norway (Yr)", "wttr.in  (3 days)"};
                    int sel = Arrays.asList(keys).indexOf(Weather.source(this));
                    dialog(tr("Weather source", "Hava durumu kaynağı")).setSingleChoiceItems(names, Math.max(0, sel), (d, w) -> {
                        Weather.prefs(this).edit().putString(Weather.K_SOURCE, keys[w]).apply();
                        Weather.changed(this);
                        refreshAll();
                        d.dismiss();
                    }).show();
                });
        infoRow(tr("Weather from", "Hava durumu sağlayıcısı"),
                "Open-Meteo",
                tr("Free forecast service, no account needed", "Ücretsiz hava tahmini servisi, hesap gerekmez"));

        section(tr("LOCATION", "KONUM"));
        String city = Weather.place(this);
        boolean isManual = Weather.manual(this);
        infoRow(tr("Current place", "Geçerli konum"),
                city.isEmpty() ? tr("Automatic (from internet)", "Otomatik (internetten)") : city,
                isManual ? tr("Set manually", "Elle ayarlandı") : tr("Detected automatically", "Otomatik algılandı"));

        row(tr("Search for a city or town…", "Şehir veya ilçe ara…"),
                () -> isManual ? city : "›",
                this::searchCity);

        if (isManual) {
            row(tr("Use automatic location", "Otomatik konumu kullan"), () -> "", () -> {
                Weather.setPlace(this, null);
                Weather.refresh(this, true, this::rebuildMainRows);
                rebuildMainRows();
                focusRowAtIndex(4);
            });
        }

        infoNote(tr("Used for the weather on the home screen and local headlines.",
                "Ana ekrandaki hava durumu ve yerel haberler için kullanılır."));
    }

    /**
     * Exact Couch-TV "Look and scenes" layout (matching docs/screens/home-settings.jpg):
     * - LOOK: Night scenes vs Classic
     * - SCENES: Night, Golden hour, Waterline, Fog, Rainforest dawn (with 16:9 thumbnails & green circular checkmarks)
     * - Each scene stays: 20 seconds
     * - Clock: 12 hour / 24 hour
     * - CLASSIC BACKGROUND: Couch-TV photos & ambient themes
     */
    void pageLookAndScenes() {
        section(tr("LOOK", "GÖRÜNÜM"));
        boolean scenesLook = Scenes.isScenesLook(this);
        choiceRow(
                tr("Night scenes", "Gece sahneleri"),
                tr("A big glass clock, scenes taking turns", "Büyük cam saat, sırayla değişen sahneler"),
                scenesLook,
                () -> {
                    Scenes.setLook(this, Scenes.LOOK_SCENES);
                    selectSection(LOOK_SCENES, true, 0);
                });
        choiceRow(
                tr("Classic", "Klasik"),
                tr("One photo and a grid of app banners", "Tek fotoğraf ve uygulama afişleri ızgarası"),
                !scenesLook,
                () -> {
                    Scenes.setLook(this, Scenes.LOOK_CLASSIC);
                    selectSection(LOOK_SCENES, true, 1);
                });

        section(tr("SCENES", "SAHNELER"));
        List<String> order = Scenes.getSceneOrder(this);
        for (int i = 0; i < order.size(); i++) {
            final int sceneIdx = i;
            Scenes.Spec spec = Scenes.byId(order.get(i));
            sceneCheckRow(spec, sceneIdx, order);
        }

        row(tr("Each scene stays", "Her sahne süresi"),
                () -> Scenes.getStaySeconds(this) + tr(" seconds", " saniye"),
                () -> {
                    Scenes.cycleStaySeconds(this);
                    refreshAll();
                });

        row(tr("Clock", "Saat"),
                () -> Scenes.isClock24h(this) ? tr("24 hour", "24 saat") : tr("12 hour", "12 saat"),
                () -> {
                    Scenes.setClock24h(this, !Scenes.isClock24h(this));
                    refreshAll();
                });

        row(tr("Clock font", "Saat yazı tipi"),
                () -> Scenes.clockStyleLabel(this),
                () -> {
                    Scenes.cycleClockStyle(this);
                    refreshAll();
                });

        section(tr("CLASSIC BACKGROUND", "KLASİK ARKA PLAN"));
        String curWall = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
        for (int i = 0; i < Scenes.CLASSIC_PHOTOS.length; i++) {
            final String photoFile = Scenes.CLASSIC_PHOTOS[i][0];
            final String wallKey = "photo:" + photoFile;
            final String label = I18n.s(this, Scenes.CLASSIC_PHOTOS[i][1], Scenes.CLASSIC_PHOTOS[i][2]);
            final int focusRowIdx = 2 + order.size() + 3 + i;
            classicPhotoRow(label, "backgrounds/" + photoFile, wallKey.equals(curWall), () -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", wallKey).apply();
                selectSection(LOOK_SCENES, true, focusRowIdx);
            });
        }
        row(tr("Ambient colour themes…", "Ortam renk temaları…"), () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
            int idx = Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.wallName(this, Math.max(0, idx));
        }, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "photo:tessellated-pavement.jpg");
            int cur = Math.max(0, Arrays.asList(MainActivity.WALL_KEYS).indexOf(k));
            String[] names = new String[MainActivity.WALL_KEYS.length];
            for (int i = 0; i < names.length; i++) names[i] = MainActivity.wallName(this, i);
            dialog(tr("Classic background", "Klasik arka plan")).setSingleChoiceItems(names, cur, (d, w) -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.WALL_KEYS[w]).apply();
                d.dismiss();
                rebuildMainRows();
            }).show();
        });
    }

    void pageApps() {
        if (showingHiddenApps) {
            pageHiddenApps();
            return;
        }
        row(tr("Hidden apps", "Gizlenen uygulamalar"),
                () -> {
                    int count = listHiddenAppItems().size();
                    return (count > 0 ? count + "  " : "") + "›";
                },
                () -> {
                    showingHiddenApps = true;
                    rebuildMainRows();
                    focusFirstRow();
                });
        row(tr("Reset app order & unhide all", "Uygulama sırasını sıfırla ve gizlenenleri göster"),
                () -> "",
                () -> {
                    Profiles.homePrefs(this).edit()
                            .remove(MainActivity.PREF_ORDER)
                            .remove(MainActivity.PREF_OTHER)
                            .remove(MainActivity.PREF_HIDDEN)
                            .apply();
                    showStatusMsg(tr("App layout reset to default.", "Uygulama düzeni varsayılana sıfırlandı."));
                });
        row(tr("Show Continue Watching on Home", "Ana ekranda İzlemeye Devam Et'i göster"),
                () -> Profiles.homePrefs(this).getBoolean("cw_enabled", false) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> {
                    boolean on = !Profiles.homePrefs(this).getBoolean("cw_enabled", false);
                    Profiles.homePrefs(this).edit().putBoolean("cw_enabled", on).apply();
                });
        row(tr("Classic Top Shelf suggestions", "Klasik Üst Raf önerileri"), () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return tr("Recommendations", "Öneriler");
                case Suggestions.SHELF_NEWS: return tr("News", "Haberler");
                case Suggestions.SHELF_NONE: return tr("Off", "Kapalı");
                default: return tr("Recommendations & News", "Öneriler ve Haberler");
            }
        }, () -> {
            String[] keys = {Suggestions.SHELF_NONE, Suggestions.SHELF_BOTH, Suggestions.SHELF_RECS, Suggestions.SHELF_NEWS};
            String[] names = {
                tr("Off", "Kapalı"),
                tr("Recommendations & News", "Öneriler ve Haberler"),
                tr("Recommendations", "Öneriler"),
                tr("News", "Haberler")
            };
            int sel = Math.max(0, Arrays.asList(keys).indexOf(Suggestions.shelf(this)));
            dialog(tr("Classic Top Shelf", "Klasik Üst Raf")).setSingleChoiceItems(names, sel, (d, w) -> {
                Suggestions.setShelf(this, keys[w]);
                d.dismiss();
                refreshAll();
            }).show();
        });
        row(tr("Clear Continue Watching history", "İzlemeye Devam Et geçmişini temizle"),
                () -> "",
                () -> {
                    MediaWatchService.Store.clear(this);
                    showStatusMsg(tr("Continue Watching cleared.", "İzlemeye Devam Et geçmişi temizlendi."));
                });
        row(tr("Manage system apps in TV settings…", "TV ayarlarında uygulamaları yönet…"),
                () -> "›",
                () -> Suggestions.start(this, new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        infoNote(tr("Tip: Hold OK on any app on the home screen dock to move or hide it.",
                "İpucu: Ana ekran dock'undaki herhangi bir uygulamayı taşımak veya gizlemek için OK tuşuna basılı tutun."));
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
                it.label = MainActivity.getCustomAppName(this, it.key, null, tr("Inputs", "Girişler"));
                it.icon = new BitmapDrawable(getResources(), Glyphs.get("input", u(22), Color.WHITE));
                out.add(it);
            }
            if (hidden.contains(MainActivity.PRESENT_KEY)) {
                HiddenAppItem it = new HiddenAppItem();
                it.key = MainActivity.PRESENT_KEY;
                it.pkg = getPackageName();
                it.label = MainActivity.getCustomAppName(this, it.key, null, tr("Presentation", "Sunum"));
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
        mainHeadView.setText(tr("Hidden apps", "Gizlenen uygulamalar"));
        List<HiddenAppItem> hiddenList = listHiddenAppItems();
        if (hiddenList.isEmpty()) {
            row("←  " + tr("Back to Apps", "Uygulamalara dön"), () -> "", () -> {
                showingHiddenApps = false;
                rebuildMainRows();
                focusFirstRow();
            });
            infoNote(tr("No hidden apps. Hold OK on any app on the home screen dock and choose Hide.",
                    "Gizlenen uygulama yok. Ana ekran dock'undaki herhangi bir uygulamada OK tuşuna basılı tutup Gizle'yi seçin."));
            return;
        }
        for (int i = 0; i < hiddenList.size(); i++) {
            final HiddenAppItem item = hiddenList.get(i);
            hiddenAppRow(item);
        }
        infoNote(tr("OK to open  ·  Hold OK to restore",
                "Açmak için OK  ·  Geri yüklemek için OK'e basılı tutun"));
    }

    private void hiddenAppRow(HiddenAppItem item) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setLongClickable(true);
        r.setPadding(u(14), u(6), u(16), u(6));

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
        icLp.rightMargin = u(12);
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
        v.setText(tr("Open", "Aç"));
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
            view.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .translationZ(has ? u(8) : 0f).setDuration(180).start();
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
            showStatusMsg(tr(item.label + " restored to Home Screen.", item.label + " Ana Ekran'a geri yüklendi."));
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(42));
        lp.bottomMargin = u(5);
        list.addView(r, lp);
    }

    void pageProfiles() {
        section(tr("PEOPLE", "KİŞİLER"));
        List<Profiles.Profile> ps = Profiles.list(this);
        String curId = Profiles.current(this).id;
        for (int i = 0; i < ps.size(); i++) {
            final Profiles.Profile p = ps.get(i);
            final int idx = i;
            boolean isActive = p.id.equals(curId);
            choiceRow(
                    I18n.profileName(this, p.name),
                    isActive ? tr("Watching now · OK to switch or rename", "Şu an izliyor · Değiştirmek veya yeniden adlandırmak için OK")
                             : tr("OK to switch or edit", "Geçiş yapmak veya düzenlemek için OK"),
                    isActive,
                    () -> showProfileActionsDialog(p, idx));
        }
        if (Profiles.canAdd(this)) {
            row(tr("Add a person…", "Kişi ekle…"), () -> "›", this::promptAddPerson);
        }
        section(tr("OPTIONS", "SEÇENEKLER"));
        row(tr("Ask who is watching on wake", "TV açıldığında kimin izlediğini sor"),
                () -> Profiles.askOnWake(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> Profiles.setAskOnWake(this, !Profiles.askOnWake(this)));
        infoNote(tr("Each person keeps their own Keep Watching row and app order.",
                "Her kişi kendi İzlemeye Devam Et satırını ve uygulama düzenini korur."));
    }

    private void showProfileActionsDialog(Profiles.Profile p, int rowIdx) {
        List<String> opts = new ArrayList<>();
        opts.add(tr("Switch to " + I18n.profileName(this, p.name), I18n.profileName(this, p.name) + " profiline geç"));
        opts.add(tr("Rename…", "Yeniden adlandır…"));
        if (Profiles.list(this).size() > 1) {
            opts.add(tr("Remove person", "Kişiyi kaldır"));
        }
        dialog(I18n.profileName(this, p.name)).setItems(opts.toArray(new String[0]), (d, w) -> {
            if (w == 0) {
                Profiles.setCurrent(this, p.id);
                selectSection(PROFILES, true, rowIdx);
            } else if (w == 1) {
                promptRenamePerson(p, rowIdx);
            } else if (w == 2) {
                Profiles.delete(this, p.id);
                selectSection(PROFILES, true, 0);
            }
        }).show();
    }

    private void promptRenamePerson(Profiles.Profile p, int rowIdx) {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setText(I18n.profileName(this, p.name));
        in.setSelection(in.getText().length());
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(tr("Rename person", "Kişiyi yeniden adlandır"))
                .setView(box)
                .setPositiveButton(tr("Save", "Kaydet"), (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) {
                        p.name = n;
                        Profiles.update(this, p);
                        selectSection(PROFILES, true, rowIdx);
                    }
                })
                .setNegativeButton(tr("Cancel", "İptal"), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    private void promptAddPerson() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(tr("Name", "Ad"));
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(tr("Add a person", "Kişi ekle"))
                .setView(box)
                .setPositiveButton(tr("Add", "Ekle"), (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) {
                        Profiles.add(this, n);
                        selectSection(PROFILES, true, Profiles.list(this).size() - 1);
                    }
                })
                .setNegativeButton(tr("Cancel", "İptal"), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    void pageControlPanel() {
        section(tr("UNIVERSAL SHORTCUT ACTIONS", "EVRENSEL KISAYOL EYLEMLERİ"));
        row(tr("Wi-Fi", "Wi-Fi"),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_WIFI),
                () -> showControlPanelAppChooser(
                        ControlPanelOverlay.ACTION_WIFI,
                        tr("Wi-Fi", "Wi-Fi")));
        row(tr("Bluetooth", "Bluetooth"),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_BLUETOOTH),
                () -> showControlPanelAppChooser(
                        ControlPanelOverlay.ACTION_BLUETOOTH,
                        tr("Bluetooth", "Bluetooth")));
        row(tr("Sound", "Ses"),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_SOUND),
                () -> showControlPanelAppChooser(
                        ControlPanelOverlay.ACTION_SOUND,
                        tr("Sound", "Ses")));
        row(tr("Inputs (Control Panel & Dock)", "Girişler (Denetim Paneli ve Dock)"),
                () -> ControlPanelOverlay.getActionSummary(this, ControlPanelOverlay.ACTION_INPUTS),
                () -> showControlPanelAppChooser(
                        ControlPanelOverlay.ACTION_INPUTS,
                        tr("Inputs", "Girişler")));

        section(tr("INPUTS APP ON DOCK", "DOCK ÜZERİNDEKİ GİRİŞLER UYGULAMASI"));
        row(tr("Show Inputs app on Dock", "Dock'ta Girişler uygulamasını göster"),
                () -> Inputs.showCard(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> {
                    boolean next = !Inputs.showCard(this);
                    Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, next).apply();
                    refreshAll();
                });

        section(tr("RESET", "SIFIRLAMA"));
        row(tr("Reset Control Panel actions to default", "Denetim Paneli eylemlerini varsayılana sıfırla"),
                () -> "",
                () -> {
                    ControlPanelOverlay.clearAllCustomActions(this);
                    refreshAll();
                    showStatusMsg(tr("Control Panel actions reset to Automatic.",
                            "Denetim Paneli eylemleri Otomatik olarak sıfırlandı."));
                });

        infoNote(tr("Each TV brand uses different package and activity names for Wi-Fi, Bluetooth, Sound, and Inputs. Choose an app and then pick the exact activity to launch from the Control Panel (and from the Inputs app on the dock).",
                "Her TV markası Wi-Fi, Bluetooth, Ses ve Girişler için farklı paket ve etkinlik adları kullanır. Bir uygulama seçin ve ardından Denetim Panelinden (ve dock'taki Girişler uygulamasından) başlatılacak etkinliği belirleyin."));
    }

    private void showControlPanelAppChooser(String actionKey, String actionTitle) {
        PackageManager pm = getPackageManager();
        List<PackageInfo> rawPkgs;
        try {
            rawPkgs = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        } catch (Throwable t) {
            rawPkgs = Collections.emptyList();
        }

        class AppEntry {
            final String pkg;
            final String label;
            final int actCount;

            AppEntry(String pkg, String label, int actCount) {
                this.pkg = pkg;
                this.label = label;
                this.actCount = actCount;
            }
        }

        List<AppEntry> entries = new ArrayList<>();
        for (PackageInfo pi : rawPkgs) {
            if (pi == null || pi.packageName == null) continue;
            int count = (pi.activities != null) ? pi.activities.length : 0;
            if (count == 0) {
                // Fallback check via queryIntentActivities in case GET_ACTIVITIES truncated
                Intent q = new Intent().setPackage(pi.packageName);
                List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
                count = (ris != null) ? ris.size() : 0;
            }
            if (count == 0) continue;
            String label = pi.packageName;
            if (pi.applicationInfo != null) {
                CharSequence cs = pi.applicationInfo.loadLabel(pm);
                if (cs != null && cs.length() > 0) {
                    label = cs.toString().trim();
                }
            }
            entries.add(new AppEntry(pi.packageName, label, count));
        }

        Collections.sort(entries, (a, b) -> {
            int cmp = a.label.compareToIgnoreCase(b.label);
            return cmp != 0 ? cmp : a.pkg.compareToIgnoreCase(b.pkg);
        });

        String curPkg = ControlPanelOverlay.getCustomPkg(this, actionKey);
        String[] labels = new String[entries.size() + 1];
        labels[0] = "↺  " + tr("Default (Automatic)", "Varsayılan (Otomatik)");
        int checkedIdx = 0;
        for (int i = 0; i < entries.size(); i++) {
            AppEntry e = entries.get(i);
            labels[i + 1] = e.label + "  (" + e.pkg + ")";
            if (!curPkg.isEmpty() && curPkg.equals(e.pkg)) {
                checkedIdx = i + 1;
            }
        }

        dialog(actionTitle + " — " + tr("Choose App", "Uygulama Seçin"))
                .setSingleChoiceItems(labels, checkedIdx, (d, which) -> {
                    d.dismiss();
                    if (which == 0) {
                        ControlPanelOverlay.clearCustomAction(this, actionKey);
                        refreshAll();
                        showStatusMsg(actionTitle + ": " + tr("Default (Automatic)", "Varsayılan (Otomatik)"));
                    } else {
                        AppEntry chosen = entries.get(which - 1);
                        showControlPanelActivityChooser(actionKey, actionTitle, chosen.pkg, chosen.label);
                    }
                })
                .setNegativeButton(tr("Cancel", "İptal"), null)
                .show();
    }

    private void showControlPanelActivityChooser(String actionKey, String actionTitle, String pkg, String appLabel) {
        PackageManager pm = getPackageManager();
        List<ActivityInfo> actList = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi != null && pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    if (ai != null && ai.name != null && seenNames.add(ai.name)) {
                        actList.add(ai);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Intent q = new Intent().setPackage(pkg);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            if (ris != null) {
                for (ResolveInfo ri : ris) {
                    if (ri.activityInfo != null && ri.activityInfo.name != null
                            && seenNames.add(ri.activityInfo.name)) {
                        actList.add(ri.activityInfo);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        String curPkg = ControlPanelOverlay.getCustomPkg(this, actionKey);
        String curCls = ControlPanelOverlay.getCustomCls(this, actionKey);

        final List<String> clsValues = new ArrayList<>();
        final List<String> summaryValues = new ArrayList<>();
        final List<String> displayLabels = new ArrayList<>();

        // Option 0: Launch app's default main activity
        clsValues.add("");
        summaryValues.add(appLabel);
        displayLabels.add("★  " + tr("Default App Launch (Main Activity)", "Uygulamayı Normal Başlat (Ana Etkinlik)")
                + "\n    " + pkg);

        int checkedIdx = 0;
        for (int i = 0; i < actList.size(); i++) {
            ActivityInfo ai = actList.get(i);
            String fullCls = ai.name;
            String shortCls = fullCls.startsWith(pkg + ".")
                    ? fullCls.substring(pkg.length())
                    : fullCls;
            CharSequence actCs = ai.loadLabel(pm);
            String actTitle = (actCs != null && actCs.length() > 0) ? actCs.toString().trim() : "";
            String rowText;
            if (!actTitle.isEmpty() && !actTitle.equalsIgnoreCase(appLabel) && !actTitle.equals(fullCls)) {
                rowText = actTitle + "  (" + shortCls + ")";
            } else {
                rowText = shortCls + "  (" + fullCls + ")";
            }
            clsValues.add(fullCls);
            summaryValues.add(appLabel + " · " + shortCls);
            displayLabels.add(rowText);
            if (pkg.equals(curPkg) && fullCls.equals(curCls)) {
                checkedIdx = i + 1;
            }
        }

        dialog(appLabel + " — " + tr("Choose Activity", "Etkinlik Seçin"))
                .setSingleChoiceItems(displayLabels.toArray(new String[0]), checkedIdx, (d, which) -> {
                    d.dismiss();
                    String chosenCls = clsValues.get(which);
                    String summary = summaryValues.get(which);
                    ControlPanelOverlay.setCustomAction(this, actionKey, pkg, chosenCls, summary);
                    if (ControlPanelOverlay.ACTION_INPUTS.equals(actionKey)) {
                        // Ensure the Inputs app on the dock is visible when user configures Inputs
                        Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, true).apply();
                    }
                    refreshAll();
                    showStatusMsg(actionTitle + " → " + summary);
                })
                .setNeutralButton(tr("← Back to Apps", "← Uygulamalara Dön"), (d, w) -> {
                    showControlPanelAppChooser(actionKey, actionTitle);
                })
                .setNegativeButton(tr("Cancel", "İptal"), null)
                .show();
    }

    void pageButtonMapper() {
        section(tr("ACCESSIBILITY SERVICE", "ERİŞİLEBİLİRLİK SERVİSİ"));
        row(tr("Button Mapper accessibility service", "Düğme Eşleyici erişilebilirlik servisi"),
                () -> ButtonMapper.isServiceEnabled(this)
                        ? tr("Active (works in all apps)", "Etkin (tüm uygulamalarda çalışır)")
                        : tr("Enable in Accessibility…", "Erişilebilirlik'te etkinleştir…"),
                () -> {
                    Intent acc = Perms.accessibilityScreen(this);
                    if (acc != null && Suggestions.start(this, acc)) {
                        return;
                    }
                    dialog(tr("Accessibility Service", "Erişilebilirlik Servisi"))
                            .setMessage(tr(
                                    "This TV does not have a built-in Accessibility Settings screen. You can enable the Button Mapper & Control Panel service automatically via Local ADB.",
                                    "Bu TV'de yerleşik bir Erişilebilirlik Ayarları ekranı bulunmuyor. Düğme Eşleyici ve Denetim Paneli servisini Yerel ADB üzerinden otomatik olarak etkinleştirebilirsiniz."))
                            .setPositiveButton(tr("Enable via ADB", "ADB ile Etkinleştir"), (d, w) -> pairOrRepairAdb())
                            .setNeutralButton(tr("TV Settings", "TV Ayarları"), (d, w) -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)))
                            .setNegativeButton(tr("Cancel", "İptal"), null)
                            .show();
                });
        row(tr("Long-press volume button fix", "Uzun basış ses tuşu düzeltmesi"),
                () -> ButtonMapper.isVolumeHoldFixEnabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> {
                    boolean next = !ButtonMapper.isVolumeHoldFixEnabled(this);
                    ButtonMapper.setVolumeHoldFixEnabled(this, next);
                    refreshAll();
                    showStatusMsg(next
                            ? tr("Long-press volume fix enabled (for TVs where holding volume stops repeating).",
                                 "Uzun basış ses tuşu düzeltmesi etkinleştirildi.")
                            : tr("Long-press volume fix disabled (using native TV volume).",
                                 "Uzun basış ses tuşu düzeltmesi kapatıldı (varsayılan TV sesi kullanılıyor)."));
                });

        section(tr("BUTTON REMAPPING", "DÜĞME YENİDEN EŞLEME"));
        row(tr("Add new button to remap", "Yeniden eşlemek için yeni düğme ekle"),
                () -> "＋",
                () -> ButtonMapper.showCaptureAndConfigureDialog(this, this::rebuildMainRows));

        List<Integer> mappedKeys = ButtonMapper.getMappedKeys(this);
        if (!mappedKeys.isEmpty()) {
            section(tr("REMAPPED BUTTONS (1X / 2X / 3X TAP)", "EŞLENMİŞ DÜĞMELER (1X / 2X / 3X BASIŞ)"));
            for (final int keyCode : mappedKeys) {
                twoLineRow(ButtonMapper.keyLabel(this, keyCode),
                        () -> ButtonMapper.keySummaryLabel(this, keyCode),
                        () -> ButtonMapper.showConfigureKeyDialog(this, keyCode, this::rebuildMainRows));
            }
            row(tr("Clear all remapped buttons", "Tüm eşlenmiş düğmeleri temizle"),
                    () -> "",
                    () -> {
                        ButtonMapper.clearAll(this);
                        rebuildMainRows();
                        showStatusMsg(tr("All button mappings cleared.", "Tüm düğme eşlemeleri temizlendi."));
                    });
        }

        infoNote(tr("Assign Single Tap (1x), Double Tap (2x) and Triple Tap (3x) actions for each remote button — such as opening the Control Panel overlay, the Recent Apps Switcher overlay, or any installed app.",
                "Her kumanda düğmesi için Tek Basış (1x), Çift Basış (2x) ve Üçlü Basış (3x) eylemleri atayın — örneğin Denetim Paneli katmanını, Son Uygulamalar Değiştiricisi katmanını veya yüklü herhangi bir uygulamayı açın."));
    }

    void pageQuickAndHome() {
        section(tr("HOME & PERMISSIONS", "ANA EKRAN VE İZİNLER"));
        row(tr("Default home screen", "Varsayılan ana ekran"),
                () -> Perms.isDefaultHome(this) ? tr("Active", "Etkin") : tr("Set as default…", "Varsayılan yap…"),
                () -> Perms.requestDefaultHome(this));
        row(tr("Power Off & Sleep Timer permission", "Kapatma ve Uyku Zamanlayıcısı izni"),
                () -> Perms.hasAdmin(this) ? tr("Allowed", "İzin verildi")
                        : Perms.adminSupported(this) ? tr("Allow…", "İzin ver…") : tr("Not on this TV", "Bu TV'de yok"),
                () -> Perms.requestAdmin(this));
        row(tr("Continue Watching & DND permission", "İzlemeye Devam Et ve Rahatsız Etmeyin izni"),
                () -> Perms.hasListener(this) ? tr("Allowed", "İzin verildi")
                        : Perms.listenerSupported(this) ? tr("Allow…", "İzin ver…") : tr("Not on this TV", "Bu TV'de yok"),
                () -> Perms.requestListener(this));
        row(tr("Navigation click sounds", "Gezinme tıklama sesleri"),
                () -> Clicks.enabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> Clicks.setEnabled(this, !Clicks.enabled(this)));

        section(tr("PRESENTATION &CLASSIC SHELF", "SUNUM VE KLASİK ÜST RAF"));
        row(tr("Presentation mode", "Sunum modu"),
                () -> com.techgenius.tvoslauncher.present.Presenter.enabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> {
                    boolean on = !com.techgenius.tvoslauncher.present.Presenter.enabled(this);
                    com.techgenius.tvoslauncher.present.Presenter.set(this, com.techgenius.tvoslauncher.present.Presenter.KEY_ENABLED, on);
                });
        row(tr("Presentation address & QR code", "Sunum adresi ve QR kodu"),
                () -> com.techgenius.tvoslauncher.present.Presenter.enabled(this)
                        ? com.techgenius.tvoslauncher.present.Presenter.address().replace("http://", "")
                        : tr("Turn on first", "Önce etkinleştirin"),
                this::showPresentInfo);
        row(tr("Classic Top Shelf content", "Klasik Üst Raf içeriği"), () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return tr("Recommendations", "Öneriler");
                case Suggestions.SHELF_NEWS: return tr("News", "Haberler");
                case Suggestions.SHELF_NONE: return tr("Off", "Kapalı");
                default: return tr("Recommendations & News", "Öneriler ve Haberler");
            }
        }, () -> {
            String[] keys = {Suggestions.SHELF_BOTH, Suggestions.SHELF_RECS, Suggestions.SHELF_NEWS, Suggestions.SHELF_NONE};
            String[] names = {
                tr("Recommendations & News", "Öneriler ve Haberler"),
                tr("Recommendations", "Öneriler"),
                tr("News", "Haberler"),
                tr("Off", "Kapalı")
            };
            int sel = Math.max(0, Arrays.asList(keys).indexOf(Suggestions.shelf(this)));
            dialog(tr("Classic Top Shelf", "Klasik Üst Raf")).setSingleChoiceItems(names, sel, (d, w) -> {
                Suggestions.setShelf(this, keys[w]);
                d.dismiss();
                refreshAll();
            }).show();
        });
        row(tr("Run first-time setup again…", "İlk kurulumu tekrar çalıştır…"),
                () -> "›",
                () -> {
                    getSharedPreferences("setup", MODE_PRIVATE).edit().putInt("cur_step", 0).apply();
                    startActivity(new Intent(this, SetupActivity.class).putExtra("step", 0));
                });
        row(tr("Open Android TV Settings…", "Android TV Ayarlarını Aç…"),
                () -> "›",
                () -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)));
    }

    void pageAdvanced() {
        section(tr("ADB & PERMISSIONS", "ADB VE İZİNLER"));
        row(tr("ADB connection status", "ADB bağlantı durumu"),
                () -> LocalAdb.getStatusLabel(this),
                this::pairOrRepairAdb);
        row(tr("Pair / Repair ADB connection", "ADB bağlantısını eşleştir / onar"),
                () -> LocalAdb.isPaired(this) ? tr("Repair", "Onar") : tr("Pair now", "Şimdi eşleştir"),
                this::pairOrRepairAdb);
        row(tr("Check permissions", "İzinleri kontrol et"),
                this::permissionsSummaryCount,
                this::showCheckPermissionsDialog);

        section(tr("KEEP ALIVE", "KEEP ALIVE (OTOMATİK ONARIM)"));
        row(tr("Keep Alive (Auto-repair permissions)", "Keep Alive (İzinleri otomatik onar)"),
                () -> LocalAdb.isKeepAliveEnabled(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> {
                    boolean next = !LocalAdb.isKeepAliveEnabled(this);
                    LocalAdb.setKeepAliveEnabled(this, next);
                    if (next) {
                        LocalAdb.checkAndRepairPermissionsIfNeeded(this, true);
                    }
                    refreshAll();
                    showStatusMsg(next
                            ? tr("Keep Alive enabled — permissions will be monitored and restored automatically via ADB.",
                                 "Keep Alive etkinleştirildi — izinler izlenecek ve ADB ile otomatik olarak onarılacak.")
                            : tr("Keep Alive disabled.", "Keep Alive devre dışı bırakıldı."));
                });

        infoNote(tr("Some TVs disable Accessibility or other permissions automatically in the background. When Keep Alive is on, tvOS Launcher lightly checks permission status in the background without extra RAM or CPU load and automatically restores any disabled permissions via ADB.",
                "Bazı TV'ler Erişilebilirlik veya diğer izinleri arka planda kendiliğinden kapatabilir. Keep Alive açıkken tvOS Launcher, ekstra RAM veya işlemci yükü oluşturmadan izin durumunu arka planda hafifçe kontrol eder ve kapanan izinleri ADB üzerinden otomatik olarak yeniden açar."));

        section(tr("CLEANYOURTV (BETA)", "CLEANYOURTV (BETA)"));
        twoLineRow(tr("Choose TV model", "TV modelini seçin"),
                () -> CleanYourTv.getSelectedModelSummary(this),
                () -> CleanYourTv.showChooseModelFlow(this, this::refreshAll));
        row(tr("Debloat status", "Debloat durumu"),
                () -> CleanYourTv.getDebloatStatusSummary(this),
                () -> CleanYourTv.showSavedDisabledAppsDialog(this, this::refreshAll));
        row(tr("Debloat TV…", "TV Debloat Başlat…"),
                () -> tr("Start ›", "Başlat ›"),
                () -> CleanYourTv.startDebloatFlow(this, this::refreshAll));
        row(tr("Disabled apps backup", "Devre dışı bırakılan uygulamalar kaydı"),
                () -> CleanYourTv.getSavedDisabledSummary(this),
                () -> CleanYourTv.showSavedDisabledAppsDialog(this, this::refreshAll));
        row(tr("Restore debloated apps…", "Debloat edilen uygulamaları geri yükle…"),
                () -> tr("Restore ›", "Geri Yükle ›"),
                () -> CleanYourTv.startRestoreFlow(this, this::refreshAll));
        row(tr("Ad-blocking Private DNS (AdGuard)", "Reklam engelleyici Özel DNS (AdGuard)"),
                () -> CleanYourTv.isAdguardDnsActive(this) ? tr("On", "Açık") : tr("Off", "Kapalı"),
                () -> CleanYourTv.toggleAdguardDns(this, this::refreshAll));

        infoNote(tr("CleanYourTV (Beta) runs the CleanYourTV debloat profiles directly on your TV over Local ADB without needing a PC. Choose your TV model (or scan by installed system app packages), pick which bloatware and telemetry apps to disable, and restore them anytime with one tap.",
                "CleanYourTV (Beta), bilgisayara ihtiyaç duymadan CleanYourTV debloat profillerini Yerel ADB üzerinden doğrudan TV'nizde çalıştırır. TV modelinizi seçin (veya yüklü sistem uygulaması paketlerine göre tarayın), devre dışı bırakılacak gereksiz ve telemetri uygulamalarını belirleyin ve istediğiniz zaman tek dokunuşla geri yükleyin."));

        LocalAdb.probePortAsync(this, () -> runOnUiThread(this::refreshAll));
    }

    void pageAbout() {
        section(tr("APPLICATION", "UYGULAMA"));
        infoRow(tr("Home screen", "Ana ekran"), "tvOS Launcher", null);
        infoRow(tr("Developer", "Geliştirici"), "techgenius", null);
        infoRow(tr("Version", "Sürüm"), version(), null);
        infoRow(tr("Settings kept", "Ayarların saklandığı yer"), tr("On this TV", "Bu TV'de"), null);

        section(tr("RESET", "SIFIRLAMA"));
        row(tr("Reset settings to defaults", "Ayarları varsayılana sıfırla"),
                () -> "",
                () -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(tr("Reset settings?", "Ayarlar sıfırlansın mı?"))
                        .setMessage(tr("This resets look, scenes, clock and language settings back to defaults.",
                                "Bu işlem görünüm, sahne, saat ve dil ayarlarını varsayılana sıfırlar."))
                        .setPositiveButton(tr("Reset", "Sıfırla"), (d, w) -> resetAllAppDataAndReturnToSetup())
                        .setNegativeButton(tr("Cancel", "İptal"), null)
                        .show());
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
            "media_watch", "cleanyourtv_prefs", "local_adb", "present"
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
        int total = 5;
        if (Perms.isDefaultHome(this)) granted++;
        if (ButtonMapper.isServiceEnabled(this)) granted++;
        if (Perms.hasAdmin(this)) granted++;
        if (Perms.hasListener(this)) granted++;
        if (Perms.hasLocation(this)) granted++;
        return granted + " / " + total + " " + tr("active", "etkin");
    }

    private void pairOrRepairAdb() {
        AlertDialog progress = dialog(tr("Pair / Repair ADB", "ADB Eşleştir / Onar"))
                .setMessage(tr(
                        "Connecting to local ADB on port 5555 and granting all permissions…\n\nIf an \u201cAllow USB debugging?\u201d prompt appears, check \u201cAlways allow\u201d and press Allow.",
                        "Port 5555 üzerinden yerel ADB'ye bağlanılıyor ve tüm izinler veriliyor…\n\nEkranda \u201cUSB hata ayıklamaya izin verilsin mi?\u201d penceresi çıkarsa \u201cHer zaman izin ver\u201di işaretleyip İzin Ver'e basın."))
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
            if (success) {
                showStatusMsg(tr("ADB paired and all permissions granted!", "ADB eşleşti ve tüm izinler verildi!"));
            } else {
                dialog(tr("ADB Debugging Needed", "ADB Hata Ayıklama Gerekli"))
                        .setMessage(tr(
                                "Could not connect to local ADB on port 5555.\n\n1. Open Settings \u2192 System \u2192 About and click Build 7 times.\n2. Open Developer Options and turn on USB Debugging / Network Debugging.\n3. Return here and tap Pair / Repair again.",
                                "Port 5555 üzerinden yerel ADB'ye bağlanılamadı.\n\n1. Ayarlar \u2192 Sistem \u2192 Hakkında menüsünden Yapı Numarası'na 7 kez basın.\n2. Geliştirici Seçenekleri'nden USB / Ağ Hata Ayıklamayı açın.\n3. Buraya dönüp tekrar Eşleştir / Onar'a basın."))
                        .setPositiveButton(tr("Open Developer Options…", "Geliştirici Seçeneklerini Aç…"), (d, w) -> {
                            Intent dev = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                            if (!Suggestions.start(this, dev)) {
                                Intent about = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                                if (!Suggestions.start(this, about)) {
                                    Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                                }
                            }
                        })
                        .setNegativeButton(tr("Cancel", "İptal"), null)
                        .show();
            }
        }));
    }

    private void showCheckPermissionsDialog() {
        boolean pHome = Perms.isDefaultHome(this);
        boolean pAcc = ButtonMapper.isServiceEnabled(this);
        boolean pAdmin = Perms.hasAdmin(this);
        boolean pNotif = Perms.hasListener(this);
        boolean pLoc = Perms.hasLocation(this);

        String[] items = {
            (pHome ? "✓  " : "✗  ") + tr("Default Home Screen", "Varsayılan Ana Ekran")
                    + "  —  " + (pHome ? tr("Active", "Etkin") : tr("Not set", "Ayarlanmadı")),
            (pAcc ? "✓  " : "✗  ") + tr("Button Mapper & Control Panel (Accessibility)", "Düğme Eşleyici ve Denetim Paneli (Erişilebilirlik)")
                    + "  —  " + (pAcc ? tr("Active", "Etkin") : tr("Disabled", "Kapalı")),
            (pAdmin ? "✓  " : "✗  ") + tr("Power Off & Sleep Timer (Device Admin)", "Kapatma ve Uyku Zamanlayıcısı (Cihaz Yöneticisi)")
                    + "  —  " + (pAdmin ? tr("Allowed", "İzin verildi") : tr("Not allowed", "İzin verilmedi")),
            (pNotif ? "✓  " : "✗  ") + tr("Continue Watching & DND (Notification Access)", "İzlemeye Devam Et ve Rahatsız Etmeyin")
                    + "  —  " + (pNotif ? tr("Allowed", "İzin verildi") : tr("Not allowed", "İzin verilmedi")),
            (pLoc ? "✓  " : "✗  ") + tr("Wi-Fi Name (Location)", "Wi-Fi Adı (Konum)")
                    + "  —  " + (pLoc ? tr("Allowed", "İzin verildi") : tr("Not allowed", "İzin verilmedi"))
        };

        dialog(tr("Check Permissions", "İzinleri Kontrol Et"))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            Perms.requestDefaultHome(this);
                            break;
                        case 1: {
                            Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            if (!Suggestions.start(this, acc)) {
                                Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
                            }
                            break;
                        }
                        case 2:
                            Perms.requestAdmin(this);
                            break;
                        case 3:
                            Perms.requestListener(this);
                            break;
                        case 4:
                            Perms.requestLocation(this);
                            break;
                    }
                })
                .setPositiveButton(tr("Grant All via ADB", "Tümünü ADB ile Ver"), (d, w) -> pairOrRepairAdb())
                .setNegativeButton(tr("Close", "Kapat"), null)
                .show();
    }

    // ================================================================ Row Builders (.shead, .srow)

    private Bitmap getSceneThumb(String assetPath, int reqW, int reqH) {
        String key = assetPath + ":" + reqW + "x" + reqH;
        Bitmap cached = sceneThumbCache.get(key);
        if (cached != null) return cached;
        Bitmap raw = Scenes.loadAssetBitmap(this, assetPath, reqW, reqH);
        if (raw != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(raw, reqW, reqH, true);
            if (scaled != raw) raw.recycle();
            sceneThumbCache.put(key, scaled);
            return scaled;
        }
        return null;
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
        lp.leftMargin = u(14);
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
        r.setPadding(u(16), u(9), u(16), u(9));

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
        ImageView mark = new ImageView(this);
        if (checked) {
            mark.setImageBitmap(Glyphs.get("check", cs, Color.WHITE));
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
                mark.setImageBitmap(Glyphs.get("check", cs, has ? 0xFF111114 : Color.WHITE));
            }
            view.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .translationZ(has ? u(8) : 0f).setDuration(180).start();
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
        r.setPadding(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        // 16:9 Scene Thumbnail (.sthumb: 5.5rem x 3.125rem -> 46dp x 26dp)
        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap tbmp = getSceneThumb(spec.bgAsset, tw * 2, th * 2);
        if (tbmp != null) thumb.setImageBitmap(tbmp);
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.rightMargin = u(12);
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
            float scale = has ? (isLifted ? 1.04f : 1.02f) : 1f;
            float tx = has && isLifted ? u(6) : 0f;
            view.animate().scaleX(scale).scaleY(scale).translationX(tx)
                    .translationZ(has ? u(10) : 0f).setDuration(180).start();
        });

        r.setOnClickListener(view -> {
            if (liftedSceneId != null) {
                liftedSceneId = null;
                updateHintText(true);
                refreshAll();
                view.animate().scaleX(1.02f).scaleY(1.02f).translationX(0f).setDuration(150).start();
                return;
            }
            if (!Scenes.toggleScene(this, spec.id)) {
                showStatusMsg(tr("One scene has to stay on.", "En az bir sahne açık kalmalıdır."));
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
            view.animate().scaleX(nowLifted ? 1.04f : 1.02f)
                    .scaleY(nowLifted ? 1.04f : 1.02f)
                    .translationX(nowLifted ? u(6) : 0f)
                    .setDuration(160).start();
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
        r.setPadding(u(14), u(6), u(16), u(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u(13));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        int tw = u(46), th = u(26);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap tbmp = getSceneThumb(assetPath, tw * 2, th * 2);
        if (tbmp != null) thumb.setImageBitmap(tbmp);
        final float trad = u(5.5f);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), trad);
            }
        });
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(tw, th);
        thlp.rightMargin = u(12);
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
            view.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .translationZ(has ? u(8) : 0f).setDuration(180).start();
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
        r.setPadding(u(16), 0, u(16), 0);

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
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setTextColor(0x9EFFFFFF);
        v.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(v, 14f);
        v.setSingleLine(true);
        r.addView(v);

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
            view.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .translationZ(has ? u(8) : 0f).setDuration(180).start();
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
        r.setPadding(u(16), u(9), u(16), u(9));

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
        chev.setText("›");
        chev.setTextColor(0x9EFFFFFF);
        chev.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(chev, 15f);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = u(10);
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
            view.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .translationZ(has ? u(8) : 0f).setDuration(180).start();
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
        r.setPadding(u(14), u(5), u(14), u(5));

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

    private void infoNote(String note) {
        TextView t = new TextView(this);
        t.setText(note);
        t.setTextColor(0x88FFFFFF);
        t.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(t, 11.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = u(14);
        lp.rightMargin = u(14);
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
                    if (APPS.equals(currentSection) && showingHiddenApps) {
                        showingHiddenApps = false;
                        rebuildMainRows();
                        focusFirstRow();
                    } else {
                        View sideRow = sideRowViews.get(currentSection);
                        if (sideRow != null) sideRow.requestFocus();
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                if (!down) {
                    if (APPS.equals(currentSection) && showingHiddenApps) {
                        showingHiddenApps = false;
                        rebuildMainRows();
                        focusFirstRow();
                    } else {
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
        if (liftedSceneId == null) return;
        List<String> order = new ArrayList<>(Scenes.getSceneOrder(this));
        int idx = order.indexOf(liftedSceneId);
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= order.size()) return;
        String moved = order.remove(idx);
        order.add(target, moved);
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
            return "2.0.2";
        }
    }

    // ================================================================ Dialog Helpers

    void searchCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(tr("City name, e.g. Istanbul", "Şehir adı, örn. İstanbul"));
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = u(14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = dialog(tr("Choose City", "Şehir Seçin"))
                .setView(box)
                .setPositiveButton(tr("Search", "Ara"), (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    showStatusMsg(tr("Searching…", "Aranıyor…"));
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
                .setNegativeButton(tr("Cancel", "İptal"), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    void showCities(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        showStatusMsg(null);
        if (res == null) {
            showStatusMsg(tr("Couldn't search. Check the internet connection.", "Arama yapılamadı. İnternet bağlantısını kontrol edin."));
            return;
        }
        if (res.isEmpty()) {
            showStatusMsg(tr("No place found for \u201c" + q + "\u201d.", "\u201c" + q + "\u201d için konum bulunamadı."));
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  ·  " + pl.detail;
        }
        dialog(tr("Matching places", "Eşleşen konumlar")).setItems(names, (d, w) -> {
            Weather.Place picked = res.get(w);
            Weather.setPlace(this, picked);
            rebuildMainRows();
            focusFirstRow();
            showStatusMsg(tr("Location set to " + picked.name + ".", "Konum " + picked.name + " olarak ayarlandı."));
        }).setNegativeButton(tr("Cancel", "İptal"), null).show();
    }

    void showPresentInfo() {
        if (!com.techgenius.tvoslauncher.present.Presenter.enabled(this)) {
            Toast.makeText(this, tr("Turn on Presentation mode first", "Önce Sunum modunu açın"), Toast.LENGTH_SHORT).show();
            return;
        }
        com.techgenius.tvoslauncher.present.Presenter.ensureRunning(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = u(16);
        box.setPadding(pad, pad, pad, pad / 2);
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(com.techgenius.tvoslauncher.present.Presenter.qr(u(110)));
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(u(6), u(6), u(6), u(6));
        box.addView(qr, new LinearLayout.LayoutParams(u(122), u(122)));
        TextView info = new TextView(this);
        String pin = com.techgenius.tvoslauncher.present.Presenter.pin(this);
        info.setText(tr("Open on a phone or computer on the same Wi-Fi:\n\n", "Aynı Wi-Fi ağındaki telefon veya bilgisayarda açın:\n\n")
                + com.techgenius.tvoslauncher.present.Presenter.address() + "\n\n"
                + tr("Pairing code\n", "Eşleştirme kodu\n")
                + pin.substring(0, 3) + " " + pin.substring(3));
        info.setTextColor(Color.WHITE);
        info.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(info, 14);
        info.setPadding(u(18), 0, 0, 0);
        box.addView(info);
        dialog(tr("Send to this TV", "Bu TV'ye gönder")).setView(box).setPositiveButton(tr("Done", "Tamam"), null).show();
    }
}
