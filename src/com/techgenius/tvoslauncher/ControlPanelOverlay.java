package com.techgenius.tvoslauncher;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * System-wide Control Panel overlay that can be displayed over ANY app using
 * {@link WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY} when {@link PowerService} is active,
 * or inside {@link MainActivity} when on the Home Screen.
 *
 * Layout order:
 * 1. Wi-Fi          | 2. Bluetooth
 * 3. Memory         | 4. Do Not Disturb
 * 5. Audio          | 6. Sleep Timer
 * 7. Ambient Mode   | 8. Power Off
 * 9..12. 1x1 Icons: Inputs, Button Mapper, Settings, Search
 */
public final class ControlPanelOverlay {

    private static WindowManager activeWm;
    private static FrameLayout activeRoot;
    private static List<View> activeCells = new ArrayList<>();
    private static View memBarFill;
    private static android.graphics.Bitmap cachedSceneBlur;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Status {
        String get();
    }

    private ControlPanelOverlay() { }

    public static boolean isShowing() {
        return activeRoot != null;
    }

    public static void toggle(Context c) {
        if (isShowing()) {
            dismiss();
            return;
        }
        if (MainActivity.activeInstance != null
                && MainActivity.activeInstance.resumed
                && MainActivity.activeInstance.pickerLayer != null) {
            MainActivity.activeInstance.closeProfilePicker();
            return;
        }
        show(c);
    }

    public static void show(Context c) {
        if (isShowing()) {
            dismiss();
        }
        if (MainActivity.activeInstance != null && MainActivity.activeInstance.resumed) {
            MainActivity.activeInstance.runOnUiThread(() -> MainActivity.activeInstance.showControlCenter());
            return;
        }
        PowerService svc = PowerService.instance;
        if (svc != null) {
            try {
                showWindowOverlay(svc);
                return;
            } catch (Exception ignored) {
                // Fall back to MainActivity panel
            }
        }
        Intent i = new Intent(c, MainActivity.class)
                .putExtra("panel", "cc")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            c.startActivity(i);
        } catch (Exception ignored) { }
    }

    public static void dismiss() {
        if (activeRoot != null && activeWm != null) {
            FrameLayout root = activeRoot;
            WindowManager wm = activeWm;
            activeRoot = null;
            activeWm = null;
            activeCells.clear();
            memBarFill = null;
            try {
                wm.removeViewImmediate(root);
            } catch (Exception ignored) { }
        }
    }

    private static void dismissAll(Context c) {
        dismiss();
        if (c instanceof MainActivity) {
            ((MainActivity) c).closeProfilePicker();
        }
    }

    private interface LiveBlurCallback {
        void onResult(android.graphics.Bitmap blurred);
    }

    private static android.graphics.Bitmap getOverlayGlassBitmap(Context svcCtx, int screenW, int screenH) {
        try {
            if (MainActivity.activeInstance != null
                    && MainActivity.activeInstance.resumed
                    && MainActivity.activeInstance.root != null) {
                int w = Math.max(1, screenW / 4), h = Math.max(1, screenH / 4);
                android.graphics.Bitmap snap = android.graphics.Bitmap.createBitmap(
                        w, h, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(snap);
                cv.scale(w / (float) screenW, h / (float) screenH);
                MainActivity.activeInstance.root.draw(cv);
                android.graphics.Bitmap blurred = Ui.blur(snap, w / 2, h / 2, 4, 3);
                snap.recycle();
                if (blurred != null) return blurred;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void captureLiveScreenBlurAsync(
            android.accessibilityservice.AccessibilityService svc,
            final int screenW,
            final int screenH,
            final LiveBlurCallback cb) {
        if (android.os.Build.VERSION.SDK_INT < 30 || svc == null) {
            cb.onResult(null);
            return;
        }
        try {
            svc.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                    new android.accessibilityservice.AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(android.accessibilityservice.AccessibilityService.ScreenshotResult res) {
                            android.graphics.Bitmap blurred = null;
                            try {
                                android.hardware.HardwareBuffer hb = res.getHardwareBuffer();
                                if (hb != null) {
                                    android.graphics.Bitmap hwBmp = android.graphics.Bitmap.wrapHardwareBuffer(
                                            hb, res.getColorSpace());
                                    hb.close();
                                    if (hwBmp != null) {
                                        int w = Math.max(1, screenW / 4);
                                        int h = Math.max(1, screenH / 4);
                                        android.graphics.Bitmap sw = hwBmp.copy(
                                                android.graphics.Bitmap.Config.ARGB_8888, false);
                                        hwBmp.recycle();
                                        if (sw != null) {
                                            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(
                                                    sw, w, h, true);
                                            if (scaled != sw) sw.recycle();
                                            blurred = Ui.blur(scaled, w / 2, h / 2, 4, 3);
                                            scaled.recycle();
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                            final android.graphics.Bitmap finalBlurred = blurred;
                            MAIN.post(() -> cb.onResult(finalBlurred));
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            MAIN.post(() -> cb.onResult(null));
                        }
                    });
        } catch (Throwable t) {
            cb.onResult(null);
        }
    }

    private static void showWindowOverlay(Context svcCtx) {
        WindowManager wm = (WindowManager) svcCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        Point screen = Ui.screen(svcCtx);
        int screenW = screen.x, screenH = screen.y;
        float unit = Ui.unit(svcCtx);
        boolean blurryMode = isBlurryBgMode(svcCtx);

        FrameLayout layer = new FrameLayout(svcCtx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ev) {
                MainActivity.navSound(svcCtx, ev);
                int k = ev.getKeyCode();
                boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
                if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                    if (!down) dismiss();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                        || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (down) moveFocusInOverlay(svcCtx, k);
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            }
        };
        layer.setClickable(true);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        layer.setOnClickListener(v -> dismiss());

        final android.graphics.Bitmap[] liveBlurHolder = new android.graphics.Bitmap[]{
                getOverlayGlassBitmap(svcCtx, screenW, screenH)
        };
        // Video is playing underneath (or will be): a static screenshot would freeze it, and DRM /
        // hardware-overlay video captures as pure black. Keep the live video visible instead.
        final boolean videoPlaying = liveBlurHolder[0] == null && isMediaPlaying(svcCtx);
        final ImageView blurBg;
        if (blurryMode && !videoPlaying) {
            blurBg = new ImageView(svcCtx);
            blurBg.setScaleType(ImageView.ScaleType.FIT_XY);
            if (liveBlurHolder[0] != null) {
                blurBg.setImageBitmap(liveBlurHolder[0]);
            } else {
                blurBg.setBackgroundColor(0x780B0D14);
            }
            blurBg.setColorFilter(0x74000000, android.graphics.PorterDuff.Mode.SRC_OVER);
            layer.addView(blurBg, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (blurryMode) {
            // Live video stays playing behind; a soft scrim toward the panel keeps tiles legible.
            // FLAG_BLUR_BEHIND (below) additionally blurs the live video on devices that support it.
            blurBg = null;
            View shade = new View(svcCtx);
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.RIGHT_LEFT,
                    new int[]{0x8C000000, 0x4D000000, 0x26000000}));
            layer.addView(shade, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            blurBg = null;
            View shade = new View(svcCtx);
            shade.setBackgroundColor(0x36000000);
            layer.addView(shade, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        MainActivity.LiquidGlass.Backdrop overlayBackdrop = () -> {
            if (MainActivity.activeInstance != null
                    && MainActivity.activeInstance.resumed
                    && MainActivity.activeInstance.glassSource != null
                    && MainActivity.activeInstance.glassSource.get() != null) {
                return MainActivity.activeInstance.glassSource.get();
            }
            return liveBlurHolder[0];
        };

        List<View> cells = new ArrayList<>();
        FrameLayout panel = buildControlPanelView(
                svcCtx, screenW, screenH, unit, overlayBackdrop, cells,
                () -> {
                    dismiss();
                    openInputs(svcCtx, () -> showInputsOverlay(svcCtx));
                },
                () -> {
                    dismiss();
                    startExternal(svcCtx, new Intent(svcCtx, SettingsActivity.class)
                            .putExtra("page", SettingsActivity.BUTTON_MAPPER));
                },
                () -> {
                    dismiss();
                    showSettingsGearPopupOverlay(svcCtx);
                },
                () -> {
                    dismiss();
                    if (!startExternal(svcCtx, new Intent(Intent.ACTION_ASSIST))) {
                        startExternal(svcCtx, new Intent("android.search.action.GLOBAL_SEARCH"));
                    }
                });

        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        plp.topMargin = dp(unit, 58);
        plp.rightMargin = dp(unit, 24);
        layer.addView(panel, plp);

        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (blurryMode && android.os.Build.VERSION.SDK_INT >= 31) {
            flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.END;
        if (blurryMode && android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                wlp.setBlurBehindRadius(dp(unit, 48));
            } catch (Throwable ignored) {}
        }

        wm.addView(layer, wlp);
        activeWm = wm;
        activeRoot = layer;
        activeCells = cells;

        layer.setAlpha(0f);
        panel.setTranslationY(-Math.round(10 * unit));
        if (!cells.isEmpty()) cells.get(0).requestFocus();

        final boolean[] revealed = {false};
        final Runnable revealNow = () -> {
            if (revealed[0] || activeRoot != layer) return;
            revealed[0] = true;
            layer.animate().alpha(1f).setDuration(180).start();
            panel.animate().translationY(0).setDuration(240).setInterpolator(MainActivity.EASE).start();
        };

        if (blurryMode
                && !videoPlaying
                && liveBlurHolder[0] == null
                && android.os.Build.VERSION.SDK_INT >= 30
                && svcCtx instanceof android.accessibilityservice.AccessibilityService) {
            MAIN.postDelayed(revealNow, 85);
            captureLiveScreenBlurAsync((android.accessibilityservice.AccessibilityService) svcCtx, screenW, screenH, blurred -> {
                if (activeRoot != layer) return;
                if (blurred != null && blurBg != null && !isMostlyBlack(blurred)) {
                    liveBlurHolder[0] = blurred;
                    blurBg.setBackgroundColor(Color.TRANSPARENT);
                    blurBg.setImageBitmap(blurred);
                    for (int i = 0; i < cells.size(); i++) {
                        cells.get(i).invalidate();
                    }
                } else if (blurBg != null) {
                    // Protected / hardware-overlay video captures as black: show the live video
                    // through a soft scrim instead of covering it with a black frame.
                    if (blurred != null) blurred.recycle();
                    blurBg.setImageDrawable(null);
                    blurBg.clearColorFilter();
                    blurBg.setBackground(new GradientDrawable(
                            GradientDrawable.Orientation.RIGHT_LEFT,
                            new int[]{0x8C000000, 0x4D000000, 0x26000000}));
                }
                revealNow.run();
            });
        } else {
            revealNow.run();
        }
    }

    /** True when the screen is likely showing video (media session / audio playback active). */
    private static boolean isMediaPlaying(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    /** Screenshots of DRM / secure video surfaces come back black. */
    private static boolean isMostlyBlack(android.graphics.Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            int dark = 0, total = 0;
            for (int y = h / 10; y < h; y += Math.max(1, h / 12)) {
                for (int x = w / 10; x < w; x += Math.max(1, w / 16)) {
                    int p = b.getPixel(x, y);
                    int lum = (Color.red(p) * 2126 + Color.green(p) * 7152 + Color.blue(p) * 722) / 10000;
                    if (lum < 10) dark++;
                    total++;
                }
            }
            return total > 0 && dark >= total * 0.9f;
        } catch (Throwable t) {
            return false;
        }
    }

    private static android.graphics.drawable.Drawable createControlTileBg(
            Context c,
            float radius,
            float unit,
            MainActivity.LiquidGlass.Backdrop glassSource,
            int screenW,
            int screenH) {
        MainActivity.LiquidGlass.Backdrop bgSrc = glassSource != null ? glassSource : () -> null;
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                radius, 0x24FFFFFF, bgSrc, screenW, screenH, unit).adaptive().darker(0x59);
        glass.lens = dp(unit, 7);
        return glass;
    }

    /**
     * Builds the unified Control Panel view used both by {@link MainActivity#showControlCenter()}
     * and the system-wide Accessibility Overlay.
     */
    public static FrameLayout buildControlPanelView(
            Context c,
            int screenW,
            int screenH,
            float unit,
            MainActivity.LiquidGlass.Backdrop glassSource,
            List<View> outCells,
            Runnable onInputs,
            Runnable onButtonMapper,
            Runnable onSettings,
            Runnable onSearch) {

        int col = dp(unit, 141);
        int gap = dp(unit, 11);
        int cap = dp(unit, 56);
        int small = (2 * col + gap - 3 * gap) / 4;
        int w = 2 * col + gap;
        int pad = 0;

        FrameLayout panel = new FrameLayout(c);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        final class CapsuleSpec {
            final String glyph;
            final String title;
            final Status status;
            final View.OnClickListener click;
            final boolean isMemory;

            CapsuleSpec(String glyph, String title, Status status, View.OnClickListener click, boolean isMemory) {
                this.glyph = glyph;
                this.title = title;
                this.status = status;
                this.click = click;
                this.isMemory = isMemory;
            }
        }

        List<CapsuleSpec> capsules = new ArrayList<>();
        if (isItemEnabled(c, ITEM_WIFI)) {
            capsules.add(new CapsuleSpec("wifi", "Wi-Fi", () -> wifiState(c), v -> {
                dismissAll(c);
                openWifiSettings(c);
            }, false));
        }
        if (isItemEnabled(c, ITEM_BLUETOOTH)) {
            capsules.add(new CapsuleSpec("bluetooth", "Bluetooth", () -> bluetoothState(c), v -> {
                dismissAll(c);
                openBluetoothSettings(c);
            }, false));
        }
        if (isItemEnabled(c, ITEM_MEMORY)) {
            capsules.add(new CapsuleSpec("memory", I18n.s(c, "Memory", "Bellek"), () -> memoryState(c), v -> {
                dismissAll(c);
                RecentAppsOverlay.show(c);
            }, true));
        }
        if (isItemEnabled(c, ITEM_DND)) {
            capsules.add(new CapsuleSpec("moon", I18n.s(c, "Do Not Disturb", "Rahatsız Etmeyin"),
                    () -> dndState(c), v -> toggleDnd(c), false));
        }
        if (isItemEnabled(c, ITEM_AUDIO)) {
            capsules.add(new CapsuleSpec("audio", I18n.s(c, "Audio", "Ses"), () -> audioState(c), v -> {
                dismissAll(c);
                openSoundSettings(c);
            }, false));
        }
        if (isItemEnabled(c, ITEM_TIMER)) {
            capsules.add(new CapsuleSpec("timer", I18n.s(c, "Sleep Timer", "Uyku Zamanlayıcısı"),
                    () -> timerState(c), v -> cycleSleepTimer(c), false));
        }
        if (isItemEnabled(c, ITEM_AMBIENT)) {
            boolean lockMode = isAmbientLockScreenMode(c);
            capsules.add(new CapsuleSpec("ambient",
                    lockMode ? I18n.s(c, "Lock Screen", "Kilit Ekranı") : I18n.s(c, "Ambient Mode", "Ortam Modu"),
                    () -> lockMode ? I18n.s(c, "Lock Now", "Şimdi Kilitle") : I18n.s(c, "Screensaver", "Ekran Koruyucu"),
                    v -> {
                        dismissAll(c);
                        startAmbientMode(c);
                    }, false));
        }
        if (isItemEnabled(c, ITEM_POWER)) {
            capsules.add(new CapsuleSpec("power", I18n.s(c, "Power Off", "Kapat"),
                    () -> I18n.s(c, "Screen Off", "Ekranı Kapat"),
                    v -> {
                        dismissAll(c);
                        SleepReceiver.sleepNow(c);
                    }, false));
        }

        for (int i = 0; i < capsules.size(); i++) {
            CapsuleSpec cs = capsules.get(i);
            int colIdx = i % 2;
            int rowIdx = i / 2;
            int x = pad + colIdx * (col + gap);
            int y = pad + rowIdx * (cap + gap);
            View tile = makeTile(c, panel, x, y, col, cap, unit, screenW, screenH, glassSource,
                    cs.glyph, cs.title, cs.status, cs.click);
            if (cs.isMemory) {
                attachMemoryBar(c, tile, unit);
            }
            outCells.add(tile);
        }

        int capsuleRows = (capsules.size() + 1) / 2;
        int y4 = capsuleRows > 0 ? capsuleRows * (cap + gap) : 0;

        String[] itemKeys = {ITEM_INPUTS, ITEM_MAPPER, ITEM_SETTINGS, ITEM_SEARCH};
        String[] icons = {"input", "remote", "gear", "search"};
        Runnable[] actions = {onInputs, onButtonMapper, onSettings, onSearch};
        List<Integer> enabledSmallIdx = new ArrayList<>();
        for (int i = 0; i < itemKeys.length; i++) {
            if (isItemEnabled(c, itemKeys[i])) {
                enabledSmallIdx.add(i);
            }
        }
        if (capsules.isEmpty() && enabledSmallIdx.isEmpty()) {
            // Never allow a completely empty panel; keep Settings accessible
            enabledSmallIdx.add(2);
        }
        for (int sIdx = 0; sIdx < enabledSmallIdx.size(); sIdx++) {
            int i = enabledSmallIdx.get(sIdx);
            final Runnable act = actions[i];
            outCells.add(makeTile(c, panel, pad + sIdx * (small + gap), pad + y4, small, small, unit, screenW, screenH, glassSource,
                    icons[i], null, null, v -> {
                        if (act != null) act.run();
                    }));
        }

        int totalH = !enabledSmallIdx.isEmpty()
                ? (y4 + small)
                : Math.max(0, capsuleRows * (cap + gap) - gap);

        if (isItemEnabled(c, ITEM_WEATHER_PANEL)) {
            int yWeather = totalH > 0 ? (totalH + gap) : 0;
            int weatherH = dp(unit, 66);
            View weatherTile = makeWeatherPanelTile(c, panel, pad, pad + yWeather, w, weatherH, unit, screenW, screenH, glassSource);
            outCells.add(weatherTile);
            totalH = yWeather + weatherH;
        }
        if (totalH <= 0) totalH = cap;

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(w + 2 * pad, totalH + 2 * pad, Gravity.TOP | Gravity.END);
        plp.topMargin = dp(unit, 36);
        plp.rightMargin = dp(unit, 28);
        panel.setLayoutParams(plp);
        return panel;
    }

    private static View makeWeatherPanelTile(
            final Context c,
            FrameLayout panel,
            int x,
            int y,
            int w,
            int h,
            final float unit,
            int screenW,
            int screenH,
            MainActivity.LiquidGlass.Backdrop glassSource) {

        float radius = dp(unit, 18);
        final android.graphics.drawable.Drawable idleBg =
                createControlTileBg(c, radius, unit, glassSource, screenW, screenH);

        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(idleBg);
        bar.setFocusable(true);
        bar.setClickable(true);
        bar.setPadding(dp(unit, 14), dp(unit, 8), dp(unit, 14), dp(unit, 8));

        // Left half: Current weather
        LinearLayout leftBox = new LinearLayout(c);
        leftBox.setOrientation(LinearLayout.HORIZONTAL);
        leftBox.setGravity(Gravity.CENTER_VERTICAL);

        final Weather.WeatherIconView curIcon = new Weather.WeatherIconView(c);
        leftBox.addView(curIcon, new LinearLayout.LayoutParams(dp(unit, 32), dp(unit, 32)));

        LinearLayout leftTexts = new LinearLayout(c);
        leftTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ltlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ltlp.leftMargin = dp(unit, 8);

        final TextView curTitleTv = new TextView(c);
        Ui.textSize(curTitleTv, 13f);
        curTitleTv.setTypeface(Ui.font(c, "semibold"));
        curTitleTv.setTextColor(Color.WHITE);
        curTitleTv.setSingleLine(true);
        curTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        leftTexts.addView(curTitleTv);

        final TextView curSubTv = new TextView(c);
        Ui.textSize(curSubTv, 10f);
        curSubTv.setTypeface(Ui.font(c, "regular"));
        curSubTv.setTextColor(0xD9FFFFFF);
        curSubTv.setSingleLine(true);
        curSubTv.setEllipsize(TextUtils.TruncateAt.END);
        leftTexts.addView(curSubTv);

        leftBox.addView(leftTexts, ltlp);
        bar.addView(leftBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Vertical divider
        final View div = new View(c);
        div.setBackgroundColor(0x2EFFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Math.max(1, dp(unit, 1)), dp(unit, 36));
        dlp.leftMargin = dp(unit, 10);
        dlp.rightMargin = dp(unit, 10);
        bar.addView(div, dlp);

        // Right half: Tomorrow's weather
        LinearLayout rightBox = new LinearLayout(c);
        rightBox.setOrientation(LinearLayout.HORIZONTAL);
        rightBox.setGravity(Gravity.CENTER_VERTICAL);

        final Weather.WeatherIconView tomIcon = new Weather.WeatherIconView(c);
        rightBox.addView(tomIcon, new LinearLayout.LayoutParams(dp(unit, 28), dp(unit, 28)));

        LinearLayout rightTexts = new LinearLayout(c);
        rightTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rtlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rtlp.leftMargin = dp(unit, 8);

        final TextView tomTitleTv = new TextView(c);
        Ui.textSize(tomTitleTv, 11.5f);
        tomTitleTv.setTypeface(Ui.font(c, "semibold"));
        tomTitleTv.setTextColor(Color.WHITE);
        tomTitleTv.setSingleLine(true);
        tomTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        rightTexts.addView(tomTitleTv);

        final TextView tomSubTv = new TextView(c);
        Ui.textSize(tomSubTv, 10f);
        tomSubTv.setTypeface(Ui.font(c, "regular"));
        tomSubTv.setTextColor(0xD9FFFFFF);
        tomSubTv.setSingleLine(true);
        tomSubTv.setEllipsize(TextUtils.TruncateAt.END);
        rightTexts.addView(tomSubTv);

        rightBox.addView(rightTexts, rtlp);
        bar.addView(rightBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Runnable bindWeatherData = () -> {
            Weather.Data d = Weather.cached(c);
            if (d == null) {
                curIcon.setType(Weather.ICON_PARTLY);
                curTitleTv.setText("--°");
                curSubTv.setText(I18n.t(c, "weather_updating"));
                tomIcon.setType(Weather.ICON_PARTLY);
                tomTitleTv.setText(I18n.t(c, "tomorrow_short"));
                tomSubTv.setText("--° / --°");
                return;
            }
            curIcon.setType(Weather.iconType(d.code));
            String citySuffix = (d.place != null && !d.place.isEmpty()) ? (" · " + d.place) : "";
            curTitleTv.setText(Weather.formatTemp(c, d.temp) + citySuffix);
            curSubTv.setText(Weather.codeText(c, d.code));

            if (d.days != null && d.days.size() > 1 && d.days.get(1) != null) {
                Weather.Day tm = d.days.get(1);
                tomIcon.setType(Weather.iconType(tm.code));
                tomTitleTv.setText(I18n.t(c, "tomorrow_short") + "  "
                        + Weather.formatTemp(c, tm.max) + "/" + Weather.formatTemp(c, tm.min));
                tomSubTv.setText(Weather.codeText(c, tm.code));
            } else {
                tomIcon.setType(Weather.iconType(d.code));
                tomTitleTv.setText(I18n.t(c, "tomorrow_short"));
                tomSubTv.setText(Weather.codeText(c, d.code));
            }
        };
        bindWeatherData.run();
        if (Weather.cached(c) == null) {
            Weather.refresh(c, false, () -> MAIN.post(bindWeatherData));
        }

        final GradientDrawable whiteFocus = new GradientDrawable();
        whiteFocus.setCornerRadius(radius);
        whiteFocus.setColor(0xF7FFFFFF);

        bar.setOnFocusChangeListener((v, has) -> {
            bar.setBackground(has ? whiteFocus : idleBg);
            curTitleTv.setTextColor(has ? 0xFF111114 : Color.WHITE);
            curSubTv.setTextColor(has ? 0xB3111114 : 0xD9FFFFFF);
            tomTitleTv.setTextColor(has ? 0xFF111114 : Color.WHITE);
            tomSubTv.setTextColor(has ? 0xB3111114 : 0xD9FFFFFF);
            div.setBackgroundColor(has ? 0x26111114 : 0x2EFFFFFF);
            v.animate().scaleX(has ? 1.035f : 1f).scaleY(has ? 1.035f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });

        bar.setOnClickListener(v -> {
            dismissAll(c);
            openFiveDayWeather(c);
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        panel.addView(bar, lp);
        return bar;
    }

    private static WindowManager weatherOverlayWm;
    private static FrameLayout weatherOverlayRoot;

    public static void dismissWeatherOverlay() {
        if (weatherOverlayRoot != null && weatherOverlayWm != null) {
            FrameLayout r = weatherOverlayRoot;
            WindowManager wm = weatherOverlayWm;
            weatherOverlayRoot = null;
            weatherOverlayWm = null;
            try {
                wm.removeViewImmediate(r);
            } catch (Exception ignored) {}
        }
    }

    public static void openFiveDayWeather(Context c) {
        if (MainActivity.activeInstance != null && MainActivity.activeInstance.resumed) {
            MainActivity.activeInstance.runOnUiThread(() -> MainActivity.activeInstance.showWeather());
            return;
        }
        if (showWeatherOverlay(c)) {
            return;
        }
        Intent i = new Intent(c, MainActivity.class)
                .putExtra("panel", "weather")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {}
    }

    public static boolean showWeatherOverlay(final Context c) {
        dismiss();
        dismissWeatherOverlay();
        final Context hostCtx = PowerService.instance != null ? PowerService.instance
                : (c instanceof android.app.Activity ? c : null);
        if (hostCtx == null) return false;
        final WindowManager wm = (WindowManager) hostCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return false;
        Point screen = Ui.screen(hostCtx);
        final int screenW = screen.x, screenH = screen.y;
        final float u = Ui.unit(hostCtx);

        FrameLayout layer = new FrameLayout(hostCtx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ev) {
                MainActivity.navSound(hostCtx, ev);
                int k = ev.getKeyCode();
                if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE
                        || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                    if (ev.getAction() == KeyEvent.ACTION_UP) dismissWeatherOverlay();
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            }
        };
        layer.setClickable(true);
        layer.setClipChildren(false);
        layer.setOnClickListener(v -> dismissWeatherOverlay());

        View shade = new View(hostCtx);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0xA6000000, 0x4D000000, 0x00000000}));
        layer.addView(shade, new FrameLayout.LayoutParams(
                screenW * 3 / 5, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        final WeatherView panel = new WeatherView(hostCtx, true, u);
        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                isBlurryBgMode(hostCtx)
                        ? new int[]{0xEA222634, 0xDE161924}
                        : new int[]{0x9412141E, 0x800E1018});
        glass.setCornerRadius(dp(u, 26));
        glass.setStroke(Math.max(1, dp(u, 1)), 0x38FFFFFF);
        panel.setBackground(glass);
        panel.setFocusable(true);
        Weather.Data cached = Weather.cached(hostCtx);
        if (cached != null) panel.setData(cached);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                dp(u, WeatherView.FULL_W), dp(u, WeatherView.FULL_H), Gravity.TOP | Gravity.END);
        plp.topMargin = dp(u, 24);
        plp.rightMargin = dp(u, 62);
        layer.addView(panel, plp);

        int winType = (hostCtx instanceof PowerService)
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (isBlurryBgMode(hostCtx) && android.os.Build.VERSION.SDK_INT >= 31) {
            flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                winType,
                flags,
                PixelFormat.TRANSLUCENT);
        if (isBlurryBgMode(hostCtx) && android.os.Build.VERSION.SDK_INT >= 31) {
            try { wlp.setBlurBehindRadius(dp(u, 36)); } catch (Throwable ignored) {}
        }
        try {
            wm.addView(layer, wlp);
            weatherOverlayWm = wm;
            weatherOverlayRoot = layer;
            layer.setAlpha(0f);
            panel.setTranslationY(-dp(u, 10));
            layer.animate().alpha(1f).setDuration(200).start();
            panel.animate().translationY(0).setDuration(260).setInterpolator(MainActivity.EASE).start();
            panel.requestFocus();
            Weather.refresh(hostCtx, false, () -> MAIN.post(() -> {
                Weather.Data nd = Weather.cached(hostCtx);
                if (nd != null && panel.isAttachedToWindow()) panel.setData(nd);
            }));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static View makeTile(
            Context c,
            FrameLayout panel,
            int x,
            int y,
            int w,
            int h,
            float unit,
            int screenW,
            int screenH,
            MainActivity.LiquidGlass.Backdrop glassSource,
            String glyph,
            String title,
            Status status,
            View.OnClickListener click) {

        boolean iconOnly = title == null;
        float radius = iconOnly ? dp(unit, 16) : h / 2f;

        final android.graphics.drawable.Drawable idleBg =
                createControlTileBg(c, radius, unit, glassSource, screenW, screenH);

        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(idleBg);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(iconOnly ? 0 : dp(unit, 14), 0, iconOnly ? 0 : dp(unit, 12), 0);

        int gs = iconOnly ? dp(unit, 26) : dp(unit, 25);
        ImageView gi = new ImageView(c);
        int baseIconColor = ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF
                : "ambient".equals(glyph) ? 0xFFFFD60A
                : "power".equals(glyph) ? 0xFFFF453A
                : Color.WHITE;
        gi.setImageBitmap(Glyphs.get(glyph, gs, baseIconColor));
        t.addView(gi, new LinearLayout.LayoutParams(gs, gs));

        TextView sub = null;
        TextView[] titleRef = {null};
        if (!iconOnly) {
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setGravity(Gravity.START);

            TextView tt = new TextView(c);
            Ui.textSize(tt, 12.5f);
            tt.setTypeface(Ui.font(c, "semibold"));
            tt.setTextColor(Color.WHITE);
            tt.setText(title);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            titleRef[0] = tt;
            texts.addView(tt);

            if (status != null) {
                sub = new TextView(c);
                Ui.textSize(sub, 10.5f);
                sub.setTypeface(Ui.font(c, "regular"));
                sub.setTextColor(0xD9FFFFFF);
                sub.setText(status.get());
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(sub);
            }

            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(unit, 10);
            t.addView(texts, tlp);
        }

        final TextView subRef = sub;
        GradientDrawable white = new GradientDrawable();
        white.setCornerRadius(radius);
        white.setColor(0xF7FFFFFF);

        final int focusIconColor = ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF
                : "ambient".equals(glyph) ? 0xFFD97706
                : "power".equals(glyph) ? 0xFFD7372A
                : 0xFF111114;
        final android.graphics.Bitmap baseBmp = Glyphs.get(glyph, gs, baseIconColor);
        final android.graphics.Bitmap focusBmp = Glyphs.get(glyph, gs, focusIconColor);

        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? white : idleBg);
            gi.setImageBitmap(has ? focusBmp : baseBmp);
            TextView tt = titleRef[0];
            if (tt != null) {
                tt.setTextColor(has ? 0xFF111114 : Color.WHITE);
            }
            if (subRef != null) {
                subRef.setTextColor(has ? 0xB3111114 : 0xD9FFFFFF);
            }
            if (memBarFill != null && memBarFill.getParent() != null
                    && v == ((View) memBarFill.getParent()).getParent().getParent()) {
                ((GradientDrawable) ((View) memBarFill.getParent()).getBackground()).setColor(has ? 0x33111114 : 0x40FFFFFF);
                ((GradientDrawable) memBarFill.getBackground()).setColor(has ? 0xFF111114 : (memoryUsed(c) > 0.85f ? 0xFFFF9F0A : Color.WHITE));
            }
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });

        t.setOnClickListener(v -> {
            click.onClick(v);
            if (subRef != null && status != null) subRef.setText(status.get());
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        panel.addView(t, lp);
        return t;
    }

    private static void moveFocusInOverlay(Context c, int keyCode) {
        if (activeCells == null || activeCells.isEmpty()) return;
        View cur = null;
        for (View v : activeCells) {
            if (v.isFocused()) {
                cur = v;
                break;
            }
        }
        if (cur == null) {
            activeCells.get(0).requestFocus();
            return;
        }
        int[] a = new int[2];
        cur.getLocationOnScreen(a);
        float ax = a[0] + cur.getWidth() / 2f, ay = a[1] + cur.getHeight() / 2f;
        View best = null;
        float bestScore = Float.MAX_VALUE;
        for (View v : activeCells) {
            if (v == cur) continue;
            int[] b = new int[2];
            v.getLocationOnScreen(b);
            float dx = b[0] + v.getWidth() / 2f - ax, dy = b[1] + v.getHeight() / 2f - ay;
            float main, cross;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: main = -dx; cross = dy; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: main = dx; cross = dy; break;
                case KeyEvent.KEYCODE_DPAD_UP: main = -dy; cross = dx; break;
                default: main = dy; cross = dx; break;
            }
            if (main <= 8) continue;
            float score = main + Math.abs(cross) * 2.2f;
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        if (best != null) best.requestFocus();
    }

    // ---------------------------------------------------------------- Live States & Actions

    public static String wifiState(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return I18n.s(c, "Not Connected", "Bağlı Değil");
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return I18n.s(c, "Connected", "Bağlı");
            WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
            String ssid = wm.getConnectionInfo().getSSID();
            if (ssid == null || ssid.contains("unknown ssid")) return I18n.s(c, "Connected", "Bağlı");
            return ssid.replace("\"", "");
        } catch (Exception e) {
            return I18n.s(c, "Connected", "Bağlı");
        }
    }

    public static String bluetoothState(Context c) {
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            if (am != null) {
                for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            || d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        CharSequence name = d.getProductName();
                        if (name != null && name.length() > 0) return name.toString();
                    }
                }
            }
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null) {
                if (!ba.isEnabled()) return I18n.s(c, "Off", "Kapalı");
                try {
                    Set<BluetoothDevice> bonded = ba.getBondedDevices();
                    if (bonded != null && !bonded.isEmpty()) {
                        return bonded.size() + I18n.s(c, " paired", " eşleşmiş");
                    }
                } catch (SecurityException ignored) { }
                return I18n.s(c, "On", "Açık");
            }
        } catch (Exception ignored) { }
        return I18n.s(c, "Settings", "Ayarlar");
    }

    public static float memoryUsed(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem > 0 ? 1f - mi.availMem / (float) mi.totalMem : 0f;
    }

    public static String memoryState(Context c) {
        return Math.round(memoryUsed(c) * 100) + I18n.s(c, "% used · Recent Apps", "% kullanımda · Son Uygulamalar");
    }

    private static void attachMemoryBar(Context c, View tile, float unit) {
        if (!(tile instanceof LinearLayout) || ((LinearLayout) tile).getChildCount() < 2) return;
        View texts = ((LinearLayout) tile).getChildAt(1);
        if (!(texts instanceof LinearLayout)) return;
        FrameLayout track = new FrameLayout(c);
        GradientDrawable tb = new GradientDrawable();
        tb.setCornerRadius(dp(unit, 2));
        tb.setColor(0x40FFFFFF);
        track.setBackground(tb);
        memBarFill = new View(c);
        GradientDrawable fb = new GradientDrawable();
        fb.setCornerRadius(dp(unit, 2));
        fb.setColor(Color.WHITE);
        memBarFill.setBackground(fb);
        track.addView(memBarFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(unit, 78), dp(unit, 4));
        lp.topMargin = dp(unit, 4);
        ((LinearLayout) texts).addView(track, lp);
        track.post(() -> updateMemoryBar(c));
    }

    private static void updateMemoryBar(Context c) {
        if (memBarFill == null || memBarFill.getParent() == null) return;
        View track = (View) memBarFill.getParent();
        float used = memoryUsed(c);
        ViewGroup.LayoutParams lp = memBarFill.getLayoutParams();
        lp.width = Math.round(track.getWidth() * used);
        memBarFill.setLayoutParams(lp);
        ((GradientDrawable) memBarFill.getBackground()).setColor(used > 0.85f ? 0xFFFF9F0A : Color.WHITE);
    }

    public static void cleanMemory(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        List<String> apps = new ArrayList<>();
        for (String p : c.getSharedPreferences("clean", Context.MODE_PRIVATE).getString("recent", "").split(",")) {
            if (!p.isEmpty() && Suggestions.installed(c, p)) apps.add(p);
        }
        if (apps.isEmpty()) {
            Toast.makeText(c, I18n.s(c, "No apps to close", "Kapatılacak uygulama yok"), Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityManager.MemoryInfo miBefore = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(miBefore);
        long before = miBefore.availMem;
        for (String pkg : apps) {
            try {
                am.killBackgroundProcesses(pkg);
            } catch (Exception ignored) { }
        }
        MAIN.postDelayed(() -> {
            c.getSharedPreferences("clean", Context.MODE_PRIVATE).edit().remove("recent").apply();
            ActivityManager.MemoryInfo miAfter = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(miAfter);
            long freed = Math.max(0, miAfter.availMem - before) / (1024 * 1024);
            updateMemoryBar(c);
            Toast.makeText(c, I18n.s(c,
                    "Closed " + apps.size() + (apps.size() == 1 ? " app" : " apps") + (freed > 0 ? " · freed " + freed + " MB" : ""),
                    apps.size() + " uygulama kapatıldı" + (freed > 0 ? " · " + freed + " MB boşaltıldı" : "")),
                    Toast.LENGTH_SHORT).show();
        }, 500);
    }

    public static String dndState(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL
                ? I18n.s(c, "On", "Açık") : I18n.s(c, "Off", "Kapalı");
    }

    public static void toggleDnd(Context c) {
        MediaWatchService svc = MediaWatchService.instance;
        if (svc == null) {
            if (c instanceof android.app.Activity) {
                Perms.requestListener((android.app.Activity) c);
            } else {
                startExternal(c, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean on = nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        svc.requestInterruptionFilter(on ? NotificationListenerService.INTERRUPTION_FILTER_ALL
                : NotificationListenerService.INTERRUPTION_FILTER_PRIORITY);
    }

    public static String audioState(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    return d.getProductName() + "";
                case AudioDeviceInfo.TYPE_HDMI_ARC:
                case AudioDeviceInfo.TYPE_HDMI_EARC:
                    return "HDMI ARC";
                default:
                    break;
            }
        }
        return I18n.s(c, "TV Speakers", "TV Hoparlörü");
    }

    public static String timerState(Context c) {
        int left = SleepReceiver.timerLeft(c);
        return left > 0 ? left + I18n.s(c, " min left", " dk kaldı") : I18n.s(c, "Off", "Kapalı");
    }

    public static void cycleSleepTimer(Context c) {
        int left = SleepReceiver.timerLeft(c);
        int[] steps = {15, 30, 60, 90};
        int next = steps[0];
        if (left > 0) {
            next = 0;
            for (int st : steps) {
                if (st > left + 1) {
                    next = st;
                    break;
                }
            }
        }
        SleepReceiver.setTimer(c, next);
    }

    // ---------------------------------------------------------------- Custom Control Panel Actions & Visibility

    public static final String PREFS_CP = "control_panel";
    public static final String ACTION_WIFI = "wifi";
    public static final String ACTION_BLUETOOTH = "bluetooth";
    public static final String ACTION_SOUND = "sound";
    public static final String ACTION_INPUTS = "inputs";
    public static final String ACTION_AMBIENT = "ambient";

    public static final String AMBIENT_MODE_DEFAULT = "default";
    public static final String AMBIENT_MODE_LOCK_SCREEN = "lock_screen";
    public static final String AMBIENT_MODE_CUSTOM = "custom";

    public static final String BG_MODE_BLURRY = "blur";
    public static final String BG_MODE_TRANSPARENT = "transparent";

    public static String getBgMode(Context c) {
        if (c == null) return BG_MODE_BLURRY;
        String m = cpPrefs(c).getString("cp_bg_mode", BG_MODE_BLURRY);
        return BG_MODE_TRANSPARENT.equals(m) ? BG_MODE_TRANSPARENT : BG_MODE_BLURRY;
    }

    public static void setBgMode(Context c, String mode) {
        if (c == null) return;
        cpPrefs(c).edit().putString("cp_bg_mode",
                BG_MODE_TRANSPARENT.equals(mode) ? BG_MODE_TRANSPARENT : BG_MODE_BLURRY).apply();
    }

    public static String cycleBgMode(Context c) {
        String next = isBlurryBgMode(c) ? BG_MODE_TRANSPARENT : BG_MODE_BLURRY;
        setBgMode(c, next);
        return next;
    }

    public static boolean isBlurryBgMode(Context c) {
        return BG_MODE_BLURRY.equals(getBgMode(c));
    }

    public static String getBgModeLabel(Context c) {
        return isBlurryBgMode(c)
                ? I18n.t(c, "cp_bg_blurry")
                : I18n.t(c, "cp_bg_transparent");
    }

    public static final String ITEM_WIFI = "wifi";
    public static final String ITEM_BLUETOOTH = "bluetooth";
    public static final String ITEM_MEMORY = "memory";
    public static final String ITEM_DND = "dnd";
    public static final String ITEM_AUDIO = "audio";
    public static final String ITEM_TIMER = "timer";
    public static final String ITEM_AMBIENT = "ambient";
    public static final String ITEM_POWER = "power";
    public static final String ITEM_INPUTS = "inputs";
    public static final String ITEM_MAPPER = "mapper";
    public static final String ITEM_SETTINGS = "settings";
    public static final String ITEM_SEARCH = "search";
    public static final String ITEM_WEATHER_PANEL = "weather_panel";

    public static final String[][] ALL_CP_ITEMS = {
        {ITEM_WIFI, "Wi-Fi", "Wi-Fi"},
        {ITEM_BLUETOOTH, "Bluetooth", "Bluetooth"},
        {ITEM_MEMORY, "Memory / Recent Apps", "Bellek / Son Uygulamalar"},
        {ITEM_DND, "Do Not Disturb", "Rahatsız Etmeyin"},
        {ITEM_AUDIO, "Audio Output", "Ses Çıkışı"},
        {ITEM_TIMER, "Sleep Timer", "Uyku Zamanlayıcısı"},
        {ITEM_AMBIENT, "Ambient Mode / Lock Screen", "Ortam Modu / Kilit Ekranı"},
        {ITEM_POWER, "Power Off / Screen Off", "Kapat / Ekranı Kapat"},
        {ITEM_INPUTS, "TV Inputs Shortcut", "TV Girişleri Kısayolu"},
        {ITEM_MAPPER, "Button Mapper Shortcut", "Tuş Atayıcı Kısayolu"},
        {ITEM_SETTINGS, "Settings Shortcut", "Ayarlar Kısayolu"},
        {ITEM_SEARCH, "Search / Assistant Shortcut", "Arama / Asistan Kısayolu"},
        {ITEM_WEATHER_PANEL, "Weather Panel (Current & Tomorrow)", "Hava Durumu Paneli (Şimdi ve Yarın)"}
    };

    public static android.content.SharedPreferences cpPrefs(Context c) {
        return c.getSharedPreferences(PREFS_CP, Context.MODE_PRIVATE);
    }

    public static String getItemTitle(Context c, String itemKey) {
        for (String[] row : ALL_CP_ITEMS) {
            if (row[0].equals(itemKey)) {
                return I18n.s(c, row[1], row[2]);
            }
        }
        return itemKey;
    }

    public static boolean isItemEnabled(Context c, String itemKey) {
        if (c == null || itemKey == null) return true;
        return cpPrefs(c).getBoolean("item_enabled_" + itemKey, true);
    }

    public static void setItemEnabled(Context c, String itemKey, boolean enabled) {
        if (c == null || itemKey == null) return;
        cpPrefs(c).edit().putBoolean("item_enabled_" + itemKey, enabled).apply();
    }

    public static boolean isAmbientLockScreenMode(Context c) {
        if (c == null) return false;
        return AMBIENT_MODE_LOCK_SCREEN.equals(
                cpPrefs(c).getString("ambient_mode_type", AMBIENT_MODE_DEFAULT));
    }

    public static void setAmbientLockScreenMode(Context c, boolean lockScreen) {
        if (c == null) return;
        cpPrefs(c).edit()
                .putString("ambient_mode_type", lockScreen ? AMBIENT_MODE_LOCK_SCREEN : AMBIENT_MODE_DEFAULT)
                .remove(ACTION_AMBIENT + "_pkg")
                .remove(ACTION_AMBIENT + "_cls")
                .remove(ACTION_AMBIENT + "_label")
                .apply();
    }

    public static boolean hasCustomAction(Context c, String key) {
        if (ACTION_AMBIENT.equals(key) && isAmbientLockScreenMode(c)) {
            return false;
        }
        String pkg = cpPrefs(c).getString(key + "_pkg", "");
        return pkg != null && !pkg.isEmpty();
    }

    public static String getCustomPkg(Context c, String key) {
        return cpPrefs(c).getString(key + "_pkg", "");
    }

    public static String getCustomCls(Context c, String key) {
        return cpPrefs(c).getString(key + "_cls", "");
    }

    public static String getActionSummary(Context c, String key) {
        if (ACTION_AMBIENT.equals(key) && isAmbientLockScreenMode(c)) {
            return I18n.s(c, "Lock Screen", "Kilit Ekranı");
        }
        String pkg = getCustomPkg(c, key);
        if (pkg == null || pkg.isEmpty()) {
            return I18n.s(c, "Default (Automatic)", "Varsayılan (Otomatik)");
        }
        String label = cpPrefs(c).getString(key + "_label", "");
        if (label != null && !label.isEmpty()) return label;
        String cls = getCustomCls(c, key);
        return cls != null && !cls.isEmpty() ? (pkg + " · " + cls) : pkg;
    }

    public static void setCustomAction(Context c, String key, String pkg, String cls, String label) {
        android.content.SharedPreferences.Editor ed = cpPrefs(c).edit()
                .putString(key + "_pkg", pkg != null ? pkg : "")
                .putString(key + "_cls", cls != null ? cls : "")
                .putString(key + "_label", label != null ? label : "");
        if (ACTION_AMBIENT.equals(key)) {
            ed.putString("ambient_mode_type", AMBIENT_MODE_CUSTOM);
        }
        ed.apply();
    }

    public static void clearCustomAction(Context c, String key) {
        android.content.SharedPreferences.Editor ed = cpPrefs(c).edit()
                .remove(key + "_pkg")
                .remove(key + "_cls")
                .remove(key + "_label");
        if (ACTION_AMBIENT.equals(key)) {
            ed.putString("ambient_mode_type", AMBIENT_MODE_DEFAULT);
        }
        ed.apply();
    }

    public static void clearAllCustomActions(Context c) {
        android.content.SharedPreferences.Editor ed = cpPrefs(c).edit();
        for (String k : new String[]{ACTION_WIFI, ACTION_BLUETOOTH, ACTION_SOUND, ACTION_INPUTS, ACTION_AMBIENT}) {
            ed.remove(k + "_pkg").remove(k + "_cls").remove(k + "_label");
        }
        ed.putString("ambient_mode_type", AMBIENT_MODE_DEFAULT);
        ed.apply();
    }

    /** Launches the user-configured app/activity for a Control Panel item if one is set. */
    public static boolean launchCustomAction(Context c, String key) {
        String pkg = getCustomPkg(c, key);
        if (pkg == null || pkg.isEmpty()) return false;
        String cls = getCustomCls(c, key);
        try {
            if (cls != null && !cls.isEmpty()) {
                Intent i = new Intent();
                i.setComponent(new ComponentName(pkg, cls));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            }
            android.content.pm.PackageManager pm = c.getPackageManager();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(launch);
                return true;
            }
        } catch (Exception e) {
            // Unexported OEM activity fallback via local ADB if paired
            if (LocalAdb.startActivityViaAdbAsync(c, pkg, cls)) {
                return true;
            }
        }
        return false;
    }

    public static void openWifiSettings(Context c) {
        if (launchCustomAction(c, ACTION_WIFI)) return;
        startExternal(c, new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS));
    }

    /** Opens Bluetooth or Remotes & Accessories / Connected Devices settings on Android TV / Google TV. */
    public static void openBluetoothSettings(Context c) {
        if (launchCustomAction(c, ACTION_BLUETOOTH)) return;
        Intent connectedDevices = new Intent("com.android.tv.settings.CONNECTED_DEVICES");
        Intent googleBtServices = new Intent().setComponent(new ComponentName(
                "com.droidlogic.android.tv.connecteddevices",
                "com.google.android.tv.btservices.settings.ConnectedDevicesSettingsActivity"));
        Intent addAccessory = new Intent().setComponent(new ComponentName(
                "com.android.tv.settings",
                "com.android.tv.settings.accessories.AddAccessoryActivity"));
        Intent btSettings = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        startExternal(c, connectedDevices, googleBtServices, addAccessory, btSettings, new Intent(Settings.ACTION_SETTINGS));
    }

    public static void openSoundSettings(Context c) {
        if (launchCustomAction(c, ACTION_SOUND)) return;
        startExternal(c, new Intent("android.settings.SOUND_SETTINGS"), new Intent(Settings.ACTION_SETTINGS));
    }

    public static void openInputs(Context c, Runnable defaultFallback) {
        if (launchCustomAction(c, ACTION_INPUTS)) return;
        if (defaultFallback != null) defaultFallback.run();
    }

    // ---------------------------------------------------------------- Settings Gear Popup Overlay (Feature 8 & 10)

    public static void showSettingsGearPopupOverlay(Context c) {
        if (MainActivity.activeInstance != null && MainActivity.activeInstance.resumed) {
            MainActivity.activeInstance.runOnUiThread(() -> MainActivity.activeInstance.showSettingsSheet());
            return;
        }
        PowerService svc = PowerService.instance;
        if (svc == null) {
            startExternal(c, new Intent(c, SettingsActivity.class));
            return;
        }
        WindowManager wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            startExternal(c, new Intent(c, SettingsActivity.class));
            return;
        }
        final float u = Ui.unit(svc);
        final FrameLayout[] rootRef = new FrameLayout[1];
        final List<View> rows = new ArrayList<>();
        final Runnable closeSheet = () -> {
            if (rootRef[0] != null) {
                try { wm.removeViewImmediate(rootRef[0]); } catch (Exception ignored) {}
                rootRef[0] = null;
            }
        };

        FrameLayout backdrop = new FrameLayout(svc) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ev) {
                MainActivity.navSound(svc, ev);
                if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK || ev.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                    if (ev.getAction() == KeyEvent.ACTION_UP) closeSheet.run();
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            }
        };
        backdrop.setBackgroundColor(isBlurryBgMode(svc) ? 0x66000000 : 0x33000000);
        backdrop.setClickable(true);
        backdrop.setOnClickListener(v -> closeSheet.run());
        rootRef[0] = backdrop;

        LinearLayout card = new LinearLayout(svc);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        int pad = dp(u, 8);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable sbg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                isBlurryBgMode(svc)
                        ? new int[]{0xED242028, 0xE61A171E}
                        : new int[]{0xC81C181E, 0xB816141A});
        sbg.setCornerRadius(dp(u, 18));
        sbg.setStroke(Math.max(1, dp(u, 1)), 0x38FFFFFF);
        card.setBackground(sbg);

        View btnHomeSettings = makeOverlayGearOptionRow(
                svc,
                u,
                "sliders",
                I18n.s(svc, "Home settings", "Ana ekran ayarları"),
                I18n.s(svc, "Language, weather, scenes, apps and people",
                        "Dil, hava durumu, sahneler, uygulamalar ve kişiler"),
                () -> {
                    closeSheet.run();
                    startExternal(svc, new Intent(svc, SettingsActivity.class));
                });
        card.addView(btnHomeSettings);
        rows.add(btnHomeSettings);

        View btnTvSettings = makeOverlayGearOptionRow(
                svc,
                u,
                "gear",
                I18n.s(svc, "TV settings", "TV ayarları"),
                I18n.s(svc, "Wi-Fi, picture, sound and the rest of the TV",
                        "Wi-Fi, görüntü, ses ve diğer TV ayarları"),
                () -> {
                    closeSheet.run();
                    startExternal(svc, new Intent(Settings.ACTION_SETTINGS));
                });
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvlp.topMargin = dp(u, 2);
        card.addView(btnTvSettings, tvlp);
        rows.add(btnTvSettings);

        if (CustomMedia.isRedditWallpaperActiveOnCurrentLook(svc)) {
            View btnNextWall = makeOverlayGearOptionRow(
                    svc,
                    u,
                    "refresh",
                    I18n.s(svc, "Next wallpaper", "Sonraki duvar kağıdı"),
                    I18n.s(svc, "Skip to the next Reddit wallpaper", "Sonraki Reddit duvar kağıdına geç"),
                    () -> {
                        closeSheet.run();
                        Toast.makeText(svc,
                                I18n.s(svc, "Loading next Reddit wallpaper…", "Sonraki Reddit duvar kağıdı yükleniyor…"),
                                Toast.LENGTH_SHORT).show();
                        CustomMedia.skipToNextRedditWallpaperOnCurrentLook(svc, null);
                    });
            LinearLayout.LayoutParams nwlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nwlp.topMargin = dp(u, 2);
            card.addView(btnNextWall, nwlp);
            rows.add(btnNextWall);
        }

        for (int i = 0; i < rows.size(); i++) {
            final int idx = i;
            View r = rows.get(i);
            r.setOnKeyListener((v, kc, ev) -> {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (kc == KeyEvent.KEYCODE_DPAD_UP) {
                    rows.get((idx - 1 + rows.size()) % rows.size()).requestFocus();
                    return true;
                }
                if (kc == KeyEvent.KEYCODE_DPAD_DOWN) {
                    rows.get((idx + 1) % rows.size()).requestFocus();
                    return true;
                }
                return false;
            });
        }

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                dp(u, 330), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        backdrop.addView(card, slp);

        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (isBlurryBgMode(svc) && android.os.Build.VERSION.SDK_INT >= 31) {
            flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        if (isBlurryBgMode(svc) && android.os.Build.VERSION.SDK_INT >= 31) {
            try { wlp.setBlurBehindRadius(dp(u, 32)); } catch (Throwable ignored) {}
        }
        wm.addView(backdrop, wlp);
        backdrop.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        backdrop.animate().alpha(1f).setDuration(180).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(MainActivity.LIFT).start();
        btnHomeSettings.requestFocus();
    }

    private static View makeOverlayGearOptionRow(
            Context c, float u, String glyphName, String title, String sub, Runnable onClick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(u, 7), dp(u, 7), dp(u, 14), dp(u, 7));
        row.setFocusable(true);
        row.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(u, 12));
        bg.setColor(Color.TRANSPARENT);
        row.setBackground(bg);

        FrameLayout iconBox = new FrameLayout(c);
        int iconBoxSz = dp(u, 28);
        final float iconRad = iconBoxSz * 0.26f;
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(iconRad);
        ibg.setColor(0x1FFFFFFF);
        iconBox.setBackground(ibg);
        ImageView iconIv = new ImageView(c);
        iconIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconIv.setImageBitmap(Glyphs.get(glyphName, dp(u, 16), 0xEBF4F4F6));
        iconBox.addView(iconIv, new FrameLayout.LayoutParams(dp(u, 16), dp(u, 16), Gravity.CENTER));
        LinearLayout.LayoutParams iblp = new LinearLayout.LayoutParams(iconBoxSz, iconBoxSz);
        iblp.rightMargin = dp(u, 10);
        row.addView(iconBox, iblp);

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(c);
        Ui.textSize(t, 13.5f);
        t.setTypeface(Ui.font(c, "semibold"));
        t.setTextColor(0xEBF4F4F6);
        t.setText(title);
        texts.addView(t);

        TextView s = new TextView(c);
        Ui.textSize(s, 9.5f);
        s.setTypeface(Ui.font(c, "regular"));
        s.setTextColor(0x9EF4F4F6);
        s.setText(sub);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(u, 1);
        texts.addView(s, slp);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            ibg.setColor(has ? 0x12000000 : 0x1FFFFFFF);
            iconIv.setImageBitmap(Glyphs.get(glyphName, dp(u, 16), has ? 0xFF111114 : 0xEBF4F4F6));
            t.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            s.setTextColor(has ? 0x99000000 : 0x9EF4F4F6);
        });
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    // ---------------------------------------------------------------- System-Wide Input Panel Overlay (Feature 1 & 10)

    private static WindowManager inputsOverlayWm;
    private static FrameLayout inputsOverlayRoot;
    private static List<View> inputsOverlayCells = new ArrayList<>();

    public static boolean isInputsOverlayShowing() {
        return inputsOverlayRoot != null;
    }

    public static void toggleInputsOverlay(Context c) {
        if (isInputsOverlayShowing()) {
            dismissInputsOverlay();
            return;
        }
        showInputsOverlay(c);
    }

    public static void dismissInputsOverlay() {
        if (inputsOverlayRoot != null && inputsOverlayWm != null) {
            FrameLayout r = inputsOverlayRoot;
            WindowManager wm = inputsOverlayWm;
            inputsOverlayRoot = null;
            inputsOverlayWm = null;
            inputsOverlayCells.clear();
            try {
                wm.removeViewImmediate(r);
            } catch (Exception ignored) {}
        }
    }

    private static Inputs.Input makeFallbackInput(String id, String label, int type) {
        Inputs.Input in = new Inputs.Input();
        in.id = id;
        in.label = label;
        in.type = type;
        in.passthrough = !"tv_channels".equals(id);
        in.disconnected = false;
        return in;
    }

    public static void showInputsOverlay(final Context c) {
        dismiss();
        dismissInputsOverlay();
        final Context hostCtx = PowerService.instance != null ? PowerService.instance
                : (MainActivity.activeInstance != null && MainActivity.activeInstance.resumed ? MainActivity.activeInstance : c);
        if (!(hostCtx instanceof PowerService) && !(hostCtx instanceof android.app.Activity)) {
            Intent i = new Intent(c, MainActivity.class)
                    .putExtra("panel", "inputs")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                c.startActivity(i);
            } catch (Exception ignored) {}
            return;
        }

        final WindowManager wm = (WindowManager) hostCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        Point screen = Ui.screen(hostCtx);
        final int screenW = screen.x, screenH = screen.y;
        final float u = Ui.unit(hostCtx);

        FrameLayout layer = new FrameLayout(hostCtx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ev) {
                MainActivity.navSound(hostCtx, ev);
                int k = ev.getKeyCode();
                boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
                if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                    if (!down) dismissInputsOverlay();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                        || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (down) moveFocusInCellList(inputsOverlayCells, k);
                    return true;
                }
                return super.dispatchKeyEvent(ev);
            }
        };
        layer.setClickable(true);
        layer.setClipChildren(false);
        layer.setOnClickListener(v -> dismissInputsOverlay());

        View shade = new View(hostCtx);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0x99000000, 0x40000000, 0x00000000}));
        layer.addView(shade, new FrameLayout.LayoutParams(
                screenW * 3 / 5, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        List<Inputs.Input> list = new ArrayList<>(Inputs.list(hostCtx));
        if (list.isEmpty()) {
            // Direct hardware/passthrough fallbacks so inputs work with the exact same UI even when Google TV Launcher is disabled
            list.add(makeFallbackInput("com.droidlogic.tvinput/.services.Hdmi1InputService/HW5", "HDMI 1", android.media.tv.TvInputInfo.TYPE_HDMI));
            list.add(makeFallbackInput("com.droidlogic.tvinput/.services.Hdmi2InputService/HW6", "HDMI 2", android.media.tv.TvInputInfo.TYPE_HDMI));
            list.add(makeFallbackInput("com.droidlogic.tvinput/.services.Hdmi3InputService/HW7", "HDMI 3", android.media.tv.TvInputInfo.TYPE_HDMI));
            list.add(makeFallbackInput("com.droidlogic.tvinput/.services.Hdmi4InputService/HW8", "HDMI 4", android.media.tv.TvInputInfo.TYPE_HDMI));
            list.add(makeFallbackInput("tv_channels", I18n.s(hostCtx, "Channels", "Kanallar"), android.media.tv.TvInputInfo.TYPE_TUNER));
            list.add(makeFallbackInput("com.droidlogic.tvinput/.services.Av1InputService/HW1", "AV", android.media.tv.TvInputInfo.TYPE_COMPOSITE));
        }

        int col = dp(u, 141);
        int gap = dp(u, 13);
        int cap = (col - gap) / 2;
        int top = dp(u, 26);

        FrameLayout panel = new FrameLayout(hostCtx);
        panel.setClipChildren(false);

        TextView title = new TextView(hostCtx);
        Ui.textSize(title, 13f);
        title.setTypeface(Ui.font(hostCtx, "semibold"));
        title.setTextColor(0xBFFFFFFF);
        title.setShadowLayer(dp(u, 8), 0, dp(u, 2), 0xB3000000);
        title.setText(I18n.s(hostCtx, "Inputs", "Girişler"));
        panel.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(u, 20)));

        MainActivity.LiquidGlass.Backdrop glassSrc = (isBlurryBgMode(hostCtx)
                && MainActivity.activeInstance != null
                && MainActivity.activeInstance.resumed)
                ? MainActivity.activeInstance.glassSource
                : null;

        final List<View> cells = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            final Inputs.Input in = list.get(i);
            int x = (i % 2) * (col + gap);
            int y = top + (i / 2) * (cap + gap);
            Status st = in.disconnected ? () -> I18n.s(hostCtx, "Not connected", "Bağlı değil") : null;
            View tile = makeTile(
                    hostCtx, panel, x, y, col, cap, u, screenW, screenH, glassSrc,
                    Inputs.glyph(in), in.label, st, v -> {
                        dismissInputsOverlay();
                        if (!Inputs.open(hostCtx, in)) {
                            switchInputFallback(hostCtx, in.id, in.label);
                        }
                    });
            cells.add(tile);
        }

        int rowsCount = (list.size() + 1) / 2;
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                2 * col + gap,
                top + rowsCount * cap + Math.max(0, rowsCount - 1) * gap,
                Gravity.TOP | Gravity.END);
        plp.topMargin = dp(u, 72);
        plp.rightMargin = dp(u, 62);
        layer.addView(panel, plp);

        int winType = (hostCtx instanceof PowerService)
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (isBlurryBgMode(hostCtx) && android.os.Build.VERSION.SDK_INT >= 31) {
            flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                winType,
                flags,
                PixelFormat.TRANSLUCENT);
        if (isBlurryBgMode(hostCtx) && android.os.Build.VERSION.SDK_INT >= 31) {
            try { wlp.setBlurBehindRadius(dp(u, 28)); } catch (Throwable ignored) {}
        }
        try {
            wm.addView(layer, wlp);
            inputsOverlayWm = wm;
            inputsOverlayRoot = layer;
            inputsOverlayCells = cells;
            layer.setAlpha(0f);
            panel.setTranslationY(-dp(u, 10));
            layer.animate().alpha(1f).setDuration(200).start();
            panel.animate().translationY(0).setDuration(260).setInterpolator(MainActivity.EASE).start();
            if (!cells.isEmpty()) cells.get(0).requestFocus();
        } catch (Exception ignored) {}
    }

    private static void moveFocusInCellList(List<View> cellList, int keyCode) {
        if (cellList == null || cellList.isEmpty()) return;
        View cur = null;
        for (View v : cellList) {
            if (v.isFocused()) {
                cur = v;
                break;
            }
        }
        if (cur == null) {
            cellList.get(0).requestFocus();
            return;
        }
        int[] a = new int[2];
        cur.getLocationOnScreen(a);
        float ax = a[0] + cur.getWidth() / 2f, ay = a[1] + cur.getHeight() / 2f;
        View best = null;
        float bestScore = Float.MAX_VALUE;
        for (View v : cellList) {
            if (v == cur) continue;
            int[] b = new int[2];
            v.getLocationOnScreen(b);
            float dx = b[0] + v.getWidth() / 2f - ax, dy = b[1] + v.getHeight() / 2f - ay;
            float main, cross;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: main = -dx; cross = dy; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: main = dx; cross = dy; break;
                case KeyEvent.KEYCODE_DPAD_UP: main = -dy; cross = dx; break;
                default: main = dy; cross = dx; break;
            }
            if (main <= 8) continue;
            float score = main + Math.abs(cross) * 2.2f;
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        if (best != null) best.requestFocus();
    }

    private static void switchInputFallback(Context c, String inputId, String label) {
        if (inputId != null && !inputId.isEmpty() && !"tv_channels".equals(inputId)) {
            try {
                android.net.Uri uri = android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId);
                if (startExternal(c, new Intent(Intent.ACTION_VIEW, uri))) return;
            } catch (Throwable ignored) {}
        }
        Intent tvView = new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.Channels.CONTENT_URI);
        Intent setupInputs = new Intent("android.media.tv.action.SETUP_INPUTS");
        Intent oemInputs = new Intent("com.android.tv.action.VIEW_INPUT");
        Intent castSettings = new Intent(Settings.ACTION_CAST_SETTINGS);
        if (!startExternal(c, oemInputs, setupInputs, tvView, castSettings)) {
            ButtonMapper.sendKeyCode(c, KeyEvent.KEYCODE_TV_INPUT);
        }
    }

    private static View buildOverlayPopupRow(Context c, float u, String label, Runnable onClick) {
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextColor(0xEBF4F4F6);
        tv.setTypeface(Ui.font(c, "medium"));
        Ui.textSize(tv, 13.5f);
        tv.setPadding(dp(u, 14), dp(u, 11), dp(u, 14), dp(u, 11));
        tv.setFocusable(true);
        tv.setClickable(true);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setCornerRadius(dp(u, 11));
        rbg.setColor(0x00FFFFFF);
        tv.setBackground(rbg);
        tv.setOnFocusChangeListener((v, has) -> {
            rbg.setColor(has ? 0xFFF4F4F6 : 0x00FFFFFF);
            tv.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
        });
        tv.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(u, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** Returns true if any dedicated Ambient Mode / Backdrop / Dream app is installed on this TV. */
    public static boolean hasExternalAmbientApp(Context c) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            Intent panoSleep = new Intent("com.google.android.pano.action.SLEEP");
            android.content.pm.ResolveInfo r1 = pm.resolveActivity(panoSleep, 0);
            if (r1 != null && r1.activityInfo != null
                    && (r1.activityInfo.name == null || !r1.activityInfo.name.contains("EmptyStubActivity"))) {
                return true;
            }
            Intent ambientAct = new Intent("com.google.android.tv.settings.ambient");
            android.content.pm.ResolveInfo r2 = pm.resolveActivity(ambientAct, 0);
            if (r2 != null && r2.activityInfo != null
                    && (r2.activityInfo.name == null || !r2.activityInfo.name.contains("EmptyStubActivity"))) {
                return true;
            }
            if (pm.getLaunchIntentForPackage("com.google.android.backdrop") != null) {
                return true;
            }
            for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai == null || ai.packageName == null) continue;
                String p = ai.packageName.toLowerCase(java.util.Locale.ROOT);
                if (p.contains("backdrop") || p.contains("dreamx") || p.contains("ambient")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Launches Lock Screen if configured for Lock Screen mode, or a custom app/activity if configured,
     * or Google Ambient Mode / Android TV Screensaver if an ambient app is installed on the TV;
     * otherwise enters tvOS Launcher's built-in Ambient Mode.
     */
    public static void startAmbientMode(Context c) {
        if (isAmbientLockScreenMode(c)) {
            MainActivity.triggerLockScreen(c);
            return;
        }
        if (launchCustomAction(c, ACTION_AMBIENT)) {
            return;
        }
        if (hasExternalAmbientApp(c)) {
            Intent panoSleep = new Intent("com.google.android.pano.action.SLEEP");
            if (startExternal(c, panoSleep)) return;

            Intent ambientAct = new Intent("com.google.android.tv.settings.ambient");
            if (startExternal(c, ambientAct)) return;

            Intent backdrop = c.getPackageManager().getLaunchIntentForPackage("com.google.android.backdrop");
            if (backdrop != null && startExternal(c, backdrop)) return;

            Intent somnambulator = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"));
            if (startExternal(c, somnambulator)) return;
        }
        MainActivity.triggerBuiltInAmbient(c);
    }

    /**
     * Projector-safe Screen Off (Blackout mode): blanks the entire screen to 100% pure pitch-black (#000000)
     * and pauses active media playback without triggering hardware ACTION_SCREEN_OFF / HDMI-CEC power-down,
     * keeping projectors (such as Wanbo) running. Wakes immediately on any remote key press.
     */
    private static WindowManager blackoutWm;
    private static View blackoutView;
    private static long blackoutEnterTime = 0L;
    private static final java.util.Set<Integer> blackoutDownKeys = new java.util.HashSet<>();

    public static boolean isScreenOffShowing() {
        return blackoutView != null;
    }

    public static long getScreenOffEnterTime() {
        return blackoutEnterTime;
    }

    public static boolean showScreenOff(Context c) {
        dismiss();
        dismissScreenOff();
        blackoutEnterTime = android.os.SystemClock.uptimeMillis();
        blackoutDownKeys.clear();

        // Pause any active media playback so video/audio doesn't keep playing in the background
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            if (am != null && am.isMusicActive()) {
                long now = android.os.SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            }
        } catch (Exception ignored) { }

        Context overlayCtx = PowerService.instance != null ? PowerService.instance : c;
        WindowManager wm = (WindowManager) overlayCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null && (PowerService.instance != null || overlayCtx instanceof android.app.Activity)) {
            try {
                FrameLayout black = new FrameLayout(overlayCtx) {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event == null) return true;
                        int action = event.getAction();
                        int code = event.getKeyCode();
                        if (action == KeyEvent.ACTION_DOWN) {
                            if (android.os.SystemClock.uptimeMillis() - blackoutEnterTime > 600L
                                    && event.getRepeatCount() == 0) {
                                blackoutDownKeys.add(code);
                            }
                            return true;
                        }
                        if (action == KeyEvent.ACTION_UP) {
                            if (blackoutDownKeys.remove(code)) {
                                dismissScreenOff();
                            }
                            return true;
                        }
                        return true;
                    }
                };
                black.setBackgroundColor(0xFF000000);
                black.setFocusable(true);
                black.setFocusableInTouchMode(true);
                black.setClickable(true);
                black.setKeepScreenOn(true);
                black.setOnClickListener(v -> dismissScreenOff());

                int type = PowerService.instance != null
                        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        type,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        PixelFormat.OPAQUE);
                wm.addView(black, lp);
                black.requestFocus();
                blackoutWm = wm;
                blackoutView = black;
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    public static void dismissScreenOff() {
        if (blackoutView != null && blackoutWm != null) {
            View v = blackoutView;
            WindowManager wm = blackoutWm;
            blackoutView = null;
            blackoutWm = null;
            try {
                wm.removeViewImmediate(v);
            } catch (Exception ignored) { }
        }
    }

    private static boolean startExternal(Context c, Intent... intents) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        for (Intent i : intents) {
            if (i == null) continue;
            try {
                android.content.pm.ResolveInfo ri = pm.resolveActivity(i, 0);
                if (ri == null || ri.activityInfo == null) continue;
                if (ri.activityInfo.name != null && ri.activityInfo.name.contains("EmptyStubActivity")) {
                    continue; // Skip Android TV stub activities that immediately exit
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static int dp(float unit, float u) {
        return Math.round(u * unit);
    }
}
