package com.techgenius.tvoslauncher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Couch-TV "Night scenes" Home Screen layout and interactions:
 * - Multi-layer scene background with giant refractive glass clock & foreground depth cutouts
 * - Top-left profile pill switcher ("Person 1 | Person 2")
 * - Top-right frosted glass Moon sleep timer / quick controls button
 * - "Keep watching" horizontal frosted glass notification cards with artwork & progress bars
 * - Bottom frosted glass dock with rounded-square app icons, Settings gear popup, and More (...)
 */
final class SceneHome {

    static final String GEAR_KEY = "scene:gear";
    static final String MORE_KEY = "scene:more";

    final MainActivity host;
    final Scenes.SceneView sceneView;
    final FrameLayout overlay;

    LinearLayout whoBar;
    LinearLayout weatherBtn;
    ImageView weatherIcon;
    LinearLayout moonBtn;
    ImageView moonIcon;
    TextView moonText;

    private static final String KEY_WEATHER_SHORTCUT = "home_weather_shortcut";

    static boolean isWeatherShortcutEnabled(android.content.Context c) {
        if (c == null) return true;
        return Profiles.homePrefs(c).getBoolean(KEY_WEATHER_SHORTCUT, true);
    }

    static void setWeatherShortcutEnabled(android.content.Context c, boolean enabled) {
        if (c == null) return;
        Profiles.homePrefs(c).edit().putBoolean(KEY_WEATHER_SHORTCUT, enabled).apply();
    }

    LinearLayout shelfBox;
    TextView shelfHeader;
    LinearLayout shelfRow;
    final List<View> shelfCards = new ArrayList<>();

    LinearLayout dockContainer;
    LinearLayout dockBox;
    TextView dockLabel;
    final List<View> dockTiles = new ArrayList<>();
    final List<MainActivity.AppEntry> dockEntries = new ArrayList<>();

    private final Map<String, Bitmap> logoCache = new HashMap<>();
    private Bitmap demoArt1, demoArt2, demoArt3;

    boolean moreExpanded = false;
    boolean backDownInHome = false;
    int focusedDockIdx = 0;
    int focusedShelfIdx = 0;
    int moveDockIdx = -1;
    int currentDockCols = 12;
    int currentMaxSingleRowApps = 10;
    ObjectAnimator moveWiggle;
    FrameLayout sheetLayer;
    View sheetReturnFocus;

    static final class WatchCardData {
        String key;
        String pkg;
        String appName;
        String title;
        String subtitle;
        float progress;
        Bitmap thumb;
        String backdropUrl;
        Suggestions.Item sourceItem;
        boolean removable;
    }

    SceneHome(MainActivity host) {
        this.host = host;
        this.focusedDockIdx = host.prefs.getInt("last_dock_idx", 0);
        this.sceneView = new Scenes.SceneView(host);
        this.sceneView.setOnSceneChangedListener(bg -> {
            if (bg != null && Scenes.isScenesLook(host)) {
                host.io.execute(() -> {
                    Bitmap blurred = Ui.glassBackdrop(bg, 192, 108);
                    host.handler.post(() -> {
                        if (blurred != null && Scenes.isScenesLook(host)) {
                            host.glassBg = blurred;
                            host.invalidateGlass();
                            invalidateSceneGlass();
                        }
                    });
                });
            }
        });

        this.overlay = new FrameLayout(host);
        this.overlay.setClipChildren(false);
        this.overlay.setClipToPadding(false);

        buildTopBar();
        buildKeepWatchingShelf();
        buildBottomDock();
    }

    void invalidateSceneGlass() {
        if (whoBar != null) whoBar.invalidate();
        if (weatherBtn != null) weatherBtn.invalidate();
        if (moonBtn != null) moonBtn.invalidate();
        if (dockBox != null) dockBox.invalidate();
        for (View c : shelfCards) c.invalidate();
    }

    private int dp(float u) {
        return host.dp(u);
    }

    // ---------------------------------------------------------------- Top Bar (Profile pill & Control Panel in top-right)

    private void buildTopBar() {
        LinearLayout topRightRow = new LinearLayout(host);
        topRightRow.setOrientation(LinearLayout.HORIZONTAL);
        topRightRow.setGravity(Gravity.CENTER_VERTICAL);
        topRightRow.setClipChildren(false);
        topRightRow.setClipToPadding(false);
        int grow = dp(6);
        topRightRow.setPadding(grow, grow, grow, grow);

        // Top-right Profile Switcher Pill (next to Control Panel button; hidden if only 1 person)
        whoBar = new LinearLayout(host);
        whoBar.setOrientation(LinearLayout.HORIZONTAL);
        whoBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(3);
        whoBar.setPadding(pad, pad, pad, pad);
        MainActivity.LiquidGlass whoGlass = new MainActivity.LiquidGlass(
                dp(22), 0x6614161C, host.glassSource, host.screenW, host.screenH, host.unit);
        whoGlass.lens = dp(5);
        whoGlass.rimStrength = 0.45f;
        whoBar.setBackground(whoGlass);
        whoBar.setFocusable(true);
        whoBar.setClickable(true);
        whoBar.setLongClickable(true);

        GradientDrawable whoFocusRing = new GradientDrawable();
        whoFocusRing.setCornerRadius(dp(22));
        whoFocusRing.setColor(Color.TRANSPARENT);
        whoFocusRing.setStroke(dp(2), Color.WHITE);

        whoBar.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? whoFocusRing : null);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });
        whoBar.setOnClickListener(v -> {
            List<Profiles.Profile> ps = Profiles.list(host);
            if (ps.size() == 2) {
                String cur = Profiles.current(host).id;
                String next = ps.get(0).id.equals(cur) ? ps.get(1).id : ps.get(0).id;
                host.switchProfile(next);
                refreshWhoBar();
                refreshKeepWatching();
                rebuildDock();
                whoBar.requestFocus();
            } else {
                host.showProfilePicker(false);
            }
        });
        whoBar.setOnLongClickListener(v -> {
            host.showProfilePicker(false);
            return true;
        });

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.rightMargin = dp(10);
        topRightRow.addView(whoBar, wlp);
        refreshWhoBar();

        // Top-right Weather Shortcut Button (icon only; opens 5-day forecast UI like Classic Home)
        int mSize = dp(42);
        weatherBtn = new LinearLayout(host);
        weatherBtn.setOrientation(LinearLayout.HORIZONTAL);
        weatherBtn.setGravity(Gravity.CENTER);
        MainActivity.LiquidGlass weatherGlass = new MainActivity.LiquidGlass(
                mSize / 2f, 0x6614161C, host.glassSource, host.screenW, host.screenH, host.unit);
        weatherGlass.lens = dp(5);
        weatherGlass.rimStrength = 0.45f;
        weatherBtn.setBackground(weatherGlass);
        weatherBtn.setFocusable(true);
        weatherBtn.setClickable(true);
        weatherBtn.setLongClickable(true);

        weatherIcon = new ImageView(host);
        weatherIcon.setImageBitmap(Glyphs.get("cloud", dp(19), 0xFFF4F4F6));
        weatherBtn.addView(weatherIcon, new LinearLayout.LayoutParams(dp(19), dp(19)));

        GradientDrawable weatherFocusRing = new GradientDrawable();
        weatherFocusRing.setCornerRadius(mSize / 2f);
        weatherFocusRing.setColor(Color.TRANSPARENT);
        weatherFocusRing.setStroke(dp(2), Color.WHITE);

        weatherBtn.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? weatherFocusRing : null);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });
        weatherBtn.setOnClickListener(v -> host.showWeather());
        weatherBtn.setOnLongClickListener(v -> {
            host.showWeather();
            return true;
        });

        LinearLayout.LayoutParams wxlp = new LinearLayout.LayoutParams(mSize, mSize);
        wxlp.rightMargin = dp(10);
        topRightRow.addView(weatherBtn, wxlp);
        refreshWeatherBtn();

        // Top-right Control Panel Button
        moonBtn = new LinearLayout(host);
        moonBtn.setOrientation(LinearLayout.HORIZONTAL);
        moonBtn.setGravity(Gravity.CENTER);
        MainActivity.LiquidGlass moonGlass = new MainActivity.LiquidGlass(
                mSize / 2f, 0x6614161C, host.glassSource, host.screenW, host.screenH, host.unit);
        moonGlass.lens = dp(5);
        moonGlass.rimStrength = 0.45f;
        moonBtn.setBackground(moonGlass);
        moonBtn.setFocusable(true);
        moonBtn.setClickable(true);
        moonBtn.setLongClickable(true);

        moonIcon = new ImageView(host);
        moonIcon.setImageBitmap(Glyphs.get("controls", dp(19), 0xFFF4F4F6));
        moonBtn.addView(moonIcon, new LinearLayout.LayoutParams(dp(19), dp(19)));

        moonText = new TextView(host);
        Ui.textSize(moonText, 11f);
        moonText.setTypeface(Ui.font(host, "semibold"));
        moonText.setTextColor(0xFFF4F4F6);
        moonText.setVisibility(View.GONE);
        LinearLayout.LayoutParams mtlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mtlp.leftMargin = dp(6);
        moonBtn.addView(moonText, mtlp);

        GradientDrawable moonFocusRing = new GradientDrawable();
        moonFocusRing.setCornerRadius(mSize / 2f);
        moonFocusRing.setColor(Color.TRANSPARENT);
        moonFocusRing.setStroke(dp(2), Color.WHITE);

        moonBtn.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? moonFocusRing : null);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });
        moonBtn.setOnClickListener(v -> host.showControlCenter());
        moonBtn.setOnLongClickListener(v -> {
            host.showControlCenter();
            return true;
        });

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mSize);
        topRightRow.addView(moonBtn, mlp);
        refreshMoonBtn();

        FrameLayout.LayoutParams trlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        trlp.rightMargin = dp(40) - grow;
        trlp.topMargin = dp(28) - grow;
        overlay.addView(topRightRow, trlp);
    }

    void refreshWhoBar() {
        if (whoBar == null) return;
        whoBar.removeAllViews();
        List<Profiles.Profile> ps = Profiles.list(host);
        if (ps.size() <= 1) {
            whoBar.setVisibility(View.GONE);
            return;
        }
        whoBar.setVisibility(View.VISIBLE);
        String curId = Profiles.current(host).id;
        for (Profiles.Profile p : ps) {
            boolean active = p.id.equals(curId);
            TextView pill = new TextView(host);
            Ui.textSize(pill, 11f);
            pill.setTypeface(Ui.font(host, "semibold"));
            pill.setText(I18n.profileName(host, p.name));
            pill.setPadding(dp(13), dp(6), dp(13), dp(6));
            pill.setSingleLine(true);
            if (active) {
                GradientDrawable abg = new GradientDrawable();
                abg.setCornerRadius(dp(18));
                abg.setColor(0xF2FFFFFF);
                pill.setBackground(abg);
                pill.setTextColor(0xFF111114);
            } else {
                pill.setTextColor(0xB8F4F4F6);
            }
            whoBar.addView(pill, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    void refreshWeatherBtn() {
        if (weatherBtn == null) return;
        boolean show = isWeatherShortcutEnabled(host) && Weather.enabled(host);
        weatherBtn.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    void refreshMoonBtn() {
        if (moonBtn == null) return;
        int left = SleepReceiver.timerLeft(host);
        int mSize = dp(42);
        if (left > 0) {
            moonText.setText(left + I18n.s(host, "m", " dk"));
            moonText.setVisibility(View.VISIBLE);
            moonBtn.setPadding(dp(13), 0, dp(14), 0);
        } else {
            moonText.setVisibility(View.GONE);
            moonBtn.setPadding(dp(11.5f), 0, dp(11.5f), 0);
        }
        moonBtn.setMinimumWidth(mSize);
    }

    // ---------------------------------------------------------------- Keep Watching Glass Shelf

    private void buildKeepWatchingShelf() {
        shelfBox = new LinearLayout(host);
        shelfBox.setOrientation(LinearLayout.VERTICAL);
        shelfBox.setClipChildren(false);
        shelfBox.setClipToPadding(false);
        shelfBox.setVisibility(View.GONE);

        shelfHeader = new TextView(host);
        Ui.textSize(shelfHeader, 11.5f);
        shelfHeader.setTypeface(Ui.font(host, "semibold"));
        shelfHeader.setTextColor(0xC7F4F4F6);
        shelfHeader.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        shelfHeader.setText(I18n.s(host, "Keep watching", "İzlemeye devam et"));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(7);
        hlp.leftMargin = dp(2);
        shelfBox.addView(shelfHeader, hlp);

        shelfRow = new LinearLayout(host);
        shelfRow.setOrientation(LinearLayout.HORIZONTAL);
        shelfRow.setClipChildren(false);
        shelfRow.setClipToPadding(false);
        shelfBox.addView(shelfRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        slp.leftMargin = dp(40);
        slp.rightMargin = dp(40);
        slp.topMargin = dp(312);
        overlay.addView(shelfBox, slp);
    }

    void refreshKeepWatching() {
        if (shelfRow == null) return;
        shelfHeader.setText(I18n.s(host, "Keep watching", "İzlemeye devam et"));
        if (!host.cwEnabled() || moreExpanded) {
            shelfBox.setVisibility(View.GONE);
            return;
        }
        if (shelfCards.isEmpty()) {
            shelfBox.setVisibility(View.GONE);
        }

        host.io.execute(() -> {
            List<WatchCardData> cards = loadWatchCards();
            host.handler.post(() -> populateWatchRow(cards));
        });
    }

    private List<WatchCardData> loadWatchCards() {
        List<WatchCardData> out = new ArrayList<>();
        List<Suggestions.Item> cw = MediaWatchService.Store.list(host);
        for (Suggestions.Item it : cw) {
            if (out.size() >= 3) break;
            WatchCardData c = new WatchCardData();
            c.key = it.key;
            c.pkg = it.pkg;
            c.appName = host.appLabel(it.pkg);
            c.title = it.title;
            if (it.remainingMin > 0) {
                c.subtitle = it.remainingMin + I18n.s(host, " min left", " dk kaldı");
            } else if (it.overview != null && !it.overview.isEmpty()) {
                c.subtitle = it.overview;
            } else {
                c.subtitle = I18n.s(host, "Resume playback", "Oynatmaya devam et");
            }
            c.progress = it.progress > 0 ? it.progress : 0.45f;
            c.backdropUrl = it.backdrop;
            c.sourceItem = it;
            c.removable = true;
            if (it.backdrop != null && !it.backdrop.isEmpty()) {
                c.thumb = Suggestions.image(host, it.backdrop, "w300", 240);
            }
            out.add(c);
        }
        return out;
    }

    private String lastWatchSig = "";

    private void populateWatchRow(List<WatchCardData> cards) {
        if (cards == null || cards.isEmpty() || !host.cwEnabled() || moreExpanded) {
            lastWatchSig = "";
            shelfRow.removeAllViews();
            shelfCards.clear();
            shelfBox.setVisibility(View.GONE);
            return;
        }
        shelfBox.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size() && i < 3; i++) {
            WatchCardData c = cards.get(i);
            sb.append(c.key).append('|').append(c.title).append('|').append(c.subtitle).append('|').append(c.progress).append(';');
        }
        String sig = sb.toString();
        if (sig.equals(lastWatchSig) && !shelfCards.isEmpty()) {
            return;
        }
        lastWatchSig = sig;
        shelfRow.removeAllViews();
        shelfCards.clear();
        int cardW = dp(282);
        int cardH = dp(64);
        int gap = dp(12);
        for (int i = 0; i < cards.size() && i < 3; i++) {
            final int idx = i;
            WatchCardData data = cards.get(i);
            View card = buildWatchCard(data, idx, cardW, cardH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) lp.leftMargin = gap;
            shelfRow.addView(card, lp);
            shelfCards.add(card);
        }
    }

    private View buildWatchCard(WatchCardData d, int index, int cardW, int cardH) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(8);
        card.setPadding(pad, pad, dp(12), pad);
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(true);
        card.setElevation(dp(5));

        float r = dp(15);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                r, 0x5C1E141C, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(6);
        glass.rimStrength = 0.42f;
        card.setBackground(glass);

        GradientDrawable focusRing = new GradientDrawable();
        focusRing.setCornerRadius(r);
        focusRing.setColor(Color.TRANSPARENT);
        focusRing.setStroke(dp(2), Color.WHITE);

        // Left thumbnail (16:9)
        int tw = dp(84), th = dp(48);
        ImageView thumb = new ImageView(host);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setCornerRadius(dp(8.5f));
        tbg.setColor(0xFF1A1C22);
        thumb.setBackground(tbg);
        thumb.setOutlineProvider(host.roundedOutline(new float[]{dp(8.5f)}));
        thumb.setClipToOutline(true);
        if (d.thumb != null) thumb.setImageBitmap(d.thumb);
        card.addView(thumb, new LinearLayout.LayoutParams(tw, th));

        // Right text column + progress bar
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(10);
        card.addView(body, blp);

        // App icon + uppercase app name
        LinearLayout metaRow = new LinearLayout(host);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        Bitmap appLogo = getSquareAppLogo(d.pkg);
        if (appLogo != null) {
            ImageView aiv = new ImageView(host);
            aiv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            aiv.setImageBitmap(appLogo);
            aiv.setOutlineProvider(host.roundedOutline(new float[]{dp(2.5f)}));
            aiv.setClipToOutline(true);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(10), dp(10));
            alp.rightMargin = dp(4.5f);
            metaRow.addView(aiv, alp);
        }
        TextView appTxt = new TextView(host);
        Ui.textSize(appTxt, 8f);
        appTxt.setTypeface(Ui.font(host, "semibold"));
        appTxt.setTextColor(0xB8F4F4F6);
        appTxt.setLetterSpacing(0.06f);
        appTxt.setSingleLine(true);
        appTxt.setEllipsize(TextUtils.TruncateAt.END);
        appTxt.setText(d.appName != null ? d.appName.toUpperCase(I18n.locale(host)) : "");
        metaRow.addView(appTxt);
        body.addView(metaRow);

        // Title
        TextView titleTxt = new TextView(host);
        Ui.textSize(titleTxt, 11.5f);
        titleTxt.setTypeface(Ui.font(host, "semibold"));
        titleTxt.setTextColor(0xFFF4F4F6);
        titleTxt.setSingleLine(true);
        titleTxt.setEllipsize(TextUtils.TruncateAt.END);
        titleTxt.setText(d.title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(1);
        body.addView(titleTxt, tlp);

        // Subtitle
        TextView subTxt = new TextView(host);
        Ui.textSize(subTxt, 9.2f);
        subTxt.setTypeface(Ui.font(host, "regular"));
        subTxt.setTextColor(0xB8F4F4F6);
        subTxt.setSingleLine(true);
        subTxt.setEllipsize(TextUtils.TruncateAt.END);
        subTxt.setText(d.subtitle);
        body.addView(subTxt);

        // Progress bar
        FrameLayout barTrack = new FrameLayout(host);
        GradientDrawable trBg = new GradientDrawable();
        trBg.setCornerRadius(dp(1.5f));
        trBg.setColor(0x2EFFFFFF);
        barTrack.setBackground(trBg);
        View barFill = new View(host);
        GradientDrawable flBg = new GradientDrawable();
        flBg.setCornerRadius(dp(1.5f));
        flBg.setColor(0xEBFFFFFF);
        barFill.setBackground(flBg);
        int maxBarW = cardW - tw - dp(30);
        int fillW = Math.max(dp(6), Math.round(maxBarW * Math.max(0.05f, Math.min(1f, d.progress))));
        barTrack.addView(barFill, new FrameLayout.LayoutParams(fillW, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2.5f));
        prlp.topMargin = dp(4.5f);
        body.addView(barTrack, prlp);

        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedShelfIdx = index;
            v.setForeground(has ? focusRing : null);
            v.animate().withLayer().scaleX(has ? 1.035f : 1f).scaleY(has ? 1.035f : 1f)
                    .translationY(has ? -dp(2) : 0)
                    .translationZ(has ? dp(10) : 0)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });
        card.setOnClickListener(v -> openWatchCard(d));
        card.setOnLongClickListener(v -> {
            showWatchCardMenu(v, d);
            return true;
        });

        return card;
    }

    private void openWatchCard(WatchCardData d) {
        if (d.sourceItem != null) {
            host.openHeroItem(d.sourceItem);
            return;
        }
        if (d.pkg != null && !d.pkg.isEmpty()) {
            for (MainActivity.AppEntry e : host.all.values()) {
                if (d.pkg.equals(e.pkg) && e.launch != null) {
                    host.launchAnimated(null, e);
                    return;
                }
            }
            Intent launch = host.getPackageManager().getLeanbackLaunchIntentForPackage(d.pkg);
            if (launch == null) launch = host.getPackageManager().getLaunchIntentForPackage(d.pkg);
            if (launch != null) {
                host.prepareForAppLaunch();
                if (Suggestions.start(host, launch)) {
                    host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                    final String pkg = d.pkg;
                    host.io.execute(() -> host.noteLaunched(pkg));
                }
                return;
            }
        }
        // Fallback: open first streaming app or show toast
        if (!host.apps.isEmpty()) {
            host.launchAnimated(shelfCards.get(Math.min(focusedShelfIdx, shelfCards.size() - 1)), host.apps.get(0));
        }
    }

    private void showWatchCardMenu(View anchor, WatchCardData d) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(I18n.s(host, "Resume", "Devam Et"));
        actions.add(() -> openWatchCard(d));
        int destructive = -1;
        if (d.removable && d.key != null) {
            destructive = labels.size();
            labels.add(I18n.s(host, "Remove from Keep Watching", "İzlemeye Devam Et'ten Kaldır"));
            actions.add(() -> {
                MediaWatchService.Store.remove(host, d.key);
                refreshKeepWatching();
            });
        }
        host.showMenu(anchor, d.title, labels, actions, destructive);
    }

    // ---------------------------------------------------------------- Bottom Frosted Glass Dock

    private void buildBottomDock() {
        dockContainer = new LinearLayout(host);
        dockContainer.setOrientation(LinearLayout.VERTICAL);
        dockContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        dockContainer.setClipChildren(false);
        dockContainer.setClipToPadding(false);

        int ptrH = dp(5f);
        dockLabel = new TextView(host);
        Ui.textSize(dockLabel, 11.5f);
        dockLabel.setTypeface(Ui.font(host, "medium"));
        dockLabel.setTextColor(0xF5F5F5F7);
        dockLabel.setGravity(Gravity.CENTER);
        dockLabel.setSingleLine(true);
        dockLabel.setPadding(dp(12), dp(5), dp(12), dp(5) + ptrH);
        dockLabel.setBackground(createMacTooltipDrawable());
        dockLabel.setElevation(dp(12));
        dockLabel.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.bottomMargin = dp(5);
        dockContainer.addView(dockLabel, llp);

        dockBox = new LinearLayout(host);
        dockBox.setOrientation(LinearLayout.VERTICAL);
        dockBox.setGravity(Gravity.CENTER_HORIZONTAL);
        dockBox.setClipChildren(false);
        dockBox.setClipToPadding(false);
        int padH = dp(13), padV = dp(10);
        dockBox.setPadding(padH, padV, padH, padV);

        MainActivity.LiquidGlass dockGlass = new MainActivity.LiquidGlass(
                dp(23), 0x541E141C, host.glassSource, host.screenW, host.screenH, host.unit);
        dockGlass.lens = dp(10);
        dockGlass.rimStrength = 0.38f;
        dockBox.setBackground(dockGlass);
        dockBox.setElevation(dp(8));

        dockContainer.addView(dockBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(14);
        overlay.addView(dockContainer, dlp);
    }

    private Drawable createMacTooltipDrawable() {
        final float rad = dp(6.5f);
        final float ptrW = dp(9.5f);
        final float ptrH = dp(5f);
        final float strokeW = Math.max(1f, dp(0.8f));
        final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0xEB1C1E24);
        final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(strokeW);
        borderPaint.setColor(0x2EFFFFFF);

        return new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect b = getBounds();
                float w = b.width();
                float h = b.height() - ptrH;
                if (w <= 0 || h <= 0) return;

                float halfStroke = strokeW / 2f;
                float cx = w / 2f;
                android.graphics.Path path = buildTooltipPath(
                        halfStroke, halfStroke, w - halfStroke, h, rad, cx, ptrW, ptrH);
                canvas.drawPath(path, fillPaint);
                canvas.drawPath(path, borderPaint);
            }

            private android.graphics.Path buildTooltipPath(
                    float l, float t, float r, float b, float radius, float cx, float pw, float ph) {
                android.graphics.Path p = new android.graphics.Path();
                float cr = Math.min(radius, Math.min((r - l) / 2f, (b - t) / 2f));
                float pLeft = Math.max(l + cr, cx - pw / 2f);
                float pRight = Math.min(r - cr, cx + pw / 2f);

                p.moveTo(l + cr, t);
                p.lineTo(r - cr, t);
                p.quadTo(r, t, r, t + cr);
                p.lineTo(r, b - cr);
                p.quadTo(r, b, r - cr, b);
                p.lineTo(pRight, b);
                p.lineTo(cx, b + ph);
                p.lineTo(pLeft, b);
                p.lineTo(l + cr, b);
                p.quadTo(l, b, l, b - cr);
                p.lineTo(l, t + cr);
                p.quadTo(l, t, l + cr, t);
                p.close();
                return p;
            }

            @Override
            public void setAlpha(int alpha) {
                fillPaint.setAlpha(alpha);
                borderPaint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                fillPaint.setColorFilter(colorFilter);
                borderPaint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    private void updateDockTooltip(View tile, String text) {
        if (dockLabel == null) return;
        if (text == null || text.isEmpty() || tile == null) {
            dockLabel.setVisibility(View.INVISIBLE);
            return;
        }
        dockLabel.setText(text);
        dockLabel.setVisibility(View.VISIBLE);
        Runnable align = () -> {
            if (dockBox == null || dockBox.getWidth() <= 0 || tile.getWidth() <= 0) return;
            View parent = (View) tile.getParent();
            int rowLeft = parent != null ? parent.getLeft() : 0;
            int rowTop = parent != null ? parent.getTop() : 0;
            float tileCenterInDock = rowLeft + tile.getLeft() + tile.getWidth() / 2f;
            float dockCenter = dockBox.getWidth() / 2f;
            dockLabel.setTranslationX(tileCenterInDock - dockCenter);
            dockLabel.setTranslationY(rowTop - dockBox.getPaddingTop());
        };
        if (dockBox != null && dockBox.getWidth() > 0 && tile.getWidth() > 0
                && !dockBox.isLayoutRequested() && !tile.isLayoutRequested()) {
            align.run();
        } else if (dockBox != null) {
            dockBox.post(align);
        } else {
            tile.post(align);
        }
    }

    void rebuildDock() {
        if (dockBox == null) return;
        int savedFocusIdx = focusedDockIdx;
        stopDockWiggle();
        dockBox.removeAllViews();
        dockTiles.clear();
        dockEntries.clear();

        float scale = Scenes.getDockScaleFactor(host);
        int tileSize = Math.max(dp(26), Math.round(dp(54) * scale));
        int tileGap = Math.max(dp(4), Math.round(dp(10) * scale));
        int padH = Math.max(dp(8), Math.round(dp(13) * scale));
        int padV = Math.max(dp(6), Math.round(dp(10) * scale));
        dockBox.setPadding(padH, padV, padH, padV);

        int maxDockInnerW = Math.max(tileSize * 4, host.screenW - dp(72) - 2 * padH);
        int maxColsFit = Math.max(4, (maxDockInnerW + tileGap) / (tileSize + tileGap));
        int cols = Math.min(12, maxColsFit);
        currentDockCols = cols;

        // Collect installed apps for Home and Other Apps
        List<MainActivity.AppEntry> homePool = new ArrayList<>();
        Set<String> seenPkgs = new HashSet<>();
        for (MainActivity.AppEntry e : host.apps) {
            if (MainActivity.SETTINGS_KEY.equals(e.key) || MainActivity.TV_SETTINGS.equals(e.pkg)) {
                continue; // Settings has its own dedicated Gear tile in the dock
            }
            homePool.add(e);
            if (e.pkg != null) seenPkgs.add(e.pkg);
        }

        List<MainActivity.AppEntry> otherPool = new ArrayList<>();
        for (MainActivity.AppEntry o : host.otherApps) {
            if (MainActivity.SETTINGS_KEY.equals(o.key) || MainActivity.TV_SETTINGS.equals(o.pkg)) {
                continue;
            }
            if (o.pkg == null || !seenPkgs.contains(o.pkg)) {
                otherPool.add(o);
                if (o.pkg != null) seenPkgs.add(o.pkg);
            }
        }

        // Determine if Other Apps (...) button is needed:
        // Hidden when otherPool is empty AND all homePool apps + Gear fit in a single row (cols).
        boolean hasAnyOtherOrOverflow = !otherPool.isEmpty() || (homePool.size() + 1 > cols);
        if (!hasAnyOtherOrOverflow && moreExpanded) {
            moreExpanded = false;
            if (shelfBox != null) {
                shelfBox.setVisibility((!host.cwEnabled() || shelfCards.isEmpty()) ? View.GONE : View.VISIBLE);
            }
        }

        int maxFirstRowApps = hasAnyOtherOrOverflow ? Math.max(1, cols - 2) : Math.max(1, cols - 1);
        currentMaxSingleRowApps = maxFirstRowApps;

        List<MainActivity.AppEntry> visibleApps;
        if (moreExpanded) {
            visibleApps = new ArrayList<>(homePool.size() + otherPool.size());
            visibleApps.addAll(homePool);
            visibleApps.addAll(otherPool);
        } else {
            visibleApps = homePool.subList(0, Math.min(maxFirstRowApps, homePool.size()));
        }

        dockEntries.addAll(visibleApps);

        // Add Gear tile
        MainActivity.AppEntry gearEntry = new MainActivity.AppEntry();
        gearEntry.key = GEAR_KEY;
        gearEntry.label = I18n.s(host, "Settings", "Ayarlar");
        dockEntries.add(gearEntry);

        // Only show Other Apps (...) tile if there are apps in Other Apps or overflow apps from the dock
        if (hasAnyOtherOrOverflow) {
            MainActivity.AppEntry moreEntry = new MainActivity.AppEntry();
            moreEntry.key = MORE_KEY;
            moreEntry.label = moreExpanded
                    ? I18n.s(host, "Fewer apps", "Daha az uygulama")
                    : I18n.s(host, "Other apps", "Diğer uygulamalar");
            dockEntries.add(moreEntry);
        }

        LinearLayout row = null;
        for (int i = 0; i < dockEntries.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = tileGap;
                dockBox.addView(row, rlp);
            }
            View tile = buildDockTile(dockEntries.get(i), i, tileSize, scale);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(tileSize, tileSize);
            if (i % cols != 0) tlp.leftMargin = tileGap;
            row.addView(tile, tlp);
            dockTiles.add(tile);
        }
        focusedDockIdx = Math.max(0, Math.min(savedFocusIdx, dockTiles.size() - 1));
        dockBox.requestLayout();
        dockBox.invalidate();
        if (dockContainer != null) {
            dockContainer.requestLayout();
            dockContainer.invalidate();
        }
    }

    private View buildDockTile(MainActivity.AppEntry a, int index, int size, float scale) {
        FrameLayout tile = new FrameLayout(host);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);
        tile.setPivotX(size / 2f);
        tile.setPivotY(size / 2f);
        float rad = Math.max(dp(6), Math.round(dp(13) * scale));

        boolean isGear = GEAR_KEY.equals(a.key);
        boolean isMore = MORE_KEY.equals(a.key);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(rad);
        if (isGear || isMore) {
            bg.setColor(0x1EFFFFFF);
            bg.setStroke(Math.max(1, dp(0.8f)), 0x24FFFFFF);
        } else {
            boolean hasCustom = CustomMedia.hasCustomAppIcon(host, a.key, a.pkg);
            Bitmap logo = getSquareAppLogo(a.pkg);
            bg.setColor((hasCustom || logo != null) ? 0xFF141519 : (a.color != 0 ? a.color : 0xFF1A1C22));
        }
        tile.setBackground(bg);
        tile.setOutlineProvider(host.roundedOutline(new float[]{rad}));
        tile.setClipToOutline(true);
        // Zero elevation on dock tiles avoids Android's dark inner/outer shadow artifact bleeding through translucent Gear/More tiles
        tile.setElevation(0f);

        int glyphSize = Math.max(dp(14), Math.round(dp(26) * scale));
        ImageView iv = new ImageView(host);
        if (isGear) {
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setImageBitmap(Glyphs.get("gear", glyphSize, 0xEBF4F4F6));
            tile.addView(iv, host.match());
        } else if (isMore) {
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setImageBitmap(Glyphs.get("more", glyphSize, 0xEBF4F4F6));
            tile.addView(iv, host.match());
        } else {
            boolean hasCustom = CustomMedia.hasCustomAppIcon(host, a.key, a.pkg);
            Bitmap sq = getSquareAppIcon(a);
            if (sq != null) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(sq);
                // Custom icons from App Icon Editor already match the exact preview bounds (1.0f);
                // built-in app icons zoom slightly (+5%) for edge-to-edge infill
                float z = hasCustom ? 1.0f : 1.05f;
                iv.setScaleX(z);
                iv.setScaleY(z);
                tile.addView(iv, host.match());
            } else if (a.icon != null) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(a.icon);
                iv.setScaleX(1.05f);
                iv.setScaleY(1.05f);
                tile.addView(iv, host.match());
            }
        }

        GradientDrawable focusRing = new GradientDrawable();
        focusRing.setCornerRadius(rad);
        focusRing.setColor(Color.TRANSPARENT);
        focusRing.setStroke(dp(2.2f), Color.WHITE);

        tile.setOnFocusChangeListener((v, has) -> {
            v.setPivotX(v.getWidth() > 0 ? v.getWidth() / 2f : size / 2f);
            v.setPivotY(v.getHeight() > 0 ? v.getHeight() / 2f : size / 2f);
            v.setForeground(has ? focusRing : null);
            if (has) {
                focusedDockIdx = index;
                host.prefs.edit().putInt("last_dock_idx", index).apply();
                if (moveDockIdx >= 0) {
                    updateDockTooltip(v, I18n.s(host,
                            "Move " + a.label + "  ·  Left/Right, OK when done",
                            a.label + " taşınıyor  ·  Sol/Sağ, bitince OK"));
                } else {
                    updateDockTooltip(v, a.label);
                }
                v.animate().withLayer().scaleX(1.13f).scaleY(1.13f)
                        .translationY(0f)
                        .translationZ(isGear || isMore ? 0f : dp(8))
                        .setDuration(190).setInterpolator(MainActivity.LIFT).start();
            } else {
                v.animate().withLayer().scaleX(1f).scaleY(1f)
                        .translationY(0f)
                        .translationZ(0f)
                        .setDuration(150).setInterpolator(MainActivity.EASE).start();
                overlay.post(() -> {
                    if (!isAnyDockTileFocused()) updateDockTooltip(null, "");
                });
            }
        });

        tile.setOnClickListener(v -> {
            if (moveDockIdx >= 0) {
                endDockMove();
                return;
            }
            if (isGear) {
                showGearSheet(v);
            } else if (isMore) {
                toggleMoreApps();
            } else {
                openDockApp(v, a);
            }
        });

        tile.setOnLongClickListener(v -> {
            if (!isGear && !isMore && moveDockIdx < 0) {
                showDockAppMenu(index, a, v);
                return true;
            }
            return false;
        });

        return tile;
    }

    private boolean isAnyDockTileFocused() {
        View f = host.getCurrentFocus();
        return f != null && dockTiles.contains(f);
    }

    private void openDockApp(View tile, MainActivity.AppEntry a) {
        if (a.key != null && a.key.startsWith("demo_app:")) {
            for (MainActivity.AppEntry e : host.all.values()) {
                if (a.pkg != null && a.pkg.equals(e.pkg) && e.launch != null) {
                    host.launchAnimated(tile, e);
                    return;
                }
            }
            Intent launch = host.getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (launch == null) launch = host.getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (launch != null) {
                host.prepareForAppLaunch();
                if (Suggestions.start(host, launch)) {
                    host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                    final String pkg = a.pkg;
                    host.io.execute(() -> host.noteLaunched(pkg));
                }
                return;
            }
            // Try opening Play Store or Settings if not installed on this device
            if (!Suggestions.start(host, a.launch)) {
                host.toast(I18n.s(host, a.label + " is not installed on this TV", a.label + " bu TV'de yüklü değil"));
            }
            return;
        }
        host.launchAnimated(tile, a);
    }

    private void toggleMoreApps() {
        moreExpanded = !moreExpanded;
        if (shelfBox != null) {
            shelfBox.setVisibility((moreExpanded || !host.cwEnabled() || shelfCards.isEmpty()) ? View.GONE : View.VISIBLE);
        }
        rebuildDock();
        focusDock(Math.min(focusedDockIdx, dockTiles.size() - 1));
    }

    private void showDockAppMenu(int index, MainActivity.AppEntry a, View tile) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(I18n.s(host, "Open", "Aç"));
        actions.add(() -> openDockApp(tile, a));

        int realIdx = host.apps.indexOf(a);
        if (realIdx >= 0) {
            labels.add(I18n.s(host, "Move", "Taşı"));
            actions.add(() -> startDockMove(index));
        }

        labels.add(I18n.s(host, "Rename", "Yeniden Adlandır"));
        actions.add(() -> host.promptRenameApp(a, () -> {
            rebuildDock();
            int targetIdx = Math.min(index, Math.max(0, dockTiles.size() - 1));
            focusDock(targetIdx);
            if (targetIdx >= 0 && targetIdx < dockTiles.size() && targetIdx < dockEntries.size()) {
                updateDockTooltip(dockTiles.get(targetIdx), dockEntries.get(targetIdx).label);
            }
        }));

        labels.add(I18n.s(host, "Change app icon", "Uygulama simgesini değiştir"));
        actions.add(() -> host.promptChangeAppIcon(a, () -> {
            invalidateAppLogoCache(a);
            host.loadApps();
            host.buildGrid();
            rebuildDock();
            int targetIdx = Math.min(index, Math.max(0, dockTiles.size() - 1));
            focusDock(targetIdx);
        }));

        boolean inOtherApps = host.otherApps.contains(a) || (moreExpanded && index >= currentMaxSingleRowApps);
        if (!host.isSettings(a)) {
            if (inOtherApps) {
                labels.add(I18n.s(host, "Show on Home Screen", "Ana Ekranda Göster"));
                actions.add(() -> {
                    host.setOtherApp(a, false);
                    rebuildDock();
                    focusDock(Math.min(index, Math.max(0, dockTiles.size() - 1)));
                });
            } else {
                labels.add(I18n.s(host, "Move to Other Apps", "Diğer Uygulamalara Taşı"));
                actions.add(() -> {
                    host.setOtherApp(a, true);
                    rebuildDock();
                    focusDock(Math.min(index, Math.max(0, dockTiles.size() - 1)));
                });
            }
            labels.add(I18n.s(host, "Hide", "Gizle"));
            actions.add(() -> {
                host.setHidden(a, true);
                rebuildDock();
                focusDock(Math.min(index, Math.max(0, dockTiles.size() - 1)));
            });
        }

        if (a.pkg != null && !a.key.startsWith("demo_app:") && !MainActivity.INPUTS_KEY.equals(a.key)) {
            labels.add(I18n.s(host, "App Info", "Uygulama Bilgileri"));
            actions.add(() -> host.startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg))));
            if (!a.system) {
                labels.add(I18n.s(host, "Uninstall", "Kaldır"));
                actions.add(() -> host.startSafe(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))));
            }
        }

        labels.add(I18n.s(host, "Reset Layout", "Düzeni Sıfırla"));
        actions.add(() -> {
            host.prefs.edit().remove(MainActivity.PREF_ORDER).remove(MainActivity.PREF_OTHER).apply();
            host.loadApps();
            rebuildDock();
            focusDock(0);
        });

        int uninstIdx = labels.indexOf(I18n.s(host, "Uninstall", "Kaldır"));
        host.showMenu(tile, a.label, labels, actions, uninstIdx);
    }

    private void startDockMove(int index) {
        moveDockIdx = index;
        focusDock(index);
        if (index >= 0 && index < dockTiles.size()) {
            startDockWiggle(dockTiles.get(index));
        }
    }

    private void moveDockBy(int delta) {
        if (moveDockIdx < 0 || moveDockIdx >= dockEntries.size()) return;
        MainActivity.AppEntry cur = dockEntries.get(moveDockIdx);
        int fromReal = host.apps.indexOf(cur);
        int toReal = fromReal + delta;
        if (fromReal < 0 || toReal < 0 || toReal >= host.apps.size()) return;
        host.apps.remove(fromReal);
        host.apps.add(toReal, cur);
        host.saveOrder();
        moveDockIdx += delta;
        rebuildDock();
        focusDock(moveDockIdx);
        if (moveDockIdx >= 0 && moveDockIdx < dockTiles.size()) {
            startDockWiggle(dockTiles.get(moveDockIdx));
        }
    }

    private void endDockMove() {
        stopDockWiggle();
        int idx = moveDockIdx;
        moveDockIdx = -1;
        if (idx >= 0 && idx < dockEntries.size() && idx < dockTiles.size()) {
            updateDockTooltip(dockTiles.get(idx), dockEntries.get(idx).label);
        }
    }

    private void startDockWiggle(View v) {
        stopDockWiggle();
        moveWiggle = ObjectAnimator.ofFloat(v, View.ROTATION, -2f, 2f);
        moveWiggle.setDuration(120);
        moveWiggle.setRepeatCount(ValueAnimator.INFINITE);
        moveWiggle.setRepeatMode(ValueAnimator.REVERSE);
        moveWiggle.start();
    }

    private void stopDockWiggle() {
        if (moveWiggle != null) {
            moveWiggle.cancel();
            Object t = moveWiggle.getTarget();
            if (t instanceof View) ((View) t).setRotation(0f);
            moveWiggle = null;
        }
    }

    void focusDock(int idx) {
        if (dockTiles.isEmpty()) return;
        int clamped = Math.max(0, Math.min(idx, dockTiles.size() - 1));
        focusedDockIdx = clamped;
        host.prefs.edit().putInt("last_dock_idx", clamped).apply();
        dockTiles.get(clamped).requestFocus();
    }

    void invalidateAppLogoCache(MainActivity.AppEntry a) {
        if (a == null) {
            logoCache.clear();
            return;
        }
        if (a.key != null) logoCache.remove(a.key);
        if (a.pkg != null) logoCache.remove(a.pkg);
    }

    Bitmap getSquareAppIcon(MainActivity.AppEntry a) {
        if (a == null) return null;
        if (CustomMedia.hasCustomAppIcon(host, a.key, a.pkg)) {
            Bitmap custom = CustomMedia.getCustomAppIcon(host, a.key, a.pkg);
            if (custom != null && !custom.isRecycled()) {
                if ((custom.getPixel(0, 0) >>> 24) == 255) {
                    return Bitmap.createScaledBitmap(custom, 256, 256, true);
                }
                return CustomMedia.prepareFullBleedAppIcon(custom, 256, 256);
            }
        }
        String cacheKey = a.key != null ? a.key : a.pkg;
        if (cacheKey != null && logoCache.containsKey(cacheKey)) {
            return logoCache.get(cacheKey);
        }
        Bitmap bmp = getSquareAppLogo(a.pkg);
        if (bmp == null) {
            bmp = buildFullSquareIcon(a);
        }
        if (cacheKey != null) {
            logoCache.put(cacheKey, bmp);
        }
        return bmp;
    }

    private Bitmap buildFullSquareIcon(MainActivity.AppEntry a) {
        int s = 256;
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        if (MainActivity.INPUTS_KEY.equals(a.key)) {
            p.setShader(new LinearGradient(0, 0, 0, s, 0xFF6E6E73, 0xFF2C2C2E, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, s, s, p);
            Bitmap g = Glyphs.get("input", Math.round(s * 0.52f), Color.WHITE);
            c.drawBitmap(g, (s - g.getWidth()) / 2f, (s - g.getHeight()) / 2f, null);
            return out;
        }
        if (MainActivity.PRESENT_KEY.equals(a.key)) {
            p.setShader(new LinearGradient(0, 0, 0, s, 0xFF5682FF, 0xFF2B50CC, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, s, s, p);
            Bitmap g = Glyphs.get("present", Math.round(s * 0.52f), Color.WHITE);
            c.drawBitmap(g, (s - g.getWidth()) / 2f, (s - g.getHeight()) / 2f, null);
            return out;
        }
        boolean isPlayStore = "com.android.vending".equals(a.pkg);
        if (isPlayStore) {
            c.drawColor(0xFFFFFFFF);
        } else {
            int baseCol = a.color != 0 ? a.color : 0xFF24262E;
            float[] hsv = new float[3];
            Color.colorToHSV(baseCol, hsv);
            int topCol = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 0.9f), Math.max(0.28f, Math.min(0.85f, hsv[2] * 1.35f))});
            int botCol = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1]), Math.max(0.14f, Math.min(0.55f, hsv[2] * 0.85f))});
            p.setShader(new LinearGradient(0, 0, 0, s, topCol, botCol, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, s, s, p);
            p.setShader(null);
        }

        if (a.icon instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            android.graphics.drawable.AdaptiveIconDrawable aid = (android.graphics.drawable.AdaptiveIconDrawable) a.icon;
            Drawable bgD = aid.getBackground();
            Drawable fgD = aid.getForeground();
            // Expand adaptive 108dp layers by 24% around the 72dp safe zone so they bleed edge-to-edge in a square
            int inset = -Math.round(s * 0.24f);
            if (bgD != null && !isPlayStore) {
                Rect old = bgD.copyBounds();
                bgD.setBounds(inset, inset, s - inset, s - inset);
                bgD.draw(c);
                bgD.setBounds(old);
            }
            if (fgD != null) {
                Rect old = fgD.copyBounds();
                fgD.setBounds(inset, inset, s - inset, s - inset);
                fgD.draw(c);
                fgD.setBounds(old);
            }
            return out;
        }

        if (a.icon != null) {
            int rawW = Math.max(128, a.icon.getIntrinsicWidth());
            int rawH = Math.max(128, a.icon.getIntrinsicHeight());
            // Avoid wide 16:9 banner icons being squished; if it's a wide banner, center-crop or extract icon
            if (rawW > rawH * 1.35f) {
                Bitmap tmp = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888);
                Canvas tc = new Canvas(tmp);
                Rect old = a.icon.copyBounds();
                a.icon.setBounds(0, 0, rawW, rawH);
                a.icon.draw(tc);
                a.icon.setBounds(old);
                int cropSz = rawH;
                int sx = (rawW - cropSz) / 2;
                int zoom = 10;
                c.drawBitmap(tmp, new Rect(sx, 0, sx + cropSz, cropSz), new Rect(-zoom, -zoom, s + zoom, s + zoom), p);
                tmp.recycle();
                return out;
            }
            Bitmap tmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas tc = new Canvas(tmp);
            Rect old = a.icon.copyBounds();
            a.icon.setBounds(0, 0, s, s);
            a.icon.draw(tc);
            a.icon.setBounds(old);

            // Trim transparent padding around legacy icons so they fill the square nicely
            int[] px = new int[s * s];
            tmp.getPixels(px, 0, s, 0, 0, s, s);
            int minX = s, minY = s, maxX = 0, maxY = 0;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    if (Color.alpha(px[y * s + x]) > 32) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX > minX && maxY > minY) {
                int bw = maxX - minX + 1;
                int bh = maxY - minY + 1;
                boolean nearlySquare = Math.abs(bw - bh) <= Math.max(bw, bh) * 0.18f;
                // Sample an inner inset (14% in from trimmed bounds) so rounded-square legacy icons (like Kodi, Stremio, Projectivy)
                // are detected as full-bleed icons and zoomed past their rounded corners!
                int insetX = Math.max(6, Math.round(bw * 0.14f));
                int insetY = Math.max(6, Math.round(bh * 0.14f));
                int cx0 = minX + insetX, cy0 = minY + insetY, cx1 = maxX - insetX, cy1 = maxY - insetY;
                boolean filledBody = nearlySquare && cx1 > cx0 && cy1 > cy0
                        && Color.alpha(px[cy0 * s + cx0]) > 200
                        && Color.alpha(px[cy0 * s + cx1]) > 200
                        && Color.alpha(px[cy1 * s + cx0]) > 200
                        && Color.alpha(px[cy1 * s + cx1]) > 200;
                Rect src = new Rect(minX, minY, maxX + 1, maxY + 1);
                if (filledBody) {
                    // Paint background with the icon's own top/bottom edge colors and zoom out any rounded corner border
                    int topEdge = px[(minY + insetY) * s + (minX + bw / 2)];
                    int botEdge = px[(maxY - insetY) * s + (minX + bw / 2)];
                    if (Color.alpha(topEdge) > 180 && Color.alpha(botEdge) > 180) {
                        p.setShader(new LinearGradient(0, 0, 0, s, topEdge | 0xFF000000, botEdge | 0xFF000000, Shader.TileMode.CLAMP));
                        c.drawRect(0, 0, s, s, p);
                        p.setShader(null);
                    }
                    int zoom = Math.round(s * 0.06f); // +15px zoom on 256x256 to eliminate rounded corner gaps
                    c.drawBitmap(tmp, src, new Rect(-zoom, -zoom, s + zoom, s + zoom), p);
                } else {
                    int pad = Math.round(s * 0.10f);
                    c.drawBitmap(tmp, src, new Rect(pad, pad, s - pad, s - pad), p);
                }
            } else {
                c.drawBitmap(tmp, 0, 0, p);
            }
            tmp.recycle();
        }
        return out;
    }

    Bitmap getSquareAppLogo(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        if (logoCache.containsKey(pkg)) return logoCache.get(pkg);
        String assetName = mapPkgToLogoAsset(pkg);
        Bitmap bmp = null;
        if (assetName != null) {
            Bitmap raw = Scenes.loadAssetBitmap(host, "app-logos/" + assetName, 256, 256);
            if (raw != null) {
                int s = 256;
                Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(out);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                if ("com.google.android.youtube.tv.png".equals(assetName)) {
                    // Official YouTube squircle look: crisp white tile with centered red YouTube badge
                    cv.drawColor(0xFFFFFFFF);
                    int padX = Math.round(s * 0.16f);
                    int badgeW = s - padX * 2;
                    int badgeH = Math.round(badgeW * ((float) raw.getHeight() / Math.max(1, raw.getWidth())));
                    int topY = (s - badgeH) / 2;
                    cv.drawBitmap(raw, null, new Rect(padX, topY, padX + badgeW, topY + badgeH), p);
                } else {
                    int zoom = 6;
                    cv.drawBitmap(raw, null, new Rect(-zoom, -zoom, s + zoom, s + zoom), p);
                }
                if (raw != out) raw.recycle();
                bmp = out;
            }
        }
        logoCache.put(pkg, bmp);
        return bmp;
    }

    private static String mapPkgToLogoAsset(String pkg) {
        switch (pkg) {
            case "com.google.android.youtube.tv":
            case "com.google.android.youtube.tvkids":
            case "org.smarttube.stable":
            case "com.liskovsoft.smarttubetv.beta":
                return "com.google.android.youtube.tv.png";
            case "com.netflix.ninja":
                return "com.netflix.ninja.png";
            case "com.disney.disneyplus":
            case "in.startv.hotstar":
                return "com.disney.disneyplus.png";
            case "com.amazon.amazonvideo.livingroom":
            case "com.amazon.avod.thirdpartyclient":
                return "com.amazon.amazonvideo.livingroom.png";
            case "com.apple.atve.androidtv.appletv":
                return "com.apple.atve.androidtv.appletv.png";
            case "com.spotify.tv.android":
            case "com.spotify.music":
                return "com.spotify.tv.android.png";
            case "tv.twitch.android.app":
                return "tv.twitch.android.app.png";
            case "com.wbd.stream":
            case "com.hbo.hbonow":
                return "com.wbd.stream.png";
            case "com.cbs.ca":
            case "com.cbs.ott":
                return "com.cbs.ca.png";
            case "au.net.abc.iview":
                return "au.net.abc.iview.png";
            case "com.sbs.ondemand.tv":
                return "com.sbs.ondemand.tv.png";
            case "au.com.ninenow.ctv":
                return "au.com.ninenow.ctv.png";
            case "com.swm.live":
                return "com.swm.live.png";
            case "au.com.tenplay":
                return "au.com.tenplay.png";
            case "au.com.stan.and":
                return "au.com.stan.and.png";
            case "au.com.kayosports.tv":
                return "au.com.kayosports.tv.png";
            case "au.com.streamotion.ares.tv":
                return "au.com.streamotion.ares.tv.png";
            case "au.com.foxtel.atv":
                return "au.com.foxtel.atv.png";
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------- Gear Popup & Sleep Sheet

    void showGearSheet(View returnTo) {
        closeSheet();
        sheetReturnFocus = returnTo;
        sheetLayer = new FrameLayout(host);
        sheetLayer.setClickable(true);
        sheetLayer.setClipChildren(false);
        sheetLayer.setClipToPadding(false);
        if (shelfBox != null && shelfBox.getVisibility() == View.VISIBLE) {
            shelfBox.animate().alpha(0f).setDuration(150).start();
        }

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        int pad = dp(8);
        card.setPadding(pad, pad, pad, pad);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                dp(18), 0xBD1C181E, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(7);
        card.setBackground(glass);
        card.setOutlineProvider(host.roundedOutline(new float[]{dp(18)}));
        card.setClipToOutline(true);
        card.setElevation(dp(24));

        View btnHomeSettings = makeGearOptionRow(
                "sliders",
                I18n.s(host, "Home settings", "Ana ekran ayarları"),
                I18n.s(host, "Language, weather, scenes, apps and people",
                        "Dil, hava durumu, sahneler, uygulamalar ve kişiler"),
                () -> {
                    closeSheet();
                    host.returningFromSettings = true;
                    host.startActivity(new Intent(host, SettingsActivity.class));
                });
        card.addView(btnHomeSettings);

        View btnTvSettings = makeGearOptionRow(
                "gear",
                I18n.s(host, "TV settings", "TV ayarları"),
                I18n.s(host, "Wi-Fi, picture, sound and the rest of the TV",
                        "Wi-Fi, görüntü, ses ve diğer TV ayarları"),
                () -> {
                    closeSheet();
                    host.startSafe(new Intent(Settings.ACTION_SETTINGS));
                });
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvlp.topMargin = dp(2);
        card.addView(btnTvSettings, tvlp);

        btnHomeSettings.setId(View.generateViewId());
        btnTvSettings.setId(View.generateViewId());
        btnHomeSettings.setNextFocusDownId(btnTvSettings.getId());
        btnHomeSettings.setNextFocusUpId(btnTvSettings.getId());
        btnTvSettings.setNextFocusUpId(btnHomeSettings.getId());
        btnTvSettings.setNextFocusDownId(btnHomeSettings.getId());

        int popW = dp(330);
        card.measure(View.MeasureSpec.makeMeasureSpec(popW, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int popH = card.getMeasuredHeight();

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(popW, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (returnTo != null) {
            int[] loc = new int[2];
            returnTo.getLocationInWindow(loc);
            int left = loc[0] + returnTo.getWidth() / 2 - popW / 2;
            left = Math.max(dp(40), Math.min(host.screenW - popW - dp(40), left));
            int top = Math.max(dp(30), loc[1] - popH - dp(18));
            clp.leftMargin = left;
            clp.topMargin = top;
        } else {
            clp.gravity = Gravity.CENTER;
        }
        sheetLayer.addView(card, clp);
        host.root.addView(sheetLayer, host.match());
        sheetLayer.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        sheetLayer.animate().alpha(1f).setDuration(180).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(MainActivity.LIFT).start();
        btnHomeSettings.requestFocus();
    }

    private View makeGearOptionRow(String glyphName, String title, String sub, Runnable onClick) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(7), dp(14), dp(7));
        row.setFocusable(true);
        row.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.TRANSPARENT);
        row.setBackground(bg);

        FrameLayout iconBox = new FrameLayout(host);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dp(8));
        ibg.setColor(0x1FFFFFFF);
        iconBox.setBackground(ibg);
        ImageView iconIv = new ImageView(host);
        iconIv.setScaleType(ImageView.ScaleType.CENTER);
        iconIv.setImageBitmap(Glyphs.get(glyphName, dp(16), 0xEBF4F4F6));
        iconBox.addView(iconIv, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        LinearLayout.LayoutParams iblp = new LinearLayout.LayoutParams(dp(28), dp(28));
        iblp.rightMargin = dp(10);
        row.addView(iconBox, iblp);

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(host);
        Ui.textSize(t, 13.5f);
        t.setTypeface(Ui.font(host, "semibold"));
        t.setTextColor(0xEBF4F4F6);
        t.setText(title);
        texts.addView(t);

        TextView s = new TextView(host);
        Ui.textSize(s, 9.5f);
        s.setTypeface(Ui.font(host, "regular"));
        s.setTextColor(0x9EF4F4F6);
        s.setText(sub);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(1);
        texts.addView(s, slp);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            ibg.setColor(has ? 0x12000000 : 0x1FFFFFFF);
            iconIv.setImageBitmap(Glyphs.get(glyphName, dp(16), has ? 0xFF111114 : 0xEBF4F4F6));
            t.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            s.setTextColor(has ? 0x99000000 : 0x9EF4F4F6);
            v.setElevation(0f);
            v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(150).setInterpolator(MainActivity.EASE).start();
        });
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    void showSleepSheet() {
        closeSheet();
        sheetReturnFocus = moonBtn;
        sheetLayer = new FrameLayout(host);
        sheetLayer.setClickable(true);
        host.addBlurredBackdrop(sheetLayer, 0x88000000);

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        card.setPadding(pad, pad, pad, pad);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                dp(22), 0xB8181820, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(8);
        card.setBackground(glass);
        card.setElevation(dp(24));

        TextView hTitle = new TextView(host);
        Ui.textSize(hTitle, 17f);
        hTitle.setTypeface(Ui.font(host, "semibold"));
        hTitle.setTextColor(0xFFF4F4F6);
        hTitle.setText(I18n.s(host, "Sleep timer", "Uyku zamanlayıcısı"));
        card.addView(hTitle);

        TextView hSub = new TextView(host);
        Ui.textSize(hSub, 11f);
        hSub.setTypeface(Ui.font(host, "regular"));
        hSub.setTextColor(0xB3F4F4F6);
        int curLeft = SleepReceiver.timerLeft(host);
        hSub.setText(curLeft > 0
                ? I18n.s(host, "Off in " + curLeft + " min", curLeft + " dk içinde kapanacak")
                : I18n.s(host, "Turn the TV off after", "TV'yi şu süreden sonra kapat:"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(2);
        slp.bottomMargin = dp(12);
        card.addView(hSub, slp);

        LinearLayout pillsRow = new LinearLayout(host);
        pillsRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] mins = {0, 15, 30, 45, 60, 90, 120};
        View firstFocus = null;
        for (int i = 0; i < mins.length; i++) {
            final int m = mins[i];
            TextView pill = new TextView(host);
            Ui.textSize(pill, 11.5f);
            pill.setTypeface(Ui.font(host, "semibold"));
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(12), dp(9), dp(12), dp(9));
            pill.setFocusable(true);
            pill.setClickable(true);
            pill.setText(m == 0
                    ? I18n.s(host, "Off", "Kapalı")
                    : m + I18n.s(host, " min", " dk"));
            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(dp(11));
            pbg.setColor(0x18FFFFFF);
            pill.setBackground(pbg);
            pill.setTextColor(0xFFF4F4F6);
            pill.setOnFocusChangeListener((v, has) -> {
                pbg.setColor(has ? 0xF2FFFFFF : 0x18FFFFFF);
                pill.setTextColor(has ? 0xFF111114 : 0xFFF4F4F6);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                        .setDuration(150).setInterpolator(MainActivity.LIFT).start();
            });
            pill.setOnClickListener(v -> {
                SleepReceiver.setTimer(host, m);
                refreshMoonBtn();
                closeSheet();
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) plp.leftMargin = dp(6);
            pillsRow.addView(pill, plp);
            if ((curLeft == 0 && m == 0) || (curLeft > 0 && m >= curLeft && firstFocus == null)) {
                firstFocus = pill;
            }
        }
        card.addView(pillsRow);

        // Quick shortcuts row below Sleep Timer pills: Control Center, Search, Weather
        LinearLayout quickRow = new LinearLayout(host);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrlp.topMargin = dp(12);
        card.addView(quickRow, qrlp);

        View ccShortcut = makeQuickChip(I18n.s(host, "Control Center", "Denetim Merkezi"), () -> {
            closeSheet();
            host.showControlCenter();
        });
        View searchShortcut = makeQuickChip(I18n.s(host, "Search", "Ara"), () -> {
            closeSheet();
            host.showSearch(null);
        });
        View weatherShortcut = makeQuickChip(I18n.s(host, "Weather Forecast", "Hava Durumu"), () -> {
            closeSheet();
            host.showWeather();
        });
        quickRow.addView(ccShortcut, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams s2lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        s2lp.leftMargin = dp(8);
        quickRow.addView(searchShortcut, s2lp);
        LinearLayout.LayoutParams s3lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        s3lp.leftMargin = dp(8);
        quickRow.addView(weatherShortcut, s3lp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        sheetLayer.addView(card, clp);
        host.root.addView(sheetLayer, host.match());
        sheetLayer.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        sheetLayer.animate().alpha(1f).setDuration(180).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(MainActivity.LIFT).start();
        if (firstFocus != null) firstFocus.requestFocus();
        else if (pillsRow.getChildCount() > 0) pillsRow.getChildAt(0).requestFocus();
    }

    private View makeQuickChip(String label, Runnable onClick) {
        TextView chip = new TextView(host);
        Ui.textSize(chip, 11f);
        chip.setTypeface(Ui.font(host, "medium"));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(8), dp(10), dp(8));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setText(label);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0x14FFFFFF);
        chip.setBackground(bg);
        chip.setTextColor(0xD9F4F4F6);
        chip.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x14FFFFFF);
            chip.setTextColor(has ? 0xFF111114 : 0xD9F4F4F6);
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(150).start();
        });
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    boolean isSheetOpen() {
        return sheetLayer != null;
    }

    void closeSheet() {
        if (sheetLayer == null) return;
        FrameLayout l = sheetLayer;
        sheetLayer = null;
        if (shelfBox != null && shelfBox.getVisibility() == View.VISIBLE) {
            shelfBox.animate().alpha(1f).setDuration(150).start();
        }
        l.animate().alpha(0f).setDuration(150).withEndAction(() -> host.root.removeView(l)).start();
        if (sheetReturnFocus != null && sheetReturnFocus.isAttachedToWindow()) {
            sheetReturnFocus.requestFocus();
        } else {
            focusDock(focusedDockIdx);
        }
        sheetReturnFocus = null;
    }

    // ---------------------------------------------------------------- D-pad Key Handling in Scenes Mode

    boolean dispatchKey(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;

        if (sheetLayer != null) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                if (!down) closeSheet();
                return true;
            }
            return false;
        }

        if (moveDockIdx >= 0) {
            int cols = Math.max(1, currentDockCols);
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) moveDockBy(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) moveDockBy(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) moveDockBy(-cols);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) moveDockBy(cols);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (down) endDockMove();
                    return true;
                default:
                    return true;
            }
        }

        View f = host.getCurrentFocus();

        if (k == KeyEvent.KEYCODE_MENU && !down) {
            int dIdx = dockTiles.indexOf(f);
            if (dIdx >= 0 && dIdx < dockEntries.size()) {
                MainActivity.AppEntry a = dockEntries.get(dIdx);
                if (!GEAR_KEY.equals(a.key) && !MORE_KEY.equals(a.key)) {
                    showDockAppMenu(dIdx, a, f);
                    return true;
                }
            }
            host.showControlCenter();
            return true;
        }

        if (k == KeyEvent.KEYCODE_BACK) {
            if (down) {
                backDownInHome = true;
            } else if (backDownInHome) {
                backDownInHome = false;
                if (moreExpanded) {
                    toggleMoreApps();
                } else if (!isAnyDockTileFocused()) {
                    focusDock(focusedDockIdx);
                }
            }
            return true;
        }

        // Top Bar navigation
        boolean whoVisible = whoBar != null && whoBar.getVisibility() == View.VISIBLE;
        boolean wxVisible = weatherBtn != null && weatherBtn.getVisibility() == View.VISIBLE;
        if (f == whoBar || f == weatherBtn || f == moonBtn) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) {
                    if (f == whoBar) {
                        if (wxVisible) weatherBtn.requestFocus();
                        else moonBtn.requestFocus();
                    } else if (f == weatherBtn) {
                        moonBtn.requestFocus();
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (down) {
                    if (f == moonBtn) {
                        if (wxVisible) weatherBtn.requestFocus();
                        else if (whoVisible) whoBar.requestFocus();
                    } else if (f == weatherBtn && whoVisible) {
                        whoBar.requestFocus();
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) {
                    if (shelfBox.getVisibility() == View.VISIBLE && !shelfCards.isEmpty()) {
                        shelfCards.get(shelfCards.size() - 1).requestFocus();
                    } else {
                        focusDock(focusedDockIdx);
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) return true;
        }

        // Keep Watching Shelf navigation
        int sIdx = shelfCards.indexOf(f);
        if (sIdx >= 0) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (down && sIdx > 0) shelfCards.get(sIdx - 1).requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down && sIdx + 1 < shelfCards.size()) shelfCards.get(sIdx + 1).requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                if (down) moonBtn.requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) focusDock(focusedDockIdx);
                return true;
            }
        }

        // Bottom Dock navigation
        int dIdx = dockTiles.indexOf(f);
        if (dIdx >= 0) {
            int cols = Math.max(1, currentDockCols);
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (down && dIdx > 0) focusDock(dIdx - 1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down && dIdx + 1 < dockTiles.size()) focusDock(dIdx + 1);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                if (down) {
                    if (dIdx >= cols) {
                        focusDock(dIdx - cols);
                    } else if (shelfBox.getVisibility() == View.VISIBLE && !shelfCards.isEmpty()) {
                        int target = Math.min(shelfCards.size() - 1, dIdx * shelfCards.size() / cols);
                        shelfCards.get(target).requestFocus();
                    } else {
                        moonBtn.requestFocus();
                    }
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) {
                    int curRow = dIdx / cols;
                    int totalRows = (dockTiles.size() + cols - 1) / cols;
                    if (curRow + 1 < totalRows) {
                        focusDock(Math.min(dIdx + cols, dockTiles.size() - 1));
                    }
                }
                return true;
            }
        }

        return false;
    }
}
