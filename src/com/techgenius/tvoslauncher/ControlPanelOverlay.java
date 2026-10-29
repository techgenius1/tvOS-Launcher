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
        if (cachedSceneBlur != null && !cachedSceneBlur.isRecycled()) {
            return cachedSceneBlur;
        }
        try {
            int w = Math.max(1, screenW / 4), h = Math.max(1, screenH / 4);
            List<String> ids = Scenes.getEnabledSceneIds(svcCtx);
            String bgAsset = (ids != null && !ids.isEmpty())
                    ? Scenes.byId(ids.get(0)).bgAsset
                    : "backgrounds/scene-night.jpg";
            android.graphics.Bitmap raw = Scenes.loadAssetBitmap(svcCtx, bgAsset, w, h);
            if (raw != null) {
                cachedSceneBlur = Ui.blur(raw, w / 2, h / 2, 4, 3);
                raw.recycle();
                return cachedSceneBlur;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void showWindowOverlay(Context svcCtx) {
        WindowManager wm = (WindowManager) svcCtx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        Point screen = Ui.screen(svcCtx);
        int screenW = screen.x, screenH = screen.y;
        float unit = Ui.unit(svcCtx);

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

        final android.graphics.Bitmap glassBmp = getOverlayGlassBitmap(svcCtx, screenW, screenH);
        if (glassBmp != null) {
            ImageView blurBg = new ImageView(svcCtx);
            blurBg.setScaleType(ImageView.ScaleType.FIT_XY);
            blurBg.setImageBitmap(glassBmp);
            blurBg.setAlpha(0.88f);
            layer.addView(blurBg, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        View shade = new View(svcCtx);
        shade.setBackgroundColor(0x8C000000);
        layer.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MainActivity.LiquidGlass.Backdrop overlayBackdrop = () -> {
            if (MainActivity.activeInstance != null
                    && MainActivity.activeInstance.glassSource != null
                    && MainActivity.activeInstance.glassSource.get() != null) {
                return MainActivity.activeInstance.glassSource.get();
            }
            return glassBmp;
        };

        List<View> cells = new ArrayList<>();
        FrameLayout panel = buildControlPanelView(
                svcCtx, screenW, screenH, unit, overlayBackdrop, cells,
                () -> {
                    dismiss();
                    openInputs(svcCtx, () -> startExternal(svcCtx, new Intent(Settings.ACTION_CAST_SETTINGS),
                            new Intent(Settings.ACTION_SETTINGS)));
                },
                () -> {
                    dismiss();
                    startExternal(svcCtx, new Intent(svcCtx, SettingsActivity.class)
                            .putExtra("page", SettingsActivity.BUTTON_MAPPER));
                },
                () -> {
                    dismiss();
                    startExternal(svcCtx, new Intent(svcCtx, SettingsActivity.class));
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

        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.END;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                wlp.setBlurBehindRadius(dp(unit, 30));
            } catch (Throwable ignored) {
            }
        }

        wm.addView(layer, wlp);
        activeWm = wm;
        activeRoot = layer;
        activeCells = cells;

        layer.setAlpha(0f);
        panel.setTranslationY(-Math.round(10 * unit));
        layer.animate().alpha(1f).setDuration(180).start();
        panel.animate().translationY(0).setDuration(240).setInterpolator(MainActivity.EASE).start();
        if (!cells.isEmpty()) cells.get(0).requestFocus();
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

        FrameLayout panel = new FrameLayout(c);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        // Row 0: Wi-Fi | Bluetooth
        int y0 = 0;
        outCells.add(makeTile(c, panel, 0, y0, col, cap, unit, screenW, screenH, glassSource,
                "wifi", "Wi-Fi", () -> wifiState(c), v -> {
                    dismissAll(c);
                    openWifiSettings(c);
                }));
        outCells.add(makeTile(c, panel, col + gap, y0, col, cap, unit, screenW, screenH, glassSource,
                "bluetooth", "Bluetooth", () -> bluetoothState(c), v -> {
                    dismissAll(c);
                    openBluetoothSettings(c);
                }));

        // Row 1: Memory | Do Not Disturb
        int y1 = y0 + cap + gap;
        View memTile = makeTile(c, panel, 0, y1, col, cap, unit, screenW, screenH, glassSource,
                "memory", I18n.s(c, "Memory", "Bellek"), () -> memoryState(c), v -> {
                    dismissAll(c);
                    RecentAppsOverlay.show(c);
                });
        attachMemoryBar(c, memTile, unit);
        outCells.add(memTile);

        outCells.add(makeTile(c, panel, col + gap, y1, col, cap, unit, screenW, screenH, glassSource,
                "moon", I18n.s(c, "Do Not Disturb", "Rahatsız Etmeyin"), () -> dndState(c), v -> toggleDnd(c)));

        // Row 2: Audio | Sleep Timer
        int y2 = y1 + cap + gap;
        outCells.add(makeTile(c, panel, 0, y2, col, cap, unit, screenW, screenH, glassSource,
                "audio", I18n.s(c, "Audio", "Ses"), () -> audioState(c), v -> {
                    dismissAll(c);
                    openSoundSettings(c);
                }));
        outCells.add(makeTile(c, panel, col + gap, y2, col, cap, unit, screenW, screenH, glassSource,
                "timer", I18n.s(c, "Sleep Timer", "Uyku Zamanlayıcısı"), () -> timerState(c), v -> cycleSleepTimer(c)));

        // Row 3: Ambient Mode | Power Off
        int y3 = y2 + cap + gap;
        outCells.add(makeTile(c, panel, 0, y3, col, cap, unit, screenW, screenH, glassSource,
                "ambient", I18n.s(c, "Ambient Mode", "Ortam Modu"),
                () -> I18n.s(c, "Screensaver", "Ekran Koruyucu"),
                v -> {
                    dismissAll(c);
                    startAmbientMode(c);
                }));
        outCells.add(makeTile(c, panel, col + gap, y3, col, cap, unit, screenW, screenH, glassSource,
                "power", I18n.s(c, "Power Off", "Kapat"),
                () -> I18n.s(c, "Screen Off", "Ekranı Kapat"),
                v -> {
                    dismissAll(c);
                    if (!SleepReceiver.sleepNow(c) && c instanceof android.app.Activity) {
                        Perms.requestAdmin((android.app.Activity) c);
                    }
                }));

        // Row 4: 4 1x1 square/circle icon shortcuts
        int y4 = y3 + cap + gap;
        String[] icons = {"input", "remote", "gear", "search"};
        Runnable[] actions = {onInputs, onButtonMapper, onSettings, onSearch};
        for (int i = 0; i < icons.length; i++) {
            final Runnable act = actions[i];
            outCells.add(makeTile(c, panel, i * (small + gap), y4, small, small, unit, screenW, screenH, glassSource,
                    icons[i], null, null, v -> {
                        if (act != null) act.run();
                    }));
        }

        int totalH = y4 + small;
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(w, totalH, Gravity.TOP | Gravity.END);
        plp.topMargin = dp(unit, 36);
        plp.rightMargin = dp(unit, 28);
        panel.setLayoutParams(plp);
        return panel;
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

        final android.graphics.drawable.Drawable idleBg;
        if (glassSource != null) {
            MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                    radius, 0x24FFFFFF, glassSource, screenW, screenH, unit).adaptive().darker(0x59);
            glass.lens = dp(unit, 7);
            idleBg = glass;
        } else {
            GradientDrawable darkGlass = new GradientDrawable();
            darkGlass.setCornerRadius(radius);
            darkGlass.setColor(0xD81E1E26);
            darkGlass.setStroke(Math.max(1, dp(unit, 1)), 0x33FFFFFF);
            idleBg = darkGlass;
        }

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

        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? white : idleBg);
            int focusIconColor = ("wifi".equals(glyph) || "bluetooth".equals(glyph)) ? 0xFF0A84FF
                    : "ambient".equals(glyph) ? 0xFFD97706
                    : "power".equals(glyph) ? 0xFFD7372A
                    : 0xFF111114;
            gi.setImageBitmap(Glyphs.get(glyph, gs, has ? focusIconColor : baseIconColor));
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
                    .translationZ(has ? dp(unit, 10) : 0)
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
        if (!SleepReceiver.canSleep(c)) {
            if (c instanceof android.app.Activity) {
                Perms.requestAdmin((android.app.Activity) c);
            } else {
                startExternal(c, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            return;
        }
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

    // ---------------------------------------------------------------- Custom Control Panel Actions

    public static final String PREFS_CP = "control_panel";
    public static final String ACTION_WIFI = "wifi";
    public static final String ACTION_BLUETOOTH = "bluetooth";
    public static final String ACTION_SOUND = "sound";
    public static final String ACTION_INPUTS = "inputs";

    public static android.content.SharedPreferences cpPrefs(Context c) {
        return c.getSharedPreferences(PREFS_CP, Context.MODE_PRIVATE);
    }

    public static boolean hasCustomAction(Context c, String key) {
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
        cpPrefs(c).edit()
                .putString(key + "_pkg", pkg != null ? pkg : "")
                .putString(key + "_cls", cls != null ? cls : "")
                .putString(key + "_label", label != null ? label : "")
                .apply();
    }

    public static void clearCustomAction(Context c, String key) {
        cpPrefs(c).edit()
                .remove(key + "_pkg")
                .remove(key + "_cls")
                .remove(key + "_label")
                .apply();
    }

    public static void clearAllCustomActions(Context c) {
        cpPrefs(c).edit().clear().apply();
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
     * Launches Google Ambient Mode / Android TV Screensaver if an ambient app is installed on the TV;
     * otherwise enters tvOS Launcher's built-in Ambient Mode (hiding dock & control panel icon to show
     * the background scene + clock + date + weather + greeting).
     */
    public static void startAmbientMode(Context c) {
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
