package com.techgenius.tvoslauncher.present;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.techgenius.tvoslauncher.MainActivity;

/**
 * After boot: starts the admin server if Presentation is enabled, opens the presentation
 * full-screen when "Start on boot" is on, and optionally launches tvOS Launcher automatically
 * right after system boot when enabled in Advanced Settings (off by default).
 */
public class BootReceiver extends BroadcastReceiver {
    public static final String KEY_LAUNCH_ON_BOOT = "launch_on_boot";

    public static boolean isLaunchOnBootEnabled(Context c) {
        if (c == null) return false;
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(KEY_LAUNCH_ON_BOOT, false);
    }

    public static void setLaunchOnBootEnabled(Context c, boolean enabled) {
        if (c == null) return;
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LAUNCH_ON_BOOT, enabled)
                .apply();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (c == null || i == null) return;
        String a = i.getAction();
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a);
        if (!isBoot && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        Presenter.ensureRunning(c);
        if (isBoot) {
            if (isLaunchOnBootEnabled(c)) {
                try {
                    Intent home = new Intent(c, MainActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    c.startActivity(home);
                } catch (Throwable ignored) { }
            }
            Presenter.openOnBootOnce(c);
        }
    }
}
