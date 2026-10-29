package com.techgenius.tvoslauncher.present;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * Browser pairing (port of PairingManager.kt): a 6-digit PIN shown on the TV is exchanged once
 * for a bearer token the browser keeps. The PIN changes each time the launcher process starts.
 */
final class Pairing {
    private final SharedPreferences prefs;
    final String pin;

    Pairing(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("present_pairing", Context.MODE_PRIVATE);
        pin = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
    }

    static final int MAX_BROWSERS = 20;

    /**
     * Returns a new token when the PIN matches, else null. Each paired browser keeps its own token
     * (phones, laptops...), up to the last 20; pairing one no longer signs out the others.
     */
    String pair(String candidate) {
        if (candidate == null || !candidate.trim().equals(pin)) return null;
        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        java.util.List<String> list = tokens();
        list.add(token);
        while (list.size() > MAX_BROWSERS) list.remove(0);
        prefs.edit().putString("admin_tokens", String.join(",", list)).remove("admin_token").apply();
        return token;
    }

    /** Paired tokens, oldest first (the single token kept by earlier versions is carried over). */
    private java.util.List<String> tokens() {
        java.util.List<String> list = new java.util.ArrayList<>();
        String saved = prefs.getString("admin_tokens", "");
        for (String t : saved.split(",")) if (!t.isEmpty()) list.add(t);
        String legacy = prefs.getString("admin_token", null);
        if (legacy != null && !list.contains(legacy)) list.add(0, legacy);
        return list;
    }

    boolean valid(String token) {
        if (token == null || token.isEmpty()) return false;
        boolean ok = false;
        // Constant-time comparison against every paired browser (AuthTokens.kt).
        for (String expected : tokens()) ok |= MessageDigest.isEqual(token.getBytes(), expected.getBytes());
        return ok;
    }
}
