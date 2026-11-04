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

    private static final android.os.Handler MAIN_HANDLER = new android.os.Handler(android.os.Looper.getMainLooper());
    private static MediaSessionManager fallbackMsm;
    private static final Map<MediaSession.Token, MediaController.Callback> fallbackTracked = new HashMap<>();
    private static boolean fallbackListenerRegistered = false;
    private static boolean periodicPollScheduled = false;
    private static long lastPollMs = 0L;
    private static Context trackedAppCtx;
    private static final MediaSessionManager.OnActiveSessionsChangedListener fallbackSessionsListener = list -> {
        if (trackedAppCtx != null) {
            syncControllersStatic(trackedAppCtx, list);
        }
    };
    private static final Runnable PERIODIC_POLL_TICK = new Runnable() {
        @Override
        public void run() {
            MAIN_HANDLER.removeCallbacks(this);
            if (trackedAppCtx == null) {
                periodicPollScheduled = false;
                return;
            }
            try {
                android.os.PowerManager pm = trackedAppCtx.getSystemService(android.os.PowerManager.class);
                if (pm == null || pm.isInteractive()) {
                    pollActiveSessionsImmediate(trackedAppCtx);
                }
            } catch (Throwable ignored) {
            }
            MAIN_HANDLER.postDelayed(this, 4000L);
        }
    };

    @Override
    public void onListenerConnected() {
        instance = this;
        trackedAppCtx = getApplicationContext();
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

    @Override
    public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        if (sbn != null) {
            pollActiveSessionsAsync(this);
        }
    }

    void onSessions(List<MediaController> list) {
        if (list == null) return;
        for (MediaController mc : list) {
            if (mc == null || mc.getSessionToken() == null) continue;
            if (!tracked.containsKey(mc.getSessionToken())) {
                MediaController.Callback cb = new MediaController.Callback() {
                    @Override public void onMetadataChanged(MediaMetadata m) {
                        Store.record(MediaWatchService.this, mc.getPackageName(), m, mc.getPlaybackState(), mc.getQueueTitle());
                    }

                    @Override public void onPlaybackStateChanged(PlaybackState s) {
                        Store.record(MediaWatchService.this, mc.getPackageName(), mc.getMetadata(), s, mc.getQueueTitle());
                    }

                    @Override public void onSessionDestroyed() {
                        tracked.remove(mc.getSessionToken());
                    }
                };
                try {
                    mc.registerCallback(cb, MAIN_HANDLER);
                    tracked.put(mc.getSessionToken(), cb);
                } catch (Throwable ignored) {
                }
            }
            Store.record(this, mc.getPackageName(), mc.getMetadata(), mc.getPlaybackState(), mc.getQueueTitle());
        }
    }

    /**
     * Ensures active MediaSession tracking is running even on low-RAM Android TVs (where
     * NotificationManagerService refuses to bind NotificationListenerService in the background,
     * yet MediaSessionManager.getActiveSessions() works as long as enabled_notification_listeners is set).
     */
    static void ensureSessionTracking(Context c) {
        if (c == null) return;
        final Context appCtx = c.getApplicationContext();
        trackedAppCtx = appCtx;
        MAIN_HANDLER.post(() -> {
            try {
                if (!granted(appCtx) && LocalAdb.hasWriteSecureSettings(appCtx)) {
                    LocalAdb.repairPermissionsViaSecureSettings(appCtx);
                }
                if (!granted(appCtx)) return;

                if (instance == null && android.os.Build.VERSION.SDK_INT >= 24) {
                    try {
                        requestRebind(new ComponentName(appCtx, MediaWatchService.class));
                    } catch (Throwable ignored) {
                    }
                }

                if (!periodicPollScheduled) {
                    periodicPollScheduled = true;
                    MAIN_HANDLER.postDelayed(PERIODIC_POLL_TICK, 4000L);
                }

                ComponentName me = new ComponentName(appCtx, MediaWatchService.class);
                if (instance != null && instance.msm != null) {
                    instance.onSessions(instance.msm.getActiveSessions(me));
                    return;
                }

                if (fallbackMsm == null) {
                    fallbackMsm = appCtx.getSystemService(MediaSessionManager.class);
                }
                if (fallbackMsm != null) {
                    if (!fallbackListenerRegistered) {
                        try {
                            fallbackMsm.addOnActiveSessionsChangedListener(fallbackSessionsListener, me, MAIN_HANDLER);
                            fallbackListenerRegistered = true;
                        } catch (Throwable ignored) {
                        }
                    }
                    List<MediaController> active = fallbackMsm.getActiveSessions(me);
                    syncControllersStatic(appCtx, active);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    static void pollActiveSessionsAsync(Context c) {
        if (c == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastPollMs < 1500L) return;
        lastPollMs = now;
        ensureSessionTracking(c);
    }

    static void pollActiveSessionsImmediate(Context c) {
        if (c == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastPollMs < 250L) return;
        lastPollMs = now;
        ensureSessionTracking(c);
    }

    private static void syncControllersStatic(Context appCtx, List<MediaController> list) {
        if (appCtx == null || list == null) return;
        for (MediaController mc : list) {
            if (mc == null || mc.getSessionToken() == null) continue;
            if (!fallbackTracked.containsKey(mc.getSessionToken())) {
                MediaController.Callback cb = new MediaController.Callback() {
                    @Override public void onMetadataChanged(MediaMetadata m) {
                        Store.record(appCtx, mc.getPackageName(), m, mc.getPlaybackState(), mc.getQueueTitle());
                    }

                    @Override public void onPlaybackStateChanged(PlaybackState s) {
                        Store.record(appCtx, mc.getPackageName(), mc.getMetadata(), s, mc.getQueueTitle());
                    }

                    @Override public void onSessionDestroyed() {
                        fallbackTracked.remove(mc.getSessionToken());
                    }
                };
                try {
                    mc.registerCallback(cb, MAIN_HANDLER);
                    fallbackTracked.put(mc.getSessionToken(), cb);
                } catch (Throwable ignored) {
                }
            }
            Store.record(appCtx, mc.getPackageName(), mc.getMetadata(), mc.getPlaybackState(), mc.getQueueTitle());
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
            record(c, pkg, m, s, null);
        }

        static synchronized void record(Context c, String pkg, MediaMetadata m, PlaybackState s, CharSequence queueTitle) {
            if (pkg == null || pkg.equals(c.getPackageName())) return;
            String title = "";
            String sub = "";
            long dur = 0L;
            if (m != null) {
                title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE,
                        MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                        MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_WRITER);
                sub = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                        MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION);
                if (sub.equals(title)) sub = "";
                dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            if (title.isEmpty() && queueTitle != null && queueTitle.length() > 0) {
                title = queueTitle.toString().trim();
            }
            if (title.isEmpty()) return;
            long pos = -1;
            int state = PlaybackState.STATE_NONE;
            if (s != null) {
                state = s.getState();
                pos = s.getPosition();
                if (state == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
                    pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
                }
            }
            String key = pkg + "|" + title;
            unmarkDismissed(c, key);
            JSONArray old = read(c);
            JSONArray out = new JSONArray();
            JSONObject prev = null;
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (key.equals(o.optString("key"))) prev = o;
                else out.put(o);
            }
            if (prev != null) {
                long prevPos = prev.optLong("pos", -1L);
                long prevDur = prev.optLong("dur", 0L);
                if (dur <= 0 && prevDur > 0) dur = prevDur;
                // When exiting a video player (Prime Video, HBO Max, TiviMate, ExoPlayer), the player often resets
                // position to 0 / -1 on STATE_STOPPED / STATE_PAUSED / STATE_NONE right before destroying the session.
                if (prevPos > 5000L && (pos <= 1000L || state == PlaybackState.STATE_STOPPED
                        || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_ERROR)) {
                    pos = prevPos;
                }
                if (sub.isEmpty()) sub = prev.optString("sub", "");
            }
            // Finished (last 3%): drop it from the list.
            if (dur > 0 && pos > dur * 0.97) {
                write(c, out);
                return;
            }
            try {
                String art = prev != null ? prev.optString("art") : "";
                Bitmap bmp = m != null ? m.getBitmap(MediaMetadata.METADATA_KEY_ART) : null;
                if (bmp == null && m != null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (bmp == null && m != null) bmp = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (bmp != null && bmp.getWidth() >= 64) {
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
                } else if (art.isEmpty() && m != null) {
                    String uri = text(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
                    if (uri.startsWith("http") || uri.startsWith("content://") || uri.startsWith("android.resource://")) {
                        art = uri;
                    }
                }
                String link = prev != null ? prev.optString("link", "") : "";
                String metaUri = m != null ? text(m, MediaMetadata.METADATA_KEY_MEDIA_URI) : "";
                if (metaUri.startsWith("http") || metaUri.startsWith("vnd.youtube") || metaUri.contains("://")) {
                    link = metaUri;
                }
                String artUri = m != null ? text(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) : "";
                String mediaId = m != null ? text(m, MediaMetadata.METADATA_KEY_MEDIA_ID) : "";
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
            markDismissed(c, key);
            JSONArray old = read(c), out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !key.equals(o.optString("key"))) out.put(o);
            }
            write(c, out);
        }

        private static java.util.Set<String> getDismissedKeys(Context c) {
            if (c == null) return java.util.Collections.emptySet();
            try {
                String pId = Profiles.current(c).id;
                return new java.util.HashSet<>(c.getSharedPreferences("cw_dismissed", Context.MODE_PRIVATE)
                        .getStringSet("keys_" + pId, java.util.Collections.emptySet()));
            } catch (Throwable t) {
                return java.util.Collections.emptySet();
            }
        }

        private static void markDismissed(Context c, String key) {
            if (c == null || key == null || key.isEmpty()) return;
            try {
                String pId = Profiles.current(c).id;
                android.content.SharedPreferences sp = c.getSharedPreferences("cw_dismissed", Context.MODE_PRIVATE);
                java.util.Set<String> cur = new java.util.HashSet<>(sp.getStringSet("keys_" + pId, java.util.Collections.emptySet()));
                cur.add(key);
                sp.edit().putStringSet("keys_" + pId, cur).apply();
            } catch (Throwable ignored) {
            }
        }

        private static void unmarkDismissed(Context c, String key) {
            if (c == null || key == null || key.isEmpty()) return;
            try {
                String pId = Profiles.current(c).id;
                android.content.SharedPreferences sp = c.getSharedPreferences("cw_dismissed", Context.MODE_PRIVATE);
                java.util.Set<String> orig = sp.getStringSet("keys_" + pId, null);
                if (orig != null && orig.contains(key)) {
                    java.util.Set<String> cur = new java.util.HashSet<>(orig);
                    cur.remove(key);
                    sp.edit().putStringSet("keys_" + pId, cur).apply();
                }
            } catch (Throwable ignored) {
            }
        }

        static synchronized void clear(Context c) {
            write(c, new JSONArray());
        }

        /** Newest first, as carousel items (pkg set, progress 0..1 or -1). */
        /** Titles not played again for this long drop off Continue Watching. */
        static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

        static List<Suggestions.Item> list(Context c) {
            JSONArray a = pruneExpired(c);
            java.util.Set<String> dismissed = getDismissedKeys(c);
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            List<Suggestions.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !Suggestions.installed(c, o.optString("pkg"))) continue;
                String k = o.optString("key");
                if (dismissed.contains(k) || !seenKeys.add(k)) continue;
                Suggestions.Item it = itemFromJson(o);
                out.add(it);
            }
            // Merge system Android TV Watch Next programs across installed apps
            for (Suggestions.Item wn : queryAllTvProviderWatchNext(c, 12)) {
                if (out.size() >= MAX) break;
                if (dismissed.contains(wn.key) || !seenKeys.add(wn.key)) continue;
                out.add(wn);
            }
            return out;
        }

        /**
         * Returns Continue Watching / Watch Next / Preview Channel items specifically for {@code pkg}
         * (combining MediaSession history + Android TV Watch Next + Android TV App Preview rows).
         */
        static List<Suggestions.Item> listForPackage(Context c, String pkg, int max) {
            if (c == null || pkg == null || pkg.isEmpty() || max <= 0) return java.util.Collections.emptyList();
            JSONArray a = pruneExpired(c);
            java.util.Set<String> dismissed = getDismissedKeys(c);
            java.util.Set<String> seenTitles = new java.util.HashSet<>();
            List<Suggestions.Item> out = new ArrayList<>();

            // 1. Active/recent MediaSession items recorded for this package
            for (int i = 0; i < a.length() && out.size() < max; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String oPkg = o.optString("pkg");
                if (!matchesPackage(pkg, oPkg)) continue;
                String k = o.optString("key");
                String tNorm = normalizeTitleKey(o.optString("title"));
                if (dismissed.contains(k) || tNorm.isEmpty() || !seenTitles.add(tNorm)) continue;
                Suggestions.Item it = itemFromJson(o);
                it.pkg = pkg;
                out.add(it);
            }

            // 2. Android TV TvProvider (watch_next_programs + preview_programs for this package)
            if (out.size() < max) {
                List<Suggestions.Item> tvItems = queryTvProviderForPackage(c, pkg, max * 2);
                for (Suggestions.Item tvIt : tvItems) {
                    if (out.size() >= max) break;
                    if (dismissed.contains(tvIt.key)) continue;
                    String tNorm = normalizeTitleKey(tvIt.title);
                    if (tNorm.isEmpty() || !seenTitles.add(tNorm)) continue;
                    out.add(tvIt);
                }
            }
            return out;
        }

        private static Suggestions.Item itemFromJson(JSONObject o) {
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
            return it;
        }

        private static String normalizeTitleKey(String t) {
            return t == null ? "" : t.trim().toLowerCase(java.util.Locale.ROOT);
        }

        private static boolean matchesPackage(String targetPkg, String candidatePkg) {
            if (targetPkg == null || candidatePkg == null) return false;
            if (targetPkg.equals(candidatePkg)) return true;
            if (("com.amazon.amazonvideo.livingroom".equals(targetPkg) || "com.amazon.avod.thirdpartyclient".equals(targetPkg))
                    && ("com.amazon.amazonvideo.livingroom".equals(candidatePkg) || "com.amazon.avod.thirdpartyclient".equals(candidatePkg))) {
                return true;
            }
            if (("com.wbd.stream".equals(targetPkg) || "com.hbo.hbonow".equals(targetPkg))
                    && ("com.wbd.stream".equals(candidatePkg) || "com.hbo.hbonow".equals(candidatePkg))) {
                return true;
            }
            return false;
        }

        private static List<String> packageCandidates(String pkg) {
            List<String> list = new ArrayList<>(2);
            list.add(pkg);
            if ("com.amazon.amazonvideo.livingroom".equals(pkg)) list.add("com.amazon.avod.thirdpartyclient");
            else if ("com.amazon.avod.thirdpartyclient".equals(pkg)) list.add("com.amazon.amazonvideo.livingroom");
            else if ("com.wbd.stream".equals(pkg)) list.add("com.hbo.hbonow");
            else if ("com.hbo.hbonow".equals(pkg)) list.add("com.wbd.stream");
            return list;
        }

        private static final class RawTvProgram {
            long id;
            String pkg;
            String title;
            String sub;
            String art;
            String link;
            long pos;
            long dur;
            long engagementTs;
            int weight;
            int priorityBucket; // 0 = watch_next, 1 = preview with progress, 2 = continue/recent channel, 3 = general preview
        }

        static List<Suggestions.Item> queryAllTvProviderWatchNext(Context c, int max) {
            List<Suggestions.Item> out = new ArrayList<>();
            if (c == null || max <= 0) return out;
            List<RawTvProgram> rows = new ArrayList<>();
            android.database.Cursor cur = null;
            try {
                android.net.Uri uri = android.net.Uri.parse("content://android.media.tv/watch_next_program");
                cur = c.getContentResolver().query(uri, null, null, null, null);
                if (cur != null) {
                    while (cur.moveToNext()) {
                        if (colInt(cur, "browsable", 1) == 0) continue;
                        String p = colStr(cur, "package_name");
                        if (p.isEmpty() || !Suggestions.installed(c, p)) continue;
                        RawTvProgram r = parseTvProgramRow(cur, p, "", "", 0);
                        if (r != null) rows.add(r);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (cur != null) {
                    try { cur.close(); } catch (Throwable ignored) { }
                }
            }
            java.util.Collections.sort(rows, (a, b) -> {
                int cmp = Long.compare(b.engagementTs, a.engagementTs);
                return cmp != 0 ? cmp : Long.compare(b.id, a.id);
            });
            for (RawTvProgram r : rows) {
                if (out.size() >= max) break;
                out.add(toSuggestionItem(r));
            }
            return out;
        }

        static List<Suggestions.Item> queryTvProviderForPackage(Context c, String pkg, int max) {
            List<Suggestions.Item> out = new ArrayList<>();
            if (c == null || pkg == null || pkg.isEmpty() || max <= 0) return out;
            List<RawTvProgram> rows = new ArrayList<>();

            for (String queryPkg : packageCandidates(pkg)) {
                // 1. Query watch_next_program?package=<queryPkg>
                android.database.Cursor wnCur = null;
                try {
                    android.net.Uri wnUri = android.net.Uri.parse("content://android.media.tv/watch_next_program")
                            .buildUpon().appendQueryParameter("package", queryPkg).build();
                    wnCur = c.getContentResolver().query(wnUri, null, null, null, null);
                    if (wnCur != null) {
                        while (wnCur.moveToNext()) {
                            if (colInt(wnCur, "browsable", 1) == 0) continue;
                            RawTvProgram r = parseTvProgramRow(wnCur, pkg, "", "", 0);
                            if (r != null) rows.add(r);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (wnCur != null) {
                        try { wnCur.close(); } catch (Throwable ignored) { }
                    }
                }

                // 2. Query channel?package=<queryPkg> to map channel_id -> display_name & app_link_intent_uri
                Map<Long, String> channelNames = new HashMap<>();
                Map<Long, String> channelLinks = new HashMap<>();
                Map<Long, Boolean> channelPriority = new HashMap<>();
                android.database.Cursor chCur = null;
                try {
                    android.net.Uri chUri = android.net.Uri.parse("content://android.media.tv/channel")
                            .buildUpon().appendQueryParameter("package", queryPkg).build();
                    chCur = c.getContentResolver().query(chUri, null, null, null, null);
                    if (chCur != null) {
                        while (chCur.moveToNext()) {
                            long cid = colLong(chCur, "_id", -1L);
                            if (cid < 0) continue;
                            String dName = colStr(chCur, "display_name");
                            String cLink = colStr(chCur, "app_link_intent_uri");
                            channelNames.put(cid, dName);
                            channelLinks.put(cid, cLink);
                            String low = dName.toLowerCase(java.util.Locale.ROOT);
                            boolean isPriority = low.contains("continue") || low.contains("watch next")
                                    || low.contains("resume") || low.contains("recent")
                                    || low.contains("history") || low.contains("up next")
                                    || low.contains("favorite") || low.contains("devam")
                                    || low.contains("son ") || low.contains("favori")
                                    || low.contains("시청");
                            channelPriority.put(cid, isPriority);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (chCur != null) {
                        try { chCur.close(); } catch (Throwable ignored) { }
                    }
                }

                // 3. Query preview_program?package=<queryPkg>
                android.database.Cursor ppCur = null;
                try {
                    android.net.Uri ppUri = android.net.Uri.parse("content://android.media.tv/preview_program")
                            .buildUpon().appendQueryParameter("package", queryPkg).build();
                    ppCur = c.getContentResolver().query(ppUri, null, null, null, null);
                    if (ppCur != null) {
                        while (ppCur.moveToNext()) {
                            if (colInt(ppCur, "browsable", 1) == 0) continue;
                            long cid = colLong(ppCur, "channel_id", -1L);
                            String chName = channelNames.getOrDefault(cid, "");
                            String chLink = channelLinks.getOrDefault(cid, "");
                            boolean isPriCh = Boolean.TRUE.equals(channelPriority.get(cid));
                            long pos = colLong(ppCur, "last_playback_position_millis", -1L);
                            int bucket = (pos > 0) ? 1 : (isPriCh ? 2 : 3);
                            RawTvProgram r = parseTvProgramRow(ppCur, pkg, chName, chLink, bucket);
                            if (r != null) rows.add(r);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (ppCur != null) {
                        try { ppCur.close(); } catch (Throwable ignored) { }
                    }
                }
            }

            java.util.Collections.sort(rows, (a, b) -> {
                int cmp = Integer.compare(a.priorityBucket, b.priorityBucket);
                if (cmp != 0) return cmp;
                if (a.priorityBucket == 0 || a.priorityBucket == 1) {
                    int eCmp = Long.compare(b.engagementTs, a.engagementTs);
                    if (eCmp != 0) return eCmp;
                    return Long.compare(b.id, a.id);
                }
                int wCmp = Integer.compare(b.weight, a.weight);
                if (wCmp != 0) return wCmp;
                return Long.compare(a.id, b.id);
            });

            for (RawTvProgram r : rows) {
                if (out.size() >= max) break;
                out.add(toSuggestionItem(r));
            }
            return out;
        }

        private static RawTvProgram parseTvProgramRow(
                android.database.Cursor cur, String pkg, String channelName, String channelLink, int bucket) {
            String title = colStr(cur, "title");
            String epTitle = colStr(cur, "episode_title");
            String seasonNum = colStr(cur, "season_display_number");
            String epNum = colStr(cur, "episode_display_number");
            String shortDesc = colStr(cur, "short_description");
            if (title.isEmpty() && !epTitle.isEmpty()) {
                title = epTitle;
                epTitle = "";
            }
            if (title.isEmpty()) return null;

            long pos = colLong(cur, "last_playback_position_millis", -1L);
            long dur = colLong(cur, "duration_millis", 0L);
            if (dur > 0 && pos > dur * 0.97) return null;

            StringBuilder subSb = new StringBuilder();
            if (!seasonNum.isEmpty() && !epNum.isEmpty()) {
                subSb.append('S').append(seasonNum).append(" · E").append(epNum);
                if (!epTitle.isEmpty() && !epTitle.equalsIgnoreCase(title)) {
                    subSb.append(" · ").append(epTitle);
                }
            } else if (!epNum.isEmpty()) {
                subSb.append('E').append(epNum);
                if (!epTitle.isEmpty() && !epTitle.equalsIgnoreCase(title)) {
                    subSb.append(" · ").append(epTitle);
                }
            } else if (!epTitle.isEmpty() && !epTitle.equalsIgnoreCase(title)) {
                subSb.append(epTitle);
            } else if (!shortDesc.isEmpty() && !shortDesc.equalsIgnoreCase(title)) {
                subSb.append(shortDesc);
            } else if (channelName != null && !channelName.isEmpty() && !channelName.equalsIgnoreCase(title)) {
                subSb.append(channelName);
            }

            String thumbUri = colStr(cur, "thumbnail_uri");
            String posterUri = colStr(cur, "poster_art_uri");
            String logoUri = colStr(cur, "logo_uri");
            String art = !thumbUri.isEmpty() ? thumbUri : (!posterUri.isEmpty() ? posterUri : logoUri);

            String intentUri = colStr(cur, "intent_uri");
            if (intentUri.isEmpty()) intentUri = colStr(cur, "preview_video_uri");
            if (intentUri.isEmpty() && channelLink != null) intentUri = channelLink;

            RawTvProgram r = new RawTvProgram();
            r.id = colLong(cur, "_id", 0L);
            r.pkg = pkg;
            r.title = title;
            r.sub = subSb.toString();
            r.art = art;
            r.link = intentUri;
            r.pos = pos;
            r.dur = dur;
            r.engagementTs = colLong(cur, "last_engagement_time_utc_millis", 0L);
            r.weight = colInt(cur, "weight", 0);
            r.priorityBucket = bucket;
            return r;
        }

        private static Suggestions.Item toSuggestionItem(RawTvProgram r) {
            Suggestions.Item it = new Suggestions.Item();
            it.key = r.pkg + "|" + r.title;
            it.id = it.key.hashCode();
            it.type = "continue";
            it.pkg = r.pkg;
            it.title = r.title;
            it.overview = r.sub != null ? r.sub : "";
            it.backdrop = r.art != null ? r.art : "";
            it.link = r.link != null ? r.link : "";
            it.year = "";
            it.genres = "";
            it.published = r.pos;
            it.progress = r.dur > 0 && r.pos > 0 ? Math.min(1f, r.pos / (float) r.dur) : -1f;
            it.remainingMin = r.dur > 0 && r.pos > 0 ? (int) Math.max(1, (r.dur - r.pos) / 60000L) : -1;
            return it;
        }

        private static String colStr(android.database.Cursor c, String col) {
            try {
                int idx = c.getColumnIndex(col);
                if (idx >= 0 && !c.isNull(idx)) {
                    String s = c.getString(idx);
                    return s != null ? s.trim() : "";
                }
            } catch (Throwable ignored) {
            }
            return "";
        }

        private static long colLong(android.database.Cursor c, String col, long def) {
            try {
                int idx = c.getColumnIndex(col);
                if (idx >= 0 && !c.isNull(idx)) {
                    return c.getLong(idx);
                }
            } catch (Throwable ignored) {
            }
            return def;
        }

        private static int colInt(android.database.Cursor c, String col, int def) {
            try {
                int idx = c.getColumnIndex(col);
                if (idx >= 0 && !c.isNull(idx)) {
                    return c.getInt(idx);
                }
            } catch (Throwable ignored) {
            }
            return def;
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

            // 1. Handle Android TV TvContract intent_uri ("intent://...#Intent;...;end" or "#Intent;...")
            if (existingLink.startsWith("intent:") || existingLink.contains("#Intent;")) {
                try {
                    android.content.Intent parsed = android.content.Intent.parseUri(
                            existingLink, android.content.Intent.URI_INTENT_SCHEME);
                    if (parsed != null) {
                        if (parsed.getPackage() == null && parsed.getComponent() == null && !pkg.isEmpty()) {
                            parsed.setPackage(pkg);
                        }
                        host.prepareForAppLaunch();
                        if (Suggestions.start(host, parsed)) {
                            host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                            host.io.execute(() -> host.noteLaunched(pkg));
                            return;
                        }
                        if (parsed.getPackage() != null) {
                            parsed.setPackage(null);
                            if (Suggestions.start(host, parsed)) {
                                host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                                host.io.execute(() -> host.noteLaunched(pkg));
                                return;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            if (isYouTubePkg(pkg)) {
                String ytId = extractYouTubeVideoId(existingLink);
                if (ytId.isEmpty()) ytId = extractYouTubeVideoId(it.backdrop);
                if (!ytId.isEmpty()) {
                    if (launchYouTubeVideo(host, pkg, ytId, posMs)) return;
                }
                if (!existingLink.isEmpty() && !existingLink.startsWith("intent:")) {
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
                try {
                    android.content.Intent viewIntent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, android.net.Uri.parse(existingLink));
                    if (!pkg.isEmpty()) viewIntent.setPackage(pkg);
                    host.prepareForAppLaunch();
                    if (Suggestions.start(host, viewIntent)) {
                        host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                        host.io.execute(() -> host.noteLaunched(pkg));
                        return;
                    }
                    if (!pkg.isEmpty()) {
                        viewIntent.setPackage(null);
                        if (Suggestions.start(host, viewIntent)) {
                            host.overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
                            host.io.execute(() -> host.noteLaunched(pkg));
                            return;
                        }
                    }
                } catch (Throwable ignored) {
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
