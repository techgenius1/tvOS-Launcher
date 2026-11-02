package com.techgenius.tvoslauncher.present;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.techgenius.tvoslauncher.MainActivity;

/**
 * Starts the launcher (or presentation mode) as early as possible when the TV boots up.
 * Supports Direct Boot (LOCKED_BOOT_COMPLETED) and multi-stage foreground re-assertion
 * for Chinese OEM TVs where the stock launcher cannot be disabled and launches at startup.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String PREF_BOOT = "boot_prefs";
    private static final String KEY_LAUNCH_ON_BOOT = "launch_on_boot";
    private static volatile boolean bootLaunchTriggeredThisSession = false;

    private static SharedPreferences getDeviceBootPrefs(Context c) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Context dpCtx = c.createDeviceProtectedStorageContext();
                return dpCtx.getSharedPreferences(PREF_BOOT, Context.MODE_PRIVATE);
            } catch (Throwable ignored) {
            }
        }
        return c.getSharedPreferences(PREF_BOOT, Context.MODE_PRIVATE);
    }

    public static boolean isLaunchOnBootEnabled(Context c) {
        if (c == null) return false;
        try {
            SharedPreferences dp = getDeviceBootPrefs(c);
            if (dp.contains(KEY_LAUNCH_ON_BOOT)) {
                return dp.getBoolean(KEY_LAUNCH_ON_BOOT, false);
            }
        } catch (Throwable ignored) {
        }
        try {
            boolean val = c.getSharedPreferences(PREF_BOOT, Context.MODE_PRIVATE)
                    .getBoolean(KEY_LAUNCH_ON_BOOT, false);
            if (val) {
                getDeviceBootPrefs(c).edit().putBoolean(KEY_LAUNCH_ON_BOOT, true).apply();
            }
            return val;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setLaunchOnBootEnabled(Context c, boolean enabled) {
        if (c == null) return;
        try {
            c.getSharedPreferences(PREF_BOOT, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LAUNCH_ON_BOOT, enabled)
                    .apply();
        } catch (Throwable ignored) {
        }
        try {
            getDeviceBootPrefs(c)
                    .edit()
                    .putBoolean(KEY_LAUNCH_ON_BOOT, enabled)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void launchHomeImmediately(Context context) {
        if (context == null) return;
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    public static void triggerFastBootSequenceIfNeeded(Context context) {
        if (context == null || !isLaunchOnBootEnabled(context)) return;
        if (SystemClock.elapsedRealtime() > 180_000L && bootLaunchTriggeredThisSession) return;
        bootLaunchTriggeredThisSession = true;
        final Context appCtx = context.getApplicationContext();
        launchHomeImmediately(appCtx);
        Handler h = new Handler(Looper.getMainLooper());
        // Multi-stage rapid re-assertion so if an OEM/Chinese TV stock launcher starts slightly later
        // during boot, tvOS Launcher immediately takes the foreground on top of it.
        long[] delays = {500L, 1400L, 2800L, 5000L};
        for (long d : delays) {
            h.postDelayed(() -> launchHomeImmediately(appCtx), d);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        boolean isBootEvent = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a)
                || (Intent.ACTION_USER_PRESENT.equals(a) && SystemClock.elapsedRealtime() < 150_000L);

        if (!isBootEvent && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            return;
        }
        try {
            Presenter.ensureRunning(context);
        } catch (Throwable ignored) {
        }
        if (isBootEvent) {
            try {
                if (Presenter.enabled(context) && Presenter.startOnBoot(context)) {
                    Presenter.open(context);
                    return;
                }
            } catch (Throwable ignored) {
            }
            if (isLaunchOnBootEnabled(context)) {
                triggerFastBootSequenceIfNeeded(context);
            }
        }
    }
}
