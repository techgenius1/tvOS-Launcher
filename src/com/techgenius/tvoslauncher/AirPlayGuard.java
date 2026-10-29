package com.techgenius.tvoslauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.InetAddress;

/**
 * Keeps the AirPlay receiver (io.github.jqssun.airplay) visible to Macs and iPhones. Only acts when
 * that app is installed. After long sleeps it can keep running without announcing itself; tvOS Launcher then
 * asks the network the way a Mac does (an mDNS query) and, if the receiver doesn't answer, re-applies
 * its server name through the app's settings receiver, which restarts the server and re-announces it
 * in the background. If the app was fully stopped, it is started through its own start-up receiver,
 * also with nothing on screen. Checked on
 * wake, when the network returns and every 15 minutes while Home is open.
 */
final class AirPlayGuard {
    static final String PKG = "io.github.jqssun.airplay";
    static final String RECEIVER = PKG + ".SettingsReceiver";
    private static long lastCheck;
    private static boolean busy;

    private AirPlayGuard() {
    }

    static void check(Context ctx, boolean force) {
        Context c = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        if (busy || !Suggestions.installed(c, PKG) || (!force && now - lastCheck < 10 * 60 * 1000L)) return;
        lastCheck = now;
        busy = true;
        learnName(c);
        Handler h = new Handler(Looper.getMainLooper());
        // After waking, give Wi-Fi a moment before asking the network.
        h.postDelayed(() -> new Thread(() -> {
            boolean visible = visibleOnNetwork(c);
            h.post(() -> {
                busy = false;
                if (visible) return;
                android.util.Log.i("TvHome", "AirPlay not answering on the network; restarting it");
                restart(c);
                h.postDelayed(() -> new Thread(() -> {
                    if (!visibleOnNetwork(c)) h.post(() -> launchBriefly(c));
                    lastCheck = System.currentTimeMillis();
                }).start(), 8000);
            });
        }).start(), force ? 8000 : 0);
    }

    /**
     * Sends an mDNS query for AirPlay receivers (224.0.0.251:5353) and listens for 3 s for an answer
     * carrying this receiver's name. Returns true when unsure, so it never restarts on a guess.
     */
    static boolean visibleOnNetwork(Context c) {
        String name = c.getSharedPreferences("airplay", Context.MODE_PRIVATE).getString("name", null);
        if (name == null) return true;
        WifiManager wm = c.getSystemService(WifiManager.class);
        WifiManager.MulticastLock lock = wm.createMulticastLock("tvos_airplay_check");
        java.net.DatagramSocket sock = null;
        try {
            lock.acquire();
            InetAddress group = InetAddress.getByName("224.0.0.251");
            // Query from an ordinary port: mDNS responders answer such "legacy" queries directly to
            // the asker, so there's no need to share port 5353 with the system's own responder.
            sock = new java.net.DatagramSocket();
            sock.setSoTimeout(400);
            byte[] q = query("_airplay._tcp.local");
            byte[] want = name.getBytes("UTF-8");
            long end = System.currentTimeMillis() + 3000;
            int sent = 0;
            while (System.currentTimeMillis() < end) {
                if (sent < 3) {
                    sock.send(new java.net.DatagramPacket(q, q.length, group, 5353));
                    sent++;
                }
                byte[] buf = new byte[9000];
                java.net.DatagramPacket p = new java.net.DatagramPacket(buf, buf.length);
                try {
                    sock.receive(p);
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                }
                if (contains(buf, p.getLength(), want)) return true;
            }
            android.util.Log.i("TvHome", "AirPlay check: \"" + name + "\" did not answer");
            return false;
        } catch (Exception e) {
            android.util.Log.w("TvHome", "AirPlay check failed: " + e);
            return true;
        } finally {
            if (sock != null) sock.close();
            if (lock.isHeld()) lock.release();
        }
    }

    /** A one-question DNS query (PTR, IN). */
    private static byte[] query(String qname) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] header = {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0};
        o.write(header, 0, header.length);
        for (String label : qname.split("\\.")) {
            o.write(label.length());
            o.write(label.getBytes(), 0, label.length());
        }
        byte[] tail = {0, 0, 12, 0, 1};
        o.write(tail, 0, tail.length);
        return o.toByteArray();
    }

    private static boolean contains(byte[] buf, int len, byte[] want) {
        outer:
        for (int i = 0; i + want.length <= len; i++) {
            for (int j = 0; j < want.length; j++) if (buf[i + j] != want[j]) continue outer;
            return true;
        }
        return false;
    }

    /** Re-applies the current server name: the app restarts its server and announces itself again. */
    static void restart(Context c) {
        Intent list = new Intent(PKG + ".LIST_SETTINGS").setClassName(PKG, RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        c.sendOrderedBroadcast(list, null, new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String name = readName(getResultData());
                if (name != null) ctx.getSharedPreferences("airplay", Context.MODE_PRIVATE).edit().putString("name", name).apply();
                else name = ctx.getSharedPreferences("airplay", Context.MODE_PRIVATE).getString("name", null);
                if (name == null) name = android.provider.Settings.Global.getString(ctx.getContentResolver(), "device_name");
                if (name == null) name = "Android TV";
                ctx.sendBroadcast(new Intent(PKG + ".SET_SETTING").setClassName(PKG, RECEIVER)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .putExtra("key", "server_name").putExtra("value", name));
            }
        }, null, 0, null, null);
    }

    /** Reads the receiver's name without changing anything, so the network check knows what to look for. */
    static void learnName(Context c) {
        Intent list = new Intent(PKG + ".LIST_SETTINGS").setClassName(PKG, RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        c.sendOrderedBroadcast(list, null, new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String n = readName(getResultData());
                if (n != null) ctx.getSharedPreferences("airplay", Context.MODE_PRIVATE).edit().putString("name", n).apply();
            }
        }, null, 0, null, null);
    }

    private static String readName(String json) {
        try {
            return new JSONObject(json).getJSONObject("server_name").getString("value");
        } catch (Exception e) {
            return null;
        }
    }

    /** When the app is fully stopped: start it silently through its own start-up receiver (no screen). */
    private static void launchBriefly(Context c) {
        android.util.Log.i("TvHome", "AirPlay still hidden; starting it in the background");
        c.sendBroadcast(new Intent("android.intent.action.QUICKBOOT_POWERON").setClassName(PKG, PKG + ".BootReceiver")
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND));
    }
}
