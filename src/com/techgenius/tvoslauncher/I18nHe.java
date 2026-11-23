package com.techgenius.tvoslauncher;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legacy Hebrew patterns for strings built with values.
 */
final class I18nHe {
    private I18nHe() { }

    private static Map<String, Integer> dictionaryIds;
    private static List<Rule> rules;

    private static final class Rule {
        final Pattern pattern;
        final int resourceId;
        final int translatedGroup;
        final String dictionaryKey;

        Rule(String regex, int resourceId, int translatedGroup, String dictionaryKey) {
            this.pattern = Pattern.compile(regex, Pattern.DOTALL);
            this.resourceId = resourceId;
            this.translatedGroup = translatedGroup;
            this.dictionaryKey = dictionaryKey;
        }
    }

    static synchronized String dictionaryString(Context c, String key) {
        if (dictionaryIds == null)
            dictionaryIds = I18n.loadLegacyIndex(c, R.array.legacy_he_keys, R.array.legacy_he_values);
        Integer id = dictionaryIds.get(key);
        return id != null ? I18n.resourceString(c, id) : null;
    }

    /** Translates a string built with values through its pattern, or null when no pattern matches. */
    static String dynamic(Context c, String en) {
        if (en == null || en.isEmpty()) return null;
        String closed = closedApps(c, en);
        if (closed != null) return closed;
        if (rules == null) rules = buildRules();
        for (Rule rule : rules) {
            Matcher m = rule.pattern.matcher(en);
            if (m.matches()) {
                int groups = m.groupCount();
                Object[] args = new Object[groups + (rule.dictionaryKey != null ? 1 : 0)];
                for (int i = 0; i < groups; i++) {
                    String value = m.group(i + 1);
                    if (rule.translatedGroup == i + 1) value = translate(c, value);
                    args[i] = value != null ? value : "";
                }
                if (rule.dictionaryKey != null) {
                    String value = dictionaryString(c, rule.dictionaryKey);
                    args[groups] = value != null ? value : rule.dictionaryKey;
                }
                return I18n.resourceString(c, rule.resourceId, args);
            }
        }
        return null;
    }

    /** "Closed 3 apps \u00b7 freed 120 MB" with the right singular and plural forms. */
    private static String closedApps(Context c, String en) {
        Matcher m = CLOSED.matcher(en);
        if (!m.matches()) return null;
        int n;
        try {
            n = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        return I18n.resourceQuantityString(c, m.group(2) != null
                        ? R.plurals.message_closed_apps_freed : R.plurals.message_closed_apps,
                n, Integer.toString(n), m.group(2));
    }

    private static final Pattern CLOSED = Pattern.compile("Closed (\\d+) apps?(?: \u00b7 freed (\\d+) MB)?");

    static String translate(Context c, String s) {
        if (s == null) return null;
        String direct = dictionaryString(c, s);
        if (direct != null) return direct;
        String d = dynamic(c, s);
        return d != null ? d : s;
    }

    private static void r(List<Rule> list, String regex, int resourceId) {
        r(list, regex, resourceId, 0, null);
    }

    private static void r(List<Rule> list, String regex, int resourceId, int translatedGroup,
                          String dictionaryKey) {
        list.add(new Rule(regex, resourceId, translatedGroup, dictionaryKey));
    }

    private static List<Rule> buildRules() {
        List<Rule> l = new ArrayList<>();
        // Updates
        r(l, "You are up to date \\(v(.+)\\)", R.string.message_update_current);
        r(l, "A new version of tvOS Launcher is available on GitHub\\.\nCurrent version: v(.+?)  \u2192  New version: v(.+?)\n\n(.*)",
                R.string.message_update_available_details, 3, null);
        r(l, "Changelog \u2014 v(.+)", R.string.message_update_changelog);
        r(l, "Downloading update v(.+)\u2026", R.string.message_update_downloading);
        r(l, "Installing update v(.+) automatically via ADB\u2026", R.string.message_update_installing_adb);

        // Button mapper
        r(l, "Removed remap for (.+)\\.", R.string.button_remap_removed, 1, null);

        // CleanYourTV
        r(l, "Done \\((\\d+) apps disabled\\)", R.string.cyt_summary_done);
        r(l, "Not done \\((\\d+)/(\\d+) disabled\\)", R.string.cyt_summary_not_done);
        r(l, "Model: (.*)", R.string.message_model_1_s);
        r(l, "Product: (.*)", R.string.message_product_1_s);
        r(l, "Board/Device: (.*)", R.string.message_board_device_1_s);
        r(l, "Brand/OEM: (.*)", R.string.cyt_brand_oem);
        r(l, "(\\d+)/(\\d+) OEM pkgs: (.*)", R.string.cyt_oem_package_match);
        r(l, "OEM family: (.*)", R.string.message_oem_family_1_s);
        r(l, "(\\d+)/(\\d+) apps \\((.*)\\)", R.string.cyt_package_match_reason, 3, null);
        r(l, "Exact TV model found and selected automatically:\n\n\u2022 (.*)", R.string.legacy_he_dynamic_09aabeb4c03e);
        r(l, "Choose Closest TV Model \u2014 (.*)", R.string.message_choose_closest_tv_model_1_s);
        r(l, "Models with Similar System Apps \\((\\d+) apps scanned\\)", R.string.cyt_similar_apps_scanned);
        r(l, "All TV Models \\((\\d+)\\)", R.string.cyt_all_tv_models);
        r(l, "Search: \"(.*)\" \\((\\d+)\\)", R.string.cyt_model_search);
        r(l, "All disabled \\((\\d+)\\)", R.string.legacy_he_dynamic_82314871b94c);
        r(l, "(\\d+) active / (\\d+) found \u203a", R.string.legacy_he_dynamic_f9b11dd83db2);
        r(l, "(.+) \\((.+)\\) is a critical system service required for your TV remote and Bluetooth connectivity\\.\n\nIt cannot be disabled under any circumstances\\.",
                R.string.cyt_critical_service_warning);
        r(l, "(.+) \\((.+)\\) is a core system package\\.\n\nDisabling it may break system settings, TV inputs, or cause instability\\. Disable anyway\\?",
                R.string.cyt_core_package_warning);
        r(l, "(.+) is a protected core system package and cannot be disabled automatically\\.",
                R.string.cyt_protected_package);
        r(l, "Checking installed packages and Wi-Fi status over ADB for (.+)\u2026",
                R.string.cyt_checking_model_wifi);
        r(l, "Checking installed packages over ADB for (.+)\u2026", R.string.cyt_checking_model);
        r(l, "None of the packages listed in (.+) \\((.+)\\) are installed on this TV\\.\n\nWould you like to scan your TV's installed apps over ADB to find the closest matching TV model\\?",
                R.string.cyt_no_model_packages_scan);
        r(l, "None of the packages listed in (.+) are installed on this TV\\.", R.string.cyt_no_model_packages);
        r(l, "Select Apps to Disable \u2014 (.+) \\((\\d+) found\\)", R.string.cyt_select_apps_for_model);
        r(l, "Starting ADB debloat for (\\d+) packages\u2026", R.string.cyt_starting_disable);
        r(l, "Disabling \\((\\d+)/(\\d+)\\):\n(.*)", R.string.cyt_disabling_progress);
        r(l, "Successfully disabled (\\d+) packages for (.+)\\.\n\n\u2022 Saved disabled app list \\((\\d+) apps\\) for one-tap Restore\\.\n\u2022 Flushed package state to disk \\(sync\\)\\.",
                R.string.cyt_disabled_for_model);
        r(l, "Disabled Apps \\((\\d+) \u2014 Tap an app to re-enable\\)", R.string.cyt_disabled_apps);
        r(l, "Found saved disabled-app data \\((\\d+) packages\\)\\.\n\nRe-enable all (\\d+) saved packages over ADB\\?",
                R.string.legacy_he_dynamic_5cacc9d910db);
        r(l, "Restore Saved \\((\\d+)\\)", R.string.cyt_restore_saved);
        r(l, "Restore Model List \\((\\d+)\\)", R.string.cyt_restore_model_list);
        r(l, "No saved disabled-app list was found\\.\n\nRestore will use your selected TV model \\((.+)\\) and re-enable its (\\d+) packages over ADB\\.",
                R.string.cyt_restore_model_confirmation);
        r(l, "Restore \\((\\d+) pkgs\\)", R.string.cyt_restore_package_count);
        r(l, "Re-enabling (\\d+) packages over ADB\u2026", R.string.cyt_starting_restore);
        r(l, "Enabling \\((\\d+)/(\\d+)\\):\n(.*)", R.string.cyt_enabling_progress);
        r(l, "Re-enabled (\\d+) packages on this TV and flushed state to disk \\(sync\\)\\.",
                R.string.cyt_restore_success);

        // Wallpapers, backup and restore
        r(l, "Added custom wallpaper #(\\d+)\\.", R.string.legacy_he_dynamic_15b45c924e73);
        r(l, "Added favorite team: (.+)", R.string.football_favorite_team_added);
        r(l, "No image files \\((.+)\\) in this folder", R.string.folder_no_image_files);
        r(l, "Select this folder & shuffle \\((\\d+) images\\)", R.string.folder_select_shuffle_count);
        r(l, "Your current tvOS Launcher settings have been saved to:\n\n(.*)", R.string.message_your_current_tvos_launcher_settings_have_been_sa);
        r(l, "Could not write to Download/([^\n]+)(\n\n.*?)?\n\nPlease allow storage access and try again\\.",
                R.string.legacy_he_dynamic_f6e850b4f2e6);
        r(l, "Restore from Download/(.+)", R.string.settings_restore_from_download);
        r(l, "Restore from this folder \\((.+)\\)", R.string.settings_restore_from_folder);
        r(l, "Settings were successfully restored from:\n(.*)", R.string.settings_restored_from_path);
        r(l, "Could not restore settings from:\n(.*)", R.string.settings_restore_failed_from_path);
        r(l, "Folder shuffle enabled: (.+)", R.string.folder_shuffle_enabled);
        r(l, "Lock Screen folder shuffle enabled: (.+)", R.string.lock_folder_shuffle_enabled);
        r(l, "Custom (\\d+)", R.string.scene_custom_number);

        // Home screen
        r(l, "Can't open (.+)", R.string.message_cannot_open_app);
        r(l, "Can't switch to (.+)", R.string.message_cannot_switch_input);
        r(l, "(.+) moved to Other Apps", R.string.message_app_moved_to_other_apps);
        r(l, "(.+) hidden \\(Settings > Apps > Hidden apps\\)", R.string.message_app_hidden);
        r(l, "(.+) is back on Home", R.string.message_app_back_home);
        r(l, "(.+) restored to Home Screen\\.", R.string.message_app_restored_home);
        r(l, "(.+) is not installed on this TV", R.string.message_app_not_installed);
        r(l, "Delete \u201c(.+)\u201d\\?", R.string.message_delete_profile);
        r(l, "Move (.+)  \u00b7  Left/Right, OK when done", R.string.dock_move_tooltip);
        r(l, "Off in (\\d+) min", R.string.message_sleep_off_in_minutes);
        r(l, "(\\d+) min left", R.string.remaining_minutes);
        r(l, "(\\d+) h (\\d+) min left", R.string.remaining_hours_minutes);
        r(l, "Removed (.+)", R.string.message_removed);
        r(l, "(\\d+) min ago", R.string.news_age_minutes);
        r(l, "(\\d+) h ago", R.string.news_age_hours);
        r(l, "(\\d+) days ago", R.string.legacy_he_dynamic_5b540d791476);
        r(l, "(\\d+)% used \u00b7 Recent Apps", R.string.legacy_he_dynamic_703dfc96737f, 0, "Recent Apps");
        r(l, "(\\d+)% used \u00b7 Clean", R.string.legacy_he_dynamic_699b3b53a882, 0, "Clean");
        r(l, "RAM (.+) used of (.+)", R.string.legacy_he_dynamic_92ea28b4da86);

        // Search
        r(l, "\u25b6  Open in (.+)", R.string.search_open_in);
        r(l, "Get (.+)", R.string.search_get_app);

        // Stremio / Nuvio
        r(l, "To sign in, focus the (.+) tile on the Home screen and scan the QR code\\.",
                R.string.message_cloud_sign_in_instructions);
        r(l, "Sign out of (.+)\\?", R.string.message_cloud_sign_out_question);
        r(l, "Continue watching on the dock will stop showing your (.+) titles\\. The (.+) app itself stays signed in\\.",
                R.string.legacy_he_dynamic_b61dee1514f6);
        r(l, "Signed out of (.+)\\.", R.string.message_cloud_signed_out);
        r(l, "Sign in to (.+)", R.string.message_cloud_sign_in);
        r(l, "Couldn't reach (.+)\\. Focus the tile again to retry\\.", R.string.message_cloud_unreachable_retry);
        r(l, "Couldn't reach (.+)", R.string.message_cloud_unreachable);

        // Settings
        r(l, "Dock size set to (\\d+)%\\.", R.string.dock_size_changed);
        r(l, "Switch to (.+)", R.string.profile_switch_to, 1, null);
        r(l, "No direct model name match for (.+)\\. Use Scan Installed System Apps via ADB above to find the closest TV profile\\.",
                R.string.cyt_no_direct_model);
        r(l, "Selected model: (.+)", R.string.cyt_selected_model);
        r(l, "CLOSEST MATCHES \\((\\d+) APPS SCANNED\\)", R.string.cyt_closest_matches_scanned);
        r(l, "PAGE (\\d+) OF (\\d+) \\((\\d+)\u2013(\\d+) OF (\\d+)\\)", R.string.cyt_page_range);
        r(l, "MODELS \\((\\d+)\\)", R.string.cyt_models_count);
        r(l, "Page (\\d+) of (\\d+)", R.string.cyt_page_number);
        r(l, "\u2705 Disable Selected Packages \\((\\d+)\\)", R.string.cyt_disable_selected_packages);
        r(l, "PACKAGES ON THIS TV \\((\\d+)\\)", R.string.cyt_packages_on_tv);
        r(l, "INSTALLED SYSTEM LAUNCHERS ON THIS TV \\((\\d+)\\)", R.string.legacy_he_dynamic_edecd490aeae);
        r(l, "All \\((\\d+)\\)", R.string.cyt_all_count);
        r(l, "ALL SYSTEM APPS \\((\\d+) \u2014 TAP TO DISABLE / ENABLE\\)", R.string.cyt_all_system_apps);
        r(l, "Restore All Saved Packages \\((\\d+)\\)", R.string.cyt_restore_all_saved_packages);
        r(l, "Restore Saved Disabled Apps \\((\\d+) packages\\)", R.string.cyt_restore_saved_disabled_apps);
        r(l, "Restore Selected Model Profile \\((.+) \u2014 (\\d+) packages\\)", R.string.cyt_restore_selected_model);
        r(l, "Re-enable all packages in (.+)", R.string.cyt_reenable_model_packages);
        r(l, "No place found for \u201c(.+)\u201d\\.", R.string.message_no_place_found);
        r(l, "Location set to (.+)\\.", R.string.message_location_set);

        // First-time setup
        r(l, "1\\. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options\\.\n"
                        + "2\\. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging\\.\n"
                        + "3\\. Return here and select Grant Permissions Now \\(ADB port 5555 will be closed automatically right after setup\\)\\.",
                R.string.setup_adb_close_steps);
        r(l, "1\\. Open TV Settings \u2192 System \u2192 About and click Build 7 times to unlock Developer Options\\.\n"
                        + "2\\. Open Settings \u2192 System \u2192 Developer Options and enable USB Debugging / Network Debugging\\.\n"
                        + "3\\. Return here and select Grant Permissions Now \\(ADB will remain enabled for CleanYourTV\\)\\.",
                R.string.setup_adb_keep_steps);
        return l;
    }
}
