package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A streaming app's cloud account, as used by the dock's continue-watching popup (WatchPopup).
 * Subclasses: NuvioCloud, StremioCloud. Sign-in is always a QR / device-code flow, so no email
 * or password is ever stored. Every network method blocks: call them off the UI thread.
 */
abstract class WatchCloud {

    static final class NotSignedInException extends Exception {
        NotSignedInException(String app) { super("Not signed in to " + app); }
    }

    static final class QrExpiredException extends Exception {
        QrExpiredException(String status) { super("QR login " + status); }
    }

    static final class HttpException extends Exception {
        final int code;

        HttpException(int code, String body) {
            super("HTTP " + code + ": " + (body.length() > 200 ? body.substring(0, 200) : body));
            this.code = code;
        }
    }

    /** One account profile. {@link #index} starts at 1. */
    static final class Profile {
        int index;
        String name;
    }

    /** A pending QR login: encode {@link #qrUrl} in the QR; show {@link #userCode} as text. */
    static final class QrLogin {
        String deviceCode, userCode, verificationUrl, qrUrl, nonce;
        long expiresAt, intervalMs;

        boolean expired() { return System.currentTimeMillis() >= expiresAt; }
    }

    /** One continue-watching entry. Times are in milliseconds. */
    static final class Item {
        String contentId, type, videoId, title, poster;
        int season = -1, episode = -1;
        long position, duration, lastWatched;
        /** The previous episode was finished and this one hasn't been started. */
        boolean upNext;

        double fraction() { return duration > 0 ? position / (double) duration : 0; }

        boolean isSeries() { return "series".equals(type); }

        /** Base id for lookups: "tt0903747" from "tt0903747:2:4". */
        String baseId() {
            if (contentId.startsWith("tmdb:")) {
                String n = contentId.substring(5);
                int c = n.indexOf(':');
                return "tmdb:" + (c >= 0 ? n.substring(0, c) : n);
            }
            int c = contentId.indexOf(':');
            return c >= 0 ? contentId.substring(0, c) : contentId;
        }
    }

    final Context ctx;
    final SharedPreferences prefs;

    WatchCloud(Context c, String prefsName) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ the app

    /** Display name, e.g. "Nuvio". */
    abstract String name();

    /** Whether a dock tile with this package gets this popup. */
    abstract boolean ownsPackage(String pkg);

    /** Package to open titles in when the tile's own package isn't known. */
    abstract String defaultPackage();

    /** URL that opens the title in the app. */
    abstract String deepLink(Item it);

    /** Key in the launcher's "settings" prefs for the popup's on/off switch. */
    abstract String popupPrefKey();

    /** Settings switch: off = no popup, no sign-in prompt, no network calls. On by default. */
    boolean popupEnabled() {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(popupPrefKey(), true);
    }

    void setPopupEnabled(boolean on) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(popupPrefKey(), on).apply();
    }

    // ------------------------------------------------------------------ account

    abstract boolean isSignedIn();

    abstract void signOut();

    abstract QrLogin startQrLogin(String deviceName) throws Exception;

    /**
     * Checks a pending QR login once. Returns true when it was approved and the session is saved,
     * false while it is still waiting. Throws {@link QrExpiredException} when a new code is needed.
     */
    abstract boolean pollQrLogin(QrLogin l) throws Exception;

    /** Profile whose progress is shown. Apps without profiles always use 1. */
    int profile() { return 1; }

    void setProfile(int index) {}

    /** The account's profiles, sorted by index. Empty for apps without profiles. */
    List<Profile> profiles() throws Exception { return new ArrayList<>(); }

    /** Last fetched profiles, or an empty list. */
    List<Profile> loadProfilesCache() { return new ArrayList<>(); }

    // ------------------------------------------------------------------ continue watching

    /** Up to {@code max} in-progress titles (one per movie/show) for one profile, newest first. */
    abstract List<Item> continueWatching(int profile, int max) throws Exception;

    private static final String K_CW = "cw_cache";
    static final int MAX_CACHED_PROFILES = 6;

    /** Continue-watching cache key; each profile keeps its own row. */
    private static String cwKey(int profile) {
        return K_CW + "_" + profile;
    }

    /** Drops every cached row (on sign-out). */
    void clearCache(SharedPreferences.Editor e) {
        e.remove(K_CW);
        for (int p = 1; p <= MAX_CACHED_PROFILES; p++) e.remove(cwKey(p));
    }

    /** Last successful result, so the popup has something to show instantly after a restart. */
    List<Item> loadCache(int profile) {
        String s = prefs.getString(cwKey(profile), "");
        if (s.isEmpty()) return null;
        try {
            JSONArray a = new JSONArray(s);
            List<Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.contentId = o.getString("id");
                it.type = o.optString("type", "movie");
                it.videoId = o.optString("video", it.contentId);
                it.title = o.optString("title", "");
                it.poster = o.optString("poster", "");
                it.season = o.optInt("s", -1);
                it.episode = o.optInt("e", -1);
                it.position = o.optLong("pos");
                it.duration = o.optLong("dur");
                it.lastWatched = o.optLong("at");
                it.upNext = o.optBoolean("next", false);
                out.add(it);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    void saveCache(int profile, List<Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                a.put(new JSONObject().put("id", it.contentId).put("type", it.type).put("video", it.videoId)
                        .put("title", it.title).put("poster", it.poster).put("s", it.season).put("e", it.episode)
                        .put("pos", it.position).put("dur", it.duration).put("at", it.lastWatched)
                        .put("next", it.upNext));
            }
            prefs.edit().putString(cwKey(profile), a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ HTTP

    static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(8_000);
            con.setReadTimeout(10_000);
            con.setRequestProperty("Accept", "application/json");
            int code = con.getResponseCode();
            if (code != 200) throw new HttpException(code, "");
            return read(con.getInputStream());
        } finally {
            con.disconnect();
        }
    }

    static String read(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
