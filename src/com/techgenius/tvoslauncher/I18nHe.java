package com.techgenius.tvoslauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hebrew translation: fixed strings live in I18nHeData; this class holds the patterns for strings built with values.
 * Keys are the original English strings, as for every other language.
 */
final class I18nHe {
    private I18nHe() { }

    private static Map<String, String> dict;
    private static List<Object[]> rules;

    static void init(Map<String, String> map) {
        dict = map;
        I18nHeData.load(map);
    }

    /** Translates a string built with values through its pattern, or null when no pattern matches. */
    static String dynamic(String en) {
        if (en == null || en.isEmpty()) return null;
        String closed = closedApps(en);
        if (closed != null) return closed;
        if (rules == null) rules = buildRules();
        for (Object[] r : rules) {
            Matcher m = ((Pattern) r[0]).matcher(en);
            if (m.matches()) return fill((String) r[1], m);
        }
        return null;
    }

    /** "Closed 3 apps \u00b7 freed 120 MB" with the right singular and plural forms. */
    private static String closedApps(String en) {
        Matcher m = CLOSED.matcher(en);
        if (!m.matches()) return null;
        int n;
        try {
            n = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String s = n == 1 ? "\u05e0\u05e1\u05d2\u05e8\u05d4 \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d4 \u05d0\u05d7\u05ea" : "\u05e0\u05e1\u05d2\u05e8\u05d5 " + n + " \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea";
        if (m.group(2) != null) s += " \u00b7 \u05e9\u05d5\u05d7\u05e8\u05e8\u05d5 " + m.group(2) + " MB";
        return s;
    }

    private static final Pattern CLOSED = Pattern.compile("Closed (\\d+) apps?(?: \u00b7 freed (\\d+) MB)?");

    /** {1} = group as is, {t1} = translated group, {k:Key} = dictionary entry. */
    private static String fill(String template, Matcher m) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char ch = template.charAt(i);
            int close = ch == '{' ? template.indexOf('}', i) : -1;
            if (close > i) {
                String tok = template.substring(i + 1, close);
                String val = null;
                boolean handled = true;
                if (tok.startsWith("k:")) {
                    String key = tok.substring(2);
                    val = dict != null && dict.containsKey(key) ? dict.get(key) : key;
                } else if (tok.startsWith("t") && tok.length() > 1 && Character.isDigit(tok.charAt(1))) {
                    val = translate(group(m, Integer.parseInt(tok.substring(1))));
                } else if (!tok.isEmpty() && Character.isDigit(tok.charAt(0))) {
                    val = group(m, Integer.parseInt(tok));
                } else {
                    handled = false;
                }
                if (handled) {
                    out.append(val != null ? val : "");
                    i = close + 1;
                    continue;
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String group(Matcher m, int g) {
        return g <= m.groupCount() ? m.group(g) : null;
    }

    private static String translate(String s) {
        if (s == null) return null;
        if (dict != null) {
            String v = dict.get(s);
            if (v != null) return v;
        }
        String d = dynamic(s);
        return d != null ? d : s;
    }

    private static void r(List<Object[]> list, String regex, String template) {
        list.add(new Object[]{Pattern.compile(regex, Pattern.DOTALL), template});
    }

    private static List<Object[]> buildRules() {
        List<Object[]> l = new ArrayList<>();
        // Updates
        r(l, "You are up to date \\(v(.+)\\)", "\u05d4\u05d2\u05e8\u05e1\u05d4 \u05d4\u05de\u05d5\u05ea\u05e7\u05e0\u05ea \u05d4\u05d9\u05d0 \u05d4\u05e2\u05d3\u05db\u05e0\u05d9\u05ea \u05d1\u05d9\u05d5\u05ea\u05e8 (v{1})");
        r(l, "A new version of tvOS Launcher is available on GitHub\\.\nCurrent version: v(.+?)  \u2192  New version: v(.+?)\n\n(.*)",
                "\u05d2\u05e8\u05e1\u05d4 \u05d7\u05d3\u05e9\u05d4 \u05e9\u05dc tvOS Launcher \u05d6\u05de\u05d9\u05e0\u05d4 \u05d1-GitHub.\n\u05d4\u05d2\u05e8\u05e1\u05d4 \u05d4\u05e0\u05d5\u05db\u05d7\u05d9\u05ea: v{1}  \u2190  \u05d4\u05d2\u05e8\u05e1\u05d4 \u05d4\u05d7\u05d3\u05e9\u05d4: v{2}\n\n{t3}");
        r(l, "Changelog \u2014 v(.+)", "\u05e8\u05e9\u05d9\u05de\u05ea \u05e9\u05d9\u05e0\u05d5\u05d9\u05d9\u05dd \u2014 v{1}");
        r(l, "Downloading update v(.+)\u2026", "\u05de\u05ea\u05d1\u05e6\u05e2\u05ea \u05d4\u05d5\u05e8\u05d3\u05d4 \u05e9\u05dc \u05e2\u05d3\u05db\u05d5\u05df v{1}\u2026");
        r(l, "Installing update v(.+) automatically via ADB\u2026", "\u05de\u05ea\u05d1\u05e6\u05e2\u05ea \u05d4\u05ea\u05e7\u05e0\u05d4 \u05d0\u05d5\u05d8\u05d5\u05de\u05d8\u05d9\u05ea \u05e9\u05dc \u05e2\u05d3\u05db\u05d5\u05df v{1} \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB\u2026");

        // Button mapper
        r(l, "Removed remap for (.+)\\.", "\u05d4\u05de\u05d9\u05e4\u05d5\u05d9 \u05e9\u05dc {t1} \u05d4\u05d5\u05e1\u05e8.");

        // CleanYourTV
        r(l, "Done \\((\\d+) apps disabled\\)", "\u05d1\u05d5\u05e6\u05e2 ({1} \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05d5\u05e9\u05d1\u05ea\u05d5)");
        r(l, "Not done \\((\\d+)/(\\d+) disabled\\)", "\u05dc\u05d0 \u05d1\u05d5\u05e6\u05e2 ({1}/{2} \u05d4\u05d5\u05e9\u05d1\u05ea\u05d5)");
        r(l, "Model: (.*)", "\u05d3\u05d2\u05dd: {1}");
        r(l, "Product: (.*)", "\u05de\u05d5\u05e6\u05e8: {1}");
        r(l, "Board/Device: (.*)", "\u05dc\u05d5\u05d7/\u05de\u05db\u05e9\u05d9\u05e8: {1}");
        r(l, "Brand/OEM: (.*)", "\u05de\u05d5\u05ea\u05d2/\u05d9\u05e6\u05e8\u05df: {1}");
        r(l, "(\\d+)/(\\d+) OEM pkgs: (.*)", "{1}/{2} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d9\u05e6\u05e8\u05df: {3}");
        r(l, "OEM family: (.*)", "\u05de\u05e9\u05e4\u05d7\u05ea \u05d9\u05e6\u05e8\u05df: {1}");
        r(l, "(\\d+)/(\\d+) apps \\((.*)\\)", "{1}/{2} \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea ({t3})");
        r(l, "Exact TV model found and selected automatically:\n\n\u2022 (.*)", "\u05d3\u05d2\u05dd \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d4\u05de\u05d3\u05d5\u05d9\u05e7 \u05d6\u05d5\u05d4\u05d4 \u05d5\u05e0\u05d1\u05d7\u05e8 \u05d0\u05d5\u05d8\u05d5\u05de\u05d8\u05d9\u05ea:\n\n\u2022 {1}");
        r(l, "Choose Closest TV Model \u2014 (.*)", "\u05d1\u05d7\u05d9\u05e8\u05ea \u05d3\u05d2\u05dd \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d4\u05e7\u05e8\u05d5\u05d1 \u05d1\u05d9\u05d5\u05ea\u05e8 \u2014 {1}");
        r(l, "Models with Similar System Apps \\((\\d+) apps scanned\\)", "\u05d3\u05d2\u05de\u05d9\u05dd \u05e2\u05dd \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05de\u05e2\u05e8\u05db\u05ea \u05d3\u05d5\u05de\u05d5\u05ea (\u05e0\u05e1\u05e8\u05e7\u05d5 {1} \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea)");
        r(l, "All TV Models \\((\\d+)\\)", "\u05db\u05dc \u05d3\u05d2\u05de\u05d9 \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 ({1})");
        r(l, "Search: \"(.*)\" \\((\\d+)\\)", "\u05d7\u05d9\u05e4\u05d5\u05e9: \"{1}\" ({2})");
        r(l, "All disabled \\((\\d+)\\)", "\u05db\u05d5\u05dc\u05df \u05de\u05d5\u05e9\u05d1\u05ea\u05d5\u05ea ({1})");
        r(l, "(\\d+) active / (\\d+) found \u203a", "{1} \u05e4\u05e2\u05d9\u05dc\u05d5\u05ea / {2} \u05e0\u05de\u05e6\u05d0\u05d5 \u2039");
        r(l, "(.+) \\((.+)\\) is a critical system service required for your TV remote and Bluetooth connectivity\\.\n\nIt cannot be disabled under any circumstances\\.",
                "{1} ({2}) \u05d4\u05d5\u05d0 \u05e9\u05d9\u05e8\u05d5\u05ea \u05de\u05e2\u05e8\u05db\u05ea \u05e7\u05e8\u05d9\u05d8\u05d9 \u05d4\u05e0\u05d3\u05e8\u05e9 \u05dc\u05e4\u05e2\u05d5\u05dc\u05ea \u05d4\u05e9\u05dc\u05d8 \u05d5\u05dc\u05d7\u05d9\u05d1\u05d5\u05e8 \u05d4-Bluetooth \u05e9\u05dc \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4.\n\n\u05d0\u05d9 \u05d0\u05e4\u05e9\u05e8 \u05dc\u05d4\u05e9\u05d1\u05d9\u05ea \u05d0\u05d5\u05ea\u05d5 \u05d1\u05e9\u05d5\u05dd \u05de\u05e6\u05d1.");
        r(l, "(.+) \\((.+)\\) is a core system package\\.\n\nDisabling it may break system settings, TV inputs, or cause instability\\. Disable anyway\\?",
                "{1} ({2}) \u05d4\u05d9\u05d0 \u05d7\u05d1\u05d9\u05dc\u05ea \u05dc\u05d9\u05d1\u05d4 \u05e9\u05dc \u05d4\u05de\u05e2\u05e8\u05db\u05ea.\n\n\u05d4\u05e9\u05d1\u05ea\u05d4 \u05e9\u05dc\u05d4 \u05e2\u05dc\u05d5\u05dc\u05d4 \u05dc\u05e9\u05d1\u05e9 \u05d0\u05ea \u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05d4\u05de\u05e2\u05e8\u05db\u05ea \u05d0\u05d5 \u05d0\u05ea \u05de\u05e7\u05d5\u05e8\u05d5\u05ea \u05d4\u05e7\u05dc\u05d8 \u05e9\u05dc \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4, \u05d0\u05d5 \u05dc\u05d2\u05e8\u05d5\u05dd \u05dc\u05d7\u05d5\u05e1\u05e8 \u05d9\u05e6\u05d9\u05d1\u05d5\u05ea. \u05dc\u05d4\u05e9\u05d1\u05d9\u05ea \u05d1\u05db\u05dc \u05d6\u05d0\u05ea?");
        r(l, "(.+) is a protected core system package and cannot be disabled automatically\\.",
                "{1} \u05d4\u05d9\u05d0 \u05d7\u05d1\u05d9\u05dc\u05ea \u05dc\u05d9\u05d1\u05d4 \u05de\u05d5\u05d2\u05e0\u05ea \u05e9\u05dc \u05d4\u05de\u05e2\u05e8\u05db\u05ea, \u05d5\u05d0\u05d9 \u05d0\u05e4\u05e9\u05e8 \u05dc\u05d4\u05e9\u05d1\u05d9\u05ea \u05d0\u05d5\u05ea\u05d4 \u05d1\u05d0\u05d5\u05e4\u05df \u05d0\u05d5\u05d8\u05d5\u05de\u05d8\u05d9.");
        r(l, "Checking installed packages and Wi-Fi status over ADB for (.+)\u2026",
                "\u05de\u05ea\u05d1\u05e6\u05e2\u05ea \u05d1\u05d3\u05d9\u05e7\u05d4 \u05e9\u05dc \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d4\u05de\u05d5\u05ea\u05e7\u05e0\u05d5\u05ea \u05d5\u05e9\u05dc \u05de\u05e6\u05d1 \u05d4-Wi-Fi \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB \u05e2\u05d1\u05d5\u05e8 {1}\u2026");
        r(l, "Checking installed packages over ADB for (.+)\u2026", "\u05de\u05ea\u05d1\u05e6\u05e2\u05ea \u05d1\u05d3\u05d9\u05e7\u05d4 \u05e9\u05dc \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d4\u05de\u05d5\u05ea\u05e7\u05e0\u05d5\u05ea \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB \u05e2\u05d1\u05d5\u05e8 {1}\u2026");
        r(l, "None of the packages listed in (.+) \\((.+)\\) are installed on this TV\\.\n\nWould you like to scan your TV's installed apps over ADB to find the closest matching TV model\\?",
                "\u05d0\u05e3 \u05d0\u05d7\u05ea \u05de\u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d1\u05e8\u05e9\u05d9\u05de\u05d4 \u05e9\u05dc {1} ({2}) \u05d0\u05d9\u05e0\u05d4 \u05de\u05d5\u05ea\u05e7\u05e0\u05ea \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d6\u05d5.\n\n\u05dc\u05e1\u05e8\u05d5\u05e7 \u05d0\u05ea \u05d4\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05de\u05d5\u05ea\u05e7\u05e0\u05d5\u05ea \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB \u05db\u05d3\u05d9 \u05dc\u05de\u05e6\u05d5\u05d0 \u05d0\u05ea \u05d4\u05d3\u05d2\u05dd \u05d4\u05ea\u05d5\u05d0\u05dd \u05d1\u05d9\u05d5\u05ea\u05e8?");
        r(l, "None of the packages listed in (.+) are installed on this TV\\.", "\u05d0\u05e3 \u05d0\u05d7\u05ea \u05de\u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d1\u05e8\u05e9\u05d9\u05de\u05d4 \u05e9\u05dc {1} \u05d0\u05d9\u05e0\u05d4 \u05de\u05d5\u05ea\u05e7\u05e0\u05ea \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d6\u05d5.");
        r(l, "Select Apps to Disable \u2014 (.+) \\((\\d+) found\\)", "\u05d1\u05d7\u05d9\u05e8\u05ea \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05dc\u05d4\u05e9\u05d1\u05ea\u05d4 \u2014 {1} (\u05e0\u05de\u05e6\u05d0\u05d5 {2})");
        r(l, "Starting ADB debloat for (\\d+) packages\u2026", "\u05de\u05ea\u05d7\u05d9\u05dc\u05d4 \u05d4\u05e1\u05e8\u05d4 \u05e9\u05dc \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05de\u05d9\u05d5\u05ea\u05e8\u05d5\u05ea \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB \u05e2\u05d1\u05d5\u05e8 {1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea\u2026");
        r(l, "Disabling \\((\\d+)/(\\d+)\\):\n(.*)", "\u05d4\u05e9\u05d1\u05ea\u05d4 ({1}/{2}):\n{3}");
        r(l, "Successfully disabled (\\d+) packages for (.+)\\.\n\n\u2022 Saved disabled app list \\((\\d+) apps\\) for one-tap Restore\\.\n\u2022 Flushed package state to disk \\(sync\\)\\.",
                "{1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d4\u05d5\u05e9\u05d1\u05ea\u05d5 \u05d1\u05d4\u05e6\u05dc\u05d7\u05d4 \u05e2\u05d1\u05d5\u05e8 {2}.\n\n\u2022 \u05e8\u05e9\u05d9\u05de\u05ea \u05d4\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05de\u05d5\u05e9\u05d1\u05ea\u05d5\u05ea ({3} \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea) \u05e0\u05e9\u05de\u05e8\u05d4 \u05dc\u05e9\u05d7\u05d6\u05d5\u05e8 \u05d1\u05dc\u05d7\u05d9\u05e6\u05d4 \u05d0\u05d7\u05ea.\n\u2022 \u05de\u05e6\u05d1 \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05e0\u05db\u05ea\u05d1 \u05dc\u05d3\u05d9\u05e1\u05e7 (sync).");
        r(l, "Disabled Apps \\((\\d+) \u2014 Tap an app to re-enable\\)", "\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05de\u05d5\u05e9\u05d1\u05ea\u05d5\u05ea ({1} \u2014 \u05d1\u05d7\u05d9\u05e8\u05d4 \u05d1\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d4 \u05de\u05e4\u05e2\u05d9\u05dc\u05d4 \u05d0\u05d5\u05ea\u05d4 \u05de\u05d7\u05d3\u05e9)");
        r(l, "Found saved disabled-app data \\((\\d+) packages\\)\\.\n\nRe-enable all (\\d+) saved packages over ADB\\?",
                "\u05e0\u05de\u05e6\u05d0\u05d5 \u05e0\u05ea\u05d5\u05e0\u05d9\u05dd \u05e9\u05de\u05d5\u05e8\u05d9\u05dd \u05e9\u05dc \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05de\u05d5\u05e9\u05d1\u05ea\u05d5\u05ea ({1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea).\n\n\u05dc\u05d4\u05e4\u05e2\u05d9\u05dc \u05de\u05d7\u05d3\u05e9 \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB \u05d0\u05ea \u05db\u05dc {2} \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d4\u05e9\u05de\u05d5\u05e8\u05d5\u05ea?");
        r(l, "Restore Saved \\((\\d+)\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05d4\u05e8\u05e9\u05d9\u05de\u05d4 \u05d4\u05e9\u05de\u05d5\u05e8\u05d4 ({1})");
        r(l, "Restore Model List \\((\\d+)\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05e8\u05e9\u05d9\u05de\u05ea \u05d4\u05d3\u05d2\u05dd ({1})");
        r(l, "No saved disabled-app list was found\\.\n\nRestore will use your selected TV model \\((.+)\\) and re-enable its (\\d+) packages over ADB\\.",
                "\u05dc\u05d0 \u05e0\u05de\u05e6\u05d0\u05d4 \u05e8\u05e9\u05d9\u05de\u05d4 \u05e9\u05de\u05d5\u05e8\u05d4 \u05e9\u05dc \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05de\u05d5\u05e9\u05d1\u05ea\u05d5\u05ea.\n\n\u05d4\u05e9\u05d7\u05d6\u05d5\u05e8 \u05d9\u05d9\u05e2\u05e9\u05d4 \u05dc\u05e4\u05d9 \u05d3\u05d2\u05dd \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05e9\u05e0\u05d1\u05d7\u05e8 ({1}), \u05d5-{2} \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05e9\u05dc\u05d5 \u05d9\u05d5\u05e4\u05e2\u05dc\u05d5 \u05de\u05d7\u05d3\u05e9 \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB.");
        r(l, "Restore \\((\\d+) pkgs\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 ({1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea)");
        r(l, "Re-enabling (\\d+) packages over ADB\u2026", "\u05de\u05ea\u05d1\u05e6\u05e2\u05ea \u05d4\u05e4\u05e2\u05dc\u05d4 \u05de\u05d7\u05d3\u05e9 \u05e9\u05dc {1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB\u2026");
        r(l, "Enabling \\((\\d+)/(\\d+)\\):\n(.*)", "\u05d4\u05e4\u05e2\u05dc\u05d4 ({1}/{2}):\n{3}");
        r(l, "Re-enabled (\\d+) packages on this TV and flushed state to disk \\(sync\\)\\.",
                "{1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d4\u05d5\u05e4\u05e2\u05dc\u05d5 \u05de\u05d7\u05d3\u05e9 \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d6\u05d5, \u05d5\u05d4\u05de\u05e6\u05d1 \u05e0\u05db\u05ea\u05d1 \u05dc\u05d3\u05d9\u05e1\u05e7 (sync).");

        // Wallpapers, backup and restore
        r(l, "Added custom wallpaper #(\\d+)\\.", "\u05e0\u05d5\u05e1\u05e4\u05d4 \u05ea\u05de\u05d5\u05e0\u05ea \u05e8\u05e7\u05e2 \u05de\u05d5\u05ea\u05d0\u05de\u05ea \u05d0\u05d9\u05e9\u05d9\u05ea \u05de\u05e1' {1}.");
        r(l, "Added favorite team: (.+)", "\u05e0\u05d5\u05e1\u05e4\u05d4 \u05e7\u05d1\u05d5\u05e6\u05d4 \u05de\u05d5\u05e2\u05d3\u05e4\u05ea: {1}");
        r(l, "No image files \\((.+)\\) in this folder", "\u05d0\u05d9\u05df \u05d1\u05ea\u05d9\u05e7\u05d9\u05d9\u05d4 \u05d6\u05d5 \u05e7\u05d5\u05d1\u05e6\u05d9 \u05ea\u05de\u05d5\u05e0\u05d4 ({1})");
        r(l, "Select this folder & shuffle \\((\\d+) images\\)", "\u05d1\u05d7\u05d9\u05e8\u05ea \u05d4\u05ea\u05d9\u05e7\u05d9\u05d9\u05d4 \u05d5\u05d4\u05e6\u05d2\u05d4 \u05d0\u05e7\u05e8\u05d0\u05d9\u05ea ({1} \u05ea\u05de\u05d5\u05e0\u05d5\u05ea)");
        r(l, "Your current tvOS Launcher settings have been saved to:\n\n(.*)", "\u05d4\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05d4\u05e0\u05d5\u05db\u05d7\u05d9\u05d5\u05ea \u05e9\u05dc tvOS Launcher \u05e0\u05e9\u05de\u05e8\u05d5 \u05d1\u05e0\u05ea\u05d9\u05d1:\n\n{1}");
        r(l, "Could not write to Download/([^\n]+)(\n\n.*?)?\n\nPlease allow storage access and try again\\.",
                "\u05dc\u05d0 \u05e0\u05d9\u05ea\u05df \u05d4\u05d9\u05d4 \u05dc\u05db\u05ea\u05d5\u05d1 \u05dc\u05e7\u05d5\u05d1\u05e5 Download/{1}{2}\n\n\u05d9\u05e9 \u05dc\u05d0\u05e9\u05e8 \u05d2\u05d9\u05e9\u05d4 \u05dc\u05d0\u05d7\u05e1\u05d5\u05df \u05d5\u05dc\u05e0\u05e1\u05d5\u05ea \u05e9\u05d5\u05d1.");
        r(l, "Restore from Download/(.+)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05de-Download/{1}");
        r(l, "Restore from this folder \\((.+)\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05de\u05ea\u05d9\u05e7\u05d9\u05d9\u05d4 \u05d6\u05d5 ({1})");
        r(l, "Settings were successfully restored from:\n(.*)", "\u05d4\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05e9\u05d5\u05d7\u05d6\u05e8\u05d5 \u05d1\u05d4\u05e6\u05dc\u05d7\u05d4 \u05de\u05d4\u05e7\u05d5\u05d1\u05e5:\n{1}");
        r(l, "Could not restore settings from:\n(.*)", "\u05dc\u05d0 \u05e0\u05d9\u05ea\u05df \u05d4\u05d9\u05d4 \u05dc\u05e9\u05d7\u05d6\u05e8 \u05d0\u05ea \u05d4\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05de\u05d4\u05e7\u05d5\u05d1\u05e5:\n{1}");
        r(l, "Folder shuffle enabled: (.+)", "\u05d4\u05e6\u05d2\u05d4 \u05d0\u05e7\u05e8\u05d0\u05d9\u05ea \u05de\u05ea\u05d9\u05e7\u05d9\u05d9\u05d4 \u05d4\u05d5\u05e4\u05e2\u05dc\u05d4: {1}");
        r(l, "Lock Screen folder shuffle enabled: (.+)", "\u05d4\u05e6\u05d2\u05d4 \u05d0\u05e7\u05e8\u05d0\u05d9\u05ea \u05de\u05ea\u05d9\u05e7\u05d9\u05d9\u05d4 \u05d4\u05d5\u05e4\u05e2\u05dc\u05d4 \u05d1\u05de\u05e1\u05da \u05d4\u05e0\u05e2\u05d9\u05dc\u05d4: {1}");
        r(l, "Custom (\\d+)", "\u05de\u05d5\u05ea\u05d0\u05dd \u05d0\u05d9\u05e9\u05d9\u05ea {1}");

        // Home screen
        r(l, "Can't open (.+)", "\u05dc\u05d0 \u05e0\u05d9\u05ea\u05df \u05dc\u05e4\u05ea\u05d5\u05d7 \u05d0\u05ea {1}");
        r(l, "Can't switch to (.+)", "\u05dc\u05d0 \u05e0\u05d9\u05ea\u05df \u05dc\u05e2\u05d1\u05d5\u05e8 \u05d0\u05dc {1}");
        r(l, "(.+) moved to Other Apps", "{1} \u05d4\u05d5\u05e2\u05d1\u05e8\u05d4 \u05dc\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05e0\u05d5\u05e1\u05e4\u05d5\u05ea");
        r(l, "(.+) hidden \\(Settings > Apps > Hidden apps\\)", "{1} \u05d4\u05d5\u05e1\u05ea\u05e8\u05d4 (\u05d4\u05d2\u05d3\u05e8\u05d5\u05ea > \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea > \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05de\u05d5\u05e1\u05ea\u05e8\u05d5\u05ea)");
        r(l, "(.+) is back on Home", "{1} \u05d7\u05d6\u05e8\u05d4 \u05dc\u05de\u05e1\u05da \u05d4\u05d1\u05d9\u05ea");
        r(l, "(.+) restored to Home Screen\\.", "{1} \u05d4\u05d5\u05d7\u05d6\u05e8\u05d4 \u05dc\u05de\u05e1\u05da \u05d4\u05d1\u05d9\u05ea.");
        r(l, "(.+) is not installed on this TV", "{1} \u05d0\u05d9\u05e0\u05d4 \u05de\u05d5\u05ea\u05e7\u05e0\u05ea \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d6\u05d5");
        r(l, "Delete \u201c(.+)\u201d\\?", "\u05dc\u05de\u05d7\u05d5\u05e7 \u05d0\u05ea \"{1}\"?");
        r(l, "Move (.+)  \u00b7  Left/Right, OK when done", "\u05d4\u05d6\u05d6\u05ea {1}  \u00b7  \u05d7\u05e6\u05d9\u05dd \u05d9\u05de\u05d9\u05e0\u05d4/\u05e9\u05de\u05d0\u05dc\u05d4, OK \u05dc\u05e1\u05d9\u05d5\u05dd");
        r(l, "Off in (\\d+) min", "\u05db\u05d9\u05d1\u05d5\u05d9 \u05d1\u05e2\u05d5\u05d3 {1} \u05d3\u05e7\u05f3");
        r(l, "(\\d+) min left", "\u05e0\u05d5\u05ea\u05e8\u05d5 {1} \u05d3\u05e7\u05f3");
        r(l, "(\\d+) h (\\d+) min left", "\u05e0\u05d5\u05ea\u05e8\u05d5 {1} \u05e9\u05e2\u05f3 \u05d5-{2} \u05d3\u05e7\u05f3");
        r(l, "Removed (.+)", "\u05d4\u05d5\u05e1\u05e8: {1}");
        r(l, "(\\d+) min ago", "\u05dc\u05e4\u05e0\u05d9 {1} \u05d3\u05e7\u05f3");
        r(l, "(\\d+) h ago", "\u05dc\u05e4\u05e0\u05d9 {1} \u05e9\u05e2\u05f3");
        r(l, "(\\d+) days ago", "\u05dc\u05e4\u05e0\u05d9 {1} \u05d9\u05de\u05d9\u05dd");
        r(l, "(\\d+)% used \u00b7 Recent Apps", "{1}% \u05d1\u05e9\u05d9\u05de\u05d5\u05e9 \u00b7 {k:Recent Apps}");
        r(l, "(\\d+)% used \u00b7 Clean", "{1}% \u05d1\u05e9\u05d9\u05de\u05d5\u05e9 \u00b7 {k:Clean}");
        r(l, "RAM (.+) used of (.+)", "RAM: {1} \u05d1\u05e9\u05d9\u05de\u05d5\u05e9 \u05de\u05ea\u05d5\u05da {2}");

        // Search
        r(l, "\u25b6  Open in (.+)", "\u25b6  \u05e4\u05ea\u05d9\u05d7\u05d4 \u05d1-{1}");
        r(l, "Get (.+)", "\u05d4\u05ea\u05e7\u05e0\u05ea {1}");

        // Stremio / Nuvio
        r(l, "To sign in, focus the (.+) tile on the Home screen and scan the QR code\\.",
                "\u05db\u05d3\u05d9 \u05dc\u05d4\u05ea\u05d7\u05d1\u05e8, \u05d9\u05e9 \u05dc\u05e1\u05de\u05df \u05d1\u05de\u05e1\u05da \u05d4\u05d1\u05d9\u05ea \u05d0\u05ea \u05d4\u05d0\u05e8\u05d9\u05d7 \u05e9\u05dc {1} \u05d5\u05dc\u05e1\u05e8\u05d5\u05e7 \u05d0\u05ea \u05e7\u05d5\u05d3 \u05d4-QR.");
        r(l, "Sign out of (.+)\\?", "\u05dc\u05d4\u05ea\u05e0\u05ea\u05e7 \u05de-{1}?");
        r(l, "Continue watching on the dock will stop showing your (.+) titles\\. The (.+) app itself stays signed in\\.",
                "\u05e9\u05d5\u05e8\u05ea \"\u05d4\u05de\u05e9\u05da \u05e6\u05e4\u05d9\u05d9\u05d4\" \u05d1-Dock \u05ea\u05e4\u05e1\u05d9\u05e7 \u05dc\u05d4\u05e6\u05d9\u05d2 \u05ea\u05db\u05e0\u05d9\u05dd \u05de-{1}. \u05d4\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d4 {2} \u05e2\u05e6\u05de\u05d4 \u05ea\u05d9\u05e9\u05d0\u05e8 \u05de\u05d7\u05d5\u05d1\u05e8\u05ea.");
        r(l, "Signed out of (.+)\\.", "\u05d1\u05d5\u05e6\u05e2\u05d4 \u05d4\u05ea\u05e0\u05ea\u05e7\u05d5\u05ea \u05de-{1}.");
        r(l, "Sign in to (.+)", "\u05d4\u05ea\u05d7\u05d1\u05e8\u05d5\u05ea \u05dc-{1}");
        r(l, "Couldn't reach (.+)\\. Focus the tile again to retry\\.", "\u05d0\u05d9\u05df \u05ea\u05e7\u05e9\u05d5\u05e8\u05ea \u05e2\u05dd {1}. \u05db\u05d3\u05d9 \u05dc\u05e0\u05e1\u05d5\u05ea \u05e9\u05d5\u05d1, \u05d9\u05e9 \u05dc\u05e1\u05de\u05df \u05d0\u05ea \u05d4\u05d0\u05e8\u05d9\u05d7 \u05de\u05d7\u05d3\u05e9.");
        r(l, "Couldn't reach (.+)", "\u05d0\u05d9\u05df \u05ea\u05e7\u05e9\u05d5\u05e8\u05ea \u05e2\u05dd {1}");

        // Settings
        r(l, "Dock size set to (\\d+)%\\.", "\u05d2\u05d5\u05d3\u05dc \u05d4-Dock \u05d4\u05d5\u05d2\u05d3\u05e8 \u05dc-{1}%.");
        r(l, "Switch to (.+)", "\u05de\u05e2\u05d1\u05e8 \u05d0\u05dc {t1}");
        r(l, "No direct model name match for (.+)\\. Use Scan Installed System Apps via ADB above to find the closest TV profile\\.",
                "\u05dc\u05d0 \u05e0\u05de\u05e6\u05d0\u05d4 \u05d4\u05ea\u05d0\u05de\u05d4 \u05d9\u05e9\u05d9\u05e8\u05d4 \u05dc\u05e9\u05dd \u05d4\u05d3\u05d2\u05dd {1}. \u05db\u05d3\u05d9 \u05dc\u05de\u05e6\u05d5\u05d0 \u05d0\u05ea \u05e4\u05e8\u05d5\u05e4\u05d9\u05dc \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d4\u05e7\u05e8\u05d5\u05d1 \u05d1\u05d9\u05d5\u05ea\u05e8, \u05d9\u05e9 \u05dc\u05d4\u05e9\u05ea\u05de\u05e9 \u05d1\u05e1\u05e8\u05d9\u05e7\u05ea \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05de\u05e2\u05e8\u05db\u05ea \u05d4\u05de\u05d5\u05ea\u05e7\u05e0\u05d5\u05ea \u05d1\u05d0\u05de\u05e6\u05e2\u05d5\u05ea ADB \u05e9\u05de\u05d5\u05e4\u05d9\u05e2\u05d4 \u05dc\u05de\u05e2\u05dc\u05d4.");
        r(l, "Selected model: (.+)", "\u05d4\u05d3\u05d2\u05dd \u05e9\u05e0\u05d1\u05d7\u05e8: {1}");
        r(l, "CLOSEST MATCHES \\((\\d+) APPS SCANNED\\)", "\u05d4\u05d4\u05ea\u05d0\u05de\u05d5\u05ea \u05d4\u05e7\u05e8\u05d5\u05d1\u05d5\u05ea \u05d1\u05d9\u05d5\u05ea\u05e8 (\u05e0\u05e1\u05e8\u05e7\u05d5 {1} \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea)");
        r(l, "PAGE (\\d+) OF (\\d+) \\((\\d+)\u2013(\\d+) OF (\\d+)\\)", "\u05e2\u05de\u05d5\u05d3 {1} \u05de\u05ea\u05d5\u05da {2} ({3}\u2013{4} \u05de\u05ea\u05d5\u05da {5})");
        r(l, "MODELS \\((\\d+)\\)", "\u05d3\u05d2\u05de\u05d9\u05dd ({1})");
        r(l, "Page (\\d+) of (\\d+)", "\u05e2\u05de\u05d5\u05d3 {1} \u05de\u05ea\u05d5\u05da {2}");
        r(l, "\u2705 Disable Selected Packages \\((\\d+)\\)", "\u2705 \u05d4\u05e9\u05d1\u05ea\u05ea \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05e9\u05e0\u05d1\u05d7\u05e8\u05d5 ({1})");
        r(l, "PACKAGES ON THIS TV \\((\\d+)\\)", "\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d6\u05d5 ({1})");
        r(l, "INSTALLED SYSTEM LAUNCHERS ON THIS TV \\((\\d+)\\)", "\u05de\u05e1\u05db\u05d9 \u05d1\u05d9\u05ea \u05e9\u05dc \u05d4\u05de\u05e2\u05e8\u05db\u05ea \u05e9\u05de\u05d5\u05ea\u05e7\u05e0\u05d9\u05dd \u05d1\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u05d6\u05d5 ({1})");
        r(l, "All \\((\\d+)\\)", "\u05d4\u05db\u05d5\u05dc ({1})");
        r(l, "ALL SYSTEM APPS \\((\\d+) \u2014 TAP TO DISABLE / ENABLE\\)", "\u05db\u05dc \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05de\u05e2\u05e8\u05db\u05ea ({1} \u2014 \u05d1\u05d7\u05d9\u05e8\u05d4 \u05dc\u05d4\u05e9\u05d1\u05ea\u05d4 / \u05dc\u05d4\u05e4\u05e2\u05dc\u05d4)");
        r(l, "Restore All Saved Packages \\((\\d+)\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05db\u05dc \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d4\u05e9\u05de\u05d5\u05e8\u05d5\u05ea ({1})");
        r(l, "Restore Saved Disabled Apps \\((\\d+) packages\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05d4\u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05de\u05d5\u05e9\u05d1\u05ea\u05d5\u05ea \u05d4\u05e9\u05de\u05d5\u05e8\u05d5\u05ea ({1} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea)");
        r(l, "Restore Selected Model Profile \\((.+) \u2014 (\\d+) packages\\)", "\u05e9\u05d7\u05d6\u05d5\u05e8 \u05e4\u05e8\u05d5\u05e4\u05d9\u05dc \u05d4\u05d3\u05d2\u05dd \u05e9\u05e0\u05d1\u05d7\u05e8 ({1} \u2014 {2} \u05d7\u05d1\u05d9\u05dc\u05d5\u05ea)");
        r(l, "Re-enable all packages in (.+)", "\u05d4\u05e4\u05e2\u05dc\u05d4 \u05de\u05d7\u05d3\u05e9 \u05e9\u05dc \u05db\u05dc \u05d4\u05d7\u05d1\u05d9\u05dc\u05d5\u05ea \u05d1-{1}");
        r(l, "No place found for \u201c(.+)\u201d\\.", "\u05dc\u05d0 \u05e0\u05de\u05e6\u05d0 \u05de\u05e7\u05d5\u05dd \u05d1\u05e9\u05dd \"{1}\".");
        r(l, "Location set to (.+)\\.", "\u05d4\u05de\u05d9\u05e7\u05d5\u05dd \u05d4\u05d5\u05d2\u05d3\u05e8: {1}.");

        // First-time setup
        r(l, "1\\. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options\\.\n"
                        + "2\\. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging\\.\n"
                        + "3\\. Return here and select Grant Permissions Now \\(ADB port 5555 will be closed automatically right after setup\\)\\.",
                "1. \u05d9\u05e9 \u05dc\u05e4\u05ea\u05d5\u05d7 \u05d0\u05ea \u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u2190 \u05de\u05e2\u05e8\u05db\u05ea \u2190 \u05de\u05d9\u05d3\u05e2 \u05db\u05dc\u05dc\u05d9 \u05d5\u05dc\u05dc\u05d7\u05d5\u05e5 7 \u05e4\u05e2\u05de\u05d9\u05dd \u05e2\u05dc \"\u05de\u05e1\u05e4\u05e8 Build\" \u05db\u05d3\u05d9 \u05dc\u05d4\u05e4\u05e2\u05d9\u05dc \u05d0\u05ea \u05d4\u05d0\u05e4\u05e9\u05e8\u05d5\u05d9\u05d5\u05ea \u05dc\u05de\u05e4\u05ea\u05d7\u05d9\u05dd.\n"
                        + "2. \u05d9\u05e9 \u05dc\u05e4\u05ea\u05d5\u05d7 \u05d0\u05ea \u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u2190 \u05de\u05e2\u05e8\u05db\u05ea \u2190 \u05d0\u05e4\u05e9\u05e8\u05d5\u05d9\u05d5\u05ea \u05dc\u05de\u05e4\u05ea\u05d7\u05d9\u05dd \u05d5\u05dc\u05d4\u05e4\u05e2\u05d9\u05dc \u05e0\u05d9\u05e4\u05d5\u05d9 \u05d1\u05d0\u05d2\u05d9\u05dd \u05d1-USB \u05d0\u05d5 \u05e0\u05d9\u05e4\u05d5\u05d9 \u05d1\u05d0\u05d2\u05d9\u05dd \u05d1\u05e8\u05e9\u05ea.\n"
                        + "3. \u05d9\u05e9 \u05dc\u05d7\u05d6\u05d5\u05e8 \u05dc\u05db\u05d0\u05df \u05d5\u05dc\u05d1\u05d7\u05d5\u05e8 \"{k:Grant Permissions Now}\" (\u05e4\u05d5\u05e8\u05d8 5555 \u05e9\u05dc ADB \u05d9\u05d9\u05e1\u05d2\u05e8 \u05d0\u05d5\u05d8\u05d5\u05de\u05d8\u05d9\u05ea \u05de\u05d9\u05d3 \u05d1\u05e1\u05d9\u05d5\u05dd \u05d4\u05d4\u05d2\u05d3\u05e8\u05d4).");
        r(l, "1\\. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options\\.\n"
                        + "2\\. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging\\.\n"
                        + "3\\. Return here and select Grant Permissions Now \\(ADB will remain enabled for CleanYourTV\\)\\.",
                "1. \u05d9\u05e9 \u05dc\u05e4\u05ea\u05d5\u05d7 \u05d0\u05ea \u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05d4\u05d8\u05dc\u05d5\u05d5\u05d9\u05d6\u05d9\u05d4 \u2190 \u05de\u05e2\u05e8\u05db\u05ea \u2190 \u05de\u05d9\u05d3\u05e2 \u05db\u05dc\u05dc\u05d9 \u05d5\u05dc\u05dc\u05d7\u05d5\u05e5 7 \u05e4\u05e2\u05de\u05d9\u05dd \u05e2\u05dc \"\u05de\u05e1\u05e4\u05e8 Build\" \u05db\u05d3\u05d9 \u05dc\u05d4\u05e4\u05e2\u05d9\u05dc \u05d0\u05ea \u05d4\u05d0\u05e4\u05e9\u05e8\u05d5\u05d9\u05d5\u05ea \u05dc\u05de\u05e4\u05ea\u05d7\u05d9\u05dd.\n"
                        + "2. \u05d9\u05e9 \u05dc\u05e4\u05ea\u05d5\u05d7 \u05d0\u05ea \u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u2190 \u05de\u05e2\u05e8\u05db\u05ea \u2190 \u05d0\u05e4\u05e9\u05e8\u05d5\u05d9\u05d5\u05ea \u05dc\u05de\u05e4\u05ea\u05d7\u05d9\u05dd \u05d5\u05dc\u05d4\u05e4\u05e2\u05d9\u05dc \u05e0\u05d9\u05e4\u05d5\u05d9 \u05d1\u05d0\u05d2\u05d9\u05dd \u05d1-USB \u05d0\u05d5 \u05e0\u05d9\u05e4\u05d5\u05d9 \u05d1\u05d0\u05d2\u05d9\u05dd \u05d1\u05e8\u05e9\u05ea.\n"
                        + "3. \u05d9\u05e9 \u05dc\u05d7\u05d6\u05d5\u05e8 \u05dc\u05db\u05d0\u05df \u05d5\u05dc\u05d1\u05d7\u05d5\u05e8 \"{k:Grant Permissions Now}\" (ADB \u05d9\u05d9\u05e9\u05d0\u05e8 \u05de\u05d5\u05e4\u05e2\u05dc \u05dc\u05e9\u05d9\u05de\u05d5\u05e9 \u05d1-CleanYourTV).");
        return l;
    }
}
