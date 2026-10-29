package com.techgenius.tvoslauncher.present;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local admin URLs (port of NetworkUtils.kt): one per non-loopback IPv4 address. */
final class Net {
    private Net() { }

    static List<String> urls(int port) {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String u = "http://" + a.getHostAddress() + ":" + port;
                        if (!out.contains(u)) out.add(u);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) out.add("http://127.0.0.1:" + port);
        return out;
    }
}
