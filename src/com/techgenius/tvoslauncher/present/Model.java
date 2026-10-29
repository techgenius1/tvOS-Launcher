package com.techgenius.tvoslauncher.present;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Presentation data model, ported from pb-marketer-tv (Models.kt, JsonCodecs.kt, PlaylistRules.kt).
 * Wire format and validation are unchanged so existing behaviour is preserved.
 */
public final class Model {
    private Model() { }

    static final int DEFAULT_IMAGE_DURATION_SEC = 10;
    static final int DEFAULT_WEBPAGE_DURATION_SEC = 20;
    static final int DEFAULT_VIDEO_DURATION_SEC = 0;
    static final int DEFAULT_WEBPAGE_SCROLL_PAUSE_SEC = 5;
    static final int DEFAULT_WEBPAGE_SCROLL_SPEED_PX_PER_SEC = 40;

    static final String IMAGE = "image", VIDEO = "video", WEBPAGE = "webpage";
    static final String CONTAIN = "contain", COVER = "cover", FILL = "fill";

    public static final class Slide {
        String id = UUID.randomUUID().toString();
        String type;
        int durationSec;
        String mediaId;
        String url;
        String fitMode = CONTAIN;
        boolean muted = true;
        boolean loop = false;
        boolean webpageAutoScroll = true;
        int webpageScrollSpeedPxPerSec = DEFAULT_WEBPAGE_SCROLL_SPEED_PX_PER_SEC;
        int webpageScrollPauseSec = DEFAULT_WEBPAGE_SCROLL_PAUSE_SEC;
        boolean enabled = true;
        int order = 0;

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("type", type)
                    .put("durationSec", durationSec)
                    .put("mediaId", mediaId == null ? JSONObject.NULL : mediaId)
                    .put("url", url == null ? JSONObject.NULL : url)
                    .put("fitMode", fitMode)
                    .put("muted", muted)
                    .put("loop", loop)
                    .put("webpageAutoScroll", webpageAutoScroll)
                    .put("webpageScrollSpeedPxPerSec", webpageScrollSpeedPxPerSec)
                    .put("webpageScrollPauseSec", webpageScrollPauseSec)
                    .put("enabled", enabled)
                    .put("order", order);
        }

        static Slide fromJson(JSONObject json, int fallbackOrder) {
            String type = typeFromWire(json.optString("type"));
            if (type == null) throw new IllegalArgumentException("Slide type is required");
            int defaultDuration = IMAGE.equals(type) ? DEFAULT_IMAGE_DURATION_SEC
                    : VIDEO.equals(type) ? DEFAULT_VIDEO_DURATION_SEC : DEFAULT_WEBPAGE_DURATION_SEC;
            Slide s = new Slide();
            String id = json.optString("id");
            if (!id.trim().isEmpty()) s.id = id;
            s.type = type;
            s.durationSec = json.optInt("durationSec", defaultDuration);
            s.mediaId = optNullableString(json, "mediaId");
            s.url = optNullableString(json, "url");
            s.fitMode = fitFromWire(json.optString("fitMode"));
            s.muted = json.optBoolean("muted", true);
            s.loop = json.optBoolean("loop", false);
            s.webpageAutoScroll = json.optBoolean("webpageAutoScroll", true);
            s.webpageScrollSpeedPxPerSec = json.optInt("webpageScrollSpeedPxPerSec", DEFAULT_WEBPAGE_SCROLL_SPEED_PX_PER_SEC);
            s.webpageScrollPauseSec = json.optInt("webpageScrollPauseSec", DEFAULT_WEBPAGE_SCROLL_PAUSE_SEC);
            s.enabled = json.optBoolean("enabled", true);
            s.order = json.optInt("order", fallbackOrder);
            return s;
        }
    }

    public static final class Media {
        String id, fileName, displayName, mimeType;
        long sizeBytes, createdAt;

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("fileName", fileName)
                    .put("displayName", displayName)
                    .put("mimeType", mimeType)
                    .put("sizeBytes", sizeBytes)
                    .put("createdAt", createdAt);
        }

        /** "image", "video" or "" from MIME type, falling back to the extension. */
        String kind() {
            if (mimeType != null && mimeType.startsWith("image/")) return IMAGE;
            if (mimeType != null && mimeType.startsWith("video/")) return VIDEO;
            String ext = extension(displayName != null ? displayName : fileName);
            switch (ext) {
                case "jpg": case "jpeg": case "png": case "gif": case "webp": return IMAGE;
                case "mp4": case "m4v": case "webm": case "mkv": case "mov": case "3gp": case "3gpp": case "avi": return VIDEO;
                default: return "";
            }
        }
    }

    public static final class Playlist {
        String id, name;
        List<Slide> slides = new ArrayList<>();
        long createdAt, updatedAt;
        boolean active;

        JSONObject toJson() throws JSONException {
            JSONArray a = new JSONArray();
            for (Slide s : sortedByOrder(slides)) a.put(s.toJson());
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("slides", a)
                    .put("createdAt", createdAt)
                    .put("updatedAt", updatedAt)
                    .put("active", active);
        }
    }

    /** What the TV player is doing, for the browser's Now Playing bar. */
    public static final class Status {
        boolean available = true;
        String state = "idle";
        String playlistId, playlistName, slideId, slideType, mediaName, url, error;
        int slideIndex = -1, slideCount = 0;

        Status copy() {
            Status s = new Status();
            s.available = available;
            s.state = state;
            s.playlistId = playlistId;
            s.playlistName = playlistName;
            s.slideId = slideId;
            s.slideType = slideType;
            s.mediaName = mediaName;
            s.url = url;
            s.error = error;
            s.slideIndex = slideIndex;
            s.slideCount = slideCount;
            return s;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("available", available)
                    .put("state", state)
                    .put("playlistId", nul(playlistId))
                    .put("playlistName", nul(playlistName))
                    .put("slideId", nul(slideId))
                    .put("slideIndex", slideIndex)
                    .put("slideCount", slideCount)
                    .put("slideType", nul(slideType))
                    .put("mediaName", nul(mediaName))
                    .put("url", nul(url))
                    .put("error", nul(error));
        }
    }

    // ------------------------------------------------------------------ codecs

    static String slidesToJson(List<Slide> slides) {
        JSONArray a = new JSONArray();
        try {
            for (Slide s : sortedByOrder(slides)) a.put(s.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return a.toString();
    }

    static List<Slide> slidesFromJson(String value) {
        List<Slide> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(value);
            for (int i = 0; i < a.length(); i++) out.add(Slide.fromJson(a.getJSONObject(i), i));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return sortedByOrder(out);
    }

    /** Body of PUT /api/playlist: {name, slides[]} with order re-indexed by position. */
    static List<Slide> slidesFromRequest(JSONObject json) {
        JSONArray a = json.optJSONArray("slides");
        List<Slide> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            Slide s = Slide.fromJson(a.optJSONObject(i) == null ? new JSONObject() : a.optJSONObject(i), i);
            s.order = i;
            out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------ rules

    static List<String> validate(String name, List<Slide> slides) {
        List<String> errors = new ArrayList<>();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) errors.add("Playlist name is required");
        if (trimmed.length() > 80) errors.add("Playlist name must be 80 characters or less");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < slides.size(); i++) {
            Slide s = slides.get(i);
            String label = "Slide " + (i + 1);
            if (!ids.add(s.id)) errors.add(label + " has a duplicate id");
            if (!VIDEO.equals(s.type) && (s.durationSec < 1 || s.durationSec > 86_400)) {
                errors.add(label + " duration must be between 1 second and 24 hours");
            }
            if (s.order < 0) errors.add(label + " order must not be negative");
            if (IMAGE.equals(s.type) || VIDEO.equals(s.type)) {
                if (s.mediaId == null || s.mediaId.trim().isEmpty()) errors.add(label + " requires a media item");
            } else {
                String url = s.url == null ? "" : s.url;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    errors.add(label + " webpage URL must start with http:// or https://");
                }
                if (s.webpageScrollSpeedPxPerSec < 5 || s.webpageScrollSpeedPxPerSec > 1000) {
                    errors.add(label + " webpage scroll speed must be between 5 and 1000 pixels per second");
                }
                if (s.webpageScrollPauseSec < 0 || s.webpageScrollPauseSec > 300) {
                    errors.add(label + " webpage wait time must be between 0 and 300 seconds");
                }
            }
        }
        return errors;
    }

    static List<Slide> normalized(List<Slide> slides) {
        List<Slide> out = sortedByOrder(slides);
        for (int i = 0; i < out.size(); i++) out.get(i).order = i;
        return out;
    }

    // ------------------------------------------------------------------ helpers

    static List<Slide> sortedByOrder(List<Slide> slides) {
        List<Slide> out = new ArrayList<>(slides);
        Collections.sort(out, (a, b) -> Integer.compare(a.order, b.order));
        return out;
    }

    static String typeFromWire(String v) {
        if (v == null) return null;
        String t = v.trim().toLowerCase(Locale.US);
        return IMAGE.equals(t) || VIDEO.equals(t) || WEBPAGE.equals(t) ? t : null;
    }

    static String fitFromWire(String v) {
        String t = v == null ? "" : v.trim().toLowerCase(Locale.US);
        return COVER.equals(t) || FILL.equals(t) ? t : CONTAIN;
    }

    static String optNullableString(JSONObject json, String name) {
        if (!json.has(name) || json.isNull(name)) return null;
        String s = json.optString(name);
        return s.trim().isEmpty() ? null : s;
    }

    static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    static Object nul(Object o) {
        return o == null ? JSONObject.NULL : o;
    }
}
