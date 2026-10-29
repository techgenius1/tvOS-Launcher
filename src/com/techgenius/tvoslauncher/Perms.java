package com.techgenius.tvoslauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Permissions, asked for on screen wherever the TV allows it; adb is only the last resort.
 * - Location (Wi-Fi name): the normal runtime prompt.
 * - Device admin (Power Off, Sleep Timer): Android's "Activate device admin app?" screen.
 * - Notification access (Continue Watching, Do Not Disturb): the system's notification-access
 *   screen (Android 11+ opens tvOS Launcher's own switch directly).
 * Some TVs (e.g. Android 9 builds) have no screen for the last two; only then is the
 * one-time computer command shown.
 */
final class Perms {
    static final int REQ_LOCATION = 41;

    private Perms() {
    }

    static ComponentName admin(Activity a) {
        return new ComponentName(a, SleepReceiver.Admin.class);
    }

    static ComponentName listener(Activity a) {
        return new ComponentName(a, MediaWatchService.class);
    }

    static boolean hasLocation(Activity a) {
        try {
            return a != null && a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Some TVs (e.g. this office TV's Android 9 build) lack device admin entirely; nothing can enable it. */
    /** Power Off is possible via device admin, or via tvOS Launcher's accessibility service (any TV with that screen). */
    static boolean adminSupported(Activity a) {
        try {
            return adminWorks(a) || accessibilityScreen(a) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static Intent accessibilityScreen(Activity a) {
        if (a == null) return null;
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            ComponentName cn = i.resolveActivity(a.getPackageManager());
            if (cn == null || cn.getClassName() == null || cn.getClassName().contains("Stub")) {
                return null;
            }
            return i;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean hasAdmin(Activity a) {
        if (PowerService.instance != null) return true;
        if (a == null) return false;
        try {
            DevicePolicyManager dpm = a.getSystemService(DevicePolicyManager.class);
            return dpm != null && dpm.isAdminActive(admin(a));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Device admin actually works only when Android's device-policy service is on. */
    static boolean adminWorks(Activity a) {
        if (a == null) return false;
        try {
            DevicePolicyManager dpm = a.getSystemService(DevicePolicyManager.class);
            return dpm != null && a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Android turns notification listeners off for other apps on low-RAM (1 GB) TVs. */
    static boolean listenerSupported(Activity a) {
        if (a == null) return false;
        try {
            android.app.ActivityManager am = a.getSystemService(android.app.ActivityManager.class);
            return am != null && !am.isLowRamDevice();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean hasListener(Activity a) {
        try {
            return MediaWatchService.granted(a);
        } catch (Throwable t) {
            return false;
        }
    }

    static void requestLocation(Activity a) {
        if (!hasLocation(a)) a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    static void requestAdmin(Activity a) {
        if (hasAdmin(a)) return;
        if (!adminSupported(a)) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(I18n.s(a, "Not available on this TV", "Bu TV'de kullan\u0131lam\u0131yor"))
                    .setMessage(I18n.s(a,
                            "This TV doesn't let apps turn the screen off, so Power Off and the Sleep Timer can't work here. Use the power button on the remote.",
                            "Bu TV uygulamalar\u0131n ekran\u0131 kapatmas\u0131na izin vermiyor, bu nedenle Kapatma ve Uyku Zamanlay\u0131c\u0131s\u0131 burada \u00e7al\u0131\u015famaz. Kumandadaki g\u00fc\u00e7 d\u00fc\u011fmesini kullan\u0131n."))
                    .setPositiveButton(I18n.s(a, "OK", "Tamam"), null).show();
            return;
        }
        if (!adminWorks(a)) {
            // Device admin is switched off on this TV: use tvOS Launcher's accessibility switch instead.
            Intent acc = accessibilityScreen(a);
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(I18n.s(a, "Turn on Power Off", "Kapatma \u00d6zelli\u011fini A\u00e7\u0131n"))
                    .setMessage(I18n.s(a,
                            "In the next screen, choose \u201ctvOS Launcher \u2013 Button Mapper & Control Panel\u201d and turn it on.",
                            "Sonraki ekranda \u201ctvOS Launcher \u2013 Button Mapper & Control Panel\u201d se\u00e7ene\u011fini se\u00e7ip a\u00e7\u0131n."))
                    .setPositiveButton(I18n.s(a, "Open Accessibility", "Eri\u015filebilirli\u011fi A\u00e7"), (d, w) -> tryStart(a, acc))
                    .setNegativeButton(I18n.s(a, "Cancel", "\u0130ptal"), null).show();
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(a))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        I18n.s(a,
                                "Lets tvOS Launcher turn the screen off for Power Off and the Sleep Timer. It can't do anything else.",
                                "tvOS Launcher'\u0131n Kapatma ve Uyku Zamanlay\u0131c\u0131s\u0131 i\u00e7in ekran\u0131 kapatmas\u0131n\u0131 sa\u011flar. Ba\u015fka hi\u00e7bir i\u015flem yapamaz."));
        if (tryStart(a, i)) return;
        lastResort(a, I18n.s(a, "Power Off & Sleep Timer", "Kapatma ve Uyku Zamanlay\u0131c\u0131s\u0131"),
                "adb shell dpm set-active-admin com.techgenius.tvoslauncher/.SleepReceiver\\$Admin");
    }

    static void requestListener(Activity a) {
        if (hasListener(a)) return;
        if (!listenerSupported(a)) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(I18n.s(a, "Not available on this TV", "Bu TV'de kullan\u0131lam\u0131yor"))
                    .setMessage(I18n.s(a,
                            "This TV has limited memory, so Android doesn't let apps read what's playing. Continue Watching and Do Not Disturb can't work here.",
                            "Bu TV'nin belle\u011fi s\u0131n\u0131rl\u0131 oldu\u011fundan Android uygulamalar\u0131n ne oynat\u0131ld\u0131\u011f\u0131n\u0131 okumas\u0131na izin vermiyor. \u0130zlemeye Devam Et ve Rahats\u0131z Etmeyin burada \u00e7al\u0131\u015famaz."))
                    .setPositiveButton(I18n.s(a, "OK", "Tamam"), null).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(a).flattenToString());
            if (tryStart(a, detail)) return;
        }
        if (tryStart(a, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return;
        if (tryStart(a, new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"))) return;
        lastResort(a, I18n.s(a, "Continue Watching & Do Not Disturb", "\u0130zlemeye Devam Et ve Rahats\u0131z Etmeyin"),
                "adb shell cmd notification allow_listener com.techgenius.tvoslauncher/com.techgenius.tvoslauncher.MediaWatchService");
    }

    /** Starts a system screen if the TV really has it (many TVs map settings actions to empty stubs). */
    private static boolean tryStart(Activity a, Intent i) {
        if (a == null || i == null) return false;
        try {
            ComponentName cn = i.resolveActivity(a.getPackageManager());
            if (cn == null || cn.getClassName() == null || cn.getClassName().contains("Stub")) return false;
            a.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- default home screen

    static boolean isDefaultHome(Activity a) {
        if (a == null) return false;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && a.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Become the home screen: Android 10+'s "Set as default home app?" prompt, else the TV's Home
     * app settings (when it is a real screen), else Android's chooser, and only then the command.
     */
    static void requestDefaultHome(Activity a) {
        if (isDefaultHome(a)) return;
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                try {
                    a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 42);
                    return;
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        if (tryStart(a, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        // With no default chosen yet, asking for Home shows Android's chooser ("Always" makes it stick).
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo def = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        boolean noDefault = def == null || def.activityInfo == null || "android".equals(def.activityInfo.packageName);
        if (noDefault && tryStart(a, home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
        lastResort(a, I18n.s(a, "Home screen", "Ana ekran"),
                "adb shell cmd package set-home-activity com.techgenius.tvoslauncher/.MainActivity\n"
                        + "(on Google TV also: adb shell pm disable-user --user 0 com.google.android.tvlauncher)");
    }

    /** Only when the TV has no screen for it: explain plainly, with the command for whoever installs the TV. */
    private static void lastResort(Activity a, String what, String cmd) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(what)
                .setMessage(I18n.s(a,
                        "This TV's settings have no switch for this permission. Whoever set up the TV can turn it on once from a computer on the same network:\n\n",
                        "Bu TV'nin ayarlar\u0131nda bu izin i\u00e7in bir se\u00e7enek yok. TV'yi kuran ki\u015fi ayn\u0131 a\u011fdaki bir bilgisayardan bir kez etkinle\u015ftirebilir:\n\n") + cmd)
                .setPositiveButton(I18n.s(a, "OK", "Tamam"), null)
                .setNeutralButton(I18n.s(a, "App info", "Uygulama bilgisi"), (d, w) -> tryStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName()))))
                .show();
    }
}
