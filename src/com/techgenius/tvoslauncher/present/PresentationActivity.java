package com.techgenius.tvoslauncher.present;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.techgenius.tvoslauncher.Ui;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen presentation player (port of PlaybackEngine.kt + MainActivity.kt from pb-marketer-tv).
 * Plays the Default playlist in a loop: images for their duration, videos to the end, webpages with
 * the original auto-scroll behaviour. Remote: \u25c0 \u25b6 previous/next, OK pause/play, \u25b2 \u25bc Info connection
 * card, Back twice to exit.
 */
public class PresentationActivity extends Activity implements AdminServer.Gateway {

    static final long CONNECTION_PANEL_HIDE_MS = 6_000, ERROR_ADVANCE_MS = 5_000;
    static final int CSS_FETCH_TIMEOUT_MS = 8_000, CSS_LAYERS_CHROME = 99;
    static final long WEBPAGE_LOAD_FALLBACK_DELAY_MS = 10_000, WEBPAGE_LAYOUT_SETTLE_DELAY_MS = 750, SCROLL_TICK_MS = 80;
    static final double PAUSE_EVERY_VIEWPORTS = 0.85;
    static final int STALL_TICK_LIMIT = 12, MIN_SCALE_PERCENT = 35, VIEWPORT_WIDTH = 1920;
    static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    final Handler handler = new Handler(Looper.getMainLooper());
    FrameLayout root;
    TextureView videoView;
    MediaPlayer mediaPlayer;
    Surface videoSurface;
    ImageView imageView;
    WebView webView;
    LinearLayout messageCard;
    View emptyView;
    TextView messageTitle, messageBody;
    View connectionCard;
    TextView stateBadge;
    boolean legacyCss;

    final Object statusLock = new Object();
    Model.Status status = new Model.Status();
    Model.Playlist playlist;
    List<Model.Slide> slides = new ArrayList<>();
    int index = 0;
    boolean playing = true;
    String activeWebpageSlideId, scrollStartedFor;
    double nextPauseY, lastScrollY;
    int stalledTicks;
    long backPressedAt;
    int videoW, videoH;
    String videoFit = Model.CONTAIN;

    final Runnable nextRunnable = this::next;
    final Runnable hidePanel = () -> fade(connectionCard, false);
    final Runnable scrollStart = () -> startAutoScroll(true);
    final Runnable scrollStep = this::stepAutoScroll;
    final Runnable hideBadge = () -> fade(stateBadge, false);

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Presenter.ensureRunning(this);
        if (Presenter.keepAwake(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        legacyCss = webViewMajor() < CSS_LAYERS_CHROME;
        buildViews();
        setContentView(root);
        Presenter.attach(this);
        reload();
        if (Presenter.showInfoOnStart(this) && !slides.isEmpty()) showConnection(CONNECTION_PANEL_HIDE_MS);
    }

    @Override
    protected void onDestroy() {
        Presenter.detach(this);
        handler.removeCallbacksAndMessages(null);
        releaseVideo();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ views

    @SuppressLint("SetJavaScriptEnabled")
    void buildViews() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(Presenter.keepAwake(this));

        videoView = new TextureView(this);
        videoView.setVisibility(View.GONE);
        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                videoSurface = new Surface(st);
                if (mediaPlayer != null) mediaPlayer.setSurface(videoSurface);
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                applyVideoFit();
            }

            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                if (videoSurface != null) videoSurface.release();
                videoSurface = null;
                return true;
            }

            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setVisibility(View.GONE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (legacyCss && isCss(r)) return rewrittenCss(r);
                return null;
            }

            @Override public void onPageFinished(WebView v, String url) {
                applyViewport(v, current());
            }

            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) showError("Webpage failed to load: " + e.getDescription(), current());
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setUserAgentString(DESKTOP_UA);
        webView.setInitialScale(100);
        webView.setVisibility(View.GONE);

        root.addView(videoView, match());
        root.addView(imageView, match());
        root.addView(webView, match());

        // Message card (empty playlist / errors): centred glass panel.
        messageCard = new LinearLayout(this);
        messageCard.setOrientation(LinearLayout.VERTICAL);
        messageCard.setGravity(Gravity.CENTER_HORIZONTAL);
        messageCard.setPadding(u(28), u(22), u(28), u(24));
        messageCard.setBackground(glass(u(22), 0xCC1C1C22));
        messageTitle = label(20, "semibold", 1f);
        messageTitle.setGravity(Gravity.CENTER);
        messageBody = label(13, "regular", 0.7f);
        messageBody.setGravity(Gravity.CENTER);
        messageBody.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams mb = new LinearLayout.LayoutParams(u(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        mb.topMargin = u(8);
        messageCard.addView(messageTitle);
        messageCard.addView(messageBody, mb);
        messageCard.setVisibility(View.GONE);
        root.addView(messageCard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        connectionCard = buildConnectionCard();
        connectionCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.BOTTOM);
        cp.leftMargin = u(32);
        cp.bottomMargin = u(32);
        root.addView(connectionCard, cp);

        emptyView = buildEmptyView();
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, match());

        stateBadge = label(14, "semibold", 1f);
        stateBadge.setPadding(u(18), u(9), u(18), u(9));
        stateBadge.setBackground(glass(u(20), 0xB31C1C22));
        stateBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        sp.topMargin = u(28);
        sp.rightMargin = u(32);
        root.addView(stateBadge, sp);
    }

    /**
     * Empty playlist: one centred screen instead of two overlapping cards \u2014 the Presentation
     * icon, a title and a line of help, then the QR / address / pairing code card.
     */
    View buildEmptyView() {
        FrameLayout v = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A1A2E, 0xFF0B0B12, 0xFF000000});
        v.setBackground(bg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(this);
        icon.setImageBitmap(PresentIcon.banner(u(150) * 2, u(90) * 2));
        icon.setScaleType(ImageView.ScaleType.FIT_XY);
        icon.setClipToOutline(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(u(12));
        icon.setBackground(shape);
        col.addView(icon, new LinearLayout.LayoutParams(u(150), u(90)));
        TextView t = label(26, "semibold", 1f);
        t.setText("Nothing to present yet");
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = u(20);
        col.addView(t, tl);
        TextView b = label(14, "regular", 0.7f);
        b.setText("Add images, videos or webpages from a phone or computer on the same Wi-Fi.");
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(u(460), ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = u(6);
        col.addView(b, bl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = u(26);
        col.addView(buildConnectionCard(), cl);
        v.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return v;
    }

    /** tvOS-style card: QR, "Send to this TV", address and PIN. */
    View buildConnectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(u(16), u(16), u(22), u(16));
        card.setBackground(glass(u(22), 0xD91C1C22));
        String url = Presenter.address();

        ImageView qr = new ImageView(this);
        qr.setImageBitmap(Qr.bitmap(url, u(112)));
        qr.setPadding(u(8), u(8), u(8), u(8));
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(u(12));
        qr.setBackground(white);
        card.addView(qr, new LinearLayout.LayoutParams(u(128), u(128)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(u(18), 0, 0, 0);
        TextView t = label(17, "semibold", 1f);
        t.setText("Send to this TV");
        TextView sub = label(11.5f, "regular", 0.65f);
        sub.setText("Scan the code or open this address on a phone or computer on the same Wi-Fi.");
        sub.setMaxWidth(u(300));
        TextView addr = label(15, "medium", 1f);
        addr.setText(url.replace("http://", ""));
        TextView pinL = label(11, "medium", 0.55f);
        pinL.setText("PAIRING CODE");
        pinL.setLetterSpacing(0.08f);
        TextView pin = label(24, "semibold", 1f);
        String p = Presenter.pin(this);
        pin.setText(p.substring(0, 3) + " " + p.substring(3));
        pin.setLetterSpacing(0.12f);
        text.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = u(3);
        text.addView(sub, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = u(10);
        text.addView(addr, lp2);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp3.topMargin = u(10);
        text.addView(pinL, lp3);
        text.addView(pin);
        card.addView(text);
        return card;
    }

    // ------------------------------------------------------------------ remote

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previous();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                next();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) control(playing ? "pause" : "play");
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_MENU:
                showConnection(CONNECTION_PANEL_HIDE_MS);
                return true;
            case KeyEvent.KEYCODE_BACK:
                long now = System.currentTimeMillis();
                if (now - backPressedAt < 2500) {
                    finish();
                } else {
                    backPressedAt = now;
                    badge("Press Back again to exit");
                }
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    // ------------------------------------------------------------------ Gateway (web \u2192 TV)

    @Override public void reload() {
        handler.post(this::loadPlaylist);
    }

    @Override public boolean control(String command) {
        handler.post(() -> doControl(command));
        return true;
    }

    @Override public Model.Status status() {
        synchronized (statusLock) {
            return status.copy();
        }
    }

    // ------------------------------------------------------------------ playback (PlaybackEngine)

    void loadPlaylist() {
        handler.removeCallbacks(nextRunnable);
        stopAutoScroll();
        stopVideo();
        playlist = Presenter.repo(this).playlist();
        slides = new ArrayList<>();
        for (Model.Slide s : Model.sortedByOrder(playlist.slides)) if (s.enabled) slides.add(s);
        index = 0;
        playing = true;
        if (slides.isEmpty()) {
            showNoSlides();
        } else {
            showSlide(0);
            hideConnection();
        }
    }

    void doControl(String command) {
        switch (command.toLowerCase(Locale.US)) {
            case "play":
                if (!playing) {
                    playing = true;
                    if (mediaPlayer != null) mediaPlayer.start();
                    if (isAutoScrollSlide()) startAutoScroll(false);
                    else if (current() != null && !Model.VIDEO.equals(current().type)) schedule();
                    setState("playing");
                    badge("\u25b6  Playing");
                }
                break;
            case "pause":
                playing = false;
                handler.removeCallbacks(nextRunnable);
                handler.removeCallbacks(scrollStart);
                handler.removeCallbacks(scrollStep);
                if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
                setState("paused");
                badge("\u275a\u275a  Paused");
                break;
            case "next":
                next();
                break;
            case "previous":
                previous();
                break;
            case "restart":
                index = 0;
                if (slides.isEmpty()) showNoSlides();
                else showSlide(0);
                break;
            case "showconnectioninfo":
            case "connectinfo":
            case "connection":
                showConnection(CONNECTION_PANEL_HIDE_MS);
                break;
            default:
                break;
        }
    }

    void showSlide(int i) {
        if (slides.isEmpty()) {
            showNoSlides();
            return;
        }
        index = Math.floorMod(i, slides.size());
        Model.Slide s = slides.get(index);
        handler.removeCallbacks(nextRunnable);
        stopAutoScroll();
        hideSurfaces();
        messageCard.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        switch (s.type) {
            case Model.IMAGE: showImage(s); break;
            case Model.VIDEO: showVideo(s); break;
            default: showWebpage(s); break;
        }
    }

    void showImage(Model.Slide s) {
        activeWebpageSlideId = null;
        Model.Media m = resolveMedia(s);
        if (m == null) return;
        imageView.setScaleType(Model.COVER.equals(s.fitMode) ? ImageView.ScaleType.CENTER_CROP
                : Model.FILL.equals(s.fitMode) ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        Bitmap bm = decodeForScreen(Presenter.repo(this).file(m).getAbsolutePath());
        if (bm != null) imageView.setImageBitmap(bm);
        else imageView.setImageURI(Uri.fromFile(Presenter.repo(this).file(m)));
        reveal(imageView);
        statusFor(s, m, playing ? "playing" : "paused");
        schedule();
    }

    void showVideo(Model.Slide s) {
        activeWebpageSlideId = null;
        Model.Media m = resolveMedia(s);
        if (m == null) return;
        stopVideo();
        videoFit = s.fitMode;
        videoW = videoH = 0;
        MediaPlayer mp = new MediaPlayer();
        mediaPlayer = mp;
        String slideId = s.id;
        try {
            mp.setDataSource(Presenter.repo(this).file(m).getAbsolutePath());
            if (videoSurface != null) mp.setSurface(videoSurface);
            mp.setLooping(false);
            float vol = s.muted ? 0f : 1f;
            mp.setVolume(vol, vol);
            mp.setOnVideoSizeChangedListener((p, w, h) -> {
                videoW = w;
                videoH = h;
                applyVideoFit();
            });
            mp.setOnPreparedListener(p -> {
                if (mediaPlayer == p && playing) p.start();
            });
            mp.setOnCompletionListener(p -> {
                Model.Slide cur = current();
                if (playing && cur != null && slideId.equals(cur.id) && Model.VIDEO.equals(cur.type)) next();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Model.Slide cur = current();
                if (cur != null && Model.VIDEO.equals(cur.type) && mediaPlayer == p) {
                    showError("Video failed: " + m.displayName + "\nMedia error " + what + " (" + extra + ")", cur);
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            showError("Video failed: " + m.displayName + "\n" + e.getMessage(), s);
            return;
        }
        reveal(videoView);
        statusFor(s, m, playing ? "playing" : "paused");
    }

    void applyVideoFit() {
        int vw = videoView.getWidth(), vh = videoView.getHeight();
        if (vw == 0 || vh == 0 || videoW == 0 || videoH == 0) return;
        float sx = vw / (float) videoW, sy = vh / (float) videoH;
        float scale = Model.COVER.equals(videoFit) ? Math.max(sx, sy) : Math.min(sx, sy);
        Matrix m = new Matrix();
        if (Model.FILL.equals(videoFit)) {
            m.reset();  // TextureView stretches to fill by default
        } else {
            // TextureView stretches the frame to its bounds; undo that, then apply the uniform scale.
            m.setScale(videoW * scale / vw, videoH * scale / vh, vw / 2f, vh / 2f);
        }
        videoView.setTransform(m);
    }

    void showWebpage(Model.Slide s) {
        String url = s.url == null ? "" : s.url;
        if (url.trim().isEmpty()) {
            showError("Webpage slide is missing a URL", s);
            return;
        }
        activeWebpageSlideId = s.id;
        resetScroll(s.id);
        reveal(webView);
        webView.stopLoading();
        webView.scrollTo(0, 0);
        webView.setInitialScale(scalePercent(s.fitMode));
        webView.loadUrl(url);
        statusFor(s, null, playing ? "playing" : "paused");
        if (s.webpageAutoScroll) handler.postDelayed(scrollStart, WEBPAGE_LOAD_FALLBACK_DELAY_MS);
        else schedule();
    }

    Model.Media resolveMedia(Model.Slide s) {
        if (s.mediaId == null || s.mediaId.trim().isEmpty()) {
            showError(s.type + " slide is missing media", s);
            return null;
        }
        Model.Media m = Presenter.repo(this).getMedia(s.mediaId);
        if (m == null || !Presenter.repo(this).file(m).exists()) {
            showError("Media file is missing for slide", s);
            return null;
        }
        return m;
    }

    void schedule() {
        if (!playing || slides.isEmpty()) return;
        if (Model.VIDEO.equals(slides.get(index).type)) return;
        if (isAutoScrollSlide()) {
            startAutoScroll(false);
            return;
        }
        int d = Math.max(1, slides.get(index).durationSec);
        handler.removeCallbacks(nextRunnable);
        handler.postDelayed(nextRunnable, d * 1000L);
    }

    void next() {
        if (slides.isEmpty()) {
            showNoSlides();
            return;
        }
        playing = true;
        showSlide(index + 1);
    }

    void previous() {
        if (slides.isEmpty()) {
            showNoSlides();
            return;
        }
        playing = true;
        showSlide(index - 1);
    }

    void showNoSlides() {
        hideSurfaces();
        handler.removeCallbacks(hidePanel);
        connectionCard.setVisibility(View.GONE);
        messageCard.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        synchronized (statusLock) {
            status = new Model.Status();
            status.state = "empty";
            status.playlistId = playlist == null ? null : playlist.id;
            status.playlistName = playlist == null ? null : playlist.name;
            status.slideCount = 0;
            status.error = "No enabled slides in the active playlist";
        }
    }

    void showError(String message, Model.Slide s) {
        hideSurfaces();
        messageTitle.setText("Skipping this slide");
        messageBody.setText(message);
        messageCard.setVisibility(View.VISIBLE);
        synchronized (statusLock) {
            status = new Model.Status();
            status.state = "error";
            status.playlistId = playlist == null ? null : playlist.id;
            status.playlistName = playlist == null ? null : playlist.name;
            status.slideId = s == null ? null : s.id;
            status.slideIndex = index;
            status.slideCount = slides.size();
            status.slideType = s == null ? null : s.type;
            status.url = s == null ? null : s.url;
            status.error = message;
        }
        if (playing && !slides.isEmpty()) {
            handler.removeCallbacks(nextRunnable);
            handler.postDelayed(nextRunnable, ERROR_ADVANCE_MS);
        }
    }

    void hideSurfaces() {
        stopVideo();
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        imageView.setImageDrawable(null);
        webView.setVisibility(View.GONE);
    }

    void stopVideo() {
        if (mediaPlayer != null) {
            MediaPlayer mp = mediaPlayer;
            mediaPlayer = null;
            try {
                mp.reset();
                mp.release();
            } catch (Exception ignored) {
            }
        }
    }

    void releaseVideo() {
        stopVideo();
        if (videoSurface != null) videoSurface.release();
    }

    // ------------------------------------------------------------------ status

    void statusFor(Model.Slide s, Model.Media m, String state) {
        synchronized (statusLock) {
            status = new Model.Status();
            status.state = state;
            status.playlistId = playlist == null ? null : playlist.id;
            status.playlistName = playlist == null ? null : playlist.name;
            status.slideId = s.id;
            status.slideIndex = index;
            status.slideCount = slides.size();
            status.slideType = s.type;
            status.mediaName = m == null ? null : m.displayName;
            status.url = s.url;
        }
    }

    void setState(String state) {
        synchronized (statusLock) {
            status = status.copy();
            status.state = state;
        }
    }

    // ------------------------------------------------------------------ webpages (viewport + auto-scroll)

    void applyViewport(WebView v, Model.Slide s) {
        String fit = s == null ? Model.CONTAIN : s.fitMode;
        double scale = viewportScale(fit);
        String sc = String.format(Locale.US, "%.4f", scale);
        String js = "(function(){var v=document.querySelector('meta[name=\"viewport\"]');"
                + "if(!v){v=document.createElement('meta');v.name='viewport';document.head.appendChild(v);}"
                + "v.content='width=" + VIEWPORT_WIDTH + ", initial-scale=" + sc + ", minimum-scale=" + sc
                + ", maximum-scale=" + sc + ", user-scalable=no';"
                + "document.documentElement.style.overflowX='hidden';document.body.style.overflowX='hidden';})();";
        v.setInitialScale(scalePercent(fit));
        v.evaluateJavascript(js, null);
        if (isAutoScrollSlide()) {
            handler.removeCallbacks(scrollStart);
            handler.postDelayed(scrollStart, WEBPAGE_LAYOUT_SETTLE_DELAY_MS);
        }
    }

    int scalePercent(String fit) {
        return (int) Math.max(MIN_SCALE_PERCENT, Math.min(100, Math.round(viewportScale(fit) * 100.0)));
    }

    double viewportScale(String fit) {
        if (Model.COVER.equals(fit)) return 1.0;
        int w = webView.getWidth() > 0 ? webView.getWidth() : root.getWidth() > 0 ? root.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        if (w <= 0) w = VIEWPORT_WIDTH;
        return Math.max(MIN_SCALE_PERCENT / 100.0, Math.min(1.0, w / (double) VIEWPORT_WIDTH));
    }

    boolean isAutoScrollSlide() {
        Model.Slide s = current();
        return s != null && Model.WEBPAGE.equals(s.type) && s.webpageAutoScroll && s.id.equals(activeWebpageSlideId);
    }

    void startAutoScroll(boolean resetToTop) {
        Model.Slide s = current();
        if (s == null || !playing || !Model.WEBPAGE.equals(s.type) || !s.webpageAutoScroll || !s.id.equals(activeWebpageSlideId)) return;
        handler.removeCallbacks(scrollStart);
        handler.removeCallbacks(scrollStep);
        if (resetToTop || !s.id.equals(scrollStartedFor)) {
            resetScroll(s.id);
            webView.evaluateJavascript("window.scrollTo(0,0);", null);
        }
        scrollStartedFor = s.id;
        handler.postDelayed(scrollStep, pauseMs(s));
    }

    void stepAutoScroll() {
        Model.Slide s = current();
        if (s == null || !playing || !Model.WEBPAGE.equals(s.type) || !s.webpageAutoScroll || !s.id.equals(activeWebpageSlideId)) return;
        double px = Math.max(1.0, Math.max(5, Math.min(1000, s.webpageScrollSpeedPxPerSec)) * SCROLL_TICK_MS / 1000.0);
        String js = "(function(){var root=document.scrollingElement||document.documentElement||document.body;"
                + "var body=document.body||root;"
                + "var height=Math.max(root.scrollHeight||0,body.scrollHeight||0,root.offsetHeight||0,body.offsetHeight||0,root.clientHeight||0);"
                + "var viewport=window.innerHeight||root.clientHeight||0;var max=Math.max(0,height-viewport);"
                + "var before=window.pageYOffset||root.scrollTop||document.documentElement.scrollTop||0;"
                + "var next=Math.min(max,before+" + px + ");window.scrollTo(0,next);root.scrollTop=next;"
                + "document.documentElement.scrollTop=next;"
                + "var after=window.pageYOffset||root.scrollTop||document.documentElement.scrollTop||0;"
                + "return {y:after,max:max,viewport:viewport,moved:after>before+0.5,done:max<=1||after>=max-1};})();";
        String id = s.id;
        webView.evaluateJavascript(js, result -> onScrollResult(id, result));
    }

    void onScrollResult(String slideId, String result) {
        Model.Slide s = current();
        if (s == null || !playing || !s.id.equals(slideId) || !s.id.equals(activeWebpageSlideId)) return;
        JSONObject m = null;
        try {
            m = new JSONObject(result == null ? "" : result);
        } catch (Exception ignored) {
        }
        double y = m == null ? 0 : m.optDouble("y", 0);
        double max = m == null ? 0 : m.optDouble("max", 0);
        double viewport = Math.max(1, m == null ? webView.getHeight() : m.optDouble("viewport", webView.getHeight()));
        boolean moved = m == null ? y > lastScrollY + 0.5 : m.optBoolean("moved", y > lastScrollY + 0.5);
        boolean done = m == null ? max <= 1 : m.optBoolean("done", max <= 1);
        stalledTicks = moved || y > lastScrollY + 0.5 ? 0 : stalledTicks + 1;
        lastScrollY = y;
        if (max <= 1.0) handler.postDelayed(nextRunnable, Math.max(1, s.durationSec) * 1000L);
        else if (done || stalledTicks >= STALL_TICK_LIMIT) handler.postDelayed(nextRunnable, pauseMs(s));
        else if (y >= nextPauseY) {
            nextPauseY = y + viewport * PAUSE_EVERY_VIEWPORTS;
            handler.postDelayed(scrollStep, pauseMs(s));
        } else handler.postDelayed(scrollStep, SCROLL_TICK_MS);
    }

    void resetScroll(String slideId) {
        scrollStartedFor = null;
        nextPauseY = Math.max(1, webView.getHeight()) * PAUSE_EVERY_VIEWPORTS;
        lastScrollY = 0;
        stalledTicks = 0;
        activeWebpageSlideId = slideId;
    }

    void stopAutoScroll() {
        handler.removeCallbacks(scrollStart);
        handler.removeCallbacks(scrollStep);
        activeWebpageSlideId = null;
        scrollStartedFor = null;
        nextPauseY = 0;
        lastScrollY = 0;
        stalledTicks = 0;
    }

    static long pauseMs(Model.Slide s) {
        return Math.max(0, Math.min(300, s.webpageScrollPauseSec)) * 1000L;
    }

    // ------------------------------------------------------------------ legacy WebView CSS

    static boolean isCss(WebResourceRequest r) {
        if (!"GET".equalsIgnoreCase(r.getMethod())) return false;
        String scheme = r.getUrl().getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        String path = r.getUrl().getPath();
        return path != null && path.toLowerCase(Locale.US).endsWith(".css");
    }

    WebResourceResponse rewrittenCss(WebResourceRequest r) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(r.getUrl().toString()).openConnection();
            c.setConnectTimeout(CSS_FETCH_TIMEOUT_MS);
            c.setReadTimeout(CSS_FETCH_TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> h : r.getRequestHeaders().entrySet()) {
                String n = h.getKey();
                if (!n.equalsIgnoreCase("Accept-Encoding") && !n.equalsIgnoreCase("Connection") && !n.equalsIgnoreCase("Host")) {
                    c.setRequestProperty(n, h.getValue());
                }
            }
            c.setRequestProperty("Accept", "text/css,*/*;q=0.1");
            c.setRequestProperty("Accept-Encoding", "identity");
            c.setRequestProperty("User-Agent", DESKTOP_UA);
            int code = c.getResponseCode();
            if (code < 200 || code > 299) {
                c.disconnect();
                return null;
            }
            String msg = c.getResponseMessage() == null ? "OK" : c.getResponseMessage();
            byte[] raw;
            try (InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                raw = b.toByteArray();
            }
            String css = LegacyCss.unwrapCascadeLayers(new String(raw, StandardCharsets.UTF_8));
            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> h : c.getHeaderFields().entrySet()) {
                if (h.getKey() != null && !h.getValue().isEmpty() && !h.getKey().equalsIgnoreCase("Content-Encoding")) {
                    headers.put(h.getKey(), h.getValue().get(0));
                }
            }
            headers.put("Content-Type", "text/css; charset=utf-8");
            c.disconnect();
            return new WebResourceResponse("text/css", "UTF-8", code, msg, headers,
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    static int webViewMajor() {
        try {
            android.content.pm.PackageInfo p = WebView.getCurrentWebViewPackage();
            if (p == null || p.versionName == null) return 0;
            return Integer.parseInt(p.versionName.split("\\.")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ overlays & helpers

    void showConnection(long ms) {
        if (emptyView.getVisibility() == View.VISIBLE) return;  // the empty screen already shows it
        handler.removeCallbacks(hidePanel);
        fade(connectionCard, true);
        if (!slides.isEmpty()) handler.postDelayed(hidePanel, ms);
    }

    void hideConnection() {
        handler.removeCallbacks(hidePanel);
        connectionCard.setVisibility(View.GONE);
    }

    void badge(String text) {
        stateBadge.setText(text);
        handler.removeCallbacks(hideBadge);
        fade(stateBadge, true);
        handler.postDelayed(hideBadge, 1800);
    }

    void reveal(View v) {
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(350).start();
    }

    void fade(View v, boolean show) {
        if (show) {
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(0f);
                v.setVisibility(View.VISIBLE);
            }
            v.animate().alpha(1f).setDuration(220).start();
        } else {
            v.animate().alpha(0f).setDuration(220).withEndAction(() -> v.setVisibility(View.GONE)).start();
        }
    }

    /** Decodes an image no larger than the screen (keeps memory low on the TV box). */
    Bitmap decodeForScreen(String path) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sw = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= sw) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, o);
    }

    Model.Slide current() {
        return index >= 0 && index < slides.size() ? slides.get(index) : null;
    }

    GradientDrawable glass(int radius, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(color);
        g.setStroke(Math.max(1, u(0.6f)), 0x33FFFFFF);
        return g;
    }

    TextView label(float size, String weight, float alpha) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setAlpha(alpha);
        t.setTypeface(Ui.font(this, weight));
        Ui.textSize(t, size);
        return t;
    }

    int u(float v) {
        return Ui.px(this, v);
    }

    static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
