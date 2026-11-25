package com.techgenius.tvoslauncher;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Vietnamese dictionary and translations of messages containing runtime values. */
final class I18nVi {
    private I18nVi() { }

    private static Map<String, String> dict;

    static void init(Map<String, String> map) {
        dict = map;
        I18nViData.load(map);
    }

    /** Unknown strings and captured app names, paths and server messages remain unchanged. */
    static String dynamic(String en) {
        if (en == null || en.isEmpty()) return null;
        for (Rule rule : DynamicRules.ALL) {
            Matcher m = rule.pattern.matcher(en);
            if (m.matches()) return fill(rule.template, m);
        }
        return null;
    }

    private static String translate(String text) {
        if (text == null) return "";
        String value = dict != null ? dict.get(text) : null;
        if (value != null) return value;
        String translated = dynamic(text);
        return translated != null ? translated : text;
    }

    /** Same template tokens as I18nHe: {1} preserves data; {t1} translates UI labels. */
    private static String fill(String template, Matcher m) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < template.length();) {
            int close = template.charAt(i) == '{' ? template.indexOf('}', i) : -1;
            if (close > i) {
                String token = template.substring(i + 1, close);
                String value;
                if (token.startsWith("k:")) {
                    String key = token.substring(2);
                    value = dict != null ? dict.get(key) : null;
                    if (value == null) value = key;
                } else {
                    boolean translated = token.startsWith("t");
                    int group = Integer.parseInt(token.substring(translated ? 1 : 0));
                    value = m.group(group);
                    if (translated) value = translate(value);
                }
                if (value != null) out.append(value);
                i = close + 1;
            } else {
                out.append(template.charAt(i++));
            }
        }
        return out.toString();
    }

    static String profileName(String name) {
        if (name.equals("Default") || name.equals("Varsay\u0131lan")) return "M\u1eb7c \u0111\u1ecbnh";
        if (name.equals("Kids") || name.equals("\u00c7ocuklar")) return "Tr\u1ebb em";
        Matcher m = DynamicRules.PROFILE.matcher(name);
        return m.matches() ? "Ng\u01b0\u1eddi d\u00f9ng " + m.group(1) : name;
    }

    private static final class Rule {
        final Pattern pattern;
        final String template;

        Rule(String regex, String template) {
            this.pattern = Pattern.compile(regex, Pattern.DOTALL);
            this.template = template;
        }
    }

    /** Compile only when Vietnamese messages or default profile names need translation. */
    private static final class DynamicRules {
        static final Pattern PROFILE = Pattern.compile("(?:Person|Profile|Ki\u015fi|Profil) ([1-3])");
        static final Rule[] ALL = {
            new Rule("You are up to date \\(v(.+)\\)", "B\u1ea1n \u0111ang d\u00f9ng phi\u00ean b\u1ea3n m\u1edbi nh\u1ea5t (v{1})"), 
            new Rule("A new version of tvOS Launcher is available on GitHub\\.\nCurrent version: v(.+?)  \u2192  New version: v(.+?)\n\n(.*)", "\u0110\u00e3 c\u00f3 phi\u00ean b\u1ea3n tvOS Launcher m\u1edbi tr\u00ean GitHub.\nPhi\u00ean b\u1ea3n hi\u1ec7n t\u1ea1i: v{1}  \u2192  Phi\u00ean b\u1ea3n m\u1edbi: v{2}\n\n{3}"), 
            new Rule("Changelog \u2014 v(.+)", "C\u00e1c thay \u0111\u1ed5i \u2014 v{1}"), 
            new Rule("Downloading update v(.+)\u2026", "\u0110ang t\u1ea3i b\u1ea3n c\u1eadp nh\u1eadt v{1}\u2026"), 
            new Rule("Installing update v(.+) automatically via ADB\u2026", "\u0110ang t\u1ef1 \u0111\u1ed9ng c\u00e0i b\u1ea3n c\u1eadp nh\u1eadt v{1} qua ADB\u2026"), 
            new Rule("Removed remap for (.+)\\.", "\u0110\u00e3 x\u00f3a g\u00e1n n\u00fat cho {t1}."), 
            new Rule("Done \\((\\d+) apps disabled\\)", "Ho\u00e0n t\u1ea5t (\u0111\u00e3 v\u00f4 hi\u1ec7u h\u00f3a {1} \u1ee9ng d\u1ee5ng)"), 
            new Rule("Not done \\((\\d+)/(\\d+) disabled\\)", "Ch\u01b0a ho\u00e0n t\u1ea5t (\u0111\u00e3 v\u00f4 hi\u1ec7u h\u00f3a {1}/{2})"), 
            new Rule("Model: (.*)", "M\u1eabu TV: {1}"), 
            new Rule("Product: (.*)", "S\u1ea3n ph\u1ea9m: {1}"), 
            new Rule("Board/Device: (.*)", "Bo m\u1ea1ch/Thi\u1ebft b\u1ecb: {1}"), 
            new Rule("Brand/OEM: (.*)", "Th\u01b0\u01a1ng hi\u1ec7u/OEM: {1}"), 
            new Rule("(\\d+)/(\\d+) OEM pkgs: (.*)", "{1}/{2} g\u00f3i OEM: {3}"), 
            new Rule("OEM family: (.*)", "Nh\u00f3m OEM: {1}"), 
            new Rule("(\\d+)/(\\d+) apps \\((.*)\\)", "{1}/{2} \u1ee9ng d\u1ee5ng ({t3})"), 
            new Rule("Exact TV model found and selected automatically:\n\n\u2022 (.*)", "\u0110\u00e3 t\u00ecm th\u1ea5y v\u00e0 t\u1ef1 \u0111\u1ed9ng ch\u1ecdn \u0111\u00fang m\u1eabu TV:\n\n\u2022 {1}"), 
            new Rule("Choose Closest TV Model \u2014 (.*)", "Ch\u1ecdn m\u1eabu TV g\u1ea7n nh\u1ea5t \u2014 {1}"), 
            new Rule("Models with Similar System Apps \\((\\d+) apps scanned\\)", "C\u00e1c m\u1eabu c\u00f3 \u1ee9ng d\u1ee5ng h\u1ec7 th\u1ed1ng t\u01b0\u01a1ng t\u1ef1 (\u0111\u00e3 qu\u00e9t {1} \u1ee9ng d\u1ee5ng)"), 
            new Rule("All TV Models \\((\\d+)\\)", "T\u1ea5t c\u1ea3 m\u1eabu TV ({1})"), 
            new Rule("Search: \"(.*)\" \\((\\d+)\\)", "T\u00ecm ki\u1ebfm: \"{1}\" ({2})"), 
            new Rule("All disabled \\((\\d+)\\)", "\u0110\u00e3 v\u00f4 hi\u1ec7u h\u00f3a t\u1ea5t c\u1ea3 ({1})"), 
            new Rule("(\\d+) active / (\\d+) found \u203a", "{1} \u0111ang b\u1eadt / t\u00ecm th\u1ea5y {2} \u203a"), 
            new Rule("(.+) \\((.+)\\) is a critical system service required for your TV remote and Bluetooth connectivity\\.\n\nIt cannot be disabled under any circumstances\\.", "{1} ({2}) l\u00e0 d\u1ecbch v\u1ee5 h\u1ec7 th\u1ed1ng thi\u1ebft y\u1ebfu cho remote TV v\u00e0 k\u1ebft n\u1ed1i Bluetooth.\n\nKh\u00f4ng th\u1ec3 v\u00f4 hi\u1ec7u h\u00f3a d\u1ecbch v\u1ee5 n\u00e0y trong b\u1ea5t k\u1ef3 tr\u01b0\u1eddng h\u1ee3p n\u00e0o."), 
            new Rule("(.+) \\((.+)\\) is a core system package\\.\n\nDisabling it may break system settings, TV inputs, or cause instability\\. Disable anyway\\?", "{1} ({2}) l\u00e0 g\u00f3i h\u1ec7 th\u1ed1ng c\u1ed1t l\u00f5i.\n\nV\u00f4 hi\u1ec7u h\u00f3a c\u00f3 th\u1ec3 l\u00e0m h\u1ecfng c\u00e0i \u0111\u1eb7t h\u1ec7 th\u1ed1ng, ngu\u1ed3n v\u00e0o TV ho\u1eb7c g\u00e2y m\u1ea5t \u1ed5n \u0111\u1ecbnh. V\u1eabn v\u00f4 hi\u1ec7u h\u00f3a?"), 
            new Rule("(.+) is a protected core system package and cannot be disabled automatically\\.", "{1} l\u00e0 g\u00f3i h\u1ec7 th\u1ed1ng c\u1ed1t l\u00f5i \u0111\u01b0\u1ee3c b\u1ea3o v\u1ec7 v\u00e0 kh\u00f4ng th\u1ec3 t\u1ef1 \u0111\u1ed9ng v\u00f4 hi\u1ec7u h\u00f3a."), 
            new Rule("Checking installed packages and Wi-Fi status over ADB for (.+)\u2026", "\u0110ang ki\u1ec3m tra c\u00e1c g\u00f3i \u0111\u00e3 c\u00e0i v\u00e0 tr\u1ea1ng th\u00e1i Wi-Fi qua ADB cho {1}\u2026"), 
            new Rule("Checking installed packages over ADB for (.+)\u2026", "\u0110ang ki\u1ec3m tra c\u00e1c g\u00f3i \u0111\u00e3 c\u00e0i qua ADB cho {1}\u2026"), 
            new Rule("None of the packages listed in (.+) \\((.+)\\) are installed on this TV\\.\n\nWould you like to scan your TV's installed apps over ADB to find the closest matching TV model\\?", "Kh\u00f4ng c\u00f3 g\u00f3i n\u00e0o trong danh s\u00e1ch {1} ({2}) \u0111\u01b0\u1ee3c c\u00e0i tr\u00ean TV n\u00e0y.\n\nB\u1ea1n mu\u1ed1n qu\u00e9t \u1ee9ng d\u1ee5ng \u0111\u00e3 c\u00e0i tr\u00ean TV qua ADB \u0111\u1ec3 t\u00ecm m\u1eabu TV ph\u00f9 h\u1ee3p nh\u1ea5t?"), 
            new Rule("None of the packages listed in (.+) are installed on this TV\\.", "Kh\u00f4ng c\u00f3 g\u00f3i n\u00e0o trong danh s\u00e1ch {1} \u0111\u01b0\u1ee3c c\u00e0i tr\u00ean TV n\u00e0y."), 
            new Rule("Select Apps to Disable \u2014 (.+) \\((\\d+) found\\)", "Ch\u1ecdn \u1ee9ng d\u1ee5ng c\u1ea7n v\u00f4 hi\u1ec7u h\u00f3a \u2014 {1} (t\u00ecm th\u1ea5y {2})"), 
            new Rule("Starting ADB debloat for (\\d+) packages\u2026", "\u0110ang b\u1eaft \u0111\u1ea7u v\u00f4 hi\u1ec7u h\u00f3a {1} g\u00f3i qua ADB\u2026"), 
            new Rule("Disabling \\((\\d+)/(\\d+)\\):\n(.*)", "\u0110ang v\u00f4 hi\u1ec7u h\u00f3a ({1}/{2}):\n{3}"), 
            new Rule("Successfully disabled (\\d+) packages for (.+)\\.\n\n\u2022 Saved disabled app list \\((\\d+) apps\\) for one-tap Restore\\.\n\u2022 Flushed package state to disk \\(sync\\)\\.", "\u0110\u00e3 v\u00f4 hi\u1ec7u h\u00f3a th\u00e0nh c\u00f4ng {1} g\u00f3i cho {2}.\n\n\u2022 \u0110\u00e3 l\u01b0u danh s\u00e1ch \u1ee9ng d\u1ee5ng b\u1ecb v\u00f4 hi\u1ec7u h\u00f3a ({3} \u1ee9ng d\u1ee5ng) \u0111\u1ec3 kh\u00f4i ph\u1ee5c b\u1eb1ng m\u1ed9t l\u1ea7n ch\u1ecdn.\n\u2022 \u0110\u00e3 ghi tr\u1ea1ng th\u00e1i g\u00f3i xu\u1ed1ng b\u1ed9 nh\u1edb (sync)."), 
            new Rule("Disabled Apps \\((\\d+) \u2014 Tap an app to re-enable\\)", "\u1ee8ng d\u1ee5ng b\u1ecb v\u00f4 hi\u1ec7u h\u00f3a ({1} \u2014 Ch\u1ecdn \u1ee9ng d\u1ee5ng \u0111\u1ec3 b\u1eadt l\u1ea1i)"), 
            new Rule("Found saved disabled-app data \\((\\d+) packages\\)\\.\n\nRe-enable all (\\d+) saved packages over ADB\\?", "T\u00ecm th\u1ea5y d\u1eef li\u1ec7u \u1ee9ng d\u1ee5ng b\u1ecb v\u00f4 hi\u1ec7u h\u00f3a \u0111\u00e3 l\u01b0u ({1} g\u00f3i).\n\nB\u1eadt l\u1ea1i to\u00e0n b\u1ed9 {2} g\u00f3i \u0111\u00e3 l\u01b0u qua ADB?"), 
            new Rule("Restore Saved \\((\\d+)\\)", "Kh\u00f4i ph\u1ee5c danh s\u00e1ch \u0111\u00e3 l\u01b0u ({1})"), 
            new Rule("Restore Model List \\((\\d+)\\)", "Kh\u00f4i ph\u1ee5c danh s\u00e1ch m\u1eabu TV ({1})"), 
            new Rule("No saved disabled-app list was found\\.\n\nRestore will use your selected TV model \\((.+)\\) and re-enable its (\\d+) packages over ADB\\.", "Kh\u00f4ng t\u00ecm th\u1ea5y danh s\u00e1ch \u1ee9ng d\u1ee5ng b\u1ecb v\u00f4 hi\u1ec7u h\u00f3a \u0111\u00e3 l\u01b0u.\n\nKh\u00f4i ph\u1ee5c s\u1ebd d\u00f9ng m\u1eabu TV b\u1ea1n \u0111\u00e3 ch\u1ecdn ({1}) v\u00e0 b\u1eadt l\u1ea1i {2} g\u00f3i c\u1ee7a m\u1eabu n\u00e0y qua ADB."), 
            new Rule("Restore \\((\\d+) pkgs\\)", "Kh\u00f4i ph\u1ee5c ({1} g\u00f3i)"), 
            new Rule("Re-enabling (\\d+) packages over ADB\u2026", "\u0110ang b\u1eadt l\u1ea1i {1} g\u00f3i qua ADB\u2026"), 
            new Rule("Enabling \\((\\d+)/(\\d+)\\):\n(.*)", "\u0110ang b\u1eadt ({1}/{2}):\n{3}"), 
            new Rule("Re-enabled (\\d+) packages on this TV and flushed state to disk \\(sync\\)\\.", "\u0110\u00e3 b\u1eadt l\u1ea1i {1} g\u00f3i tr\u00ean TV n\u00e0y v\u00e0 ghi tr\u1ea1ng th\u00e1i xu\u1ed1ng b\u1ed9 nh\u1edb (sync)."), 
            new Rule("Added custom wallpaper #(\\d+)\\.", "\u0110\u00e3 th\u00eam h\u00ecnh n\u1ec1n t\u00f9y ch\u1ec9nh #{1}."), 
            new Rule("Added favorite team: (.+)", "\u0110\u00e3 th\u00eam \u0111\u1ed9i y\u00eau th\u00edch: {1}"), 
            new Rule("No image files \\((.+)\\) in this folder", "Kh\u00f4ng c\u00f3 file \u1ea3nh ({1}) trong th\u01b0 m\u1ee5c n\u00e0y"), 
            new Rule("Select this folder & shuffle \\((\\d+) images\\)", "Ch\u1ecdn th\u01b0 m\u1ee5c n\u00e0y v\u00e0 ph\u00e1t ng\u1eabu nhi\u00ean ({1} \u1ea3nh)"), 
            new Rule("Your current tvOS Launcher settings have been saved to:\n\n(.*)", "\u0110\u00e3 l\u01b0u c\u00e0i \u0111\u1eb7t tvOS Launcher hi\u1ec7n t\u1ea1i v\u00e0o:\n\n{1}"), 
            new Rule("Could not write to Download/([^\n]+)(\n\n.*?)?\n\nPlease allow storage access and try again\\.", "Kh\u00f4ng th\u1ec3 ghi v\u00e0o Download/{1}{2}\n\nH\u00e3y cho ph\u00e9p truy c\u1eadp b\u1ed9 nh\u1edb v\u00e0 th\u1eed l\u1ea1i."), 
            new Rule("Restore from Download/(.+)", "Kh\u00f4i ph\u1ee5c t\u1eeb Download/{1}"), 
            new Rule("Restore from this folder \\((.+)\\)", "Kh\u00f4i ph\u1ee5c t\u1eeb th\u01b0 m\u1ee5c n\u00e0y ({1})"), 
            new Rule("Settings were successfully restored from:\n(.*)", "\u0110\u00e3 kh\u00f4i ph\u1ee5c c\u00e0i \u0111\u1eb7t th\u00e0nh c\u00f4ng t\u1eeb:\n{1}"), 
            new Rule("Could not restore settings from:\n(.*)", "Kh\u00f4ng th\u1ec3 kh\u00f4i ph\u1ee5c c\u00e0i \u0111\u1eb7t t\u1eeb:\n{1}"), 
            new Rule("Folder shuffle enabled: (.+)", "\u0110\u00e3 b\u1eadt h\u00ecnh n\u1ec1n ng\u1eabu nhi\u00ean t\u1eeb th\u01b0 m\u1ee5c: {1}"), 
            new Rule("Lock Screen folder shuffle enabled: (.+)", "\u0110\u00e3 b\u1eadt h\u00ecnh n\u1ec1n m\u00e0n h\u00ecnh kh\u00f3a ng\u1eabu nhi\u00ean t\u1eeb th\u01b0 m\u1ee5c: {1}"), 
            new Rule("Custom (\\d+)", "T\u00f9y ch\u1ec9nh {1}"), 
            new Rule("Can't open (.+)", "Kh\u00f4ng th\u1ec3 m\u1edf {1}"), 
            new Rule("Can't switch to (.+)", "Kh\u00f4ng th\u1ec3 chuy\u1ec3n sang {1}"), 
            new Rule("(.+) moved to Other Apps", "\u0110\u00e3 chuy\u1ec3n {1} v\u00e0o \u1ee8ng d\u1ee5ng kh\u00e1c"), 
            new Rule("(.+) hidden \\(Settings > Apps > Hidden apps\\)", "\u0110\u00e3 \u1ea9n {1} (C\u00e0i \u0111\u1eb7t > \u1ee8ng d\u1ee5ng > \u1ee8ng d\u1ee5ng \u0111\u00e3 \u1ea9n)"), 
            new Rule("(.+) is back on Home", "{1} \u0111\u00e3 tr\u1edf l\u1ea1i m\u00e0n h\u00ecnh ch\u00ednh"), 
            new Rule("(.+) restored to Home Screen\\.", "\u0110\u00e3 \u0111\u01b0a {1} tr\u1edf l\u1ea1i m\u00e0n h\u00ecnh ch\u00ednh."), 
            new Rule("(.+) is not installed on this TV", "{1} ch\u01b0a \u0111\u01b0\u1ee3c c\u00e0i tr\u00ean TV n\u00e0y"), 
            new Rule("Delete \u201c(.+)\u201d\\?", "X\u00f3a \u201c{1}\u201d?"), 
            new Rule("Move (.+)  \u00b7  Left/Right, OK when done", "Di chuy\u1ec3n {1}  \u00b7  Tr\u00e1i/Ph\u1ea3i, nh\u1ea5n OK khi xong"), 
            new Rule("Off in (\\d+) min", "T\u1eaft sau {1} ph\u00fat"), 
            new Rule("(\\d+) min left", "C\u00f2n {1} ph\u00fat"), 
            new Rule("(\\d+) h (\\d+) min left", "C\u00f2n {1} gi\u1edd {2} ph\u00fat"), 
            new Rule("Removed (.+)", "\u0110\u00e3 x\u00f3a {1}"), 
            new Rule("(\\d+) min ago", "{1} ph\u00fat tr\u01b0\u1edbc"), 
            new Rule("(\\d+) h ago", "{1} gi\u1edd tr\u01b0\u1edbc"), 
            new Rule("(\\d+) days ago", "{1} ng\u00e0y tr\u01b0\u1edbc"), 
            new Rule("(\\d+)% used \u00b7 Recent Apps", "\u0110\u00e3 d\u00f9ng {1}% \u00b7 {k:Recent Apps}"), 
            new Rule("(\\d+)% used \u00b7 Clean", "\u0110\u00e3 d\u00f9ng {1}% \u00b7 {k:Clean}"), 
            new Rule("RAM (.+) used of (.+)", "RAM: \u0111\u00e3 d\u00f9ng {1} / {2}"), 
            new Rule("\u25b6  Open in (.+)", "\u25b6  M\u1edf b\u1eb1ng {1}"), 
            new Rule("Get (.+)", "C\u00e0i \u0111\u1eb7t {1}"), 
            new Rule("To sign in, focus the (.+) tile on the Home screen and scan the QR code\\.", "\u0110\u1ec3 \u0111\u0103ng nh\u1eadp, ch\u1ecdn \u00f4 {1} tr\u00ean m\u00e0n h\u00ecnh ch\u00ednh v\u00e0 qu\u00e9t m\u00e3 QR."), 
            new Rule("Sign out of (.+)\\?", "\u0110\u0103ng xu\u1ea5t kh\u1ecfi {1}?"), 
            new Rule("Continue watching on the dock will stop showing your (.+) titles\\. The (.+) app itself stays signed in\\.", "H\u00e0ng Ti\u1ebfp t\u1ee5c xem tr\u00ean Dock s\u1ebd kh\u00f4ng c\u00f2n hi\u1ec3n th\u1ecb n\u1ed9i dung {1} c\u1ee7a b\u1ea1n. \u1ee8ng d\u1ee5ng {2} v\u1eabn \u0111\u0103ng nh\u1eadp."), 
            new Rule("Signed out of (.+)\\.", "\u0110\u00e3 \u0111\u0103ng xu\u1ea5t kh\u1ecfi {1}."), 
            new Rule("Sign in to (.+)", "\u0110\u0103ng nh\u1eadp v\u00e0o {1}"), 
            new Rule("Couldn't reach (.+)\\. Focus the tile again to retry\\.", "Kh\u00f4ng th\u1ec3 k\u1ebft n\u1ed1i {1}. Ch\u1ecdn l\u1ea1i \u00f4 \u0111\u1ec3 th\u1eed l\u1ea1i."), 
            new Rule("Couldn't reach (.+)", "Kh\u00f4ng th\u1ec3 k\u1ebft n\u1ed1i {1}"), 
            new Rule("Dock size set to (\\d+)%\\.", "\u0110\u00e3 \u0111\u1eb7t k\u00edch th\u01b0\u1edbc Dock th\u00e0nh {1}%."), 
            new Rule("Switch to (.+)", "Chuy\u1ec3n sang {1}"),
            new Rule("No direct model name match for (.+)\\. Use Scan Installed System Apps via ADB above to find the closest TV profile\\.", "Kh\u00f4ng t\u00ecm th\u1ea5y m\u1eabu kh\u1edbp tr\u1ef1c ti\u1ebfp v\u1edbi t\u00ean {1}. D\u00f9ng Qu\u00e9t \u1ee9ng d\u1ee5ng h\u1ec7 th\u1ed1ng \u0111\u00e3 c\u00e0i qua ADB \u1edf tr\u00ean \u0111\u1ec3 t\u00ecm h\u1ed3 s\u01a1 TV ph\u00f9 h\u1ee3p nh\u1ea5t."), 
            new Rule("Selected model: (.+)", "M\u1eabu \u0111\u00e3 ch\u1ecdn: {1}"), 
            new Rule("CLOSEST MATCHES \\((\\d+) APPS SCANNED\\)", "K\u1ebeT QU\u1ea2 G\u1ea6N NH\u1ea4T (\u0110\u00c3 QU\u00c9T {1} \u1ee8NG D\u1ee4NG)"), 
            new Rule("PAGE (\\d+) OF (\\d+) \\((\\d+)\u2013(\\d+) OF (\\d+)\\)", "TRANG {1}/{2} ({3}\u2013{4} TR\u00caN {5})"), 
            new Rule("MODELS \\((\\d+)\\)", "M\u1eaaU TV ({1})"), 
            new Rule("Page (\\d+) of (\\d+)", "Trang {1}/{2}"), 
            new Rule("\u2705 Disable Selected Packages \\((\\d+)\\)", "\u2705 V\u00f4 hi\u1ec7u h\u00f3a g\u00f3i \u0111\u00e3 ch\u1ecdn ({1})"), 
            new Rule("PACKAGES ON THIS TV \\((\\d+)\\)", "G\u00d3I TR\u00caN TV N\u00c0Y ({1})"), 
            new Rule("INSTALLED SYSTEM LAUNCHERS ON THIS TV \\((\\d+)\\)", "LAUNCHER H\u1ec6 TH\u1ed0NG \u0110\u00c3 C\u00c0I TR\u00caN TV N\u00c0Y ({1})"), 
            new Rule("All \\((\\d+)\\)", "T\u1ea5t c\u1ea3 ({1})"), 
            new Rule("ALL SYSTEM APPS \\((\\d+) \u2014 TAP TO DISABLE / ENABLE\\)", "T\u1ea4T C\u1ea2 \u1ee8NG D\u1ee4NG H\u1ec6 TH\u1ed0NG ({1} \u2014 CH\u1eccN \u0110\u1ec2 V\u00d4 HI\u1ec6U H\u00d3A / B\u1eacT)"), 
            new Rule("Restore All Saved Packages \\((\\d+)\\)", "Kh\u00f4i ph\u1ee5c t\u1ea5t c\u1ea3 g\u00f3i \u0111\u00e3 l\u01b0u ({1})"), 
            new Rule("Restore Saved Disabled Apps \\((\\d+) packages\\)", "Kh\u00f4i ph\u1ee5c \u1ee9ng d\u1ee5ng b\u1ecb v\u00f4 hi\u1ec7u h\u00f3a \u0111\u00e3 l\u01b0u ({1} g\u00f3i)"), 
            new Rule("Restore Selected Model Profile \\((.+) \u2014 (\\d+) packages\\)", "Kh\u00f4i ph\u1ee5c h\u1ed3 s\u01a1 m\u1eabu TV \u0111\u00e3 ch\u1ecdn ({1} \u2014 {2} g\u00f3i)"), 
            new Rule("Re-enable all packages in (.+)", "B\u1eadt l\u1ea1i t\u1ea5t c\u1ea3 g\u00f3i trong {1}"), 
            new Rule("No place found for \u201c(.+)\u201d\\.", "Kh\u00f4ng t\u00ecm th\u1ea5y \u0111\u1ecba \u0111i\u1ec3m \u201c{1}\u201d."), 
            new Rule("Location set to (.+)\\.", "\u0110\u00e3 \u0111\u1eb7t v\u1ecb tr\u00ed th\u00e0nh {1}."), 
            new Rule("1\\. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options\\.\n2\\. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging\\.\n3\\. Return here and select Grant Permissions Now \\(ADB port 5555 will be closed automatically right after setup\\)\\.", "1. M\u1edf C\u00e0i \u0111\u1eb7t TV \u2192 H\u1ec7 th\u1ed1ng \u2192 Gi\u1edbi thi\u1ec7u v\u00e0 nh\u1ea5n S\u1ed1 b\u1ea3n d\u1ef1ng 7 l\u1ea7n \u0111\u1ec3 m\u1edf T\u00f9y ch\u1ecdn nh\u00e0 ph\u00e1t tri\u1ec3n.\n2. M\u1edf C\u00e0i \u0111\u1eb7t \u2192 H\u1ec7 th\u1ed1ng \u2192 T\u00f9y ch\u1ecdn nh\u00e0 ph\u00e1t tri\u1ec3n v\u00e0 b\u1eadt G\u1ee1 l\u1ed7i USB / G\u1ee1 l\u1ed7i m\u1ea1ng.\n3. Quay l\u1ea1i \u0111\u00e2y v\u00e0 ch\u1ecdn C\u1ea5p quy\u1ec1n ngay (c\u1ed5ng ADB 5555 s\u1ebd t\u1ef1 \u0111\u00f3ng ngay sau khi thi\u1ebft l\u1eadp)."), 
            new Rule("1\\. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options\\.\n2\\. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging\\.\n3\\. Return here and select Grant Permissions Now \\(ADB will remain enabled for CleanYourTV\\)\\.", "1. M\u1edf C\u00e0i \u0111\u1eb7t TV \u2192 H\u1ec7 th\u1ed1ng \u2192 Gi\u1edbi thi\u1ec7u v\u00e0 nh\u1ea5n S\u1ed1 b\u1ea3n d\u1ef1ng 7 l\u1ea7n \u0111\u1ec3 m\u1edf T\u00f9y ch\u1ecdn nh\u00e0 ph\u00e1t tri\u1ec3n.\n2. M\u1edf C\u00e0i \u0111\u1eb7t \u2192 H\u1ec7 th\u1ed1ng \u2192 T\u00f9y ch\u1ecdn nh\u00e0 ph\u00e1t tri\u1ec3n v\u00e0 b\u1eadt G\u1ee1 l\u1ed7i USB / G\u1ee1 l\u1ed7i m\u1ea1ng.\n3. Quay l\u1ea1i \u0111\u00e2y v\u00e0 ch\u1ecdn C\u1ea5p quy\u1ec1n ngay (ADB s\u1ebd ti\u1ebfp t\u1ee5c \u0111\u01b0\u1ee3c b\u1eadt cho CleanYourTV)."), 
            new Rule("Closed (\\d+) apps? \u00b7 freed (\\d+) MB", "\u0110\u00e3 \u0111\u00f3ng {1} \u1ee9ng d\u1ee5ng \u00b7 gi\u1ea3i ph\u00f3ng {2} MB"), 
            new Rule("Closed (\\d+) apps?", "\u0110\u00e3 \u0111\u00f3ng {1} \u1ee9ng d\u1ee5ng"), 
            new Rule("Move (.+)", "Di chuy\u1ec3n {1}"), 
            new Rule("(\\d+) minutes?", "{1} ph\u00fat"), 
            new Rule("(\\d+) seconds?", "{1} gi\u00e2y"), 
            new Rule("(\\d+) hours?", "{1} gi\u1edd"), 
            new Rule("Downloaded \\((\\d+) TV models ready\\)", "\u0110\u00e3 t\u1ea3i ({1} m\u1eabu TV s\u1eb5n s\u00e0ng)"),
            new Rule("1 model kept \\((.+)\\) \u00b7 Tap to download all", "\u0110\u00e3 gi\u1eef 1 m\u1eabu ({1}) \u00b7 Ch\u1ecdn \u0111\u1ec3 t\u1ea3i t\u1ea5t c\u1ea3"),
            new Rule("\u26a0\ufe0f Selected TV files missing \\((.+)\\) \u2014 Download CleanYourTV again", "\u26a0\ufe0f Thi\u1ebfu file c\u1ee7a TV \u0111\u00e3 ch\u1ecdn ({1}) \u2014 H\u00e3y t\u1ea3i l\u1ea1i CleanYourTV"),
            new Rule("Persistent ADB Mode \\(Keep port (\\d+) open\\)", "Ch\u1ebf \u0111\u1ed9 ADB th\u01b0\u1eddng tr\u1ef1c (gi\u1eef c\u1ed5ng {1} m\u1edf)"),
            new Rule("Connected & Paired \\(Port (\\d+) Open\\)", "\u0110\u00e3 k\u1ebft n\u1ed1i v\u00e0 gh\u00e9p n\u1ed1i (c\u1ed5ng {1} m\u1edf)"),
            new Rule("Port (\\d+) Open \u00b7 Ready to Pair", "C\u1ed5ng {1} m\u1edf \u00b7 S\u1eb5n s\u00e0ng gh\u00e9p n\u1ed1i"),
        };
    }
}
