package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * TV inputs (HDMI, AV, component, tuner) from Android's TV input framework, and switching to
 * one the way the stock launcher does: VIEW the input's passthrough "channel" so the TV app
 * shows it. Boxes without inputs (e.g. a Mi Box) report none, and the Inputs card and button hide.
 */
final class Inputs {
    static final String K_CARD = "inputs_card", K_BUTTON = "inputs_button";

    private Inputs() {
    }

    static final class Input {
        String id, label, origLabel;
        int type;
        boolean passthrough, disconnected, usb;
    }

    static String getInputKey(Input in) {
        if (in == null) return "";
        if (in.id != null && !in.id.isEmpty()) return in.id;
        if (in.origLabel != null && !in.origLabel.isEmpty()) return in.origLabel;
        return in.label != null ? in.label : "";
    }

    static boolean isInputHidden(Context c, String key) {
        if (key == null || key.isEmpty()) return false;
        return prefs(c).getBoolean("hide_" + key, false);
    }

    static void setInputHidden(Context c, String key, boolean hidden) {
        if (key == null || key.isEmpty()) return;
        prefs(c).edit().putBoolean("hide_" + key, hidden).apply();
    }

    static String getCustomInputLabel(Context c, String key) {
        if (key == null || key.isEmpty()) return "";
        return prefs(c).getString("label_" + key, "").trim();
    }

    static void setCustomInputLabel(Context c, String key, String customLabel) {
        if (key == null || key.isEmpty()) return;
        String trimmed = customLabel != null ? customLabel.trim() : "";
        if (trimmed.isEmpty()) {
            prefs(c).edit().remove("label_" + key).apply();
        } else {
            prefs(c).edit().putString("label_" + key, trimmed).apply();
        }
    }

    /** File/media apps that can browse a USB drive, in order of preference. */
    static final String[] USB_APPS = {"com.droidlogic.mediacenter", "com.droidlogic.FileBrower", "com.yunfang.files",
            "nextapp.fx", "com.android.documentsui"};

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("inputs", Context.MODE_PRIVATE);
    }

    static boolean showCard(Context c) {
        return prefs(c).getBoolean(K_CARD, true);
    }

    static boolean showButton(Context c) {
        return prefs(c).getBoolean(K_BUTTON, true);
    }

    /** Hardware inputs in a natural order: HDMI first, then AV and the rest, tuner last. */
    static List<Input> list(Context c) {
        List<Input> raw = listAllRaw(c);
        List<Input> out = new ArrayList<>();
        for (Input in : raw) {
            String key = getInputKey(in);
            if (isInputHidden(c, key)) continue;
            String customName = getCustomInputLabel(c, key);
            if (!customName.isEmpty()) {
                in.label = customName;
            }
            out.add(in);
        }
        return out;
    }

    /** Returns all inputs (including hidden ones) for Settings -> Control Panel -> Inputs manager. */
    static List<Input> listAllForSettings(Context c) {
        List<Input> all = listAllRaw(c);
        if (all.isEmpty()) {
            String[][] fallbacks = {
                {"fallback:hdmi1", "HDMI 1", String.valueOf(TvInputInfo.TYPE_HDMI)},
                {"fallback:hdmi2", "HDMI 2", String.valueOf(TvInputInfo.TYPE_HDMI)},
                {"fallback:hdmi3", "HDMI 3", String.valueOf(TvInputInfo.TYPE_HDMI)},
                {"fallback:hdmi4", "HDMI 4", String.valueOf(TvInputInfo.TYPE_HDMI)},
                {"fallback:tv", "TV", String.valueOf(TvInputInfo.TYPE_TUNER)},
                {"fallback:av", "AV", String.valueOf(TvInputInfo.TYPE_COMPOSITE)}
            };
            for (String[] fb : fallbacks) {
                Input in = new Input();
                in.id = fb[0];
                in.origLabel = fb[1];
                in.label = fb[1];
                in.type = Integer.parseInt(fb[2]);
                in.passthrough = in.type != TvInputInfo.TYPE_TUNER;
                all.add(in);
            }
        }
        for (Input in : all) {
            if (in.origLabel == null || in.origLabel.isEmpty()) {
                in.origLabel = in.label;
            }
            String key = getInputKey(in);
            String customName = getCustomInputLabel(c, key);
            if (!customName.isEmpty()) {
                in.label = customName;
            }
        }
        return all;
    }

    private static List<Input> listAllRaw(Context c) {
        List<Input> out = new ArrayList<>();
        TvInputManager tm;
        try {
            tm = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        } catch (Exception e) {
            return out;
        }
        if (tm == null) return out;
        List<TvInputInfo> infos;
        try {
            infos = tm.getTvInputList();
        } catch (Exception e) {
            return out;
        }
        for (TvInputInfo info : infos) {
            try {
                if (info.isHidden(c) || info.getParentId() != null) continue;
                if (!info.isPassthroughInput() && info.getType() != TvInputInfo.TYPE_TUNER) continue;
                // Only the TV's own hardware (HDMI, AV, built-in tuner): their ids end in "/HW<n>".
                // App-provided channel sources (Mi TV+, VLC previews...) are not inputs.
                if (!info.getId().contains("/HW")) continue;
                Input in = new Input();
                in.id = info.getId();
                in.type = info.getType();
                in.passthrough = info.isPassthroughInput();
                CharSequence custom = info.loadCustomLabel(c), label = info.loadLabel(c);
                in.origLabel = custom != null && custom.length() > 0 ? custom.toString() : label != null ? tidy(label.toString(), in.type) : I18n.t(c, "Input");
                in.label = in.origLabel;
                in.disconnected = tm.getInputState(in.id) == TvInputManager.INPUT_STATE_DISCONNECTED;
                out.add(in);
            } catch (Exception ignored) {
                // skip inputs that can't be read
            }
        }
        java.util.Collections.sort(out, (a, b) -> Integer.compare(rank(a), rank(b)) != 0 ? Integer.compare(rank(a), rank(b))
                : a.label.compareToIgnoreCase(b.label));
        // A plugged-in USB drive shows as its own source (opens the TV's file or media app).
        if (!out.isEmpty() && android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                android.os.storage.StorageManager sm = c.getSystemService(android.os.storage.StorageManager.class);
                for (android.os.storage.StorageVolume v : sm.getStorageVolumes()) {
                    if (!v.isRemovable() || !android.os.Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
                    Input in = new Input();
                    in.id = "usb:" + v.getUuid();
                    in.usb = true;
                    String d = v.getDescription(c);
                    in.origLabel = d == null || d.isEmpty() ? "USB" : "USB \u00b7 " + d;
                    in.label = in.origLabel;
                    out.add(in);
                }
            } catch (Exception ignored) {
                // no storage info
            }
        }
        return out;
    }

    private static int rank(Input in) {
        switch (in.type) {
            case TvInputInfo.TYPE_HDMI: return 0;
            case TvInputInfo.TYPE_DISPLAY_PORT: return 1;
            case TvInputInfo.TYPE_COMPONENT: return 2;
            case TvInputInfo.TYPE_COMPOSITE: return 3;
            case TvInputInfo.TYPE_TUNER: return 9;
            default: return 5;
        }
    }

    /** "HDMI1" -> "HDMI 1", "AV1" -> "AV 1"; tuners read "TV". */
    private static String tidy(String s, int type) {
        if (type == TvInputInfo.TYPE_TUNER && (s.toUpperCase().contains("TV") || s.isEmpty())) return "TV";
        return s.replaceAll("^([A-Za-z]+)(\\d+)$", "$1 $2");
    }

    /** Glyph for an input: from its type, or its name when the type is generic (e.g. "USB", "AV"). */
    static String glyph(Input in) {
        if (in.usb) return "usb";
        switch (in.type) {
            case TvInputInfo.TYPE_TUNER: return "antenna";
            case TvInputInfo.TYPE_HDMI: return "hdmi";
            case TvInputInfo.TYPE_COMPOSITE: return "av";
            case TvInputInfo.TYPE_COMPONENT: return "component";
            case TvInputInfo.TYPE_SVIDEO: return "svideo";
            case TvInputInfo.TYPE_SCART: return "scart";
            case TvInputInfo.TYPE_VGA:
            case TvInputInfo.TYPE_DVI: return "vga";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "displayport";
            default: break;
        }
        String l = in.label.toUpperCase(java.util.Locale.ROOT);
        if (l.contains("USB") || l.contains("MEDIA")) return "usb";
        if (l.contains("HDMI")) return "hdmi";
        if (l.startsWith("AV") || l.contains("COMPOSITE")) return "av";
        if (l.contains("YPBPR") || l.contains("COMPONENT")) return "component";
        if (l.contains("VGA") || l.contains("PC")) return "vga";
        if (l.contains("SCART")) return "scart";
        if (l.contains("TV") || l.contains("DTV") || l.contains("ATV")) return "antenna";
        return "input";
    }

    /** Switches the TV to the input. Returns false when no app can show it. */
    static boolean open(Context c, Input in) {
        if (in.usb) {
            for (String pkg : USB_APPS) {
                Intent l = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (l == null) l = c.getPackageManager().getLaunchIntentForPackage(pkg);
                if (l != null) {
                    c.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return true;
                }
            }
            return false;
        }
        Uri uri = in.passthrough ? TvContract.buildChannelUriForPassthroughInput(in.id) : TvContract.buildChannelsUriForInput(in.id);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, in.passthrough ? TvContract.Channels.CONTENT_ITEM_TYPE : TvContract.Channels.CONTENT_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }
}
