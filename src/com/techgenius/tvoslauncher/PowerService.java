package com.techgenius.tvoslauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * tvOS Launcher's accessibility switch, turned on by the user in the TV's Accessibility settings. It does
 * two things, both only when asked from tvOS Launcher: put the TV to sleep (Power Off, Sleep Timer) on TVs
 * where device admin is switched off, and close apps for Clean by pressing Force stop on each app's
 * info page. It reads nothing else on screen.
 */
public class PowerService extends AccessibilityService {
    static PowerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> queue = new ArrayList<>();
    private Runnable onDone;
    private int step;

    // Optional long-press volume button repeat fix (matching tvOS 2.0.4 RemapService, off by default)
    private android.media.AudioManager audioManager;
    private int volDir = 0;
    private long volDownTime = 0L;
    private volatile boolean volWatchActive = false;
    private volatile boolean volWatchConnected = false;
    private Thread volWatchThread;

    private final Runnable volRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (volDir == 0 || audioManager == null
                    || android.os.SystemClock.uptimeMillis() - volDownTime > 8000L) {
                volDir = 0;
                return;
            }
            int adjust = volDir > 0
                    ? android.media.AudioManager.ADJUST_RAISE
                    : android.media.AudioManager.ADJUST_LOWER;
            audioManager.adjustSuggestedStreamVolume(
                    adjust,
                    android.media.AudioManager.USE_DEFAULT_STREAM_TYPE,
                    android.media.AudioManager.FLAG_SHOW_UI);
            handler.postDelayed(this, 85L);
        }
    };

    void onVolumeKeyState(int dir, boolean down) {
        if (!ButtonMapper.isVolumeHoldFixEnabled(this)) return;
        if (!down) {
            handler.removeCallbacks(volRepeatRunnable);
            if (volDir == dir) {
                volDir = 0;
            }
            return;
        }
        if (volDir == dir) {
            return;
        }
        handler.removeCallbacks(volRepeatRunnable);
        volDir = dir;
        volDownTime = android.os.SystemClock.uptimeMillis();
        handler.postDelayed(volRepeatRunnable, 320L);
    }

    void syncVolumeHoldFix() {
        boolean enabled = ButtonMapper.isVolumeHoldFixEnabled(this);
        if (!enabled) {
            volWatchActive = false;
            volWatchConnected = false;
            volDir = 0;
            handler.removeCallbacks(volRepeatRunnable);
            if (volWatchThread != null) {
                volWatchThread.interrupt();
                volWatchThread = null;
            }
            return;
        }
        if (audioManager == null) {
            audioManager = getSystemService(android.media.AudioManager.class);
        }
        volWatchActive = true;
        if (volWatchThread == null || !volWatchThread.isAlive()) {
            volWatchThread = new Thread(() -> {
                while (volWatchActive && instance == this) {
                    boolean connected = LocalAdb.watchVolumeKeys(
                            this,
                            (dir, down) -> {
                                volWatchConnected = true;
                                handler.post(() -> onVolumeKeyState(dir, down));
                            },
                            () -> volWatchActive && instance == this);
                    volWatchConnected = false;
                    if (!volWatchActive || instance != this) break;
                    try {
                        Thread.sleep(connected ? 2000L : 5000L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "tvos-vol-watch");
            volWatchThread.setDaemon(true);
            volWatchThread.start();
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        audioManager = getSystemService(android.media.AudioManager.class);
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Exception ignored) { }

        syncVolumeHoldFix();
        LocalAdb.ensureKeepAliveWatchdog(this);
        if (LocalAdb.hasConflictingAccessibilityService(this)) {
            LocalAdb.checkAndRepairPermissionsIfNeeded(this, true);
        }
    }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        if (event != null) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && LocalAdb.hasConflictingAccessibilityService(this)) {
                LocalAdb.checkAndRepairPermissionsIfNeeded(this, true);
            }
            int code = event.getKeyCode();
            if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP
                    || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (!volWatchConnected && ButtonMapper.isVolumeHoldFixEnabled(this)) {
                    int dir = (code == android.view.KeyEvent.KEYCODE_VOLUME_UP) ? 1 : -1;
                    int action = event.getAction();
                    if (action == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        onVolumeKeyState(dir, true);
                    } else if (action == android.view.KeyEvent.ACTION_UP) {
                        onVolumeKeyState(dir, false);
                    }
                }
                return false;
            }
            if (code == android.view.KeyEvent.KEYCODE_VOLUME_MUTE) {
                return false;
            }
        }
        if (ButtonMapper.handleKeyEvent(this, event)) {
            return true;
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e != null && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (LocalAdb.hasConflictingAccessibilityService(this)) {
                LocalAdb.checkAndRepairPermissionsIfNeeded(this, true);
            }
            CharSequence pkgCs = e.getPackageName();
            if (pkgCs != null && pkgCs.length() > 0) {
                RecentAppsOverlay.recordAppUsed(this, pkgCs.toString());
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        volWatchActive = false;
        volWatchConnected = false;
        volDir = 0;
        handler.removeCallbacks(volRepeatRunnable);
        if (volWatchThread != null) {
            volWatchThread.interrupt();
            volWatchThread = null;
        }
        ControlPanelOverlay.dismiss();
        RecentAppsOverlay.dismiss();
        instance = null;
        Context appCtx = getApplicationContext();
        super.onDestroy();
        // If Keep Alive is enabled and the TV OS disabled Accessibility on its own, restore it automatically
        LocalAdb.scheduleKeepAliveRepair(appCtx, 2000L);
    }

    /** Screen off / sleep. Returns false when the service isn't on. */
    static boolean sleep() {
        PowerService s = instance;
        return s != null && android.os.Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Force-stops each app via its App info page, then returns to tvOS Launcher and runs {@code done}. */
    static void forceStop(Context c, List<String> pkgs, Runnable done) {
        PowerService s = instance;
        if (s == null) {
            done.run();
            return;
        }
        s.queue.clear();
        s.queue.addAll(pkgs);
        s.onDone = done;
        s.next();
    }

    private void next() {
        if (queue.isEmpty()) {
            Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(home);
            if (onDone != null) handler.postDelayed(onDone, 300);
            onDone = null;
            return;
        }
        String pkg = queue.remove(0);
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY));
        } catch (Exception e) {
            next();
            return;
        }
        step = 0;
        handler.postDelayed(this::pressForceStop, 900);
    }

    /** Presses "Force stop", then the confirmation "OK"; gives up on an app after a few tries. */
    private void pressForceStop() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean clicked = false;
        if (root != null) {
            String[] labels = step == 0 ? new String[]{"Force stop", "FORCE STOP", "Force Stop"} : new String[]{"OK", "Ok"};
            for (String l : labels) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(l);
                for (AccessibilityNodeInfo n : found) {
                    AccessibilityNodeInfo t = n;
                    while (t != null && !t.isClickable()) t = t.getParent();
                    if (t != null && t.isEnabled() && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clicked = true;
                        break;
                    }
                }
                if (clicked) break;
            }
        }
        if (clicked && step == 0) {
            step = 1;
            handler.postDelayed(this::pressForceStop, 600);
            return;
        }
        if (!clicked && step == 0 && retries++ < 3) {
            handler.postDelayed(this::pressForceStop, 500);
            return;
        }
        retries = 0;
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(this::next, 400);
    }

    private int retries;
}
