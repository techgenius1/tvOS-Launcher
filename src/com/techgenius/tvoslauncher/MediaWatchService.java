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
                JSONObject e = new JSONObject().put("key", key).put("pkg", pkg).put("title", title).put("sub", sub)
                        .put("art", art).put("pos", pos).put("dur", dur).put("ts", System.currentTimeMillis());
                JSONArray res = new JSONArray().put(e);
                for (int i = 0; i < out.length() && res.length() < MAX; i++) res.put(out.get(i));
                write(c, res);
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
                it.year = "";
                it.genres = "";
                long pos = o.optLong("pos", -1), dur = o.optLong("dur", 0);
                it.progress = dur > 0 && pos >= 0 ? Math.min(1f, pos / (float) dur) : -1f;
                it.remainingMin = dur > 0 && pos >= 0 ? (int) Math.max(1, (dur - pos) / 60000) : -1;
                out.add(it);
            }
            return out;
        }
    }
}
