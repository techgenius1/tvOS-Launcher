package com.techgenius.tvoslauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Permissions, asked for on screen wherever the TV allows it; adb is only the last resort.
 * - Location (Wi-Fi name): the normal runtime prompt.
 * - Notification access (Continue Watching, Do Not Disturb): the system's notification-access
 *   screen (Android 11+ opens tvOS Launcher's own switch directly).
 */
final class Perms {
    static final int REQ_LOCATION = 41;

    private Perms() {
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

    private static final String[] STOCK_TV_HOME_PACKAGES = {
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "org.droidtv.ui.home",
        "org.droidtv.home",
        "com.tpvision.home",
        "com.tcl.cyberui",
        "com.tcl.tv.launcher",
        "com.tcl.launcher",
        "com.falcon.launcher",
        "com.ffalcon.launcher",
        "com.hisense.tv.launcher",
        "com.mi.tv.launcher",
        "com.mitv.tvhome",
        "com.sony.dtv.homemenu",
        "com.skyworth.tvos.launcher",
        "com.coocaa.tv.launcher",
        "com.haier.tv.launcher",
        "com.homwee.launcher",
        "com.zeasn.whaleos.launcher",
        "com.sdmc.launcher"
    };

    static String[] getStockTvHomePackages() {
        return STOCK_TV_HOME_PACKAGES;
    }

    static boolean hasEnabledStockLauncher(android.content.Context c) {
        if (c == null) return false;
        PackageManager pm = c.getPackageManager();
        for (String pkg : STOCK_TV_HOME_PACKAGES) {
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai != null && ai.enabled) {
                    int state = pm.getApplicationEnabledSetting(pkg);
                    if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean isOemLockedHomeTv(android.content.Context c) {
        String brand = (Build.BRAND != null ? Build.BRAND : "").toLowerCase(java.util.Locale.US);
        String manuf = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "").toLowerCase(java.util.Locale.US);
        String model = (Build.MODEL != null ? Build.MODEL : "").toLowerCase(java.util.Locale.US);
        if (brand.contains("philips") || manuf.contains("philips")
                || brand.contains("tpvision") || manuf.contains("tpvision")
                || brand.contains("tpv") || manuf.contains("tpv")
                || brand.contains("tcl") || manuf.contains("tcl") || model.contains("tcl")) {
            return true;
        }
        if (c != null) {
            PackageManager pm = c.getPackageManager();
            String[] oemPkgs = {
                "org.droidtv.ui.home", "org.droidtv.home", "org.droidtv.settings",
                "com.tpvision.home", "com.tcl.cyberui", "com.tcl.tv.launcher"
            };
            for (String p : oemPkgs) {
                try {
                    if (pm.getApplicationInfo(p, 0) != null) return true;
                } catch (Throwable ignored) { }
            }
        }
        return false;
    }

    static boolean isDefaultHome(Activity a) {
        return isDefaultHome((android.content.Context) a);
    }

    static boolean isDefaultHome(android.content.Context c) {
        if (c == null) return false;
        try {
            // If Home Button Fix is enabled in Button Mapper and Accessibility is active, Home opens tvOS Launcher.
            if (ButtonMapper.isHomeFixEnabled(c) && ButtonMapper.isServiceEnabled(c)) {
                return true;
            }
            PackageManager pm = c.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            boolean resolvedToUs = ri != null && ri.activityInfo != null
                    && c.getPackageName().equals(ri.activityInfo.packageName);
            if (!resolvedToUs) {
                return false;
            }
            // On Android 10+ (API 29+), also verify RoleManager.ROLE_HOME
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = c.getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if (!rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        return false;
                    }
                }
            }
            // On TCL and Philips Android 11/12 TVs, the firmware intercepts HOME and launches the stock
            // Android TV / Google TV / OEM launcher as long as that stock launcher package remains enabled.
            if (Build.VERSION.SDK_INT >= 30 && isOemLockedHomeTv(c) && hasEnabledStockLauncher(c)) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Become the home screen: offers reliable methods for all Android TVs including TCL & Philips
     * Android 11/12 TVs where standard RoleManager / Home Settings UI is stripped or ignored.
     */
    static void requestDefaultHome(Activity a) {
        if (a == null) return;
        // If inside SettingsActivity or SetupActivity, open the native Default Home setup sub-menu
        if (a instanceof SettingsActivity) {
            ((SettingsActivity) a).openDefaultHomeSubMenu();
            return;
        }
        if (a instanceof SetupActivity) {
            ((SetupActivity) a).openDefaultHomeSubMenu();
            return;
        }

        String[] items = {
            I18n.s(a, "Set Default via Local ADB (Recommended)", "Yerel ADB ile Varsayılan Yap (Önerilen)"),
            I18n.s(a, "Disable Stock TV Launchers (TCL / Philips / Google TV)", "Stok TV Başlatıcılarını Devre Dışı Bırak (TCL / Philips / Google TV)"),
            I18n.s(a, "Enable Home Button Fix (Accessibility Remap)", "Ana Ekran Tuşu Düzeltmesini Aç (Erişilebilirlik)"),
            I18n.s(a, "Open Android Default Home Settings", "Android Varsayılan Ana Ekran Ayarlarını Aç")
        };

        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.s(a, "Set Default Home Launcher", "Varsayılan Ana Ekran Yap"))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            LocalAdb.setDefaultLauncherViaAdb(a, (ok, msg) -> a.runOnUiThread(() -> {
                                if (a.isFinishing()) return;
                                if (ok) {
                                    android.widget.Toast.makeText(a,
                                            I18n.s(a, "Default home launcher set via ADB!", "Varsayılan ana ekran ADB ile ayarlandı!"),
                                            android.widget.Toast.LENGTH_SHORT).show();
                                    if (hasEnabledStockLauncher(a)) {
                                        CleanYourTv.showDisableSystemLaunchersDialog(a, null);
                                    }
                                } else {
                                    lastResort(a, I18n.s(a, "Home screen", "Ana ekran"),
                                            "adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.techgenius.tvoslauncher\n"
                                            + "adb shell cmd package set-home-activity com.techgenius.tvoslauncher/.MainActivity\n"
                                            + "adb shell pm disable-user --user 0 com.google.android.tvlauncher");
                                }
                            }));
                            break;
                        case 1:
                            CleanYourTv.showDisableSystemLaunchersDialog(a, null);
                            break;
                        case 2:
                            ButtonMapper.setHomeFixEnabled(a, true);
                            if (!ButtonMapper.isServiceEnabled(a)) {
                                if (LocalAdb.hasWriteSecureSettings(a) && LocalAdb.repairPermissionsViaSecureSettings(a)) {
                                    android.widget.Toast.makeText(a,
                                            I18n.s(a, "Home Button Fix & Accessibility enabled!", "Ana Ekran Tuşu Düzeltmesi ve Erişilebilirlik etkinleştirildi!"),
                                            android.widget.Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Intent acc = accessibilityScreen(a);
                                if (acc != null && tryStart(a, acc)) {
                                    return;
                                }
                                LocalAdb.grantAllPermissions(a, null);
                            }
                            android.widget.Toast.makeText(a,
                                    I18n.s(a, "Home Button Fix enabled!", "Ana Ekran Tuşu Düzeltmesi etkinleştirildi!"),
                                    android.widget.Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            openSystemHomeSettingsUi(a);
                            break;
                    }
                })
                .setNegativeButton(I18n.s(a, "Cancel", "İptal"), null)
                .show();
    }

    static void openSystemHomeSettingsUi(Activity a) {
        if (a == null) return;
        if (tryStart(a, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        if (Build.VERSION.SDK_INT >= 24 && tryStart(a, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                        && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    Intent roleIntent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME);
                    if (roleIntent != null) {
                        a.startActivityForResult(roleIntent, 42);
                        return;
                    }
                }
            } catch (Throwable ignored) { }
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        if (tryStart(a, Intent.createChooser(home, I18n.s(a, "Select Home App", "Ana Ekran Uygulamasını Seçin")))) return;
        tryStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
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
