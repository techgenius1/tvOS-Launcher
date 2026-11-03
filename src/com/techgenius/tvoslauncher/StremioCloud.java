package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Stremio account access for the dock's "continue watching" popup.
 *
 * Sign-in uses link.stremio.com, the same "link a device" flow Stremio's own TV app uses: the TV
 * shows a code, the phone opens the link and signs in, and the TV receives the session key. Only
 * that key is stored. Continue watching is read from the account's library (the same data the
 * Stremio app syncs), using Stremio's own rule for what counts as "continue watching".
 *
 * Endpoints are the ones stremio-core (Stremio's open-source app core) calls. They aren't a
 * documented public API and could change.
 */
final class StremioCloud extends WatchCloud {

    private static final String API = "https://api.strem.io/api/";
    private static final String LINK = "https://link.stremio.com/api/v2/";
    static final String PREFS = "stremio_cloud";
    private static final String K_AUTH = "auth_key";
    /** Only the most recently changed library items can be in continue watching; fetch just those. */
    private static final int RECENT_ITEMS = 60;
    /** link.stremio.com error code while a code is waiting (and for unknown codes). */
    private static final int LINK_PENDING = 101;
    /** api.strem.io error code for an unknown/expired session key. */
    private static final int SESSION_GONE = 1;

    StremioCloud(Context c) {
        super(c, PREFS);
    }

    @Override String name() { return "Stremio"; }

    @Override boolean ownsPackage(String pkg) { return pkg != null && pkg.startsWith("com.stremio."); }

    @Override String defaultPackage() { return Suggestions.STREMIO.pkg; }

    /**
     * stremio:///detail/{type}/{id}/{videoId}: for a show this opens the episode that was being
     * watched (its stream list), for a movie the movie's streams.
     */
    @Override String deepLink(Item it) {
        String video = it.videoId == null || it.videoId.isEmpty() ? it.contentId : it.videoId;
        return "stremio:///detail/" + Uri.encode(it.type) + "/" + Uri.encode(it.contentId, ":")
                + "/" + Uri.encode(video, ":");
    }

    @Override String popupPrefKey() { return "stremio_popup"; }

    @Override boolean isSignedIn() {
        return !prefs.getString(K_AUTH, "").isEmpty();
    }

    @Override synchronized void signOut() {
        String auth = prefs.getString(K_AUTH, "");
        if (!auth.isEmpty()) {
            try {
                post(API + "logout", new JSONObject().put("authKey", auth));
            } catch (Exception ignored) {
                // Signing out locally is what matters.
            }
        }
        SharedPreferences.Editor e = prefs.edit().remove(K_AUTH);
        clearCache(e);
        e.apply();
    }

    // ------------------------------------------------------------------ QR login

    @Override QrLogin startQrLogin(String deviceName) throws Exception {
        JSONObject r = new JSONObject(get(LINK + "create?type=Create")).optJSONObject("result");
        if (r == null || r.optString("code", "").isEmpty()) {
            throw new IllegalStateException("Stremio returned no link code");
        }
        QrLogin l = new QrLogin();
        l.deviceCode = l.userCode = r.getString("code");
        l.verificationUrl = "https://link.stremio.com";
        l.qrUrl = r.optString("link", l.verificationUrl + "/" + l.userCode);
        l.intervalMs = 3_000L;
        // The server doesn't say how long a code lives and answers "pending" and "unknown code"
        // the same way, so ask for a fresh code every few minutes.
        l.expiresAt = System.currentTimeMillis() + 5 * 60_000L;
        return l;
    }

    @Override synchronized boolean pollQrLogin(QrLogin l) throws Exception {
        if (l.expired()) throw new QrExpiredException("expired");
        JSONObject o = new JSONObject(get(LINK + "read?type=Read&code=" + Uri.encode(l.deviceCode)));
        JSONObject r = o.optJSONObject("result");
        String auth = r != null ? r.optString("authKey", "") : "";
        if (!auth.isEmpty()) {
            prefs.edit().putString(K_AUTH, auth).apply();
            return true;
        }
        JSONObject err = o.optJSONObject("error");
        if (err != null && err.optInt("code") != LINK_PENDING) {
            throw new IllegalStateException("Stremio link: " + err.optString("message"));
        }
        return false;  // still waiting
    }

    // ------------------------------------------------------------------ continue watching

    @Override List<Item> continueWatching(int profile, int max) throws Exception {
        JSONArray lib = recentLibrary();
        List<Item> out = new ArrayList<>();
        List<Long> mtimes = new ArrayList<>();
        for (int i = 0; i < lib.length(); i++) {
            JSONObject o = lib.optJSONObject(i);
            if (o == null || !inContinueWatching(o)) continue;
            JSONObject st = o.optJSONObject("state");
            Item it = new Item();
            it.contentId = o.optString("_id", "");
            it.type = o.optString("type", "movie");
            it.title = o.optString("name", "");
            it.poster = o.isNull("poster") ? "" : o.optString("poster", "");
            it.videoId = st.isNull("video_id") ? "" : st.optString("video_id", "");
            it.position = st.optLong("timeOffset");
            it.duration = st.optLong("duration");
            // Stremio sets the offset to 1 ms when it moves on to the next, unstarted episode.
            it.upNext = it.position <= 1_000L;
            if (it.upNext) it.position = 0;
            long mtime = time(o.opt("_mtime"));
            long watched = time(st.opt("lastWatched"));
            it.lastWatched = watched > 0 ? watched : mtime;
            episode(it);
            if (it.contentId.isEmpty()) continue;
            // Newest first by modification time, like Stremio's own continue watching row.
            int at = 0;
            while (at < mtimes.size() && mtimes.get(at) >= mtime) at++;
            out.add(at, it);
            mtimes.add(at, mtime);
        }
        if (out.size() > max) out = new ArrayList<>(out.subList(0, max));
        saveCache(profile, out);
        return out;
    }

    /**
     * Stremio's rule (stremio-core LibraryItem::is_in_continue_watching): a real library item (or
     * one just watched without adding it) with a playback position. Live channels are skipped.
     */
    private static boolean inContinueWatching(JSONObject o) {
        JSONObject st = o.optJSONObject("state");
        if (st == null) return false;
        String type = o.optString("type", "");
        if (type.equals("other") || type.equals("tv")) return false;
        JSONObject hints = o.optJSONObject("behaviorHints");
        if (hints != null && hints.optBoolean("isLive", false)) return false;
        boolean removed = o.optBoolean("removed", false), temp = o.optBoolean("temp", false);
        return (!removed || temp) && st.optLong("timeOffset") > 0;
    }

    /** Season/episode from the video id: "tt0903747:2:4" -> S2 E4, "kitsu:123:5" -> E5. */
    private static void episode(Item it) {
        if (it.videoId.isEmpty() || !it.videoId.startsWith(it.contentId + ":")) return;
        String[] parts = it.videoId.substring(it.contentId.length() + 1).split(":");
        try {
            if (parts.length == 2) {
                it.season = Integer.parseInt(parts[0]);
                it.episode = Integer.parseInt(parts[1]);
            } else if (parts.length == 1) {
                it.episode = Integer.parseInt(parts[0]);
            }
        } catch (NumberFormatException ignored) {
            it.season = it.episode = -1;
        }
    }

    /**
     * The most recently changed library items. datastoreMeta lists every item's id and change
     * time (small); then only the newest ones are fetched in full, instead of the whole library.
     */
    private JSONArray recentLibrary() throws Exception {
        JSONArray ids = new JSONArray();
        try {
            JSONArray meta = (JSONArray) call("datastoreMeta", new JSONObject().put("collection", "libraryItem"));
            List<String> sortedIds = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            for (int i = 0; i < meta.length(); i++) {
                JSONArray m = meta.optJSONArray(i);
                if (m == null || m.length() < 2) continue;
                long t = time(m.opt(1));
                int at = 0;
                while (at < times.size() && times.get(at) >= t) at++;
                if (at >= RECENT_ITEMS) continue;
                sortedIds.add(at, m.optString(0));
                times.add(at, t);
                if (sortedIds.size() > RECENT_ITEMS) {
                    sortedIds.remove(RECENT_ITEMS);
                    times.remove(RECENT_ITEMS);
                }
            }
            for (String id : sortedIds) ids.put(id);
        } catch (NotSignedInException e) {
            throw e;
        } catch (Exception e) {
            // Unexpected meta format: fall back to the whole library below.
            ids = null;
        }
        if (ids != null && ids.length() == 0) return new JSONArray();
        JSONObject body = new JSONObject().put("collection", "libraryItem")
                .put("ids", ids != null ? ids : new JSONArray()).put("all", ids == null);
        Object r = call("datastoreGet", body);
        return r instanceof JSONArray ? (JSONArray) r : new JSONArray();
    }

    /** Stremio times are ISO-8601 strings ("2026-11-02T20:15:31.123Z") or epoch milliseconds. */
    private static long time(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (!(v instanceof String) || ((String) v).isEmpty()) return 0;
        String s = (String) v;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long t = f.parse(s).getTime();  // parses the leading part; fraction + "Z" follow
            int dot = s.indexOf('.', 19);
            if (dot == 19) {
                int end = dot + 1;
                while (end < s.length() && end < dot + 4 && Character.isDigit(s.charAt(end))) end++;
                String ms = (s.substring(dot + 1, end) + "000").substring(0, 3);
                t += Integer.parseInt(ms);
            }
            return t;
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ HTTP

    /** api.strem.io call with the session key. Returns the "result" value. */
    private Object call(String method, JSONObject body) throws Exception {
        String auth = prefs.getString(K_AUTH, "");
        if (auth.isEmpty()) throw new NotSignedInException("Stremio");
        JSONObject o = new JSONObject(post(API + method, body.put("authKey", auth)));
        JSONObject err = o.optJSONObject("error");
        if (err != null) {
            if (err.optInt("code") == SESSION_GONE) {
                // Signed out elsewhere (or the key was revoked): show the QR again.
                SharedPreferences.Editor e = prefs.edit().remove(K_AUTH);
                clearCache(e);
                e.apply();
                throw new NotSignedInException("Stremio");
            }
            throw new IllegalStateException("Stremio " + method + ": " + err.optString("message"));
        }
        return o.opt("result");
    }

    private static String post(String url, JSONObject body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(10_000);
            con.setReadTimeout(20_000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
            String text = in == null ? "" : read(in);
            if (code < 200 || code >= 300) throw new HttpException(code, text);
            return text;
        } finally {
            con.disconnect();
        }
    }
}
