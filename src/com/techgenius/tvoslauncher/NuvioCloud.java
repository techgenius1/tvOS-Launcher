package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nuvio Cloud account access for the dock's "continue watching" popup.
 * Sign-in is the same QR / device flow the Nuvio TV app uses, so no email or password is ever
 * stored here — only the session tokens. Every method blocks: call them off the UI thread.
 *
 * Endpoints: https://nuvio.tv/docs (public API). The three QR-login calls are not in the public
 * docs (same ones the Nuvio TV app and the Home Assistant integration use) and could change.
 */
final class NuvioCloud {

    static final String BASE = "https://api.nuvio.tv";
    /** Nuvio's public client key (published in its API docs; ships inside every Nuvio app). */
    static final String KEY = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN";
    static final String PREFS = "nuvio_cloud";
    private static final String K_ACCESS = "access", K_REFRESH = "refresh", K_EXPIRES = "expires_at",
            K_PROFILE = "profile", K_CW = "cw_cache", K_META = "meta_cache", K_PROFILES = "profiles_cache";
    static final int MAX_PROFILES = 6;
    /** Ignore titles opened for less than a minute; Nuvio itself marks >= 90% as watched. */
    static final long MIN_POSITION_MS = 60_000L;
    static final double WATCHED_FRACTION = 0.90;

    static final class NotSignedInException extends Exception {
        NotSignedInException() { super("Not signed in to Nuvio"); }
    }

    static final class QrExpiredException extends Exception {
        QrExpiredException(String status) { super("Nuvio QR login " + status); }
    }

    static final class HttpException extends Exception {
        final int code;

        HttpException(int code, String body) {
            super("Nuvio HTTP " + code + ": " + (body.length() > 200 ? body.substring(0, 200) : body));
            this.code = code;
        }
    }

    /** One Nuvio profile. {@link #index} (1..6) is what the sync calls take as p_profile_id. */
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

    /** One continue-watching entry. title/poster come from Nuvio's library, Cinemeta or TMDB. */
    static final class Item {
        String contentId, type, videoId, title, poster;
        int season = -1, episode = -1;
        long position, duration, lastWatched;

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

    private final Context ctx;
    private final SharedPreferences prefs;

    NuvioCloud(Context c) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isSignedIn() {
        return !prefs.getString(K_REFRESH, "").isEmpty();
    }

    /** Nuvio profile 1..6 whose progress is shown. Profile 1 is the main profile. */
    int profile() {
        return prefs.getInt(K_PROFILE, 1);
    }

    void setProfile(int index) {
        prefs.edit().putInt(K_PROFILE, Math.max(1, Math.min(MAX_PROFILES, index))).apply();
    }

    /** The account's profiles, sorted by index. Also cached for instant display next time. */
    List<Profile> profiles() throws Exception {
        JSONArray a = new JSONArray(rpc("sync_pull_profiles", new JSONObject()));
        List<Profile> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            int idx = o.optInt("profile_index", -1);
            if (idx < 1 || idx > MAX_PROFILES) continue;
            Profile p = new Profile();
            p.index = idx;
            p.name = o.isNull("name") ? "" : o.optString("name", "").trim();
            out.add(p);
        }
        out.sort((x, y) -> Integer.compare(x.index, y.index));
        JSONArray cache = new JSONArray();
        for (Profile p : out) cache.put(new JSONObject().put("i", p.index).put("n", p.name));
        prefs.edit().putString(K_PROFILES, cache.toString()).apply();
        return out;
    }

    /** Last fetched profiles, or an empty list. */
    List<Profile> loadProfilesCache() {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(K_PROFILES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.index = o.getInt("i");
                p.name = o.optString("n", "");
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    synchronized void signOut() {
        String access = prefs.getString(K_ACCESS, "");
        if (!access.isEmpty()) {
            try {
                post("/auth/v1/logout", null, access);
            } catch (Exception ignored) {
                // Signing out locally is what matters.
            }
        }
        SharedPreferences.Editor e = prefs.edit()
                .remove(K_ACCESS).remove(K_REFRESH).remove(K_EXPIRES).remove(K_CW).remove(K_PROFILES);
        for (int p = 1; p <= MAX_PROFILES; p++) e.remove(cwKey(p));
        e.apply();
    }

    /** Continue-watching cache key; each profile keeps its own row. */
    private static String cwKey(int profile) {
        return K_CW + "_" + profile;
    }

    // ------------------------------------------------------------------ QR login

    QrLogin startQrLogin(String deviceName) throws Exception {
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        String nonce = Base64.encodeToString(raw, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        JSONObject body = new JSONObject()
                .put("p_device_nonce", nonce)
                .put("p_redirect_base_url", "https://nuvio.tv/link")
                .put("p_device_type", "tv")
                .put("p_device_name", deviceName);
        JSONObject o = new JSONArray(post("/rest/v1/rpc/start_device_login_session", body, null)).optJSONObject(0);
        if (o == null) throw new IllegalStateException("Nuvio returned no login session");

        QrLogin l = new QrLogin();
        l.nonce = nonce;
        l.deviceCode = o.getString("device_code");
        l.userCode = o.getString("user_code");
        l.verificationUrl = o.optString("verification_uri", "https://nuvio.tv/link");
        String complete = o.optString("verification_uri_complete", "");
        l.qrUrl = complete.isEmpty() ? l.verificationUrl + "?code=" + l.userCode : complete;
        l.intervalMs = Math.max(2, o.optLong("poll_interval_seconds", 3)) * 1000L;
        // The server's codes last 5 minutes; it also reports "expired" itself when polled.
        l.expiresAt = System.currentTimeMillis() + 5 * 60_000L - 5_000L;
        return l;
    }

    /**
     * Checks a pending QR login once. Returns true when it was approved and the session is saved,
     * false while it is still waiting. Throws {@link QrExpiredException} when a new code is needed.
     */
    synchronized boolean pollQrLogin(QrLogin l) throws Exception {
        if (l.expired()) throw new QrExpiredException("expired");
        JSONObject body = new JSONObject().put("p_code", l.deviceCode).put("p_device_nonce", l.nonce);
        JSONObject s = new JSONArray(post("/rest/v1/rpc/poll_tv_login_session", body, null)).optJSONObject(0);
        if (s == null) return false;
        long next = s.optLong("poll_interval_seconds", 0);
        if (next > 0) l.intervalMs = Math.max(2, next) * 1000L;

        String status = s.optString("status", "").toLowerCase();
        if ("approved".equals(status)) {
            JSONObject ex = new JSONObject().put("code", l.deviceCode).put("device_nonce", l.nonce);
            saveSession(new JSONObject(post("/functions/v1/tv-logins-exchange", ex, null)));
            return true;
        }
        if ("expired".equals(status) || "used".equals(status) || "cancelled".equals(status)) {
            throw new QrExpiredException(status);
        }
        return false;  // "pending"
    }

    // ------------------------------------------------------------------ continue watching

    /**
     * Up to {@code max} in-progress titles (one per movie/show) for one profile, newest first,
     * with title + poster.
     */
    List<Item> continueWatching(int profile, int max) throws Exception {
        JSONObject body = new JSONObject().put("p_profile_id", profile).put("p_limit", 200);
        JSONArray rows = new JSONArray(rpc("sync_pull_watch_progress", body));

        // Rows arrive newest first; keep the newest episode per show.
        Map<String, Item> latest = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i);
            Item it = new Item();
            it.contentId = o.optString("content_id", "");
            it.type = o.optString("content_type", "movie");
            it.videoId = o.optString("video_id", it.contentId);
            it.season = o.isNull("season") ? -1 : o.optInt("season", -1);
            it.episode = o.isNull("episode") ? -1 : o.optInt("episode", -1);
            it.position = o.optLong("position");
            it.duration = o.optLong("duration");
            it.lastWatched = o.optLong("last_watched");
            if (it.contentId.isEmpty() || it.duration <= 0 || it.position < MIN_POSITION_MS
                    || it.fraction() >= WATCHED_FRACTION) continue;
            Item prev = latest.get(it.contentId);
            if (prev == null || it.lastWatched > prev.lastWatched) latest.put(it.contentId, it);
        }
        List<Item> out = new ArrayList<>(latest.values());
        out.sort((a, b) -> Long.compare(b.lastWatched, a.lastWatched));
        if (out.size() > max) out = new ArrayList<>(out.subList(0, max));

        enrich(profile, out);
        saveCache(profile, out);
        return out;
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
                out.add(it);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCache(int profile, List<Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                a.put(new JSONObject().put("id", it.contentId).put("type", it.type).put("video", it.videoId)
                        .put("title", it.title).put("poster", it.poster).put("s", it.season).put("e", it.episode)
                        .put("pos", it.position).put("dur", it.duration).put("at", it.lastWatched));
            }
            prefs.edit().putString(cwKey(profile), a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Progress rows carry no title or artwork. Fill them from (1) a small on-device cache,
     * (2) the profile's Nuvio library, (3) Cinemeta for IMDb ids, (4) TMDB for tmdb: ids when a
     * TMDB token is set in the launcher's Suggestions settings.
     */
    private void enrich(int profile, List<Item> items) {
        JSONObject meta;
        try {
            meta = new JSONObject(prefs.getString(K_META, "{}"));
        } catch (Exception e) {
            meta = new JSONObject();
        }
        Map<String, String[]> library = null;
        boolean changed = false;

        for (Item it : items) {
            String id = it.baseId();
            JSONObject m = meta.optJSONObject(id);
            if (m != null) {
                it.title = m.optString("t", "");
                it.poster = m.optString("p", "");
                if (!it.title.isEmpty()) continue;
            }
            String[] found = null;
            if (library == null) library = libraryIndex(profile);
            found = library.get(id);
            if (found == null || found[1].isEmpty()) {
                try {
                    String[] alt = id.startsWith("tt") ? cinemeta(it.isSeries(), id)
                            : id.startsWith("tmdb:") ? tmdb(it.isSeries(), id.substring(5)) : null;
                    if (alt != null) found = alt;
                } catch (Exception ignored) {
                    // Title stays empty; the card falls back to the content type.
                }
            }
            if (found != null) {
                it.title = found[0];
                it.poster = found[1];
                try {
                    meta.put(id, new JSONObject().put("t", it.title).put("p", it.poster));
                    changed = true;
                } catch (Exception ignored) {
                }
            }
        }
        if (changed) prefs.edit().putString(K_META, meta.toString()).apply();
    }

    /** content_id -> {name, poster} from the profile's Nuvio library (first 500 items). */
    private Map<String, String[]> libraryIndex(int profile) {
        Map<String, String[]> map = new HashMap<>();
        try {
            JSONObject body = new JSONObject().put("p_profile_id", profile).put("p_limit", 500).put("p_offset", 0);
            JSONArray a = new JSONArray(rpc("sync_pull_library", body));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String poster = o.isNull("poster") ? "" : o.optString("poster", "");
                map.put(o.optString("content_id"), new String[]{o.optString("name", ""), poster});
            }
        } catch (Exception ignored) {
            // The library is only a shortcut for titles/posters.
        }
        return map;
    }

    private static String[] cinemeta(boolean series, String imdbId) throws Exception {
        String url = "https://v3-cinemeta.strem.io/meta/" + (series ? "series" : "movie") + "/" + imdbId + ".json";
        JSONObject meta = new JSONObject(get(url)).optJSONObject("meta");
        if (meta == null) return null;
        return new String[]{meta.optString("name", ""), meta.optString("poster", "")};
    }

    private String[] tmdb(boolean series, String tmdbId) throws Exception {
        if (!Suggestions.hasToken(ctx)) return null;
        JSONObject o = Suggestions.get(ctx, (series ? "/tv/" : "/movie/") + tmdbId);
        String title = o.optString(series ? "name" : "title", "");
        String path = o.isNull("poster_path") ? "" : o.optString("poster_path", "");
        return new String[]{title, path.isEmpty() ? "" : "https://image.tmdb.org/t/p/w342" + path};
    }

    // ------------------------------------------------------------------ session + HTTP

    /** Authenticated RPC; refreshes the session once if the access token was rejected. */
    private String rpc(String name, JSONObject body) throws Exception {
        String token = accessToken();
        try {
            return post("/rest/v1/rpc/" + name, body, token);
        } catch (HttpException e) {
            if (e.code != 401) throw e;
            return post("/rest/v1/rpc/" + name, body, refresh());
        }
    }

    private synchronized String accessToken() throws Exception {
        if (!isSignedIn()) throw new NotSignedInException();
        String access = prefs.getString(K_ACCESS, "");
        if (access.isEmpty() || System.currentTimeMillis() > prefs.getLong(K_EXPIRES, 0) - 60_000L) {
            return refresh();
        }
        return access;
    }

    private synchronized String refresh() throws Exception {
        String rt = prefs.getString(K_REFRESH, "");
        if (rt.isEmpty()) throw new NotSignedInException();
        try {
            JSONObject j = new JSONObject(post("/auth/v1/token?grant_type=refresh_token",
                    new JSONObject().put("refresh_token", rt), null));
            saveSession(j);
            return j.getString("access_token");
        } catch (HttpException e) {
            // 400/401: the refresh token is dead -> back to signed out (the popup shows the QR again).
            // Anything else (5xx, 429, offline): keep the session and try again later.
            if (e.code == 400 || e.code == 401) {
                prefs.edit().remove(K_ACCESS).remove(K_REFRESH).remove(K_EXPIRES).apply();
                throw new NotSignedInException();
            }
            throw e;
        }
    }

    private void saveSession(JSONObject j) throws Exception {
        String access = j.getString("access_token");
        String refresh = j.optString("refresh_token", "");
        if (refresh.isEmpty()) refresh = prefs.getString(K_REFRESH, "");
        if (refresh.isEmpty()) throw new IllegalStateException("Nuvio returned no refresh token");
        long expiresIn = j.optLong("expires_in", 3600);
        prefs.edit()
                .putString(K_ACCESS, access)
                .putString(K_REFRESH, refresh)
                .putLong(K_EXPIRES, System.currentTimeMillis() + expiresIn * 1000L)
                .apply();
    }

    /** POST to Nuvio. Unauthenticated calls send the public key as the bearer, like Nuvio's own apps. */
    private static String post(String path, JSONObject body, String bearer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(10_000);
            con.setReadTimeout(15_000);
            con.setDoOutput(true);
            con.setRequestProperty("apikey", KEY);
            con.setRequestProperty("Authorization", "Bearer " + (bearer != null ? bearer : KEY));
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            try (OutputStream os = con.getOutputStream()) {
                os.write((body != null ? body : new JSONObject()).toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
            String text = in == null ? "" : read(in);
            if (code < 200 || code >= 300) throw new HttpException(code, text);
            return text.isEmpty() ? "null" : text;
        } finally {
            con.disconnect();
        }
    }

    private static String get(String url) throws Exception {
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

    private static String read(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
