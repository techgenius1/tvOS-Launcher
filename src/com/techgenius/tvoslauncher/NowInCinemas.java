package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Process;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data for the "Now in Cinemas" Home widget: films playing in cinemas in the widget's country and
 * their trailers. No keys or accounts; none of these sources is an official API, so every parser
 * fails soft (no films / no trailer) and the next source is tried.
 *
 * <ul>
 * <li>Listings: the AlloCin&eacute; network's "now showing" page for TR (Beyazperde), FR (AlloCin&eacute;),
 * ES (SensaCine) and BR (AdoroCinema), parsed from HTML; every other country (or when that page
 * gives nothing) uses JustWatch's public GraphQL titles with the {@code CINEMA} monetization type.
 * One listing request per refresh, at most once an hour; only non-empty lists are cached.</li>
 * <li>Trailers: AlloCin&eacute;-network films have the site's own MP4 trailer (film page &rarr; trailer
 * page &rarr; best-quality MP4, up to 1280 px wide). They are downloaded in the background, one at a
 * time, on Wi-Fi/Ethernet only, into {@code cache/cinema_trailers} (at most {@link #CACHE_LIMIT},
 * never when less than {@link #MIN_FREE} is free) and deleted once the film leaves the list, so a
 * trailer plays instantly from the TV while the film is in cinemas. Other films open their
 * YouTube trailer (JustWatch's clip id, else the first YouTube search result) in the YouTube app.</li>
 * </ul>
 */
final class NowInCinemas {

    /** One film in cinemas. */
    static final class Film {
        /** Stable id ("tr-1000051044", "jw-tm123"); also the trailer file name. */
        String id = "";
        String title = "", poster = "", dateText = "", dateIso = "", youtube = "", page = "", source = "";
        double rating;
        int ratingMax = 10;

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("t", title).put("p", poster).put("dt", dateText)
                    .put("di", dateIso).put("y", youtube).put("pg", page).put("s", source)
                    .put("r", rating).put("rm", ratingMax);
        }

        static Film fromJson(JSONObject o) {
            Film f = new Film();
            f.id = o.optString("id");
            f.title = o.optString("t");
            f.poster = o.optString("p");
            f.dateText = o.optString("dt");
            f.dateIso = o.optString("di");
            f.youtube = o.optString("y");
            f.page = o.optString("pg");
            f.source = o.optString("s");
            f.rating = o.optDouble("r", 0);
            f.ratingMax = o.optInt("rm", 10);
            return f;
        }

        /** Whether this film has (or may have) the site's own MP4 trailer. */
        boolean hasSiteTrailer() {
            return SRC_ALLOCINE.equals(source) && !page.isEmpty();
        }
    }

    /** What a fetch produced: the films, or whether every source failed. */
    static final class Result {
        final List<Film> films;
        final boolean failed;

        Result(List<Film> films, boolean failed) {
            this.films = films;
            this.failed = failed;
        }
    }

    static final String SRC_ALLOCINE = "allocine", SRC_JUSTWATCH = "justwatch";
    private static final String PREFS = "now_in_cinemas";
    private static final String K_JSON = "cache_json", K_SIG = "cache_sig", K_TS = "cache_ts";
    private static final String K_MP4 = "mp4_", K_YT = "yt_";
    static final long TTL_MS = 60 * 60_000L;
    static final long EMPTY_RETRY_MS = 15 * 60_000L;
    /** A film page without a trailer is asked again after this long. */
    private static final long NO_TRAILER_RETRY_MS = 24 * 60 * 60_000L;
    private static final int MAX_FILMS = 20;
    /** JustWatch keeps old re-runs in its cinema list; drop films released longer ago than this. */
    private static final long MAX_AGE_MS = 180L * 24 * 60 * 60_000L;
    static final long CACHE_LIMIT = 400L * 1024 * 1024;
    static final long MIN_FREE = 1024L * 1024 * 1024;
    private static final long SYNC_MIN_INTERVAL_MS = 10 * 60_000L;

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /** AlloCin&eacute;-network "now showing" pages: { country, site, list path, date locale, date pattern }. */
    private static final String[][] ALLOCINE_SITES = {
        {"TR", "https://www.beyazperde.com", "/filmler/vizyondakiler/sinema-sayisi/", "tr-TR", "d MMMM yyyy"},
        {"FR", "https://www.allocine.fr", "/film/aucinema/", "fr-FR", "d MMMM yyyy"},
        {"ES", "https://www.sensacine.com", "/peliculas/en-cartelera/cines/", "es-ES", "d 'de' MMMM 'de' yyyy"},
        {"BR", "https://www.adorocinema.com", "/filmes/numero-cinemas/", "pt-BR", "d 'de' MMMM 'de' yyyy"},
    };

    private static final Pattern P_TITLE = Pattern.compile("<a class=\"meta-title-link\" href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
    private static final Pattern P_POSTER = Pattern.compile("(?:data-src|src)=\"(https://[^\"]+/c_310_420/[^\"]+)\"");
    private static final Pattern P_DATE = Pattern.compile("<span class=\"date\">([^<]+)</span>");
    private static final Pattern P_RATING = Pattern.compile("stareval-note\">([0-9]+[.,]?[0-9]*)<");
    private static final Pattern P_ID = Pattern.compile("(\\d{3,})");
    private static final Pattern P_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern P_MP4 = Pattern.compile("(https://[^\"\\s\\\\]+?_(hd|high|medium|low)_\\d+\\.mp4[^\"\\s\\\\]*)");
    private static final Pattern P_VIDEO_ID = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"");

    static final String JW_CINEMA_QUERY = "query C($country: Country!, $language: Language!, $first: Int!, $filter: TitleFilter) {"
            + " popularTitles(country: $country, filter: $filter, first: $first, sortBy: POPULAR, sortRandomSeed: 0) {"
            + " edges { node { id content(country: $country, language: $language) {"
            + " title originalReleaseYear originalReleaseDate posterUrl scoring { imdbScore } clips { externalId provider } } } } } }";

    /** Trailer downloads: one low-priority thread, one file at a time. */
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "cinema-trailers");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final AtomicBoolean sSyncing = new AtomicBoolean(false);
    private static volatile long sLastSync = 0L;

    private NowInCinemas() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The widget follows the Most Watched country setting (first country when several are chosen). */
    static String country(Context c) {
        List<String> cc = HomeDashboard.getEffectiveReleasesCountries(c);
        return cc.isEmpty() ? HomeDashboard.defaultCountryForUiLang(c) : cc.get(0);
    }

    private static String shortLang(Context c) {
        String l = I18n.getLang(c);
        if (l == null) return "en";
        int dash = l.indexOf('-');
        if (dash > 0) l = l.substring(0, dash);
        return l.length() == 2 ? l : "en";
    }

    static String signature(Context c) {
        return I18n.getLang(c) + ":" + country(c);
    }

    // ------------------------------------------------------------------ listings

    /** Cached films for the current country/language, whatever their age. */
    static List<Film> cached(Context c) {
        SharedPreferences sp = prefs(c);
        if (!signature(c).equals(sp.getString(K_SIG, ""))) return Collections.emptyList();
        String raw = sp.getString(K_JSON, null);
        if (raw == null) return Collections.emptyList();
        try {
            JSONArray arr = new JSONArray(raw);
            List<Film> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(Film.fromJson(arr.getJSONObject(i)));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Whether the cached list is recent enough that no request is needed. */
    static boolean isFresh(Context c) {
        SharedPreferences sp = prefs(c);
        return signature(c).equals(sp.getString(K_SIG, ""))
                && System.currentTimeMillis() - sp.getLong(K_TS, 0L) < TTL_MS
                && !cached(c).isEmpty();
    }

    static void store(Context c, String sig, List<Film> films) {
        if (films == null || films.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            for (Film f : films) arr.put(f.toJson());
            prefs(c).edit().putString(K_JSON, arr.toString()).putString(K_SIG, sig)
                    .putLong(K_TS, System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Blocking fetch (call off the UI thread): the AlloCin&eacute;-network page when the country has one, else JustWatch. */
    static Result fetch(Context c) {
        String cc = country(c);
        boolean anyAnswered = false;
        String[] site = siteFor(cc);
        if (site != null) {
            try {
                List<Film> films = fetchAllocine(site);
                anyAnswered = true;
                if (!films.isEmpty()) return new Result(films, false);
            } catch (Exception ignored) {
            }
        }
        try {
            return new Result(fetchJustWatch(cc, shortLang(c)), false);
        } catch (Exception e) {
            return new Result(Collections.<Film>emptyList(), !anyAnswered);
        }
    }

    private static String[] siteFor(String cc) {
        for (String[] s : ALLOCINE_SITES) if (s[0].equalsIgnoreCase(cc)) return s;
        return null;
    }

    static List<Film> fetchAllocine(String[] site) throws Exception {
        String html = httpGet(site[1] + site[2], DESKTOP_UA);
        String[] cards = html.split("class=\"card entity-card");
        Locale dateLocale = Locale.forLanguageTag(site[3]);
        List<Film> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < cards.length && out.size() < MAX_FILMS; i++) {
            String card = cards[i];
            Matcher t = P_TITLE.matcher(card);
            if (!t.find()) continue;
            String href = t.group(1);
            Matcher idm = P_ID.matcher(href);
            if (!idm.find()) continue;
            Film f = new Film();
            f.source = SRC_ALLOCINE;
            f.id = site[0].toLowerCase(Locale.US) + "-" + idm.group(1);
            if (!seen.add(f.id)) continue;
            f.title = unescape(t.group(2)).trim();
            if (f.title.isEmpty()) continue;
            f.page = href.startsWith("http") ? href : site[1] + href;
            Matcher p = P_POSTER.matcher(card);
            if (p.find()) f.poster = p.group(1);
            Matcher d = P_DATE.matcher(card);
            if (d.find()) {
                f.dateText = unescape(d.group(1)).trim();
                f.dateIso = parseLocalDate(f.dateText, site[4], dateLocale);
            }
            Matcher r = P_RATING.matcher(card);
            if (r.find()) {
                try {
                    f.rating = Double.parseDouble(r.group(1).replace(',', '.'));
                    f.ratingMax = 5;
                } catch (NumberFormatException ignored) {
                }
            }
            out.add(f);
        }
        return out;
    }

    static List<Film> fetchJustWatch(String cc, String language) throws Exception {
        JSONObject filter = new JSONObject()
                .put("objectTypes", new JSONArray().put("MOVIE"))
                .put("monetizationTypes", new JSONArray().put("CINEMA"));
        JSONObject vars = new JSONObject().put("country", cc).put("language", language)
                .put("first", 40).put("filter", filter);
        JSONObject body = new JSONObject().put("query", JW_CINEMA_QUERY).put("variables", vars);
        JSONObject res = Suggestions.post("https://apis.justwatch.com/graphql", body.toString());
        JSONArray edges = res.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");
        long now = System.currentTimeMillis();
        int thisYear = Integer.parseInt(new SimpleDateFormat("yyyy", Locale.US).format(new Date(now)));
        List<Film> out = new ArrayList<>();
        for (int i = 0; i < edges.length() && out.size() < MAX_FILMS; i++) {
            JSONObject node = edges.getJSONObject(i).getJSONObject("node");
            JSONObject ct = node.optJSONObject("content");
            if (ct == null || ct.optString("title").isEmpty()) continue;
            String date = ct.optString("originalReleaseDate", "");
            if (date.equals("null")) date = "";
            long released = parseIso(date);
            if (released > 0) {
                if (now - released > MAX_AGE_MS) continue;  // an old film re-run, not a new release
            } else if (ct.optInt("originalReleaseYear", 0) < thisYear) {
                continue;
            }
            Film f = new Film();
            f.source = SRC_JUSTWATCH;
            f.id = "jw-" + node.optString("id").replaceAll("[^A-Za-z0-9]", "");
            f.title = ct.optString("title");
            f.dateIso = date;
            String poster = ct.optString("posterUrl", "");
            if (!poster.isEmpty() && !poster.equals("null")) {
                f.poster = "https://images.justwatch.com"
                        + poster.replace("{profile}", "s166").replace("{format}", "jpg");
            }
            JSONObject sc = ct.optJSONObject("scoring");
            f.rating = sc == null ? 0 : sc.optDouble("imdbScore", 0);
            JSONArray clips = ct.optJSONArray("clips");
            for (int k = 0; clips != null && k < clips.length(); k++) {
                JSONObject clip = clips.optJSONObject(k);
                if (clip != null && "YOUTUBE".equals(clip.optString("provider"))
                        && !clip.optString("externalId").isEmpty()) {
                    f.youtube = clip.optString("externalId");
                    break;
                }
            }
            out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ dates

    /** "2026-07-15" to epoch ms (UTC), 0 when missing or malformed. */
    static long parseIso(String ymd) {
        if (ymd == null || ymd.length() < 10) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(ymd.substring(0, 10));
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** "13 Kas\u0131m 2026" (site language) to "2026-11-13", or "" when it doesn't parse. */
    private static String parseLocalDate(String text, String pattern, Locale locale) {
        try {
            SimpleDateFormat in = new SimpleDateFormat(pattern, locale);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(text);
            if (d == null) return "";
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            return iso.format(d);
        } catch (Exception e) {
            return "";
        }
    }

    /** The release date as shown: the site's own text, else "15 Jul 2026" in the UI language. */
    static String displayDate(Context c, Film f) {
        if (!f.dateText.isEmpty()) return f.dateText;
        long t = parseIso(f.dateIso);
        if (t <= 0) return "";
        SimpleDateFormat out = new SimpleDateFormat("d MMM yyyy", I18n.locale(c));
        out.setTimeZone(TimeZone.getTimeZone("UTC"));
        return out.format(new Date(t));
    }

    // ------------------------------------------------------------------ trailers

    static File trailerDir(Context c) {
        return new File(c.getCacheDir(), "cinema_trailers");
    }

    /** The downloaded trailer, or null while it isn't complete. */
    static File localTrailer(Context c, Film f) {
        File file = new File(trailerDir(c), f.id + ".mp4");
        return file.isFile() && file.length() > 0 ? file : null;
    }

    /**
     * The site's best MP4 trailer URL (blocking: film page, then trailer page; remembered per film).
     * Returns "" when the film has none.
     */
    static String trailerMp4(Context c, Film f) {
        if (!f.hasSiteTrailer()) return "";
        SharedPreferences sp = prefs(c);
        String saved = sp.getString(K_MP4 + f.id, null);
        if (saved != null) {
            if (!saved.startsWith("none:")) return saved;
            try {
                if (System.currentTimeMillis() - Long.parseLong(saved.substring(5)) < NO_TRAILER_RETRY_MS) return "";
            } catch (NumberFormatException ignored) {
            }
        }
        String mp4 = "";
        boolean answered = false;
        try {
            String page = httpGet(f.page, DESKTOP_UA);
            answered = true;
            int at = page.indexOf("\"trailer\"");
            if (at >= 0) {
                Matcher u = P_URL.matcher(page.substring(at, Math.min(page.length(), at + 2500)));
                if (u.find()) {
                    String trailerPage = jsonUnescape(u.group(1));
                    if (trailerPage.startsWith("/")) {
                        Uri base = Uri.parse(f.page);
                        trailerPage = base.getScheme() + "://" + base.getHost() + trailerPage;
                    }
                    mp4 = bestMp4(jsonUnescape(httpGet(trailerPage, DESKTOP_UA)));
                }
            }
        } catch (Exception ignored) {
        }
        if (!mp4.isEmpty()) sp.edit().putString(K_MP4 + f.id, mp4).apply();
        else if (answered) sp.edit().putString(K_MP4 + f.id, "none:" + System.currentTimeMillis()).apply();
        return mp4;
    }

    /** Highest quality of the MP4s on a trailer page (hd > high > medium > low). */
    static String bestMp4(String page) {
        Map<String, Integer> rank = new HashMap<>();
        rank.put("hd", 4);
        rank.put("high", 3);
        rank.put("medium", 2);
        rank.put("low", 1);
        Matcher m = P_MP4.matcher(page);
        String best = "";
        int bestRank = 0;
        while (m.find()) {
            Integer r = rank.get(m.group(2));
            if (r != null && r > bestRank) {
                bestRank = r;
                best = m.group(1);
            }
        }
        return best;
    }

    /**
     * A YouTube video id for the film: JustWatch's clip, else the first result of a YouTube search
     * for "title trailer" ("fragman" in Turkish), remembered per film. "" when none is found.
     */
    static String youtubeId(Context c, Film f) {
        if (!f.youtube.isEmpty()) return f.youtube;
        SharedPreferences sp = prefs(c);
        String saved = sp.getString(K_YT + f.id, "");
        if (!saved.isEmpty()) return saved;
        try {
            String html = httpGet("https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(searchQuery(c, f), "UTF-8"), DESKTOP_UA);
            Matcher m = P_VIDEO_ID.matcher(html);
            if (m.find()) {
                sp.edit().putString(K_YT + f.id, m.group(1)).apply();
                return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String searchQuery(Context c, Film f) {
        return f.title + ("tr".equals(shortLang(c)) ? " fragman" : " trailer");
    }

    /** Opens the YouTube trailer in the YouTube app (or SmartTube), or a YouTube search without an id. */
    static boolean openYoutube(Context c, Film f, String videoId) {
        String url;
        if (videoId != null && !videoId.isEmpty()) {
            url = "https://www.youtube.com/watch?v=" + videoId;
        } else {
            try {
                url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(searchQuery(c, f), "UTF-8");
            } catch (Exception e) {
                return false;
            }
        }
        return Suggestions.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /** Plays a downloaded file or streams the MP4 in the in-app player. */
    static boolean playInApp(Context c, Film f, File local, String mp4) {
        Intent i = new Intent(c, TrailerActivity.class)
                .putExtra(TrailerActivity.EXTRA_TITLE, f.title)
                .putExtra(TrailerActivity.EXTRA_URL, local != null ? Uri.fromFile(local).toString() : mp4);
        return Suggestions.start(c, i);
    }

    // ------------------------------------------------------------------ trailer cache

    /**
     * Brings the trailer cache in line with the list, in the background: deletes trailers (and any
     * unfinished .part files) of films no longer in cinemas, then downloads the missing ones in list
     * order, one at a time, only on an unmetered (Wi-Fi/Ethernet) network, with at least
     * {@link #MIN_FREE} free and at most {@link #CACHE_LIMIT} in total (films released earliest are
     * dropped first to make room). Runs at most every 10 minutes.
     */
    static void syncTrailers(Context c, final List<Film> films, boolean force) {
        if (films == null || films.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (!force && now - sLastSync < SYNC_MIN_INTERVAL_MS) return;
        if (!sSyncing.compareAndSet(false, true)) return;
        sLastSync = now;
        final Context app = c.getApplicationContext();
        final List<Film> list = new ArrayList<>(films);
        DOWNLOADS.execute(() -> {
            try {
                syncNow(app, list);
            } catch (Throwable ignored) {
            } finally {
                sSyncing.set(false);
            }
        });
    }

    private static void syncNow(Context c, List<Film> films) {
        File dir = trailerDir(c);
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        Set<String> keep = new HashSet<>();
        for (Film f : films) if (f.hasSiteTrailer()) keep.add(f.id + ".mp4");
        File[] existing = dir.listFiles();
        for (int i = 0; existing != null && i < existing.length; i++) {
            File e = existing[i];
            if (e.getName().endsWith(".part") || !keep.contains(e.getName())) e.delete();
        }
        for (Film f : films) {
            if (!f.hasSiteTrailer() || localTrailer(c, f) != null) continue;
            if (!unmetered(c) || dir.getUsableSpace() < MIN_FREE) return;
            String mp4 = trailerMp4(c, f);
            if (mp4.isEmpty()) continue;
            if (!download(c, dir, films, f, mp4)) return;
        }
    }

    /** Whether the active network is Wi-Fi or Ethernet (ACCESS_NETWORK_STATE is already declared). */
    static boolean unmetered(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return false;
            int t = ni.getType();
            return t == ConnectivityManager.TYPE_WIFI || t == ConnectivityManager.TYPE_ETHERNET;
        } catch (Exception e) {
            return false;
        }
    }

    /** Downloads one trailer to .part and renames it when complete. Returns false to stop the run. */
    private static boolean download(Context c, File dir, List<Film> films, Film f, String mp4) {
        File part = new File(dir, f.id + ".part");
        File done = new File(dir, f.id + ".mp4");
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(mp4).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);
            con.setRequestProperty("User-Agent", DESKTOP_UA);
            if (con.getResponseCode() != 200) return true;
            long len = con.getContentLength();
            if (len <= 0 || !makeRoom(dir, films, f, len)) return true;  // skip this one
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > len) break;
                }
                if (total != len) {
                    part.delete();
                    return false;
                }
            }
            if (!part.renameTo(done)) part.delete();
            return true;
        } catch (Exception e) {
            part.delete();
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /**
     * Frees space for {@code need} bytes under {@link #CACHE_LIMIT} by deleting trailers of films
     * released earlier than {@code f}, earliest first. Returns false when that isn't enough.
     */
    private static boolean makeRoom(File dir, List<Film> films, Film f, long need) {
        if (need > CACHE_LIMIT) return false;
        long used = 0;
        File[] files = dir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) used += files[i].length();
        if (used + need <= CACHE_LIMIT) return true;
        List<Film> older = new ArrayList<>();
        long mine = parseIso(f.dateIso);
        for (Film o : films) {
            if (o == f || !new File(dir, o.id + ".mp4").isFile()) continue;
            if (mine <= 0 || parseIso(o.dateIso) < mine) older.add(o);
        }
        Collections.sort(older, (a, b) -> Long.compare(parseIso(a.dateIso), parseIso(b.dateIso)));
        for (Film o : older) {
            File victim = new File(dir, o.id + ".mp4");
            used -= victim.length();
            victim.delete();
            if (used + need <= CACHE_LIMIT) return true;
        }
        return used + need <= CACHE_LIMIT;
    }

    // ------------------------------------------------------------------ http

    private static String httpGet(String url, String ua) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", ua);
        con.setRequestProperty("Accept-Language", "en;q=0.5");
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            return new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    private static String unescape(String html) {
        return Html.fromHtml(html).toString();
    }

    /** Undoes JSON string escapes found in page source: "\/" and "\\u0026". */
    private static String jsonUnescape(String s) {
        return s.replace("\\/", "/").replace("\\u0026", "&");
    }
}
