package com.techgenius.tvoslauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.techgenius.tvoslauncher.present.BootReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * AccessibilityService for:
 *  - Global remote key interception & remapping (Button Mapper: 1x, 2x, 3x tap over any app + Send Key Code)
 *  - Control Panel overlay & Recent Apps overlay over any app
 *  - Fast boot launch & stock launcher interception on boot (when Launch on System Boot is enabled)
 *  - Volume key hold repeat fix (including Android 9 projectors like Magcubic HY320, without ADB getevent)
 *  - Turning the screen off (Android 9+ GLOBAL_ACTION_LOCK_SCREEN)
 *  - Pressing Force stop -> OK when cleaning background apps
 */
public class PowerService extends AccessibilityService {
    static PowerService instance;

    private static final List<String> queue = new ArrayList<>();
    private static Runnable onDone;
    private static String current;
    private static boolean clickedStop, clickedOk;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable timeout = PowerService::advance;

    // Volume hold repeat state (handles both LocalAdb getevent and Android 9 IR projector repeat/pulses)
    private AudioManager audioManager;
    private volatile int volDir = 0;
    private volatile long volDownTime = 0L;
    private volatile long lastDownTime = 0L;
    private volatile int lastDownDir = 0;
    private volatile long lastPulseDuration = 999L;
    private volatile long lastStepTime = 0L;
    private volatile boolean volWatchActive = false;
    private volatile boolean volLongPressRegistered = false;
    private Object volLongPressProxy;
    private Thread volWatchThread;
    private volatile boolean audioDumpWatchActive = false;
    private Thread audioDumpWatchThread;
    private volatile long serviceConnectedElapsedMs = 0L;
    private static final long VOL_REPEAT_INITIAL_MS = 280L;
    private static final long VOL_REPEAT_INTERVAL_MS = 85L;

    private void stepVolume(int dir) {
        if (dir == 0) return;
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) return;
        lastStepTime = SystemClock.uptimeMillis();
        int adjust = dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        try {
            int before = -1;
            if (Build.VERSION.SDK_INT <= 28) {
                try {
                    before = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                } catch (Throwable ignored) {
                }
            }
            audioManager.adjustSuggestedStreamVolume(
                    adjust,
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    AudioManager.FLAG_SHOW_UI);
            if (Build.VERSION.SDK_INT <= 28) {
                int after = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (before >= 0 && after == before) {
                    audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            adjust,
                            AudioManager.FLAG_SHOW_UI);
                }
            }
        } catch (Throwable t) {
            try {
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        adjust,
                        AudioManager.FLAG_SHOW_UI);
            } catch (Throwable ignored) {
            }
        }
    }

    private final Runnable volRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            int d = volDir;
            if (d == 0) return;
            if (SystemClock.uptimeMillis() - volDownTime > 4500L) {
                volDir = 0;
                return;
            }
            stepVolume(d);
            if (volDir == d) {
                handler.postDelayed(this, VOL_REPEAT_INTERVAL_MS);
            }
        }
    };

    private void onVolumeKeyState(int dir, boolean down, boolean isRepeat) {
        if (!ButtonMapper.isVolumeHoldFixEnabled(this)) {
            volDir = 0;
            handler.removeCallbacks(volRepeatRunnable);
            return;
        }
        int mode = ButtonMapper.getVolumeHoldFixMode(this);
        if (mode == ButtonMapper.VOLUME_FIX_MODE_1) {
            // Mode 1 (Standard - Default): Original v2.0.5 timing & behavior (320ms initial delay, no pulse-burst/repeat injection)
            if (isRepeat) {
                return;
            }
            if (!down) {
                handler.removeCallbacks(volRepeatRunnable);
                if (dir == 0 || volDir == dir) {
                    volDir = 0;
                }
                return;
            }
            if (volDir == dir) {
                return;
            }
            handler.removeCallbacks(volRepeatRunnable);
            volDir = dir;
            volDownTime = SystemClock.uptimeMillis();
            handler.postDelayed(volRepeatRunnable, 320L);
            return;
        }

        // Mode 2 (HY320 / Projector): 280ms initial delay + IR repeat & pulse-burst handling
        long now = SystemClock.uptimeMillis();
        if (down) {
            volDownTime = now;
            if (isRepeat) {
                if (now - lastStepTime >= 70L) {
                    stepVolume(dir);
                }
                volDir = dir;
                handler.removeCallbacks(volRepeatRunnable);
                handler.postDelayed(volRepeatRunnable, VOL_REPEAT_INTERVAL_MS);
                return;
            }
            // Magcubic HY320 rapid DOWN/UP pulse burst when holding volume button:
            // if the previous press was a short IR pulse (<110ms) and a new DOWN in the same direction arrives within 240ms
            if (lastDownDir == dir && lastPulseDuration < 110L && (now - lastDownTime) < 240L) {
                lastDownTime = now;
                if (now - lastStepTime >= 70L) {
                    stepVolume(dir);
                }
                volDir = dir;
                handler.removeCallbacks(volRepeatRunnable);
                handler.postDelayed(volRepeatRunnable, VOL_REPEAT_INTERVAL_MS);
                return;
            }
            lastDownDir = dir;
            lastDownTime = now;
            if (volDir != dir) {
                volDir = dir;
                handler.removeCallbacks(volRepeatRunnable);
                handler.postDelayed(volRepeatRunnable, VOL_REPEAT_INITIAL_MS);
            }
        } else {
            if (volDownTime > 0L) {
                lastPulseDuration = Math.max(0L, now - volDownTime);
            }
            if (dir == 0 || volDir == dir) {
                volDir = 0;
                handler.removeCallbacks(volRepeatRunnable);
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        serviceConnectedElapsedMs = SystemClock.elapsedRealtime();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                setServiceInfo(info);
            }
        } catch (Throwable ignored) {
        }
        syncVolumeHoldFix();
        LocalAdb.ensureKeepAliveWatchdog(this);

        // If "Launch Automatically on System Boot" is enabled and the system just booted,
        // AccessibilityService connects much earlier than BOOT_COMPLETED on many TVs: launch Home immediately!
        if (SystemClock.elapsedRealtime() < 150_000L && BootReceiver.isLaunchOnBootEnabled(this)) {
            BootReceiver.triggerFastBootSequenceIfNeeded(this);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterVolumeLongPressListener();
        stopVolumeWatcher();
        stopAudioDumpVolumeWatcher();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        unregisterVolumeLongPressListener();
        stopVolumeWatcher();
        stopAudioDumpVolumeWatcher();
        if (instance == this) instance = null;
        Context appCtx = getApplicationContext();
        if (LocalAdb.shouldAutoRepairAccessibility(appCtx)) {
            LocalAdb.scheduleKeepAliveRepair(appCtx, 1000L);
        }
        super.onDestroy();
    }

    void syncVolumeHoldFix() {
        volDir = 0;
        handler.removeCallbacks(volRepeatRunnable);
        if (ButtonMapper.isVolumeHoldFixEnabled(this)) {
            int mode = ButtonMapper.getVolumeHoldFixMode(this);
            registerVolumeLongPressListener();
            // Ensure DUMP + SET_VOLUME_KEY_LONG_PRESS_LISTENER are granted if port 5555 is currently open
            if (!LocalAdb.hasDumpPermission(this) || !LocalAdb.hasVolumeLongPressPermission(this)) {
                LocalAdb.grantVolumeLongPressPermissionIfNeeded(this, () -> {
                    if (instance == this && ButtonMapper.isVolumeHoldFixEnabled(this)) {
                        registerVolumeLongPressListener();
                        startAudioDumpVolumeWatcher();
                        if (ButtonMapper.getVolumeHoldFixMode(this) == ButtonMapper.VOLUME_FIX_MODE_2
                                && LocalAdb.hasDumpPermission(this)) {
                            stopVolumeWatcher();
                        }
                    }
                });
            }
            if (mode == ButtonMapper.VOLUME_FIX_MODE_2) {
                // Mode 2 (HY320 / Projector): AudioService binder dump watcher for both Secure & Persistent modes
                startAudioDumpVolumeWatcher();
                if (!LocalAdb.hasDumpPermission(this)) {
                    startVolumeWatcher();
                } else {
                    stopVolumeWatcher();
                }
            } else {
                // Mode 1 (Standard - Default): LocalAdb getevent when ADB is open + AudioService dump in Secure Mode
                startVolumeWatcher();
                startAudioDumpVolumeWatcher();
            }
        } else {
            unregisterVolumeLongPressListener();
            stopVolumeWatcher();
            stopAudioDumpVolumeWatcher();
        }
    }

    private synchronized boolean registerVolumeLongPressListener() {
        if (volLongPressRegistered) return true;
        if (!LocalAdb.hasVolumeLongPressPermission(this)) return false;
        try {
            if (LocalAdb.hasWriteSecureSettings(this)) {
                try {
                    android.content.ContentResolver cr = getContentResolver();
                    Settings.Global.putInt(cr, "hidden_api_policy", 1);
                    Settings.Global.putInt(cr, "hidden_api_policy_p_apps", 1);
                    Settings.Global.putInt(cr, "hidden_api_policy_pre_p_apps", 1);
                } catch (Throwable ignored) {
                }
            }
            android.media.session.MediaSessionManager msm =
                    (android.media.session.MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return false;
            Class<?> listenerClass = Class.forName(
                    "android.media.session.MediaSessionManager$OnVolumeKeyLongPressListener");
            java.lang.reflect.Method setMethod = android.media.session.MediaSessionManager.class
                    .getMethod("setOnVolumeKeyLongPressListener", listenerClass, Handler.class);
            if (volLongPressProxy == null) {
                volLongPressProxy = java.lang.reflect.Proxy.newProxyInstance(
                        listenerClass.getClassLoader(),
                        new Class<?>[]{listenerClass},
                        (proxy, method, args) -> {
                            String mName = method.getName();
                            if ("onVolumeKeyLongPress".equals(mName)
                                    && args != null && args.length == 1 && args[0] instanceof KeyEvent) {
                                onSystemVolumeKeyLongPress((KeyEvent) args[0]);
                                return null;
                            }
                            if ("equals".equals(mName) && args != null && args.length == 1) {
                                return proxy == args[0];
                            }
                            if ("hashCode".equals(mName)) {
                                return System.identityHashCode(proxy);
                            }
                            if ("toString".equals(mName)) {
                                return "PowerServiceVolumeKeyLongPressListener";
                            }
                            return null;
                        });
            }
            setMethod.invoke(msm, volLongPressProxy, handler);
            volLongPressRegistered = true;
            return true;
        } catch (Throwable t) {
            volLongPressRegistered = false;
            return false;
        }
    }

    private synchronized void unregisterVolumeLongPressListener() {
        volDir = 0;
        handler.removeCallbacks(volRepeatRunnable);
        if (!volLongPressRegistered) return;
        volLongPressRegistered = false;
        try {
            android.media.session.MediaSessionManager msm =
                    (android.media.session.MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                Class<?> listenerClass = Class.forName(
                        "android.media.session.MediaSessionManager$OnVolumeKeyLongPressListener");
                java.lang.reflect.Method setMethod = android.media.session.MediaSessionManager.class
                        .getMethod("setOnVolumeKeyLongPressListener", listenerClass, Handler.class);
                setMethod.invoke(msm, null, null);
            }
        } catch (Throwable ignored) {
        }
    }

    private void onSystemVolumeKeyLongPress(KeyEvent event) {
        if (event == null || !ButtonMapper.isVolumeHoldFixEnabled(this)) {
            volDir = 0;
            handler.removeCallbacks(volRepeatRunnable);
            return;
        }
        int code = event.getKeyCode();
        if (ButtonMapper.isKeyCustomMapped(this, code)) {
            return;
        }
        int dir = (code == KeyEvent.KEYCODE_VOLUME_UP) ? 1
                : (code == KeyEvent.KEYCODE_VOLUME_DOWN ? -1 : 0);
        if (dir == 0) return;
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            volDownTime = SystemClock.uptimeMillis();
            if (volDir != dir) {
                volDir = dir;
                stepVolume(dir);
                handler.removeCallbacks(volRepeatRunnable);
                handler.postDelayed(volRepeatRunnable, VOL_REPEAT_INTERVAL_MS);
            }
        } else if (action == KeyEvent.ACTION_UP) {
            volDir = 0;
            handler.removeCallbacks(volRepeatRunnable);
        }
    }

    private synchronized void startVolumeWatcher() {
        if (volWatchActive && volWatchThread != null && volWatchThread.isAlive()) {
            return;
        }
        volWatchActive = true;
        final Context appCtx = getApplicationContext();
        volWatchThread = new Thread(() -> {
            LocalAdb.watchVolumeKeys(
                    appCtx,
                    (dir, down, isRepeat) -> handler.post(() -> onVolumeKeyState(dir, down, isRepeat)),
                    () -> instance == this && volWatchActive
                            && ButtonMapper.isVolumeHoldFixEnabled(appCtx)
                            && (ButtonMapper.getVolumeHoldFixMode(appCtx) == ButtonMapper.VOLUME_FIX_MODE_1
                                || !LocalAdb.hasDumpPermission(appCtx))
            );
        }, "vol-hold-watch");
        volWatchThread.setDaemon(true);
        volWatchThread.start();
    }

    private synchronized void stopVolumeWatcher() {
        volWatchActive = false;
        volDir = 0;
        handler.removeCallbacks(volRepeatRunnable);
        if (volWatchThread != null) {
            volWatchThread.interrupt();
            volWatchThread = null;
        }
    }

    /**
     * Volume Hold Fix Engine using AudioService Binder dump:
     * - Mode 1 (Standard - Default): Active only when LocalAdb getevent is NOT connected (Secure Mode)
     *   and matches "Events log: volume changes" (Android 13-14+ TVs where WindowManager logs both DOWN & UP).
     * - Mode 2 (HY320 / Projector): Active in both Secure Mode and Persistent ADB Mode, matching both
     *   "Audio event log: volume changes" (Android 9-12, e.g. HY320) and "Events log: volume changes",
     *   plus IR pulse-burst handling.
     */
    private synchronized void startAudioDumpVolumeWatcher() {
        if (audioDumpWatchActive && audioDumpWatchThread != null && audioDumpWatchThread.isAlive()) {
            return;
        }
        audioDumpWatchActive = true;
        final Context appCtx = getApplicationContext();
        audioDumpWatchThread = new Thread(() -> {
            android.os.IBinder audioBinder = null;
            android.os.PowerManager pm = (android.os.PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
            String prevLastWmLine = null;
            boolean baselineInitialized = false;
            boolean wmKeyIsDown = false;
            long lastFastPulseMs = 0L;
            int lastFastPulseDir = 0;

            while (instance == this && audioDumpWatchActive && ButtonMapper.isVolumeHoldFixEnabled(appCtx)) {
                try {
                    int mode = ButtonMapper.getVolumeHoldFixMode(appCtx);
                    // In Mode 1 (Standard), when Persistent ADB Mode is active and getevent stream is connected, let getevent handle it
                    if (mode == ButtonMapper.VOLUME_FIX_MODE_1 && LocalAdb.volWatchConnected) {
                        if (wmKeyIsDown) {
                            wmKeyIsDown = false;
                            handler.post(() -> onVolumeKeyState(0, false, false));
                        }
                        prevLastWmLine = null;
                        baselineInitialized = false;
                        Thread.sleep(1000L);
                        continue;
                    }
                    // Require android.permission.DUMP and interactive screen
                    if (!LocalAdb.hasDumpPermission(appCtx) || (pm != null && !pm.isInteractive())) {
                        if (wmKeyIsDown) {
                            wmKeyIsDown = false;
                            handler.post(() -> onVolumeKeyState(0, false, false));
                        }
                        prevLastWmLine = null;
                        baselineInitialized = false;
                        Thread.sleep(1200L);
                        continue;
                    }
                    if (audioBinder == null) {
                        try {
                            Class<?> sm = Class.forName("android.os.ServiceManager");
                            java.lang.reflect.Method getService = sm.getMethod("getService", String.class);
                            audioBinder = (android.os.IBinder) getService.invoke(null, Context.AUDIO_SERVICE);
                        } catch (Throwable ignored) {
                        }
                        if (audioBinder == null) {
                            Thread.sleep(2000L);
                            continue;
                        }
                    }

                    android.os.ParcelFileDescriptor[] pipe = android.os.ParcelFileDescriptor.createPipe();
                    final android.os.ParcelFileDescriptor readPfd = pipe[0];
                    final android.os.ParcelFileDescriptor writePfd = pipe[1];
                    final android.os.IBinder binderRef = audioBinder;

                    // Run dump() on a helper thread and read lines on this thread
                    Thread writer = new Thread(() -> {
                        try {
                            binderRef.dump(writePfd.getFileDescriptor(), new String[0]);
                        } catch (Throwable ignored) {
                        } finally {
                            try {
                                writePfd.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }, "audio-dump-writer");
                    writer.setDaemon(true);
                    writer.start();

                    String secondLastWmLine = null;
                    String lastWmLine = null;
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(
                                    new android.os.ParcelFileDescriptor.AutoCloseInputStream(readPfd),
                                    java.nio.charset.StandardCharsets.UTF_8), 8192)) {
                        String line;
                        boolean inVolumeEvents = false;
                        while ((line = br.readLine()) != null) {
                            if (!inVolumeEvents) {
                                if (mode == ButtonMapper.VOLUME_FIX_MODE_2) {
                                    // Mode 2 (HY320 / Projector): matches both "Audio event log: volume changes" and "Events log: volume changes"
                                    if (line.contains("volume changes")) {
                                        inVolumeEvents = true;
                                    }
                                } else {
                                    // Mode 1 (Standard): matches "Events log: volume changes"
                                    if (line.contains("Events log: volume changes")) {
                                        inVolumeEvents = true;
                                    }
                                }
                                continue;
                            }
                            if (line.contains("from android/WindowManager")
                                    && line.contains("adjustSuggestedStreamVolume")) {
                                secondLastWmLine = lastWmLine;
                                lastWmLine = line;
                            } else if (line.startsWith("Events log:") || line.startsWith("Audio event log:")) {
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    if (!baselineInitialized) {
                        prevLastWmLine = lastWmLine;
                        baselineInitialized = true;
                    } else if (lastWmLine == null) {
                        prevLastWmLine = null;
                    } else if (!lastWmLine.equals(prevLastWmLine)) {
                        int dir = 0;
                        if (lastWmLine.contains("dir:ADJUST_RAISE")) {
                            dir = 1;
                        } else if (lastWmLine.contains("dir:ADJUST_LOWER")) {
                            dir = -1;
                        }
                        if (!wmKeyIsDown) {
                            boolean singleNewEvent = (prevLastWmLine == null && secondLastWmLine == null)
                                    || (prevLastWmLine != null && secondLastWmLine != null && secondLastWmLine.equals(prevLastWmLine));
                            if (singleNewEvent && dir != 0) {
                                wmKeyIsDown = true;
                                prevLastWmLine = lastWmLine;
                                final int d = dir;
                                handler.post(() -> onVolumeKeyState(d, true, false));
                            } else {
                                if (mode == ButtonMapper.VOLUME_FIX_MODE_2) {
                                    long nowMs = SystemClock.uptimeMillis();
                                    if (dir != 0 && lastFastPulseDir == dir && (nowMs - lastFastPulseMs) < 250L) {
                                        final int d = dir;
                                        handler.post(() -> stepVolume(d));
                                    }
                                    lastFastPulseDir = dir;
                                    lastFastPulseMs = nowMs;
                                }
                                prevLastWmLine = lastWmLine;
                            }
                        } else {
                            // Key was held down and a new WindowManager event arrived -> ACTION_UP!
                            wmKeyIsDown = false;
                            prevLastWmLine = lastWmLine;
                            handler.post(() -> onVolumeKeyState(0, false, false));
                        }
                    }

                    Thread.sleep(wmKeyIsDown ? 45L : 60L);
                } catch (InterruptedException ie) {
                    break;
                } catch (Throwable t) {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            if (wmKeyIsDown) {
                handler.post(() -> onVolumeKeyState(0, false, false));
            }
        }, "vol-audio-dump-watch");
        audioDumpWatchThread.setDaemon(true);
        audioDumpWatchThread.start();
    }

    private synchronized void stopAudioDumpVolumeWatcher() {
        audioDumpWatchActive = false;
        volDir = 0;
        handler.removeCallbacks(volRepeatRunnable);
        if (audioDumpWatchThread != null) {
            audioDumpWatchThread.interrupt();
            audioDumpWatchThread = null;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int code = event.getKeyCode();
        // Fallback when LocalAdb getevent watcher is not connected (Mode 1 or when DUMP permission is not active)
        if (!LocalAdb.volWatchConnected
                && (ButtonMapper.getVolumeHoldFixMode(this) == ButtonMapper.VOLUME_FIX_MODE_1 || !LocalAdb.hasDumpPermission(this))
                && (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)
                && ButtonMapper.isVolumeHoldFixEnabled(this)
                && !ButtonMapper.isKeyCustomMapped(this, code)) {
            int dir = (code == KeyEvent.KEYCODE_VOLUME_UP) ? 1 : -1;
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                onVolumeKeyState(dir, true, event.getRepeatCount() > 0);
            } else if (action == KeyEvent.ACTION_UP) {
                onVolumeKeyState(dir, false, false);
            }
            return false;
        }
        if (ButtonMapper.handleKeyEvent(this, event)) {
            return true;
        }
        return super.onKeyEvent(event);
    }

    static boolean sleep() {
        PowerService s = instance;
        return s != null && Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    static boolean sendGlobalAction(int action) {
        PowerService s = instance;
        return s != null && s.performGlobalAction(action);
    }

    static void forceStop(Context c, List<String> pkgs, Runnable done) {
        if (instance == null || pkgs.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        queue.clear();
        queue.addAll(pkgs);
        onDone = done;
        next(c);
    }

    private static void next(Context c) {
        handler.removeCallbacks(timeout);
        if (queue.isEmpty() || instance == null) {
            current = null;
            Runnable r = onDone;
            onDone = null;
            if (c != null) {
                c.startActivity(new Intent(c, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
            if (r != null) r.run();
            return;
        }
        current = queue.remove(0);
        clickedStop = false;
        clickedOk = false;
        try {
            c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            handler.postDelayed(timeout, 2500);
        } catch (Exception e) {
            next(c);
        }
    }

    private static void advance() {
        if (instance != null) next(instance);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e != null && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String fgPkg = e.getPackageName().toString();
            RecentAppsOverlay.recordAppUsed(this, fgPkg);

            // Fast boot guard: during the first 40s after system boot, if "Launch on System Boot" is enabled
            // and a stock OEM/Chinese TV launcher pops to the foreground, immediately bring tvOS Launcher to front!
            if (SystemClock.elapsedRealtime() < 160_000L
                    && (SystemClock.elapsedRealtime() - serviceConnectedElapsedMs) < 40_000L
                    && BootReceiver.isLaunchOnBootEnabled(this)) {
                for (String stockLauncher : Perms.getStockTvHomePackages()) {
                    if (stockLauncher.equals(fgPkg)) {
                        BootReceiver.launchHomeImmediately(this);
                        break;
                    }
                }
            }
        }
        if (current == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (!clickedStop) {
                AccessibilityNodeInfo stop = findButton(root,
                        "force stop", "force_stop", "forcestop",
                        "zorla durdur", "durdurmaya zorla",
                        "arr\u00eater", "forzar detenci\u00f3n", "beenden erzwingen", "\u5f3a\u884c\u505c\u6b62");
                if (stop != null) {
                    if (!stop.isEnabled()) {
                        advance();
                        return;
                    }
                    stop.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    clickedStop = true;
                    handler.removeCallbacks(timeout);
                    handler.postDelayed(timeout, 1500);
                    return;
                }
            }
            if (clickedStop && !clickedOk) {
                AccessibilityNodeInfo ok = findButton(root,
                        "android:id/button1", "ok", "tamam", "force stop", "zorla durdur");
                if (ok != null && ok.isEnabled()) {
                    ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    clickedOk = true;
                    handler.removeCallbacks(timeout);
                    handler.postDelayed(timeout, 250);
                }
            }
        } finally {
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo findButton(AccessibilityNodeInfo n, String... needles) {
        if (n == null) return null;
        String id = n.getViewIdResourceName() != null ? n.getViewIdResourceName().toLowerCase() : "";
        String txt = n.getText() != null ? n.getText().toString().toLowerCase() : "";
        String desc = n.getContentDescription() != null ? n.getContentDescription().toString().toLowerCase() : "";
        for (String s : needles) {
            if (id.contains(s) || txt.equals(s) || desc.equals(s)) {
                for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) {
                    if (p.isClickable()) return p;
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findButton(n.getChild(i), needles);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
    }
}
