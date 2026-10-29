package com.techgenius.tvoslauncher.present;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Presentation feature entry point (replaces AppGraph.kt). Owns the repository, pairing and the
 * admin server, and forwards web commands to the on-screen player when it is open.
 * All user-facing options live in the launcher's Settings \u2192 Presentation.
 */
public final class Presenter {
    private Presenter() { }

    private static Repo repo;
    private static Pairing pairing;
    private static AdminServer server;
    private static volatile AdminServer.Gateway player;

    static final String PREFS = "presentation";
    static final String K_ENABLED = "enabled", K_BOOT = "start_on_boot", K_AWAKE = "keep_awake",
            K_INFO = "show_info_on_start";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Feature switch: the admin server runs (and browsers can send content) only while on. */
    public static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ENABLED, false);
    }

    public static boolean startOnBoot(Context c) {
        return prefs(c).getBoolean(K_BOOT, false);
    }

    public static boolean keepAwake(Context c) {
        return prefs(c).getBoolean(K_AWAKE, true);
    }

    public static boolean showInfoOnStart(Context c) {
        return true;  // the address and pairing code always show when a presentation starts
    }

    public static void set(Context c, String key, boolean on) {
        prefs(c).edit().putBoolean(key, on).apply();
        if (K_ENABLED.equals(key)) {
            if (on) ensureRunning(c);
            else stop();
        }
    }

    static synchronized void init(Context c) {
        if (repo != null) return;
        Context app = c.getApplicationContext();
        repo = new Repo(app);
        pairing = new Pairing(app);
    }

    static Repo repo(Context c) {
        init(c);
        return repo;
    }

    /** Starts the admin server when the feature is enabled (call from launcher start and boot). */
    public static synchronized void ensureRunning(Context c) {
        if (!enabled(c)) return;
        init(c);
        if (server == null) {
            server = new AdminServer(c, repo, pairing, new AdminServer.Gateway() {
                @Override public void reload() {
                    AdminServer.Gateway p = player;
                    if (p != null) p.reload();
                }

                @Override public boolean control(String command) {
                    AdminServer.Gateway p = player;
                    return p != null && p.control(command);
                }

                @Override public Model.Status status() {
                    AdminServer.Gateway p = player;
                    if (p != null) return p.status();
                    Model.Status s = new Model.Status();
                    s.available = false;
                    s.state = "not_on_screen";
                    s.error = "Presentation isn\u2019t on screen";
                    return s;
                }
            });
        }
        try {
            server.start();
        } catch (Exception e) {
            Log.w("tvOSPresent", "server start: " + e);
        }
    }

    static synchronized void stop() {
        if (server != null) server.stop();
    }

    public static boolean serverRunning() {
        return server != null && server.running();
    }

    /** First admin URL for the TV screen / Settings (e.g. http://192.168.1.13:8080). */
    public static String address() {
        return Net.urls(AdminServer.PORT).get(0);
    }

    public static String pin(Context c) {
        init(c);
        return pairing.pin;
    }

    /** QR code for the admin address (Settings shows it with the pairing code). */
    public static android.graphics.Bitmap qr(int size) {
        return Qr.bitmap(address(), size);
    }

    public static final String KEY_ENABLED = K_ENABLED, KEY_BOOT = K_BOOT, KEY_AWAKE = K_AWAKE, KEY_INFO = K_INFO;

    static void attach(AdminServer.Gateway g) {
        player = g;
    }

    static void detach(AdminServer.Gateway g) {
        if (player == g) player = null;
    }

    private static boolean bootHandled;

    /**
     * "Start on boot": opens the presentation once per boot (whichever comes first \u2014 the boot
     * broadcast or the Home screen starting up shortly after boot).
     */
    public static synchronized void openOnBootOnce(Context c) {
        if (bootHandled) return;
        bootHandled = true;
        if (enabled(c) && startOnBoot(c)) open(c);
    }

    /** True for the first few minutes after the TV booted. */
    public static boolean justBooted() {
        return android.os.SystemClock.elapsedRealtime() < 5 * 60_000L;
    }

    /** Opens the full-screen presentation. */
    public static void open(Context c) {
        ensureRunning(c);
        c.startActivity(new Intent(c, PresentationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
