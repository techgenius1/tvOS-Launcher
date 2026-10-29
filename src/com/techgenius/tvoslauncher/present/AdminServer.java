package com.techgenius.tvoslauncher.present;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The admin web server on port 8080 (replaces NanoHTTPD in pb-marketer-tv). Same API as the
 * original minus the removed admin-lock, debug telemetry and app-update endpoints:
 *
 *   GET  /api/info                     (no auth)  name, port, URLs, pairing required
 *   POST /api/pair {pin}               (no auth)  -> {token}
 *   GET  /api/media                    list        POST /api/media  multipart upload (field "file")
 *   DELETE /api/media/{id}                         GET  /api/media/{id}/file  (thumbnails/previews)
 *   GET/PUT /api/playlist (+ /api/playlists, /api/playlists/{id}[/activate] aliases)
 *   GET  /api/playback/status          POST /api/playback/control {command}
 *
 * Authorised calls carry "Authorization: Bearer &lt;token&gt;" (or ?token= for media file URLs).
 */
final class AdminServer {
    static final int PORT = 8080;
    private static final String TAG = "tvOSPresent";

    interface Gateway {
        void reload();

        boolean control(String command);

        Model.Status status();
    }

    private final Context context;
    private final Repo repo;
    private final Pairing pairing;
    private final Gateway gateway;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private volatile ServerSocket socket;
    private Thread acceptor;

    AdminServer(Context c, Repo repo, Pairing pairing, Gateway gateway) {
        this.context = c.getApplicationContext();
        this.repo = repo;
        this.pairing = pairing;
        this.gateway = gateway;
    }

    synchronized void start() throws IOException {
        if (socket != null && !socket.isClosed()) return;
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new java.net.InetSocketAddress(PORT));
        socket = s;
        acceptor = new Thread(() -> {
            while (!s.isClosed()) {
                try {
                    Socket client = s.accept();
                    pool.execute(() -> handle(client));
                } catch (IOException e) {
                    if (!s.isClosed()) Log.w(TAG, "accept: " + e);
                }
            }
        }, "present-http");
        acceptor.start();
    }

    synchronized void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
    }

    boolean running() {
        ServerSocket s = socket;
        return s != null && !s.isClosed();
    }

    // ------------------------------------------------------------------ request handling

    static final class Req {
        String method, path;
        final Map<String, String> headers = new HashMap<>();
        final Map<String, String> query = new HashMap<>();
        InputStream body;
        long contentLength;
    }

    private void handle(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(60_000);
            InputStream in = new BufferedInputStream(c.getInputStream(), 64 * 1024);
            OutputStream out = c.getOutputStream();
            Req r = parse(in);
            if (r == null) return;
            try {
                route(r, out);
            } catch (Exception e) {
                Log.w(TAG, r.method + " " + r.path + ": " + e);
                json(out, 500, new JSONObject().put("error", e.getMessage() == null ? "Unexpected server error" : e.getMessage()));
            }
        } catch (SocketException ignored) {
        } catch (Exception e) {
            Log.w(TAG, "connection: " + e);
        }
    }

    private static Req parse(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null || line.isEmpty()) return null;
        String[] parts = line.split(" ");
        if (parts.length < 2) return null;
        Req r = new Req();
        r.method = parts[0].toUpperCase(Locale.US);
        String target = parts[1];
        int q = target.indexOf('?');
        r.path = decode(q < 0 ? target : target.substring(0, q));
        if (q >= 0) {
            for (String kv : target.substring(q + 1).split("&")) {
                int e = kv.indexOf('=');
                if (e > 0) r.query.put(decode(kv.substring(0, e)), decode(kv.substring(e + 1)));
            }
        }
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            int colon = h.indexOf(':');
            if (colon > 0) r.headers.put(h.substring(0, colon).trim().toLowerCase(Locale.US), h.substring(colon + 1).trim());
        }
        try {
            r.contentLength = Long.parseLong(r.headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            r.contentLength = 0;
        }
        r.body = in;
        return r;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
            if (b.size() > 16 * 1024) throw new IOException("header too long");
        }
        if (ch == -1 && b.size() == 0) return null;
        return b.toString("ISO-8859-1");
    }

    private void route(Req r, OutputStream out) throws Exception {
        String p = r.path;
        if ("OPTIONS".equals(r.method)) {
            send(out, 200, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        // Web app
        if (p.equals("/") || p.equals("/index.html")) { asset(out, "index.html", "text/html; charset=utf-8"); return; }
        if (p.equals("/app.css")) { asset(out, "app.css", "text/css; charset=utf-8"); return; }
        if (p.equals("/app.js")) { asset(out, "app.js", "application/javascript; charset=utf-8"); return; }
        if (p.equals("/logo.png")) { asset(out, "logo.png", "image/png"); return; }
        if (p.equals("/icon.svg")) { asset(out, "icon.svg", "image/svg+xml"); return; }
        if (p.equals("/api/info")) { json(out, 200, info()); return; }
        if (p.equals("/api/pair") && "POST".equals(r.method)) {
            String token = pairing.pair(body(r).optString("pin"));
            if (token == null) json(out, 403, new JSONObject().put("error", "That PIN doesn\u2019t match the one on the TV."));
            else json(out, 200, new JSONObject().put("token", token));
            return;
        }
        if (!p.startsWith("/api/")) { asset(out, "index.html", "text/html; charset=utf-8"); return; }

        String auth = r.headers.getOrDefault("authorization", "");
        String token = auth.startsWith("Bearer") ? auth.substring(6).trim() : r.query.get("token");
        if (!pairing.valid(token)) {
            drain(r);
            json(out, 401, new JSONObject().put("error", "Pair with the TV PIN first"));
            return;
        }
        String[] seg = p.replaceAll("^/+|/+$", "").split("/");

        if (p.equals("/api/media") && "GET".equals(r.method)) {
            JSONArray a = new JSONArray();
            for (Model.Media m : repo.listMedia()) a.put(m.toJson());
            json(out, 200, new JSONObject().put("media", a));
        } else if (p.equals("/api/media") && "POST".equals(r.method)) {
            upload(r, out);
        } else if (seg.length == 4 && seg[1].equals("media") && seg[3].equals("file") && "GET".equals(r.method)) {
            mediaFile(r, out, seg[2]);
        } else if (seg.length == 3 && seg[1].equals("media") && "DELETE".equals(r.method)) {
            if (!repo.deleteMedia(seg[2])) json(out, 404, new JSONObject().put("error", "Media item not found"));
            else {
                gateway.reload();
                json(out, 200, new JSONObject().put("deleted", true));
            }
        } else if ((p.equals("/api/playlist") || p.equals("/api/playlists")) && "GET".equals(r.method)) {
            if (p.equals("/api/playlists")) json(out, 200, new JSONObject().put("playlists", new JSONArray().put(repo.playlist().toJson())));
            else json(out, 200, new JSONObject().put("playlist", repo.playlist().toJson()));
        } else if (seg.length == 3 && seg[1].equals("playlists") && "GET".equals(r.method)) {
            json(out, 200, new JSONObject().put("playlist", repo.playlist().toJson()));
        } else if ((p.equals("/api/playlist") && "PUT".equals(r.method))
                || (p.equals("/api/playlists") && "POST".equals(r.method))
                || (seg.length == 3 && seg[1].equals("playlists") && "PUT".equals(r.method))) {
            savePlaylist(r, out, "POST".equals(r.method) ? 201 : 200);
        } else if (seg.length == 3 && seg[1].equals("playlists") && "DELETE".equals(r.method)) {
            json(out, 400, new JSONObject().put("error", "Default playlist cannot be deleted"));
        } else if (seg.length == 4 && seg[1].equals("playlists") && seg[3].equals("activate") && "POST".equals(r.method)) {
            gateway.reload();
            json(out, 200, new JSONObject().put("playlist", repo.playlist().toJson()));
        } else if (p.equals("/api/playback/status") && "GET".equals(r.method)) {
            json(out, 200, new JSONObject().put("playback", gateway.status().toJson()));
        } else if (p.equals("/api/playback/control") && "POST".equals(r.method)) {
            String cmd = body(r).optString("command").toLowerCase(Locale.US);
            if (!cmd.matches("play|pause|next|previous|restart|showconnectioninfo|connectinfo|connection")) {
                json(out, 400, new JSONObject().put("error", "Unsupported playback command"));
            } else if (!gateway.control(cmd)) {
                json(out, 409, new JSONObject().put("error", "Presentation isn\u2019t on screen. Start it from the TV or turn on \u201cStart on boot\u201d."));
            } else {
                json(out, 200, new JSONObject().put("accepted", true));
            }
        } else {
            drain(r);
            json(out, 404, new JSONObject().put("error", "Endpoint not found"));
        }
    }

    private JSONObject info() throws Exception {
        String version = "";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        return new JSONObject()
                .put("name", "tvOS Presentation")
                .put("device", android.os.Build.MODEL)
                .put("port", PORT)
                .put("urls", new JSONArray(Net.urls(PORT)))
                .put("versionName", version)
                .put("pairingRequired", true);
    }

    private void savePlaylist(Req r, OutputStream out, int okStatus) throws Exception {
        List<Model.Slide> slides = Model.slidesFromRequest(body(r));
        List<String> errors = Model.validate(Repo.PLAYLIST_NAME, slides);
        if (!errors.isEmpty()) {
            json(out, 400, new JSONObject().put("errors", new JSONArray(errors)));
            return;
        }
        Model.Playlist p = repo.savePlaylist(slides);
        gateway.reload();
        json(out, okStatus, new JSONObject().put("playlist", p.toJson()));
    }

    // ------------------------------------------------------------------ uploads

    /**
     * Streams the request body to a temp file, then extracts the file part (multipart/form-data,
     * field "file") or treats the whole body as the file (raw upload with X-File-Name).
     */
    private void upload(Req r, OutputStream out) throws Exception {
        File dir = new File(context.getCacheDir(), "uploads");
        dir.mkdirs();
        File raw = File.createTempFile("body", ".part", dir);
        try {
            try (OutputStream fo = new FileOutputStream(raw)) {
                copy(r.body, fo, r.contentLength);
            }
            String type = r.headers.getOrDefault("content-type", "");
            String name = header(r, "x-file-name");
            String mime = header(r, "x-file-type");
            File payload;
            int b = type.indexOf("boundary=");
            if (type.startsWith("multipart/form-data") && b > 0) {
                String boundary = type.substring(b + 9).replace("\"", "").trim();
                Multipart mp = Multipart.extract(raw, boundary, dir);
                if (mp.file == null) {
                    json(out, 400, new JSONObject().put("error", "Upload form field named file is required"));
                    return;
                }
                payload = mp.file;
                if (mp.fileName != null && !mp.fileName.isEmpty()) name = mp.fileName;
                if (mp.fields.containsKey("originalName")) name = mp.fields.get("originalName");
                if (mp.fields.containsKey("mimeType")) mime = mp.fields.get("mimeType");
            } else {
                payload = raw;
            }
            Model.Media m = repo.saveUpload(payload, name == null ? "upload" : name, mime);
            json(out, 201, new JSONObject().put("media", m.toJson()));
        } finally {
            raw.delete();
        }
    }

    private static String header(Req r, String key) {
        String v = r.headers.get(key);
        return v == null || v.isEmpty() ? null : decode(v);
    }

    /** Minimal multipart parser over a file on disk (memory stays flat for large videos). */
    static final class Multipart {
        File file;
        String fileName;
        final Map<String, String> fields = new HashMap<>();

        static Multipart extract(File body, String boundary, File dir) throws IOException {
            Multipart m = new Multipart();
            byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            try (RandomAccessFile f = new RandomAccessFile(body, "r")) {
                long pos = indexOf(f, delim, 0);
                while (pos >= 0) {
                    long headStart = pos + delim.length;
                    f.seek(headStart);
                    int a = f.read(), b2 = f.read();
                    if (a == '-' && b2 == '-') break;          // closing delimiter
                    long hEnd = indexOf(f, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), headStart);
                    if (hEnd < 0) break;
                    byte[] hb = new byte[(int) (hEnd - headStart)];
                    f.seek(headStart);
                    f.readFully(hb);
                    String headers = new String(hb, StandardCharsets.UTF_8);
                    long dataStart = hEnd + 4;
                    long next = indexOf(f, ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1), dataStart);
                    if (next < 0) break;
                    String disp = "";
                    for (String hl : headers.split("\r\n")) {
                        if (hl.toLowerCase(Locale.US).startsWith("content-disposition")) disp = hl;
                    }
                    String fieldName = param(disp, "name");
                    String fn = param(disp, "filename");
                    if (fn != null && m.file == null) {  // first file part (the UI sends it as "file")
                        File out = File.createTempFile("file", ".bin", dir);
                        try (OutputStream o = new FileOutputStream(out)) {
                            f.seek(dataStart);
                            long left = next - dataStart;
                            byte[] buf = new byte[64 * 1024];
                            while (left > 0) {
                                int n = f.read(buf, 0, (int) Math.min(buf.length, left));
                                if (n <= 0) break;
                                o.write(buf, 0, n);
                                left -= n;
                            }
                        }
                        m.file = out;
                        m.fileName = fn;
                    } else if (fieldName != null && next - dataStart < 64 * 1024) {
                        byte[] vb = new byte[(int) (next - dataStart)];
                        f.seek(dataStart);
                        f.readFully(vb);
                        m.fields.put(fieldName, new String(vb, StandardCharsets.UTF_8));
                    }
                    pos = next + 2;
                }
            }
            return m;
        }

        static String param(String disp, String key) {
            String k = key + "=\"";
            int i = disp.indexOf("; " + k);
            if (i < 0) i = disp.indexOf(";" + k);
            if (i < 0) return null;
            int s = disp.indexOf('"', i) + 1;
            int e = disp.indexOf('"', s);
            return e > s ? disp.substring(s, e) : "";
        }

        /** Byte-sequence search in a file from `from` (buffered, overlapping windows). */
        static long indexOf(RandomAccessFile f, byte[] needle, long from) throws IOException {
            int bufSize = 256 * 1024;
            byte[] buf = new byte[bufSize];
            long base = from;
            long len = f.length();
            while (base < len) {
                f.seek(base);
                int n = f.read(buf, 0, (int) Math.min(bufSize, len - base));
                if (n <= 0) return -1;
                outer:
                for (int i = 0; i + needle.length <= n; i++) {
                    for (int j = 0; j < needle.length; j++) if (buf[i + j] != needle[j]) continue outer;
                    return base + i;
                }
                if (base + n >= len) return -1;
                base += n - needle.length + 1;
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------ media files

    private void mediaFile(Req r, OutputStream out, String id) throws IOException {
        Model.Media m = repo.getMedia(id);
        File f = m == null ? null : repo.file(m);
        if (f == null || !f.exists()) {
            send(out, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        long len = f.length();
        String head = "HTTP/1.1 200 OK\r\nContent-Type: " + m.mimeType + "\r\nContent-Length: " + len
                + "\r\nCache-Control: private, max-age=3600\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        try (InputStream in = new FileInputStream(f)) {
            copy(in, out, len);
        }
        out.flush();
    }

    // ------------------------------------------------------------------ io helpers

    private void asset(OutputStream out, String name, String type) throws IOException {
        try (InputStream in = context.getAssets().open("present/" + name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            copy(in, b, Long.MAX_VALUE);
            send(out, 200, type, b.toByteArray());
        }
    }

    private static JSONObject body(Req r) throws Exception {
        if (r.contentLength <= 0) return new JSONObject();
        ByteArrayOutputStream b = new ByteArrayOutputStream((int) Math.min(r.contentLength, 1 << 20));
        copy(r.body, b, r.contentLength);
        String s = b.toString("UTF-8").trim();
        return s.isEmpty() ? new JSONObject() : new JSONObject(s);
    }

    private static void drain(Req r) throws IOException {
        if (r.contentLength > 0) copy(r.body, new OutputStream() {
            @Override public void write(int b) { }
            @Override public void write(byte[] b, int o, int l) { }
        }, r.contentLength);
    }

    private static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long left = limit;
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) break;
            out.write(buf, 0, n);
            left -= n;
        }
    }

    private static void json(OutputStream out, int status, JSONObject j) throws IOException {
        send(out, status, "application/json; charset=utf-8", j.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, int status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Authorization, Content-Type, X-File-Name, X-File-Type\r\n"
                + "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String reason(int s) {
        switch (s) {
            case 200: return "OK";
            case 201: return "Created";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            default: return "Internal Server Error";
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
