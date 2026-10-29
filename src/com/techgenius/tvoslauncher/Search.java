package com.techgenius.tvoslauncher;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Search data: titles from JustWatch (every service in India, with each service's link) and
 * Stremio's Cinemeta catalogue, plus the generic matching of a streaming service to the app that
 * plays it. Nothing here touches views; network calls block and run off the UI thread.
 */
final class Search {

    static final String K_TITLES = "search_titles", K_RECENT = "search_recent", K_APP = "search_app_";
    static final String JW_IMG = "https://images.justwatch.com";
    static final int MAX_TITLES = 20, MAX_RECENT = 8;

    /** One way to watch a title: a service, how it is offered, and its links. */
    static final class Offer {
        String service = "", tech = "", monetization = "", web = "", deeplink = "", icon = "";
        /** The app that plays it ("" when none is installed) and the app to get otherwise. */
        String pkg = "", getPkg = "";

        boolean installed() {
            return !pkg.isEmpty();
        }

        /** Subscription first, then free, rent and buy (lower is better). */
        int rank() {
            switch (monetization) {
                case "FLATRATE": return 0;
                case "FREE": return 1;
                case "ADS": return 2;
                case "RENT": return 3;
                case "BUY": return 4;
                default: return 5;
            }
        }

        String label() {
            switch (monetization) {
                case "FLATRATE": return "Subscription";
                case "FREE": return "Free";
                case "ADS": return "Free with ads";
                case "RENT": return "Rent";
                case "BUY": return "Buy";
                case "STREMIO": return "Add-ons";
                default: return "";
            }
        }
    }

    static final class Title {
        String title = "", type = "movie", year = "", overview = "", genres = "", poster = "", backdrop = "", imdb = "";
        double rating;
        /** One offer per app, best first (installed apps, then subscription before rent/buy). */
        List<Offer> offers = new ArrayList<>();
    }

    static SharedPreferences prefs(Context c) {
        return Suggestions.prefs(c);
    }

    static boolean titlesEnabled(Context c) {
        return prefs(c).getBoolean(K_TITLES, true);
    }

    // ------------------------------------------------------------------ recent searches

    /** This profile's recent searches, newest first. */
    static List<String> recent(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Profiles.homePrefs(c).getString(K_RECENT, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void remember(Context c, String q) {
        q = q.trim();
        if (q.length() < 2) return;
        List<String> r = recent(c);
        for (int i = r.size() - 1; i >= 0; i--) if (r.get(i).equalsIgnoreCase(q)) r.remove(i);
        r.add(0, q);
        JSONArray a = new JSONArray();
        for (int i = 0; i < Math.min(MAX_RECENT, r.size()); i++) a.put(r.get(i));
        Profiles.homePrefs(c).edit().putString(K_RECENT, a.toString()).apply();
    }

    static void clearRecent(Context c) {
        Profiles.homePrefs(c).edit().remove(K_RECENT).apply();
    }

    // ------------------------------------------------------------------ text matching

    /** Lower case, letters and digits only (so "Sony LIV" == "sonyliv"). */
    static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(ch)) b.append(ch);
        return b.toString();
    }

    /**
     * How well a name matches what was typed: 0 starts with it, 1 a word starts with it,
     * 2 contains it, 3 a keyword starts with it; -1 no match.
     */
    static int score(String name, String keywords, String q) {
        String n = name.toLowerCase(Locale.ROOT), query = q.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return -1;
        if (n.startsWith(query) || norm(n).startsWith(norm(query))) return 0;
        for (String w : n.split("[^\\p{L}\\p{N}]+")) if (!w.isEmpty() && w.startsWith(query)) return 1;
        if (norm(n).contains(norm(query)) && norm(query).length() >= 2) return 2;
        if (keywords != null) for (String k : keywords.split(" ")) if (k.startsWith(query)) return 3;
        return -1;
    }

    // ------------------------------------------------------------------ titles

    static final String JW_SEARCH = "query S($q: String!, $first: Int!) { popularTitles(country: IN, first: $first,"
            + " filter: {searchQuery: $q}) { edges { node { objectType"
            + " content(country: IN, language: \"en\") { title originalReleaseYear shortDescription"
            + " posterUrl(profile: S332, format: JPG) backdrops(profile: S1440, format: JPG) { backdropUrl }"
            + " genres { translation(language: \"en\") } externalIds { imdbId } scoring { imdbScore } }"
            + " offers(country: IN, platform: WEB) { monetizationType standardWebURL deeplinkURL(platform: ANDROID_TV)"
            + " package { clearName technicalName icon(profile: S100, format: PNG) } } } } } }";

    /** Titles matching q on any service in India (JustWatch), best match first. */
    static List<Title> titles(Context c, String q, Apps apps) throws Exception {
        JSONObject vars = new JSONObject().put("q", q).put("first", MAX_TITLES);
        JSONObject res = post("https://apis.justwatch.com/graphql",
                new JSONObject().put("query", JW_SEARCH).put("variables", vars).toString());
        JSONArray edges = res.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject n = edges.getJSONObject(i).getJSONObject("node");
            JSONObject ct = n.optJSONObject("content");
            if (ct == null) continue;
            Title t = new Title();
            t.title = ct.optString("title");
            t.type = "SHOW".equals(n.optString("objectType")) ? "tv" : "movie";
            int y = ct.optInt("originalReleaseYear");
            t.year = y > 0 ? String.valueOf(y) : "";
            t.overview = clean(ct.optString("shortDescription"));
            String poster = clean(ct.optString("posterUrl"));
            if (!poster.isEmpty()) t.poster = JW_IMG + poster;
            JSONArray bd = ct.optJSONArray("backdrops");
            if (bd != null && bd.length() > 0) t.backdrop = JW_IMG + bd.getJSONObject(0).optString("backdropUrl");
            JSONArray g = ct.optJSONArray("genres");
            List<String> gn = new ArrayList<>();
            for (int j = 0; g != null && j < g.length() && gn.size() < 2; j++) gn.add(g.getJSONObject(j).optString("translation"));
            t.genres = String.join(", ", gn);
            JSONObject ids = ct.optJSONObject("externalIds");
            if (ids != null) t.imdb = clean(ids.optString("imdbId"));
            JSONObject sc = ct.optJSONObject("scoring");
            if (sc != null) t.rating = sc.optDouble("imdbScore", 0);
            if (Double.isNaN(t.rating)) t.rating = 0;
            JSONArray offers = n.optJSONArray("offers");
            List<Offer> raw = new ArrayList<>();
            for (int j = 0; offers != null && j < offers.length(); j++) {
                JSONObject o = offers.getJSONObject(j);
                JSONObject p = o.optJSONObject("package");
                if (p == null) continue;
                Offer of = new Offer();
                of.service = tidyService(p.optString("clearName"));
                of.tech = p.optString("technicalName");
                of.monetization = o.optString("monetizationType");
                of.web = clean(o.optString("standardWebURL"));
                of.deeplink = clean(o.optString("deeplinkURL"));
                String icon = clean(p.optString("icon"));
                of.icon = icon.isEmpty() ? "" : JW_IMG + icon;
                raw.add(of);
            }
            t.offers = group(c, raw, apps);
            if (!t.imdb.isEmpty()) addStremio(c, t);
            out.add(t);
        }
        return out;
    }

    /**
     * Stremio's catalogue search (Cinemeta), for titles JustWatch doesn't list. Only used when
     * Stremio or Nuvio is installed, since those are the only apps that can open them.
     */
    static List<Title> stremio(Context c, String q) throws Exception {
        List<Title> out = new ArrayList<>();
        String enc = URLEncoder.encode(q, "UTF-8").replace("+", "%20");
        for (String kind : new String[]{"movie", "series"}) {
            JSONObject res = get("https://v3-cinemeta.strem.io/catalog/" + kind + "/top/search=" + enc + ".json");
            JSONArray metas = res.optJSONArray("metas");
            for (int i = 0; metas != null && i < metas.length() && i < 10; i++) {
                JSONObject m = metas.getJSONObject(i);
                String id = m.optString("id");
                if (!id.startsWith("tt")) continue;
                Title t = new Title();
                t.title = m.optString("name");
                t.type = kind.equals("series") ? "tv" : "movie";
                String yr = m.optString("releaseInfo", m.optString("year"));
                t.year = yr.length() >= 4 ? yr.substring(0, 4) : "";
                t.overview = m.optString("description");
                t.poster = m.optString("poster");
                t.backdrop = m.optString("background");
                t.imdb = id;
                t.rating = m.optDouble("imdbRating", 0);
                if (Double.isNaN(t.rating)) t.rating = 0;
                JSONArray g = m.optJSONArray("genres");
                List<String> gn = new ArrayList<>();
                for (int j = 0; g != null && j < g.length() && gn.size() < 2; j++) gn.add(g.optString(j));
                t.genres = String.join(", ", gn);
                addStremio(c, t);
                out.add(t);
            }
        }
        return out;
    }

    static boolean hasStremioApp(Context c) {
        return Suggestions.installed(c, Suggestions.STREMIO.pkg) || Suggestions.installed(c, Suggestions.NUVIO.pkg);
    }

    /** "Open in Stremio" (or Nuvio) by IMDb id, after the services, when one of them is installed. */
    static void addStremio(Context c, Title t) {
        if (!hasStremioApp(c)) return;
        Suggestions.Provider p = Suggestions.stremioApp(c);
        Offer o = new Offer();
        o.service = p.name;
        o.tech = "stremio";
        o.monetization = "STREMIO";
        o.pkg = p.pkg;
        t.offers.add(o);
    }

    static String tidyService(String s) {
        return s.replace(" Free with Ads", "").replace(" with Ads", "").replace(" Amazon Channel", "").trim();
    }

    static String clean(String s) {
        return s == null || s.equals("null") ? "" : s;
    }

    // ------------------------------------------------------------------ services -> apps

    /** The installed launcher apps, by normalised label, and the browsers to ignore as handlers. */
    static final class Apps {
        final Map<String, String> byLabel = new LinkedHashMap<>();
        final Set<String> browsers = new HashSet<>();
    }

    static Apps apps(Context c, Map<String, MainActivity.AppEntry> all) {
        RESOLVED.clear();
        Apps a = new Apps();
        for (MainActivity.AppEntry e : all.values()) {
            if (e.pkg != null && e.launch != null && !e.key.startsWith("special:")) a.byLabel.put(norm(e.label), e.pkg);
        }
        // Whatever opens an ordinary website is a browser (or the TV's "no browser" stub), not the service's app.
        PackageManager pm = c.getPackageManager();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/")), 0)) {
            a.browsers.add(r.activityInfo.packageName);
        }
        return a;
    }

    /**
     * One offer per app, best first. Each offer's app is found generically, without a list of
     * services: the app named in JustWatch's Android TV link, an installed app that opens the
     * service's website, the app remembered from last time, or an installed app with the same name.
     */
    static List<Offer> group(Context c, List<Offer> raw, Apps apps) {
        Map<String, Offer> best = new LinkedHashMap<>();
        for (Offer o : raw) {
            resolve(c, o, apps);
            String key = !o.pkg.isEmpty() ? o.pkg : !o.getPkg.isEmpty() ? o.getPkg : norm(o.service);
            Offer cur = best.get(key);
            if (cur == null || o.rank() < cur.rank()) best.put(key, o);
        }
        List<Offer> out = new ArrayList<>(best.values());
        out.sort((x, y) -> x.installed() != y.installed() ? (x.installed() ? -1 : 1) : Integer.compare(x.rank(), y.rank()));
        return out;
    }

    /** Service -> "app|app to get", for the life of one search screen (apps may be installed in between). */
    static final Map<String, String> RESOLVED = java.util.Collections.synchronizedMap(new HashMap<>());

    static void resolve(Context c, Offer o, Apps apps) {
        String memo = RESOLVED.get(o.tech);
        if (memo != null) {
            String[] p = memo.split("\\|", -1);
            o.pkg = p[0];
            o.getPkg = p[1];
            return;
        }
        PackageManager pm = c.getPackageManager();
        String named = "";
        if (!o.deeplink.isEmpty()) {
            try {
                Intent i = Intent.parseUri(o.deeplink, Intent.URI_INTENT_SCHEME);
                if (i.getPackage() != null) named = i.getPackage();
                if (named.isEmpty()) {
                    ResolveInfo r = pm.resolveActivity(i, 0);
                    if (r != null && !apps.browsers.contains(r.activityInfo.packageName)) named = r.activityInfo.packageName;
                }
            } catch (Exception ignored) {
            }
        }
        if (!named.isEmpty() && Suggestions.installed(c, named)) o.pkg = named;
        if (o.pkg.isEmpty() && !o.web.isEmpty()) {
            ResolveInfo r = pm.resolveActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(o.web)), 0);
            if (r != null && !apps.browsers.contains(r.activityInfo.packageName)
                    && !"android".equals(r.activityInfo.packageName)) o.pkg = r.activityInfo.packageName;
        }
        if (o.pkg.isEmpty()) {
            String saved = prefs(c).getString(K_APP + o.tech, "");
            if (!saved.isEmpty() && Suggestions.installed(c, saved)) o.pkg = saved;
        }
        if (o.pkg.isEmpty()) o.pkg = byName(o.service, apps);
        if (o.pkg.isEmpty()) {
            // Known services from the recommendations list, by JustWatch's own technical name.
            for (Suggestions.Provider p : Suggestions.PROVIDERS) {
                if (p.jwTech.equals(o.tech) && Suggestions.installed(c, p.pkg)) o.pkg = p.pkg;
            }
        }
        if (!o.pkg.isEmpty()) prefs(c).edit().putString(K_APP + o.tech, o.pkg).apply();
        o.getPkg = o.pkg.isEmpty() ? named : o.pkg;
        if (o.getPkg.isEmpty()) {
            for (Suggestions.Provider p : Suggestions.PROVIDERS) if (p.jwTech.equals(o.tech)) o.getPkg = p.pkg;
        }
        RESOLVED.put(o.tech, o.pkg + "|" + o.getPkg);
    }

    /** An installed app whose name matches the service's ("Sony Liv" ~ "Sony LIV", "Zee5" ~ "ZEE5"). */
    static String byName(String service, Apps apps) {
        String s = norm(service);
        if (s.length() < 3) return "";
        String hit = apps.byLabel.get(s);
        if (hit != null) return hit;
        for (Map.Entry<String, String> e : apps.byLabel.entrySet()) {
            String l = e.getKey();
            if (l.length() >= 3 && (l.startsWith(s) || s.startsWith(l))) return e.getValue();
        }
        return "";
    }

    // ------------------------------------------------------------------ opening

    /**
     * Opens the title in the offer's app as directly as it allows: the title's page on the
     * service (in the app), JustWatch's Android TV link, the app's own search, or the app itself.
     */
    static void open(Context c, Title t, Offer o) {
        if (o.monetization.equals("STREMIO")) {
            Suggestions.Item it = new Suggestions.Item();
            it.type = t.type;
            it.link = t.imdb;
            it.providerId = Suggestions.STREMIO_ID;
            Suggestions.openStremio(c, it, Suggestions.stremioApp(c));
            return;
        }
        PackageManager pm = c.getPackageManager();
        List<Intent> tries = new ArrayList<>();
        if (!o.web.isEmpty()) tries.add(Suggestions.view(o.web, o.pkg));
        if (!o.deeplink.isEmpty()) {
            try {
                Intent i = Intent.parseUri(o.deeplink, Intent.URI_INTENT_SCHEME);
                i.setSelector(null);
                i.setComponent(null);
                i.setPackage(o.pkg);
                tries.add(i);
            } catch (Exception ignored) {
            }
        }
        tries.add(new Intent(Intent.ACTION_SEARCH).setPackage(o.pkg).putExtra(SearchManager.QUERY, t.title));
        for (Intent i : tries) {
            if (i.resolveActivity(pm) != null && Suggestions.start(c, i)) return;
        }
        Intent launch = pm.getLeanbackLaunchIntentForPackage(o.pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(o.pkg);
        if (launch != null) Suggestions.start(c, launch);
    }

    /** The service's app on the Play Store: its exact page when the app is known, else a search for it. */
    static void get(Context c, Offer o) {
        Uri u = !o.getPkg.isEmpty() ? Uri.parse("market://details?id=" + o.getPkg)
                : Uri.parse("market://search?q=" + Uri.encode(o.service) + "&c=apps");
        if (!Suggestions.start(c, new Intent(Intent.ACTION_VIEW, u))) {
            Suggestions.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q="
                    + Uri.encode(o.service) + "&c=apps")));
        }
    }

    // ------------------------------------------------------------------ http

    /** The connection in flight, so a newer search can cut it off at once. */
    static volatile HttpURLConnection current;

    static void cancel() {
        HttpURLConnection con = current;
        if (con != null) con.disconnect();
    }

    static JSONObject post(String url, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(4000);
        con.setReadTimeout(6000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        current = con;
        try {
            con.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            return new JSONObject(new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8));
        } finally {
            con.disconnect();
        }
    }

    static JSONObject get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(4000);
        con.setReadTimeout(6000);
        try {
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            return new JSONObject(new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8));
        } finally {
            con.disconnect();
        }
    }
}
