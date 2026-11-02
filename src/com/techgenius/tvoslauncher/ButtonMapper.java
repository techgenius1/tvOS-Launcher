package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * System-wide Remote Button Mapper powered by {@link PowerService} (AccessibilityService).
 * Remaps any remote control button with independent actions for Single Tap (1x),
 * Double Tap (2x), and Triple Tap (3x) to open the Control Panel overlay, Recent Apps Switcher
 * overlay, or launch any installed app.
 */
public final class ButtonMapper {

    public static final String TARGET_CONTROL_PANEL = "control_panel";
    public static final String TARGET_RECENT_APPS = "recent_apps";
    public static final String TARGET_AMBIENT = "ambient";
    public static final String TARGET_LOCK_SCREEN = "lock_screen";
    public static final String TARGET_STANDBY = "standby";
    public static final String TARGET_POWER_OFF = "power_off";
    public static final String TARGET_APP_PREFIX = "app:";
    public static final String TARGET_KEYCODE_PREFIX = "keycode:";
    public static final String TARGET_PICK_KEYCODE = "__pick_keycode__";

    private static volatile int injectedKeyCode = -1;
    private static volatile long injectedKeyTimeMs = 0L;

    public static final int[][] COMMON_SEND_KEYCODES = {
        {KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0},
        {KeyEvent.KEYCODE_MEDIA_PLAY, 0},
        {KeyEvent.KEYCODE_MEDIA_PAUSE, 0},
        {KeyEvent.KEYCODE_MEDIA_STOP, 0},
        {KeyEvent.KEYCODE_MEDIA_NEXT, 0},
        {KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0},
        {KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 0},
        {KeyEvent.KEYCODE_MEDIA_REWIND, 0},
        {KeyEvent.KEYCODE_VOLUME_MUTE, 0},
        {KeyEvent.KEYCODE_VOLUME_UP, 0},
        {KeyEvent.KEYCODE_VOLUME_DOWN, 0},
        {KeyEvent.KEYCODE_TV_INPUT, 0},
        {KeyEvent.KEYCODE_GUIDE, 0},
        {KeyEvent.KEYCODE_INFO, 0},
        {KeyEvent.KEYCODE_CAPTIONS, 0},
        {KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, 0},
        {KeyEvent.KEYCODE_CHANNEL_UP, 0},
        {KeyEvent.KEYCODE_CHANNEL_DOWN, 0},
        {KeyEvent.KEYCODE_LAST_CHANNEL, 0},
        {KeyEvent.KEYCODE_MENU, 0},
        {KeyEvent.KEYCODE_SETTINGS, 0},
        {KeyEvent.KEYCODE_SEARCH, 0},
        {KeyEvent.KEYCODE_BACK, 0},
        {KeyEvent.KEYCODE_HOME, 0},
        {KeyEvent.KEYCODE_APP_SWITCH, 0},
        {KeyEvent.KEYCODE_NOTIFICATION, 0},
        {KeyEvent.KEYCODE_SLEEP, 0},
        {KeyEvent.KEYCODE_PROG_RED, 0},
        {KeyEvent.KEYCODE_PROG_GREEN, 0},
        {KeyEvent.KEYCODE_PROG_YELLOW, 0},
        {KeyEvent.KEYCODE_PROG_BLUE, 0}
    };

    public static String sendKeyCodeName(Context c, int code) {
        switch (code) {
            case KeyEvent.KEYCODE_VOLUME_MUTE: return I18n.s(c, "Mute / Unmute (164)", "Sesi Kapat / Aç (164)");
            case KeyEvent.KEYCODE_VOLUME_UP: return I18n.s(c, "Volume Up (24)", "Sesini Aç (24)");
            case KeyEvent.KEYCODE_VOLUME_DOWN: return I18n.s(c, "Volume Down (25)", "Sesini Kıs (25)");
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK: return I18n.s(c, "Audio Track (222)", "Ses Parçası / Dil (222)");
            case KeyEvent.KEYCODE_BACK: return I18n.s(c, "Back (4)", "Geri (4)");
            case KeyEvent.KEYCODE_HOME: return I18n.s(c, "Home (3)", "Ana Ekran (3)");
            case KeyEvent.KEYCODE_APP_SWITCH: return I18n.s(c, "App Switch / Recents (187)", "Son Uygulamalar (187)");
            case KeyEvent.KEYCODE_NOTIFICATION: return I18n.s(c, "Notifications (83)", "Bildirimler (83)");
            case KeyEvent.KEYCODE_SLEEP: return I18n.s(c, "Sleep / Standby (223)", "Uyku / Bekleme (223)");
            default: return keyLabel(c, code) + " (" + code + ")";
        }
    }

    public static final int TAP_SINGLE = 1;
    public static final int TAP_DOUBLE = 2;
    public static final int TAP_TRIPLE = 3;

    /** Mode 1 (Default): Original standard volume hold repeat timing (320ms delay, no IR pulse-burst injection). */
    public static final int VOLUME_FIX_MODE_1 = 1;
    /** Mode 2 (HY320 / Projector): AudioService dump watcher (Android 9-14) + IR pulse-burst handling. */
    public static final int VOLUME_FIX_MODE_2 = 2;

    private static final long MULTI_TAP_WINDOW_MS = 300L;
    private static final String PREFS = "button_mapper";
    private static final String KEY_ORDER = "mapped_keys";
    private static final String KEY_VOLUME_HOLD_FIX = "volume_hold_fix";
    private static final String KEY_VOLUME_HOLD_FIX_MODE = "volume_hold_fix_mode";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Map<Integer, Integer> pendingTapCounts = new HashMap<>();
    private static final Map<Integer, Runnable> pendingTapRunnables = new HashMap<>();

    public interface KeyCaptureListener {
        void onKeyCaptured(int keyCode);
    }

    public static volatile KeyCaptureListener captureListener;
    private static volatile java.lang.ref.WeakReference<AlertDialog> captureDialogRef;

    private ButtonMapper() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isVolumeHoldFixEnabled(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.getBoolean("volume_hold_fix_default_on", false)) {
            sp.edit()
                    .putBoolean("volume_hold_fix_default_on", true)
                    .putBoolean(KEY_VOLUME_HOLD_FIX, true)
                    .apply();
        }
        return sp.getBoolean(KEY_VOLUME_HOLD_FIX, true);
    }

    public static void setVolumeHoldFixEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_VOLUME_HOLD_FIX, enabled).apply();
        if (PowerService.instance != null) {
            PowerService.instance.syncVolumeHoldFix();
        }
    }

    public static int getVolumeHoldFixMode(Context c) {
        if (c == null) return VOLUME_FIX_MODE_1;
        int m = prefs(c).getInt(KEY_VOLUME_HOLD_FIX_MODE, VOLUME_FIX_MODE_1);
        return (m == VOLUME_FIX_MODE_2) ? VOLUME_FIX_MODE_2 : VOLUME_FIX_MODE_1;
    }

    public static void setVolumeHoldFixMode(Context c, int mode) {
        if (c == null) return;
        int valid = (mode == VOLUME_FIX_MODE_2) ? VOLUME_FIX_MODE_2 : VOLUME_FIX_MODE_1;
        prefs(c).edit().putInt(KEY_VOLUME_HOLD_FIX_MODE, valid).apply();
        if (PowerService.instance != null) {
            PowerService.instance.syncVolumeHoldFix();
        }
    }

    public static String getVolumeHoldFixModeLabel(Context c) {
        if (getVolumeHoldFixMode(c) == VOLUME_FIX_MODE_2) {
            return I18n.s(c, "Mode 2 (HY320 / Projector)", "Mod 2 (HY320 / Projeksiyon)");
        }
        return I18n.s(c, "Mode 1 (Standard · Default)", "Mod 1 (Standart · Varsayılan)");
    }

    public static boolean isKeyCustomMapped(Context c, int keyCode) {
        return hasAnyMapping(c, keyCode);
    }

    private static final String KEY_HOME_FIX = "home_button_fix";

    /**
     * Home button fix (off by default): when on, tvOS Launcher's accessibility service catches the
     * remote's HOME key and opens tvOS Launcher, for TVs whose firmware keeps sending HOME to the
     * stock launcher even after another home app was chosen.
     */
    public static boolean isHomeFixEnabled(Context c) {
        return c != null && prefs(c).getBoolean(KEY_HOME_FIX, false);
    }

    public static void setHomeFixEnabled(Context c, boolean enabled) {
        if (c == null) return;
        prefs(c).edit().putBoolean(KEY_HOME_FIX, enabled).apply();
    }

    /** Opens tvOS Launcher's Home screen from anywhere (used by the Home button fix). */
    public static void launchHome(Context c) {
        if (c == null) return;
        try {
            ControlPanelOverlay.dismiss();
            RecentAppsOverlay.dismiss();
        } catch (Throwable ignored) { }
        try {
            Intent home = new Intent(c, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(home);
        } catch (Throwable ignored) { }
    }

    public static boolean isServiceEnabled(Context c) {
        if (PowerService.instance != null) return true;
        try {
            String enabled = Settings.Secure.getString(
                    c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled != null) {
                String comp1 = c.getPackageName() + "/" + PowerService.class.getName();
                String comp2 = c.getPackageName() + "/.PowerService";
                return enabled.contains(comp1) || enabled.contains(comp2);
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static boolean isCapturableKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            default:
                return keyCode > 0;
        }
    }

    /** Returns ordered list of all keyCodes that have at least one tap action (1x, 2x, or 3x) mapped. */
    public static List<Integer> getMappedKeys(Context c) {
        SharedPreferences sp = prefs(c);
        Set<Integer> keys = new LinkedHashSet<>();
        String order = sp.getString(KEY_ORDER, "");
        if (!order.isEmpty()) {
            for (String part : order.split(",")) {
                try {
                    int code = Integer.parseInt(part.trim());
                    if (hasAnyMapping(c, code)) {
                        keys.add(code);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("key_") && e.getValue() instanceof String) {
                String val = (String) e.getValue();
                if (val == null || val.isEmpty()) continue;
                String sub = k.substring(4);
                int us = sub.indexOf('_');
                if (us > 0) sub = sub.substring(0, us);
                try {
                    int code = Integer.parseInt(sub);
                    if (hasAnyMapping(c, code)) {
                        keys.add(code);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        return new ArrayList<>(keys);
    }

    /** Backward-compatible map of keyCode -> summary or primary target. */
    public static Map<Integer, String> getMappings(Context c) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (int code : getMappedKeys(c)) {
            out.put(code, keySummaryLabel(c, code));
        }
        return out;
    }

    public static boolean hasAnyMapping(Context c, int keyCode) {
        return !getTarget(c, keyCode, TAP_SINGLE).isEmpty()
                || !getTarget(c, keyCode, TAP_DOUBLE).isEmpty()
                || !getTarget(c, keyCode, TAP_TRIPLE).isEmpty();
    }

    public static String getTarget(Context c, int keyCode) {
        return getTarget(c, keyCode, TAP_SINGLE);
    }

    public static String getTarget(Context c, int keyCode, int tapCount) {
        SharedPreferences sp = prefs(c);
        if (tapCount == TAP_SINGLE) {
            String t1 = sp.getString("key_" + keyCode + "_1", null);
            if (t1 != null) return t1;
            String legacy = sp.getString("key_" + keyCode, "");
            return legacy != null ? legacy : "";
        }
        String t = sp.getString("key_" + keyCode + "_" + tapCount, "");
        return t != null ? t : "";
    }

    public static void setMapping(Context c, int keyCode, String target) {
        setTarget(c, keyCode, TAP_SINGLE, target);
    }

    public static void setTarget(Context c, int keyCode, int tapCount, String target) {
        List<Integer> order = getMappedKeys(c);
        SharedPreferences sp = prefs(c);
        SharedPreferences.Editor ed = sp.edit();
        String cleanTarget = target == null ? "" : target.trim();
        if (cleanTarget.isEmpty()) {
            ed.remove("key_" + keyCode + "_" + tapCount);
            if (tapCount == TAP_SINGLE) ed.remove("key_" + keyCode);
        } else {
            ed.putString("key_" + keyCode + "_" + tapCount, cleanTarget);
            if (tapCount == TAP_SINGLE) ed.putString("key_" + keyCode, cleanTarget);
            if (!order.contains(keyCode)) order.add(keyCode);
        }
        ed.apply();

        // Clean up order list if all taps for this key were cleared
        if (!hasAnyMapping(c, keyCode)) {
            order.remove(Integer.valueOf(keyCode));
        }
        saveOrder(c, order);
    }

    public static void removeMapping(Context c, int keyCode) {
        List<Integer> order = getMappedKeys(c);
        order.remove(Integer.valueOf(keyCode));
        SharedPreferences.Editor ed = prefs(c).edit();
        ed.remove("key_" + keyCode);
        ed.remove("key_" + keyCode + "_1");
        ed.remove("key_" + keyCode + "_2");
        ed.remove("key_" + keyCode + "_3");
        ed.apply();
        saveOrder(c, order);
    }

    public static void clearAll(Context c) {
        boolean volFix = isVolumeHoldFixEnabled(c);
        boolean homeFix = isHomeFixEnabled(c);
        prefs(c).edit().clear()
                .putBoolean(KEY_VOLUME_HOLD_FIX, volFix)
                .putBoolean("volume_hold_fix_default_on", true)
                .putBoolean(KEY_HOME_FIX, homeFix)
                .apply();
        cancelPendingTaps();
    }

    /** Drops any half-finished multi-tap detection so no key can stay swallowed. */
    public static void cancelPendingTaps() {
        try {
            for (Runnable r : pendingTapRunnables.values()) MAIN.removeCallbacks(r);
        } catch (Throwable ignored) { }
        pendingTapRunnables.clear();
        pendingTapCounts.clear();
    }

    private static void saveOrder(Context c, List<Integer> order) {
        StringBuilder sb = new StringBuilder();
        for (int code : order) {
            if (sb.length() > 0) sb.append(',');
            sb.append(code);
        }
        prefs(c).edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    public static String tapTypeLabel(Context c, int tapCount) {
        switch (tapCount) {
            case TAP_SINGLE: return I18n.s(c, "Single Tap (1x)", "Tek Basış (1x)");
            case TAP_DOUBLE: return I18n.s(c, "Double Tap (2x)", "Çift Basış (2x)");
            case TAP_TRIPLE: return I18n.s(c, "Triple Tap (3x)", "Üçlü Basış (3x)");
            default: return tapCount + "x";
        }
    }

    /** Human-readable summary of all configured tap actions on a key (e.g. "1x: Control Panel · 2x: Recent Apps"). */
    public static String keySummaryLabel(Context c, int keyCode) {
        String t1 = getTarget(c, keyCode, TAP_SINGLE);
        String t2 = getTarget(c, keyCode, TAP_DOUBLE);
        String t3 = getTarget(c, keyCode, TAP_TRIPLE);
        List<String> parts = new ArrayList<>();
        if (!t1.isEmpty()) parts.add("1x: " + targetLabel(c, t1));
        if (!t2.isEmpty()) parts.add("2x: " + targetLabel(c, t2));
        if (!t3.isEmpty()) parts.add("3x: " + targetLabel(c, t3));
        if (parts.isEmpty()) return I18n.s(c, "None", "Yok");
        if (parts.size() == 1 && !t1.isEmpty() && t2.isEmpty() && t3.isEmpty()) {
            return "1x: " + targetLabel(c, t1);
        }
        return TextUtils.join("  ·  ", parts);
    }

    public static String keyLabel(Context c, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PROG_RED: return I18n.s(c, "Red Button", "Kırmızı Tuş");
            case KeyEvent.KEYCODE_PROG_GREEN: return I18n.s(c, "Green Button", "Yeşil Tuş");
            case KeyEvent.KEYCODE_PROG_YELLOW: return I18n.s(c, "Yellow Button", "Sarı Tuş");
            case KeyEvent.KEYCODE_PROG_BLUE: return I18n.s(c, "Blue Button", "Mavi Tuş");
            case KeyEvent.KEYCODE_MENU: return I18n.s(c, "Menu Button", "Menü Tuşu");
            case KeyEvent.KEYCODE_GUIDE: return I18n.s(c, "Guide Button", "Rehber Tuşu");
            case KeyEvent.KEYCODE_INFO: return I18n.s(c, "Info Button", "Bilgi Tuşu");
            case KeyEvent.KEYCODE_CAPTIONS: return I18n.s(c, "Subtitles / CC", "Altyazı Tuşu");
            case KeyEvent.KEYCODE_TV_INPUT: return I18n.s(c, "TV Input / Source", "Giriş / Kaynak Tuşu");
            case KeyEvent.KEYCODE_CHANNEL_UP: return I18n.s(c, "Channel Up", "Kanal Yukarı");
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return I18n.s(c, "Channel Down", "Kanal Aşağı");
            case KeyEvent.KEYCODE_BOOKMARK: return I18n.s(c, "Bookmark / Favorite", "Favori Tuşu");
            case KeyEvent.KEYCODE_DVR: return "DVR";
            case KeyEvent.KEYCODE_LAST_CHANNEL: return I18n.s(c, "Last Channel", "Son Kanal");
            case KeyEvent.KEYCODE_SETTINGS: return I18n.s(c, "Settings Button", "Ayarlar Tuşu");
            case KeyEvent.KEYCODE_SEARCH: return I18n.s(c, "Search Button", "Arama Tuşu");
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return I18n.s(c, "Play / Pause", "Oynat / Duraklat");
            case KeyEvent.KEYCODE_MEDIA_PLAY: return I18n.s(c, "Play", "Oynat");
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return I18n.s(c, "Pause", "Duraklat");
            case KeyEvent.KEYCODE_MEDIA_STOP: return I18n.s(c, "Stop", "Durdur");
            case KeyEvent.KEYCODE_MEDIA_REWIND: return I18n.s(c, "Rewind", "Geri Sar");
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return I18n.s(c, "Fast Forward", "İleri Sar");
            case KeyEvent.KEYCODE_MEDIA_NEXT: return I18n.s(c, "Next Track", "Sonraki Parça");
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return I18n.s(c, "Previous Track", "Önceki Parça");
            case KeyEvent.KEYCODE_MEDIA_RECORD: return I18n.s(c, "Record Button", "Kayıt Tuşu");
            default: {
                String raw = KeyEvent.keyCodeToString(keyCode);
                if (raw != null && raw.startsWith("KEYCODE_")) {
                    raw = raw.substring(8).replace('_', ' ');
                }
                return raw + " (#" + keyCode + ")";
            }
        }
    }

    public static String targetLabel(Context c, String target) {
        if (target == null || target.isEmpty()) return I18n.s(c, "None", "Yok");
        if (TARGET_CONTROL_PANEL.equals(target)) {
            return I18n.s(c, "Control Panel", "Denetim Paneli");
        }
        if (TARGET_RECENT_APPS.equals(target)) {
            return I18n.s(c, "Recent Apps Switcher", "Son Uygulamalar");
        }
        if (TARGET_AMBIENT.equals(target) || TARGET_STANDBY.equals(target)) {
            return I18n.s(c, "Ambient Mode / StandBy", "Ortam Modu / StandBy");
        }
        if (TARGET_LOCK_SCREEN.equals(target)) {
            return I18n.s(c, "Lock Screen", "Kilit Ekranı");
        }
        if (TARGET_POWER_OFF.equals(target)) {
            return I18n.s(c, "Power Off (Screen Off)", "Kapat (Ekranı Kapat)");
        }
        if (target.startsWith(TARGET_KEYCODE_PREFIX)) {
            try {
                int kc = Integer.parseInt(target.substring(TARGET_KEYCODE_PREFIX.length()).trim());
                return I18n.s(c, "Key: ", "Tuş: ") + sendKeyCodeName(c, kc);
            } catch (Exception ignored) {
                return target;
            }
        }
        if (target.startsWith(TARGET_APP_PREFIX)) {
            String pkg = target.substring(TARGET_APP_PREFIX.length());
            try {
                PackageManager pm = c.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                return pm.getApplicationLabel(ai).toString();
            } catch (Exception e) {
                return pkg;
            }
        }
        return target;
    }

    /**
     * Intercepts key events from {@link PowerService#onKeyEvent(KeyEvent)} or {@link MainActivity#dispatchKeyEvent(KeyEvent)}.
     * Supports Single Tap (1x), Double Tap (2x), and Triple Tap (3x) detection per key.
     * Returns true if the key event was consumed.
     */
    public static boolean handleKeyEvent(Context c, KeyEvent ev) {
        // Never let a bug in remapping lock the remote: any failure lets the key through untouched.
        try {
            return handleKeyEventInner(c, ev);
        } catch (Throwable t) {
            cancelPendingTaps();
            return false;
        }
    }

    private static boolean handleKeyEventInner(Context c, KeyEvent ev) {
        if (ev == null || c == null) return false;
        final int code = ev.getKeyCode();
        final int action = ev.getAction();

        // Do not intercept key events synthesized by our own "Send Key Code" action
        if (code == injectedKeyCode && (android.os.SystemClock.uptimeMillis() - injectedKeyTimeMs) < 600L) {
            return false;
        }

        // While StandBy / Ambient Mode or Screen Off Blackout is active:
        // ignore trailing events within 600ms of entry, and after 600ms let any key press wake/exit cleanly.
        if (MainActivity.activeInstance != null
                && MainActivity.activeInstance.resumed
                && MainActivity.activeInstance.ambientShowcase) {
            long elapsed = android.os.SystemClock.uptimeMillis() - MainActivity.activeInstance.ambientEnterTime;
            if (elapsed <= 600L) {
                return true;
            }
            return false;
        }
        if (ControlPanelOverlay.isScreenOffShowing()) {
            long elapsed = android.os.SystemClock.uptimeMillis() - ControlPanelOverlay.getScreenOffEnterTime();
            if (elapsed <= 600L) {
                return true;
            }
            return false;
        }

        // Never intercept or lock core navigation, selection, back, or volume keys under any scenario.
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
        }

        // Home button fix (off by default): intercept KEYCODE_HOME via Accessibility to launch tvOS Launcher
        if (code == KeyEvent.KEYCODE_HOME) {
            if (isHomeFixEnabled(c)) {
                if (action == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                    final Context appCtx = c.getApplicationContext();
                    MAIN.post(() -> launchHome(appCtx));
                }
                return true;
            }
            return false;
        }

        KeyCaptureListener cap = captureListener;
        if (cap != null) {
            AlertDialog activeDlg = captureDialogRef != null ? captureDialogRef.get() : null;
            if (activeDlg == null || !activeDlg.isShowing()) {
                captureListener = null;
                captureDialogRef = null;
            } else {
                if (isCapturableKey(code)) {
                    if (action == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                        MAIN.post(() -> cap.onKeyCaptured(code));
                    }
                    return true;
                }
                return false;
            }
        }

        String t1 = getTarget(c, code, TAP_SINGLE);
        String t2 = getTarget(c, code, TAP_DOUBLE);
        String t3 = getTarget(c, code, TAP_TRIPLE);
        if (t1.isEmpty() && t2.isEmpty() && t3.isEmpty()) {
            return false;
        }

        if (action == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            final Context appCtx = c.getApplicationContext();
            int maxTap = !t3.isEmpty() ? TAP_TRIPLE : (!t2.isEmpty() ? TAP_DOUBLE : TAP_SINGLE);

            Runnable existing = pendingTapRunnables.remove(code);
            if (existing != null) {
                MAIN.removeCallbacks(existing);
            }

            int prevCount = pendingTapCounts.containsKey(code) ? pendingTapCounts.get(code) : 0;
            final int newCount = prevCount + 1;

            if (newCount >= maxTap) {
                pendingTapCounts.remove(code);
                final String targetToRun = getTargetForTapOrFallback(c, code, newCount);
                if (!targetToRun.isEmpty()) {
                    MAIN.post(() -> executeTarget(appCtx, targetToRun));
                }
            } else {
                pendingTapCounts.put(code, newCount);
                Runnable dispatch = () -> {
                    pendingTapRunnables.remove(code);
                    Integer finalCountObj = pendingTapCounts.remove(code);
                    int finalCount = finalCountObj != null ? finalCountObj : newCount;
                    String targetToRun = getTargetForTapOrFallback(appCtx, code, finalCount);
                    if (!targetToRun.isEmpty()) {
                        executeTarget(appCtx, targetToRun);
                    }
                };
                pendingTapRunnables.put(code, dispatch);
                MAIN.postDelayed(dispatch, MULTI_TAP_WINDOW_MS);
            }
        }
        return true;
    }

    private static String getTargetForTapOrFallback(Context c, int keyCode, int tapCount) {
        String exact = getTarget(c, keyCode, tapCount);
        if (!exact.isEmpty()) return exact;
        if (tapCount == TAP_TRIPLE) {
            String t2 = getTarget(c, keyCode, TAP_DOUBLE);
            if (!t2.isEmpty()) return t2;
        }
        return getTarget(c, keyCode, TAP_SINGLE);
    }

    public static void executeTarget(Context c, String target) {
        if (target == null || target.isEmpty()) return;
        if (TARGET_CONTROL_PANEL.equals(target)) {
            RecentAppsOverlay.dismiss();
            ControlPanelOverlay.toggle(c);
            return;
        }
        if (TARGET_RECENT_APPS.equals(target)) {
            ControlPanelOverlay.dismiss();
            RecentAppsOverlay.toggle(c);
            return;
        }
        if (TARGET_AMBIENT.equals(target) || TARGET_STANDBY.equals(target)) {
            ControlPanelOverlay.dismiss();
            RecentAppsOverlay.dismiss();
            ControlPanelOverlay.startAmbientMode(c);
            return;
        }
        if (TARGET_LOCK_SCREEN.equals(target)) {
            ControlPanelOverlay.dismiss();
            RecentAppsOverlay.dismiss();
            MainActivity.triggerLockScreen(c);
            return;
        }
        if (TARGET_POWER_OFF.equals(target)) {
            ControlPanelOverlay.dismiss();
            RecentAppsOverlay.dismiss();
            SleepReceiver.sleepNow(c);
            return;
        }
        if (target.startsWith(TARGET_KEYCODE_PREFIX)) {
            try {
                int kc = Integer.parseInt(target.substring(TARGET_KEYCODE_PREFIX.length()).trim());
                sendKeyCode(c, kc);
            } catch (Exception ignored) { }
            return;
        }
        if (target.startsWith(TARGET_APP_PREFIX)) {
            ControlPanelOverlay.dismiss();
            RecentAppsOverlay.dismiss();
            String pkg = target.substring(TARGET_APP_PREFIX.length());
            RecentAppsOverlay.recordAppUsed(c, pkg);
            PackageManager pm = c.getPackageManager();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    c.startActivity(launch);
                } catch (Exception ignored) { }
            }
        }
    }

    // ---------------------------------------------------------------- Shared UI Dialogs (Settings & Setup Assistant)

    private static AlertDialog.Builder themedDialog(Activity a, String title) {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title);
    }

    /** Shows the "Press any button on your remote" capture dialog, then opens the 1x/2x/3x tap editor. */
    public static void showCaptureAndConfigureDialog(Activity a, Runnable onChanged) {
        final AlertDialog[] dlgRef = new AlertDialog[1];
        captureListener = keyCode -> a.runOnUiThread(() -> {
            captureListener = null;
            if (dlgRef[0] != null && dlgRef[0].isShowing()) {
                try {
                    dlgRef[0].dismiss();
                } catch (Exception ignored) { }
            }
            if (a instanceof SettingsActivity) {
                ((SettingsActivity) a).openButtonKeySubMenu(keyCode);
            } else {
                showConfigureKeyDialog(a, keyCode, onChanged);
            }
        });

        AlertDialog dlg = themedDialog(a, I18n.s(a, "Add new button to remap", "Yeniden eşlemek için yeni düğme ekle"))
                .setMessage(I18n.s(a,
                        "Press the button on your remote that you want to remap…\n\n(D-pad navigation, OK and Back are kept for normal navigation.)",
                        "Yeniden eşlemek istediğiniz kumanda düğmesine basın…\n\n(Yön tuşları, OK ve Geri tuşu normal gezinme için korunur.)"))
                .setNegativeButton(I18n.s(a, "Cancel", "İptal"), (d, w) -> captureListener = null)
                .setOnCancelListener(d -> captureListener = null)
                .setOnDismissListener(d -> {
                    captureListener = null;
                    captureDialogRef = null;
                })
                .create();
        dlgRef[0] = dlg;
        captureDialogRef = new java.lang.ref.WeakReference<>(dlg);
        dlg.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    captureListener = null;
                    d.dismiss();
                }
                return true;
            }
            if (isCapturableKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    captureListener = null;
                    d.dismiss();
                    if (a instanceof SettingsActivity) {
                        ((SettingsActivity) a).openButtonKeySubMenu(keyCode);
                    } else {
                        showConfigureKeyDialog(a, keyCode, onChanged);
                    }
                }
                return true;
            }
            return false;
        });
        dlg.show();
    }

    /** Shows the Single Tap (1x) / Double Tap (2x) / Triple Tap (3x) action configuration dialog for a key. */
    public static void showConfigureKeyDialog(Activity a, int keyCode, Runnable onChanged) {
        String keyName = keyLabel(a, keyCode);
        String t1 = getTarget(a, keyCode, TAP_SINGLE);
        String t2 = getTarget(a, keyCode, TAP_DOUBLE);
        String t3 = getTarget(a, keyCode, TAP_TRIPLE);
        boolean hasAny = !t1.isEmpty() || !t2.isEmpty() || !t3.isEmpty();

        List<String> items = new ArrayList<>();
        items.add(tapTypeLabel(a, TAP_SINGLE) + ":  " + targetLabel(a, t1));
        items.add(tapTypeLabel(a, TAP_DOUBLE) + ":  " + targetLabel(a, t2));
        items.add(tapTypeLabel(a, TAP_TRIPLE) + ":  " + targetLabel(a, t3));
        items.add("✓  " + I18n.s(a, "Done", "Tamam"));
        if (hasAny) {
            items.add("🗑  " + I18n.s(a, "Remove button remap", "Düğme eşlemesini kaldır"));
        }

        themedDialog(a, keyName)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        showTargetPickerForKeyTap(a, keyCode, TAP_SINGLE, onChanged);
                    } else if (which == 1) {
                        showTargetPickerForKeyTap(a, keyCode, TAP_DOUBLE, onChanged);
                    } else if (which == 2) {
                        showTargetPickerForKeyTap(a, keyCode, TAP_TRIPLE, onChanged);
                    } else if (which == 3) {
                        if (onChanged != null) onChanged.run();
                    } else if (which == 4) {
                        removeMapping(a, keyCode);
                        if (onChanged != null) onChanged.run();
                        Toast.makeText(a,
                                I18n.s(a, "Removed remap for " + keyName + ".", keyName + " eşlemesi kaldırıldı."),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(I18n.s(a, "Close", "Kapat"), (d, w) -> {
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }

    /** Shows the target action picker (Control Panel Overlay, Recent Apps Switcher Overlay, Installed Apps, or None) for a specific tap count. */
    public static void showTargetPickerForKeyTap(Activity a, int keyCode, int tapCount, Runnable onChanged) {
        String keyName = keyLabel(a, keyCode);
        String tapName = tapTypeLabel(a, tapCount);
        String current = getTarget(a, keyCode, tapCount);

        List<String> labels = new ArrayList<>();
        List<String> targets = new ArrayList<>();

        // 1. Control Panel (Overlay)
        labels.add("🎛  " + I18n.s(a, "Control Panel (Overlay)", "Denetim Paneli (Ekran Üstü)"));
        targets.add(TARGET_CONTROL_PANEL);

        // 2. Recent Apps Switcher (Overlay)
        labels.add("🗂  " + I18n.s(a, "Recent Apps Switcher (Overlay)", "Son Uygulamalar Değiştiricisi (Ekran Üstü)"));
        targets.add(TARGET_RECENT_APPS);

        // 3. Ambient Mode / StandBy
        labels.add("🌙  " + I18n.s(a, "Ambient Mode / StandBy", "Ortam Modu / StandBy"));
        targets.add(TARGET_AMBIENT);

        // 4. Power Off (Screen Off)
        labels.add("⏻  " + I18n.s(a, "Power Off (Screen Off)", "Kapat (Ekranı Kapat)"));
        targets.add(TARGET_POWER_OFF);

        // 5. Send Key Code (Media, Mute, Input, Subtitles, Custom KeyCode...)
        labels.add("⌨  " + I18n.s(a, "Send Key Code…", "Tuş Kodu Gönder…"));
        targets.add(TARGET_PICK_KEYCODE);

        // 6. Clear / None option if already set
        if (!current.isEmpty()) {
            labels.add("🚫  " + I18n.s(a, "None (Clear action)", "Yok (Eylemi temizle)"));
            targets.add("");
        }

        // 7. Installed apps (Leanback + Launcher)
        PackageManager pm = a.getPackageManager();
        Set<String> seenPkgs = new HashSet<>();
        List<String[]> appPairs = new ArrayList<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(a.getPackageName()) || seenPkgs.contains(pkg)) continue;
                seenPkgs.add(pkg);
                String appLabel = ri.loadLabel(pm).toString();
                appPairs.add(new String[]{appLabel, TARGET_APP_PREFIX + pkg});
            }
        }
        Collections.sort(appPairs, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        for (String[] pair : appPairs) {
            labels.add(pair[0]);
            targets.add(pair[1]);
        }

        themedDialog(a, keyName + " · " + tapName)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String chosenTarget = targets.get(which);
                    if (TARGET_PICK_KEYCODE.equals(chosenTarget)) {
                        showSendKeyCodePickerDialog(a, keyCode, tapCount, () -> {
                            if (onChanged != null) onChanged.run();
                            showConfigureKeyDialog(a, keyCode, onChanged);
                        });
                        return;
                    }
                    setTarget(a, keyCode, tapCount, chosenTarget);
                    if (onChanged != null) onChanged.run();
                    // Re-open the key's 1x/2x/3x tap dialog so user can easily configure double/triple tap too
                    showConfigureKeyDialog(a, keyCode, onChanged);
                })
                .setNegativeButton(I18n.s(a, "Back", "Geri"), (d, w) -> showConfigureKeyDialog(a, keyCode, onChanged))
                .show();
    }

    public static void showSendKeyCodePickerDialog(Activity a, int keyCode, int tapCount, Runnable onDone) {
        List<String> items = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        items.add("🔢  " + I18n.s(a, "Custom Key Code (Enter Number…)", "Özel Tuş Kodu (Sayı Girin…)"));
        codes.add(-1);
        for (int[] row : COMMON_SEND_KEYCODES) {
            int kc = row[0];
            items.add(sendKeyCodeName(a, kc));
            codes.add(kc);
        }
        themedDialog(a, I18n.s(a, "Send Key Code", "Tuş Kodu Gönder"))
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    int picked = codes.get(which);
                    if (picked == -1) {
                        showCustomKeyCodeInputDialog(a, keyCode, tapCount, onDone);
                    } else {
                        setTarget(a, keyCode, tapCount, TARGET_KEYCODE_PREFIX + picked);
                        if (onDone != null) onDone.run();
                    }
                })
                .setNegativeButton(I18n.s(a, "Cancel", "İptal"), null)
                .show();
    }

    public static void showCustomKeyCodeInputDialog(Activity a, int keyCode, int tapCount, Runnable onDone) {
        android.widget.EditText in = new android.widget.EditText(a);
        in.setSingleLine(true);
        in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        in.setHint(I18n.s(a, "e.g. 85 (Play/Pause), 164 (Mute), 178 (TV Input)", "örn. 85 (Oynat/Duraklat), 164 (Sessiz), 178 (TV Girişi)"));
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        int pad = Math.round(16 * Ui.unit(a));
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        AlertDialog dlg = themedDialog(a, I18n.s(a, "Enter Android Key Code", "Android Tuş Kodunu Girin"))
                .setView(box)
                .setPositiveButton(I18n.s(a, "Save", "Kaydet"), (d, w) -> {
                    try {
                        int customKc = Integer.parseInt(in.getText().toString().trim());
                        if (customKc > 0) {
                            setTarget(a, keyCode, tapCount, TARGET_KEYCODE_PREFIX + customKc);
                            if (onDone != null) onDone.run();
                        }
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(I18n.s(a, "Cancel", "İptal"), null)
                .show();
        Ui.setupDialogEditText(in, dlg);
        in.requestFocus();
    }

    /**
     * Dispatches any Android KeyEvent keycode across the system:
     * 1. Uses AccessibilityService global actions for Back/Home/Recents/Notifications/Sleep.
     * 2. Uses AudioManager for Volume Up/Down/Mute and Media keys (Play/Pause/Next/Prev/Stop/FF/Rew)
     *    so it works immediately across all apps without needing root or ADB.
     * 3. Uses Instrumentation / Local ADB fallback for arbitrary hardware keycodes.
     */
    public static void sendKeyCode(Context c, int keyCode) {
        if (c == null || keyCode <= 0) return;
        injectedKeyCode = keyCode;
        injectedKeyTimeMs = android.os.SystemClock.uptimeMillis();

        // 1. System / Navigation Global Actions via AccessibilityService
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (PowerService.sendGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) {
                    return;
                }
                break;
            case KeyEvent.KEYCODE_HOME:
                if (PowerService.sendGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)) {
                    return;
                }
                launchHome(c);
                return;
            case KeyEvent.KEYCODE_APP_SWITCH:
                if (PowerService.sendGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)) {
                    return;
                }
                RecentAppsOverlay.toggle(c);
                return;
            case KeyEvent.KEYCODE_NOTIFICATION:
                if (PowerService.sendGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) {
                    return;
                }
                break;
            case KeyEvent.KEYCODE_SLEEP:
                SleepReceiver.sleepNow(c);
                return;
        }

        // 2. Volume & Media Session keys via AudioManager (works over every app with zero ADB requirement!)
        try {
            android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
                    return;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
                    return;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            android.media.AudioManager.ADJUST_TOGGLE_MUTE, android.media.AudioManager.FLAG_SHOW_UI);
                    return;
                }
                switch (keyCode) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    case KeyEvent.KEYCODE_MEDIA_REWIND:
                    case KeyEvent.KEYCODE_MEDIA_RECORD:
                    case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                    case KeyEvent.KEYCODE_CAPTIONS: {
                        long now = android.os.SystemClock.uptimeMillis();
                        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                        if (keyCode != KeyEvent.KEYCODE_CAPTIONS && keyCode != KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK) {
                            return;
                        }
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Inject via Instrumentation (if inside our own process) or Local ADB (if paired & open),
        //    plus intent fallbacks for Settings / TV Input
        final Context appCtx = c.getApplicationContext();
        new Thread(() -> {
            boolean sent = false;
            try {
                new android.app.Instrumentation().sendKeyDownUpSync(keyCode);
                sent = true;
            } catch (Throwable ignored) {
            }
            if (!sent) {
                if (LocalAdb.portReachable && LocalAdb.isPaired(appCtx)) {
                    LocalAdb.sendKeyEventViaAdbAsync(appCtx, keyCode);
                    return;
                }
                // Non-ADB fallback for Settings / TV Input / Search keycodes
                MAIN.post(() -> {
                    try {
                        if (keyCode == KeyEvent.KEYCODE_SETTINGS) {
                            appCtx.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else if (keyCode == KeyEvent.KEYCODE_TV_INPUT) {
                            ControlPanelOverlay.openInputs(appCtx, null);
                        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
                            Intent s = new Intent(appCtx, MainActivity.class)
                                    .putExtra("panel", "search")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            appCtx.startActivity(s);
                        }
                    } catch (Throwable ignored) { }
                });
            }
        }, "btn-send-keycode").start();
    }
}
