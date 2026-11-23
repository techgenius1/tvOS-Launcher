package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Multi-language localization support for tvOS Launcher:
 * English, Turkish, Azerbaijani, Swedish, Hindi, Spanish, French, German, Italian, Russian,
 * Portuguese (Portugal and Brazil), Polish, Arabic, Japanese, Chinese, Korean and Hebrew.
 */
public final class I18n {
    public static final String LANG_EN = "en";
    public static final String LANG_TR = "tr";
    public static final String LANG_AZ = "az";
    public static final String LANG_SV = "sv";
    public static final String LANG_HI = "hi";
    public static final String LANG_ES = "es";
    public static final String LANG_FR = "fr";
    public static final String LANG_DE = "de";
    public static final String LANG_IT = "it";
    public static final String LANG_RU = "ru";
    public static final String LANG_PL = "pl";
    public static final String LANG_PT = "pt";
    public static final String LANG_PT_BR = "pt-BR";
    public static final String LANG_AR = "ar";
    public static final String LANG_JA = "ja";
    public static final String LANG_ZH = "zh";
    public static final String LANG_KO = "ko";
    public static final String LANG_HE = "he";

    private static final String[] LANGUAGE_CODES = {
        LANG_EN, LANG_TR, LANG_AZ, LANG_SV, LANG_HI, LANG_ES, LANG_FR, LANG_DE, LANG_IT,
        LANG_RU, LANG_PT, LANG_PT_BR, LANG_PL, LANG_AR, LANG_JA, LANG_ZH, LANG_KO, LANG_HE
    };

    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(LANGUAGE_CODES));

    private static String cachedLang;
    private static final Map<Context, LocalizedResources> RESOURCE_CACHE = new WeakHashMap<>();
    private static Map<String, Integer> legacyErrorIds;
    private static Set<String> legacyTurkishErrorKeys;

    private I18n() { }

    public static String detectSystemLang(Context c) {
        Locale loc = null;
        try {
            if (c != null && c.getResources() != null && c.getResources().getConfiguration() != null) {
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    android.os.LocaleList locales = c.getResources().getConfiguration().getLocales();
                    if (locales != null && !locales.isEmpty()) {
                        loc = locales.get(0);
                    }
                } else {
                    @SuppressWarnings("deprecation")
                    Locale confLoc = c.getResources().getConfiguration().locale;
                    loc = confLoc;
                }
            }
        } catch (Throwable ignored) {
        }
        if (loc == null) {
            loc = Locale.getDefault();
        }
        String raw = loc != null ? loc.getLanguage() : null;
        if (raw == null || raw.isEmpty()) {
            return LANG_EN;
        }
        String code = raw.trim().toLowerCase(Locale.US);
        int sep = Math.max(code.indexOf('-'), code.indexOf('_'));
        if (sep > 0) {
            code = code.substring(0, sep);
        }
        // Older Android/Java versions report Hebrew with the legacy code "iw".
        if ("iw".equals(code)) code = LANG_HE;
        if (LANG_PT.equals(code) && loc != null && "BR".equalsIgnoreCase(loc.getCountry())) {
            return LANG_PT_BR;
        }
        return SUPPORTED.contains(code) ? code : LANG_EN;
    }

    public static void resetCache() {
        cachedLang = null;
    }

    public static String getLang(Context c) {
        if (cachedLang != null) return cachedLang;
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String saved = p.getString("lang", null);
        if (saved == null || !SUPPORTED.contains(saved)) {
            saved = detectSystemLang(c);
            p.edit().putString("lang", saved).apply();
            if (!p.contains("clock_24h")) {
                Scenes.setClock24h(c, isDefault24hForLang(saved));
            }
        }
        cachedLang = saved;
        return cachedLang;
    }

    public static boolean isDefault24hForLang(String lang) {
        if (lang == null) return false;
        switch (lang) {
            case LANG_EN:
            case LANG_HI:
            case LANG_AR:
                return false;
            default:
                return true;
        }
    }

    public static void setLang(Context c, String lang) {
        String valid = (lang != null && SUPPORTED.contains(lang)) ? lang : LANG_EN;
        cachedLang = valid;
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putString("lang", valid).apply();
        Scenes.setClock24h(c, isDefault24hForLang(valid));
    }

    public static boolean isTr(Context c) {
        return LANG_TR.equals(getLang(c));
    }

    public static Locale locale(Context c) {
        return localeForLanguage(getLang(c));
    }

    private static Locale localeForLanguage(String language) {
        switch (language) {
            case LANG_TR: return Locale.forLanguageTag("tr-TR");
            case LANG_AZ: return Locale.forLanguageTag("az-AZ");
            case LANG_SV: return Locale.forLanguageTag("sv-SE");
            case LANG_HI: return Locale.forLanguageTag("hi-IN");
            case LANG_ES: return Locale.forLanguageTag("es-ES");
            case LANG_FR: return Locale.forLanguageTag("fr-FR");
            case LANG_DE: return Locale.forLanguageTag("de-DE");
            case LANG_IT: return Locale.forLanguageTag("it-IT");
            case LANG_RU: return Locale.forLanguageTag("ru-RU");
            case LANG_PT: return Locale.forLanguageTag("pt-PT");
            case LANG_PT_BR: return Locale.forLanguageTag("pt-BR");
            case LANG_PL: return Locale.forLanguageTag("pl-PL");
            case LANG_AR: return Locale.forLanguageTag("ar-SA");
            case LANG_JA: return Locale.forLanguageTag("ja-JP");
            case LANG_ZH: return Locale.forLanguageTag("zh-CN");
            case LANG_KO: return Locale.forLanguageTag("ko-KR");
            case LANG_HE: return Locale.forLanguageTag("he-IL");
            default: return Locale.US;
        }
    }

    static final class LanguageOption {
        final String code;
        final String title;
        final String description;

        LanguageOption(String code, String title, String description) {
            this.code = code;
            this.title = title;
            this.description = description;
        }
    }

    static LanguageOption[] languageOptions(Context c) {
        LanguageOption[] options = new LanguageOption[LANGUAGE_CODES.length];
        android.content.res.Resources[] resources = languageResources(c);
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            options[i] = new LanguageOption(LANGUAGE_CODES[i], resources[i].getString(R.string.language_option_title),
                    resources[i].getString(R.string.language_option_description));
        }
        return options;
    }

    static android.content.res.Resources[] languageResources(Context c) {
        android.content.res.Resources[] resources = new android.content.res.Resources[LANGUAGE_CODES.length];
        android.content.res.Configuration config =
                new android.content.res.Configuration(c.getResources().getConfiguration());
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            config.setLocale(localeForLanguage(LANGUAGE_CODES[i]));
            resources[i] = c.createConfigurationContext(config).getResources();
        }
        return resources;
    }

    private static final class LocalizedResources {
        final android.content.res.Configuration baseConfiguration;
        final String language;
        final android.content.res.Resources resources;

        LocalizedResources(android.content.res.Configuration baseConfiguration, String language,
                           android.content.res.Resources resources) {
            this.baseConfiguration = baseConfiguration;
            this.language = language;
            this.resources = resources;
        }
    }

    private static android.content.res.Resources localizedResources(Context c) {
        String language = getLang(c);
        android.content.res.Configuration current = c.getResources().getConfiguration();
        synchronized (RESOURCE_CACHE) {
            LocalizedResources cached = RESOURCE_CACHE.get(c);
            if (cached == null || !cached.language.equals(language)
                    || !cached.baseConfiguration.equals(current)) {
                android.content.res.Configuration base = new android.content.res.Configuration(current);
                android.content.res.Configuration localized = new android.content.res.Configuration(base);
                localized.setLocale(locale(c));
                cached = new LocalizedResources(base, language,
                        c.createConfigurationContext(localized).getResources());
                RESOURCE_CACHE.put(c, cached);
            }
            return cached.resources;
        }
    }

    /** Loads an Android string using the language selected in the launcher. */
    public static String resourceString(Context c, int resourceId) {
        return localizedResources(c).getString(resourceId);
    }

    public static String resourceString(Context c, int resourceId, Object... args) {
        String localized = resourceString(c, resourceId);
        if (args == null || args.length == 0) return localized;
        return String.format(locale(c), localized, args);
    }

    public static String resourceQuantityString(Context c, int resourceId, int quantity, Object... args) {
        String localized = localizedResources(c).getQuantityString(resourceId, quantity);
        return String.format(locale(c), localized, args);
    }

    static Map<String, Integer> loadLegacyIndex(Context c, int keysId, int valuesId) {
        String[] keys = c.getResources().getStringArray(keysId);
        android.content.res.TypedArray values = c.getResources().obtainTypedArray(valuesId);
        try {
            if (keys.length != values.length()) throw new IllegalStateException("Legacy translation index is inconsistent");
            Map<String, Integer> ids = new HashMap<>();
            for (int i = 0; i < keys.length; i++) {
                int id = values.getResourceId(i, 0);
                if (id == 0 || ids.put(keys[i], id) != null)
                    throw new IllegalStateException("Invalid legacy translation index entry: " + keys[i]);
            }
            return ids;
        } finally {
            values.recycle();
        }
    }

    private static synchronized int legacyErrorId(Context c, String key) {
        if (legacyErrorIds == null) {
            Map<String, Integer> ids = loadLegacyIndex(c, R.array.legacy_error_keys, R.array.legacy_error_values);
            Set<String> turkishKeys = new HashSet<>();
            for (String turkishKey : c.getResources().getStringArray(R.array.legacy_error_turkish_keys)) {
                if (!ids.containsKey(turkishKey) || !turkishKeys.add(turkishKey))
                    throw new IllegalStateException("Invalid Turkish legacy translation key: " + turkishKey);
            }
            legacyTurkishErrorKeys = turkishKeys;
            legacyErrorIds = ids;
        }
        Integer id = legacyErrorIds.get(key);
        return id != null ? id : 0;
    }

    private static String englishResourceString(Context c, int id) {
        android.content.res.Configuration config =
                new android.content.res.Configuration(c.getResources().getConfiguration());
        config.setLocale(Locale.US);
        return c.createConfigurationContext(config).getString(id);
    }

    private static String legacyErrorValue(Context c, String lang, String key, int id) {
        if (LANG_TR.equals(lang) && !legacyTurkishErrorKeys.contains(key))
            return englishResourceString(c, id);
        return resourceString(c, id);
    }

    /** Translates arbitrary legacy error messages using the active language. */
    public static String t(Context c, String enKey) {
        if (enKey == null) return "";
        String lang = getLang(c);
        if (LANG_HE.equals(lang)) {
            String direct = translateOther(c, lang, enKey);
            if (!direct.equals(enKey)) return direct;
            int id = legacyErrorId(c, enKey);
            if (id != 0) {
                String mappedEn = englishResourceString(c, id);
                if (!mappedEn.equals(enKey)) return translateOther(c, lang, mappedEn);
            }
            return enKey;
        }
        int id = legacyErrorId(c, enKey);
        if (id != 0) return legacyErrorValue(c, lang, enKey, id);
        if (LANG_EN.equals(lang) || LANG_TR.equals(lang)) return enKey;
        return translateOther(c, lang, enKey);
    }

    private static String translateOther(Context c, String lang, String en) {
        if (LANG_HE.equals(lang)) {
            String direct = I18nHe.dictionaryString(c, en);
            if (direct != null) return direct;
            String dyn = I18nHe.dynamic(c, en);
            if (dyn != null) return dyn;
        }
        // Dynamic pattern translations
        if (en.endsWith("% used · Recent Apps")) {
            String num = en.substring(0, en.length() - "% used · Recent Apps".length());
            return num + "% · " + legacyDynamicValue(c, lang, "Recent Apps");
        }
        if (en.endsWith("% used · Clean")) {
            String num = en.substring(0, en.length() - "% used · Clean".length());
            return num + "% · " + legacyDynamicValue(c, lang, "Clean");
        }
        if (en.endsWith(" min left")) {
            String num = en.substring(0, en.length() - " min left".length());
            return num + " min";
        }
        if (en.startsWith("Move ")) {
            String rest = en.substring("Move ".length());
            if (!rest.equals("left in dock") && !rest.equals("right in dock")) {
                return legacyDynamicValue(c, lang, "Move") + " " + rest;
            }
        }
        if (en.startsWith("Switch to ")) {
            String rest = en.substring("Switch to ".length());
            return legacyDynamicValue(c, lang, "Switch") + ": " + rest;
        }
        if (en.startsWith("Done (") && en.endsWith(" apps disabled)")) {
            String num = en.substring("Done (".length(), en.length() - " apps disabled)".length());
            return legacyDynamicValue(c, lang, "Done") + " (" + num + " " + legacyDynamicValue(c, lang, "apps") + ")";
        }
        if (en.startsWith("Not done (") && en.endsWith(" disabled)")) {
            String ratio = en.substring("Not done (".length(), en.length() - " disabled)".length());
            return legacyDynamicValue(c, lang, "Not done") + " (" + ratio + ")";
        }
        return en;
    }

    private static String legacyDynamicValue(Context c, String lang, String key) {
        if (LANG_HE.equals(lang)) {
            String value = I18nHe.dictionaryString(c, key);
            return value != null ? value : key;
        }
        int id = legacyErrorId(c, key);
        return id != 0 ? legacyErrorValue(c, lang, key, id) : key;
    }

    /** Formats the date line above the scene clock according to the active language. */
    public static String formatSceneDate(Context c, Date date) {
        String lang = getLang(c);
        Locale loc = locale(c);
        switch (lang) {
            case LANG_TR:
            case LANG_AZ:
                return new SimpleDateFormat("d MMMM EEEE", loc).format(date);
            case LANG_JA:
            case LANG_ZH:
                return new SimpleDateFormat("M月d日 EEEE", loc).format(date);
            case LANG_KO:
                return new SimpleDateFormat("M월 d일 EEEE", loc).format(date);
            case LANG_HE:
                return new SimpleDateFormat("EEEE, d \u05d1MMMM", loc).format(date);
            case LANG_DE:
                return new SimpleDateFormat("EEEE, d. MMMM", loc).format(date);
            case LANG_PT_BR:
                return new SimpleDateFormat("EEEE, d 'de' MMMM", loc).format(date);
            case LANG_SV:
            case LANG_ES:
            case LANG_FR:
            case LANG_IT:
            case LANG_PT:
            case LANG_PL:
            case LANG_RU:
            case LANG_HI:
            case LANG_AR:
                return new SimpleDateFormat("EEEE d MMMM", loc).format(date);
            default:
                return new SimpleDateFormat("EEEE d MMMM", Locale.UK).format(date);
        }
    }

    /** True when the active language is written right to left and the UI should be mirrored. */
    public static boolean isRtl(Context c) {
        String lang = getLang(c);
        return LANG_HE.equals(lang) || LANG_AR.equals(lang);
    }

    /**
     * Wraps an activity's base context so its layout direction follows the in-app language
     * (not the system locale). Dialogs created from the activity inherit it.
     */
    public static Context wrap(Context base) {
        if (base == null) return null;
        try {
            android.content.res.Configuration conf =
                    new android.content.res.Configuration(base.getResources().getConfiguration());
            conf.setLayoutDirection(isRtl(base) ? locale(base) : Locale.US);
            return base.createConfigurationContext(conf);
        } catch (Throwable t) {
            return base;
        }
    }

    /** Applies the in-app reading direction to a view tree (used for overlays and windows). */
    public static void applyDirection(Context c, android.view.View root) {
        if (root == null) return;
        boolean rtl = isRtl(c);
        root.setLayoutDirection(rtl ? android.view.View.LAYOUT_DIRECTION_RTL : android.view.View.LAYOUT_DIRECTION_LTR);
        root.setTextDirection(rtl ? android.view.View.TEXT_DIRECTION_ANY_RTL : android.view.View.TEXT_DIRECTION_FIRST_STRONG);
        if (rtl) {
            // Rows are built and rebuilt in code: keep start-aligned text on the right edge after every layout,
            // so Latin-only lines (app names, percentages) line up with the Hebrew ones.
            final android.view.View r = root;
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> alignToStart(r));
        }
    }

    private static void alignToStart(android.view.View v) {
        if (v instanceof android.widget.TextView) {
            android.widget.TextView t = (android.widget.TextView) v;
            int h = t.getGravity() & android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
            if (t.getTextAlignment() == android.view.View.TEXT_ALIGNMENT_GRAVITY
                    && (h == android.view.Gravity.START || h == android.view.Gravity.LEFT)) {
                // Gravity (not textAlignment) so TextView rebuilds its cached layout right away.
                t.setGravity((t.getGravity() & ~android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) | android.view.Gravity.RIGHT);
            }
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) alignToStart(g.getChildAt(i));
        }
    }

    /** Keeps a view tree left-to-right (Home screen layout) while Hebrew text still reads right to left. */
    public static void keepLtr(Context c, android.view.View root) {
        if (root == null) return;
        root.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
        root.setTextDirection(isRtl(c) ? android.view.View.TEXT_DIRECTION_ANY_RTL : android.view.View.TEXT_DIRECTION_FIRST_STRONG);
    }

    /** Translates default profile names ("Person 1", "Default", "Kids") while preserving custom names. */
    public static String profileName(Context c, String name) {
        if (name == null) return "";
        String lang = getLang(c);
        if (LANG_HE.equals(lang)) {
            if (name.equals("Default") || name.equals("Varsay\u0131lan")) return resourceString(c, R.string.profile_name_default);
            if (name.startsWith("Person ")) return resourceString(c, R.string.profile_name_person, name.substring("Person ".length()));
            if (name.startsWith("Ki\u015fi ")) return resourceString(c, R.string.profile_name_person, name.substring("Ki\u015fi ".length()));
            if (name.startsWith("Profile ")) return resourceString(c, R.string.profile_name_profile, name.substring("Profile ".length()));
            if (name.startsWith("Profil ")) return resourceString(c, R.string.profile_name_profile, name.substring("Profil ".length()));
            if (name.equals("Kids") || name.equals("\u00c7ocuklar")) return resourceString(c, R.string.profile_name_kids);
            return name;
        }
        if (LANG_TR.equals(lang)) {
            if (name.equals("Default")) return resourceString(c, R.string.profile_name_default);
            if (name.startsWith("Person ")) return resourceString(c, R.string.profile_name_person, name.substring("Person ".length()));
            if (name.startsWith("Profile ")) return resourceString(c, R.string.profile_name_profile, name.substring("Profile ".length()));
            if (name.equals("Kids")) return resourceString(c, R.string.profile_name_kids);
        } else if (LANG_AZ.equals(lang)) {
            if (name.equals("Default") || name.equals("Varsayılan")) return resourceString(c, R.string.profile_name_default);
            if (name.startsWith("Person ")) return resourceString(c, R.string.profile_name_person, name.substring("Person ".length()));
            if (name.equals("Kids")) return resourceString(c, R.string.profile_name_kids);
        } else {
            if (name.equals("Varsayılan")) return resourceString(c, R.string.profile_name_default);
            if (name.startsWith("Kişi ")) return resourceString(c, R.string.profile_name_person, name.substring("Kişi ".length()));
            if (name.startsWith("Profil ")) return resourceString(c, R.string.profile_name_profile, name.substring("Profil ".length()));
        }
        return name;
    }
}
