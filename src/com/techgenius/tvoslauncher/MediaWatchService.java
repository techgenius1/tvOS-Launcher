package com.techgenius.tvoslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Continue Watching list. Android's own Watch Next list is reserved for system
 * launchers, so instead this listens to the media sessions apps publish while they play (title,
 * artwork, position) and remembers the latest per title. Requires notification-listener access:
 *   adb shell cmd notification allow_listener com.techgenius.tvoslauncher/com.techgenius.tvoslauncher.MediaWatchService
 */
public class MediaWatchService extends NotificationListenerService {

    MediaSessionManager msm;
    final Map<MediaSession.Token, MediaController.Callback> tracked = new HashMap<>();
    final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::onSessions;

    /** Live instance while the listener is connected (Control Center uses it for Do Not Disturb). */
    static MediaWatchService instance;

    @Override
    public void onListenerConnected() {
        instance = this;
        try {
            msm = getSystemService(MediaSessionManager.class);
            if (msm != null) {
                ComponentName me = new ComponentName(this, MediaWatchService.class);
                msm.addOnActiveSessionsChangedListener(sessions, me);
                onSessions(msm.getActiveSessions(me));
            }
        } catch (Throwable e) {
            android.util.Log.w("tvOSLauncher", "media sessions: " + e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        } catch (Throwable ignored) {
        }
        tracked.clear();
    }

    void onSessions(List<MediaController> list) {
        if (list == null) return;
        for (MediaController mc : list) {
            if (tracked.containsKey(mc.getSessionToken())) continue;
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata m) {
                    Store.record(MediaWatchService.this, mc.getPackageName(), m, mc.getPlaybackState());
                }

                @Override public void onPlaybackStateChanged(PlaybackState s) {
                    Store.record(MediaWatchService.this, mc.getPackageName(), mc.getMetadata(), s);
                }

                @Override public void onSessionDestroyed() {
                    tracked.remove(mc.getSessionToken());
                }
            };
            mc.registerCallback(cb);
            tracked.put(mc.getSessionToken(), cb);
            Store.record(this, mc.getPackageName(), mc.getMetadata(), mc.getPlaybackState());
        }
    }

    static boolean granted(Context c) {
        if (instance != null) return true;
        if (c == null) return false;
        try {
            String flat = android.provider.Settings.Secure.getString(
                    c.getContentResolver(), "enabled_notification_listeners");
            if (flat != null && !flat.isEmpty()) {
                ComponentName cn = new ComponentName(c, MediaWatchService.class);
                if (flat.contains(cn.flattenToString()) || flat.contains(cn.flattenToShortString())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            try {
                android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
                return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, MediaWatchService.class));
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** The Continue Watching list on disk (files/continue.json + artwork in files/cw/). */
    static final class Store {
        static final int MAX = 20;

        static File file(Context c) {
            return new File(c.getFilesDir(), Profiles.continueFile(Profiles.current(c).id));
        }

        static synchronized JSONArray read(Context c) {
            try {
                return new JSONArray(new String(Suggestions.readAll(new FileInputStream(file(c))), StandardCharsets.UTF_8));
            } catch (Exception e) {
                return new JSONArray();
            }
        }

        static synchronized void write(Context c, JSONArray a) {
            try (OutputStream os = new FileOutputStream(file(c))) {
                os.write(a.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }

        static String text(MediaMetadata m, String... keys) {
            for (String k : keys) {
                CharSequence s = m.getText(k);
                if (s != null && s.length() > 0) return s.toString();
            }
            return "";
        }

        static synchronized void record(Context c, String pkg, MediaMetadata m, PlaybackState s) {
            if (m == null || pkg == null || pkg.equals(c.getPackageName())) return;
            String title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
            if (title.isEmpty()) return;
            String sub = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_ALBUM);
            long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            long pos = -1;
            if (s != null) {
                pos = s.getPosition();
                if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
                    pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
                }
            }
            String key = pkg + "|" + title;
            JSONArray old = read(c);
            JSONArray out = new JSONArray();
            JSONObject prev = null;
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (key.equals(o.optString("key"))) prev = o;
                else out.put(o);
            }
            // Finished (last 3%): drop it from the list.
            if (dur > 0 && pos > dur * 0.97) {
                write(c, out);
                return;
            }
            try {
                String art = prev != null ? prev.optString("art") : "";
                Bitmap bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (bmp == null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (bmp != null && bmp.getWidth() >= 160) {
                    File dir = new File(c.getFilesDir(), "cw");
                    dir.mkdirs();
                    File f = new File(dir, Integer.toHexString(key.hashCode()) + ".jpg");
                    float sc = Math.min(1f, 960f / bmp.getWidth());
                    Bitmap small = sc < 1f ? Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * sc),
                            Math.round(bmp.getHeight() * sc), true) : bmp;
                    try (OutputStream os = new FileOutputStream(f)) {
                        small.compress(Bitmap.CompressFormat.JPEG, 88, os);
                    }
                    art = f.getAbsolutePath();
                } else if (art.isEmpty()) {
                    String uri = text(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
                    if (uri.startsWith("http")) art = uri;
                }
                String link = prev != null ? prev.optString("link", "") : "";
                String metaUri = text(m, MediaMetadata.METADATA_KEY_MEDIA_URI);
                if (metaUri.startsWith("http") || metaUri.startsWith("vnd.youtube") || metaUri.contains("://")) {
                    link = metaUri;
                }
                String artUri = text(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
                String mediaId = text(m, MediaMetadata.METADATA_KEY_MEDIA_ID);
                if (isYouTubePkg(pkg)) {
                    String ytId = extractYouTubeVideoId(metaUri);
                    if (ytId.isEmpty()) ytId = extractYouTubeVideoId(artUri);
                    if (ytId.isEmpty()) ytId = extractYouTubeVideoId(mediaId);
                    if (!ytId.isEmpty()) {
                        link = "https://www.youtube.com/watch?v=" + ytId;
                    }
                }
                JSONObject e = new JSONObject().put("key", key).put("pkg", pkg).put("title", title).put("sub", sub)
                        .put("art", art).put("pos", pos).put("dur", dur).put("ts", System.currentTimeMillis())
                        .put("link", link);
                JSONArray res = new JSONArray().put(e);
                for (int i = 0; i < out.length() && res.length() < MAX; i++) res.put(out.get(i));
                write(c, res);

                if (isYouTubePkg(pkg) && link.isEmpty()) {
                    final Context appCtx = c.getApplicationContext();
                    final String fKey = key;
                    final String fTitle = title;
                    final String fSub = sub;
                    new Thread(() -> {
                        String resolvedId = searchYouTubeVideoId(fTitle, fSub);
                        if (!resolvedId.isEmpty()) {
                            updateLinkForKey(appCtx, fKey, "https://www.youtube.com/watch?v=" + resolvedId);
                        }
                    }, "yt-cw-resolve").start();
                }
            } catch (Exception ignored) {
            }
        }

        static synchronized void updateLinkForKey(Context c, String key, String link) {
            if (key == null || link == null || link.isEmpty()) return;
            try {
                JSONArray a = read(c);
                boolean changed = false;
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && key.equals(o.optString("key"))) {
                        o.put("link", link);
                        changed = true;
                    }
                }
                if (changed) write(c, a);
            } catch (Exception ignored) {
            }
        }

        /** Drops entries last played more than 7 days ago (and their saved artwork). */
        static synchronized JSONArray pruneExpired(Context c) {
            JSONArray a = read(c), keep = new JSONArray();
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (now - o.optLong("ts", now) > EXPIRE_MS) {
                    changed = true;
                    String art = o.optString("art");
                    if (art.startsWith("/")) new File(art).delete();
                } else {
                    keep.put(o);
                }
            }
            if (changed) write(c, keep);
            return keep;
        }

        static synchronized void remove(Context c, String key) {
            JSONArray old = read(c), out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !key.equals(o.optString("key"))) out.put(o);
            }
            write(c, out);
        }

        static synchronized void clear(Context c) {
            write(c, new JSONArray());
        }

        /** Newest first, as carousel items (pkg set, progress 0..1 or -1). */
        /** Titles not played again for this long drop off Continue Watching. */
        static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

        static List<Suggestions.Item> list(Context c) {
            JSONArray a = pruneExpired(c);
            List<Suggestions.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !Suggestions.installed(c, o.optString("pkg"))) continue;
                Suggestions.Item it = new Suggestions.Item();
                it.id = o.optString("key").hashCode();
                it.type = "continue";
                it.pkg = o.optString("pkg");
                it.key = o.optString("key");
                it.title = o.optString("title");
                it.overview = o.optString("sub");
                it.backdrop = o.optString("art");
                it.link = o.optString("link", "");
                it.year = "";
                it.genres = "";
                long pos = o.optLong("pos", -1), dur = o.optLong("dur", 0);
                it.published = pos; // store raw resume position (ms) for deep-link resume
                it.progress = dur > 0 && pos >= 0 ? Math.min(1f, pos / (float) dur) : -1f;
                it.remainingMin = dur > 0 && pos >= 0 ? (int) Math.max(1, (dur - pos) / 60000) : -1;
                out.add(it);
            }
            return out;
        }

        static boolean isYouTubePkg(String pkg) {
            if (pkg == null) return false;
            String p = pkg.toLowerCase(java.util.Locale.ROOT);
            return p.contains("youtube") || p.contains("smarttube") || p.contains("teamsmart.videomanager");
        }

        static String extractYouTubeVideoId(String raw) {
            if (raw == null || raw.isEmpty()) return "";
            String s = raw.trim();
            if (s.matches("^[a-zA-Z0-9_-]{11}$")) return s;
            java.util.regex.Matcher mVi = java.util.regex.Pattern
                    .compile("(?:/vi/|/vi_webp/|v=|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})")
                    .matcher(s);
            if (mVi.find()) return mVi.group(1);
            return "";
        }

        static String searchYouTubeVideoId(String title, String sub) {
            if (title == null || title.trim().isEmpty()) return "";
            java.net.HttpURLConnection conn = null;
            try {
                String q = title.trim() + (sub != null && !sub.trim().isEmpty() ? " " + sub.trim() : "");
                String urlStr = "https://www.youtube.com/results?search_query="
                        + java.net.URLEncoder.encode(q, "UTF-8") + "&hl=en";
                conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                try (java.io.InputStream in = conn.getInputStream()) {
                    byte[] bytes = Suggestions.readAll(in);
                    String html = new String(bytes, StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                            .matcher(html);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return "";
        }

        static void openContinueItem(MainActivity host, Suggestions.Item it, Runnable fallbackAppLaunch) {
            if (host == null || it == null) return;
            final String pkg = it.pkg != null ? it.pkg : "";
            final String existingLink = it.link != null ? it.link.trim() : "";
            final long posMs = it.published;

            if (isYouTubePkg(pkg)) {
                String ytId = extractYouTubeVideoId(existingLink);
                if (!ytId.isEmpty()) {
                    if (launchYouTubeVideo(host, pkg, ytId, posMs)) return;
                }
                // Resolve videoId asynchronously from title + channel name if not cached yet
                host.io.execute(() -> {
                    String resolvedId = searchYouTubeVideoId(it.title, it.overview);
                    if (!resolvedId.isEmpty()) {
                        updateLinkForKey(host.getApplicationContext(), it.key,
                                "https://www.youtube.com/watch?v=" + resolvedId);
                        it.link = "https://www.youtube.com/watch?v=" + resolvedId;
                        host.handler.post(() -> {
                            if (!launchYouTubeVideo(host, pkg, resolvedId, posMs) && fallbackAppLaunch != null) {
                                fallbackAppLaunch.run();
                            }
                        });
                    } else {
                        host.handler.post(() -> {
                            android.content.Intent searchIntent = new android.content.Intent(android.content.Intent.ACTION_SEARCH)
                                    .setPackage(pkg)
                                    .putExtra(android.app.SearchManager.QUERY, it.title);
                            host.prepareForAppLaunch();
                            if (Suggestions.start(host, searchIntent)) {
                                host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                                host.io.execute(() -> host.noteLaunched(pkg));
                            } else if (fallbackAppLaunch != null) {
                                fallbackAppLaunch.run();
                            }
                        });
                    }
                });
                return;
            }

            if (!existingLink.isEmpty()) {
                android.content.Intent viewIntent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(existingLink))
                        .setPackage(pkg);
                host.prepareForAppLaunch();
                if (Suggestions.start(host, viewIntent)) {
                    host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                    host.io.execute(() -> host.noteLaunched(pkg));
                    return;
                }
            }
            if (fallbackAppLaunch != null) {
                fallbackAppLaunch.run();
            }
        }

        private static boolean launchYouTubeVideo(MainActivity host, String pkg, String videoId, long posMs) {
            long resumeSec = posMs > 5000L ? (posMs / 1000L) : 0L;
            String watchUrl = "https://www.youtube.com/watch?v=" + videoId
                    + (resumeSec > 0 ? "&t=" + resumeSec + "s" : "");
            String vndUrl = "vnd.youtube://" + videoId
                    + (resumeSec > 0 ? "?t=" + resumeSec : "");
            String[] urls = {watchUrl, vndUrl};
            for (String u : urls) {
                android.content.Intent vi = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))
                        .setPackage(pkg);
                vi.putExtra("force_fullscreen", true);
                host.prepareForAppLaunch();
                if (Suggestions.start(host, vi)) {
                    host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                    host.io.execute(() -> host.noteLaunched(pkg));
                    return true;
                }
            }
            return false;
        }
    }
}
