package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nuvio Cloud account access for the dock's "continue watching" popup.
 * Sign-in is the same QR / device flow the Nuvio TV app uses, so no email or password is ever
 * stored here — only the session tokens. Every method blocks: call them off the UI thread.
 *
 * Endpoints: https://nuvio.tv/docs (public API). The three QR-login calls are not in the public
 * docs (same ones the Nuvio TV app and the Home Assistant integration use) and could change.
 */
final class NuvioCloud extends WatchCloud {

    static final String BASE = "https://api.nuvio.tv";
    /** Nuvio's public client key (published in its API docs; ships inside every Nuvio app). */
    static final String KEY = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN";
    static final String PREFS = "nuvio_cloud";
    private static final String K_ACCESS = "access", K_REFRESH = "refresh", K_EXPIRES = "expires_at",
            K_PROFILE = "profile", K_META = "meta_cache", K_PROFILES = "profiles_cache";
    static final int MAX_PROFILES = MAX_CACHED_PROFILES;
    /** Nuvio marks >= 90% as watched (WatchProgress.COMPLETED_THRESHOLD). */
    static final double WATCHED_FRACTION = 0.90;

    NuvioCloud(Context c) {
        super(c, PREFS);
    }

    @Override String name() { return "Nuvio"; }

    @Override boolean ownsPackage(String pkg) { return isNuvioApp(ctx, pkg); }

    @Override String defaultPackage() {
        String pkg = installedApp(ctx);
        return pkg != null ? pkg : OFFICIAL_PKG;
    }

    @Override List<String> deepLinks(Item it) {
        return titleLinks(it.isSeries(), it.contentId);
    }

    // ------------------------------------------------------------------ Nuvio apps (official + linked forks)

    /** The official Nuvio TV app. Every com.nuvio.* package is always treated as Nuvio. */
    static final String OFFICIAL_PKG = "com.nuvio.tv";
    /** "settings" prefs: packages of Nuvio forks the user linked in Settings › Apps › Nuvio. */
    static final String PREF_LINKED = "nuvio_linked_apps";

    /** The official Nuvio namespace (built in, can't be unlinked). */
    static boolean isOfficial(String pkg) {
        return pkg != null && pkg.startsWith("com.nuvio.");
    }

    /** Forks that sign in to the same Nuvio account (e.g. Nuvio Native Legacy), chosen by the user. */
    static Set<String> linkedApps(Context c) {
        Set<String> s = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getStringSet(PREF_LINKED, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    static void setLinked(Context c, String pkg, boolean linked) {
        Set<String> s = linkedApps(c);
        if (linked) s.add(pkg); else s.remove(pkg);
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putStringSet(PREF_LINKED, s).apply();
    }

    /** Whether this package shows Nuvio's continue watching: the official app or a linked fork. */
    static boolean isNuvioApp(Context c, String pkg) {
        return isOfficial(pkg) || (pkg != null && linkedApps(c).contains(pkg));
    }

    /** The installed Nuvio app titles open in: the official one, else a linked fork; null when none is installed. */
    static String installedApp(Context c) {
        List<String> all = installedApps(c);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every installed Nuvio app: the official one first, then linked forks (A-Z by package). */
    static List<String> installedApps(Context c) {
        List<String> out = new ArrayList<>();
        if (Suggestions.installed(c, OFFICIAL_PKG)) out.add(OFFICIAL_PKG);
        List<String> linked = new ArrayList<>(linkedApps(c));
        Collections.sort(linked);
        for (String pkg : linked) if (!out.contains(pkg) && Suggestions.installed(c, pkg)) out.add(pkg);
        return out;
    }

    /**
     * Links that open a title in a Nuvio app, most exact first (the opener uses the first one the
     * app handles): Nuvio's own detail page, then the title's IMDb page, which forks like Nuvio
     * Native Legacy open instead of nuvio:// links.
     */
    static List<String> titleLinks(boolean series, String contentId) {
        List<String> out = new ArrayList<>();
        out.add("nuvio://detail/" + (series ? "series" : "movie") + "/" + Uri.encode(contentId, ":"));
        int colon = contentId.indexOf(':');
        String base = colon >= 0 ? contentId.substring(0, colon) : contentId;
        if (base.startsWith("tt")) out.add("https://www.imdb.com/title/" + base + "/");
        return out;
    }

    @Override String popupPrefKey() { return "nuvio_popup"; }

    @Override boolean isSignedIn() {
        return !prefs.getString(K_REFRESH, "").isEmpty();
    }

    /** Nuvio profile 1..6 whose progress is shown. Profile 1 is the main profile. */
    @Override int profile() {
        return prefs.getInt(K_PROFILE, 1);
    }

    @Override void setProfile(int index) {
        prefs.edit().putInt(K_PROFILE, Math.max(1, Math.min(MAX_PROFILES, index))).apply();
    }

    /** The account's profiles, sorted by index. Also cached for instant display next time. */
    @Override List<Profile> profiles() throws Exception {
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

    @Override List<Profile> loadProfilesCache() {
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

    @Override synchronized void signOut() {
        String access = prefs.getString(K_ACCESS, "");
        if (!access.isEmpty()) {
            try {
                // scope=local ends only this launcher's session. The default ("global") would
                // also sign the Nuvio apps out on every device.
                post("/auth/v1/logout?scope=local", null, access);
            } catch (Exception ignored) {
                // Signing out locally is what matters.
            }
        }
        SharedPreferences.Editor e = prefs.edit()
                .remove(K_ACCESS).remove(K_REFRESH).remove(K_EXPIRES).remove(K_PROFILES);
        clearCache(e);
        e.apply();
    }

    // ------------------------------------------------------------------ QR login

    @Override QrLogin startQrLogin(String deviceName) throws Exception {
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

    @Override synchronized boolean pollQrLogin(QrLogin l) throws Exception {
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
     * The same row the Nuvio app shows, with title + poster filled in (see {@link #enrich}).
     * Follows the app's rules (NuvioTV HomeViewModelContinueWatching):
     * - Per title only its most recent entry counts (ties, e.g. a season marked watched at once:
     *   the highest episode). An older half-watched episode never comes back once a later one
     *   was finished. Episodes marked watched (watched_items) count as finished entries too.
     * - That entry not finished (under 90%): a resume card.
     * - A show whose latest entry is finished: an "Up next" card for the episode after the
     *   furthest finished one, once it has aired. Finished movies and shows drop out.
     */
    @Override List<Item> continueWatching(int profile, int max) throws Exception {
        JSONObject body = new JSONObject().put("p_profile_id", profile).put("p_limit", 200);
        JSONArray rows = new JSONArray(rpc("sync_pull_watch_progress", body));

        Map<String, Item> latest = new LinkedHashMap<>();
        Map<String, Item> furthestFinished = new HashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i);
            Item it = new Item();
            it.contentId = o.optString("content_id", "");
            it.type = normalizeType(o.optString("content_type", "movie"));
            it.videoId = o.optString("video_id", it.contentId);
            it.season = o.isNull("season") ? -1 : o.optInt("season", -1);
            it.episode = o.isNull("episode") ? -1 : o.optInt("episode", -1);
            it.position = o.optLong("position");
            it.duration = o.optLong("duration");
            it.lastWatched = o.optLong("last_watched");
            consider(it, latest, furthestFinished);
        }
        // "Mark as watched" lands in watched_items. The official app also writes a 100% progress
        // row, but other Nuvio apps (e.g. Nuvio Native Legacy) may write only the watched item,
        // so each one counts as a finished entry at its watched_at time. Only for titles that have
        // progress, like the app: a show only ever marked watched doesn't get an Up next card.
        java.util.Set<String> withProgress = new java.util.HashSet<>(latest.keySet());
        for (Item it : watchedItems(profile)) {
            if (withProgress.contains(it.contentId)) consider(it, latest, furthestFinished);
        }

        List<Item> recent = new ArrayList<>(latest.values());
        recent.sort((a, b) -> Long.compare(b.lastWatched, a.lastWatched));
        List<Item> out = new ArrayList<>();
        int lookups = 0;
        for (Item it : recent) {
            if (out.size() >= max) break;
            if (!finished(it)) {
                if (it.position > 0) out.add(it);
                continue;
            }
            Item seed = furthestFinished.get(it.contentId);
            if (seed == null || !seed.baseId().startsWith("tt") || lookups >= MAX_NEXT_UP_LOOKUPS) continue;
            lookups++;
            Item next = nextUp(seed, it.lastWatched);
            if (next != null) out.add(next);
        }

        enrich(profile, out);
        saveCache(profile, out);
        return out;
    }

    /** Adds one progress or watched entry: tracks each title's latest entry and furthest finished episode. */
    private static void consider(Item it, Map<String, Item> latest, Map<String, Item> furthestFinished) {
        if (it.contentId.isEmpty()) return;
        Item prev = latest.get(it.contentId);
        if (prev == null || it.lastWatched > prev.lastWatched
                || (it.lastWatched == prev.lastWatched && episodeOrder(it, prev) > 0)) {
            latest.put(it.contentId, it);
        }
        if (it.isSeries() && it.season > 0 && it.episode >= 0 && finished(it)) {
            Item f = furthestFinished.get(it.contentId);
            if (f == null || episodeOrder(it, f) > 0) furthestFinished.put(it.contentId, it);
        }
    }

    private static String normalizeType(String type) {
        return "tv".equalsIgnoreCase(type) ? "series" : type.toLowerCase(java.util.Locale.ROOT);
    }

    /** Watched-items pages read per refresh (900 each, like the Nuvio app's sync). */
    private static final int WATCHED_PAGE_SIZE = 900, MAX_WATCHED_PAGES = 3;

    /**
     * The profile's watched items (marked watched or finished), as finished entries: full
     * progress, last watched = watched_at. Empty when they can't be loaded (the row then
     * falls back to progress alone).
     */
    private List<Item> watchedItems(int profile) throws Exception {
        List<Item> out = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_WATCHED_PAGES; page++) {
                JSONObject body = new JSONObject().put("p_profile_id", profile)
                        .put("p_page", page).put("p_page_size", WATCHED_PAGE_SIZE);
                JSONArray rows = new JSONArray(rpc("sync_pull_watched_items", body));
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject o = rows.getJSONObject(i);
                    Item it = new Item();
                    it.contentId = o.optString("content_id", "");
                    it.type = normalizeType(o.optString("content_type", "movie"));
                    it.season = o.isNull("season") ? -1 : o.optInt("season", -1);
                    it.episode = o.isNull("episode") ? -1 : o.optInt("episode", -1);
                    it.videoId = it.isSeries() && it.season >= 0 && it.episode >= 0
                            ? it.contentId + ":" + it.season + ":" + it.episode : it.contentId;
                    it.position = 1;
                    it.duration = 1;
                    it.lastWatched = o.optLong("watched_at");
                    out.add(it);
                }
                if (rows.length() < WATCHED_PAGE_SIZE) break;
            }
        } catch (NotSignedInException e) {
            throw e;
        } catch (Exception e) {
            android.util.Log.w("NuvioCloud", "Couldn't load watched items", e);
        }
        return out;
    }

    /** Next-up lookups per refresh: each one asks Cinemeta for the show's episode list. */
    private static final int MAX_NEXT_UP_LOOKUPS = 8;

    private static boolean finished(Item it) {
        return it.duration > 0 && it.fraction() >= WATCHED_FRACTION;
    }

    private static int episodeOrder(Item a, Item b) {
        if (a.season != b.season) return Integer.compare(a.season, b.season);
        return Integer.compare(a.episode, b.episode);
    }

    /** Positive "next episode" lookups ("tt123:2:5" -> next item); an aired episode stays aired. */
    private final Map<String, Item> nextUpCache = new HashMap<>();

    /**
     * The episode after {@code seed} (season 0 specials skipped), as an "Up next" card, or null
     * when the show has no further episode that has aired yet.
     */
    private Item nextUp(Item seed, long lastWatched) {
        String id = seed.baseId();
        String key = id + ":" + seed.season + ":" + seed.episode;
        Item cached;
        synchronized (nextUpCache) {
            cached = nextUpCache.get(key);
        }
        if (cached == null) {
            try {
                cached = fetchNextEpisode(id, seed.season, seed.episode);
            } catch (Exception e) {
                return null;  // offline / Cinemeta down: no card this time
            }
            if (cached == null) return null;
            synchronized (nextUpCache) {
                nextUpCache.put(key, cached);
            }
        }
        Item it = new Item();
        it.contentId = id;
        it.type = "series";
        it.videoId = cached.videoId;
        it.season = cached.season;
        it.episode = cached.episode;
        it.title = cached.title;
        it.poster = cached.poster;
        it.upNext = true;
        it.lastWatched = lastWatched;
        return it;
    }

    private static Item fetchNextEpisode(String imdbId, int season, int episode) throws Exception {
        String url = "https://v3-cinemeta.strem.io/meta/series/" + imdbId + ".json";
        JSONObject meta = new JSONObject(get(url)).optJSONObject("meta");
        if (meta == null) return null;
        JSONArray videos = meta.optJSONArray("videos");
        if (videos == null) return null;
        // Release dates are ISO strings; comparing the "yyyy-MM-dd" part is enough.
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
        day.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String today = day.format(new java.util.Date());

        JSONObject best = null;
        int bestS = 0, bestE = 0;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v == null) continue;
            int s = v.optInt("season", -1);
            int e = v.has("episode") ? v.optInt("episode", -1) : v.optInt("number", -1);
            if (s <= 0 || e < 0) continue;
            boolean after = s > season || (s == season && e > episode);
            if (!after) continue;
            String released = v.optString("released", "");
            if (released.length() < 10 || released.substring(0, 10).compareTo(today) > 0) continue;
            if (best == null || s < bestS || (s == bestS && e < bestE)) {
                best = v;
                bestS = s;
                bestE = e;
            }
        }
        if (best == null) return null;
        Item next = new Item();
        next.videoId = best.optString("id", imdbId + ":" + bestS + ":" + bestE);
        next.season = bestS;
        next.episode = bestE;
        next.title = meta.optString("name", "");
        next.poster = meta.optString("poster", "");
        return next;
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
        if (!isSignedIn()) throw new NotSignedInException("Nuvio");
        String access = prefs.getString(K_ACCESS, "");
        if (access.isEmpty() || System.currentTimeMillis() > prefs.getLong(K_EXPIRES, 0) - 60_000L) {
            return refresh();
        }
        return access;
    }

    private synchronized String refresh() throws Exception {
        String rt = prefs.getString(K_REFRESH, "");
        if (rt.isEmpty()) throw new NotSignedInException("Nuvio");
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
                throw new NotSignedInException("Nuvio");
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
}
