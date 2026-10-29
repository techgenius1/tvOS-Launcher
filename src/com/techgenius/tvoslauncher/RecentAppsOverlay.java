package com.techgenius.tvoslauncher;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * tvOS Recent Apps Manager / App Switcher overlay (ported from tvOS_2.0.3-standard-release)
 * with blurry Liquid Glass UI. Can be opened over ANY app via Button Mapper
 * ({@link WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY}) or on the Home Screen.
 */
public final class RecentAppsOverlay {

    private static WindowManager activeWm;
    private static FrameLayout activeRoot;
    private static HorizontalScrollView activeScroll;
    private static LinearLayout activeRow;
    private static TextView activeRamText;
    private static View activeRamTrack;
    private static View activeRamFill;
    private static View activeEmptyView;
    private static View activeHintView;
    private static ObjectAnimator scrollAnimator;
    private static int currentScrollX;
    private static Bitmap cachedShadowBmp;
    private static Bitmap cachedSceneBlur;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final PathInterpolator SCROLL_EASE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);
    private static final DecelerateInterpolator ENTER_DECEL = new DecelerateInterpolator(1.8f);

    private static final Map<String, String> LOGO_ASSETS = new HashMap<>();
    static {
        LOGO_ASSETS.put("com.netflix.ninja", "com.netflix.ninja.png");
        LOGO_ASSETS.put("com.netflix.mediaclient", "com.netflix.ninja.png");
        LOGO_ASSETS.put("com.google.android.youtube.tv", "com.google.android.youtube.tv.png");
        LOGO_ASSETS.put("com.google.android.youtube.tvkids", "com.google.android.youtube.tv.png");
        LOGO_ASSETS.put("org.smarttube.stable", "com.google.android.youtube.tv.png");
        LOGO_ASSETS.put("com.liskovsoft.smarttubetv.beta", "com.google.android.youtube.tv.png");
        LOGO_ASSETS.put("com.teamsmart.videomanager.tv", "com.google.android.youtube.tv.png");
        LOGO_ASSETS.put("com.amazon.amazonvideo.livingroom", "com.amazon.amazonvideo.livingroom.png");
        LOGO_ASSETS.put("com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom.png");
        LOGO_ASSETS.put("com.disney.disneyplus", "com.disney.disneyplus.png");
        LOGO_ASSETS.put("in.startv.hotstar", "com.disney.disneyplus.png");
        LOGO_ASSETS.put("com.apple.atve.androidtv.appletv", "com.apple.atve.androidtv.appletv.png");
        LOGO_ASSETS.put("com.apple.atve.sony.appletv", "com.apple.atve.androidtv.appletv.png");
        LOGO_ASSETS.put("com.spotify.tv.android", "com.spotify.tv.android.png");
        LOGO_ASSETS.put("com.spotify.music", "com.spotify.tv.android.png");
        LOGO_ASSETS.put("com.wbd.stream", "com.wbd.stream.png");
        LOGO_ASSETS.put("com.hbo.hbonow", "com.wbd.stream.png");
        LOGO_ASSETS.put("tv.twitch.android.app", "tv.twitch.android.app.png");
        LOGO_ASSETS.put("tv.twitch.android.viewer", "tv.twitch.android.app.png");
        LOGO_ASSETS.put("com.cbs.ca", "com.cbs.ca.png");
        LOGO_ASSETS.put("com.cbs.ott", "com.cbs.ca.png");
        LOGO_ASSETS.put("au.net.abc.iview", "au.net.abc.iview.png");
        LOGO_ASSETS.put("com.sbs.ondemand.tv", "com.sbs.ondemand.tv.png");
        LOGO_ASSETS.put("au.com.ninenow.ctv", "au.com.ninenow.ctv.png");
        LOGO_ASSETS.put("com.swm.live", "com.swm.live.png");
        LOGO_ASSETS.put("au.com.tenplay", "au.com.tenplay.png");
        LOGO_ASSETS.put("au.com.foxtel.atv", "au.com.foxtel.atv.png");
        LOGO_ASSETS.put("au.com.kayosports.tv", "au.com.kayosports.tv.png");
        LOGO_ASSETS.put("au.com.stan.and", "au.com.stan.and.png");
        LOGO_ASSETS.put("au.com.streamotion.ares.tv", "au.com.streamotion.ares.tv.png");
    }

    private static final class RecentAppItem {
        String pkg;
        String label;
        ComponentName component;
        Drawable banner;
        Drawable icon;
    }

    private RecentAppsOverlay() { }

    public static boolean isShowing() {
        return activeRoot != null;
    }

    public static void toggle(Context c) {
        if (isShowing()) {
            dismiss();
            return;
        }
        ControlPanelOverlay.dismiss();
        show(c);
    }

    public static void show(Context c) {
        if (isShowing()) {
            dismiss();
        }
        ControlPanelOverlay.dismiss();
        PowerService svc = PowerService.instance;
        if (svc != null) {
            try {
                showWindowOverlay(svc);
                return;
            } catch (Exception ignored) {
            }
        }
        if (MainActivity.activeInstance != null && MainActivity.activeInstance.resumed) {
            MainActivity.activeInstance.runOnUiThread(() -> showInActivity(MainActivity.activeInstance));
            return;
        }
        Intent i = new Intent(c, MainActivity.class)
                .putExtra("panel", "recents")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            c.startActivity(i);
        } catch (Exception ignored) { }
    }

    public static void dismiss() {
        MAIN.removeCallbacksAndMessages(null);
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        if (activeRoot != null) {
            final FrameLayout root = activeRoot;
            final WindowManager wm = activeWm;
            activeRoot = null;
            activeWm = null;
            activeScroll = null;
            activeRow = null;
            activeRamText = null;
            activeRamTrack = null;
            activeRamFill = null;
            activeEmptyView = null;
            activeHintView = null;
            if (wm != null) {
                try {
                    wm.removeViewImmediate(root);
                } catch (Exception ignored) { }
            } else if (root.getParent() instanceof ViewGroup) {
                try {
                    ((ViewGroup) root.getParent()).removeView(root);
                } catch (Exception ignored) { }
            }
        }
    }

    /** Records an app package as recently opened/used (newest first, up to 12 apps). */
    public static void recordAppUsed(Context c, String pkg) {
        if (c == null || pkg == null || pkg.isEmpty()) return;
        String myPkg = c.getPackageName();
        if (pkg.equals(myPkg)
                || pkg.equals("com.android.systemui")
                || pkg.startsWith("com.google.android.inputmethod")
                || pkg.startsWith("com.android.inputmethod")) {
            return;
        }
        if (!isLaunchableApp(c, pkg)) return;

        // 1. Update SharedPreferences("recents") order & clear any old closed_ timestamp
        SharedPreferences recSp = c.getSharedPreferences("recents", Context.MODE_PRIVATE);
        List<String> order = new ArrayList<>();
        for (String p : recSp.getString("order", "").split(",")) {
            if (!p.isEmpty() && !p.equals(pkg)) order.add(p);
        }
        order.add(0, pkg);
        while (order.size() > 12) order.remove(order.size() - 1);
        recSp.edit()
                .putString("order", TextUtils.join(",", order))
                .remove("closed_" + pkg)
                .apply();

        // 2. Also keep SharedPreferences("clean") "recent" in sync for Control Panel memory clean
        SharedPreferences cleanSp = c.getSharedPreferences("clean", Context.MODE_PRIVATE);
        List<String> cleanList = new ArrayList<>(Arrays.asList(cleanSp.getString("recent", "").split(",")));
        cleanList.remove(pkg);
        cleanList.remove("");
        cleanList.add(0, pkg);
        while (cleanList.size() > 12) cleanList.remove(cleanList.size() - 1);
        cleanSp.edit().putString("recent", TextUtils.join(",", cleanList)).apply();
    }

    private static boolean isLaunchableApp(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            if (pm.getLeanbackLaunchIntentForPackage(pkg) != null) return true;
            if (pm.getLaunchIntentForPackage(pkg) != null) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private static List<String> getRecentPackages(Context c, int maxCount) {
        Set<String> result = new LinkedHashSet<>();
        String myPkg = c.getPackageName();
        SharedPreferences recSp = c.getSharedPreferences("recents", Context.MODE_PRIVATE);

        // 1. Saved recents order (from Accessibility window tracking + launcher launches)
        String savedOrder = recSp.getString("order", "");
        if (!savedOrder.isEmpty()) {
            for (String pkg : savedOrder.split(",")) {
                if (pkg.isEmpty() || pkg.equals(myPkg)) continue;
                if (isLaunchableApp(c, pkg)) {
                    result.add(pkg);
                    if (result.size() >= maxCount) return new ArrayList<>(result);
                }
            }
        }

        // 2. Saved "clean" recent list from MainActivity
        String cleanOrder = c.getSharedPreferences("clean", Context.MODE_PRIVATE).getString("recent", "");
        if (!cleanOrder.isEmpty()) {
            for (String pkg : cleanOrder.split(",")) {
                if (pkg.isEmpty() || pkg.equals(myPkg)) continue;
                long closedAt = recSp.getLong("closed_" + pkg, 0L);
                if (closedAt > 0) continue;
                if (isLaunchableApp(c, pkg)) {
                    result.add(pkg);
                    if (result.size() >= maxCount) return new ArrayList<>(result);
                }
            }
        }

        // 3. UsageStatsManager (matches I4.M in tvOS_2.0.3-standard-release)
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long now = System.currentTimeMillis();
                long weekAgo = now - 604800000L;
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, weekAgo, now);
                if (stats != null && !stats.isEmpty()) {
                    List<UsageStats> valid = new ArrayList<>();
                    for (UsageStats us : stats) {
                        if (us.getLastTimeUsed() <= 0 || us.getTotalTimeInForeground() <= 0) continue;
                        String pkg = us.getPackageName();
                        if (pkg == null || pkg.equals(myPkg)) continue;
                        long closedAt = recSp.getLong("closed_" + pkg, 0L);
                        if (us.getLastTimeUsed() <= closedAt) continue;
                        valid.add(us);
                    }
                    Collections.sort(valid, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
                    for (UsageStats us : valid) {
                        String pkg = us.getPackageName();
                        if (isLaunchableApp(c, pkg)) {
                            result.add(pkg);
                            if (result.size() >= maxCount) break;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }

        return new ArrayList<>(result);
    }

    private static List<RecentAppItem> resolveRecentItems(Context c, int maxCount) {
        List<RecentAppItem> items = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String pkg : getRecentPackages(c, maxCount)) {
            RecentAppItem item = resolvePackage(pm, pkg);
            if (item != null) items.add(item);
        }
        return items;
    }

    private static RecentAppItem resolvePackage(PackageManager pm, String pkg) {
        try {
            String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
            for (String cat : cats) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat).setPackage(pkg);
                List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
                if (list != null && !list.isEmpty()) {
                    ResolveInfo ri = list.get(0);
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null) continue;
                    RecentAppItem item = new RecentAppItem();
                    item.pkg = pkg;
                    item.label = String.valueOf(ri.loadLabel(pm));
                    item.component = new ComponentName(pkg, ai.name);
                    Drawable banner = ai.loadBanner(pm);
                    if (banner == null && ai.applicationInfo != null) {
                        banner = ai.applicationInfo.loadBanner(pm);
                    }
                    item.banner = banner;
                    item.icon = ri.loadIcon(pm);
                    return item;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static Bitmap getOverlayGlassBitmap(Context c, int screenW, int screenH) {
        try {
            if (MainActivity.activeInstance != null
                    && MainActivity.activeInstance.resumed
                    && MainActivity.activeInstance.root != null) {
                int w = Math.max(1, screenW / 4), h = Math.max(1, screenH / 4);
                Bitmap snap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(snap);
                cv.scale(w / (float) screenW, h / (float) screenH);
                MainActivity.activeInstance.root.draw(cv);
                Bitmap blurred = Ui.blur(snap, w / 2, h / 2, 4, 3);
                snap.recycle();
                if (blurred != null) return blurred;
            }
        } catch (Throwable ignored) { }

        if (cachedSceneBlur != null && !cachedSceneBlur.isRecycled()) {
            return cachedSceneBlur;
        }
        try {
            int w = Math.max(1, screenW / 4), h = Math.max(1, screenH / 4);
            List<String> ids = Scenes.getEnabledSceneIds(c);
            String bgAsset = (ids != null && !ids.isEmpty())
                    ? Scenes.byId(ids.get(0)).bgAsset
                    : "backgrounds/scene-night.jpg";
            Bitmap raw = Scenes.loadAssetBitmap(c, bgAsset, w, h);
            if (raw != null) {
                cachedSceneBlur = Ui.blur(raw, w / 2, h / 2, 4, 3);
                raw.recycle();
                return cachedSceneBlur;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void showWindowOverlay(Context svcCtx) {
        WindowManager wm = (WindowManager) svcCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        Point screen = Ui.screen(svcCtx);
        int screenW = screen.x, screenH = screen.y;
        float unit = Ui.unit(svcCtx);

        FrameLayout layer = buildRecentsLayer(svcCtx, screenW, screenH, unit);

        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.CENTER;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                wlp.setBlurBehindRadius(dp(unit, 32));
            } catch (Throwable ignored) { }
        }

        wm.addView(layer, wlp);
        activeWm = wm;
        activeRoot = layer;
        animateEntrance(unit);
    }

    public static void showInActivity(MainActivity act) {
        if (act == null || act.root == null) return;
        dismiss();
        FrameLayout layer = buildRecentsLayer(act, act.screenW, act.screenH, act.unit);
        act.root.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeWm = null;
        activeRoot = layer;
        animateEntrance(act.unit);
    }

    private static FrameLayout buildRecentsLayer(Context c, int screenW, int screenH, float unit) {
        FrameLayout root = new FrameLayout(c) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ev) {
                MainActivity.navSound(c, ev);
                int k = ev.getKeyCode();
                boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
                if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                    if (!down) dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            }
        };
        root.setClickable(true);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setOnClickListener(v -> dismiss());

        // 1. Liquid Glass Blurred Backdrop
        final Bitmap glassBmp = getOverlayGlassBitmap(c, screenW, screenH);
        if (glassBmp != null) {
            ImageView backdrop = new ImageView(c);
            backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
            backdrop.setImageBitmap(glassBmp);
            backdrop.setAlpha(0.88f);
            root.addView(backdrop, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        View scrim = new View(c);
        scrim.setBackgroundColor(0x94000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MainActivity.LiquidGlass.Backdrop glassBackdrop = () -> {
            if (MainActivity.activeInstance != null
                    && MainActivity.activeInstance.glassSource != null
                    && MainActivity.activeInstance.glassSource.get() != null) {
                return MainActivity.activeInstance.glassSource.get();
            }
            return glassBmp;
        };

        // 2. Top-Left Title: "Recent Apps"
        TextView title = new TextView(c);
        title.setText(I18n.s(c, "Recent Apps", "Son Uygulamalar"));
        title.setTextColor(0xE6FFFFFF);
        title.setTypeface(Ui.font(c, "semibold"));
        Ui.textSize(title, 20f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tlp.topMargin = dp(unit, 34);
        tlp.leftMargin = dp(unit, 55);
        root.addView(title, tlp);

        // 3. Top-Right RAM Usage Liquid Glass Pill
        LinearLayout ramPill = new LinearLayout(c);
        ramPill.setOrientation(LinearLayout.HORIZONTAL);
        ramPill.setGravity(Gravity.CENTER_VERTICAL);
        int pillH = dp(unit, 40);
        MainActivity.LiquidGlass pillGlass = new MainActivity.LiquidGlass(
                pillH / 2f, 0x28FFFFFF, glassBackdrop, screenW, screenH, unit).adaptive().darker(0x55);
        pillGlass.lens = dp(unit, 6);
        ramPill.setBackground(pillGlass);
        ramPill.setPadding(dp(unit, 16), 0, dp(unit, 16), 0);

        TextView ramText = new TextView(c);
        ramText.setTextColor(Color.WHITE);
        ramText.setTypeface(Ui.font(c, "medium"));
        Ui.textSize(ramText, 13f);
        ramPill.addView(ramText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout ramTrack = new FrameLayout(c);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(unit, 3));
        trackBg.setColor(0x4DFFFFFF);
        ramTrack.setBackground(trackBg);

        View ramFill = new View(c);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(dp(unit, 3));
        fillBg.setColor(Color.WHITE);
        ramFill.setBackground(fillBg);
        ramTrack.addView(ramFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams rklp = new LinearLayout.LayoutParams(dp(unit, 90), dp(unit, 6));
        rklp.leftMargin = dp(unit, 12);
        ramPill.addView(ramTrack, rklp);

        FrameLayout.LayoutParams rplp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, pillH, Gravity.TOP | Gravity.END);
        rplp.topMargin = dp(unit, 28);
        rplp.rightMargin = dp(unit, 55);
        root.addView(ramPill, rplp);

        activeRamText = ramText;
        activeRamTrack = ramTrack;
        activeRamFill = ramFill;
        updateRamUsage(c);

        // 4. Empty Text ("No recent apps yet")
        TextView emptyView = new TextView(c);
        emptyView.setText(I18n.s(c, "No recent apps yet", "Henüz son kullanılan uygulama yok"));
        emptyView.setTextColor(0xB3FFFFFF);
        emptyView.setTypeface(Ui.font(c, "medium"));
        Ui.textSize(emptyView, 16f);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(emptyView, elp);
        activeEmptyView = emptyView;

        // 5. HorizontalScrollView + recentsRow
        HorizontalScrollView scroll = new HorizontalScrollView(c) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                return 0; // Disable Android's automatic focus-snap so smoothScrollToCard controls scrolling
            }
        };
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        root.addView(scroll, slp);

        activeScroll = scroll;
        activeRow = row;

        // 6. Bottom Hint ("Press ▲ on an app to close it")
        TextView hintView = new TextView(c);
        hintView.setText(I18n.s(c, "Press ▲ on an app to close it", "Kapatmak için uygulamanın üzerinde ▲ tuşuna basın"));
        hintView.setTextColor(0x99FFFFFF);
        hintView.setTypeface(Ui.font(c, "medium"));
        Ui.textSize(hintView, 13f);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = dp(unit, 40);
        root.addView(hintView, hlp);
        activeHintView = hintView;

        // 7. Populate Recent App Cards + Close All button
        int cardW = Math.round(screenW * 0.44f);
        int cardH = cardW * 9 / 16;
        int sidePad = (screenW - cardW) / 2;
        scroll.setPadding(sidePad, 0, sidePad, 0);
        int vPad = dp(unit, 72);
        row.setPadding(0, vPad, 0, vPad);

        List<RecentAppItem> items = resolveRecentItems(c, 12);
        for (RecentAppItem item : items) {
            View card = buildRecentCard(c, item, cardW, cardH, screenW, screenH, unit);
            row.addView(card);
        }

        if (!items.isEmpty()) {
            View closeAllBtn = buildCloseAllButton(c, cardW, screenH, unit);
            row.addView(closeAllBtn);
        }

        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        hintView.setVisibility(empty ? View.GONE : View.VISIBLE);

        return root;
    }

    private static View buildRecentCard(
            Context c,
            RecentAppItem item,
            int cardW,
            int cardH,
            int screenW,
            int screenH,
            float unit) {

        LinearLayout container = new LinearLayout(c);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setFocusable(true);
        container.setClickable(true);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setTag(item);

        // Top header row: small 40x24 rounded badge + App Name
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        int badgeW = dp(unit, 40);
        int badgeH = dp(unit, 24);
        ImageView badgeIv = new ImageView(c);
        badgeIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap smallBmp = renderCardBitmap(c, item, badgeW * 2, badgeH * 2);
        badgeIv.setImageBitmap(smallBmp);
        final float badgeRad = dp(unit, 6);
        badgeIv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), badgeRad);
            }
        });
        badgeIv.setClipToOutline(true);
        header.addView(badgeIv, new LinearLayout.LayoutParams(badgeW, badgeH));

        TextView nameTv = new TextView(c);
        nameTv.setText(item.label);
        nameTv.setTextColor(Color.WHITE);
        nameTv.setTypeface(Ui.font(c, "semibold"));
        Ui.textSize(nameTv, 14f);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setAlpha(0f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                Math.max(dp(unit, 80), cardW - badgeW - dp(unit, 20)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = dp(unit, 10);
        header.addView(nameTv, nlp);

        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(unit, 12);
        hlp.leftMargin = dp(unit, 4);
        container.addView(header, hlp);

        // Card frame with drop shadow + rounded 16:9 artwork
        FrameLayout cardFrame = new FrameLayout(c);
        cardFrame.setClipChildren(false);
        cardFrame.setClipToPadding(false);

        int shadowPad = dp(unit, 72);
        ImageView shadowIv = new ImageView(c);
        shadowIv.setImageBitmap(getOrCreateShadowBitmap(cardW, cardH, shadowPad, unit));
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(
                cardW + shadowPad * 2, cardH + shadowPad * 2);
        shLp.leftMargin = -shadowPad;
        shLp.topMargin = -shadowPad;
        cardFrame.addView(shadowIv, shLp);

        ImageView cardIv = new ImageView(c);
        cardIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        final float cardRad = dp(unit, 30);
        cardIv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cardRad);
            }
        });
        cardIv.setClipToOutline(true);
        cardIv.setImageBitmap(renderCardBitmap(c, item, 640, 360));
        cardFrame.addView(cardIv, new FrameLayout.LayoutParams(cardW, cardH));

        container.addView(cardFrame, new LinearLayout.LayoutParams(cardW, cardH));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMarginEnd(Math.round(cardW * -0.06f));
        container.setLayoutParams(clp);

        container.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                updateDeckFocus(v, cardW, unit);
            }
        });

        container.setOnClickListener(v -> {
            dismiss();
            launchRecentApp(c, item);
        });

        container.setOnKeyListener((v, keyCode, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (activeRow == null) return false;
            int idx = activeRow.indexOfChild(v);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int nextIdx = idx + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (nextIdx >= 0 && nextIdx < activeRow.getChildCount()) {
                    activeRow.getChildAt(nextIdx).requestFocus();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                closeSingleCard(c, v, cardW, screenH, unit, true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
            return false;
        });

        return container;
    }

    private static View buildCloseAllButton(Context c, int cardW, int screenH, float unit) {
        LinearLayout btn = new LinearLayout(c);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER_HORIZONTAL);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setAlpha(0.7f);

        int circleSize = dp(unit, 84);
        FrameLayout circle = new FrameLayout(c);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(0x33FFFFFF);
        circleBg.setStroke(Math.max(1, dp(unit, 1)), 0x59FFFFFF);
        circle.setBackground(circleBg);

        int iconSize = dp(unit, 32);
        ImageView trashIv = new ImageView(c);
        trashIv.setImageBitmap(Glyphs.get("trash", iconSize, Color.WHITE));
        circle.addView(trashIv, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        btn.addView(circle, new LinearLayout.LayoutParams(circleSize, circleSize));

        TextView label = new TextView(c);
        label.setText(I18n.s(c, "Close All", "Tümünü Kapat"));
        label.setTextColor(Color.WHITE);
        label.setTypeface(Ui.font(c, "semibold"));
        Ui.textSize(label, 14f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(unit, 12);
        btn.addView(label, llp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(unit, 38);
        blp.setMarginStart(Math.round(cardW * 0.06f) + dp(unit, 40));
        btn.setLayoutParams(blp);

        btn.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate()
                    .scaleX(hasFocus ? 1.12f : 1f)
                    .scaleY(hasFocus ? 1.12f : 1f)
                    .alpha(hasFocus ? 1f : 0.7f)
                    .setDuration(220)
                    .start();
            circleBg.setColor(hasFocus ? 0xF2FFFFFF : 0x33FFFFFF);
            trashIv.setImageBitmap(Glyphs.get("trash", iconSize, hasFocus ? 0xFF1C1C1E : Color.WHITE));
            if (hasFocus) {
                updateDeckFocus(v, cardW, unit);
            }
        });

        btn.setOnKeyListener((v, keyCode, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && activeRow != null) {
                int idx = activeRow.indexOfChild(v);
                if (idx > 0) activeRow.getChildAt(idx - 1).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true;
            }
            return false;
        });

        btn.setOnClickListener(v -> closeAllCards(c, cardW, screenH, unit));
        return btn;
    }

    /** Exact tvOS App Switcher stacked-deck focus animation from Lm2 in tvOS_2.0.3-standard-release. */
    private static void updateDeckFocus(View focusedView, int cardW, float unit) {
        if (activeRow == null) return;
        int focusedIdx = activeRow.indexOfChild(focusedView);
        if (focusedIdx < 0) return;
        float negOverlap = (-cardW) * -0.06f; // +0.06 * cardW

        int count = activeRow.getChildCount();
        boolean isCloseAll = !(focusedView.getTag() instanceof RecentAppItem);
        // When Close All is focused, keep the app card deck anchored at the last app card
        // so cards stay on the left and swipe left with the scroll view instead of sliding behind Close All.
        int deckAnchorIdx = isCloseAll ? Math.max(0, count - 2) : focusedIdx;

        for (int i = 0; i < count; i++) {
            View child = activeRow.getChildAt(i);
            if (!(child.getTag() instanceof RecentAppItem)) {
                // Ensure Close All button glides back to translationX = 0f after a card is removed
                child.animate()
                        .translationX(0f)
                        .setDuration(380)
                        .setInterpolator(SCROLL_EASE)
                        .start();
                continue;
            }
            int rel = i - deckAnchorIdx;
            int dist = Math.abs(rel);
            float headerAlpha = (!isCloseAll && dist == 0) ? 1f : 0f;
            float transX = 0f;
            if (rel < 0) {
                float d = (float) dist;
                float shift = (0.12f * d + 0.25f) * cardW;
                transX = (cardW + negOverlap) * d - shift;
            }
            int zDp = (!isCloseAll && dist == 0) ? 40 : (20 - Math.min(9, dist) * 2);
            child.setTranslationZ(zDp * unit);
            child.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .translationX(transX)
                    .setDuration(380)
                    .setInterpolator(SCROLL_EASE)
                    .start();

            if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof ViewGroup) {
                    ViewGroup hdr = (ViewGroup) vg.getChildAt(0);
                    if (hdr.getChildCount() > 1) {
                        hdr.getChildAt(1).setAlpha(headerAlpha);
                    }
                }
            }
        }
        smoothScrollToCard(focusedView, focusedIdx, cardW, unit);
    }

    private static void smoothScrollToCard(View card, int focusedIdx, int cardW, float unit) {
        if (activeScroll == null || card == null || focusedIdx < 0) return;
        int step = cardW + Math.round(cardW * -0.06f);
        int targetX;
        if (card.getTag() instanceof RecentAppItem) {
            targetX = Math.max(0, focusedIdx * step);
        } else {
            int btnLeft = focusedIdx * step + Math.round(cardW * 0.06f) + dp(unit, 40);
            int btnW = card.getWidth() > 0 ? card.getWidth() : dp(unit, 84);
            int scrollW = activeScroll.getWidth() > 0 ? activeScroll.getWidth() : (cardW + activeScroll.getPaddingLeft() * 2);
            targetX = Math.max(0, btnLeft + btnW / 2 - scrollW / 2 + activeScroll.getPaddingLeft());
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        if (Math.abs(targetX - currentScrollX) <= 1) {
            currentScrollX = targetX;
            activeScroll.scrollTo(targetX, 0);
            return;
        }
        activeScroll.scrollTo(currentScrollX, 0);
        scrollAnimator = ObjectAnimator.ofInt(activeScroll, "scrollX", currentScrollX, targetX);
        scrollAnimator.addUpdateListener(anim -> {
            Object val = anim.getAnimatedValue();
            if (val instanceof Integer) currentScrollX = (Integer) val;
        });
        scrollAnimator.setDuration(380);
        scrollAnimator.setInterpolator(SCROLL_EASE);
        scrollAnimator.start();
    }

    private static void closeSingleCard(Context c, View cardView, int cardW, int screenH, float unit, boolean refocusAfter) {
        if (activeRow == null || !(cardView.getTag() instanceof RecentAppItem)) return;
        RecentAppItem item = (RecentAppItem) cardView.getTag();
        cardView.setTag(null);
        final int idx = activeRow.indexOfChild(cardView);
        final int step = cardW + Math.round(cardW * -0.06f);

        cardView.animate()
                .translationY(-screenH * 0.62f)
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(250)
                .setInterpolator(new AccelerateInterpolator(1.35f))
                .withEndAction(() -> {
                    if (activeRow == null) return;
                    // Prevent Android ViewGroup from auto-jumping focus to child 0 during removeView
                    activeRow.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    activeRow.removeView(cardView);
                    activeRow.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

                    int lastAppIdx = -1;
                    for (int i = 0; i < activeRow.getChildCount(); i++) {
                        if (activeRow.getChildAt(i).getTag() instanceof RecentAppItem) {
                            lastAppIdx = i;
                        }
                    }
                    if (lastAppIdx < 0) {
                        activeRow.removeAllViews();
                        if (activeHintView != null) {
                            activeHintView.setVisibility(View.GONE);
                        }
                        if (activeEmptyView != null) {
                            activeEmptyView.setAlpha(0f);
                            activeEmptyView.setVisibility(View.VISIBLE);
                            activeEmptyView.animate().alpha(1f).setDuration(200).start();
                        }
                        if (activeRoot != null) {
                            activeRoot.setFocusable(true);
                            activeRoot.requestFocus();
                        }
                        final Context appCtx = c.getApplicationContext();
                        MAIN.postDelayed(() -> dismissAndReturnHome(appCtx), 1500L);
                        return;
                    }
                    if (refocusAfter) {
                        // Offset cards that shifted left by +step so they smoothly glide into place
                        for (int i = idx; i < activeRow.getChildCount(); i++) {
                            View shifted = activeRow.getChildAt(i);
                            shifted.animate().cancel();
                            shifted.setTranslationX(shifted.getTranslationX() + step);
                        }
                        int nextIdx = Math.min(idx, lastAppIdx);
                        View nextChild = activeRow.getChildAt(nextIdx);
                        if (nextChild != null) {
                            nextChild.requestFocus();
                            updateDeckFocus(nextChild, cardW, unit);
                        }
                    }
                })
                .start();

        removeAndKillApp(c, item.pkg);
        MAIN.postDelayed(() -> updateRamUsage(c), 600);
    }

    private static void closeAllCards(Context c, int cardW, int screenH, float unit) {
        if (activeRow == null) return;
        int total = activeRow.getChildCount();
        int lastAppIdx = total - 2;
        List<String> allPkgs = new ArrayList<>();
        for (int i = lastAppIdx; i >= 0; i--) {
            final View child = activeRow.getChildAt(i);
            if (child.getTag() instanceof RecentAppItem) {
                allPkgs.add(((RecentAppItem) child.getTag()).pkg);
            }
            long delay = (long) (lastAppIdx - i) * 70L;
            MAIN.postDelayed(() -> closeSingleCard(c, child, cardW, screenH, unit, false), delay);
        }
        if (!allPkgs.isEmpty()) {
            LocalAdb.forceStopPackagesAsync(c, allPkgs);
        } else {
            final Context appCtx = c.getApplicationContext();
            MAIN.postDelayed(() -> dismissAndReturnHome(appCtx), 1500L);
        }
    }

    private static void dismissAndReturnHome(Context c) {
        dismiss();
        if (c == null) return;
        if (MainActivity.activeInstance != null && MainActivity.activeInstance.resumed) {
            return;
        }
        try {
            Intent home = new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(home);
        } catch (Exception ignored) { }
    }

    private static void removeAndKillApp(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        long now = System.currentTimeMillis();

        SharedPreferences recSp = c.getSharedPreferences("recents", Context.MODE_PRIVATE);
        List<String> order = new ArrayList<>();
        for (String p : recSp.getString("order", "").split(",")) {
            if (!p.isEmpty() && !p.equals(pkg)) order.add(p);
        }
        recSp.edit()
                .putString("order", TextUtils.join(",", order))
                .putLong("closed_" + pkg, now)
                .apply();

        SharedPreferences cleanSp = c.getSharedPreferences("clean", Context.MODE_PRIVATE);
        List<String> cleanList = new ArrayList<>(Arrays.asList(cleanSp.getString("recent", "").split(",")));
        cleanList.remove(pkg);
        cleanList.remove("");
        cleanSp.edit().putString("recent", TextUtils.join(",", cleanList)).apply();

        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
            }
        } catch (Exception ignored) { }

        LocalAdb.forceStopPackagesAsync(c, Collections.singletonList(pkg));
    }

    private static void launchRecentApp(Context c, RecentAppItem item) {
        if (item == null || item.pkg == null) return;
        recordAppUsed(c, item.pkg);
        PackageManager pm = c.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(item.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(item.pkg);
        if (launch == null && item.component != null) {
            launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(item.component);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                c.startActivity(launch);
            } catch (Exception ignored) { }
        }
    }

    private static void updateRamUsage(Context c) {
        if (activeRamText == null || activeRamTrack == null || activeRamFill == null) return;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long usedBytes = Math.max(0L, mi.totalMem - mi.availMem);
            double gb = 1073741824.0;
            String usedStr = String.format(Locale.US, "%.1f GB", usedBytes / gb);
            String totalStr = String.format(Locale.US, "%.1f GB", mi.totalMem / gb);
            activeRamText.setText(I18n.s(c,
                    "RAM " + usedStr + " used of " + totalStr,
                    "RAM " + totalStr + " içinde " + usedStr + " kullanımda"));
            activeRamTrack.post(() -> {
                if (activeRamTrack == null || activeRamFill == null) return;
                int w = activeRamTrack.getWidth();
                float frac = mi.totalMem > 0 ? (float) usedBytes / (float) mi.totalMem : 0f;
                frac = Math.max(0f, Math.min(1f, frac));
                ViewGroup.LayoutParams lp = activeRamFill.getLayoutParams();
                lp.width = Math.round(w * frac);
                activeRamFill.setLayoutParams(lp);
            });
        } catch (Exception ignored) { }
    }

    private static void animateEntrance(float unit) {
        if (activeRoot == null) return;
        currentScrollX = 0;
        if (activeScroll != null) activeScroll.scrollTo(0, 0);
        activeRoot.setAlpha(0f);
        activeRoot.animate()
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(SCROLL_EASE)
                .start();
        if (activeRow != null) {
            int count = activeRow.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = activeRow.getChildAt(i);
                child.setTranslationY(dp(unit, 70));
                child.animate()
                        .translationY(0f)
                        .setStartDelay(i * 35L)
                        .setDuration(420)
                        .setInterpolator(ENTER_DECEL)
                        .start();
            }
            if (count > 0) {
                activeRow.post(() -> {
                    if (activeRow != null && activeRow.getChildCount() > 0) {
                        activeRow.getChildAt(0).requestFocus();
                    }
                });
            }
        }
    }

    private static Bitmap getOrCreateShadowBitmap(int cardW, int cardH, int pad, float unit) {
        int fullW = cardW + pad * 2;
        int fullH = cardH + pad * 2;
        if (cachedShadowBmp != null
                && !cachedShadowBmp.isRecycled()
                && cachedShadowBmp.getWidth() == fullW
                && cachedShadowBmp.getHeight() == fullH) {
            return cachedShadowBmp;
        }
        Bitmap bmp = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x8C000000);
        p.setMaskFilter(new BlurMaskFilter(pad * 0.6f, BlurMaskFilter.Blur.NORMAL));
        float ox = pad * 0.18f;
        float oy = pad * 0.12f;
        RectF rf = new RectF(pad + ox, pad + oy, pad + cardW - ox, pad + cardH + oy);
        float rad = dp(unit, 22);
        cv.drawRoundRect(rf, rad, rad, p);
        cachedShadowBmp = bmp;
        return bmp;
    }

    private static Bitmap renderCardBitmap(Context c, RecentAppItem item, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);

        // 1. Check if we have a crisp HD app-logo asset or 16:9 banner
        String logoAsset = LOGO_ASSETS.get(item.pkg);
        if (logoAsset != null) {
            Bitmap logo = Scenes.loadAssetBitmap(c, "app-logos/" + logoAsset, 256, 256);
            if (logo != null) {
                int edgeCol = sampleEdgeColor(logo);
                cv.drawColor(edgeCol);
                int s = Math.round(h * 0.65f);
                Rect dst = new Rect((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
                cv.drawBitmap(logo, null, dst, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                logo.recycle();
                return out;
            }
        }

        if (item.banner != null) {
            drawCenterCropDrawable(cv, item.banner, w, h);
            return out;
        }

        // 2. Fallback: vivid tvOS gradient in the app's dominant color + centered icon
        int baseCol = dominantColor(item.icon);
        float[] hsv = new float[3];
        Color.colorToHSV(baseCol, hsv);
        float sat = hsv[1] < 0.15f ? 0.12f : 0.72f;
        int topCol = Color.HSVToColor(new float[]{hsv[0], sat * 0.85f, 0.78f});
        int botCol = Color.HSVToColor(new float[]{(hsv[0] + 8f) % 360f, Math.min(1f, sat * 1.1f), 0.42f});
        Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        bgP.setShader(new LinearGradient(0, 0, 0, h, topCol, botCol, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, bgP);

        if (item.icon != null) {
            int s = Math.round(h * 0.46f);
            Rect old = item.icon.copyBounds();
            item.icon.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
            item.icon.draw(cv);
            item.icon.setBounds(old);
        }
        return out;
    }

    private static void drawCenterCropDrawable(Canvas cv, Drawable d, int w, int h) {
        int dw = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 16;
        int dh = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 9;
        float scale = Math.max((float) w / dw, (float) h / dh);
        int sw = Math.round(dw * scale);
        int sh = Math.round(dh * scale);
        int left = (w - sw) / 2;
        int top = (h - sh) / 2;
        Rect old = d.copyBounds();
        d.setBounds(left, top, left + sw, top + sh);
        d.draw(cv);
        d.setBounds(old);
    }

    private static int sampleEdgeColor(Bitmap b) {
        if (b == null || b.getWidth() < 4 || b.getHeight() < 4) return 0xFF1C1C1E;
        int p = b.getPixel(b.getWidth() / 2, 2);
        if (Color.alpha(p) > 200) return p;
        return 0xFF1C1C1E;
    }

    private static int dominantColor(Drawable d) {
        if (d == null) return 0xFF2C2C2E;
        Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        Rect old = d.copyBounds();
        d.setBounds(0, 0, 16, 16);
        d.draw(new Canvas(b));
        d.setBounds(old);
        long r = 0, g = 0, bl = 0, n = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
                n++;
            }
        }
        b.recycle();
        if (n == 0) return 0xFF2C2C2E;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    private static int dp(float unit, float v) {
        return Math.round(v * unit);
    }
}
