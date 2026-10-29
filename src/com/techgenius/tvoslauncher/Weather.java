package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Current weather and a 5-day forecast from free services that need no key: Open-Meteo,
 * MET Norway (Yr) and wttr.in. The chosen source is tried first and the others are fallbacks.
 * The location comes from the internet address (ipapi.co, ipwho.is, geojs.io) unless a city is
 * chosen in Settings. Every source is stored in one format, cached, and refreshed on a schedule
 * (every 15 minutes by default).
 */
final class Weather {
    static final String PREFS = "weather";
    static final String K_ENABLED = "enabled", K_UNITS = "units", K_PLACE = "place", K_LAT = "lat", K_LON = "lon",
            K_MANUAL = "manual", K_SOURCE = "source", K_REFRESH = "refresh_min",
            K_CACHE = "cache2", K_CACHE_AT = "cache_at", K_CACHE_KEY = "cache_key";
    static final String SRC_OPEN_METEO = "openmeteo", SRC_MET = "met", SRC_WTTR = "wttr";
    static final String[] SOURCES = {SRC_OPEN_METEO, SRC_MET, SRC_WTTR};
    static final int[] REFRESH_CHOICES = {10, 15, 30, 60};
    static final String UA = "tvOSLauncher/2.0 (Android TV launcher)";

    private Weather() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ENABLED, true);
    }

    /** "c" or "f"; automatic picks Fahrenheit for the US, Liberia and Myanmar. */
    static String units(Context c) {
        String u = prefs(c).getString(K_UNITS, "auto");
        if (!u.equals("auto")) return u;
        String cc = Locale.getDefault().getCountry();
        return cc.equals("US") || cc.equals("LR") || cc.equals("MM") ? "f" : "c";
    }

    static boolean manual(Context c) {
        return prefs(c).getBoolean(K_MANUAL, false);
    }

    static String place(Context c) {
        return prefs(c).getString(K_PLACE, "");
    }

    static String source(Context c) {
        return prefs(c).getString(K_SOURCE, SRC_OPEN_METEO);
    }

    static String sourceName(String s) {
        return s.equals(SRC_MET) ? "MET Norway" : s.equals(SRC_WTTR) ? "wttr.in" : "Open-Meteo";
    }

    /** Minutes between updates (15 by default). */
    static int refreshMinutes(Context c) {
        return prefs(c).getInt(K_REFRESH, 15);
    }

    /** Settings changed: the next refresh fetches again. */
    static void changed(Context c) {
        prefs(c).edit().remove(K_CACHE_AT).apply();
    }

    // ---------------------------------------------------------------- data

    static final class Hour {
        long time;
        int temp, code;
        boolean day;
        String label;
    }

    static final class Day {
        String date, label;
        int code, max, min, rain = -1;
    }

    static final class Data {
        String place = "", source = SRC_OPEN_METEO, windUnit = "km/h";
        long fetchedAt;
        int temp, feels, code, humidity, wind, max, min;
        boolean day = true;
        final List<Hour> hours = new ArrayList<>();
        final List<Day> days = new ArrayList<>();

        JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject();
            j.put("place", place).put("source", source).put("windUnit", windUnit).put("fetchedAt", fetchedAt)
                    .put("temp", temp).put("feels", feels).put("code", code).put("humidity", humidity)
                    .put("wind", wind).put("day", day);
            JSONArray h = new JSONArray();
            for (Hour x : hours) {
                h.put(new JSONObject().put("t", x.time).put("temp", x.temp).put("code", x.code).put("day", x.day));
            }
            JSONArray d = new JSONArray();
            for (Day x : days) {
                d.put(new JSONObject().put("date", x.date).put("code", x.code).put("max", x.max).put("min", x.min)
                        .put("rain", x.rain));
            }
            return j.put("hours", h).put("days", d);
        }

        static Data fromJson(JSONObject j) throws Exception {
            Data d = new Data();
            d.place = j.optString("place");
            d.source = j.optString("source", SRC_OPEN_METEO);
            d.windUnit = j.optString("windUnit", "km/h");
            d.fetchedAt = j.optLong("fetchedAt");
            d.temp = j.getInt("temp");
            d.feels = j.optInt("feels", d.temp);
            d.code = j.getInt("code");
            d.humidity = j.optInt("humidity");
            d.wind = j.optInt("wind");
            d.day = j.optBoolean("day", true);
            JSONArray h = j.getJSONArray("hours");
            for (int i = 0; i < h.length(); i++) {
                JSONObject o = h.getJSONObject(i);
                Hour x = new Hour();
                x.time = o.getLong("t");
                x.temp = o.getInt("temp");
                x.code = o.getInt("code");
                x.day = o.optBoolean("day", true);
                d.hours.add(x);
            }
            JSONArray a = j.getJSONArray("days");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Day x = new Day();
                x.date = o.getString("date");
                x.code = o.getInt("code");
                x.max = o.getInt("max");
                x.min = o.getInt("min");
                x.rain = o.optInt("rain", -1);
                d.days.add(x);
            }
            return d;
        }
    }

    /**
     * The cached forecast ready to show: hours from the current hour ("Now" first), days from
     * today ("Today" first), and today's high/low. Null when nothing has loaded yet.
     */
    static Data cached(Context c) {
        String raw = prefs(c).getString(K_CACHE, null);
        if (raw == null) return null;
        try {
            return present(c, Data.fromJson(new JSONObject(raw)));
        } catch (Exception e) {
            return null;
        }
    }

    private static Data present(Context c, Data src) throws Exception {
        Data d = Data.fromJson(src.toJson());
        d.hours.clear();
        d.days.clear();
        long hourStart = localHourStart();
        SimpleDateFormat hourFmt = new SimpleDateFormat(
                android.text.format.DateFormat.is24HourFormat(c) ? "HH" : "h a", I18n.locale(c));
        for (Hour h : src.hours) {
            if (h.time < hourStart || d.hours.size() >= 6) continue;
            if (d.hours.isEmpty()) {
                h.label = I18n.s(c, "Now", "Şimdi");
                h.temp = d.temp;
                h.code = d.code;
                h.day = d.day;
            } else {
                h.label = hourFmt.format(new Date(h.time)).replace(" ", "").toUpperCase(I18n.locale(c));
            }
            d.hours.add(h);
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", I18n.locale(c));
        for (Day x : src.days) {
            if (x.date.compareTo(today) < 0 || d.days.size() >= 5) continue;
            x.label = d.days.isEmpty() ? I18n.s(c, "Today", "Bugün") : dayFmt.format(ymd.parse(x.date));
            d.days.add(x);
        }
        if (!d.days.isEmpty()) {
            d.max = Math.max(d.days.get(0).max, d.temp);
            d.min = Math.min(d.days.get(0).min, d.temp);
        } else {
            d.max = d.min = d.temp;
        }
        return d;
    }

    static boolean stale(Context c) {
        SharedPreferences p = prefs(c);
        return System.currentTimeMillis() - p.getLong(K_CACHE_AT, 0) > refreshMinutes(c) * 60000L
                || !key(c).equals(p.getString(K_CACHE_KEY, ""));
    }

    private static String key(Context c) {
        return units(c) + "|" + source(c) + (manual(c) ? "|" + prefs(c).getFloat(K_LAT, 0) + "," + prefs(c).getFloat(K_LON, 0) : "|auto");
    }

    private static boolean busy;

    /** Fetches in the background when the cache is due; {@code done} runs on the main thread. */
    static void refresh(Context ctx, boolean force, Runnable done) {
        Context c = ctx.getApplicationContext();
        if (!enabled(c) || busy || (!force && !stale(c))) return;
        busy = true;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                SharedPreferences p = prefs(c);
                double lat, lon;
                String place;
                if (manual(c)) {
                    lat = p.getFloat(K_LAT, 0);
                    lon = p.getFloat(K_LON, 0);
                    place = p.getString(K_PLACE, "");
                } else {
                    double[] ll = new double[2];
                    place = locate(ll);
                    lat = ll[0];
                    lon = ll[1];
                }
                boolean f = units(c).equals("f");
                // The chosen source first, then the others.
                List<String> order = new ArrayList<>();
                order.add(source(c));
                for (String s : SOURCES) if (!order.contains(s)) order.add(s);
                Data d = null;
                for (String s : order) {
                    try {
                        d = s.equals(SRC_MET) ? fetchMet(lat, lon, f) : s.equals(SRC_WTTR) ? fetchWttr(lat, lon, f)
                                : fetchOpenMeteo(lat, lon, f);
                        d.source = s;
                        break;
                    } catch (Exception ignored) {
                        // try the next source
                    }
                }
                if (d != null) {
                    if (!place.isEmpty() || d.place.isEmpty()) d.place = place;
                    d.fetchedAt = System.currentTimeMillis();
                    p.edit().putString(K_CACHE, d.toJson().toString()).putString(K_PLACE, d.place)
                            .putLong(K_CACHE_AT, d.fetchedAt).putString(K_CACHE_KEY, key(c)).apply();
                }
            } catch (Exception ignored) {
                // Keep showing the last forecast.
            }
            main.post(() -> {
                busy = false;
                if (done != null) done.run();
            });
        }).start();
    }

    // ---------------------------------------------------------------- sources

    /** Open-Meteo: hourly and daily forecast in the place's own time zone. */
    private static Data fetchOpenMeteo(double lat, double lon, boolean f) throws Exception {
        JSONObject j = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m"
                + "&hourly=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto&forecast_days=6&forecast_hours=24"
                + (f ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "")));
        Data d = new Data();
        d.windUnit = f ? "mph" : "km/h";
        JSONObject cur = j.getJSONObject("current");
        d.temp = round(cur.getDouble("temperature_2m"));
        d.feels = round(cur.getDouble("apparent_temperature"));
        d.code = cur.getInt("weather_code");
        d.day = cur.optInt("is_day", 1) == 1;
        d.humidity = cur.optInt("relative_humidity_2m");
        d.wind = round(cur.optDouble("wind_speed_10m", 0));
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone(j.optString("timezone", "UTC")));
        JSONObject h = j.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time");
        for (int i = 0; i < ht.length(); i++) {
            Hour x = new Hour();
            x.time = iso.parse(ht.getString(i)).getTime();
            x.temp = round(h.getJSONArray("temperature_2m").getDouble(i));
            x.code = h.getJSONArray("weather_code").getInt(i);
            x.day = h.getJSONArray("is_day").getInt(i) == 1;
            d.hours.add(x);
        }
        JSONObject dl = j.getJSONObject("daily");
        JSONArray dt = dl.getJSONArray("time"), rain = dl.optJSONArray("precipitation_probability_max");
        for (int i = 0; i < dt.length(); i++) {
            Day x = new Day();
            x.date = dt.getString(i);
            x.code = dl.getJSONArray("weather_code").getInt(i);
            x.max = round(dl.getJSONArray("temperature_2m_max").getDouble(i));
            x.min = round(dl.getJSONArray("temperature_2m_min").getDouble(i));
            x.rain = rain == null || rain.isNull(i) ? -1 : rain.getInt(i);
            d.days.add(x);
        }
        return d;
    }

    /** MET Norway (Yr) Locationforecast: UTC time series in \u00b0C and m/s; days grouped locally. */
    private static Data fetchMet(double lat, double lon, boolean f) throws Exception {
        JSONObject j = new JSONObject(get(String.format(Locale.US,
                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon)));
        JSONArray ts = j.getJSONObject("properties").getJSONArray("timeseries");
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Data d = new Data();
        d.windUnit = f ? "mph" : "km/h";
        long now = System.currentTimeMillis();
        Map<String, Day> days = new LinkedHashMap<>();
        Map<String, Long> noonGap = new LinkedHashMap<>();
        boolean first = true;
        for (int i = 0; i < ts.length(); i++) {
            JSONObject e = ts.getJSONObject(i);
            long t = iso.parse(e.getString("time")).getTime();
            JSONObject data = e.getJSONObject("data");
            JSONObject inst = data.getJSONObject("instant").getJSONObject("details");
            double c = inst.getDouble("air_temperature");
            String sym = symbol(data, "next_1_hours");
            if (sym == null) sym = symbol(data, "next_6_hours");
            if (first && t <= now + 3600000L) {
                double ws = inst.optDouble("wind_speed", 0), rh = inst.optDouble("relative_humidity", 50);
                d.temp = temp(c, f);
                d.feels = temp(apparent(c, rh, ws), f);
                d.humidity = (int) Math.round(rh);
                d.wind = (int) Math.round(f ? ws * 2.237 : ws * 3.6);
                d.code = metCode(sym);
                d.day = sym == null || !sym.contains("night");
                first = false;
            }
            if (t >= now - 3600000L && t <= now + 24 * 3600000L && data.has("next_1_hours")) {
                Hour x = new Hour();
                x.time = t;
                x.temp = temp(c, f);
                x.code = metCode(sym);
                x.day = sym == null || !sym.contains("night");
                d.hours.add(x);
            }
            String date = ymd.format(new Date(t));
            Day day = days.get(date);
            if (day == null) {
                day = new Day();
                day.date = date;
                day.max = Integer.MIN_VALUE;
                day.min = Integer.MAX_VALUE;
                day.code = 3;
                days.put(date, day);
            }
            day.max = Math.max(day.max, temp(c, f));
            day.min = Math.min(day.min, temp(c, f));
            // The day's weather: the symbol nearest midday.
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t);
            long gap = Math.abs(cal.get(Calendar.HOUR_OF_DAY) - 12);
            Long best = noonGap.get(date);
            String daySym = symbol(data, "next_6_hours");
            if (daySym == null) daySym = sym;
            if (daySym != null && (best == null || gap < best)) {
                noonGap.put(date, gap);
                day.code = metCode(daySym);
            }
        }
        if (first) throw new Exception("No current data");
        d.days.addAll(days.values());
        return d;
    }

    /** wttr.in JSON: current conditions and 3 days in 3-hour steps, local time. */
    private static Data fetchWttr(double lat, double lon, boolean f) throws Exception {
        JSONObject j = new JSONObject(get(String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1", lat, lon)));
        JSONObject cur = j.getJSONArray("current_condition").getJSONObject(0);
        Data d = new Data();
        d.windUnit = f ? "mph" : "km/h";
        d.temp = cur.getInt(f ? "temp_F" : "temp_C");
        d.feels = cur.optInt(f ? "FeelsLikeF" : "FeelsLikeC", d.temp);
        d.humidity = cur.optInt("humidity");
        d.wind = cur.optInt(f ? "windspeedMiles" : "windspeedKmph");
        d.code = wwoCode(cur.optInt("weatherCode", 116));
        int hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        d.day = hourNow >= 6 && hourNow < 18;
        try {
            d.place = j.getJSONArray("nearest_area").getJSONObject(0).getJSONArray("areaName").getJSONObject(0).getString("value");
        } catch (Exception ignored) {
            // no area name
        }
        SimpleDateFormat local = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US);
        JSONArray w = j.getJSONArray("weather");
        for (int i = 0; i < w.length(); i++) {
            JSONObject o = w.getJSONObject(i);
            Day x = new Day();
            x.date = o.getString("date");
            x.max = o.getInt(f ? "maxtempF" : "maxtempC");
            x.min = o.getInt(f ? "mintempF" : "mintempC");
            JSONArray hr = o.getJSONArray("hourly");
            x.code = 3;
            x.rain = 0;
            for (int k = 0; k < hr.length(); k++) {
                JSONObject h = hr.getJSONObject(k);
                int hhmm = h.getInt("time");
                int code = wwoCode(h.optInt("weatherCode", 116));
                if (hhmm == 1200) x.code = code;
                x.rain = Math.max(x.rain, h.optInt("chanceofrain", 0));
                Hour hour = new Hour();
                hour.time = local.parse(x.date + " " + String.format(Locale.US, "%04d", hhmm)).getTime();
                hour.temp = h.getInt(f ? "tempF" : "tempC");
                hour.code = code;
                hour.day = hhmm >= 600 && hhmm < 1800;
                d.hours.add(hour);
            }
            d.days.add(x);
        }
        // 3-hour steps: keep the step containing now as the first hour.
        long now = System.currentTimeMillis();
        List<Hour> keep = new ArrayList<>();
        for (Hour h : d.hours) if (h.time + 3 * 3600000L > now) keep.add(h);
        if (!keep.isEmpty()) keep.get(0).time = Math.max(keep.get(0).time, localHourStart());
        d.hours.clear();
        d.hours.addAll(keep);
        return d;
    }

    private static String symbol(JSONObject data, String period) {
        JSONObject p = data.optJSONObject(period);
        if (p == null || p.optJSONObject("summary") == null) return null;
        return p.optJSONObject("summary").optString("symbol_code", null);
    }

    /** MET Norway symbol \u2192 WMO weather code. */
    static int metCode(String sym) {
        if (sym == null) return 3;
        String s = sym.replace("_day", "").replace("_night", "").replace("_polartwilight", "");
        if (s.contains("thunder")) return 95;
        if (s.contains("snow")) return s.startsWith("heavy") ? 75 : s.startsWith("light") ? 71 : 73;
        if (s.contains("sleet")) return 66;
        if (s.contains("rain")) {
            if (s.contains("showers")) return s.startsWith("heavy") ? 82 : 80;
            return s.startsWith("heavy") ? 65 : s.startsWith("light") ? 61 : 63;
        }
        switch (s) {
            case "clearsky": return 0;
            case "fair": return 1;
            case "partlycloudy": return 2;
            case "fog": return 45;
            default: return 3;
        }
    }

    /** World Weather Online code (used by wttr.in) \u2192 WMO weather code. */
    static int wwoCode(int c) {
        switch (c) {
            case 113: return 0;
            case 116: return 2;
            case 143: case 248: case 260: return 45;
            case 176: case 263: case 266: case 293: case 296: case 353: return 61;
            case 299: case 302: case 356: return 63;
            case 305: case 308: case 359: return 65;
            case 179: case 227: case 323: case 326: case 368: return 71;
            case 329: case 332: case 335: case 371: return 73;
            case 230: case 338: case 392: case 395: return 75;
            case 182: case 185: case 281: case 284: case 311: case 314: case 317: case 320:
            case 350: case 362: case 365: case 374: case 377: return 66;
            case 200: case 386: case 389: return 95;
            default: return 3;
        }
    }

    /** Apparent temperature (Australian BoM formula), \u00b0C from \u00b0C, % humidity and m/s wind. */
    static double apparent(double t, double rh, double ws) {
        double e = rh / 100 * 6.105 * Math.exp(17.27 * t / (237.7 + t));
        return t + 0.33 * e - 0.70 * ws - 4.00;
    }

    /** Start of the current hour in local time (half-hour time zones such as India included). */
    static long localHourStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static int temp(double celsius, boolean f) {
        return round(f ? celsius * 9 / 5 + 32 : celsius);
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    // ---------------------------------------------------------------- location

    /** Approximate location from the internet address; returns the city name. */
    private static String locate(double[] out) throws Exception {
        String[] services = {"https://ipapi.co/json/", "https://ipwho.is/", "https://get.geojs.io/v1/ip/geo.json"};
        Exception last = null;
        for (String url : services) {
            try {
                JSONObject j = new JSONObject(get(url));
                if (!j.has("latitude") || j.optBoolean("error") || !j.optBoolean("success", true)) continue;
                out[0] = Double.parseDouble(j.getString("latitude"));
                out[1] = Double.parseDouble(j.getString("longitude"));
                return j.optString("city", "");
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("No location");
    }

    static final class Place {
        String name, detail;
        double lat, lon;
    }

    /** City search for Settings (Open-Meteo geocoding). Call off the main thread. */
    static List<Place> search(String q) throws Exception {
        JSONObject j = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name="
                + URLEncoder.encode(q.trim(), "UTF-8")));
        List<Place> out = new ArrayList<>();
        JSONArray a = j.optJSONArray("results");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            Place pl = new Place();
            pl.name = r.optString("name");
            StringBuilder d = new StringBuilder();
            for (String k : new String[]{"admin1", "country"}) {
                String v = r.optString(k, "");
                if (!v.isEmpty()) d.append(d.length() > 0 ? ", " : "").append(v);
            }
            pl.detail = d.toString();
            pl.lat = r.getDouble("latitude");
            pl.lon = r.getDouble("longitude");
            out.add(pl);
        }
        return out;
    }

    static void setPlace(Context c, Place pl) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (pl == null) e.putBoolean(K_MANUAL, false);
        else e.putBoolean(K_MANUAL, true).putString(K_PLACE, pl.name)
                .putFloat(K_LAT, (float) pl.lat).putFloat(K_LON, (float) pl.lon);
        e.remove(K_CACHE_AT).apply();
    }

    /** WMO weather code -> short description, as Apple Weather words it. */
    static String describe(int code, boolean day) {
        switch (code) {
            case 0: return day ? "Sunny" : "Clear";
            case 1: return day ? "Mostly Sunny" : "Mostly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: case 56: case 57: return "Drizzle";
            case 61: case 63: case 80: case 81: return "Rain";
            case 65: case 82: return "Heavy Rain";
            case 66: case 67: return "Sleet";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Snow";
            case 95: case 96: case 99: return "Thunderstorms";
            default: return "";
        }
    }

    static String describe(Context c, int code, boolean day) {
        if (!I18n.isTr(c)) return I18n.t(c, describe(code, day));
        switch (code) {
            case 0: return day ? "Güneşli" : "Açık";
            case 1: return day ? "Çoğunlukla Güneşli" : "Çoğunlukla Açık";
            case 2: return "Parçalı Bulutlu";
            case 3: return "Bulutlu";
            case 45: case 48: return "Sisli";
            case 51: case 53: case 55: case 56: case 57: return "Çiseleme";
            case 61: case 63: case 80: case 81: return "Yağmurlu";
            case 65: case 82: return "Şiddetli Yağmur";
            case 66: case 67: return "Karla Karışık Yağmur";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Karlı";
            case 95: case 96: case 99: return "Fırtına";
            default: return "";
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", UA);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }
}
