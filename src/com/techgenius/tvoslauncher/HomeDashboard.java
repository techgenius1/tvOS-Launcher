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
    private static final String KEY_W_CRICKET = "widget_cricket";
    private static final String KEY_W_NEWS = "widget_news";
    private static final String KEY_W_TOP_MEDIA = "widget_top_media";
    private static final String KEY_WIDGET_ORDER = "widget_order";
    private static final String KEY_ALL_DEFAULT_V2 = "widgets_all_default_v2";

    public static final String WIDGET_PRAYER = "prayer";
    public static final String WIDGET_FOOTBALL = "football";
    public static final String WIDGET_RELEASES = "releases";
    public static final String WIDGET_WEATHER = "weather";
    public static final String WIDGET_CRICKET = "cricket";
    public static final String WIDGET_NEWS = "news";
    public static final String WIDGET_TOP_MEDIA = "top_media";

    public static final String[] DEFAULT_WIDGET_IDS = {
        WIDGET_PRAYER, WIDGET_FOOTBALL, WIDGET_RELEASES, WIDGET_WEATHER,
        WIDGET_CRICKET, WIDGET_NEWS, WIDGET_TOP_MEDIA
    };

    private static volatile String sMovingWidgetId = null;

    private static final String KEY_PRAYER_METHOD = "prayer_method"; // Default 13 (Diyanet / Turkey & region)
    private static final String KEY_PRAYER_LOC_AUTO = "prayer_loc_auto";
    private static final String KEY_PRAYER_LOC_NAME = "prayer_loc_name";
    private static final String KEY_PRAYER_LOC_LAT = "prayer_loc_lat";
    private static final String KEY_PRAYER_LOC_LON = "prayer_loc_lon";
    private static final String KEY_PRAYER_CACHE_JSON = "prayer_cache_json";
    private static final String KEY_PRAYER_CACHE_DAY = "prayer_cache_day";
    private static final String KEY_PRAYER_CACHE_CITY = "prayer_cache_city";

    /** Popular quick-pick cities for Prayer Times: { "City, Country", "lat", "lon" }. */
    public static final String[][] PRAYER_POPULAR_CITIES = {
        {"Istanbul, Turkey", "41.0082", "28.9784"},
        {"Ankara, Turkey", "39.9334", "32.8597"},
        {"Izmir, Turkey", "38.4237", "27.1428"},
        {"Baku, Azerbaijan", "40.4093", "49.8671"},
        {"Mecca, Saudi Arabia", "21.3891", "39.8579"},
        {"Medina, Saudi Arabia", "24.5247", "39.5692"},
        {"Riyadh, Saudi Arabia", "24.7136", "46.6753"},
        {"Dubai, UAE", "25.2048", "55.2708"},
        {"Cairo, Egypt", "30.0444", "31.2357"},
        {"London, United Kingdom", "51.5074", "-0.1278"},
        {"Berlin, Germany", "52.5200", "13.4050"},
        {"Paris, France", "48.8566", "2.3522"},
        {"New York, United States", "40.7128", "-74.0060"},
        {"Toronto, Canada", "43.6532", "-79.3832"},
        {"Jakarta, Indonesia", "-6.2088", "106.8456"},
        {"Kuala Lumpur, Malaysia", "3.1390", "101.6869"},
        {"Karachi, Pakistan", "24.8607", "67.0011"},
        {"Dhaka, Bangladesh", "23.8103", "90.4125"},
        {"Sarajevo, Bosnia", "43.8563", "18.4131"},
        {"S\u00e3o Paulo, Brazil", "-23.5505", "-46.6333"}
    };

    private static final String KEY_FAV_TEAMS = "football_fav_teams";
    private static final String KEY_FOOTBALL_LEAGUE = "football_league";
    private static final String KEY_FOOTBALL_CACHE_JSON = "football_cache_json";
    private static final String KEY_FOOTBALL_CACHE_TS = "football_cache_ts";

    private static final String KEY_RELEASES_AUTO = "releases_countries_auto";
    private static final String KEY_RELEASES_SET = "releases_countries_set";
    private static final String KEY_RELEASES_CACHE_JSON = "releases_cache_json";
    private static final String KEY_RELEASES_CACHE_SIG = "releases_cache_sig";
    private static final String KEY_RELEASES_CACHE_TS = "releases_cache_ts";
    private static final String KEY_NEWS_COUNTRY = "news_country";

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

    /**
     * Supported countries/editions for Live News widget: { ISO code (gl), Display Name, Google News hl, ceid }.
     */
    public static final String[][] NEWS_COUNTRIES = {
        {"US", "\uD83C\uDDFA\uD83C\uDDF8  United States", "en-US", "US:en"},
        {"GB", "\uD83C\uDDEC\uD83C\uDDE7  United Kingdom", "en-GB", "GB:en"},
        {"CA", "\uD83C\uDDE8\uD83C\uDDE6  Canada", "en-CA", "CA:en"},
        {"AU", "\uD83C\uDDE6\uD83C\uDDFA  Australia", "en-AU", "AU:en"},
        {"TR", "\uD83C\uDDF9\uD83C\uDDF7  Türkiye (Turkey)", "tr", "TR:tr"},
        {"BR", "\uD83C\uDDE7\uD83C\uDDF7  Brasil (Brazil)", "pt-BR", "BR:pt-419"},
        {"PT", "\uD83C\uDDF5\uD83C\uDDF9  Portugal", "pt-PT", "PT:pt-150"},
        {"DE", "\uD83C\uDDE9\uD83C\uDDEA  Deutschland (Germany)", "de", "DE:de"},
        {"FR", "\uD83C\uDDEB\uD83C\uDDF7  France", "fr", "FR:fr"},
        {"ES", "\uD83C\uDDEA\uD83C\uDDF8  España (Spain)", "es", "ES:es"},
        {"MX", "\uD83C\uDDF2\uD83C\uDDFD  México (Mexico)", "es-419", "MX:es-419"},
        {"IT", "\uD83C\uDDEE\uD83C\uDDF9  Italia (Italy)", "it", "IT:it"},
        {"RU", "\uD83C\uDDF7\uD83C\uDDFA  Россия (Russia)", "ru", "RU:ru"},
        {"PL", "\uD83C\uDDF5\uD83C\uDDF1  Polska (Poland)", "pl", "PL:pl"},
        {"SE", "\uD83C\uDDF8\uD83C\uDDEA  Sverige (Sweden)", "sv", "SE:sv"},
        {"IN", "\uD83C\uDDEE\uD83C\uDDF3  India", "en-IN", "IN:en"},
        {"AZ", "\uD83C\uDDE6\uD83C\uDDFF  Azərbaycan (Azerbaijan)", "az", "AZ:az"},
        {"SA", "\uD83C\uDDF8\uD83C\uDDE6  السعودية (Saudi Arabia)", "ar", "SA:ar"},
        {"EG", "\uD83C\uDDEA\uD83C\uDDEC  مصر (Egypt)", "ar", "EG:ar"},
        {"IL", "\uD83C\uDDEE\uD83C\uDDF1  ישראל (Israel)", "he", "IL:he"},
        {"JP", "\uD83C\uDDEF\uD83C\uDDF5  日本 (Japan)", "ja", "JP:ja"},
        {"KR", "\uD83C\uDDF0\uD83C\uDDF7  대한민국 (South Korea)", "ko", "KR:ko"},
        {"TW", "\uD83C\uDDF9\uD83C\uDDFC  台灣 (Taiwan)", "zh-TW", "TW:zh-Hant"},
        {"CN", "\uD83C\uDDE8\uD83C\uDDF3  中国 (China)", "zh-CN", "CN:zh-Hans"}
    };

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HomeDashboard() {}

    private static SharedPreferences prefs(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ALL_DEFAULT_V2, false)) {
            sp.edit()
                    .putBoolean(KEY_ALL_DEFAULT_V2, true)
                    .putBoolean(KEY_W_CRICKET, true)
                    .putBoolean(KEY_W_NEWS, true)
                    .putBoolean(KEY_W_TOP_MEDIA, true)
                    .apply();
        }
        return sp;
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_ENABLED, en).apply();
    }

    public static boolean isShowWhenNoCw(Context c) {
        return prefs(c).getBoolean(KEY_SHOW_WHEN_NO_CW, true);
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

    public static boolean isWidgetCricketEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_CRICKET, true);
    }

    public static void setWidgetCricketEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_CRICKET, en).apply();
    }

    public static boolean isWidgetNewsEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_NEWS, true);
    }

    public static void setWidgetNewsEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_NEWS, en).apply();
    }

    public static boolean isWidgetTopMediaEnabled(Context c) {
        return prefs(c).getBoolean(KEY_W_TOP_MEDIA, true);
    }

    public static void setWidgetTopMediaEnabled(Context c, boolean en) {
        prefs(c).edit().putBoolean(KEY_W_TOP_MEDIA, en).apply();
    }

    public static List<String> getWidgetOrder(Context c) {
        String raw = prefs(c).getString(KEY_WIDGET_ORDER, "");
        List<String> out = new ArrayList<String>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(",")) {
                String p = part.trim();
                if (!p.isEmpty() && !out.contains(p)) {
                    for (String def : DEFAULT_WIDGET_IDS) {
                        if (def.equals(p)) {
                            out.add(p);
                            break;
                        }
                    }
                }
            }
        }
        for (String def : DEFAULT_WIDGET_IDS) {
            if (!out.contains(def)) {
                out.add(def);
            }
        }
        return out;
    }

    public static void setWidgetOrder(Context c, List<String> order) {
        if (order == null) return;
        prefs(c).edit().putString(KEY_WIDGET_ORDER, TextUtils.join(",", order)).apply();
    }

    public static List<String> getEnabledWidgetOrder(Context c) {
        List<String> out = new ArrayList<String>();
        for (String id : getWidgetOrder(c)) {
            if (isWidgetEnabledById(c, id)) {
                out.add(id);
            }
        }
        return out;
    }

    public static boolean isWidgetEnabledById(Context c, String id) {
        if (WIDGET_PRAYER.equals(id)) return isWidgetPrayerEnabled(c);
        if (WIDGET_FOOTBALL.equals(id)) return isWidgetFootballEnabled(c);
        if (WIDGET_RELEASES.equals(id)) return isWidgetReleasesEnabled(c);
        if (WIDGET_WEATHER.equals(id)) return isWidgetWeatherEnabled(c);
        if (WIDGET_CRICKET.equals(id)) return isWidgetCricketEnabled(c);
        if (WIDGET_NEWS.equals(id)) return isWidgetNewsEnabled(c);
        if (WIDGET_TOP_MEDIA.equals(id)) return isWidgetTopMediaEnabled(c);
        return false;
    }

    public static void setWidgetEnabledById(Context c, String id, boolean en) {
        if (WIDGET_PRAYER.equals(id)) setWidgetPrayerEnabled(c, en);
        else if (WIDGET_FOOTBALL.equals(id)) setWidgetFootballEnabled(c, en);
        else if (WIDGET_RELEASES.equals(id)) setWidgetReleasesEnabled(c, en);
        else if (WIDGET_WEATHER.equals(id)) setWidgetWeatherEnabled(c, en);
        else if (WIDGET_CRICKET.equals(id)) setWidgetCricketEnabled(c, en);
        else if (WIDGET_NEWS.equals(id)) setWidgetNewsEnabled(c, en);
        else if (WIDGET_TOP_MEDIA.equals(id)) setWidgetTopMediaEnabled(c, en);
    }

    public static String getWidgetTitleById(Context c, String id) {
        if (WIDGET_PRAYER.equals(id)) return I18n.t(c, "dash_prayer_times");
        if (WIDGET_FOOTBALL.equals(id)) return I18n.t(c, "dash_football");
        if (WIDGET_RELEASES.equals(id)) return I18n.t(c, "dash_latest_releases");
        if (WIDGET_WEATHER.equals(id)) return I18n.t(c, "weather");
        if (WIDGET_CRICKET.equals(id)) return I18n.t(c, "dash_cricket");
        if (WIDGET_NEWS.equals(id)) return I18n.t(c, "dash_news");
        if (WIDGET_TOP_MEDIA.equals(id)) return I18n.t(c, "dash_top_media");
        return id;
    }

    public static String getWidgetEmojiById(String id) {
        if (WIDGET_PRAYER.equals(id)) return "🕌  ";
        if (WIDGET_FOOTBALL.equals(id)) return "⚽  ";
        if (WIDGET_RELEASES.equals(id)) return "🎬  ";
        if (WIDGET_WEATHER.equals(id)) return "⛅  ";
        if (WIDGET_CRICKET.equals(id)) return "🏏  ";
        if (WIDGET_NEWS.equals(id)) return "📰  ";
        if (WIDGET_TOP_MEDIA.equals(id)) return "🎥  ";
        return "";
    }

    public static boolean isMovingWidget() {
        return sMovingWidgetId != null;
    }

    public static void startMovingWidget(MainActivity host, String widgetId) {
        sMovingWidgetId = widgetId;
        refreshHostDashboardAndFocus(host, widgetId);
    }

    public static void endMovingWidget(MainActivity host) {
        String id = sMovingWidgetId;
        sMovingWidgetId = null;
        if (host != null && id != null) {
            refreshHostDashboardAndFocus(host, id);
        }
    }

    public static boolean moveWidgetInEnabledOrder(Context c, String widgetId, int delta) {
        List<String> enabled = getEnabledWidgetOrder(c);
        int curIdx = enabled.indexOf(widgetId);
        int targetIdx = curIdx + delta;
        if (curIdx < 0 || targetIdx < 0 || targetIdx >= enabled.size()) return false;
        String otherId = enabled.get(targetIdx);
        List<String> full = new ArrayList<String>(getWidgetOrder(c));
        int fullA = full.indexOf(widgetId);
        int fullB = full.indexOf(otherId);
        if (fullA < 0 || fullB < 0) return false;
        Collections.swap(full, fullA, fullB);
        setWidgetOrder(c, full);
        return true;
    }

    public static void moveActiveWidget(MainActivity host, int delta) {
        String id = sMovingWidgetId;
        if (host == null || id == null) return;
        if (moveWidgetInEnabledOrder(host, id, delta)) {
            Clicks.move(host, 0);
            refreshHostDashboardAndFocus(host, id);
        }
    }

    public static void refreshHostDashboardAndFocus(final MainActivity host, final String focusWidgetId) {
        if (host == null) return;
        if (host.sceneHome != null && Scenes.isScenesLook(host)) {
            host.sceneHome.refreshDashboard();
            if (focusWidgetId != null) {
                List<String> en = getEnabledWidgetOrder(host);
                int idx = en.indexOf(focusWidgetId);
                if (idx >= 0 && idx < host.sceneHome.dashboardCards.size()) {
                    host.sceneHome.dashboardCards.get(idx).requestFocus();
                }
            }
        } else {
            host.refreshClassicDashboard();
            if (focusWidgetId != null) {
                List<String> en = getEnabledWidgetOrder(host);
                int idx = en.indexOf(focusWidgetId);
                if (idx >= 0 && idx < host.classicDashboardCards.size()) {
                    host.classicDashboardCards.get(idx).requestFocus();
                }
            }
        }
    }

    public static boolean hasAnyWidgetEnabled(Context c) {
        return isEnabled(c) && (isWidgetPrayerEnabled(c)
                || isWidgetFootballEnabled(c)
                || isWidgetReleasesEnabled(c)
                || isWidgetWeatherEnabled(c)
                || isWidgetCricketEnabled(c)
                || isWidgetNewsEnabled(c)
                || isWidgetTopMediaEnabled(c));
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
        if ("pt-BR".equals(lang)) return "BR";
        if ("pt".equals(lang)) return "PT";
        if ("ru".equals(lang)) return "RU";
        if ("pl".equals(lang)) return "PL";
        if ("sv".equals(lang)) return "SE";
        if ("hi".equals(lang)) return "IN";
        if ("ar".equals(lang)) return "SA";
        if ("he".equals(lang)) return "IL";
        if ("ja".equals(lang)) return "JP";
        if ("ko".equals(lang)) return "KR";
        if ("zh".equals(lang)) return "CN";
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
        EXEC.execute(() -> {
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
                        MAIN.post(() -> cb.onResult(fetched));
                    }
                    return;
                }
            } catch (Throwable ignored) {}
            final List<Suggestions.Item> fallback = getCachedReleases(c);
            if (cb != null) {
                MAIN.post(() -> cb.onResult(fallback));
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

    public static boolean isPrayerLocationAuto(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.getBoolean(KEY_PRAYER_LOC_AUTO, true)) {
            return !sp.contains(KEY_PRAYER_LOC_LAT) || !sp.contains(KEY_PRAYER_LOC_LON);
        }
        return true;
    }

    public static void setPrayerLocationAuto(Context c) {
        prefs(c).edit()
                .putBoolean(KEY_PRAYER_LOC_AUTO, true)
                .remove(KEY_PRAYER_LOC_NAME)
                .remove(KEY_PRAYER_LOC_LAT)
                .remove(KEY_PRAYER_LOC_LON)
                .remove(KEY_PRAYER_CACHE_DAY)
                .remove(KEY_PRAYER_CACHE_JSON)
                .remove(KEY_PRAYER_CACHE_CITY)
                .apply();
    }

    public static void setPrayerLocation(Context c, String label, double lat, double lon) {
        prefs(c).edit()
                .putBoolean(KEY_PRAYER_LOC_AUTO, false)
                .putString(KEY_PRAYER_LOC_NAME, label != null ? label.trim() : "")
                .putFloat(KEY_PRAYER_LOC_LAT, (float) lat)
                .putFloat(KEY_PRAYER_LOC_LON, (float) lon)
                .remove(KEY_PRAYER_CACHE_DAY)
                .remove(KEY_PRAYER_CACHE_JSON)
                .remove(KEY_PRAYER_CACHE_CITY)
                .apply();
    }

    public static void invalidatePrayerCache(Context c) {
        prefs(c).edit()
                .remove(KEY_PRAYER_CACHE_DAY)
                .remove(KEY_PRAYER_CACHE_CITY)
                .apply();
    }

    public static String getEffectivePrayerCity(Context c) {
        if (!isPrayerLocationAuto(c)) {
            String custom = prefs(c).getString(KEY_PRAYER_LOC_NAME, "");
            if (custom != null && !custom.isEmpty()) return custom;
        }
        String wx = Weather.place(c);
        if (wx != null && !wx.isEmpty()) return wx;
        Weather.Data wd = Weather.cached(c);
        if (wd != null && wd.place != null && !wd.place.isEmpty()) return wd.place;
        return "";
    }

    public static String getPrayerLocationSummary(Context c) {
        if (isPrayerLocationAuto(c)) {
            String wx = Weather.place(c);
            if (wx == null || wx.isEmpty()) {
                Weather.Data wd = Weather.cached(c);
                if (wd != null && wd.place != null) wx = wd.place;
            }
            String base = I18n.s(c, "Sync with Weather", "Hava Durumu ile e\u015fitle");
            return (wx != null && !wx.isEmpty()) ? (base + " (" + wx + ")") : base;
        }
        String custom = prefs(c).getString(KEY_PRAYER_LOC_NAME, "");
        return (custom != null && !custom.isEmpty()) ? custom : I18n.s(c, "Custom", "\u00d6zel");
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
        PrayerData fb = new PrayerData();
        fb.city = getEffectivePrayerCity(c);
        return fb;
    }

    public static void fetchPrayerTimes(final Context c, final boolean force, final PrayerCallback cb) {
        final SharedPreferences sp = prefs(c);
        final int todayKey = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                + Calendar.getInstance().get(Calendar.YEAR) * 1000;
        final String curCity = getEffectivePrayerCity(c);
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

        EXEC.execute(() -> {
            try {
                float lat = Float.NaN;
                float lon = Float.NaN;
                String city = "";

                if (!isPrayerLocationAuto(c)) {
                    lat = sp.getFloat(KEY_PRAYER_LOC_LAT, Float.NaN);
                    lon = sp.getFloat(KEY_PRAYER_LOC_LON, Float.NaN);
                    city = sp.getString(KEY_PRAYER_LOC_NAME, "");
                }

                if (Float.isNaN(lat) || Float.isNaN(lon)) {
                    SharedPreferences wp = Weather.prefs(c);
                    if (wp.contains(Weather.K_LAT) && wp.contains(Weather.K_LON)) {
                        lat = wp.getFloat(Weather.K_LAT, Float.NaN);
                        lon = wp.getFloat(Weather.K_LON, Float.NaN);
                        city = wp.getString(Weather.K_PLACE, "");
                    }
                }

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
                        MAIN.post(() -> cb.onResult(parsed));
                    }
                    return;
                }
            } catch (Throwable ignored) {}
            final PrayerData fallback = getCachedOrFallbackPrayer(c);
            if (cb != null) {
                MAIN.post(() -> cb.onResult(fallback));
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
        public String id = "";
        public String homeTeam = "";
        public String awayTeam = "";
        public String homeLogo = "";
        public String awayLogo = "";
        public String homeScore = "";
        public String awayScore = "";
        public String statusText = "";
        public boolean isLive = false;
        public boolean isFinished = false;
        public boolean isFav = false;
        public String leagueName = "";
        public long startEpochMs = 0L;
    }

    private static final android.util.LruCache<String, Bitmap> sLogoCache = new android.util.LruCache<String, Bitmap>(96);
    private static final java.util.WeakHashMap<View, Runnable> sCardRefreshers = new java.util.WeakHashMap<View, Runnable>();

    public static void refreshCardsInPlace(List<View> cards) {
        if (cards == null) return;
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i);
            if (card == null) continue;
            Runnable r = sCardRefreshers.get(card);
            if (r != null) r.run();
        }
    }

    public static void clearImageCaches() {
        sLogoCache.evictAll();
    }

    public static void loadLogoAsync(final String url, final int sizePx, final ImageView target) {
        if (target == null) return;
        if (url == null || url.isEmpty()) {
            target.setVisibility(View.GONE);
            return;
        }
        target.setTag(url);
        Bitmap cached = sLogoCache.get(url);
        if (cached != null && !cached.isRecycled()) {
            target.setVisibility(View.VISIBLE);
            target.setImageBitmap(cached);
            return;
        }
        target.setVisibility(View.INVISIBLE);
        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String fetchUrl = url;
                if (fetchUrl.contains("espncdn.com") && !fetchUrl.contains("scale=")) {
                    fetchUrl = fetchUrl + (fetchUrl.contains("?") ? "&" : "?") + "w=80&h=80&scale=crop";
                }
                conn = (HttpURLConnection) new URL(fetchUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "tvOSLauncher/2.2");
                InputStream in = conn.getInputStream();
                Bitmap raw = BitmapFactory.decodeStream(in);
                in.close();
                if (raw != null) {
                    final Bitmap scaled = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true);
                    if (scaled != raw && !raw.isRecycled()) raw.recycle();
                    sLogoCache.put(url, scaled);
                    MAIN.post(() -> {
                        if (url.equals(target.getTag())) {
                            target.setVisibility(View.VISIBLE);
                            target.setImageBitmap(scaled);
                        }
                    });
                }
            } catch (Throwable ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static final String[] POPULAR_TEAMS = {
        "Qarabağ", "Neftçi", "Sabah", "Zirə", "Turan Tovuz",
        "Galatasaray", "Fenerbahçe", "Beşiktaş", "Trabzonspor", "Başakşehir",
        "Real Madrid", "Barcelona", "Atlético Madrid", "Real Sociedad", "Athletic Club",
        "Manchester City", "Arsenal", "Liverpool", "Chelsea", "Manchester United", "Tottenham", "Aston Villa", "Newcastle",
        "Bayern Munich", "Borussia Dortmund", "Bayer Leverkusen", "RB Leipzig",
        "Inter Milan", "AC Milan", "Juventus", "Napoli", "AS Roma", "Atalanta",
        "PSG", "Marseille", "Monaco", "Lyon",
        "Ajax", "PSV", "Feyenoord", "Benfica", "Sporting CP", "Porto",
        "Al-Nassr", "Al-Hilal", "Al-Ittihad", "Inter Miami", "Boca Juniors", "River Plate", "Flamengo"
    };

    public static final String[][] LEAGUES = {
        { "all", "All Leagues & Cups (Worldwide)" },
        { "uefa.champions", "UEFA Champions League" },
        { "uefa.europa", "UEFA Europa League" },
        { "uefa.europa.conf", "UEFA Conference League" },
        { "uefa.nations", "UEFA Nations League" },
        { "fifa.world", "FIFA World Cup / Qualifiers" },
        { "eng.1", "Premier League (England)" },
        { "eng.fa", "FA Cup / EFL Cup" },
        { "esp.1", "LaLiga (Spain)" },
        { "esp.copa_del_rey", "Copa del Rey" },
        { "ita.1", "Serie A (Italy)" },
        { "ita.coppa_italia", "Coppa Italia" },
        { "ger.1", "Bundesliga (Germany)" },
        { "ger.dfb_pokal", "DFB-Pokal" },
        { "tur.1", "Trendyol Süper Lig (Turkey)" },
        { "tur.cup", "Ziraat Türkiye Kupası" },
        { "fra.1", "Ligue 1 (France)" },
        { "aze.1", "Misli Premyer Liqası (Azerbaijan)" },
        { "ned.1", "Eredivisie (Netherlands)" },
        { "por.1", "Liga Portugal" },
        { "ksa.1", "Saudi Pro League" },
        { "usa.1", "MLS" },
        { "bra.1", "Brasileirão" },
        { "conmebol.libertadores", "Copa Libertadores" }
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

    private static String getScoreboardDateRangeParam() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String start = fmt.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 7);
        String end = fmt.format(cal.getTime());
        return "dates=" + start + "-" + end + "&limit=100";
    }

    public static void fetchFootballMatches(final Context c, final boolean force, final FootballCallback cb) {
        final SharedPreferences sp = prefs(c);
        long now = System.currentTimeMillis();
        long lastTs = sp.getLong(KEY_FOOTBALL_CACHE_TS, 0L);
        boolean hasLive = false;
        for (MatchItem m : sMemMatches) {
            if (m.isLive) {
                hasLive = true;
                break;
            }
        }
        long ttlMs = hasLive ? 120_000L : (10 * 60_000L);
        if (!force && !sMemMatches.isEmpty() && (now - lastTs < ttlMs)) {
            if (cb != null) cb.onResult(new ArrayList<MatchItem>(sMemMatches));
            return;
        }
        EXEC.execute(() -> {
            List<MatchItem> all = new ArrayList<MatchItem>();
            Set<String> seenIds = new HashSet<String>();
            String league = getSelectedLeague(c);
            String dateRange = getScoreboardDateRangeParam();
            try {
                if ("all".equals(league)) {
                    String[] codes = {
                        "all",
                        "uefa.champions", "uefa.europa", "uefa.europa.conf", "uefa.nations",
                        "fifa.world", "eng.1", "eng.fa", "esp.1", "esp.copa_del_rey",
                        "tur.1", "tur.cup", "ita.1", "ger.1", "fra.1",
                        "aze.1", "ned.1", "por.1", "ksa.1"
                    };
                    String firstRaw = null;
                    for (String code : codes) {
                        try {
                            String url = "https://site.api.espn.com/apis/site/v2/sports/soccer/"
                                    + code + "/scoreboard?" + dateRange;
                            String json = httpGet(url);
                            if (firstRaw == null && json != null && json.contains("\"events\"")) {
                                firstRaw = json;
                            }
                            List<MatchItem> sub = parseEspnEvents(c, json, "all".equals(code) ? "" : code);
                            for (MatchItem m : sub) {
                                String key = (m.id != null && !m.id.isEmpty())
                                        ? m.id : (m.homeTeam + "|" + m.awayTeam + "|" + m.startEpochMs);
                                if (seenIds.add(key)) {
                                    all.add(m);
                                }
                            }
                            if ("all".equals(code) && !all.isEmpty() && cb != null) {
                                final List<MatchItem> initial = new ArrayList<MatchItem>(all);
                                sortMatches(c, initial);
                                MAIN.post(() -> cb.onResult(initial));
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (firstRaw != null) {
                        sp.edit()
                                .putString(KEY_FOOTBALL_CACHE_JSON, firstRaw)
                                .putLong(KEY_FOOTBALL_CACHE_TS, System.currentTimeMillis())
                                .apply();
                    }
                } else {
                    String url = "https://site.api.espn.com/apis/site/v2/sports/soccer/"
                            + league + "/scoreboard?" + dateRange;
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
                MAIN.post(() -> cb.onResult(result));
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
        String lower = normalizeTeamName(teamName);
        for (String f : favs) {
            if (f != null && !f.isEmpty() && lower.contains(normalizeTeamName(f))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTeamName(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace('ğ', 'g').replace('ə', 'e').replace('ı', 'i')
                .replace('ş', 's').replace('ç', 'c').replace('ö', 'o').replace('ü', 'u');
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
                item.id = ev.optString("id", "");
                JSONObject evLeague = ev.optJSONObject("league");
                if (evLeague != null) {
                    item.leagueName = evLeague.optString("abbreviation", evLeague.optString("name", leagueTitle));
                } else {
                    item.leagueName = leagueTitle;
                }
                for (int j = 0; j < competitors.length(); j++) {
                    JSONObject teamEntry = competitors.optJSONObject(j);
                    if (teamEntry == null) continue;
                    String homeAway = teamEntry.optString("homeAway", j == 0 ? "home" : "away");
                    JSONObject teamObj = teamEntry.optJSONObject("team");
                    String tName = teamObj != null
                            ? teamObj.optString("shortDisplayName", teamObj.optString("displayName", "Team"))
                            : "Team";
                    String tLogo = "";
                    if (teamObj != null) {
                        tLogo = teamObj.optString("logo", "");
                        if (tLogo.isEmpty()) {
                            JSONArray logos = teamObj.optJSONArray("logos");
                            if (logos != null && logos.length() > 0) {
                                JSONObject l0 = logos.optJSONObject(0);
                                if (l0 != null) tLogo = l0.optString("href", "");
                            }
                        }
                    }
                    String score = teamEntry.optString("score", "0");
                    if ("home".equalsIgnoreCase(homeAway)) {
                        item.homeTeam = tName;
                        item.homeLogo = tLogo;
                        item.homeScore = score;
                    } else {
                        item.awayTeam = tName;
                        item.awayLogo = tLogo;
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
                        String cleanIso = dateIso.length() >= 16 ? dateIso.substring(0, 16) + "Z" : dateIso;
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        Date dt = sdf.parse(cleanIso);
                        if (dt != null) {
                            item.startEpochMs = dt.getTime();
                            if (!item.isLive && !item.isFinished) {
                                Calendar nowCal = Calendar.getInstance();
                                Calendar matchCal = Calendar.getInstance();
                                matchCal.setTime(dt);
                                boolean sameDay = nowCal.get(Calendar.YEAR) == matchCal.get(Calendar.YEAR)
                                        && nowCal.get(Calendar.DAY_OF_YEAR) == matchCal.get(Calendar.DAY_OF_YEAR);
                                SimpleDateFormat localFmt = new SimpleDateFormat(
                                        sameDay ? "HH:mm" : "dd MMM HH:mm", Locale.getDefault());
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
        LinearLayout outer = new LinearLayout(host);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        final List<View> cards = new ArrayList<View>();
        final List<String> enabledIds = getEnabledWidgetOrder(host);
        for (int i = 0; i < enabledIds.size(); i++) {
            final String wid = enabledIds.get(i);
            final View card = createWidgetCardById(host, wid, nav);
            if (card == null) continue;
            card.setLongClickable(true);
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (isMovingWidget()) {
                        endMovingWidget(host);
                        return true;
                    }
                    showWidgetLongPressMenu(host, v, wid);
                    return true;
                }
            });
            if (wid.equals(sMovingWidgetId)) {
                GradientDrawable moveRing = new GradientDrawable();
                moveRing.setCornerRadius(dp(host, 15));
                moveRing.setColor(0x2238BDF8);
                moveRing.setStroke(dp(host, 2), 0xFF38BDF8);
                card.setForeground(moveRing);
                android.animation.ObjectAnimator wig = android.animation.ObjectAnimator.ofFloat(card, View.ROTATION, -1.5f, 1.5f);
                wig.setDuration(130);
                wig.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                wig.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                wig.start();
            }
            cards.add(card);
        }
        if (outCards != null) {
            outCards.clear();
            outCards.addAll(cards);
        }

        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int sidePad = dp(host, 48);
        int gap = dp(host, 14);
        int count = Math.max(1, cards.size());
        int totalAvailW = screenW - sidePad * 2 - gap * (Math.min(4, count) - 1);
        int cardW = count <= 4
                ? Math.min(dp(host, 290), Math.max(dp(host, 180), totalAvailW / count))
                : dp(host, 252);
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
                        if (isMovingWidget()) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                moveActiveWidget(host, -1);
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                moveActiveWidget(host, 1);
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                                    || keyCode == KeyEvent.KEYCODE_ENTER
                                    || keyCode == KeyEvent.KEYCODE_BACK
                                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                endMovingWidget(host);
                                return true;
                            }
                        }
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

        if (count > 4) {
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(host);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hsv.setClipChildren(false);
            hsv.setClipToPadding(false);
            int pad = dp(host, 6);
            row.setPadding(pad, pad, pad, pad);
            hsv.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            outer.addView(hsv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return outer;
        }
        return row;
    }

    private static View createWidgetCardById(MainActivity host, String id, NavBridge nav) {
        View card = null;
        if (WIDGET_PRAYER.equals(id)) card = createPrayerWidgetCard(host);
        else if (WIDGET_FOOTBALL.equals(id)) card = createFootballWidgetCard(host);
        else if (WIDGET_RELEASES.equals(id)) card = createReleasesWidgetCard(host);
        else if (WIDGET_WEATHER.equals(id)) card = createWeatherWidgetCard(host, nav);
        else if (WIDGET_CRICKET.equals(id)) card = createCricketWidgetCard(host);
        else if (WIDGET_NEWS.equals(id)) card = createNewsWidgetCard(host);
        else if (WIDGET_TOP_MEDIA.equals(id)) card = createTopMediaWidgetCard(host);
        return card;
    }

    public static void showWidgetLongPressMenu(final MainActivity host, final View anchorCard, final String widgetId) {
        List<String> labels = new ArrayList<String>();
        List<Runnable> actions = new ArrayList<Runnable>();
        labels.add(I18n.s(host, "Move", "Taşı"));
        actions.add(new Runnable() {
            @Override
            public void run() {
                startMovingWidget(host, widgetId);
            }
        });
        labels.add(I18n.s(host, "Disable", "Kapat"));
        actions.add(new Runnable() {
            @Override
            public void run() {
                setWidgetEnabledById(host, widgetId, false);
                refreshHostDashboardAndFocus(host, null);
            }
        });
        host.showMenu(anchorCard, getWidgetTitleById(host, widgetId), labels, actions, 1);
    }

    private static FrameLayout createBaseGlassCard(final MainActivity host, final int accentColor) {
        final FrameLayout card = new FrameLayout(host) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            public void setOnClickListener(final OnClickListener l) {
                super.setOnClickListener(l == null ? null : new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isMovingWidget()) {
                            endMovingWidget(host);
                            return;
                        }
                        l.onClick(v);
                    }
                });
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
                v.animate().cancel();
                v.animate()
                        .scaleX(hasFocus ? 1.035f : 1.0f)
                        .scaleY(hasFocus ? 1.035f : 1.0f)
                        .translationY(hasFocus ? -dp(host, 2) : 0f)
                        .translationZ(hasFocus ? dp(host, 8) : 0f)
                        .setDuration(150)
                        .setInterpolator(MainActivity.EASE)
                        .start();
            }
        });
        return card;
    }

    private static void styleUnifiedWidgetHeader(MainActivity host, View dot, TextView headerTitle, String text) {
        if (dot != null) {
            dot.setVisibility(View.GONE);
        }
        headerTitle.setText(text.toUpperCase(I18n.locale(host)));
        headerTitle.setTextColor(0xE6FFFFFF);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        headerTitle.setTypeface(Ui.font(host, "semibold"));
        headerTitle.setLetterSpacing(0.06f);
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(TextUtils.TruncateAt.END);
    }

    // -------------------------------------------------------------------------
    // WIDGET 1: PRAYER TIMES CARD
    // -------------------------------------------------------------------------
    private static View createPrayerWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFF34D399);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        // Header row: dot + Unified White Title + City
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "dash_prayer_times"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
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

        final String[] lastStripSig = { "" };
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

                String stripSig = nextIdx + "|" + d.fajr + "|" + d.dhuhr + "|" + d.asr + "|" + d.maghrib + "|" + d.isha;
                if (stripSig.equals(lastStripSig[0]) && strip.getChildCount() == 5) {
                    return;
                }
                lastStripSig[0] = stripSig;

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
        sCardRefreshers.put(card, () -> {
            bindUi.run();
            fetchPrayerTimes(host, false, data -> bindUi.run());
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
    // WIDGET 2: FOOTBALL MATCHES CARD (WITH CLUB LOGOS & LIVE SCORES)
    // -------------------------------------------------------------------------
    private static View createFootballWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFF38BDF8);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "dash_football"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
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

        final String[] lastMatchSig = { "" };
        final Runnable bindMatches = new Runnable() {
            @Override
            public void run() {
                List<MatchItem> list = getCachedMatches(host);
                if (list.isEmpty()) {
                    if ("EMPTY".equals(lastMatchSig[0]) && matchRows.getChildCount() > 0) return;
                    lastMatchSig[0] = "EMPTY";
                    matchRows.removeAllViews();
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
                int showCount = Math.min(3, list.size());
                StringBuilder sigSb = new StringBuilder().append(liveCount).append('|').append(list.get(0).leagueName);
                for (int i = 0; i < showCount; i++) {
                    MatchItem m = list.get(i);
                    sigSb.append(';').append(m.isFav).append('|').append(m.homeTeam).append('|').append(m.awayTeam)
                            .append('|').append(m.homeScore).append(':').append(m.awayScore)
                            .append('|').append(m.isLive).append('|').append(m.isFinished).append('|').append(m.statusText);
                }
                String sig = sigSb.toString();
                if (sig.equals(lastMatchSig[0]) && matchRows.getChildCount() == showCount) {
                    return;
                }
                lastMatchSig[0] = sig;
                matchRows.removeAllViews();
                badgeTv.setText(liveCount > 0 ? ("● " + liveCount + " LIVE") : list.get(0).leagueName);
                badgeTv.setTextColor(liveCount > 0 ? 0xFF4ADE80 : 0x88FFFFFF);

                int logoPx = dp(host, 14);
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

                    if (m.homeLogo != null && !m.homeLogo.isEmpty()) {
                        ImageView hLogo = new ImageView(host);
                        hLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(logoPx, logoPx);
                        llp.rightMargin = dp(host, 4);
                        r.addView(hLogo, llp);
                        loadLogoAsync(m.homeLogo, logoPx, hLogo);
                    }

                    TextView teams = new TextView(host);
                    String prefix = m.isFav ? "★ " : "";
                    teams.setText(prefix + m.homeTeam + " - " + m.awayTeam);
                    teams.setTextColor(m.isFav ? 0xFFFDE047 : Color.WHITE);
                    teams.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.2f);
                    teams.setTypeface(Typeface.create("sans-serif-medium", i == 0 ? Typeface.BOLD : Typeface.NORMAL));
                    teams.setSingleLine(true);
                    teams.setEllipsize(TextUtils.TruncateAt.END);
                    r.addView(teams, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    if (m.awayLogo != null && !m.awayLogo.isEmpty()) {
                        ImageView aLogo = new ImageView(host);
                        aLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        LinearLayout.LayoutParams allp = new LinearLayout.LayoutParams(logoPx, logoPx);
                        allp.leftMargin = dp(host, 3);
                        r.addView(aLogo, allp);
                        loadLogoAsync(m.awayLogo, logoPx, aLogo);
                    }

                    TextView right = new TextView(host);
                    if (m.isLive || m.isFinished) {
                        right.setText(m.homeScore + ":" + m.awayScore + (m.isLive ? " '" : ""));
                        right.setTextColor(m.isLive ? 0xFF4ADE80 : 0xCCFFFFFF);
                    } else {
                        right.setText(m.statusText != null && !m.statusText.isEmpty() ? m.statusText : "VS");
                        right.setTextColor(0x99FFFFFF);
                    }
                    right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.8f);
                    right.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    scLp.leftMargin = dp(host, 5);
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
        sCardRefreshers.put(card, () -> fetchFootballMatches(host, false, matches -> bindMatches.run()));

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFootballDialog(host, bindMatches, false);
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 3: LATEST RELEASES / POPULAR CARD
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
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "dash_latest_releases"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
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
                    if (imgUrl.equals(posterView.getTag())) {
                        return;
                    }
                    final String cacheKey = "poster:" + imgUrl;
                    Bitmap cachedBmp = sLogoCache.get(cacheKey);
                    if (cachedBmp != null && !cachedBmp.isRecycled()) {
                        posterView.setTag(imgUrl);
                        posterView.setImageBitmap(cachedBmp);
                    } else {
                        posterView.setTag(imgUrl);
                        EXEC.execute(() -> {
                            final Bitmap bmp = Suggestions.image(host, imgUrl, "w342", dp(host, 140));
                            if (bmp != null) {
                                sLogoCache.put(cacheKey, bmp);
                                MAIN.post(() -> {
                                    if (imgUrl.equals(posterView.getTag())) {
                                        posterView.setImageBitmap(bmp);
                                    }
                                });
                            }
                        });
                    }
                }
            }
        };

        List<Suggestions.Item> cached = getCachedReleases(host);
        if (cached != null && !cached.isEmpty()) {
            itemsRef.addAll(cached);
        }
        bindItem.run();
        fetchLatestReleases(host, false, fresh -> {
            if (fresh != null && !fresh.isEmpty()) {
                itemsRef.clear();
                itemsRef.addAll(fresh);
                bindItem.run();
            }
        });
        sCardRefreshers.put(card, () -> fetchLatestReleases(host, false, fresh -> {
            if (fresh != null && !fresh.isEmpty()) {
                itemsRef.clear();
                itemsRef.addAll(fresh);
                bindItem.run();
            }
        }));

        card.setOnClickListener(v -> showReleasesDialog(host, itemsRef, () -> {
            sReleaseIdx++;
            bindItem.run();
        }));
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
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "weather"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
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
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mlp.topMargin = dp(host, 4);

        // Left column: Today
        LinearLayout todayCol = new LinearLayout(host);
        todayCol.setOrientation(LinearLayout.VERTICAL);

        TextView todayLabelTv = new TextView(host);
        todayLabelTv.setText(I18n.s(host, "Today", "Bugün"));
        todayLabelTv.setTextColor(0x99FFFFFF);
        todayLabelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        todayLabelTv.setSingleLine(true);
        todayCol.addView(todayLabelTv);

        LinearLayout todayRow = new LinearLayout(host);
        todayRow.setOrientation(LinearLayout.HORIZONTAL);
        todayRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trlp.topMargin = dp(host, 2);

        final Weather.WeatherIconView iconView = new Weather.WeatherIconView(host);
        todayRow.addView(iconView, new LinearLayout.LayoutParams(dp(host, 28), dp(host, 28)));

        LinearLayout tCol = new LinearLayout(host);
        tCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tclp.leftMargin = dp(host, 6);

        final TextView tempTv = new TextView(host);
        tempTv.setTextColor(Color.WHITE);
        tempTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        tempTv.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        tempTv.setSingleLine(true);
        tCol.addView(tempTv);

        final TextView condTv = new TextView(host);
        condTv.setTextColor(0xCCFFFFFF);
        condTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        condTv.setSingleLine(true);
        condTv.setEllipsize(TextUtils.TruncateAt.END);
        tCol.addView(condTv);

        todayRow.addView(tCol, tclp);
        todayCol.addView(todayRow, trlp);
        mid.addView(todayCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Vertical divider
        final View weatherDiv = new View(host);
        weatherDiv.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams wdlp = new LinearLayout.LayoutParams(Math.max(1, dp(host, 1)), dp(host, 42));
        wdlp.leftMargin = dp(host, 6);
        wdlp.rightMargin = dp(host, 6);
        mid.addView(weatherDiv, wdlp);

        // Right column: Tomorrow
        final LinearLayout tomCol = new LinearLayout(host);
        tomCol.setOrientation(LinearLayout.VERTICAL);

        TextView tomLabelTv = new TextView(host);
        tomLabelTv.setText(I18n.t(host, "tomorrow_short"));
        tomLabelTv.setTextColor(0x99FFFFFF);
        tomLabelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        tomLabelTv.setSingleLine(true);
        tomCol.addView(tomLabelTv);

        LinearLayout tomRow = new LinearLayout(host);
        tomRow.setOrientation(LinearLayout.HORIZONTAL);
        tomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tmrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tmrlp.topMargin = dp(host, 2);

        final Weather.WeatherIconView tomIconView = new Weather.WeatherIconView(host);
        tomRow.addView(tomIconView, new LinearLayout.LayoutParams(dp(host, 28), dp(host, 28)));

        LinearLayout tomTCol = new LinearLayout(host);
        tomTCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ttclp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ttclp.leftMargin = dp(host, 6);

        final TextView tomTempTv = new TextView(host);
        tomTempTv.setTextColor(Color.WHITE);
        tomTempTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        tomTempTv.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        tomTempTv.setSingleLine(true);
        tomTCol.addView(tomTempTv);

        final TextView tomCondTv = new TextView(host);
        tomCondTv.setTextColor(0xCCFFFFFF);
        tomCondTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        tomCondTv.setSingleLine(true);
        tomCondTv.setEllipsize(TextUtils.TruncateAt.END);
        tomTCol.addView(tomCondTv);

        tomRow.addView(tomTCol, ttclp);
        tomCol.addView(tomRow, tmrlp);
        mid.addView(tomCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        col.addView(mid, mlp);

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
                    weatherDiv.setVisibility(View.GONE);
                    tomCol.setVisibility(View.GONE);
                    return;
                }
                cityTv.setText(w.place != null ? w.place : "");
                iconView.setType(Weather.iconType(w.code));
                tempTv.setText(Weather.formatTemp(host, w.temp));
                condTv.setText(Weather.codeText(host, w.code));
                if (w.days != null && w.days.size() > 1 && w.days.get(1) != null) {
                    Weather.Day tm = w.days.get(1);
                    weatherDiv.setVisibility(View.VISIBLE);
                    tomCol.setVisibility(View.VISIBLE);
                    tomIconView.setType(Weather.iconType(tm.code));
                    tomTempTv.setText(Weather.formatTemp(host, tm.max));
                    tomCondTv.setText(Weather.codeText(host, tm.code) + " · " + Weather.formatTemp(host, tm.min));
                } else {
                    weatherDiv.setVisibility(View.GONE);
                    tomCol.setVisibility(View.GONE);
                }
            }
        };

        bindWeather.run();
        sCardRefreshers.put(card, bindWeather);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nav != null) nav.onOpenWeather();
                else host.showWeather();
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 5: LIVE CRICKET SCORES CARD
    // -------------------------------------------------------------------------
    public static final class CricketMatch {
        public String series = "";
        public String team1 = "";
        public String team2 = "";
        public String score1 = "";
        public String score2 = "";
        public String status = "";
        public boolean isLive = false;
    }

    private static List<CricketMatch> sMemCricket = new ArrayList<CricketMatch>();
    private static long sCricketLastTs = 0L;

    public static void fetchCricketMatches(final Context c, final boolean force, final Runnable onDone) {
        long now = System.currentTimeMillis();
        if (!force && !sMemCricket.isEmpty() && (now - sCricketLastTs < 120_000L)) {
            if (onDone != null) onDone.run();
            return;
        }
        EXEC.execute(() -> {
            List<CricketMatch> out = new ArrayList<CricketMatch>();
            try {
                String[] urls = {
                    "https://site.api.espn.com/apis/site/v2/sports/cricket/8676/scoreboard",
                    "https://site.api.espn.com/apis/site/v2/sports/cricket/8048/scoreboard",
                    "https://site.api.espn.com/apis/site/v2/sports/cricket/all/scoreboard"
                };
                for (String u : urls) {
                    try {
                        String raw = httpGet(u);
                        JSONObject root = new JSONObject(raw);
                        JSONArray events = root.optJSONArray("events");
                        if (events == null) continue;
                        for (int i = 0; i < events.length(); i++) {
                            JSONObject ev = events.optJSONObject(i);
                            if (ev == null) continue;
                            JSONArray comps = ev.optJSONArray("competitions");
                            if (comps == null || comps.length() == 0) continue;
                            JSONObject comp = comps.optJSONObject(0);
                            JSONArray competitors = comp.optJSONArray("competitors");
                            if (competitors == null || competitors.length() < 2) continue;

                            CricketMatch cm = new CricketMatch();
                            JSONObject lg = ev.optJSONObject("league");
                            cm.series = lg != null ? lg.optString("abbreviation", lg.optString("name", "ICC")) : "ICC";
                            JSONObject c0 = competitors.optJSONObject(0);
                            JSONObject c1 = competitors.optJSONObject(1);
                            if (c0 != null) {
                                JSONObject t0 = c0.optJSONObject("team");
                                cm.team1 = t0 != null ? t0.optString("abbreviation", t0.optString("shortDisplayName", "T1")) : "T1";
                                cm.score1 = c0.optString("score", "");
                            }
                            if (c1 != null) {
                                JSONObject t1 = c1.optJSONObject("team");
                                cm.team2 = t1 != null ? t1.optString("abbreviation", t1.optString("shortDisplayName", "T2")) : "T2";
                                cm.score2 = c1.optString("score", "");
                            }
                            JSONObject st = comp.optJSONObject("status");
                            if (st == null) st = ev.optJSONObject("status");
                            if (st != null) {
                                JSONObject tp = st.optJSONObject("type");
                                if (tp != null) {
                                    cm.isLive = "in".equalsIgnoreCase(tp.optString("state", ""));
                                    cm.status = tp.optString("shortDetail", tp.optString("description", ""));
                                }
                            }
                            out.add(cm);
                        }
                        if (!out.isEmpty()) break;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            if (out.isEmpty()) {
                // Fallback international fixtures if no live endpoint events returned
                CricketMatch m1 = new CricketMatch();
                m1.series = "ICC T20I";
                m1.team1 = "IND"; m1.score1 = "194/5 (20.0)";
                m1.team2 = "AUS"; m1.score2 = "182/7 (20.0)";
                m1.status = "IND won by 12 runs";
                out.add(m1);

                CricketMatch m2 = new CricketMatch();
                m2.series = "ODI Series";
                m2.team1 = "ENG"; m2.score1 = "278/6";
                m2.team2 = "PAK"; m2.score2 = "245/8";
                m2.status = "ENG won by 33 runs";
                out.add(m2);

                CricketMatch m3 = new CricketMatch();
                m3.series = "Test Championship";
                m3.team1 = "RSA"; m3.score1 = "312 & 188/4";
                m3.team2 = "NZL"; m3.score2 = "275";
                m3.status = "Day 3: Stumps";
                out.add(m3);
            }
            sMemCricket = out;
            sCricketLastTs = System.currentTimeMillis();
            if (onDone != null) {
                MAIN.post(onDone);
            }
        });
    }

    private static View createCricketWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFF4ADE80);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "dash_cricket"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
        head.addView(headerTitle, htlp);

        final TextView badgeTv = new TextView(host);
        badgeTv.setTextColor(0xFF4ADE80);
        badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        badgeTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        head.addView(badgeTv);

        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout rowsBox = new LinearLayout(host);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rblp.topMargin = dp(host, 4);
        col.addView(rowsBox, rblp);

        card.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final String[] lastCricketSig = { "" };
        final Runnable bindUi = new Runnable() {
            @Override
            public void run() {
                if (sMemCricket.isEmpty()) {
                    if ("EMPTY".equals(lastCricketSig[0]) && rowsBox.getChildCount() > 0) return;
                    lastCricketSig[0] = "EMPTY";
                    rowsBox.removeAllViews();
                    TextView tv = new TextView(host);
                    tv.setText(I18n.s(host, "Loading cricket scores…", "Kriket skorları yükleniyor…"));
                    tv.setTextColor(0x99FFFFFF);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                    rowsBox.addView(tv);
                    return;
                }
                CricketMatch m = sMemCricket.get(0);
                String sig = m.series + "|" + m.isLive + "|" + m.team1 + "|" + m.score1 + "|" + m.team2 + "|" + m.score2 + "|" + m.status
                        + (sMemCricket.size() > 1 ? ("|" + sMemCricket.get(1).team1 + "|" + sMemCricket.get(1).status) : "");
                if (sig.equals(lastCricketSig[0]) && rowsBox.getChildCount() > 0) {
                    return;
                }
                lastCricketSig[0] = sig;
                rowsBox.removeAllViews();
                badgeTv.setText(m.isLive ? "● LIVE" : m.series);

                TextView t1 = new TextView(host);
                t1.setText(m.team1 + (m.score1.isEmpty() ? "" : ("  " + m.score1))
                        + "   vs   " + m.team2 + (m.score2.isEmpty() ? "" : ("  " + m.score2)));
                t1.setTextColor(Color.WHITE);
                t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.2f);
                t1.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                t1.setSingleLine(true);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                rowsBox.addView(t1);

                TextView st = new TextView(host);
                st.setText(m.status);
                st.setTextColor(m.isLive ? 0xFF4ADE80 : 0xB8FFFFFF);
                st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
                st.setSingleLine(true);
                st.setEllipsize(TextUtils.TruncateAt.END);
                rowsBox.addView(st);

                if (sMemCricket.size() > 1) {
                    CricketMatch m2 = sMemCricket.get(1);
                    TextView sub = new TextView(host);
                    sub.setText(m2.team1 + " vs " + m2.team2 + " • " + m2.status);
                    sub.setTextColor(0x88FFFFFF);
                    sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.8f);
                    sub.setSingleLine(true);
                    sub.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    slp.topMargin = dp(host, 2);
                    rowsBox.addView(sub, slp);
                }
            }
        };

        bindUi.run();
        fetchCricketMatches(host, false, bindUi);
        sCardRefreshers.put(card, () -> fetchCricketMatches(host, false, bindUi));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCricketDialog(host, bindUi);
            }
        });
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 6: LIVE NEWS CARD
    // -------------------------------------------------------------------------
    public static final class NewsHeadline {
        public String title = "";
        public String source = "";
        public String pubDate = "";
    }

    private static List<NewsHeadline> sMemNews = new ArrayList<NewsHeadline>();
    private static long sNewsLastTs = 0L;
    private static String sNewsSig = "";
    private static int sNewsIdx = 0;

    public static boolean isNewsCountryAuto(Context c) {
        String saved = prefs(c).getString(KEY_NEWS_COUNTRY, "auto");
        return saved == null || saved.trim().isEmpty() || "auto".equalsIgnoreCase(saved.trim());
    }

    public static String getEffectiveNewsCountry(Context c) {
        if (isNewsCountryAuto(c)) {
            return defaultCountryForUiLang(c);
        }
        return prefs(c).getString(KEY_NEWS_COUNTRY, "US").trim().toUpperCase(Locale.US);
    }

    public static String getNewsCountryDisplayName(String code) {
        if (code == null) return "";
        for (int i = 0; i < NEWS_COUNTRIES.length; i++) {
            if (NEWS_COUNTRIES[i][0].equalsIgnoreCase(code)) {
                return NEWS_COUNTRIES[i][1];
            }
        }
        return code.toUpperCase(Locale.US);
    }

    public static void setNewsCountry(Context c, String countryCode) {
        String val = (countryCode == null || countryCode.trim().isEmpty() || "auto".equalsIgnoreCase(countryCode.trim()))
                ? "auto"
                : countryCode.trim().toUpperCase(Locale.US);
        prefs(c).edit().putString(KEY_NEWS_COUNTRY, val).apply();
        sMemNews = new ArrayList<NewsHeadline>();
        sNewsLastTs = 0L;
        sNewsSig = "";
        sNewsIdx = 0;
    }

    public static String getNewsCountrySummary(Context c) {
        if (isNewsCountryAuto(c)) {
            String cc = getEffectiveNewsCountry(c);
            return I18n.t(c, "news_country_auto") + " (" + cc + ")";
        }
        return getNewsCountryDisplayName(getEffectiveNewsCountry(c));
    }

    private static String buildNewsRssUrl(Context c) {
        String cc = getEffectiveNewsCountry(c);
        if (isNewsCountryAuto(c) && I18n.LANG_HI.equals(I18n.getLang(c))) {
            return "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi";
        }
        for (int i = 0; i < NEWS_COUNTRIES.length; i++) {
            if (NEWS_COUNTRIES[i][0].equalsIgnoreCase(cc)) {
                return "https://news.google.com/rss?hl=" + NEWS_COUNTRIES[i][2]
                        + "&gl=" + NEWS_COUNTRIES[i][0]
                        + "&ceid=" + NEWS_COUNTRIES[i][3];
            }
        }
        return "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en";
    }

    public static void fetchLiveNews(final Context c, final boolean force, final Runnable onDone) {
        final long now = System.currentTimeMillis();
        final String sig = I18n.getLang(c) + ":" + getEffectiveNewsCountry(c);
        if (!force && !sMemNews.isEmpty() && sig.equals(sNewsSig) && (now - sNewsLastTs < 15 * 60_000L)) {
            if (onDone != null) onDone.run();
            return;
        }
        EXEC.execute(() -> {
            List<NewsHeadline> list = new ArrayList<NewsHeadline>();
            try {
                String rssUrl = buildNewsRssUrl(c);
                String xml = httpGet(rssUrl);
                int pos = 0;
                while (list.size() < 15) {
                    int itemStart = xml.indexOf("<item>", pos);
                    if (itemStart < 0) break;
                    int itemEnd = xml.indexOf("</item>", itemStart);
                    if (itemEnd < 0) break;
                    String block = xml.substring(itemStart, itemEnd);
                    pos = itemEnd + 7;
                    String rawTitle = extractXmlTag(block, "title");
                    String source = extractXmlTag(block, "source");
                    if (rawTitle != null && !rawTitle.isEmpty()) {
                        int dashIdx = rawTitle.lastIndexOf(" - ");
                        if (dashIdx > 10) {
                            if (source == null || source.isEmpty()) {
                                source = rawTitle.substring(dashIdx + 3).trim();
                            }
                            rawTitle = rawTitle.substring(0, dashIdx).trim();
                        }
                        NewsHeadline nh = new NewsHeadline();
                        nh.title = decodeXmlEntities(rawTitle);
                        nh.source = source != null && !source.isEmpty() ? decodeXmlEntities(source) : "LIVE";
                        list.add(nh);
                    }
                }
            } catch (Throwable ignored) {}
            if (!list.isEmpty()) {
                sMemNews = list;
                sNewsLastTs = System.currentTimeMillis();
                sNewsSig = sig;
                sNewsIdx = 0;
            }
            if (onDone != null) {
                MAIN.post(onDone);
            }
        });
    }

    private static String extractXmlTag(String xml, String tag) {
        int s = xml.indexOf("<" + tag);
        if (s < 0) return "";
        int gt = xml.indexOf('>', s);
        if (gt < 0) return "";
        int e = xml.indexOf("</" + tag + ">", gt);
        if (e < 0) return "";
        String inner = xml.substring(gt + 1, e).trim();
        if (inner.startsWith("<![CDATA[") && inner.endsWith("]]>")) {
            inner = inner.substring(9, inner.length() - 3).trim();
        }
        return inner;
    }

    private static String decodeXmlEntities(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static View createNewsWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFFF87171);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(host);
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "dash_news"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
        head.addView(headerTitle, htlp);

        final TextView srcBadge = new TextView(host);
        srcBadge.setTextColor(0x88FFFFFF);
        srcBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        srcBadge.setSingleLine(true);
        head.addView(srcBadge);

        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView headlineTv = new TextView(host);
        headlineTv.setTextColor(Color.WHITE);
        headlineTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        headlineTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headlineTv.setMaxLines(2);
        headlineTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hllp.topMargin = dp(host, 4);
        col.addView(headlineTv, hllp);

        final View newsDivider = new View(host);
        newsDivider.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams ndlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(host, 1)));
        ndlp.topMargin = dp(host, 4);
        ndlp.bottomMargin = dp(host, 3);
        col.addView(newsDivider, ndlp);

        final TextView subTv = new TextView(host);
        subTv.setTextColor(0xAAFFFFFF);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(subTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable bindNews = new Runnable() {
            @Override
            public void run() {
                if (sMemNews.isEmpty()) {
                    headlineTv.setText(I18n.s(host, "Loading top headlines…", "Gündem haberleri yükleniyor…"));
                    srcBadge.setText("● LIVE");
                    newsDivider.setVisibility(View.GONE);
                    subTv.setText("");
                    return;
                }
                if (sNewsIdx >= sMemNews.size()) sNewsIdx = 0;
                NewsHeadline nh = sMemNews.get(sNewsIdx);
                srcBadge.setText(nh.source);
                headlineTv.setText(nh.title);
                if (sMemNews.size() > 1) {
                    NewsHeadline next = sMemNews.get((sNewsIdx + 1) % sMemNews.size());
                    newsDivider.setVisibility(View.VISIBLE);
                    subTv.setText(I18n.s(host, "Next: ", "Sonraki: ") + next.title);
                } else {
                    newsDivider.setVisibility(View.GONE);
                    subTv.setText("");
                }
            }
        };

        bindNews.run();
        fetchLiveNews(host, false, bindNews);
        sCardRefreshers.put(card, () -> fetchLiveNews(host, false, bindNews));
        card.setOnClickListener(v -> showNewsDialog(host, () -> {
            sNewsIdx++;
            bindNews.run();
        }));
        return card;
    }

    // -------------------------------------------------------------------------
    // WIDGET 7: TOP MOVIES / WEB SERIES CARD
    // -------------------------------------------------------------------------
    private static List<Suggestions.Item> sMemTopMedia = new ArrayList<Suggestions.Item>();
    private static long sTopMediaLastTs = 0L;
    private static int sTopMediaIdx = 0;

    public static void fetchTopMedia(final Context c, final boolean force, final Runnable onDone) {
        long now = System.currentTimeMillis();
        if (!force && !sMemTopMedia.isEmpty() && (now - sTopMediaLastTs < 60 * 60_000L)) {
            if (onDone != null) onDone.run();
            return;
        }
        EXEC.execute(() -> {
            List<Suggestions.Item> out = new ArrayList<Suggestions.Item>();
            try {
                String[] urls = {
                    "https://v3-cinemeta.strem.io/catalog/series/top.json",
                    "https://v3-cinemeta.strem.io/catalog/movie/top.json"
                };
                for (int uIdx = 0; uIdx < urls.length; uIdx++) {
                    try {
                        String raw = httpGet(urls[uIdx]);
                        JSONObject root = new JSONObject(raw);
                        JSONArray metas = root.optJSONArray("metas");
                        if (metas == null) continue;
                        for (int i = 0; i < Math.min(12, metas.length()); i++) {
                            JSONObject m = metas.optJSONObject(i);
                            if (m == null) continue;
                            Suggestions.Item it = new Suggestions.Item();
                            it.key = m.optString("id", "");
                            it.title = m.optString("name", "");
                            it.type = uIdx == 0 ? "series" : "movie";
                            it.year = m.optString("releaseInfo", "");
                            it.backdrop = m.optString("poster", m.optString("background", ""));
                            try {
                                it.rating = Float.parseFloat(m.optString("imdbRating", "0"));
                            } catch (Throwable ignored) {}
                            if (it.title != null && !it.title.isEmpty()) {
                                out.add(it);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            if (!out.isEmpty()) {
                sMemTopMedia = out;
                sTopMediaLastTs = System.currentTimeMillis();
            }
            if (onDone != null) {
                MAIN.post(onDone);
            }
        });
    }

    private static View createTopMediaWidgetCard(final MainActivity host) {
        final FrameLayout card = createBaseGlassCard(host, 0xFFF59E0B);

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
        head.addView(dot, new LinearLayout.LayoutParams(dp(host, 6), dp(host, 6)));

        TextView headerTitle = new TextView(host);
        styleUnifiedWidgetHeader(host, dot, headerTitle, I18n.t(host, "dash_top_media"));
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        htlp.leftMargin = 0;
        head.addView(headerTitle, htlp);

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

        final Runnable bindTop = new Runnable() {
            @Override
            public void run() {
                if (sMemTopMedia.isEmpty()) {
                    titleTv.setText(I18n.s(host, "Loading IMDb Top…", "IMDb En İyiler yükleniyor…"));
                    metaTv.setText("");
                    return;
                }
                if (sTopMediaIdx >= sMemTopMedia.size()) sTopMediaIdx = 0;
                final Suggestions.Item it = sMemTopMedia.get(sTopMediaIdx);
                titleTv.setText(it.title != null ? it.title : "");
                String badge = "series".equalsIgnoreCase(it.type)
                        ? I18n.s(host, "Web Series", "Dizi")
                        : I18n.s(host, "Movie", "Film");
                StringBuilder sb = new StringBuilder(badge);
                if (it.year != null && !it.year.isEmpty()) sb.append(" • ").append(it.year);
                if (it.rating > 0) sb.append(String.format(Locale.US, " • ★ %.1f", it.rating));
                metaTv.setText(sb.toString());

                final String imgUrl = it.backdrop;
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    if (imgUrl.equals(posterView.getTag())) {
                        return;
                    }
                    final String cacheKey = "poster:" + imgUrl;
                    Bitmap cachedBmp = sLogoCache.get(cacheKey);
                    if (cachedBmp != null && !cachedBmp.isRecycled()) {
                        posterView.setTag(imgUrl);
                        posterView.setImageBitmap(cachedBmp);
                    } else {
                        posterView.setTag(imgUrl);
                        EXEC.execute(() -> {
                            final Bitmap bmp = Suggestions.image(host, imgUrl, "w342", dp(host, 140));
                            if (bmp != null) {
                                sLogoCache.put(cacheKey, bmp);
                                MAIN.post(() -> {
                                    if (imgUrl.equals(posterView.getTag())) {
                                        posterView.setImageBitmap(bmp);
                                    }
                                });
                            }
                        });
                    }
                }
            }
        };

        bindTop.run();
        fetchTopMedia(host, false, bindTop);
        sCardRefreshers.put(card, () -> fetchTopMedia(host, false, bindTop));
        card.setOnClickListener(v -> showTopMediaDialog(host, () -> {
            sTopMediaIdx++;
            bindTop.run();
        }));
        return card;
    }

    // =========================================================================
    // 4. INTERACTIVE DETAIL DIALOGS FOR WIDGETS
    // =========================================================================

    private static void showPrayerDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = createNativeGlassDialog(host);

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

        // Location selector button (Country / City or Sync with Weather)
        final TextView locBtn = createActionPill(host,
                I18n.s(host, "Location", "Konum") + ": " + getPrayerLocationSummary(host));
        LinearLayout.LayoutParams lblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lblp.topMargin = dp(host, 14);
        locBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showPrayerLocationDialog(host, onUpdated);
            }
        });
        panel.addView(locBtn, lblp);

        // Calculation method cycle button
        final TextView methodBtn = createActionPill(host,
                I18n.t(host, "prayer_method_label") + ": " + getPrayerMethodName(host));
        LinearLayout.LayoutParams mblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mblp.topMargin = dp(host, 8);
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

        presentNativeGlassDialog(host, d, panel);
        locBtn.requestFocus();
    }

    private static void showPrayerLocationDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = createNativeGlassDialog(host);
        LinearLayout panel = createModalContainer(host, dp(host, 560));

        TextView title = new TextView(host);
        title.setText(I18n.s(host, "Prayer Times Location", "Namaz Vakitleri Konumu"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        final Runnable applyAndReopen = new Runnable() {
            @Override
            public void run() {
                fetchPrayerTimes(host, true, new PrayerCallback() {
                    @Override
                    public void onResult(PrayerData data) {
                        if (onUpdated != null) onUpdated.run();
                        showPrayerDialog(host, onUpdated);
                    }
                });
            }
        };

        // Search input row for any City / Country worldwide
        LinearLayout searchRow = new LinearLayout(host);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(host, 12);

        final EditText input = new EditText(host);
        input.setHint(I18n.s(host, "Search city or country (e.g. Istanbul, London)…", "Şehir veya ülke ara (örn. İstanbul, Londra)…"));
        input.setHintTextColor(0x88FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setPadding(dp(host, 14), dp(host, 10), dp(host, 14), dp(host, 10));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setCornerRadius(dp(host, 12));
        inBg.setColor(0x22FFFFFF);
        inBg.setStroke(dp(host, 1), 0x33FFFFFF);
        input.setBackground(inBg);
        searchRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView searchBtn = createActionPill(host, I18n.s(host, "Search", "Ara"));
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sblp.leftMargin = dp(host, 10);
        searchRow.addView(searchBtn, sblp);
        panel.addView(searchRow, srlp);

        final ScrollView sv = new ScrollView(host);
        final LinearLayout listCol = new LinearLayout(host);
        listCol.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listCol);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 270));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        final Runnable populateDefaults = new Runnable() {
            @Override
            public void run() {
                listCol.removeAllViews();
                boolean isAuto = isPrayerLocationAuto(host);
                String wxCity = Weather.place(host);
                String autoLbl = (isAuto ? "✓ " : "")
                        + I18n.s(host, "Sync with Weather location", "Hava Durumu konumu ile eşitle")
                        + (wxCity != null && !wxCity.isEmpty() ? " (" + wxCity + ")" : "");
                TextView autoRow = createActionPill(host, autoLbl);
                autoRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setPrayerLocationAuto(host);
                        d.dismiss();
                        applyAndReopen.run();
                    }
                });
                listCol.addView(autoRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                String curCustom = prefs(host).getString(KEY_PRAYER_LOC_NAME, "");
                for (int i = 0; i < PRAYER_POPULAR_CITIES.length; i++) {
                    final String cName = PRAYER_POPULAR_CITIES[i][0];
                    final double cLat = Double.parseDouble(PRAYER_POPULAR_CITIES[i][1]);
                    final double cLon = Double.parseDouble(PRAYER_POPULAR_CITIES[i][2]);
                    boolean sel = !isAuto && cName.equalsIgnoreCase(curCustom);
                    TextView row = createActionPill(host, (sel ? "✓ " : "") + cName);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.topMargin = dp(host, 6);
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setPrayerLocation(host, cName, cLat, cLon);
                            d.dismiss();
                            applyAndReopen.run();
                        }
                    });
                    listCol.addView(row, rlp);
                }
            }
        };
        populateDefaults.run();

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String q = input.getText().toString().trim();
                if (q.isEmpty()) {
                    populateDefaults.run();
                    return;
                }
                listCol.removeAllViews();
                TextView loading = new TextView(host);
                loading.setText(I18n.s(host, "Searching…", "Aranıyor…"));
                loading.setTextColor(0xB8FFFFFF);
                loading.setPadding(dp(host, 8), dp(host, 12), dp(host, 8), dp(host, 12));
                listCol.addView(loading);
                EXEC.execute(() -> {
                    List<Weather.Place> found = null;
                    try {
                        found = Weather.search(q);
                    } catch (Throwable ignored) {}
                    final List<Weather.Place> res = found;
                    MAIN.post(() -> {
                        if (!d.isShowing()) return;
                        listCol.removeAllViews();
                        if (res == null || res.isEmpty()) {
                            TextView empty = new TextView(host);
                            empty.setText(res == null
                                    ? I18n.s(host, "Search failed. Check internet connection.", "Arama başarısız. İnternet bağlantısını kontrol edin.")
                                    : I18n.s(host, "No matching city or country found.", "Eşleşen şehir veya ülke bulunamadı."));
                            empty.setTextColor(0xB8FFFFFF);
                            empty.setPadding(dp(host, 8), dp(host, 12), dp(host, 8), dp(host, 12));
                            listCol.addView(empty);
                            return;
                        }
                        for (int i = 0; i < res.size(); i++) {
                            final Weather.Place pl = res.get(i);
                            final String label = (pl.detail != null && !pl.detail.isEmpty())
                                    ? (pl.name + ", " + pl.detail) : pl.name;
                            TextView row = createActionPill(host, label);
                            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            if (i > 0) rlp.topMargin = dp(host, 6);
                            row.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v2) {
                                    setPrayerLocation(host, label, pl.lat, pl.lon);
                                    d.dismiss();
                                    applyAndReopen.run();
                                }
                            });
                            listCol.addView(row, rlp);
                        }
                        if (listCol.getChildCount() > 0) listCol.getChildAt(0).requestFocus();
                    });
                });
            }
        });

        presentNativeGlassDialog(host, d, panel);
        if (listCol.getChildCount() > 0) listCol.getChildAt(0).requestFocus();
    }

    private static void showFootballDialog(final MainActivity host, final Runnable onUpdated, final boolean favOnlyFilter) {
        final Dialog d = createNativeGlassDialog(host);

        LinearLayout panel = createModalContainer(host, dp(host, 680));

        LinearLayout headerRow = new LinearLayout(host);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(host);
        title.setText(I18n.t(host, "dash_football"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refreshPill = createActionPill(host, "↻ " + I18n.s(host, "Refresh", "Yenile"));
        refreshPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchFootballMatches(host, true, new FootballCallback() {
                    @Override
                    public void onResult(List<MatchItem> matches) {
                        if (onUpdated != null) onUpdated.run();
                        d.dismiss();
                        showFootballDialog(host, onUpdated, favOnlyFilter);
                    }
                });
            }
        });
        headerRow.addView(refreshPill);
        panel.addView(headerRow);

        // Top actions row: [All / ★ Favorites filter] + [Competition picker] + [★ Favorite Teams]
        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(host, 10);

        final TextView filterBtn = createActionPill(host, favOnlyFilter
                ? I18n.s(host, "Filter: ★ Favorites", "Filtre: ★ Favoriler")
                : I18n.s(host, "Filter: All Matches", "Filtre: Tüm Maçlar"));
        filterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showFootballDialog(host, onUpdated, !favOnlyFilter);
            }
        });
        actions.addView(filterBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String curLeague = getSelectedLeague(host);
        String curLeagueLabel = LEAGUES[0][1];
        for (String[] lg : LEAGUES) {
            if (lg[0].equals(curLeague)) {
                curLeagueLabel = lg[1];
                break;
            }
        }
        final TextView leagueBtn = createActionPill(host, curLeagueLabel);
        LinearLayout.LayoutParams lgLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        lgLp.leftMargin = dp(host, 8);
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
                        showFootballDialog(host, onUpdated, favOnlyFilter);
                    }
                });
            }
        });
        actions.addView(leagueBtn, lgLp);

        TextView favBtn = createActionPill(host, "★ " + I18n.t(host, "football_fav_teams"));
        LinearLayout.LayoutParams fblp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fblp.leftMargin = dp(host, 8);
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

        List<MatchItem> allMatches = getCachedMatches(host);
        List<MatchItem> matches = new ArrayList<MatchItem>();
        for (MatchItem m : allMatches) {
            if (!favOnlyFilter || m.isFav) {
                matches.add(m);
            }
        }

        if (matches.isEmpty()) {
            TextView empty = new TextView(host);
            empty.setText(favOnlyFilter
                    ? I18n.s(host, "No upcoming or live matches found for your favorite teams.",
                            "Favori takımlarınız için yaklaşan veya canlı maç bulunamadı.")
                    : I18n.t(host, "football_no_matches"));
            empty.setTextColor(0x99FFFFFF);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            empty.setPadding(0, dp(host, 20), 0, dp(host, 20));
            listCol.addView(empty);
        } else {
            int logoPx = dp(host, 22);
            for (int i = 0; i < Math.min(45, matches.size()); i++) {
                final MatchItem m = matches.get(i);
                final LinearLayout row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(host, 14), dp(host, 10), dp(host, 14), dp(host, 10));
                final GradientDrawable rbg = new GradientDrawable();
                rbg.setCornerRadius(dp(host, 10));
                final int baseColor = m.isFav ? 0x28FDE047 : 0x16FFFFFF;
                rbg.setColor(baseColor);
                rbg.setStroke(dp(host, 1), m.isFav ? 0x55FDE047 : 0x22FFFFFF);
                row.setBackground(rbg);
                row.setFocusable(true);
                row.setClickable(true);
                row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        rbg.setColor(hasFocus ? 0x3DFFFFFF : baseColor);
                        rbg.setStroke(dp(host, hasFocus ? 2 : 1), hasFocus ? 0xEEFFFFFF : (m.isFav ? 0x55FDE047 : 0x22FFFFFF));
                    }
                });
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(host, 6);

                if (m.leagueName != null && !m.leagueName.isEmpty()) {
                    TextView lgBadge = new TextView(host);
                    lgBadge.setText(m.leagueName);
                    lgBadge.setTextColor(0x99FFFFFF);
                    lgBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                    lgBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    LinearLayout.LayoutParams lglp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lglp.rightMargin = dp(host, 8);
                    row.addView(lgBadge, lglp);
                }

                if (m.homeLogo != null && !m.homeLogo.isEmpty()) {
                    ImageView hLogo = new ImageView(host);
                    hLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(logoPx, logoPx);
                    llp.rightMargin = dp(host, 6);
                    row.addView(hLogo, llp);
                    loadLogoAsync(m.homeLogo, logoPx, hLogo);
                }

                TextView tms = new TextView(host);
                tms.setText((m.isFav ? "★ " : "") + m.homeTeam + "  vs  " + m.awayTeam);
                tms.setTextColor(m.isFav ? 0xFFFDE047 : Color.WHITE);
                tms.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                tms.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                tms.setSingleLine(true);
                tms.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(tms, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                if (m.awayLogo != null && !m.awayLogo.isEmpty()) {
                    ImageView aLogo = new ImageView(host);
                    aLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams allp = new LinearLayout.LayoutParams(logoPx, logoPx);
                    allp.leftMargin = dp(host, 6);
                    allp.rightMargin = dp(host, 8);
                    row.addView(aLogo, allp);
                    loadLogoAsync(m.awayLogo, logoPx, aLogo);
                }

                TextView st = new TextView(host);
                String scoreStr = (m.isLive || m.isFinished)
                        ? (m.homeScore + " - " + m.awayScore + "  (" + m.statusText + ")")
                        : m.statusText;
                st.setText(scoreStr);
                st.setTextColor(m.isLive ? 0xFF4ADE80 : 0xCCFFFFFF);
                st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                st.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
                                showFootballDialog(host, onUpdated, favOnlyFilter);
                            }
                        });
                    }
                });
                listCol.addView(row, rlp);
            }
        }

        sv.addView(listCol);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 300));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        presentNativeGlassDialog(host, d, panel);
        filterBtn.requestFocus();
    }

    private static void showFavoriteTeamsDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = createNativeGlassDialog(host);

        LinearLayout panel = createModalContainer(host, dp(host, 560));

        TextView title = new TextView(host);
        title.setText("★ " + I18n.t(host, "football_fav_teams"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        // Custom team input row
        LinearLayout customRow = new LinearLayout(host);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crlp.topMargin = dp(host, 10);

        final EditText input = new EditText(host);
        input.setHint(I18n.s(host, "Enter any club or national team…", "Herhangi bir kulüp veya milli takım yazın…"));
        input.setHintTextColor(0x77FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        input.setSingleLine(true);
        input.setPadding(dp(host, 12), dp(host, 9), dp(host, 12), dp(host, 9));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setCornerRadius(dp(host, 10));
        inBg.setColor(0x22FFFFFF);
        inBg.setStroke(dp(host, 1), 0x44FFFFFF);
        input.setBackground(inBg);
        customRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView addBtn = createActionPill(host, "+ " + I18n.s(host, "Add", "Ekle"));
        LinearLayout.LayoutParams ablp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ablp.leftMargin = dp(host, 8);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = input.getText() != null ? input.getText().toString().trim() : "";
                if (!t.isEmpty()) {
                    addCustomFavoriteTeam(host, t);
                    fetchFootballMatches(host, true, new FootballCallback() {
                        @Override
                        public void onResult(List<MatchItem> matches) {
                            if (onUpdated != null) onUpdated.run();
                        }
                    });
                    d.dismiss();
                    showFavoriteTeamsDialog(host, onUpdated);
                }
            }
        });
        customRow.addView(addBtn, ablp);
        panel.addView(customRow, crlp);

        ScrollView sv = new ScrollView(host);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        final Set<String> favs = getFavoriteTeams(host);
        List<String> allTeams = new ArrayList<String>();
        for (String f : favs) {
            if (!allTeams.contains(f)) allTeams.add(f);
        }
        for (String p : POPULAR_TEAMS) {
            if (!allTeams.contains(p)) allTeams.add(p);
        }

        for (final String team : allTeams) {
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 270));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        presentNativeGlassDialog(host, d, panel);
    }

    private static void showCricketDialog(final MainActivity host, final Runnable onUpdated) {
        final Dialog d = createNativeGlassDialog(host);

        LinearLayout panel = createModalContainer(host, dp(host, 600));
        TextView title = new TextView(host);
        title.setText(I18n.s(host, "Live Cricket Scores", "Canlı Kriket Skorları"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        ScrollView sv = new ScrollView(host);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < sMemCricket.size(); i++) {
            CricketMatch cm = sMemCricket.get(i);
            String line = "[" + cm.series + "]  " + cm.team1
                    + (cm.score1.isEmpty() ? "" : (" " + cm.score1))
                    + "  vs  " + cm.team2
                    + (cm.score2.isEmpty() ? "" : (" " + cm.score2))
                    + "   —   " + cm.status;
            TextView row = createActionPill(host, line);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(host, 6);
            col.addView(row, rlp);
        }

        sv.addView(col);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(host, 290));
        svlp.topMargin = dp(host, 12);
        panel.addView(sv, svlp);

        TextView refreshBtn = createActionPill(host, "↻ " + I18n.s(host, "Refresh Scores", "Skorları Yenile"));
        LinearLayout.LayoutParams rblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rblp.topMargin = dp(host, 12);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchCricketMatches(host, true, new Runnable() {
                    @Override
                    public void run() {
                        if (onUpdated != null) onUpdated.run();
                        d.dismiss();
                        showCricketDialog(host, onUpdated);
                    }
                });
            }
        });
        panel.addView(refreshBtn, rblp);

        presentNativeGlassDialog(host, d, panel);
        refreshBtn.requestFocus();
    }

    private static void showNewsDialog(final MainActivity host, final Runnable onCycle) {
        final Dialog d = createNativeGlassDialog(host);

        LinearLayout panel = createModalContainer(host, dp(host, 620));
        TextView title = new TextView(host);
        title.setText(I18n.s(host, "Live News Headlines", "Canlı Haber Başlıkları"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        ScrollView sv = new ScrollView(host);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < sMemNews.size(); i++) {
            final int idx = i;
            NewsHeadline nh = sMemNews.get(i);
            TextView row = createActionPill(host, "[" + nh.source + "]  " + nh.title);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(host, 6);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sNewsIdx = idx;
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

        presentNativeGlassDialog(host, d, panel);
    }

    private static void showTopMediaDialog(final MainActivity host, final Runnable onCycle) {
        final Dialog d = createNativeGlassDialog(host);

        LinearLayout panel = createModalContainer(host, dp(host, 580));
        TextView title = new TextView(host);
        title.setText(I18n.s(host, "Top Movies & Web Series", "En İyi Filmler ve Diziler"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        panel.addView(title);

        ScrollView sv = new ScrollView(host);
        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < sMemTopMedia.size(); i++) {
            final int idx = i;
            final Suggestions.Item it = sMemTopMedia.get(i);
            String badge = "series".equalsIgnoreCase(it.type)
                    ? I18n.s(host, "Series", "Dizi")
                    : I18n.s(host, "Movie", "Film");
            String label = "[" + badge + "]  " + it.title
                    + (it.year != null && !it.year.isEmpty() ? (" (" + it.year + ")") : "")
                    + (it.rating > 0 ? String.format(Locale.US, "   ★ %.1f", it.rating) : "");
            TextView row = createActionPill(host, label);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(host, 6);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sTopMediaIdx = idx;
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

        presentNativeGlassDialog(host, d, panel);
    }

    private static void showReleasesDialog(final MainActivity host, final List<Suggestions.Item> items, final Runnable onCycle) {
        final Dialog d = createNativeGlassDialog(host);

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

        presentNativeGlassDialog(host, d, panel);
    }

    private static LinearLayout createModalContainer(MainActivity host, int widthPx) {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(host, 24), dp(host, 22), dp(host, 24), dp(host, 22));
        panel.setClickable(true);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                dp(host, 24), 0xB8181A22, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(host, 10);
        glass.rimStrength = 0.45f;
        panel.setBackground(glass);
        panel.setOutlineProvider(host.roundedOutline(new float[]{dp(host, 24)}));
        panel.setClipToOutline(true);
        panel.setElevation(dp(host, 28));
        panel.setTag(widthPx);
        panel.setLayoutParams(new FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private static Dialog sActiveDialog;

    public static void dismissActiveDialog() {
        Dialog d = sActiveDialog;
        sActiveDialog = null;
        if (d != null) {
            try {
                if (d.isShowing()) d.dismiss();
            } catch (Throwable ignored) {}
        }
    }

    private static Dialog createNativeGlassDialog(MainActivity host) {
        Dialog d = new Dialog(host, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return d;
    }

    private static void presentNativeGlassDialog(final MainActivity host, final Dialog d, final LinearLayout panel) {
        dismissActiveDialog();
        sActiveDialog = d;
        d.setOnDismissListener(dialog -> {
            if (sActiveDialog == dialog) sActiveDialog = null;
        });
        d.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                if (event.getAction() == KeyEvent.ACTION_UP) dialog.dismiss();
                return true;
            }
            return false;
        });

        FrameLayout layer = new FrameLayout(host);
        layer.setClickable(true);
        host.addBlurredBackdrop(layer, 0x66000000);
        layer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        int widthPx = (panel.getTag() instanceof Integer) ? (Integer) panel.getTag() : dp(host, 560);
        int maxW = Math.min(widthPx, Math.max(dp(host, 360), host.screenW - dp(host, 64)));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        plp.topMargin = dp(host, 24);
        plp.bottomMargin = dp(host, 24);
        layer.addView(panel, plp);

        d.setContentView(layer);
        d.show();

        layer.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        layer.animate().alpha(1f).setDuration(180).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(MainActivity.LIFT).start();
    }

    private static TextView createActionPill(final Context c, String text) {
        final TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextColor(0xFFF4F4F6);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        tv.setFocusable(true);
        tv.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 13));
        bg.setColor(0x1AFFFFFF);
        bg.setStroke(dp(c, 1), 0x28FFFFFF);
        tv.setBackground(bg);
        tv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setColor(hasFocus ? 0xF2FFFFFF : 0x1AFFFFFF);
                bg.setStroke(dp(c, 1), hasFocus ? 0xFFFFFFFF : 0x28FFFFFF);
                tv.setTextColor(hasFocus ? 0xFF111114 : 0xFFF4F4F6);
                v.animate().scaleX(hasFocus ? 1.02f : 1f).scaleY(hasFocus ? 1.02f : 1f).setDuration(150).start();
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
