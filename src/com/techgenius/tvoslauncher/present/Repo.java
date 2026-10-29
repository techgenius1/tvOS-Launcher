package com.techgenius.tvoslauncher.present;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Media library and the single "Default" playlist (port of SignageRepository.kt / SignageDatabase.kt).
 * Same schema (signage.db: media, playlists) and storage (files/media/&lt;uuid&gt;.&lt;ext&gt;).
 */
final class Repo {
    static final String PLAYLIST_ID = "default", PLAYLIST_NAME = "Default";

    private final Db db;
    private final File mediaDir;

    Repo(Context c) {
        db = new Db(c.getApplicationContext());
        mediaDir = new File(c.getFilesDir(), "media");
        mediaDir.mkdirs();
        playlist();
    }

    // ------------------------------------------------------------------ media

    synchronized List<Model.Media> listMedia() {
        List<Model.Media> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query("media", null, null, null, null, null, "created_at DESC")) {
            while (c.moveToNext()) out.add(media(c));
        }
        return out;
    }

    synchronized Model.Media getMedia(String id) {
        if (id == null) return null;
        try (Cursor c = db.getReadableDatabase().query("media", null, "id = ?", new String[]{id}, null, null, null, "1")) {
            return c.moveToFirst() ? media(c) : null;
        }
    }

    File file(Model.Media m) {
        return new File(mediaDir, m.fileName);
    }

    /** Stores an upload. `src` is moved when possible (uploads are streamed to a temp file first). */
    synchronized Model.Media saveUpload(File src, String originalName, String mimeType) throws IOException {
        String id = UUID.randomUUID().toString();
        String display = sanitizeDisplayName(originalName);
        String fileName = storedFileName(id, display);
        File dest = new File(mediaDir, fileName);
        if (!src.renameTo(dest)) {
            try (InputStream in = new java.io.FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            src.delete();
        }
        Model.Media m = new Model.Media();
        m.id = id;
        m.fileName = fileName;
        m.displayName = display;
        m.mimeType = mimeType == null || mimeType.trim().isEmpty() ? guessMime(display) : mimeType;
        m.sizeBytes = dest.length();
        m.createdAt = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", m.id);
        v.put("file_name", m.fileName);
        v.put("display_name", m.displayName);
        v.put("mime_type", m.mimeType);
        v.put("size_bytes", m.sizeBytes);
        v.put("created_at", m.createdAt);
        db.getWritableDatabase().insertOrThrow("media", null, v);
        return m;
    }

    /** Deletes a file and any slides that use it. */
    synchronized boolean deleteMedia(String id) {
        Model.Media m = getMedia(id);
        if (m == null) return false;
        Model.Playlist p = playlist();
        List<Model.Slide> keep = new ArrayList<>();
        for (Model.Slide s : p.slides) if (!id.equals(s.mediaId)) keep.add(s);
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            w.delete("media", "id = ?", new String[]{id});
            if (keep.size() != p.slides.size()) {
                p.slides = Model.normalized(keep);
                p.updatedAt = System.currentTimeMillis();
                w.update("playlists", values(p), "id = ?", new String[]{p.id});
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        file(m).delete();
        return true;
    }

    // ------------------------------------------------------------------ playlist

    /** The single playlist, created (or migrated) on demand. */
    synchronized Model.Playlist playlist() {
        List<Model.Playlist> all = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query("playlists", null, null, null, null, null, "active DESC, updated_at DESC")) {
            while (c.moveToNext()) all.add(playlist(c));
        }
        if (all.size() == 1 && PLAYLIST_ID.equals(all.get(0).id) && PLAYLIST_NAME.equals(all.get(0).name) && all.get(0).active) {
            return all.get(0);
        }
        Model.Playlist sel = null;
        for (Model.Playlist p : all) if (p.active) { sel = p; break; }
        if (sel == null && !all.isEmpty()) sel = all.get(0);
        long now = System.currentTimeMillis();
        Model.Playlist r = new Model.Playlist();
        r.id = PLAYLIST_ID;
        r.name = PLAYLIST_NAME;
        r.slides = Model.normalized(sel == null ? new ArrayList<>() : sel.slides);
        r.createdAt = sel == null ? now : sel.createdAt;
        r.updatedAt = now;
        r.active = true;
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            w.delete("playlists", null, null);
            w.insertOrThrow("playlists", null, values(r));
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        return r;
    }

    synchronized Model.Playlist savePlaylist(List<Model.Slide> slides) {
        Model.Playlist p = playlist();
        List<Model.Slide> norm = Model.normalized(slides);
        List<String> errors = Model.validate(PLAYLIST_NAME, norm);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        p.name = PLAYLIST_NAME;
        p.slides = norm;
        p.updatedAt = System.currentTimeMillis();
        p.active = true;
        db.getWritableDatabase().update("playlists", values(p), "id = ?", new String[]{p.id});
        return p;
    }

    // ------------------------------------------------------------------ mapping

    private static ContentValues values(Model.Playlist p) {
        ContentValues v = new ContentValues();
        v.put("id", p.id);
        v.put("name", p.name);
        v.put("slides_json", Model.slidesToJson(p.slides));
        v.put("created_at", p.createdAt);
        v.put("updated_at", p.updatedAt);
        v.put("active", p.active ? 1 : 0);
        return v;
    }

    private static Model.Media media(Cursor c) {
        Model.Media m = new Model.Media();
        m.id = c.getString(c.getColumnIndexOrThrow("id"));
        m.fileName = c.getString(c.getColumnIndexOrThrow("file_name"));
        m.displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
        m.mimeType = c.getString(c.getColumnIndexOrThrow("mime_type"));
        m.sizeBytes = c.getLong(c.getColumnIndexOrThrow("size_bytes"));
        m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return m;
    }

    private static Model.Playlist playlist(Cursor c) {
        Model.Playlist p = new Model.Playlist();
        p.id = c.getString(c.getColumnIndexOrThrow("id"));
        p.name = c.getString(c.getColumnIndexOrThrow("name"));
        p.slides = Model.slidesFromJson(c.getString(c.getColumnIndexOrThrow("slides_json")));
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        p.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        p.active = c.getInt(c.getColumnIndexOrThrow("active")) == 1;
        return p;
    }

    static String sanitizeDisplayName(String original) {
        String raw = original == null ? "" : original;
        raw = raw.substring(raw.lastIndexOf('/') + 1);
        raw = raw.substring(raw.lastIndexOf('\\') + 1);
        String s = raw.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return s.isEmpty() ? "upload" : s;
    }

    static String storedFileName(String id, String display) {
        int dot = display.lastIndexOf('.');
        String ext = dot < 0 ? "" : display.substring(dot + 1);
        if (ext.length() < 1 || ext.length() > 12) return id;
        ext = ext.replaceAll("[^A-Za-z0-9]", "");
        return ext.isEmpty() ? id : id + "." + ext;
    }

    static String guessMime(String name) {
        switch (Model.extension(name)) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "mp4": case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            case "mov": return "video/quicktime";
            case "3gp": case "3gpp": return "video/3gpp";
            case "avi": return "video/x-msvideo";
            default: return "application/octet-stream";
        }
    }

    static final class Db extends SQLiteOpenHelper {
        Db(Context c) {
            super(c, "signage.db", null, 1);
        }

        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE media (id TEXT PRIMARY KEY NOT NULL, file_name TEXT NOT NULL, "
                    + "display_name TEXT NOT NULL, mime_type TEXT NOT NULL, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            d.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, slides_json TEXT NOT NULL, "
                    + "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 0)");
            d.execSQL("CREATE INDEX idx_playlists_active ON playlists(active)");
        }

        @Override public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
            if (oldV < 1) onCreate(d);
        }
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s.toLowerCase(Locale.US);
    }
}
