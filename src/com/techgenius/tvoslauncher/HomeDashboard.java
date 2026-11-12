package com.techgenius.tvoslauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default Home Dashboard displayed when no application is selected, when focus is on
 * non-app elements (Settings, More, top bar), or when the focused app has no Continue Watching items.
 *
 * Displays 4 customizable Liquid Glass widgets in one horizontal row:
 *  1. Prayer Times: Daily prayer schedule and live countdown to the next prayer.
 *  2. Football Matches: Upcoming matches, live scores, and favorite teams.
 *  3. Latest Releases: Recently released movies and TV series from streaming platforms.
 *  4. Weather: Current conditions and forecast.
 */
public final class HomeDashboard {

    private static final String PREF = "home_dashboard_prefs";
    private static final String KEY_ENABLED = "dashboard_enabled";
    private static final String KEY_SHOW_WHEN_NO_CW = "show_when_no_cw";
    private static final String KEY_W_PRAYER = "widget_prayer";
    private static final String KEY_W_FOOTBALL = "widget_football";
    private static final String KEY_W_RELEASES = "widget_releases";
    private static final String KEY_W_WEATHER = "widget_weather";

    private static final String KEY_PRAYER_METHOD = "prayer_method"; // Default 13 (Diyanet / Turkey & region)
    private static final String KEY_PRAYER_CACHE_JSON = "prayer_cache_json";
    private static final String KEY_PRAYER_CACHE_DAY = "prayer_cache_day";
    private static final String KEY_PRAYER_CACHE_CITY = "prayer_cache_city";

    private static final String KEY_FAV_TEAMS = "football_fav_teams";
    private static final String KEY_FOOTBALL_LEAGUE = "football_league";
    private static final String KEY_FOOTBALL_CACHE_JSON = "football_cache_json";
    private static final String KEY_FOOTBALL_CACHE_TS = "football_cache_ts";

    private static final String KEY_RELEASES_AUTO = "releases_countries_auto";
    private static final String KEY_RELEASES_SET = "releases_countries_set";
    private static final String KEY_RELEASES_CACHE_JSON = "releases_cache_json";
    private static final String KEY_RELEASES_CACHE_SIG = "releases_cache_sig";
    private static final String KEY_RELEASES_CACHE_TS = "releases_cache_ts";

    /**
     * Supported countries for Latest Releases widget: { ISO-3166 alpha-2 code, Display Name, UI language code }.
     */
    public static final String[][] RELEASES_COUNTRIES = {
        {"TR", "Türkiye (Turkey)", "tr"},
        {"AZ", "Azərbaycan (Azerbaijan)", "az"},
        {"US", "United States", "en"},
        {"GB", "United Kingdom", "en"},
        {"DE", "Deutschland (Germany)", "de"},
        {"FR", "France", "fr"},
        {"ES", "España (Spain)", "es"},
        {"IT", "Italia (Italy)", "it"},
        {"PT", "Portugal", "pt"},
        {"BR", "Brasil (Brazil)", "pt"},
        {"RU", "Россия (Russia)", "ru"},
        {"SA", "السعودية (Saudi Arabia)", "ar"},
        {"EG", "مصر (Egypt)", "ar"},
        {"IN", "India", "en"},
        {"KR", "South Korea", "en"},
        {"JP", "Japan", "en"}
    };

    private static final ExecutorService EXEC = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HomeDashboard() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_ENABLED, en).apply();
    }

    public static boolean isShowWhenNoCw(Context c) {
        return prefs(c).getBoolean(KEY_SHOW_WHEN_NO_CW, false);
    }

    public static void setShowWhenNoCw(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_SHOW_WHEN_NO_CW, en).apply();
    }

    public static boolean isWidgetPrayerEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_PRAYER, true);
    }

    public static void setWidgetPrayerEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_PRAYER, en).apply();
    }

    public static boolean isWidgetFootballEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_FOOTBALL, true);
    }

    public static void setWidgetFootballEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_FOOTBALL, en).apply();
    }

    public static boolean isWidgetReleasesEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_RELEASES, true);
    }

    public static void setWidgetReleasesEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_RELEASES, en).apply();
    }

    public static boolean isWidgetWeatherEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_WEATHER, true);
    }

    public static void setWidgetWeatherEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_WEATHER, en).apply();
    }

    public static boolean hasAnyWidgetEnabled(Context c) {
        return isEnabled(c) && (isWidgetPrayerEnabled(c)
                || isWidgetFootballEnabled(c)
                || isWidgetReleasesEnabled(c)
                || isWidgetWeatherEnabled(c));
    }

    public static boolean isAnyEnabled(Context c) {
        return hasAnyWidgetEnabled(c);
    }

    // =========================================================================
    // 0. LATEST RELEASES COUNTRY SETTINGS (AUTO UI-LANG SYNC + MULTI-COUNTRY)
    // =========================================================================

    public static String defaultCountryForUiLang(Context c) {
        String lang = I18n.getLang(c);
        if ("tr".equals(lang)) return "TR";
        if ("az".equals(lang)) return "AZ";
        if ("de".equals(lang)) return "DE";
        if ("fr".equals(lang)) return "FR";
        if ("es".equals(lang)) return "ES";
        if ("it".equals(lang)) return "IT";
        if ("pt".equals(lang)) return "PT";
        if ("ru".equals(lang)) return "RU";
        if ("ar".equals(lang)) return "SA";
        return "US";
    }

    public static String getCountryDisplayName(String code) {
        if (code == null) return "";
        for (String[] row : RELEASES_COUNTRIES) {
            if (row[0].equalsIgnoreCase(code)) return row[1];
        }
        return code.toUpperCase(Locale.US);
    }

    public static boolean isReleasesCountryAuto(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.getBoolean(KEY_RELEASES_AUTO, true)) {
            Set<String> custom = sp.getStringSet(KEY_RELEASES_SET, null);
            return custom == null || custom.isEmpty();
        }
        return true;
    }

    public static void setReleasesCountryAuto(Context c, boolean auto) {
        SharedPreferences.Editor ed = prefs(c).edit().putBoolean(KEY_RELEASES_AUTO, auto);
        if (auto) {
            ed.remove(KEY_RELEASES_SET);
        }
        ed.remove(KEY_RELEASES_CACHE_JSON)
          .remove(KEY_RELEASES_CACHE_SIG)
          .remove(KEY_RELEASES_CACHE_TS)
          .apply();
    }

    public static List<String> getEffectiveReleasesCountries(Context c) {
        if (isReleasesCountryAuto(c)) {
            List<String> single = new ArrayList<String>(1);
            single.add(defaultCountryForUiLang(c));
            return single;
        }
        Set<String> raw = prefs(c).getStringSet(KEY_RELEASES_SET, Collections.<String>emptySet());
        List<String> ordered = new ArrayList<String>();
        for (String[] row : RELEASES_COUNTRIES) {
            if (raw != null && raw.contains(row[0])) {
                ordered.add(row[0]);
            }
        }
        if (ordered.isEmpty()) {
            ordered.add(defaultCountryForUiLang(c));
        }
        return ordered;
    }

    public static boolean isReleasesCountrySelected(Context c, String code) {
        if (code == null) return false;
        if (isReleasesCountryAuto(c)) {
            return code.equalsIgnoreCase(defaultCountryForUiLang(c));
        }
        Set<String> raw = prefs(c).getStringSet(KEY_RELEASES_SET, Collections.<String>emptySet());
        return raw != null && raw.contains(code.toUpperCase(Locale.US));
    }

    public static void toggleReleasesCountry(Context c, String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) return;
        String cc = countryCode.trim().toUpperCase(Locale.US);
        SharedPreferences sp = prefs(c);
        Set<String> next = new HashSet<String>();
        if (isReleasesCountryAuto(c)) {
            String autoCode = defaultCountryForUiLang(c);
            next.add(autoCode);
            if (cc.equalsIgnoreCase(autoCode)) {
                // Already the active auto country; switch to explicit custom selection of this country
            } else {
                next.add(cc);
            }
        } else {
            Set<String> cur = sp.getStringSet(KEY_RELEASES_SET, Collections.<String>emptySet());
            if (cur != null) next.addAll(cur);
            if (next.contains(cc)) {
                next.remove(cc);
            } else {
                next.add(cc);
            }
        }
        if (next.isEmpty()) {
            setReleasesCountryAuto(c, true);
        } else {
            sp.edit()
                    .putBoolean(KEY_RELEASES_AUTO, false)
                    .putStringSet(KEY_RELEASES_SET, next)
                    .remove(KEY_RELEASES_CACHE_JSON)
                    .remove(KEY_RELEASES_CACHE_SIG)
                    .remove(KEY_RELEASES_CACHE_TS)
                    .apply();
        }
    }

    public static String getReleasesCountriesSummary(Context c) {
        if (isReleasesCountryAuto(c)) {
            String cc = defaultCountryForUiLang(c);
            return I18n.t(c, "releases_country_auto") + " (" + cc + ")";
        }
        List<String> codes = getEffectiveReleasesCountries(c);
        if (codes.size() == 1) {
            return getCountryDisplayName(codes.get(0));
        }
        return TextUtils.join(", ", codes);
    }

    private static String getReleasesCacheSignature(Context c) {
        return I18n.getLang(c) + ":" + TextUtils.join(",", getEffectiveReleasesCountries(c));
    }

    public static List<Suggestions.Item> getCachedReleases(Context c) {
        SharedPreferences sp = prefs(c);
        String sig = getReleasesCacheSignature(c);
        String savedSig = sp.getString(KEY_RELEASES_CACHE_SIG, "");
        String raw = sp.getString(KEY_RELEASES_CACHE_JSON, null);
        if (raw != null && sig.equals(savedSig)) {
            try {
                JSONArray arr = new JSONArray(raw);
                List<Suggestions.Item> out = new ArrayList<Suggestions.Item>();
                for (int i = 0; i < arr.length(); i++) {
                    out.add(Suggestions.Item.fromJson(arr.getJSONObject(i)));
                }
                if (!out.isEmpty()) return out;
            } catch (Throwable ignored) {}
        }
        return Collections.emptyList();
    }

    public interface ReleasesCallback {
        void onResult(List<Suggestions.Item> items);
    }

    public static void fetchLatestReleases(final Context c, final boolean force, final ReleasesCallback cb) {
        final SharedPreferences sp = prefs(c);
        final String sig = getReleasesCacheSignature(c);
        long now = System.currentTimeMillis();
        long lastTs = sp.getLong(KEY_RELEASES_CACHE_TS, 0L);
        if (!force && sig.equals(sp.getString(KEY_RELEASES_CACHE_SIG, "")) && (now - lastTs) < 60 * 60_000L) {
            List<Suggestions.Item> cached = getCachedReleases(c);
            if (!cached.isEmpty()) {
                if (cb != null) cb.onResult(cached);
                return;
            }
        }
        final List<String> countries = getEffectiveReleasesCountries(c);
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Suggestions.Item> fetched = Suggestions.fetchReleasesForCountries(c, countries);
                    if (fetched != null && !fetched.isEmpty()) {
                        JSONArray arr = new JSONArray();
                        for (Suggestions.Item it : fetched) {
                            arr.put(it.toJson());
                        }
                        sp.edit()
                                .putString(KEY_RELEASES_CACHE_JSON, arr.toString())
                                .putString(KEY_RELEASES_CACHE_SIG, sig)
                                .putLong(KEY_RELEASES_CACHE_TS, System.currentTimeMillis())
                                .apply();
                        if (cb != null) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    cb.onResult(fetched);
                                }
                            });
                        }
                        return;
                    }
                } catch (Throwable ignored) {}
                final List<Suggestions.Item> fallback = getCachedReleases(c);
                if (cb != null) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(fallback);
                        }
                    });
                }
            }
        });
    }

    // =========================================================================
    // 1. PRAYER TIMES MODEL & FETCHER
    // =========================================================================

    public static final class PrayerData {
        public String city = "";
        public String fajr = "05:15";
        public String sunrise = "06:42";
        public String dhuhr = "12:55";
        public String asr = "16:20";
        public String maghrib = "19:05";
        public String isha = "20:30";
        public String dateStr = "";
    }

    public static final int[] PRAYER_METHOD_IDS = { 13, 3, 2, 4, 5, 0, 7 };
    public static final String[] PRAYER_METHOD_NAMES = {
        "Diyanet / Qafqaz (Regional)",
        "Muslim World League (MWL)",
        "ISNA (North America)",
        "Umm Al-Qura (Makkah)",
        "Egyptian General Authority",
        "Shia Ithna-Ashari (Jafari)",
        "Institute of Geophysics (Tehran)"
    };

    public static int getPrayerMethod(Context c) {
        return prefs(c).getInt(KEY_PRAYER_METHOD, 13);
    }

    public static void setPrayerMethod(Context c, int methodId) {
        prefs(c).edit()
                .putInt(KEY_PRAYER_METHOD, methodId)
                .remove(KEY_PRAYER_CACHE_DAY)
                .apply();
    }

    public static String getPrayerMethodName(Context c) {
        int m = getPrayerMethod(c);
        for (int i = 0; i < PRAYER_METHOD_IDS.length; i++) {
            if (PRAYER_METHOD_IDS[i] == m) return PRAYER_METHOD_NAMES[i];
        }
        return PRAYER_METHOD_NAMES[0];
    }

    public interface PrayerCallback {
        void onResult(PrayerData data);
    }

    public static PrayerData getCachedOrFallbackPrayer(Context c) {
        SharedPreferences sp = prefs(c);
        String json = sp.getString(KEY_PRAYER_CACHE_JSON, null);
        String city = sp.getString(KEY_PRAYER_CACHE_CITY, "");
        if (json != null) {
            PrayerData parsed = parsePrayerJson(json, city);
            if (parsed != null) return parsed;
        }
        Weather.Data w = Weather.cached(c);
        PrayerData fb = new PrayerData();
        fb.city = (w != null && w.place != null && !w.place.isEmpty()) ? w.place : "";
        return fb;
    }

    public static void fetchPrayerTimes(final Context c, final boolean force, final PrayerCallback cb) {
        final SharedPreferences sp = prefs(c);
        final int todayKey = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                + Calendar.getInstance().get(Calendar.YEAR) * 1000;
        Weather.Data w = Weather.cached(c);
        final String curCity = (w != null && w.place != null) ? w.place : "";
        if (!force && sp.getInt(KEY_PRAYER_CACHE_DAY, -1) == todayKey) {
            String cachedJson = sp.getString(KEY_PRAYER_CACHE_JSON, null);
            String cachedCity = sp.getString(KEY_PRAYER_CACHE_CITY, "");
            if (cachedJson != null && (curCity.isEmpty() || curCity.equalsIgnoreCase(cachedCity))) {
                PrayerData d = parsePrayerJson(cachedJson, cachedCity);
                if (d != null) {
                    if (cb != null) cb.onResult(d);
                    return;
                }
            }
        }

        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences wp = c.getSharedPreferences("weather_store", Context.MODE_PRIVATE);
                    float lat = wp.getFloat("lat", Float.NaN);
                    float lon = wp.getFloat("lon", Float.NaN);
                    String city = wp.getString("city", "");
                    if (Float.isNaN(lat) || Float.isNaN(lon)) {
                        try {
                            String geoRaw = httpGet("https://ipwho.is/");
                            JSONObject g = new JSONObject(geoRaw);
                            if (g.optBoolean("success", true)) {
                                lat = (float) g.optDouble("latitude", 40.4093);
                                lon = (float) g.optDouble("longitude", 49.8671);
                                city = g.optString("city", "Baku");
                            }
                        } catch (Throwable ignored) {
                            lat = 40.4093f;
                            lon = 49.8671f;
                            city = "Baku";
                        }
                    }
                    int method = getPrayerMethod(c);
                    long ts = System.currentTimeMillis() / 1000L;
                    String url = String.format(Locale.US,
                            "https://api.aladhan.com/v1/timings/%d?latitude=%.4f&longitude=%.4f&method=%d",
                            ts, lat, lon, method);
                    String resp = httpGet(url);
                    final PrayerData parsed = parsePrayerJson(resp, city);
                    if (parsed != null) {
                        sp.edit()
                                .putString(KEY_PRAYER_CACHE_JSON, resp)
                                .putInt(KEY_PRAYER_CACHE_DAY, todayKey)
                                .putString(KEY_PRAYER_CACHE_CITY, city)
                                .apply();
                        if (cb != null) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    cb.onResult(parsed);
                                }
                            });
                        }
                        return;
                    }
                } catch (Throwable ignored) {}
                final PrayerData fallback = getCachedOrFallbackPrayer(c);
                if (cb != null) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(fallback);
                        }
                    });
                }
            }
        });
    }

    private static PrayerData parsePrayerJson(String raw, String city) {
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONObject timings = data.optJSONObject("timings");
            if (timings == null) return null;
            PrayerData d = new PrayerData();
            d.city = city != null ? city : "";
            d.fajr = cleanTime(timings.optString("Fajr", "05:15"));
            d.sunrise = cleanTime(timings.optString("Sunrise", "06:42"));
            d.dhuhr = cleanTime(timings.optString("Dhuhr", "12:55"));
            d.asr = cleanTime(timings.optString("Asr", "16:20"));
            d.maghrib = cleanTime(timings.optString("Maghrib", "19:05"));
            d.isha = cleanTime(timings.optString("Isha", "20:30"));
            JSONObject dateObj = data.optJSONObject("date");
            if (dateObj != null) {
                d.dateStr = dateObj.optString("readable", "");
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String cleanTime(String t) {
        if (t == null) return "00:00";
        int idx = t.indexOf(' ');
        if (idx > 0) t = t.substring(0, idx);
        return t.trim();
    }

    private static int parseMinutes(String hhmm) {
        try {
            String[] parts = cleanTime(hhmm).split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Returns [nextPrayerIndex (0..4 for Fajr,Dhuhr,Asr,Maghrib,Isha), minutesUntilNext].
     */
    public static int[] computeNextPrayer(PrayerData d) {
        Calendar now = Calendar.getInstance();
        int curMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String[] times = { d.fajr, d.dhuhr, d.asr, d.maghrib, d.isha };
        for (int i = 0; i < times.length; i++) {
            int pm = parseMinutes(times[i]);
            if (pm > curMin) {
                return new int[]{ i, pm - curMin };
            }
        }
        int fajrMin = parseMinutes(d.fajr);
        int untilTomorrowFajr = (24 * 60 - curMin) + fajrMin;
        return new int[]{ 0, untilTomorrowFajr };
    }

    public static String[] getPrayerLabels(Context c) {
        return new String[]{
            I18n.t(c, "prayer_fajr"),
            I18n.t(c, "prayer_dhuhr"),
            I18n.t(c, "prayer_asr"),
            I18n.t(c, "prayer_maghrib"),
            I18n.t(c, "prayer_isha")
        };
    }

    // =========================================================================
    // 2. FOOTBALL MATCHES MODEL & FETCHER
    // =========================================================================

    public static final class MatchItem {
        public String homeTeam = "";
        public String awayTeam = "";
        public String homeScore = "";
        public String awayScore = "";
        public String statusText = "";
        public boolean isLive = false;
        public boolean isFinished = false;
        public boolean isFav = false;
        public String leagueName = "";
        public long startEpochMs = 0L;
    }

    public static final String[] POPULAR_TEAMS = {
        "Qarabağ", "Neftçi", "Sabah", "Zirə",
        "Galatasaray", "Fenerbahçe", "Beşiktaş", "Trabzonspor",
        "Real Madrid", "Barcelona", "Atlético Madrid",
        "Manchester City", "Arsenal", "Liverpool", "Chelsea", "Manchester United",
        "Bayern Munich", "Borussia Dortmund", "Bayer Leverkusen",
        "Inter Milan", "AC Milan", "Juventus", "Napoli",
        "PSG", "Inter Miami"
    };

    public static final String[][] LEAGUES = {
        { "all", "All Top Leagues" },
        { "uefa.champions", "UEFA Champions League" },
        { "uefa.europa", "UEFA Europa League" },
        { "eng.1", "Premier League" },
        { "esp.1", "LaLiga" },
        { "ita.1", "Serie A" },
        { "ger.1", "Bundesliga" },
        { "tur.1", "Trendyol Süper Lig" },
        { "fra.1", "Ligue 1" }
    };

    public static Set<String> getFavoriteTeams(Context c) {
        Set<String> raw = prefs(c).getStringSet(KEY_FAV_TEAMS, null);
        if (raw == null) {
            Set<String> def = new HashSet<String>();
            def.add("Qarabağ");
            def.add("Galatasaray");
            def.add("Real Madrid");
            return def;
        }
        return new HashSet<String>(raw);
    }

    public static void toggleFavoriteTeam(Context c, String team) {
        Set<String> favs = getFavoriteTeams(c);
        if (favs.contains(team)) {
            favs.remove(team);
        } else {
            favs.add(team);
        }
        prefs(c).edit().putStringSet(KEY_FAV_TEAMS, favs).apply();
    }

    public static void addCustomFavoriteTeam(Context c, String team) {
        if (team == null || team.trim().isEmpty()) return;
        Set<String> favs = getFavoriteTeams(c);
        favs.add(team.trim());
        prefs(c).edit().putStringSet(KEY_FAV_TEAMS, favs).apply();
    }

    public static String getSelectedLeague(Context c) {
        return prefs(c).getString(KEY_FOOTBALL_LEAGUE, "all");
    }

    public static void setSelectedLeague(Context c, String code) {
        prefs(c).edit()
                .putString(KEY_FOOTBALL_LEAGUE, code)
                .putLong(KEY_FOOTBALL_CACHE_TS, 0L)
                .apply();
    }

    public interface FootballCallback {
        void onResult(List<MatchItem> matches);
    }

    private static List<MatchItem> sMemMatches = new ArrayList<MatchItem>();

    public static List<MatchItem> getCachedMatches(Context c) {
        if (!sMemMatches.isEmpty()) return new ArrayList<MatchItem>(sMemMatches);
        String raw = prefs(c).getString(KEY_FOOTBALL_CACHE_JSON, null);
        if (raw != null) {
            List<MatchItem> parsed = parseEspnEvents(c, raw, "");
            if (!parsed.isEmpty()) {
                sMemMatches = parsed;
                return parsed;
            }
        }
        return Collections.emptyList();
    }

    public static void fetchFootballMatches(final Context c, final boolean force, final FootballCallback cb) {
        final SharedPreferences sp = prefs(c);
        long now = System.currentTimeMillis();
        long lastTs = sp.getLong(KEY_FOOTBALL_CACHE_TS, 0L);
        if (!force && !sMemMatches.isEmpty() && (now - lastTs < 120_000L)) {
            if (cb != null) cb.onResult(new ArrayList<MatchItem>(sMemMatches));
            return;
        }
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                List<MatchItem> all = new ArrayList<MatchItem>();
                String league = getSelectedLeague(c);
                try {
                    if ("all".equals(league)) {
                        String[] codes = { "uefa.champions", "eng.1", "esp.1", "tur.1", "ita.1", "ger.1" };
                        String firstRaw = null;
                        for (String code : codes) {
                            try {
                                String url = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + code + "/scoreboard";
                                String json = httpGet(url);
                                if (firstRaw == null) firstRaw = json;
                                List<MatchItem> sub = parseEspnEvents(c, json, code);
                                all.addAll(sub);
                            } catch (Throwable ignored) {}
                        }
                        if (all.isEmpty()) {
                            String url = "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard";
                            String json = httpGet(url);
                            all.addAll(parseEspnEvents(c, json, ""));
                            firstRaw = json;
                        }
                        if (firstRaw != null) {
                            sp.edit()
                                    .putString(KEY_FOOTBALL_CACHE_JSON, firstRaw)
                                    .putLong(KEY_FOOTBALL_CACHE_TS, System.currentTimeMillis())
                                    .apply();
                        }
                    } else {
                        String url = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + league + "/scoreboard";
                        String json = httpGet(url);
                        all.addAll(parseEspnEvents(c, json, league));
                        sp.edit()
                                .putString(KEY_FOOTBALL_CACHE_JSON, json)
                                .putLong(KEY_FOOTBALL_CACHE_TS, System.currentTimeMillis())
                                .apply();
                    }
                } catch (Throwable ignored) {}

                sortMatches(c, all);
                if (!all.isEmpty()) {
                    sMemMatches = all;
                }
                final List<MatchItem> result = !all.isEmpty() ? all : getCachedMatches(c);
                if (cb != null) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(result);
                        }
                    });
                }
            }
        });
    }

    private static void sortMatches(Context c, List<MatchItem> list) {
        final Set<String> favs = getFavoriteTeams(c);
        for (MatchItem m : list) {
            m.isFav = isTeamFav(m.homeTeam, favs) || isTeamFav(m.awayTeam, favs);
        }
        Collections.sort(list, new Comparator<MatchItem>() {
            @Override
            public int compare(MatchItem a, MatchItem b) {
                if (a.isFav != b.isFav) return a.isFav ? -1 : 1;
                if (a.isLive != b.isLive) return a.isLive ? -1 : 1;
                if (a.isFinished != b.isFinished) return a.isFinished ? 1 : -1;
                return Long.compare(a.startEpochMs, b.startEpochMs);
            }
        });
    }

    private static boolean isTeamFav(String teamName, Set<String> favs) {
        if (teamName == null || favs == null) return false;
        String lower = teamName.toLowerCase(Locale.ROOT);
        for (String f : favs) {
            if (f != null && !f.isEmpty() && lower.contains(f.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<MatchItem> parseEspnEvents(Context c, String rawJson, String fallbackLeague) {
        List<MatchItem> out = new ArrayList<MatchItem>();
        try {
            JSONObject root = new JSONObject(rawJson);
            String leagueTitle = fallbackLeague;
            JSONArray leagues = root.optJSONArray("leagues");
            if (leagues != null && leagues.length() > 0) {
                JSONObject lg = leagues.optJSONObject(0);
                if (lg != null) leagueTitle = lg.optString("abbreviation", lg.optString("name", fallbackLeague));
            }
            JSONArray events = root.optJSONArray("events");
            if (events == null) return out;
            Set<String> favs = getFavoriteTeams(c);
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                JSONArray comps = ev.optJSONArray("competitions");
                if (comps == null || comps.length() == 0) continue;
                JSONObject comp = comps.optJSONObject(0);
                JSONArray competitors = comp.optJSONArray("competitors");
                if (competitors == null || competitors.length() < 2) continue;

                MatchItem item = new MatchItem();
                item.leagueName = leagueTitle;
                for (int j = 0; j < competitors.length(); j++) {
                    JSONObject teamEntry = competitors.optJSONObject(j);
                    if (teamEntry == null) continue;
                    String homeAway = teamEntry.optString("homeAway", j == 0 ? "home" : "away");
                    JSONObject teamObj = teamEntry.optJSONObject("team");
                    String tName = teamObj != null
                            ? teamObj.optString("shortDisplayName", teamObj.optString("displayName", "Team"))
                            : "Team";
                    String score = teamEntry.optString("score", "0");
                    if ("home".equalsIgnoreCase(homeAway)) {
                        item.homeTeam = tName;
                        item.homeScore = score;
                    } else {
                        item.awayTeam = tName;
                        item.awayScore = score;
                    }
                }

                JSONObject status = comp.optJSONObject("status");
                if (status == null) status = ev.optJSONObject("status");
                if (status != null) {
                    JSONObject type = status.optJSONObject("type");
                    if (type != null) {
                        String state = type.optString("state", "pre");
                        item.isLive = "in".equalsIgnoreCase(state);
                        item.isFinished = "post".equalsIgnoreCase(state);
                        item.statusText = type.optString("shortDetail", type.optString("detail", ""));
                    }
                }

                String dateIso = ev.optString("date", "");
                if (!dateIso.isEmpty()) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        Date dt = sdf.parse(dateIso);
                        if (dt != null) {
                            item.startEpochMs = dt.getTime();
                            if (!item.isLive && !item.isFinished) {
                                SimpleDateFormat localFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                item.statusText = localFmt.format(dt);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                item.isFav = isTeamFav(item.homeTeam, favs) || isTeamFav(item.awayTeam, favs);
                out.add(item);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    // =========================================================================
    // 3. DASHBOARD ROW UI BUILDER
    // =========================================================================

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public interface NavBridge {
        void onFocusUp();
        void onFocusDown();
        void onOpenWeather();
    }

    /**
     * Builds the 4-widget horizontal dashboard row.
     */
    public static View buildDashboardView(
            final MainActivity host,
            final MainActivity.LiquidGlass.Backdrop glassSource,
            int screenW,
            int screenH,
            float unit,
            final List<View> outCards) {
        return buildDashboardViewInternal(host, outCards, null);
    }

    public static FrameLayout buildRow(
            final MainActivity host,
            float unit,
            int screenW,
            int side,
            final List<View> outCards,
            final MainActivity.LiquidGlass.Backdrop glassSource) {
        FrameLayout wrap = new FrameLayout(host);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);
        LinearLayout box = (LinearLayout) buildDashboardViewInternal(host, outCards, null);
        box.setPadding(side, 0, side, 0);
        wrap.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    public static View buildDashboardView(final MainActivity host, final NavBridge nav) {
        return buildDashboardViewInternal(host, null, nav);
    }

    private static View buildDashboardViewInternal(
            final MainActivity host,
            final List<View> outCards,
            final NavBridge nav) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        final List<View> cards = new ArrayList<View>();

        if (isWidgetPrayerEnabled(host)) {
            cards.add(createPrayerWidgetCard(host));
        }
        if (isWidgetFootballEnabled(host)) {
            cards.add(createFootballWidgetCard(host));
        }
        if (isWidgetReleasesEnabled(host)) {
            cards.add(createReleasesWidgetCard(host));
        }
        if (isWidgetWeatherEnabled(host)) {
            cards.add(createWeatherWidgetCard(host, nav));
        }
        if (outCards != null) {
            outCards.clear();
            outCards.addAll(cards);
        }

        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int sidePad = dp(host, 48);
        int gap = dp(host, 14);
        int count = Math.max(1, cards.size());
        int totalAvailW = screenW - sidePad * 2 - gap * (count - 1);
        int cardW = Math.min(dp(host, 290), Math.max(dp(host, 180), totalAvailW / count));
        int cardH = dp(host, 96);

        for (int i = 0; i < cards.size(); i++) {
            final int idx = i;
            final View card = cards.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) lp.leftMargin = gap;
            row.addView(card, lp);

                if (nav != null) {
                    card.setOnKeyListener(new View.OnKeyListener() {
                        @Override
                        public boolean onKey(View v, int keyCode, KeyEvent event) {
                            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                if (idx > 0) {
                                    cards.get(idx - 1).requestFocus();
                                }
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                if (idx + 1 < cards.size()) {
                                    cards.get(idx + 1).requestFocus();
                                }
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                nav.onFocusUp();
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                nav.onFocusDown();
                                return true;
                            }
                            return false;
                        }
                    });
                }
        }
       TextView titleTv = new TextView(host);
titleTv.setText(Html.fromHtml(host.getString(R.string.tiggo_title), Html.FROM_HTML_MODE_LEGACY));
titleTv.setTextSize(22);
FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.WRAP_CONTENT,
    FrameLayout.LayoutParams.WRAP_CONTENT
);
titleParams.gravity = Gravity.START | Gravity.TOP;
titleParams.leftMargin = dp(host, 32);
titleParams.topMargin = dp(host, 24);
row.addView(titleTv, titleParams);
 return row;

    }

    private static FrameLayout createBaseGlassCard(final MainActivity host, final int accentColor) {
        final FrameLayout card = new FrameLayout(host) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        float r = dp(host, 15);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                r, 0x5C1E141C, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(host, 6);
        glass.rimStrength = 0.42f;
        card.setBackground(glass);

        final GradientDrawable focusRing = new GradientDrawable();
        focusRing.setCornerRadius(r);
        focusRing.setColor(Color.TRANSPARENT);
        focusRing.setStroke(dp(host, 2), Color.WHITE);

        card.setFocusable(true);
        card.setFocusableInTouchMode(false);
        card.setPadding(dp(host, 12), dp(host, 8), dp(host, 12), dp(host, 8));
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                card.setForeground(hasFocus ? focusRing : null);
                v.animate()
                        .scaleX(hasFocus ? 1.035f : 1.0f)
                        .scaleY(hasFocus ? 1.035f : 1.0f)
                        .translationY(hasFocus ? -dp(host, 2) : 0f)
                        .translationZ(hasFocus ? dp(host, 8) : 0f)
                        .setDuration(150)
                        .setInterpolator(MainActivity.EASE)
                        .start();
                v.invalidate();
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 1: PRAYER TIMES CARD
    // -------------------------------------------------------------------------
    private static View createPrayerWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFF34D399);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        // Header row: Mosque/Crescent dot + Title + City
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF34D399);
        dot.setBackground(dotBg);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        headerTitle.setText(I18n.t(host, "dash_prayer_times").toUpperCase(I18n.locale(host)));
        headerTitle.setTextColor(0xCC34D399);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        headerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerTitle.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = dp(host, 5);
        head.addView(headerTitle, htlp);

        final TextView cityTv = new TextView(host);
        cityTv.setTextColor(0x88FFFFFF);
        cityTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        cityTv.setSingleLine(true);
        cityTv.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(cityTv);

        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Next prayer + countdown row
        final TextView nextPrayerTv = new TextView(host);
        nextPrayerTv.setTextColor(Color.WHITE);
        nextPrayerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        nextPrayerTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        nextPrayerTv.setSingleLine(true);
        nextPrayerTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nplp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nplp.topMargin = dp(host, 2);
        col.addView(nextPrayerTv, nplp);

        final TextView countdownTv = new TextView(host);
        countdownTv.setTextColor(0xB8FFFFFF);
        countdownTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.8f);
        countdownTv.setSingleLine(true);
        col.addView(countdownTv);

        // Mini 5-prayer strip at bottom
        final LinearLayout strip = new LinearLayout(host);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(host, 3);
        col.addView(strip, slp);

        card.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable bindUi = new Runnable() {
            @Override
            public void run() {
                PrayerData d = getCachedOrFallbackPrayer(host);
                cityTv.setText(d.city != null ? d.city : "");
                int[] next = computeNextPrayer(d);
                String[] labels = getPrayerLabels(host);
                String[] times = { d.fajr, d.dhuhr, d.asr, d.maghrib, d.isha };
                int nextIdx = next[0];
                int remMin = next[1];
                int hrs = remMin / 60;
                int mins = remMin % 60;
                nextPrayerTv.setText(labels[nextIdx] + "  " + times[nextIdx]);
                String remStr = hrs > 0
                        ? (hrs + "h " + mins + "m " + I18n.t(host, "prayer_remaining"))
                        : (mins + "m " + I18n.t(host, "prayer_remaining"));
                countdownTv.setText(remStr);

                strip.removeAllViews();
                for (int i = 0; i < 5; i++) {
                    LinearLayout cell = new LinearLayout(host);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    boolean isNext = (i == nextIdx);

                    TextView lbl = new TextView(host);
                    String shortName = labels[i].length() > 3 ? labels[i].substring(0, 3) : labels[i];
                    lbl.setText(shortName);
                    lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
                    lbl.setTextColor(isNext ? 0xFF34D399 : 0x77FFFFFF);
                    lbl.setTypeface(Typeface.create("sans-serif", isNext ? Typeface.BOLD : Typeface.NORMAL));
                    cell.addView(lbl);

                    TextView tm = new TextView(host);
                    tm.setText(times[i]);
                    tm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
                    tm.setTextColor(isNext ? Color.WHITE : 0xAAFFFFFF);
                    tm.setTypeface(Typeface.create("sans-serif-medium", isNext ? Typeface.BOLD : Typeface.NORMAL));
                    cell.addView(tm);

                    strip.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
            }
        };

        bindUi.run();
        fetchPrayerTimes(host, false, new PrayerCallback() {
            @Override
            public void onResult(PrayerData data) {
                bindUi.run();
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPrayerDialog(host, bindUi);
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 2: FOOTBALL MATCHES CARD
    // -------------------------------------------------------------------------
    private static View createFootballWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFF38BDF8);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF38BDF8);
        dot.setBackground(dotBg);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        headerTitle.setText(I18n.t(host, "dash_football").toUpperCase(I18n.locale(host)));
        headerTitle.setTextColor(0xCC38BDF8);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        headerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerTitle.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = dp(host, 5);
        head.addView(headerTitle, htlp);

        final TextView badgeTv = new TextView(host);
        badgeTv.setTextColor(0xFF34D399);
        badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        badgeTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        head.addView(badgeTv);

        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout matchRows = new LinearLayout(host);
        matchRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrlp.topMargin = dp(host, 4);
        col.addView(matchRows, mrlp);

        card.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable bindMatches = new Runnable() {
            @Override
            public void run() {
                List<MatchItem> list = getCachedMatches(host);
                matchRows.removeAllViews();
                if (list.isEmpty()) {
                    badgeTv.setText("");
                    TextView empty = new TextView(host);
                    empty.setText(I18n.t(host, "football_loading"));
                    empty.setTextColor(0x99FFFFFF);
                    empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                    matchRows.addView(empty);
                    return;
                }
                int liveCount = 0;
                for (MatchItem m : list) if (m.isLive) liveCount++;
                badgeTv.setText(liveCount > 0 ? ("● " + liveCount + " LIVE") : list.get(0).leagueName);
                badgeTv.setTextColor(liveCount > 0 ? 0xFF4ADE80 : 0x88FFFFFF);

                int showCount = Math.min(3, list.size());
                for (int i = 0; i < showCount; i++) {
                    MatchItem m = list.get(i);
                    LinearLayout r = new LinearLayout(host);
                    r.setOrientation(LinearLayout.HORIZONTAL);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    if (i > 0) {
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rlp.topMargin = dp(host, 2);
                        r.setLayoutParams(rlp);
                    }

                    TextView teams = new TextView(host);
                    String prefix = m.isFav ? "★ " : "";
                    teams.setText(prefix + m.homeTeam + " - " + m.awayTeam);
                    teams.setTextColor(m.isFav ? 0xFFFDE047 : Color.WHITE);
                    teams.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                    teams.setTypeface(Typeface.create("sans-serif-medium", i == 0 ? Typeface.BOLD : Typeface.NORMAL));
                    teams.setSingleLine(true);
                    teams.setEllipsize(TextUtils.TruncateAt.END);
                    r.addView(teams, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView right = new TextView(host);
                    if (m.isLive || m.isFinished) {
                        right.setText(m.homeScore + ":" + m.awayScore + (m.isLive ? " '" : ""));
                        right.setTextColor(m.isLive ? 0xFF4ADE80 : 0xCCFFFFFF);
                    } else {
                        right.setText(m.statusText != null && !m.statusText.isEmpty() ? m.statusText : "VS");
                        right.setTextColor(0x99FFFFFF);
                    }
                    right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
                    right.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    scLp.leftMargin = dp(host, 6);
                    r.addView(right, scLp);

                    matchRows.addView(r);
                }
            }
        };

        bindMatches.run();
        fetchFootballMatches(host, false, new FootballCallback() {
            @Override
            public void onResult(List<MatchItem> matches) {
                bindMatches.run();
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFootballDialog(host, bindMatches);
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 3: LATEST RELEASES CARD
    // -------------------------------------------------------------------------
    private static int sReleaseIdx = 0;

    private static View createReleasesWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFFA855F7);

        LinearLayout rootRow = new LinearLayout(host);
        rootRow.setOrientation(LinearLayout.HORIZONTAL);
        rootRow.setGravity(Gravity.CENTER_VERTICAL);

        final ImageView posterView = new ImageView(host);
        posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable posterBg = new GradientDrawable();
        posterBg.setColor(0x33FFFFFF);
        posterBg.setCornerRadius(dp(host, 8));
        posterView.setBackground(posterBg);
        posterView.setOutlineProvider(host.roundedOutline(new float[]{dp(host, 8)}));
        posterView.setClipToOutline(true);
        rootRow.addView(posterView, new LinearLayout.LayoutParams(dp(host, 50), dp(host, 74)));

        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colp.leftMargin = dp(host, 10);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFFC084FC);
        dot.setBackground(dotBg);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        headerTitle.setText(I18n.t(host, "dash_latest_releases").toUpperCase(I18n.locale(host)));
        headerTitle.setTextColor(0xCCC084FC);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        headerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerTitle.setLetterSpacing(0.06f);
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = dp(host, 5);
        head.addView(headerTitle, htlp);

        final TextView countryBadgeTv = new TextView(host);
        countryBadgeTv.setTextColor(0x88FFFFFF);
        countryBadgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        countryBadgeTv.setSingleLine(true);
        head.addView(countryBadgeTv);

        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView titleTv = new TextView(host);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ttlp.topMargin = dp(host, 3);
        col.addView(titleTv, ttlp);

        final TextView metaTv = new TextView(host);
        metaTv.setTextColor(0xB8FFFFFF);
        metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.8f);
        metaTv.setSingleLine(true);
        metaTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mtlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mtlp.topMargin = dp(host, 2);
        col.addView(metaTv, mtlp);

        rootRow.addView(col, colp);
        card.addView(rootRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final List<Suggestions.Item> itemsRef = new ArrayList<Suggestions.Item>();
        final Runnable bindItem = new Runnable() {
            @Override
            public void run() {
                List<String> effCountries = getEffectiveReleasesCountries(host);
                countryBadgeTv.setText(TextUtils.join(",", effCountries));
                if (itemsRef.isEmpty()) {
                    titleTv.setText(I18n.t(host, "releases_loading"));
                    metaTv.setText("");
                    return;
                }
                if (sReleaseIdx >= itemsRef.size()) sReleaseIdx = 0;
                final Suggestions.Item it = itemsRef.get(sReleaseIdx);
                titleTv.setText(it.title != null ? it.title : "");
                String typeBadge = "tv".equalsIgnoreCase(it.type) || "series".equalsIgnoreCase(it.type) ? "TV Series" : "Movie";
                StringBuilder sb = new StringBuilder(typeBadge);
                if (it.year != null && !it.year.isEmpty()) sb.append(" • ").append(it.year);
                if (it.rating > 0) sb.append(String.format(Locale.US, " • ★ %.1f", it.rating));
                if (it.country != null && !it.country.isEmpty() && effCountries.size() > 1) {
                    sb.append(" • ").append(it.country);
                }
                metaTv.setText(sb.toString());

                final String imgUrl = it.backdrop;
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    EXEC.execute(new Runnable() {
                        @Override
                        public void run() {
                            final Bitmap bmp = Suggestions.image(host, imgUrl, "w342", dp(host, 140));
                            if (bmp != null) {
                                MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        posterView.setImageBitmap(bmp);
                                    }
                                });
                            }
                        }
                    });
                }
            }
        };

        List<Suggestions.Item> cached = getCachedReleases(host);
        if (cached != null && !cached.isEmpty()) {
            itemsRef.addAll(cached);
        }
        bindItem.run();
        fetchLatestReleases(host, false, new ReleasesCallback() {
            @Override
            public void onResult(List<Suggestions.Item> fresh) {
                if (fresh != null && !fresh.isEmpty()) {
                    itemsRef.clear();
                    itemsRef.addAll(fresh);
                    bindItem.run();
                }
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showReleasesDialog(host, itemsRef, new Runnable() {
                    @Override
                    public void run() {
                        sReleaseIdx++;
                        bindItem.run();
                    }
                });
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 4: WEATHER CARD
    // -------------------------------------------------------------------------
    private static View createWeatherWidgetCard(final MainActivity host, final NavBridge nav) {
        final FrameLayout card = createBaseGlassCard(host, 0xFFFBBF24);

        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFFFBBF24);
        dot.setBackground(dotBg);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        headerTitle.setText(I18n.t(host, "weather").toUpperCase(I18n.locale(host)));
        headerTitle.setTextColor(0xCCFBBF24);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        headerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerTitle.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = dp(host, 5);
        head.addView(headerTitle, htlp);

        final TextView cityTv = new TextView(host);
        cityTv.setTextColor(0x88FFFFFF);
        cityTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        cityTv.setSingleLine(true);
        cityTv.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(cityTv);

        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout mid = new LinearLayout(host);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(host, 3);

        final Weather.WeatherIconView iconView = new Weather.WeatherIconView(host);
        mid.addView(iconView, new LinearLayout.LayoutParams(dp(host, 34), dp(host, 34)));

        LinearLayout tCol = new LinearLayout(host);
        tCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tclp.leftMargin = dp(host, 8);

        final TextView tempTv = new TextView(host);
        tempTv.setTextColor(Color.WHITE);
        tempTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f);
        tempTv.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        tCol.addView(tempTv);

        final TextView condTv = new TextView(host);
        condTv.setTextColor(0xCCFFFFFF);
        condTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        condTv.setSingleLine(true);
        condTv.setEllipsize(TextUtils.TruncateAt.END);
        tCol.addView(condTv);

        mid.addView(tCol, tclp);
        col.addView(mid, mlp);

        final TextView forecastSummaryTv = new TextView(host);
        forecastSummaryTv.setTextColor(0x99FFFFFF);
        forecastSummaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        forecastSummaryTv.setSingleLine(true);
        forecastSummaryTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams fslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fslp.topMargin = dp(host, 3);
        col.addView(forecastSummaryTv, fslp);

        card.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable bindWeather = new Runnable() {
            @Override
            public void run() {
                Weather.Data w = Weather.cached(host);
                if (w == null) {
                    tempTv.setText("--°");
                    condTv.setText(I18n.t(host, "weather_updating"));
                    cityTv.setText("");
                    forecastSummaryTv.setText("");
                    return;
                }
                cityTv.setText(w.place != null ? w.place : "");
                iconView.setType(Weather.iconType(w.code));
                tempTv.setText(Weather.formatTemp(host, w.temp));
                condTv.setText(Weather.codeText(host, w.code));
                if (w.days != null && w.days.size() > 1 && w.days.get(1) != null) {
                    Weather.Day tm = w.days.get(1);
                    forecastSummaryTv.setText(I18n.t(host, "tomorrow_short") + ": "
                            + Weather.codeText(host, tm.code) + "  "
                            + Weather.formatTemp(host, tm.max) + " / " + Weather.formatTemp(host, tm.min));
                } else if (w.days != null && !w.days.isEmpty() && w.days.get(0) != null) {
                    Weather.Day td = w.days.get(0);
                    forecastSummaryTv.setText("H: " + Weather.formatTemp(host, td.max)
                            + "  L: " + Weather.formatTemp(host, td.min));
                }
            }
        };

        bindWeather.run();
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nav != null) nav.onOpenWeather();
                else host.showWeather();
            }
        });
        return card;
    }

    // =========================================================================
    // 4. INTERACTIVE DETAIL DIALOGS FOR WIDGETS
    // =========================================================================

    private static void showPrayerDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = new Dialog(host);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout panel = createModalContainer(host, dp(host, 520));
        PrayerData pd = getCachedOrFallbackPrayer(host);

        TextView title = new TextView(host);
        title.setText(I18n.t(host, "dash_prayer_times")
                + (pd.city != null && !pd.city.isEmpty() ? (" — " + pd.city) : ""));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        int[] next = computeNextPrayer(pd);
        String[] labels = {
            I18n.t(host, "prayer_fajr"),
            I18n.t(host, "prayer_sunrise"),
            I18n.t(host, "prayer_dhuhr"),
            I18n.t(host, "prayer_asr"),
            I18n.t(host, "prayer_maghrib"),
            I18n.t(host, "prayer_isha")
        };
        String[] times = { pd.fajr, pd.sunrise, pd.dhuhr, pd.asr, pd.maghrib, pd.isha };
        int highlightIdx = next[0] == 0 ? 0 : next[0] + 1;

        LinearLayout table = new LinearLayout(host);
        table.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(host, 14);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout r = new LinearLayout(host);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(host, 14), dp(host, 10), dp(host, 14), dp(host, 10));
            GradientDrawable rbg = new GradientDrawable();
            rbg.setCornerRadius(dp(host, 10));
            boolean hi = (i == highlightIdx);
            rbg.setColor(hi ? 0x3334D399 : 0x14FFFFFF);
            r.setBackground(rbg);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = dp(host, 6);

            TextView nl = new TextView(host);
            nl.setText(labels[i]);
            nl.setTextColor(hi ? 0xFF34D399 : Color.WHITE);
            nl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            nl.setTypeface(Typeface.create("sans-serif-medium", hi ? Typeface.BOLD : Typeface.NORMAL));
            r.addView(nl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tl = new TextView(host);
            tl.setText(times[i]);
            tl.setTextColor(Color.WHITE);
            tl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            tl.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            r.addView(tl);
            table.addView(r, rlp);
        }
        panel.addView(table, tlp);

        // Calculation method cycle button
        final TextView methodBtn = createActionPill(host,
                I18n.t(host, "prayer_method_label") + ": " + getPrayerMethodName(host));
        LinearLayout.LayoutParams mblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mblp.topMargin = dp(host, 14);
        methodBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = getPrayerMethod(host);
                int nextIdx = 0;
                for (int i = 0; i < PRAYER_METHOD_IDS.length; i++) {
                    if (PRAYER_METHOD_IDS[i] == cur) {
                        nextIdx = (i + 1) % PRAYER_METHOD_IDS.length;
                        break;
                    }
                }
                setPrayerMethod(host, PRAYER_METHOD_IDS[nextIdx]);
                methodBtn.setText(I18n.t(host, "prayer_method_label") + ": " + getPrayerMethodName(host));
                fetchPrayerTimes(host, true, new PrayerCallback() {
                    @Override
                    public void onResult(PrayerData data) {
                        if (onUpdated != null) onUpdated.run();
                        d.dismiss();
                        showPrayerDialog(host, onUpdated);
                    }
                });
            }
        });
        panel.addView(methodBtn, mblp);

        d.setContentView(panel);
        d.show();
        methodBtn.requestFocus();
    }

    private static void showFootballDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = new Dialog(host);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout panel = createModalContainer(host, dp(host, 620));

        TextView title = new TextView(host);
        title.setText(I18n.t(host, "dash_football"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        // Top actions row: League filter + Favorite Teams picker
        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(host, 10);

        String curLeague = getSelectedLeague(host);
        String curLeagueLabel = LEAGUES[0][1];
        for (String[] lg : LEAGUES) {
            if (lg[0].equals(curLeague)) {
                curLeagueLabel = lg[1];
                break;
            }
        }
        final TextView leagueBtn = createActionPill(host, "League: " + curLeagueLabel);
        leagueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cLg = getSelectedLeague(host);
                int ni = 0;
                for (int i = 0; i < LEAGUES.length; i++) {
                    if (LEAGUES[i][0].equals(cLg)) {
                        ni = (i + 1) % LEAGUES.length;
                        break;
                    }
                }
                setSelectedLeague(host, LEAGUES[ni][0]);
                fetchFootballMatches(host, true, new FootballCallback() {
                    @Override
                    public void onResult(List<MatchItem> matches) {
                        if (onUpdated != null) onUpdated.run();
                        d.dismiss();
                        showFootballDialog(host, onUpdated);
                    }
                });
            }
        });
        actions.addView(leagueBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView favBtn = createActionPill(host, "★ " + I18n.t(host, "football_fav_teams"));
        LinearLayout.LayoutParams fblp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fblp.leftMargin = dp(host, 10);
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showFavoriteTeamsDialog(host, onUpdated);
            }
        });
        actions.addView(favBtn, fblp);
        panel.addView(actions, alp);

        ScrollView sv = new ScrollView(host);
        LinearLayout listCol = new LinearLayout(host);
        listCol.setOrientation(LinearLayout.VERTICAL);

        List<MatchItem> matches = getCachedMatches(host);
        if (matches.isEmpty()) {
            TextView empty = new TextView(host);
            empty.setText(I18n.t(host, "football_no_matches"));
            empty.setTextColor(0x99FFFFFF);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            empty.setPadding(0, dp(host, 20), 0, dp(host, 20));
            listCol.addView(empty);
        } else {
            for (int i = 0; i < Math.min(20, matches.size()); i++) {
                final MatchItem m = matches.get(i);
                LinearLayout row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(host, 14), dp(host, 10), dp(host, 14), dp(host, 10));
                GradientDrawable rbg = new GradientDrawable();
                rbg.setCornerRadius(dp(host, 10));
                rbg.setColor(m.isFav ? 0x28FDE047 : 0x16FFFFFF);
                row.setBackground(rbg);
                row.setFocusable(true);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(host, 6);

                TextView tms = new TextView(host);
                tms.setText((m.isFav ? "★ " : "") + m.homeTeam + "  vs  " + m.awayTeam);
                tms.setTextColor(m.isFav ? 0xFFFDE047 : Color.WHITE);
                tms.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                tms.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                row.addView(tms, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView st = new TextView(host);
                String scoreStr = (m.isLive || m.isFinished)
                        ? (m.homeScore + " - " + m.awayScore + "  (" + m.statusText + ")")
                        : m.statusText;
                st.setText(scoreStr);
                st.setTextColor(m.isLive ? 0xFF4ADE80 : 0xCCFFFFFF);
                st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                row.addView(st);

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleFavoriteTeam(host, m.homeTeam);
                        fetchFootballMatches(host, true, new FootballCallback() {
                            @Override
                            public void onResult(List<MatchItem> matches) {
                                if (onUpdated != null) onUpdated.run();
                                d.dismiss();
                                showFootballDialog(host, onUpdated);
                            }
                        });
                    }
                });
                listCol.addView(row, rlp);
            }
        }

        sv.addView(listCol);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 290));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        d.setContentView(panel);
        d.show();
        leagueBtn.requestFocus();
    }

    private static void showFavoriteTeamsDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = new Dialog(host);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout panel = createModalContainer(host, dp(host, 540));

        TextView title = new TextView(host);
        title.setText("★ " + I18n.t(host, "football_fav_teams"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        ScrollView sv = new ScrollView(host);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        final Set<String> favs = getFavoriteTeams(host);
        for (final String team : POPULAR_TEAMS) {
            final boolean isFav = favs.contains(team);
            final TextView row = createActionPill(host, (isFav ? "★  " : "☆  ") + team);
            row.setTextColor(isFav ? 0xFFFDE047 : Color.WHITE);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(host, 6);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFavoriteTeam(host, team);
                    boolean nowFav = getFavoriteTeams(host).contains(team);
                    row.setText((nowFav ? "★  " : "☆  ") + team);
                    row.setTextColor(nowFav ? 0xFFFDE047 : Color.WHITE);
                    fetchFootballMatches(host, true, new FootballCallback() {
                        @Override
                        public void onResult(List<MatchItem> matches) {
                            if (onUpdated != null) onUpdated.run();
                        }
                    });
                }
            });
            col.addView(row, rlp);
        }

        sv.addView(col);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 300));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        d.setContentView(panel);
        d.show();
    }

    private static void showReleasesDialog(final MainActivity host, final List<Suggestions.Item> items, final Runnable onCycle) {
        final Dialog d = new Dialog(host);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout panel = createModalContainer(host, dp(host, 560));

        TextView title = new TextView(host);
        title.setText(I18n.t(host, "dash_latest_releases"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        ScrollView sv = new ScrollView(host);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < Math.min(20, items.size()); i++) {
            final int idx = i;
            final Suggestions.Item it = items.get(i);
            String label = it.title + (it.year != null && !it.year.isEmpty() ? (" (" + it.year + ")") : "")
                    + (it.rating > 0 ? String.format(Locale.US, "  ★ %.1f", it.rating) : "");
            TextView row = createActionPill(host, label);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(host, 6);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sReleaseIdx = idx;
                    if (onCycle != null) onCycle.run();
                    d.dismiss();
                }
            });
            col.addView(row, rlp);
        }

        sv.addView(col);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 310));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        d.setContentView(panel);
        d.show();
    }

    private static LinearLayout createModalContainer(Context c, int widthPx) {
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(c, 24), dp(c, 22), dp(c, 24), dp(c, 22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2141824);
        bg.setCornerRadius(dp(c, 22));
        bg.setStroke(dp(c, 1), 0x33FFFFFF);
        panel.setBackground(bg);
        panel.setLayoutParams(new FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private static TextView createActionPill(final Context c, String text) {
        final TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        tv.setFocusable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 12));
        bg.setColor(0x22FFFFFF);
        bg.setStroke(dp(c, 1), 0x33FFFFFF);
        tv.setBackground(bg);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setColor(hasFocus ? 0x44FFFFFF : 0x22FFFFFF);
                bg.setStroke(dp(c, hasFocus ? 2 : 1), hasFocus ? 0xEEFFFFFF : 0x33FFFFFF);
            }
        });
        return tv;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "tvOSLauncher/2.2 (Android TV)");
            conn.setRequestProperty("Accept", "application/json");
            InputStream in = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
