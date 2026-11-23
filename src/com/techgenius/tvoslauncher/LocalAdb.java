package com.techgenius.tvoslauncher;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;

/**
 * Pure-Java local ADB client (127.0.0.1:5555) for granting tvOS Launcher's system permissions
 * automatically during Easy Setup when ADB / Network Debugging is enabled on the TV.
 */
final class LocalAdb {
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int A_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096;

    private static final int ADB_AUTH_TOKEN = 1;
    private static final int ADB_AUTH_SIGNATURE = 2;
    private static final int ADB_AUTH_RSAPUBLICKEY = 3;

    // ASN.1 DigestInfo prefix for SHA-1 (15 bytes) + 20-byte SHA-1 token = 35 bytes
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = new byte[]{
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    interface Callback {
        void onResult(boolean success, String message);
    }

    interface VolumeKeyListener {
        void onVolumeKey(int dir, boolean down, boolean isRepeat);
    }

    interface BooleanSupplier {
        boolean getAsBoolean();
    }

    /** Mode 0: Grant WRITE_SECURE_SETTINGS once via ADB, then close ADB port 5555 (Recommended / Secure). */
    public static final int ADB_MODE_SECURE_CLOSE = 0;
    /** Mode 1: Keep network ADB enabled on port 5555 for runtime ADB features (CleanYourTV, etc.). */
    public static final int ADB_MODE_PERSISTENT = 1;

    static volatile boolean portReachable = false;
    static volatile boolean volWatchConnected = false;
    static volatile int activeAdbPort = 5555;
    private static final Object ADB_SESSION_LOCK = new Object();
    static volatile boolean setupGrantInProgress = false;
    private static final android.os.Handler KEEP_ALIVE_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static Context keepAliveAppCtx;
    private static boolean keepAliveWatchdogScheduled = false;
    private static android.database.ContentObserver accSettingsObserver;
    private static volatile boolean repairInProgress = false;
    private static volatile long lastRepairAttemptMs = 0L;
    private static final long KEEP_ALIVE_INTERVAL_MS = 60_000L;
    private static final long REPAIR_COOLDOWN_MS = 2_500L;

    private static final Runnable KEEP_ALIVE_TICK = new Runnable() {
        @Override
        public void run() {
            KEEP_ALIVE_HANDLER.removeCallbacks(this);
            if (keepAliveAppCtx == null || !shouldAutoRepairAccessibility(keepAliveAppCtx)) {
                keepAliveWatchdogScheduled = false;
                return;
            }
            try {
                android.os.PowerManager pm = keepAliveAppCtx.getSystemService(android.os.PowerManager.class);
                if (pm == null || pm.isInteractive()) {
                    checkAndRepairPermissionsIfNeeded(keepAliveAppCtx, false);
                }
            } catch (Throwable ignored) {
            }
            KEEP_ALIVE_HANDLER.postDelayed(this, KEEP_ALIVE_INTERVAL_MS);
        }
    };

    private LocalAdb() { }

    static int getActiveAdbPort(Context c) {
        if (c != null && activeAdbPort == 5555) {
            int saved = c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).getInt("adb_port", 5555);
            if (saved > 0) activeAdbPort = saved;
        }
        return activeAdbPort > 0 ? activeAdbPort : 5555;
    }

    private static void saveActiveAdbPort(Context c, int port) {
        if (port <= 0) return;
        activeAdbPort = port;
        if (c != null) {
            try {
                c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).edit().putInt("adb_port", port).apply();
            } catch (Throwable ignored) {
            }
        }
    }

    private static List<Integer> getCandidateAdbPorts(Context c) {
        List<Integer> ports = new ArrayList<>();
        int cur = getActiveAdbPort(c);
        if (cur > 0) ports.add(cur);
        // Check system properties (e.g. service.adb.tcp.port, persist.adb.tcp.port)
        String[] propNames = {"service.adb.tcp.port", "persist.adb.tcp.port", " persist.vendor.adb.tcp.port"};
        for (String prop : propNames) {
            int p = readSystemPropertyInt(prop.trim());
            if (p > 0 && p <= 65535 && !ports.contains(p)) {
                ports.add(p);
            }
        }
        int[] common = {5555, 5070, 5556, 5558, 5037};
        for (int p : common) {
            if (!ports.contains(p)) ports.add(p);
        }
        // Also scan /proc/net/tcp6 and /proc/net/tcp for listening high ports if standard ports fail
        for (int p : scanProcListeningPorts()) {
            if (!ports.contains(p)) ports.add(p);
        }
        return ports;
    }

    private static int readSystemPropertyInt(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getMethod("get", String.class, String.class);
            String val = (String) m.invoke(null, key, "");
            if (val != null && !val.trim().isEmpty()) {
                return Integer.parseInt(val.trim());
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static List<Integer> scanProcListeningPorts() {
        List<Integer> res = new ArrayList<>();
        String[] files = {"/proc/net/tcp6", "/proc/net/tcp"};
        for (String path : files) {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) continue;
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("sl")) continue;
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length < 4) continue;
                    // State 0A = TCP_LISTEN
                    if (!"0A".equalsIgnoreCase(parts[3])) continue;
                    String localAddr = parts[1];
                    int colon = localAddr.lastIndexOf(':');
                    if (colon > 0 && colon + 1 < localAddr.length()) {
                        int port = Integer.parseInt(localAddr.substring(colon + 1), 16);
                        if ((port >= 5000 && port <= 5600) && !res.contains(port)) {
                            res.add(port);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return res;
    }

    static int resolveOpenPort(Context c, String host, int timeoutMs) {
        if (host == null || host.isEmpty()) return -1;
        List<Integer> candidates = getCandidateAdbPorts(c);
        for (int port : candidates) {
            if (isPortOpen(host, port, timeoutMs)) {
                saveActiveAdbPort(c, port);
                return port;
            }
        }
        return -1;
    }

    static boolean isPaired(Context c) {
        return c != null && c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).getBoolean("paired", false);
    }

    private static void setPaired(Context c, boolean val) {
        if (c == null) return;
        c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).edit().putBoolean("paired", val).apply();
    }

    static int getAdbMode(Context c) {
        if (c == null) return ADB_MODE_SECURE_CLOSE;
        return c.getSharedPreferences("adb_state", Context.MODE_PRIVATE)
                .getInt("adb_mode", ADB_MODE_SECURE_CLOSE);
    }

    static String getAdbModeLabel(Context c) {
        int p = getActiveAdbPort(c);
        if (getAdbMode(c) == ADB_MODE_PERSISTENT) {
            return I18n.s(c,
                    "Persistent ADB Mode (Keep port " + p + " open)",
                    "Kalıcı ADB Modu (Port " + p + " açık kalır)");
        }
        return I18n.s(c,
                "Secure Mode — WRITE_SECURE_SETTINGS & Close ADB (Recommended)",
                "Güvenli Mod — WRITE_SECURE_SETTINGS ve ADB Kapat (Önerilen)");
    }

    static void setAdbMode(Context c, int mode) {
        if (c == null) return;
        c.getSharedPreferences("adb_state", Context.MODE_PRIVATE)
                .edit()
                .putInt("adb_mode", mode)
                .apply();
        if (mode == ADB_MODE_SECURE_CLOSE) {
            portReachable = false;
            volWatchConnected = false;
        }
        if (PowerService.instance != null) {
            PowerService.instance.syncVolumeHoldFix();
        }
    }

    static boolean isIntentionalRevokeActive(Context c) {
        if (c == null) return false;
        return c.getSharedPreferences("adb_state", Context.MODE_PRIVATE)
                .getBoolean("intentional_revoke", false);
    }

    static void setIntentionalRevokeActive(Context c, boolean val) {
        if (c == null) return;
        c.getSharedPreferences("adb_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("intentional_revoke", val)
                .apply();
    }

    static boolean hasWriteSecureSettings(Context c) {
        if (c == null) return false;
        try {
            return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Keep Alive is ON by default since v2.0.7 (works with Secure Mode via WRITE_SECURE_SETTINGS
     * without touching ADB, or via Local ADB when Persistent ADB Mode is selected).
     */
    static boolean isKeepAliveEnabled(Context c) {
        if (c == null) return true;
        android.content.SharedPreferences sp = c.getSharedPreferences("adb_state", Context.MODE_PRIVATE);
        if (!sp.getBoolean("keep_alive_v207_default_on", false)) {
            sp.edit()
                    .putBoolean("keep_alive_v207_default_on", true)
                    .putBoolean("keep_alive", true)
                    .apply();
        }
        return sp.getBoolean("keep_alive", true);
    }

    static boolean shouldAutoRepairAccessibility(Context c) {
        if (c == null || isIntentionalRevokeActive(c)) return false;
        return isKeepAliveEnabled(c);
    }

    static void setKeepAliveEnabled(Context c, boolean enabled) {
        if (c == null) return;
        c.getSharedPreferences("adb_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("keep_alive_v207_default_on", true)
                .putBoolean("keep_alive", enabled)
                .apply();
        if (enabled) {
            setIntentionalRevokeActive(c, false);
            ensureKeepAliveWatchdog(c);
            checkAndRepairPermissionsIfNeeded(c, true);
        } else {
            KEEP_ALIVE_HANDLER.removeCallbacks(KEEP_ALIVE_TICK);
            keepAliveWatchdogScheduled = false;
            if (accSettingsObserver != null) {
                try {
                    c.getApplicationContext().getContentResolver().unregisterContentObserver(accSettingsObserver);
                } catch (Throwable ignored) {
                }
                accSettingsObserver = null;
            }
        }
    }

    static void ensureKeepAliveWatchdog(Context c) {
        if (c == null) return;
        keepAliveAppCtx = c.getApplicationContext();
        registerAccessibilityObserverIfNeeded(keepAliveAppCtx);
        if (!shouldAutoRepairAccessibility(keepAliveAppCtx)) return;
        if (!keepAliveWatchdogScheduled) {
            keepAliveWatchdogScheduled = true;
            KEEP_ALIVE_HANDLER.removeCallbacks(KEEP_ALIVE_TICK);
            KEEP_ALIVE_HANDLER.postDelayed(KEEP_ALIVE_TICK, 3_000L);
        }
    }

    private static void registerAccessibilityObserverIfNeeded(Context appCtx) {
        if (appCtx == null || accSettingsObserver != null) return;
        try {
            accSettingsObserver = new android.database.ContentObserver(KEEP_ALIVE_HANDLER) {
                @Override
                public void onChange(boolean selfChange) {
                    if (keepAliveAppCtx == null || !shouldAutoRepairAccessibility(keepAliveAppCtx)) return;
                    if (!ButtonMapper.isServiceEnabled(keepAliveAppCtx)
                            || hasConflictingAccessibilityService(keepAliveAppCtx)) {
                        KEEP_ALIVE_HANDLER.postDelayed(() -> {
                            if (keepAliveAppCtx != null && shouldAutoRepairAccessibility(keepAliveAppCtx)) {
                                checkAndRepairPermissionsIfNeeded(keepAliveAppCtx, true);
                            }
                        }, 900L);
                    }
                }
            };
            android.content.ContentResolver cr = appCtx.getContentResolver();
            cr.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    false, accSettingsObserver);
            cr.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                    false, accSettingsObserver);
        } catch (Throwable ignored) {
        }
    }

    static void scheduleKeepAliveRepair(Context c, long delayMs) {
        if (c == null) return;
        final Context appCtx = c.getApplicationContext();
        keepAliveAppCtx = appCtx;
        registerAccessibilityObserverIfNeeded(appCtx);
        if (!shouldAutoRepairAccessibility(appCtx)) return;
        KEEP_ALIVE_HANDLER.postDelayed(
                () -> checkAndRepairPermissionsIfNeeded(appCtx, true),
                Math.max(1_000L, delayMs));
    }

    /**
     * Checks whether a known conflicting firmware AccessibilityService (such as Wanbo's
     * {@code com.sdt.projector.observer/com.sdt.hdmi.MyAccessibilityService}, which swallows all
     * D-Pad, OK, and Back key events in its {@code onKeyEvent}) is currently enabled alongside ours.
     */
    static boolean hasConflictingAccessibilityService(Context c) {
        if (c == null) return false;
        try {
            String existingAcc = Settings.Secure.getString(
                    c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (existingAcc != null && (existingAcc.contains("com.sdt.hdmi.MyAccessibilityService")
                    || existingAcc.contains("com.sdt.projector.observer"))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static String buildCleanAccessibilityServicesList(Context c, String existingAcc) {
        String pkg = c.getPackageName();
        String ourAcc = pkg + "/" + pkg + ".PowerService";
        String ourShort = pkg + "/.PowerService";
        StringBuilder sb = new StringBuilder();
        if (existingAcc != null && !existingAcc.trim().isEmpty() && !"null".equalsIgnoreCase(existingAcc.trim())) {
            for (String part : existingAcc.split(":")) {
                String trimmed = part == null ? "" : part.trim();
                if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) continue;
                if (trimmed.equals(ourAcc) || trimmed.equals(ourShort)) continue;
                // Exclude Wanbo HDMI observer accessibility service which intercepts & drops all remote keys
                if (trimmed.contains("com.sdt.hdmi.MyAccessibilityService")
                        || trimmed.contains("com.sdt.projector.observer")) {
                    continue;
                }
                if (sb.length() > 0) sb.append(':');
                sb.append(trimmed);
            }
        }
        if (sb.length() > 0) sb.append(':');
        sb.append(ourAcc);
        return sb.toString();
    }

    /**
     * Restores the Accessibility Service (and Notification Listener if supported) directly in-process
     * using WRITE_SECURE_SETTINGS (ServiceRepair / GuardKeep style), without needing ADB or port 5555!
     */
    static boolean repairPermissionsViaSecureSettings(Context c) {
        if (c == null || !hasWriteSecureSettings(c)) return false;
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            String existingAcc = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String newAcc = buildCleanAccessibilityServicesList(c, existingAcc);
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newAcc);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            try {
                Settings.Global.putInt(cr, "hidden_api_policy", 1);
                Settings.Global.putInt(cr, "hidden_api_policy_p_apps", 1);
                Settings.Global.putInt(cr, "hidden_api_policy_pre_p_apps", 1);
            } catch (Throwable ignored) {
            }

            // Also restore NotificationListener in-process (even on low-RAM TVs where ro.config.low_ram=true,
            // setting enabled_notification_listeners allows MediaSessionManager.getActiveSessions to work!)
            try {
                if (!MediaWatchService.granted(c)) {
                    String pkg = c.getPackageName();
                    String ourNotif = pkg + "/" + pkg + ".MediaWatchService";
                    String curNotif = Settings.Secure.getString(cr, "enabled_notification_listeners");
                    if (curNotif == null || curNotif.trim().isEmpty() || "null".equalsIgnoreCase(curNotif.trim())) {
                        Settings.Secure.putString(cr, "enabled_notification_listeners", ourNotif);
                    } else if (!curNotif.contains(ourNotif)) {
                        Settings.Secure.putString(cr, "enabled_notification_listeners", curNotif + ":" + ourNotif);
                    }
                }
                MediaWatchService.ensureSessionTracking(c);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Lightweight, zero-allocation permission status check for background Keep Alive / ServiceRepair.
     */
    static boolean needsPermissionRepair(Context c) {
        if (c == null || isIntentionalRevokeActive(c)) return false;
        if (!ButtonMapper.isServiceEnabled(c) || hasConflictingAccessibilityService(c)) {
            return true;
        }
        return false;
    }

    static void checkAndRepairPermissionsIfNeeded(Context c, boolean forceNow) {
        if (c == null) return;
        final Context appCtx = c.getApplicationContext();
        if (isIntentionalRevokeActive(appCtx) || setupGrantInProgress) return;
        if (!forceNow && !shouldAutoRepairAccessibility(appCtx)) return;
        if (!needsPermissionRepair(appCtx)) {
            return;
        }
        // 1. First try in-process ServiceRepair via WRITE_SECURE_SETTINGS (instant, no network ADB needed!)
        if (hasWriteSecureSettings(appCtx)) {
            if (repairPermissionsViaSecureSettings(appCtx)) {
                return;
            }
        }
        // 2. If Secure Mode is selected, never connect to ADB port in the background even if ADB is enabled on the TV
        if (getAdbMode(appCtx) == ADB_MODE_SECURE_CLOSE) {
            return;
        }
        // 3. Fallback to Local ADB in Persistent ADB Mode only if Keep Alive (or forceNow) is active and already paired
        if (!isPaired(appCtx)) return;
        if (!forceNow && !isKeepAliveEnabled(appCtx)) return;
        if (repairInProgress) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!forceNow && lastRepairAttemptMs > 0L && (now - lastRepairAttemptMs) < REPAIR_COOLDOWN_MS) {
            return;
        }
        repairInProgress = true;
        lastRepairAttemptMs = now;
        repairAccessibilityOnly(appCtx, (success, msg) -> {
            repairInProgress = false;
        });
    }

    private static void repairAccessibilityOnly(Context c, Callback cb) {
        if (hasWriteSecureSettings(c) && repairPermissionsViaSecureSettings(c)) {
            if (cb != null) cb.onResult(true, "SECURE_SETTINGS");
            return;
        }
        if (setupGrantInProgress || !isPaired(c)) {
            if (cb != null) cb.onResult(false, "NOT_PAIRED");
            return;
        }
        new Thread(() -> {
            String pkg = c.getPackageName();
            String existingAcc = "";
            try {
                existingAcc = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
            } catch (Exception ignored) {
            }
            String newAcc = buildCleanAccessibilityServicesList(c, existingAcc);
            String script = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; "
                    + "appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; "
                    + "settings put secure enabled_accessibility_services '" + newAcc + "'; "
                    + "settings put secure accessibility_enabled 1";
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 600);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, script);
                    portReachable = true;
                    setPaired(c, true);
                    if (cb != null) cb.onResult(true, null);
                    return;
                } catch (Exception ignored) {
                }
            }
            if (cb != null) cb.onResult(false, "PORT_CLOSED");
        }, "local-adb-acc").start();
    }

    /**
     * Streams `getevent -l` over a persistent LocalAdb shell session to detect hardware/IR/BT
     * KEY_VOLUMEUP / KEY_VOLUMEDOWN press, release, and repeat states in real time.
     * Required on Android TV because PhoneWindowManager intercepts volume keys before AccessibilityService.onKeyEvent,
     * while AccessibilityService key filtering disables Android's default volume repeat in InputDispatcher.
     */
    static void watchVolumeKeys(Context c, VolumeKeyListener listener, BooleanSupplier shouldRun) {
        while (shouldRun.getAsBoolean()) {
            if (setupGrantInProgress || !isPaired(c)) {
                try {
                    Thread.sleep(2500L);
                } catch (InterruptedException ie) {
                    break;
                }
                continue;
            }
            boolean connectedOnce = false;
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String host : hosts) {
                if (!shouldRun.getAsBoolean() || setupGrantInProgress) break;
                if (host == null || host.isEmpty()) continue;
                int port = resolveOpenPort(c, host, 600);
                if (port <= 0) continue;
                try {
                    KeyPair kp = getOrCreateKeyPair(c);
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(host, port), 1500);
                        s.setSoTimeout(0);
                        s.setTcpNoDelay(true);
                        DataInputStream in = new DataInputStream(s.getInputStream());
                        OutputStream out = s.getOutputStream();

                        byte[] banner = "host::tvoslauncher\0".getBytes(StandardCharsets.UTF_8);
                        sendPacket(out, A_CNXN, A_VERSION, MAX_PAYLOAD, banner);

                        Packet msg = readPacket(in);
                        if (msg.command == A_AUTH && msg.arg0 == ADB_AUTH_TOKEN) {
                            byte[] sig = signToken((RSAPrivateKey) kp.getPrivate(), msg.data);
                            sendPacket(out, A_AUTH, ADB_AUTH_SIGNATURE, 0, sig);
                            msg = readPacket(in);
                            if (msg.command == A_AUTH) {
                                // Do not send public key from background watcher to avoid duplicate RSA prompts
                                continue;
                            }
                        }
                        if (msg.command != A_CNXN) {
                            continue;
                        }
                        portReachable = true;
                        setPaired(c, true);
                        connectedOnce = true;

                        int localId = 77;
                        byte[] openPayload = "shell:getevent -l 2>/dev/null\0".getBytes(StandardCharsets.UTF_8);
                        sendPacket(out, A_OPEN, localId, 0, openPayload);
                        volWatchConnected = true;

                        StringBuilder lineBuf = new StringBuilder();
                        while (shouldRun.getAsBoolean()) {
                            Packet m = readPacket(in);
                            if (m.command == A_OKAY) {
                                continue;
                            } else if (m.command == A_WRTE) {
                                int remoteId = m.arg0;
                                sendPacket(out, A_OKAY, localId, remoteId, null);
                                if (m.data != null && m.data.length > 0) {
                                    String chunk = new String(m.data, StandardCharsets.UTF_8);
                                    for (int i = 0; i < chunk.length(); i++) {
                                        char ch = chunk.charAt(i);
                                        if (ch == '\n' || ch == '\r') {
                                            if (lineBuf.length() > 0) {
                                                String line = lineBuf.toString();
                                                lineBuf.setLength(0);
                                                int dir = 0;
                                                if (line.contains("KEY_VOLUMEUP") || line.contains(" 0073 ")) {
                                                    dir = 1;
                                                } else if (line.contains("KEY_VOLUMEDOWN") || line.contains(" 0072 ")) {
                                                    dir = -1;
                                                }
                                                if (dir != 0) {
                                                    String trimmed = line.trim();
                                                    boolean isDown = trimmed.endsWith("DOWN") || trimmed.endsWith("00000001");
                                                    boolean isUp = trimmed.endsWith("UP") || trimmed.endsWith("00000000");
                                                    boolean isRepeat = trimmed.endsWith("REPEAT") || trimmed.endsWith("00000002");
                                                    if (isDown) {
                                                        listener.onVolumeKey(dir, true, false);
                                                    } else if (isRepeat) {
                                                        listener.onVolumeKey(dir, true, true);
                                                    } else if (isUp) {
                                                        listener.onVolumeKey(dir, false, false);
                                                    }
                                                }
                                            }
                                        } else {
                                            if (lineBuf.length() < 512) {
                                                lineBuf.append(ch);
                                            }
                                        }
                                    }
                                }
                            } else if (m.command == A_CLSE) {
                                sendPacket(out, A_CLSE, localId, m.arg0, null);
                                break;
                            } else {
                                break;
                            }
                        }
                    }
                    if (connectedOnce) break;
                } catch (Exception ignored) {
                } finally {
                    volWatchConnected = false;
                    listener.onVolumeKey(0, false, false);
                }
            }
            if (!shouldRun.getAsBoolean()) break;
            try {
                Thread.sleep(connectedOnce ? 2000L : 5000L);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    static boolean hasDumpPermission(Context c) {
        if (c == null) return false;
        try {
            return c.checkSelfPermission(android.Manifest.permission.DUMP)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean hasVolumeLongPressPermission(Context c) {
        if (c == null) return false;
        try {
            return c.checkSelfPermission("android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static void grantVolumeLongPressPermissionIfNeeded(Context c, Runnable onGranted) {
        if (c == null) return;
        if (hasDumpPermission(c) && hasVolumeLongPressPermission(c)) {
            if (onGranted != null) onGranted.run();
            return;
        }
        if (getAdbMode(c) == ADB_MODE_SECURE_CLOSE || setupGrantInProgress || !isPaired(c)) {
            return;
        }
        new Thread(() -> {
            String pkg = c.getPackageName();
            String script = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; "
                    + "pm grant " + pkg + " android.permission.DUMP 2>/dev/null; "
                    + "pm grant " + pkg + " android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER 2>/dev/null; "
                    + "pm grant " + pkg + " android.permission.READ_TV_LISTINGS 2>/dev/null; "
                    + "appops set " + pkg + " READ_TV_LISTINGS allow 2>/dev/null; "
                    + "settings put global hidden_api_policy 1 2>/dev/null; "
                    + "settings put global hidden_api_policy_p_apps 1 2>/dev/null; "
                    + "settings put global hidden_api_policy_pre_p_apps 1 2>/dev/null";
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 500);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, script);
                    break;
                } catch (Exception ignored) {
                }
            }
            if ((hasDumpPermission(c) || hasVolumeLongPressPermission(c)) && onGranted != null) {
                KEEP_ALIVE_HANDLER.post(onGranted);
            }
        }, "local-adb-volperm").start();
    }

    static void probePortAsync(Context c, Runnable onDone) {
        if (c != null && getAdbMode(c) == ADB_MODE_SECURE_CLOSE) {
            portReachable = false;
            if (onDone != null) onDone.run();
            return;
        }
        new Thread(() -> {
            String[] hosts = {"127.0.0.1", localLanIp()};
            boolean open = false;
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                if (resolveOpenPort(c, h, 450) > 0) {
                    open = true;
                    break;
                }
            }
            portReachable = (c == null || getAdbMode(c) == ADB_MODE_PERSISTENT) && open;
            if (onDone != null) onDone.run();
        }, "adb-probe").start();
    }

    static String getStatusLabel(Context c) {
        boolean secureGranted = hasWriteSecureSettings(c);
        if (getAdbMode(c) == ADB_MODE_SECURE_CLOSE) {
            if (secureGranted) {
                return I18n.s(c, "Secure Mode Active (ADB Not Used · Self-Repair Ready)", "Güvenli Mod Aktif (ADB Kullanılmıyor · Otomatik Onarım Hazır)");
            }
            return I18n.s(c, "Secure Mode (Needs One-Time Pair to Grant Permissions)", "Güvenli Mod (İzinler İçin Tek Seferlik Eşleştirme Gerekli)");
        }
        int p = getActiveAdbPort(c);
        boolean adbOn = isAdbSettingEnabled(c);
        boolean paired = isPaired(c);
        if (portReachable && paired) {
            return I18n.s(c, "Connected & Paired (Port " + p + " Open)", "Bağlı ve Eşleşti (Port " + p + " Açık)");
        }
        if (portReachable) {
            return I18n.s(c, "Port " + p + " Open · Ready to Pair", "Port " + p + " Açık · Eşleşmeye Hazır");
        }
        if (adbOn && paired) {
            return I18n.s(c, "Paired · ADB Enabled", "Eşleşti · ADB Açık");
        }
        if (adbOn) {
            return I18n.s(c, "ADB Enabled · Not Paired", "ADB Açık · Eşleşmedi");
        }
        return I18n.s(c, "Disconnected (ADB Off)", "Bağlı Değil (ADB Kapalı)");
    }

    static boolean isAdbSettingEnabled(Context c) {
        try {
            if (Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String localLanIp() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Closes ADB after granting WRITE_SECURE_SETTINGS, DUMP,
     * and SET_VOLUME_KEY_LONG_PRESS_LISTENER, switches to Secure Mode, and verifies the ADB port is closed.
     */
    static void closeAdbPort(Context c, Callback cb) {
        if (c != null) {
            setAdbMode(c, ADB_MODE_SECURE_CLOSE);
        }
        new Thread(() -> {
            String pkg = c.getPackageName();
            String script = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; "
                    + "pm grant " + pkg + " android.permission.DUMP 2>/dev/null; "
                    + "pm grant " + pkg + " android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER 2>/dev/null; "
                    + "pm grant " + pkg + " android.permission.READ_TV_LISTINGS 2>/dev/null; "
                    + "appops set " + pkg + " READ_TV_LISTINGS allow 2>/dev/null; "
                    + "settings put global hidden_api_policy 1 2>/dev/null; "
                    + "settings put global hidden_api_policy_p_apps 1 2>/dev/null; "
                    + "settings put global hidden_api_policy_pre_p_apps 1 2>/dev/null; "
                    + "settings put global adb_enabled 0 2>/dev/null";
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 600);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, script);
                    break;
                } catch (Exception ignored) {
                }
            }
            if (hasWriteSecureSettings(c)) {
                try {
                    android.content.ContentResolver cr = c.getContentResolver();
                    Settings.Global.putInt(cr, "hidden_api_policy", 1);
                    Settings.Global.putInt(cr, "hidden_api_policy_p_apps", 1);
                    Settings.Global.putInt(cr, "hidden_api_policy_pre_p_apps", 1);
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0);
                } catch (Throwable ignored) {
                }
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
            }
            boolean stillOpen = resolveOpenPort(c, "127.0.0.1", 350) > 0;
            String lan = localLanIp();
            if (!stillOpen && lan != null && !lan.isEmpty()) {
                stillOpen = resolveOpenPort(c, lan, 350) > 0;
            }
            portReachable = stillOpen;
            if (PowerService.instance != null) {
                PowerService.instance.syncVolumeHoldFix();
            }
            if (cb != null) {
                cb.onResult(!stillOpen, stillOpen ? "PORT_STILL_OPEN" : "CLOSED");
            }
        }, "local-adb-close").start();
    }

    /**
     * Turns USB Debugging (ADB) back ON when WRITE_SECURE_SETTINGS is granted (without enabling Wireless Debugging).
     * If WRITE_SECURE_SETTINGS is not granted, invokes cb.onResult(false, "NO_PERMISSION")
     * so the caller can open Developer Options.
     */
    static void enableAdbPort(Context c, Callback cb) {
        if (c == null) {
            if (cb != null) cb.onResult(false, "NO_CONTEXT");
            return;
        }
        new Thread(() -> {
            boolean enabledSetting = false;
            if (hasWriteSecureSettings(c)) {
                try {
                    android.content.ContentResolver cr = c.getContentResolver();
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1);
                    enabledSetting = true;
                } catch (Throwable ignored) {
                }
            }
            if (!enabledSetting && !isAdbSettingEnabled(c) && resolveOpenPort(c, "127.0.0.1", 350) <= 0) {
                if (cb != null) cb.onResult(false, "NO_PERMISSION");
                return;
            }
            try {
                Thread.sleep(550L);
            } catch (InterruptedException ignored) {
            }
            boolean open = resolveOpenPort(c, "127.0.0.1", 450) > 0;
            if (!open) {
                String lan = localLanIp();
                if (lan != null && !lan.isEmpty()) {
                    open = resolveOpenPort(c, lan, 450) > 0;
                }
            }
            portReachable = open;
            if (PowerService.instance != null) {
                PowerService.instance.syncVolumeHoldFix();
            }
            if (cb != null) {
                cb.onResult(true, open ? "PORT_OPEN" : "ADB_ENABLED");
            }
        }, "local-adb-enable").start();
    }

    static void grantAllPermissions(Context c, Callback cb) {
        grantAllPermissions(c, c != null && getAdbMode(c) == ADB_MODE_SECURE_CLOSE, cb);
    }

    static void grantAllPermissions(Context c, boolean closeAdbAfter, Callback cb) {
        if (c == null) {
            if (cb != null) cb.onResult(false, "NO_CONTEXT");
            return;
        }
        setIntentionalRevokeActive(c, false);
        // If Secure Mode is active and WRITE_SECURE_SETTINGS + DUMP are already granted, repair in-process without touching ADB!
        if (getAdbMode(c) == ADB_MODE_SECURE_CLOSE
                && hasWriteSecureSettings(c)
                && hasDumpPermission(c)
                && repairPermissionsViaSecureSettings(c)) {
            portReachable = false;
            ensureKeepAliveWatchdog(c);
            if (PowerService.instance != null) {
                PowerService.instance.syncVolumeHoldFix();
            }
            if (cb != null) cb.onResult(true, "SECURE_SETTINGS");
            return;
        }
        if (setupGrantInProgress) {
            return;
        }
        setupGrantInProgress = true;
        new Thread(() -> {
            try {
                String pkg = c.getPackageName();
                List<String> cmds = new ArrayList<>();

                // 0. Grant WRITE_SECURE_SETTINGS, DUMP, SET_VOLUME_KEY_LONG_PRESS_LISTENER, and READ_TV_LISTINGS
                cmds.add("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS 2>/dev/null");
                cmds.add("pm grant " + pkg + " android.permission.DUMP 2>/dev/null");
                cmds.add("pm grant " + pkg + " android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER 2>/dev/null");
                cmds.add("pm grant " + pkg + " android.permission.READ_TV_LISTINGS 2>/dev/null");
                cmds.add("appops set " + pkg + " READ_TV_LISTINGS allow 2>/dev/null");
                cmds.add("settings put global hidden_api_policy 1 2>/dev/null");
                cmds.add("settings put global hidden_api_policy_p_apps 1 2>/dev/null");
                cmds.add("settings put global hidden_api_policy_pre_p_apps 1 2>/dev/null");

                // 1. Runtime Location permissions (only if not already granted)
                try {
                    if (c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        cmds.add("pm grant " + pkg + " android.permission.ACCESS_FINE_LOCATION 2>/dev/null");
                        cmds.add("pm grant " + pkg + " android.permission.ACCESS_COARSE_LOCATION 2>/dev/null");
                    }
                } catch (Throwable ignored) {
                }

                // 2. Storage permissions (only if not already granted — avoids Android 11/12 StorageManagerService killUid loop!)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        if (!android.os.Environment.isExternalStorageManager()) {
                            cmds.add("appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow 2>/dev/null");
                        }
                    } else {
                        if (c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            cmds.add("pm grant " + pkg + " android.permission.READ_EXTERNAL_STORAGE 2>/dev/null");
                            cmds.add("pm grant " + pkg + " android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null");
                        }
                    }
                } catch (Throwable ignored) {
                }

                try {
                    if (android.os.Build.VERSION.SDK_INT < 26 || !c.getPackageManager().canRequestPackageInstalls()) {
                        cmds.add("appops set " + pkg + " REQUEST_INSTALL_PACKAGES allow 2>/dev/null");
                    }
                } catch (Throwable ignored) {
                }
                cmds.add("appops set " + pkg + " GET_USAGE_STATS allow 2>/dev/null");
                cmds.add("appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null");

                // 3. Notification Listener & MediaSession access (both cmd notification allow_listener and
                // enabled_notification_listeners setting, so low-RAM Android TVs also allow MediaSessionManager!)
                cmds.add("pm grant " + pkg + " android.permission.MEDIA_CONTENT_CONTROL 2>/dev/null");
                if (!MediaWatchService.granted(c)) {
                    cmds.add("cmd notification allow_listener " + pkg + "/" + pkg + ".MediaWatchService 2>/dev/null");
                    String ourNotif = pkg + "/" + pkg + ".MediaWatchService";
                    String curNotif = "";
                    try {
                        curNotif = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
                    } catch (Throwable ignored) {
                    }
                    String newNotif = (curNotif == null || curNotif.trim().isEmpty() || "null".equalsIgnoreCase(curNotif.trim()))
                            ? ourNotif
                            : (curNotif.contains(ourNotif) ? curNotif : (curNotif + ":" + ourNotif));
                    cmds.add("settings put secure enabled_notification_listeners '" + newNotif + "'");
                }

                // 4. Accessibility Service (never toggle accessibility_enabled 0, only update if needed)
                String existingAcc = "";
                try {
                    existingAcc = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
                } catch (Exception ignored) {
                }
                String newAcc = buildCleanAccessibilityServicesList(c, existingAcc);
                cmds.add("settings put secure enabled_accessibility_services '" + newAcc + "'");
                cmds.add("settings put secure accessibility_enabled 1");

                // 5. Default Home Role + Preferred Home Activity (compatible with Android 9, 10, 11, 12, 14)
                cmds.add("cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 1 2>/dev/null");
                cmds.add("cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 2>/dev/null");
                cmds.add("cmd package set-home-activity --user 0 " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");
                cmds.add("cmd package set-home-activity --user 0 " + pkg + "/.MainActivity 2>/dev/null");
                cmds.add("cmd package set-home-activity " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");
                cmds.add("pm set-home-activity --user 0 " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");

                // 6. If Secure Mode (closeAdbAfter) was requested, close ADB right at the end of setup
                if (closeAdbAfter) {
                    cmds.add("settings put global adb_enabled 0 2>/dev/null");
                }

                // Connect to the first reachable host/port only so we NEVER trigger a second RSA key prompt!
                String[] hosts = {"127.0.0.1", localLanIp()};
                String targetHost = null;
                int targetPort = -1;
                for (String h : hosts) {
                    if (h == null || h.isEmpty()) continue;
                    int p = resolveOpenPort(c, h, 800);
                    if (p > 0) {
                        targetHost = h;
                        targetPort = p;
                        break;
                    }
                }

                Exception lastErr = null;
                if (targetHost != null && targetPort > 0) {
                    try {
                        runShellCommandsOnHost(c, targetHost, targetPort, cmds, null);
                        setPaired(c, true);
                        repairPermissionsViaSecureSettings(c);
                        setupGrantInProgress = false;
                        ensureKeepAliveWatchdog(c);
                        if (closeAdbAfter || getAdbMode(c) == ADB_MODE_SECURE_CLOSE) {
                            try { Thread.sleep(350L); } catch (InterruptedException ignored) { }
                            portReachable = false;
                        } else {
                            portReachable = true;
                        }
                        if (PowerService.instance != null) {
                            PowerService.instance.syncVolumeHoldFix();
                        }
                        if (cb != null) cb.onResult(true, closeAdbAfter ? "CLOSED_ADB" : null);
                        return;
                    } catch (Exception e) {
                        lastErr = e;
                    }
                }

                // If ADB port is already closed, check if WRITE_SECURE_SETTINGS is already granted!
                if (hasWriteSecureSettings(c) && repairPermissionsViaSecureSettings(c)) {
                    setupGrantInProgress = false;
                    ensureKeepAliveWatchdog(c);
                    if (cb != null) cb.onResult(true, "SECURE_SETTINGS");
                    return;
                }

                if (cb != null) {
                    if (lastErr != null) {
                        cb.onResult(false, lastErr.getMessage());
                    } else {
                        cb.onResult(false, "PORT_CLOSED");
                    }
                }
            } finally {
                setupGrantInProgress = false;
            }
        }, "local-adb").start();
    }

    static void setDefaultLauncherViaAdb(Context c, Callback cb) {
        new Thread(() -> {
            String pkg = c.getPackageName();
            List<String> cmds = new ArrayList<>();
            // Remove stock home role holders first (fixes Android 11 TVs where add-role-holder fails if stock holds it)
            for (String stockPkg : Perms.getStockTvHomePackages()) {
                cmds.add("cmd role remove-role-holder --user 0 android.app.role.HOME " + stockPkg + " 2>/dev/null");
            }
            cmds.add("cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 1 2>/dev/null");
            cmds.add("cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 2>/dev/null");
            cmds.add("cmd package set-home-activity --user 0 " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");
            cmds.add("cmd package set-home-activity --user 0 " + pkg + "/.MainActivity 2>/dev/null");
            cmds.add("cmd package set-home-activity " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");
            cmds.add("pm set-home-activity --user 0 " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");
            cmds.add("pm set-home-activity " + pkg + "/" + pkg + ".MainActivity 2>/dev/null");

            String[] hosts = {"127.0.0.1", localLanIp()};
            Exception lastErr = null;
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 800);
                if (port <= 0) continue;
                try {
                    runShellCommandsOnHost(c, h, port, cmds, null);
                    portReachable = true;
                    setPaired(c, true);
                    // On Android 11 TVs where the OS still ignores ROLE_HOME, also enable Home Button Fix if Accessibility is ready
                    if (!Perms.isDefaultHome(c)) {
                        ButtonMapper.setHomeFixEnabled(c, true);
                        if (hasWriteSecureSettings(c)) {
                            repairPermissionsViaSecureSettings(c);
                        }
                    }
                    if (cb != null) cb.onResult(true, null);
                    return;
                } catch (Exception e) {
                    lastErr = e;
                }
            }
            // Fallback when ADB port is closed but WRITE_SECURE_SETTINGS is available: enable Home Button Fix + Accessibility
            if (hasWriteSecureSettings(c) && repairPermissionsViaSecureSettings(c)) {
                ButtonMapper.setHomeFixEnabled(c, true);
                if (cb != null) cb.onResult(true, "HOME_FIX");
                return;
            }
            if (cb != null) {
                cb.onResult(false, lastErr != null ? lastErr.getMessage() : "PORT_CLOSED");
            }
        }, "local-adb-home").start();
    }

    /**
     * Revokes Device Admin, Accessibility, Notification Listener, and Default Home cleanly
     * so the user can uninstall the app on any TV without crashing the app process or locking out ADB on Android 9.
     */
    static void revokeAllPermissions(Context c, Callback cb) {
        if (c == null) {
            if (cb != null) cb.onResult(false, "NO_CONTEXT");
            return;
        }
        final Context appCtx = c.getApplicationContext();
        // 1. Mark intentional revoke and disable Keep Alive so auto-repair does not re-enable Accessibility
        setIntentionalRevokeActive(appCtx, true);
        setKeepAliveEnabled(appCtx, false);

        // 2. Clear sleep timer and preferred Home activity via standard non-destructive Android APIs
        try {
            SleepReceiver.setTimer(appCtx, 0);
        } catch (Throwable ignored) { }
        try {
            appCtx.getPackageManager().clearPackagePreferredActivities(appCtx.getPackageName());
        } catch (Throwable ignored) { }

        String pkg = appCtx.getPackageName();
        String existingAcc = "";
        try {
            existingAcc = Settings.Secure.getString(appCtx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        } catch (Exception ignored) { }
        String ourAcc = pkg + "/" + pkg + ".PowerService";
        String ourShort = pkg + "/.PowerService";
        StringBuilder cleanAcc = new StringBuilder();
        if (existingAcc != null && !existingAcc.trim().isEmpty() && !"null".equalsIgnoreCase(existingAcc.trim())) {
            for (String part : existingAcc.split(":")) {
                String trimmed = part == null ? "" : part.trim();
                if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)
                        || trimmed.equals(ourAcc) || trimmed.equals(ourShort)) {
                    continue;
                }
                if (cleanAcc.length() > 0) cleanAcc.append(':');
                cleanAcc.append(trimmed);
            }
        }
        final String cleanAccStr = cleanAcc.toString();

        // 3. If WRITE_SECURE_SETTINGS is granted, cleanly remove our Accessibility & Notification Listener in-process
        boolean updatedInProcess = false;
        if (hasWriteSecureSettings(appCtx)) {
            try {
                android.content.ContentResolver cr = appCtx.getContentResolver();
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cleanAccStr);
                if (cleanAccStr.isEmpty()) {
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
                }
                String ourNotif = pkg + "/" + pkg + ".MediaWatchService";
                String curNotif = Settings.Secure.getString(cr, "enabled_notification_listeners");
                if (curNotif != null && curNotif.contains(ourNotif)) {
                    StringBuilder cleanNotif = new StringBuilder();
                    for (String p : curNotif.split(":")) {
                        String t = p == null ? "" : p.trim();
                        if (t.isEmpty() || t.equals(ourNotif)) continue;
                        if (cleanNotif.length() > 0) cleanNotif.append(':');
                        cleanNotif.append(t);
                    }
                    Settings.Secure.putString(cr, "enabled_notification_listeners", cleanNotif.toString());
                }
                updatedInProcess = true;
            } catch (Throwable ignored) { }
        }

        final boolean localUpdated = updatedInProcess;
        // 4. Also run non-destructive ADB cleanup commands if ADB port is open (NO `pm revoke` so Android 9 never kills the app!)
        new Thread(() -> {
            List<String> cmds = new ArrayList<>();
            cmds.add("settings put secure enabled_accessibility_services '" + cleanAccStr + "'");
            if (cleanAccStr.isEmpty()) {
                cmds.add("settings put secure accessibility_enabled 0");
            }
            cmds.add("cmd notification disallow_listener " + pkg + "/" + pkg + ".MediaWatchService 2>/dev/null");
            cmds.add("cmd role remove-role-holder --user 0 android.app.role.HOME " + pkg + " 2>/dev/null");
            cmds.add("cmd package clear-package-preferred-activities " + pkg + " 2>/dev/null");

            String[] hosts = {"127.0.0.1", localLanIp()};
            boolean adbRan = false;
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(appCtx, h, 600);
                if (port <= 0) continue;
                try {
                    runShellCommandsOnHost(appCtx, h, port, cmds, null);
                    adbRan = true;
                    break;
                } catch (Exception ignored) { }
            }
            if (cb != null) cb.onResult(true, (adbRan || localUpdated) ? "ADB_AND_LOCAL" : "LOCAL_ONLY");
        }, "local-adb-revoke").start();
    }

    static void forceStopPackagesAsync(Context c, java.util.List<String> pkgs) {
        if (c == null || getAdbMode(c) == ADB_MODE_SECURE_CLOSE || pkgs == null || pkgs.isEmpty() || !isPaired(c)) return;
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (String p : pkgs) {
                if (p == null || p.isEmpty() || p.equals(c.getPackageName())) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append("am force-stop ").append(p);
            }
            if (sb.length() == 0) return;
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 400);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, sb.toString());
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "local-adb-forcestop").start();
    }

    static boolean startActivityViaAdbAsync(Context c, String pkg, String cls) {
        if (c == null || getAdbMode(c) == ADB_MODE_SECURE_CLOSE || pkg == null || pkg.isEmpty() || !isPaired(c)) return false;
        new Thread(() -> {
            String cmd = (cls != null && !cls.isEmpty())
                    ? ("am start -n " + pkg + "/" + cls)
                    : ("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 400);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, cmd);
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "local-adb-start").start();
        return true;
    }

    static boolean sendKeyEventViaAdbAsync(Context c, int keyCode) {
        if (c == null || getAdbMode(c) == ADB_MODE_SECURE_CLOSE || keyCode <= 0 || !isPaired(c)) return false;
        new Thread(() -> {
            String cmd = "input keyevent " + keyCode;
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 350);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, cmd);
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "local-adb-keyevent").start();
        return true;
    }

    static boolean sendLongPressKeyEventViaAdbAsync(Context c, int keyCode) {
        if (c == null || getAdbMode(c) == ADB_MODE_SECURE_CLOSE || keyCode <= 0 || !isPaired(c)) return false;
        new Thread(() -> {
            String cmd = "input keyevent --longpress " + keyCode;
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                int port = resolveOpenPort(c, h, 350);
                if (port <= 0) continue;
                try {
                    runShellCommand(c, h, port, cmd);
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "local-adb-longpress").start();
        return true;
    }

    interface BatchProgressListener {
        void onStep(int index, int total, String cmd, String output);
    }

    static String execShellOutput(Context c, String shellCmd) throws Exception {
        String[] hosts = {"127.0.0.1", localLanIp()};
        Exception lastErr = null;
        for (String h : hosts) {
            if (h == null || h.isEmpty()) continue;
            int port = resolveOpenPort(c, h, 600);
            if (port <= 0) continue;
            try {
                String out = runShellCommandOutput(c, h, port, shellCmd);
                portReachable = true;
                setPaired(c, true);
                return out;
            } catch (Exception e) {
                lastErr = e;
            }
        }
        if (lastErr != null) throw lastErr;
        throw new Exception("PORT_CLOSED");
    }

    /**
     * Runs each shell command in its own separate `shell:` stream over a single authenticated ADB socket
     * (matching CleanYourTV's rule: "Run pm commands one per shell call, chained commands may silently stop after the first").
     */
    static boolean execShellSequential(Context c, List<String> commands, BatchProgressListener progress) {
        String[] hosts = {"127.0.0.1", localLanIp()};
        for (String h : hosts) {
            if (h == null || h.isEmpty()) continue;
            int port = resolveOpenPort(c, h, 600);
            if (port <= 0) continue;
            try {
                runShellCommandsOnHost(c, h, port, commands, progress);
                portReachable = true;
                setPaired(c, true);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    static boolean isAdbPortCurrentlyOpen() {
        return isAdbPortCurrentlyOpen(keepAliveAppCtx);
    }

    static boolean isAdbPortCurrentlyOpen(Context c) {
        String[] hosts = {"127.0.0.1", localLanIp()};
        for (String h : hosts) {
            if (h == null || h.isEmpty()) continue;
            if (resolveOpenPort(c, h, 450) > 0) {
                portReachable = true;
                return true;
            }
        }
        portReachable = false;
        return false;
    }

    /**
     * Installs {@code apkFile} directly from {@code /storage/emulated/0/Download/tvOSLauncherUpdates}
     * over Local ADB using a two-phase PackageInstaller shell session:
     * 1) Synchronously {@code pm install-create} + {@code cat | pm install-write} (returns false to fall back to PackageInstaller if anything fails),
     * 2) Detached {@code setsid}/{@code nohup} {@code pm install-commit} + permission restore + {@code am start}.
     */
    static boolean installApkViaAdb(Context c, File apkFile) {
        if (c == null || apkFile == null || !apkFile.exists() || apkFile.length() < 10_000) {
            return false;
        }
        String pkg = c.getPackageName();
        String apkAbsPath = apkFile.getAbsolutePath();
        String sdcardUpdatesPath = "/storage/emulated/0/Download/tvOSLauncherUpdates/" + apkFile.getName();
        long apkSize = apkFile.length();

        String[] hosts = {"127.0.0.1", localLanIp()};
        for (String h : hosts) {
            if (h == null || h.isEmpty()) continue;
            int port = resolveOpenPort(c, h, 600);
            if (port <= 0) continue;
            try {
                String apkPath = new File(sdcardUpdatesPath).exists() ? sdcardUpdatesPath : apkAbsPath;

                // Phase 1: Create session & stream APK via pipe (avoids Android 14 FUSE SELinux denial and verifies success before committing)
                String stageCmd = "appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow 2>/dev/null; "
                        + "appops set " + pkg + " REQUEST_INSTALL_PACKAGES allow 2>/dev/null; "
                        + "chmod 644 \"" + apkPath + "\" 2>/dev/null; "
                        + "S=$(pm install-create -r -d -g 2>/dev/null || pm install-create -r -g 2>/dev/null || pm install-create -r 2>/dev/null); "
                        + "SID=$(echo \"$S\" | sed -n 's/.*\\[\\([0-9]*\\)\\].*/\\1/p'); "
                        + "if [ -z \"$SID\" ]; then echo STAGE_FAIL_CREATE; exit 1; fi; "
                        + "W=$(cat \"" + apkPath + "\" | pm install-write -S " + apkSize + " \"$SID\" base.apk - 2>&1); "
                        + "case \"$W\" in *Success*) echo \"STAGE_OK:$SID\" ;; *) pm install-abandon \"$SID\" 2>/dev/null; echo \"STAGE_FAIL_WRITE:$W\" ;; esac";
                String stageOut = runShellCommandOutput(c, h, port, stageCmd);
                if (stageOut == null || !stageOut.contains("STAGE_OK:")) {
                    continue;
                }
                int idx = stageOut.indexOf("STAGE_OK:");
                String sid = stageOut.substring(idx + "STAGE_OK:".length()).trim().replaceAll("[^0-9].*$", "");
                if (sid.isEmpty()) {
                    continue;
                }

                // Phase 2: Commit session synchronously (system_server receives the commit immediately;
                // even if replacing our process closes the ADB socket, system_server completes the install)
                String existingAcc = "";
                try {
                    existingAcc = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
                } catch (Throwable ignored) {
                }
                String newAcc = buildCleanAccessibilityServicesList(c, existingAcc);

                String commitCmd = "pm install-commit " + sid + " 2>&1; "
                        + "appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; "
                        + "settings put secure enabled_accessibility_services \"" + newAcc + "\" 2>/dev/null; "
                        + "settings put secure accessibility_enabled 1 2>/dev/null; "
                        + "am start -n " + pkg + "/.MainActivity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null "
                        + "|| am start -n " + pkg + "/" + pkg + ".MainActivity 2>/dev/null";

                portReachable = true;
                setPaired(c, true);
                String out = runShellCommandOutput(c, h, port, commitCmd);
                if (out == null || out.contains("Success") || out.trim().isEmpty()) {
                    return true;
                }
            } catch (Throwable t) {
                // If socket closed/reset because system_server killed our process during pm install-commit, install is succeeding!
                if (t instanceof java.io.EOFException || t instanceof java.net.SocketException) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void copyLocalFile(File src, File dst) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(src);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[32768];
            int r;
            while ((r = fis.read(buf)) > 0) {
                fos.write(buf, 0, r);
            }
            fos.flush();
        }
    }

    private static boolean streamFileOverAdbExec(Context c, String host, int port, File srcFile, String remotePath) {
        try {
            KeyPair kp = getOrCreateKeyPair(c);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2500);
                socket.setSoTimeout(20000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();

                byte[] banner = "host::tvoslauncher\0".getBytes(StandardCharsets.UTF_8);
                sendPacket(out, A_CNXN, A_VERSION, MAX_PAYLOAD, banner);

                boolean sentSignature = false;
                while (true) {
                    Packet p = readPacket(in);
                    if (p.command == A_CNXN) {
                        break;
                    } else if (p.command == A_AUTH && p.arg0 == ADB_AUTH_TOKEN) {
                        if (!sentSignature) {
                            sentSignature = true;
                            byte[] sig = signToken((RSAPrivateKey) kp.getPrivate(), p.data);
                            sendPacket(out, A_AUTH, ADB_AUTH_SIGNATURE, 0, sig);
                        } else {
                            byte[] pubKeyPayload = encodeAndroidRsaPublicKey((RSAPublicKey) kp.getPublic());
                            sendPacket(out, A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyPayload);
                        }
                    } else {
                        return false;
                    }
                }

                int localId = 91;
                byte[] openData = ("exec:sh -c 'cat > " + remotePath + "'\0").getBytes(StandardCharsets.UTF_8);
                sendPacket(out, A_OPEN, localId, 0, openData);

                Packet resp = readPacket(in);
                if (resp.command != A_OKAY) {
                    return false;
                }
                int remoteId = resp.arg0;

                try (java.io.FileInputStream fis = new java.io.FileInputStream(srcFile)) {
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = fis.read(buf)) > 0) {
                        byte[] chunk = (r == buf.length) ? buf : java.util.Arrays.copyOf(buf, r);
                        sendPacket(out, A_WRTE, localId, remoteId, chunk);
                        while (true) {
                            Packet ack = readPacket(in);
                            if (ack.command == A_OKAY) {
                                break;
                            } else if (ack.command == A_WRTE) {
                                sendPacket(out, A_OKAY, localId, ack.arg0, null);
                            } else if (ack.command == A_CLSE) {
                                return false;
                            }
                        }
                    }
                }
                sendPacket(out, A_CLSE, localId, remoteId, null);
            }
            String check = runShellCommandOutput(c, host, port, "chmod 644 " + remotePath + " 2>/dev/null; [ -s " + remotePath + " ] && echo STREAM_OK");
            return check != null && check.contains("STREAM_OK");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void runShellCommand(Context c, String host, int port, String shellCmd) throws Exception {
        runShellCommandOutput(c, host, port, shellCmd);
    }

    private static String runShellCommandOutput(Context c, String host, int port, String shellCmd) throws Exception {
        List<String> res = runShellCommandsOnHost(c, host, port, java.util.Collections.singletonList(shellCmd), null);
        return res.isEmpty() ? "" : res.get(0);
    }

    private static List<String> runShellCommandsOnHost(
            Context c, String host, int port, List<String> commands, BatchProgressListener progress) throws Exception {
        synchronized (ADB_SESSION_LOCK) {
            KeyPair kp = getOrCreateKeyPair(c);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2500);
                socket.setSoTimeout(35000); // Allow up to 35s for user to tap "Allow USB debugging" on TV
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();

                // 1. Send CNXN with consistent host identity so adbd only prompts once for our RSA key
                byte[] banner = "host::tvoslauncher\0".getBytes(StandardCharsets.UTF_8);
                sendPacket(out, A_CNXN, A_VERSION, MAX_PAYLOAD, banner);

                boolean sentSignature = false;
                boolean sentPubKey = false;
                while (true) {
                    Packet p = readPacket(in);
                    if (p.command == A_CNXN) {
                        saveActiveAdbPort(c, port);
                        setPaired(c, true);
                        break;
                    } else if (p.command == A_AUTH) {
                        if (p.arg0 == ADB_AUTH_TOKEN) {
                            if (!sentSignature) {
                                sentSignature = true;
                                byte[] sig = signToken((RSAPrivateKey) kp.getPrivate(), p.data);
                                sendPacket(out, A_AUTH, ADB_AUTH_SIGNATURE, 0, sig);
                            } else if (!sentPubKey) {
                                sentPubKey = true;
                                byte[] pubKeyPayload = encodeAndroidRsaPublicKey((RSAPublicKey) kp.getPublic());
                                sendPacket(out, A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyPayload);
                            } else {
                                throw new Exception("ADB_AUTH_REJECTED");
                            }
                        } else {
                            throw new Exception("Unexpected AUTH arg0: " + p.arg0);
                        }
                    } else {
                        throw new Exception("Unexpected ADB response: " + p.command);
                    }
                }

                socket.setSoTimeout(15000);
                List<String> outputs = new java.util.ArrayList<>(commands.size());
                int total = commands.size();
                for (int i = 0; i < total; i++) {
                    String shellCmd = commands.get(i);
                    int localId = i + 1;
                    byte[] openData = ("shell:" + shellCmd + "\0").getBytes(StandardCharsets.UTF_8);
                    sendPacket(out, A_OPEN, localId, 0, openData);

                    StringBuilder sb = new StringBuilder();
                    while (true) {
                        Packet p = readPacket(in);
                        if (p.command == A_WRTE) {
                            sendPacket(out, A_OKAY, localId, p.arg0, null);
                            if (p.data != null && p.data.length > 0) {
                                sb.append(new String(p.data, StandardCharsets.UTF_8));
                            }
                        } else if (p.command == A_CLSE) {
                            sendPacket(out, A_CLSE, localId, p.arg0, null);
                            break;
                        }
                    }
                    String outStr = sb.toString();
                    outputs.add(outStr);
                    if (progress != null) {
                        progress.onStep(i + 1, total, shellCmd, outStr);
                    }
                }
                return outputs;
            }
        }
    }

    private static final class Packet {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    private static void sendPacket(OutputStream out, int command, int arg0, int arg1, byte[] data) throws Exception {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        if (data != null) {
            for (byte b : data) {
                sum += (b & 0xFF);
            }
        }
        ByteBuffer hdr = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putInt(command);
        hdr.putInt(arg0);
        hdr.putInt(arg1);
        hdr.putInt(len);
        hdr.putInt(sum);
        hdr.putInt(~command);
        out.write(hdr.array());
        if (len > 0) {
            out.write(data);
        }
        out.flush();
    }

    private static Packet readPacket(DataInputStream in) throws Exception {
        byte[] hdrBytes = new byte[24];
        in.readFully(hdrBytes);
        ByteBuffer hdr = ByteBuffer.wrap(hdrBytes).order(ByteOrder.LITTLE_ENDIAN);
        Packet p = new Packet();
        p.command = hdr.getInt();
        p.arg0 = hdr.getInt();
        p.arg1 = hdr.getInt();
        int len = hdr.getInt();
        hdr.getInt(); // checksum
        hdr.getInt(); // magic
        if (len > 0) {
            p.data = new byte[len];
            in.readFully(p.data);
        } else {
            p.data = new byte[0];
        }
        return p;
    }

    private static byte[] signToken(RSAPrivateKey privKey, byte[] token) throws Exception {
        byte[] digestInfo = new byte[SHA1_DIGEST_INFO_PREFIX.length + token.length];
        System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA1_DIGEST_INFO_PREFIX.length);
        System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO_PREFIX.length, token.length);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, privKey);
        return cipher.doFinal(digestInfo);
    }

    /**
     * Encodes an RSAPublicKey into Android's 524-byte RSAPublicKey struct (Base64 + " tvoslauncher@tv\0").
     */
    private static byte[] encodeAndroidRsaPublicKey(RSAPublicKey pub) {
        BigInteger n = pub.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32);
        BigInteger r = BigInteger.ONE.shiftLeft(2048);
        BigInteger rr = r.multiply(r).mod(n);

        ByteBuffer buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(64); // 2048 bits / 32 bits
        buf.putInt((int) n0inv.longValue());
        buf.put(toLittleEndian256(n));
        buf.put(toLittleEndian256(rr));
        buf.putInt(pub.getPublicExponent().intValue());

        String b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        return (b64 + " tvoslauncher@tv\0").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] toLittleEndian256(BigInteger val) {
        byte[] be = val.toByteArray();
        byte[] le = new byte[256];
        int srcEnd = be.length;
        int copyLen = Math.min(256, srcEnd);
        for (int i = 0; i < copyLen; i++) {
            le[i] = be[srcEnd - 1 - i];
        }
        return le;
    }

    private static volatile KeyPair cachedKeyPair;

    private static synchronized KeyPair getOrCreateKeyPair(Context c) throws Exception {
        if (cachedKeyPair != null) return cachedKeyPair;
        File privFile = new File(c.getFilesDir(), "adb_rsa.priv");
        File pubFile = new File(c.getFilesDir(), "adb_rsa.pub");
        if (privFile.exists() && pubFile.exists()) {
            try {
                byte[] privBytes = readAll(privFile);
                byte[] pubBytes = readAll(pubFile);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                cachedKeyPair = new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(pubBytes)),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes)));
                return cachedKeyPair;
            } catch (Exception ignored) {
            }
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        try (FileOutputStream fos = new FileOutputStream(privFile)) {
            fos.write(kp.getPrivate().getEncoded());
        }
        try (FileOutputStream fos = new FileOutputStream(pubFile)) {
            fos.write(kp.getPublic().getEncoded());
        }
        cachedKeyPair = kp;
        return kp;
    }

    private static byte[] readAll(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0;
            while (off < b.length) {
                int r = fis.read(b, off, b.length - off);
                if (r <= 0) break;
                off += r;
            }
        }
        return b;
    }
}
