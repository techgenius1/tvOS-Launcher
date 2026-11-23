package com.techgenius.tvoslauncher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search, as on tvOS: the Home Screen blurred behind, what you typed at the top, a one-row
 * keyboard, and rows of results under it. Apps, settings and inputs match on every key press with
 * no network; movies and series (JustWatch, any service in India, and Stremio) are fetched once
 * typing pauses. OK on a title opens its "How to Watch" sheet with an Open / Get button per app.
 *
 * Geometry in design units (960 x 540, half of Apple's 1920 x 1080 points): 48 side margin as the
 * Home Screen, query 26 high at the top, keys on a 30 pitch, poster lockups 96 x 144 (2:3).
 */
final class SearchPanel {

    static final int REQ_VOICE = 7301, REQ_MIC = 7302;
    static final long DEBOUNCE_MS = 350;
    static final String LATIN = "abcdefghijklmnopqrstuvwxyz";
    static final String TURKISH = "abc\u00e7defg\u011fh\u0131ijklmno\u00f6prs\u015ftu\u00fcvyzqwx";
    static final String HEBREW = "\u05d0\u05d1\u05d2\u05d3\u05d4\u05d5\u05d6\u05d7\u05d8\u05d9\u05db\u05da\u05dc\u05de\u05dd\u05e0\u05df\u05e1\u05e2\u05e4\u05e3\u05e6\u05e5\u05e7\u05e8\u05e9\u05ea";
    static final String DIGITS = "1234567890.-'&:!?";
    static final String HINDI = "\u0905\u0906\u0907\u0908\u0909\u090a\u090f\u0910\u0913\u0914"
            + "\u0915\u0916\u0917\u0918\u091a\u091b\u091c\u091d\u091f\u0920\u0921\u0922\u0923"
            + "\u0924\u0925\u0926\u0927\u0928\u092a\u092b\u092c\u092d\u092e\u092f\u0930\u0932\u0935"
            + "\u0936\u0937\u0938\u0939\u093e\u093f\u0940\u0941\u0942\u0947\u0948\u094b\u094c\u0902\u094d";

    final MainActivity a;
    final FrameLayout layer;
    final TextView queryView;
    final LinearLayout keys, list;
    final HorizontalScrollView keyScroll;
    final ScrollView results;
    final StringBuilder query = new StringBuilder();
    final Search.Apps apps;
    final ExecutorService net = Executors.newFixedThreadPool(2), img = Executors.newSingleThreadExecutor();
    /** Recent searches' results, so deleting a letter shows the earlier results at once. */
    final Map<String, List<Search.Title>> cache = new LinkedHashMap<String, List<Search.Title>>(24, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Search.Title>> e) {
            return size() > 20;
        }
    };
    final Map<String, List<Search.Title>> stremioCache = new LinkedHashMap<String, List<Search.Title>>(24, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Search.Title>> e) {
            return size() > 20;
        }
    };
    final Map<String, List<Search.InAppResult>> inAppCache = new LinkedHashMap<String, List<Search.InAppResult>>(24, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Search.InAppResult>> e) {
            return size() > 20;
        }
    };
    /** Decoded posters and logos, capped at 1/12 of the heap (the 1 GB TV gets ~16 MB). */
    final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 12)) {
        @Override protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    final Runnable fetch = this::fetchTitles;
    /** The TV has voice input (a prompt app); live dictates in place when it also has a speech service. */
    final boolean voice, live;
    final List<Search.AppSearchEntry> searchableApps;
    ImageView mic;
    GradientDrawable micBg;
    SpeechRecognizer recognizer;
    boolean listening, heard;
    int mode, gen;
    String failed;
    FrameLayout sheet;
    View sheetReturn;
    LinearLayout titleRow, stremioRow, inAppRow;
    TextView titleStatus;
    boolean closed;

    SearchPanel(MainActivity a) {
        this.a = a;
        apps = Search.apps(a, a.all);
        searchableApps = Search.discoverSearchableApps(a);
        voice = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(a.getPackageManager()) != null;
        live = SpeechRecognizer.isRecognitionAvailable(a);

        layer = new FrameLayout(a);
        layer.setClickable(true);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        a.addBlurredBackdrop(layer, 0x55000000);
        layer.setOnClickListener(v -> {
            if (sheet != null) closeSheet();
            else a.closeSearch();
        });

        int panelW = Math.min(a.dp(780), Math.max(a.dp(520), a.screenW - a.dp(72)));
        int panelH = Math.min(a.dp(448), Math.max(a.dp(340), a.screenH - a.dp(60)));
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        MainActivity.LiquidGlass cardGlass = new MainActivity.LiquidGlass(
                a.dp(24), 0xB8181A22, a.glassSource, a.screenW, a.screenH, a.unit);
        cardGlass.lens = a.dp(10);
        cardGlass.rimStrength = 0.45f;
        card.setBackground(cardGlass);
        card.setOutlineProvider(a.roundedOutline(new float[]{a.dp(24)}));
        card.setClipToOutline(true);
        card.setElevation(a.dp(28));

        // Top Header Bar (matches Other Apps modal): [Search Icon] Search   [Frosted Query Pill]   [Mic]
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        head.setClipToPadding(false);
        head.setPadding(a.dp(22), a.dp(14), a.dp(20), a.dp(13));

        ImageView headerGlyph = new ImageView(a);
        headerGlyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        headerGlyph.setImageBitmap(Glyphs.get("search", a.dp(17), 0xFFF4F4F6));
        LinearLayout.LayoutParams hglp = new LinearLayout.LayoutParams(a.dp(17), a.dp(17));
        hglp.setMarginEnd(a.dp(9));
        head.addView(headerGlyph, hglp);

        TextView headerTitle = a.text(15.5f, Ui.font(a, "semibold"), 1f);
        headerTitle.setTextColor(0xFFF4F4F6);
        headerTitle.setText(tr("Search", "Ara"));
        headerTitle.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        htlp.setMarginEnd(a.dp(16));
        head.addView(headerTitle, htlp);

        LinearLayout queryPill = new LinearLayout(a);
        queryPill.setOrientation(LinearLayout.HORIZONTAL);
        queryPill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(a.dp(13));
        pillBg.setColor(0x16FFFFFF);
        pillBg.setStroke(Math.max(1, a.dp(0.8f)), 0x26FFFFFF);
        queryPill.setBackground(pillBg);
        queryPill.setPadding(a.dp(14), a.dp(7), a.dp(14), a.dp(7));

        queryView = a.text(16f, Ui.font(a, "medium"), 1f);
        queryView.setSingleLine(true);
        queryView.setEllipsize(TextUtils.TruncateAt.START);
        queryView.setShadowLayer(0, 0, 0, 0);
        queryPill.addView(queryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(queryPill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (voice || live) {
            mic = new ImageView(a);
            mic.setId(View.generateViewId());
            mic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mic.setCropToPadding(false);
            int pad = a.dp(8);
            mic.setPadding(pad, pad, pad, pad);
            mic.setFocusable(true);
            mic.setClickable(true);
            micBg = new GradientDrawable();
            micBg.setShape(GradientDrawable.OVAL);
            mic.setBackground(micBg);
            mic.setOnFocusChangeListener((v, has) -> {
                paintMic();
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).setInterpolator(MainActivity.LIFT).start();
            });
            mic.setOnClickListener(v -> listen());
            paintMic();
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(a.dp(36), a.dp(36));
            mlp.gravity = Gravity.CENTER_VERTICAL;
            mlp.setMarginStart(a.dp(12));
            head.addView(mic, mlp);
        }
        card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Subtle 1dp horizontal divider (matches Other Apps)
        View div1 = new View(a);
        div1.setBackgroundColor(0x1FFFFFFF);
        card.addView(div1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, a.dp(0.8f))));

        keyScroll = new HorizontalScrollView(a);
        keyScroll.setHorizontalScrollBarEnabled(false);
        keyScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        keyScroll.setClipToPadding(false);
        keyScroll.setClipChildren(false);
        keyScroll.setBackgroundColor(0x0BFFFFFF);
        keyScroll.setPadding(a.dp(18), a.dp(6), a.dp(18), a.dp(6));
        keys = new LinearLayout(a);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.setClipChildren(false);
        keyScroll.addView(keys);
        card.addView(keyScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(46)));

        View div2 = new View(a);
        div2.setBackgroundColor(0x14FFFFFF);
        card.addView(div2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, a.dp(0.8f))));

        results = new ScrollView(a);
        results.setVerticalScrollBarEnabled(false);
        results.setOverScrollMode(View.OVER_SCROLL_NEVER);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        results.setPadding(0, a.dp(4), 0, a.dp(18));
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        results.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(panelW, panelH, Gravity.CENTER);
        layer.addView(card, clp);
        card.setScaleX(0.95f);
        card.setScaleY(0.95f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(210).setInterpolator(MainActivity.LIFT).start();

        buildKeys(null);
        showQuery();
        render();
    }

    // ---------------------------------------------------------------- keyboard

    String tr(String en, String trStr) {
        return I18n.s(a, en, trStr);
    }

    String tr(String en, String trStr, Object... args) {
        return I18n.s(a, en, trStr, args);
    }

    /** The one-row keyboard for the current mode, then space, delete, mode switches and the mic. */
    void buildKeys(String focusLabel) {
        keys.removeAllViews();
        boolean isTr = I18n.isTr(a);
        boolean isHe = I18n.isRtl(a);
        // Hebrew: Hebrew letters first, with a key that switches to Latin letters (mode 2) instead of Hindi.
        String chars = mode == 1 ? DIGITS : mode == 2 ? (isHe ? LATIN : HINDI) : (isTr ? TURKISH : isHe ? HEBREW : LATIN);
        List<View> made = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) {
            String ch = String.valueOf(chars.charAt(i));
            made.add(key(ch, a.dp(30), () -> type(ch)));
        }
        made.add(key(tr("space", "bo\u015fluk"), a.dp(64), () -> type(" ")));
        made.add(key("\u232b", a.dp(40), this::backspace));
        made.add(key(mode == 1 ? (isHe ? "\u05d0\u05d1\u05d2" : "abc") : "123", a.dp(48), () -> switchMode(mode == 1 ? 0 : 1)));
        if (isHe) made.add(key(mode == 2 ? "\u05d0\u05d1\u05d2" : "abc", a.dp(48), () -> switchMode(mode == 2 ? 0 : 2)));
        else if (!isTr) made.add(key(mode == 2 ? "abc" : "\u0905\u0906", a.dp(48), () -> switchMode(mode == 2 ? 0 : 2)));
        made.add(key(tr("clear", "temizle"), a.dp(56), this::clear));
        View focus = made.get(0);
        for (View k : made) {
            if (mic != null) k.setNextFocusUpId(mic.getId());
            keys.addView(k);
            if (focusLabel != null && focusLabel.equals(k.getTag())) focus = k;
        }
        if (mic != null) mic.setNextFocusDownId(made.get(0).getId());
        // The ends of the row stop focus instead of wrapping into the results.
        made.get(0).setNextFocusLeftId(made.get(0).getId());
        made.get(made.size() - 1).setNextFocusRightId(made.get(made.size() - 1).getId());
        if (focusLabel != null || keys.getTag() == null) focus.requestFocus();
        keys.setTag("built");
    }

    View key(String label, int w, Runnable click) {
        FrameLayout k = new FrameLayout(a);
        k.setId(View.generateViewId());
        k.setTag(label);
        k.setFocusable(true);
        k.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(8));
        bg.setColor(0x00FFFFFF);
        k.setBackground(bg);
        TextView face = a.text(label.length() > 2 ? 13 : 17, Ui.font(a, "medium"), 1f);
        face.setText(label);
        face.setGravity(Gravity.CENTER);
        face.setShadowLayer(0, 0, 0, 0);
        k.addView(face, a.match());
        k.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x00FFFFFF);
            face.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(150).start();
        });
        k.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, a.dp(34));
        lp.gravity = Gravity.CENTER_VERTICAL;
        k.setLayoutParams(lp);
        return k;
    }

    /** Focus stays on the switch just pressed (its label flips to "abc" and back). */
    void switchMode(int m) {
        int prev = mode;
        mode = m;
        buildKeys(m != 0 ? "abc" : prev == 1 ? "123" : "\u0905\u0906");
    }

    void type(String s) {
        if (s.equals(" ") && (query.length() == 0 || query.charAt(query.length() - 1) == ' ')) return;
        query.append(s);
        changed();
    }

    void backspace() {
        if (query.length() == 0) return;
        query.setLength(query.length() - 1);
        changed();
    }

    void clear() {
        query.setLength(0);
        changed();
    }

    void setQuery(String q) {
        query.setLength(0);
        query.append(q);
        changed();
    }

    // ---------------------------------------------------------------- dictation

    /** Focused white; red while listening; glass otherwise. */
    void paintMic() {
        if (mic == null) return;
        boolean f = mic.isFocused();
        micBg.setColor(listening ? 0xFFFF3B30 : f ? 0xF2FFFFFF : 0x1EFFFFFF);
        micBg.setStroke(Math.max(1, a.dp(0.8f)), listening ? 0xFFFF3B30 : f ? 0xFFFFFFFF : 0x2EFFFFFF);
        mic.setImageBitmap(Glyphs.get("mic", a.dp(22), listening || !f ? 0xFFF4F4F6 : 0xFF111114));
    }

    /**
     * Dictation. With the TV's speech service (Google's on Android TV) and the microphone allowed,
     * the words appear in the search field as you speak and results follow; otherwise the TV's
     * own voice prompt (Google's) listens and hands back what it heard. The microphone is asked
     * for on screen the first time. Pressing the mic again stops listening.
     */
    void listen() {
        if (listening) {
            if (recognizer != null) recognizer.stopListening();
            return;
        }
        if (!live) {
            prompt();
            return;
        }
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(a);
                recognizer.setRecognitionListener(new Dictation());
            }
            listening = true;
            heard = false;
            paintMic();
            showQuery();
            recognizer.startListening(speechIntent());
        } catch (Exception e) {
            stopped();
            prompt();
        }
    }

    /** The microphone prompt was answered: dictate in place, or fall back to the voice prompt. */
    void onMicPermission(boolean granted) {
        if (granted) listen();
        else prompt();
    }

    Intent speechIntent() {
        boolean isTr = I18n.isTr(a);
        String langTag = I18n.locale(a).toLanguageTag();
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                .putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{isTr || I18n.isRtl(a) ? "en-US" : "hi-IN"})
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, a.getPackageName())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Say a movie, show or app", "Bir film, dizi veya uygulama s\u00f6yleyin"));
    }

    /** The TV's own voice prompt (Google's on Android TV); the answer arrives in onVoice. */
    void prompt() {
        if (!voice) {
            a.toast(tr("Voice input isn't available on this TV", "Bu TV'de sesli arama kullan\u0131lam\u0131yor"));
            return;
        }
        try {
            Intent i = speechIntent();
            i.removeExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS);
            a.startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) {
            a.toast(tr("Voice input isn't available on this TV", "Bu TV'de sesli arama kullan\u0131lam\u0131yor"));
        }
    }

    void stopped() {
        listening = false;
        if (mic != null) mic.animate().scaleX(mic.isFocused() ? 1.12f : 1f).scaleY(mic.isFocused() ? 1.12f : 1f).setDuration(150).start();
        paintMic();
        showQuery();
    }

    /** Live words go straight into the query, so results follow the voice like typing. */
    final class Dictation implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle b) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onBufferReceived(byte[] buf) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int type, Bundle b) { }

        /** The mic swells gently with the voice level, only while listening. */
        @Override public void onRmsChanged(float db) {
            if (mic == null || !listening) return;
            float s = 1f + Math.max(0f, Math.min(1f, (db + 2f) / 12f)) * 0.18f;
            mic.setScaleX(s);
            mic.setScaleY(s);
        }

        @Override public void onPartialResults(Bundle b) {
            String t = first(b);
            if (!t.isEmpty() && !closed) {
                heard = true;
                setQuery(t.toLowerCase());
            }
        }

        @Override public void onResults(Bundle b) {
            String t = first(b);
            if (closed) return;
            stopped();
            if (!t.isEmpty()) setQuery(t.toLowerCase());
        }

        @Override public void onError(int error) {
            if (closed) return;
            boolean wasHeard = heard;
            stopped();
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    || (!wasHeard && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER
                    || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY))) {
                prompt();  // the service won't dictate for us: use the TV's voice prompt
            } else if (!wasHeard) {
                queryView.setText(error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                        ? tr("Voice needs the internet", "Sesli arama i\u00e7in internet gerekli")
                        : tr("Didn\u2019t catch that \u2014 press the mic to try again", "Anla\u015f\u0131lamad\u0131 \u2014 tekrar denemek i\u00e7in mikrofona bas\u0131n"));
                queryView.setAlpha(0.6f);
            }
        }

        String first(Bundle b) {
            ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0).trim();
        }
    }

    void onVoice(Intent data) {
        if (data == null) return;
        ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r != null && !r.isEmpty()) setQuery(r.get(0));
    }

    void showQuery() {
        boolean empty = query.length() == 0;
        queryView.setText(empty ? (listening ? tr("Listening\u2026", "Dinleniyor\u2026") : tr("Search apps, movies and shows", "Uygulama, film ve dizi ara")) : query);
        queryView.setAlpha(empty ? 0.45f : 1f);
    }

    /** Every key press: the local rows at once; titles from the cache, or fetched once typing pauses. */
    void changed() {
        showQuery();
        a.handler.removeCallbacks(fetch);
        gen++;
        Search.cancel();
        render();
        String q = q();
        if (q.length() >= 2 && ((Search.titlesEnabled(a) && !cache.containsKey(q.toLowerCase())) || !inAppCache.containsKey(q.toLowerCase()))) {
            a.handler.postDelayed(fetch, DEBOUNCE_MS);
        }
    }

    String q() {
        return query.toString().trim();
    }

    // ---------------------------------------------------------------- results

    void render() {
        View focus = a.getCurrentFocus();
        boolean inResults = focus != null && isInside(focus, list);
        list.removeAllViews();
        titleRow = stremioRow = inAppRow = null;
        titleStatus = null;
        String q = q();
        if (q.isEmpty()) {
            renderSuggestions();
        } else {
            List<View> local = localResults(q);
            if (!local.isEmpty()) addRow(tr("Apps & Settings", "Uygulamalar ve Ayarlar"), local, a.dp(92));
            boolean hasAppTargets = false;
            if (q.length() >= 2) {
                List<Search.AppSearchTarget> targets = Search.appSearchTargets(searchableApps, q);
                if (!targets.isEmpty()) {
                    hasAppTargets = true;
                    List<View> chips = new ArrayList<>();
                    for (Search.AppSearchTarget t : targets) {
                        chips.add(chip(t.label, () -> Search.openAppSearch(a, t)));
                    }
                    addRow(tr("Search in Apps", "Uygulamalarda Ara"), chips, a.dp(40));
                }
                List<Search.InAppResult> inAppHits = inAppCache.get(q.toLowerCase());
                if (inAppHits != null && !inAppHits.isEmpty()) fillInApp(inAppHits);
            }
            if (q.length() >= 2 && Search.titlesEnabled(a)) {
                titleRow = addRow(tr("Movies & TV", "Filmler ve Diziler"), new ArrayList<>(), a.dp(162));
                List<Search.Title> hit = cache.get(q.toLowerCase());
                if (hit != null) fillTitles(hit);
                else titleStatus(q.equals(failed) ? tr("Can\u2019t reach the internet", "\u0130nternete ula\u015f\u0131lam\u0131yor") : tr("Searching\u2026", "Aran\u0131yor\u2026"));
                if (Search.hasStremioApp(a)) {
                    List<Search.Title> sh = stremioCache.get(q.toLowerCase());
                    if (sh != null) fillStremio(sh);
                }
            } else if (local.isEmpty() && !hasAppTargets) {
                addNote(q.length() < 2 ? tr("Keep typing to search movies and shows", "Film ve dizileri aramak i\u00e7in yazmaya devam edin") : tr("No results", "Sonu\u00e7 bulunamad\u0131"));
            }
        }
        if (inResults) firstKey().requestFocus();
    }

    View firstKey() {
        return keys.getChildAt(0);
    }

    static boolean isInside(View v, View parent) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    /** Before typing: recent searches and what is trending in the carousel. */
    void renderSuggestions() {
        List<String> recent = Search.recent(a);
        if (!recent.isEmpty()) {
            List<View> chips = new ArrayList<>();
            for (String r : recent) chips.add(chip(r, () -> setQuery(r)));
            chips.add(chip(tr("Clear", "Temizle"), () -> {
                Search.clearRecent(a);
                render();
                firstKey().requestFocus();
            }));
            addRow(tr("Recent Searches", "Son Aramalar"), chips, a.dp(44));
        }
        List<View> trend = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Suggestions.Item> items = a.heroItems;
        if (items == null || items.isEmpty()) items = Suggestions.cached(a, true);
        if (items != null) {
            for (Suggestions.Item it : items) {
                if ("news".equals(it.type) || it.title == null || it.title.isEmpty() || !seen.add(it.title)) continue;
                if (!it.pkg.isEmpty()) continue;  // Continue Watching entries
                trend.add(chip(it.title, () -> setQuery(it.title)));
                if (trend.size() >= 10) break;
            }
        }
        if (!trend.isEmpty()) addRow(tr("Trending", "Trendler"), trend, a.dp(44));
        if (recent.isEmpty() && trend.isEmpty()) addNote(tr("Type to search your apps, settings, movies and shows", "Uygulamalar\u0131n\u0131z\u0131, ayarlar\u0131n\u0131z\u0131, filmleri ve dizileri aramak i\u00e7in yaz\u0131n"));
    }

    /** Apps (including hidden ones), TV inputs, tvOS Launcher actions and TV settings matching q, best first. */
    List<View> localResults(String q) {
        List<Object[]> hits = new ArrayList<>();
        for (MainActivity.AppEntry e : a.all.values()) {
            if (e.key.equals(MainActivity.FOLDER_KEY) || e.key.equals(MainActivity.INPUTS_KEY)) continue;
            int s = Search.score(e.label, null, q);
            if (s >= 0) hits.add(new Object[]{s, appCard(e)});
        }
        for (Inputs.Input in : a.inputs) {
            int s = Search.score(in.label, "input hdmi av tv source giri\u015f kaynak", q);
            if (s >= 0) hits.add(new Object[]{s, actionCard(in.label, Inputs.glyph(in), () -> Inputs.open(a, in))});
        }
        for (Object[] act : actions()) {
            int s = Search.score((String) act[0], (String) act[1], q);
            if (s >= 0) hits.add(new Object[]{s + 1, actionCard((String) act[0], (String) act[2], (Runnable) act[3])});
        }
        java.util.Collections.sort(hits, (x, y) -> Integer.compare((int) x[0], (int) y[0]));
        List<View> out = new ArrayList<>();
        for (int i = 0; i < hits.size() && i < 14; i++) out.add((View) hits.get(i)[1]);
        return out;
    }

    /** tvOS Launcher's own screens and the TV settings people look for: name, keywords, glyph, action. */
    List<Object[]> actions() {
        List<Object[]> l = new ArrayList<>();
        l.add(new Object[]{tr("Control Center", "Denetim Merkezi"), "controls power sleep denetim g\u00fc\u00e7 uyku \u05dc\u05d5\u05d7 \u05d1\u05e7\u05e8\u05d4 \u05db\u05d9\u05d1\u05d5\u05d9 \u05e9\u05d9\u05e0\u05d4", "controls", (Runnable) () -> { close(); a.showControlCenter(); }});
        l.add(new Object[]{tr("Weather Forecast", "Hava Durumu Tahmini"), "weather temperature rain hava s\u0131cakl\u0131k ya\u011fmur \u05de\u05d6\u05d2 \u05d0\u05d5\u05d5\u05d9\u05e8 \u05d8\u05de\u05e4\u05e8\u05d8\u05d5\u05e8\u05d4 \u05d2\u05e9\u05dd \u05ea\u05d7\u05d6\u05d9\u05ea", "refresh", (Runnable) () -> { close(); a.showWeather(); }});
        l.add(new Object[]{tr("Switch Profile", "Profil De\u011fi\u015ftir"), "profile user who profil kullan\u0131c\u0131 ki\u015fi \u05e4\u05e8\u05d5\u05e4\u05d9\u05dc \u05de\u05e9\u05ea\u05de\u05e9 \u05e6\u05d5\u05e4\u05d4", "controls", (Runnable) () -> { close(); a.showProfilePicker(false); }});
        l.add(new Object[]{tr("Home Settings", "tvOS Launcher Ayarlar\u0131"), "settings launcher preferences ayarlar g\u00f6r\u00fcn\u00fcm sahneler dil \u05d4\u05d2\u05d3\u05e8\u05d5\u05ea \u05de\u05e1\u05da \u05d4\u05d1\u05d9\u05ea \u05e9\u05e4\u05d4 \u05e1\u05e6\u05e0\u05d5\u05ea", "gear", (Runnable) () -> {
            a.returningFromSettings = true;
            a.startActivity(new Intent(a, SettingsActivity.class));
        }});
        l.add(new Object[]{tr("Refresh Carousel", "Vitrin Yenile"), "refresh reload news recommendations yenile haberler \u00f6neriler \u05e8\u05e2\u05e0\u05d5\u05df \u05d7\u05d3\u05e9\u05d5\u05ea \u05d4\u05de\u05dc\u05e6\u05d5\u05ea", "refresh", (Runnable) () -> { close(); a.refreshAll(); }});
        l.add(new Object[]{"Wi-Fi", "wifi network internet a\u011f \u05e8\u05e9\u05ea \u05d0\u05d9\u05e0\u05d8\u05e8\u05e0\u05d8 \u05d0\u05dc\u05d7\u05d5\u05d8\u05d9", "wifi", settings(Settings.ACTION_WIFI_SETTINGS)});
        l.add(new Object[]{"Bluetooth", "bluetooth remote controller headphones speaker pair kumanda kulakl\u0131k hoparl\u00f6r \u05e9\u05dc\u05d8 \u05d0\u05d5\u05d6\u05e0\u05d9\u05d5\u05ea \u05e8\u05de\u05e7\u05d5\u05dc \u05e6\u05d9\u05de\u05d5\u05d3", "gamepad", settings(Settings.ACTION_BLUETOOTH_SETTINGS)});
        l.add(new Object[]{tr("Sound", "Ses"), "sound audio volume speaker ses hoparl\u00f6r \u05e6\u05dc\u05d9\u05dc \u05e9\u05de\u05e2 \u05e2\u05d5\u05e6\u05de\u05d4 \u05e8\u05de\u05e7\u05d5\u05dc", "audio", settings("android.settings.SOUND_SETTINGS")});
        l.add(new Object[]{tr("Display", "Ekran"), "display resolution screen picture ekran \u00e7\u00f6z\u00fcn\u00fcrl\u00fck g\u00f6r\u00fcnt\u00fc \u05ea\u05e6\u05d5\u05d2\u05d4 \u05de\u05e1\u05da \u05e8\u05d6\u05d5\u05dc\u05d5\u05e6\u05d9\u05d4 \u05ea\u05de\u05d5\u05e0\u05d4", "present", settings(Settings.ACTION_DISPLAY_SETTINGS)});
        l.add(new Object[]{tr("Screen Saver", "Ekran Koruyucu"), "screensaver dream ambient ekran koruyucu \u05e9\u05d5\u05de\u05e8 \u05de\u05e1\u05da \u05de\u05e6\u05d1 \u05d0\u05d5\u05d5\u05d9\u05e8\u05d4", "moon", settings("android.settings.DREAM_SETTINGS")});
        l.add(new Object[]{tr("Date & Time", "Tarih ve Saat"), "date time clock tarih saat \u05ea\u05d0\u05e8\u05d9\u05da \u05e9\u05e2\u05d4 \u05e9\u05e2\u05d5\u05df", "timer", settings(Settings.ACTION_DATE_SETTINGS)});
        l.add(new Object[]{tr("Accessibility", "Eri\u015filebilirlik"), "accessibility captions eri\u015filebilirlik altyaz\u0131 \u05e0\u05d2\u05d9\u05e9\u05d5\u05ea \u05db\u05ea\u05d5\u05d1\u05d9\u05d5\u05ea", "accessibility", settings(Settings.ACTION_ACCESSIBILITY_SETTINGS)});
        l.add(new Object[]{tr("Apps", "Uygulamalar"), "apps applications uninstall storage uygulamalar kald\u0131r depolama \u05d0\u05e4\u05dc\u05d9\u05e7\u05e6\u05d9\u05d5\u05ea \u05d4\u05e1\u05e8\u05d4 \u05d0\u05d7\u05e1\u05d5\u05df", "memory", settings(Settings.ACTION_APPLICATION_SETTINGS)});
        l.add(new Object[]{tr("Storage", "Depolama"), "storage space memory depolama alan bellek \u05d0\u05d7\u05e1\u05d5\u05df \u05de\u05e7\u05d5\u05dd \u05d6\u05d9\u05db\u05e8\u05d5\u05df", "memory", settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)});
        l.add(new Object[]{tr("TV Settings", "TV Ayarlar\u0131"), "settings system device ayarlar sistem cihaz", "gear", settings(Settings.ACTION_SETTINGS)});
        return l;
    }

    Runnable settings(String action) {
        return () -> {
            if (!Suggestions.start(a, new Intent(action))) Suggestions.start(a, new Intent(Settings.ACTION_SETTINGS));
        };
    }

    /** A section title and a horizontal row; items keep focus inside the row at its ends. */
    LinearLayout addRow(String title, List<View> items, int height) {
        TextView h = a.text(12.5f, Ui.font(a, "semibold"), 0.72f);
        h.setText(title);
        h.setShadowLayer(0, 0, 0, 0);
        h.setPadding(a.dp(22), a.dp(12), a.dp(22), a.dp(2));
        list.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(a.dp(22), a.dp(8), a.dp(22), a.dp(8));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        hs.addView(row);
        list.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height + a.dp(16)));
        setItems(row, items);
        return row;
    }

    void setItems(LinearLayout row, List<View> items) {
        row.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            View v = items.get(i);
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) v.getLayoutParams()
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = a.dp(14);
            row.addView(v, lp);
        }
        if (!items.isEmpty()) {
            items.get(0).setNextFocusLeftId(items.get(0).getId());
            View last = items.get(items.size() - 1);
            last.setNextFocusRightId(last.getId());
        }
    }

    void addNote(String s) {
        TextView t = a.text(14, Ui.font(a, "regular"), 0.6f);
        t.setText(s);
        t.setShadowLayer(0, 0, 0, 0);
        t.setPadding(a.dp(22), a.dp(20), a.dp(22), 0);
        list.addView(t);
    }

    void titleStatus(String s) {
        if (titleRow == null) return;
        titleRow.removeAllViews();
        titleStatus = a.text(14, Ui.font(a, "regular"), 0.6f);
        titleStatus.setText(s);
        titleStatus.setShadowLayer(0, 0, 0, 0);
        titleRow.addView(titleStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(144)));
        titleStatus.setGravity(Gravity.CENTER_VERTICAL);
    }

    // ---------------------------------------------------------------- titles (network)

    void fetchTitles() {
        String q = q();
        if (q.length() < 2 || closed) return;
        int g = gen;
        String key = q.toLowerCase();
        if (Search.titlesEnabled(a) && !cache.containsKey(key)) {
            net.execute(() -> {
                List<Search.Title> r = null;
                try {
                    r = Search.titles(a, q, apps);
                } catch (Exception e) {
                    android.util.Log.w("TvHome", "search: " + e);
                }
                List<Search.Title> res = r;
                a.handler.post(() -> {
                    if (closed) return;
                    if (res != null) cache.put(key, res);
                    else failed = q;
                    if (g != gen) return;
                    if (res != null) fillTitles(res);
                    else titleStatus(tr("Can\u2019t reach the internet", "\u0130nternete ula\u015f\u0131lam\u0131yor"));
                });
            });
        }
        if (Search.titlesEnabled(a) && Search.hasStremioApp(a) && !stremioCache.containsKey(key)) {
            net.execute(() -> {
                List<Search.Title> r;
                try {
                    r = Search.stremio(a, q);
                } catch (Exception e) {
                    return;
                }
                a.handler.post(() -> {
                    if (closed) return;
                    stremioCache.put(key, r);
                    if (g == gen) fillStremio(r);
                });
            });
        }
        if (!inAppCache.containsKey(key)) {
            net.execute(() -> {
                List<Search.InAppResult> r = Search.queryAppSearchProviders(a, q, 12);
                a.handler.post(() -> {
                    if (closed) return;
                    inAppCache.put(key, r);
                    if (g == gen) fillInApp(r);
                });
            });
        }
    }

    void fillTitles(List<Search.Title> ts) {
        if (titleRow == null) return;
        if (ts.isEmpty()) {
            titleStatus(tr("No movies or shows found", "Film veya dizi bulunamad\u0131"));
            return;
        }
        List<View> cards = new ArrayList<>();
        for (Search.Title t : ts) cards.add(titleCard(t));
        setItems(titleRow, cards);
        // Stremio-only titles may have arrived first; drop the ones JustWatch now lists.
        List<Search.Title> sh = stremioCache.get(q().toLowerCase());
        if (sh != null) fillStremio(sh);
    }

    /** Titles only Stremio's catalogue has (JustWatch lists the rest with their services). */
    void fillStremio(List<Search.Title> ts) {
        Set<String> listed = new HashSet<>();
        List<Search.Title> jw = cache.get(q().toLowerCase());
        if (jw != null) for (Search.Title t : jw) if (!t.imdb.isEmpty()) listed.add(t.imdb);
        List<View> cards = new ArrayList<>();
        for (Search.Title t : ts) if (!listed.contains(t.imdb)) cards.add(titleCard(t));
        if (cards.isEmpty()) return;
        if (stremioRow == null) {
            // Every app that can open these: "On Stremio \u00b7 Nuvio \u00b7 Nuvio Legacy".
            List<String> names = new ArrayList<>();
            for (Suggestions.Provider p : Suggestions.stremioApps(a)) names.add(p.name);
            if (names.isEmpty()) names.add(Suggestions.stremioApp(a).name);
            stremioRow = addRow(tr("On ", "") + android.text.TextUtils.join(" \u00b7 ", names) + tr("", " \u00fczerinde"), cards, a.dp(162));
        }
        else setItems(stremioRow, cards);
    }

    void fillInApp(List<Search.InAppResult> items) {
        if (items == null || items.isEmpty()) return;
        List<View> cards = new ArrayList<>();
        for (Search.InAppResult r : items) cards.add(inAppCard(r));
        if (inAppRow == null) {
            inAppRow = addRow(tr("In-App Results", "Uygulama İçi Sonuçlar"), cards, a.dp(162));
        } else {
            setItems(inAppRow, cards);
        }
    }

    View inAppCard(Search.InAppResult r) {
        int w = a.dp(86), h = a.dp(129);
        FrameLayout art = new FrameLayout(a);
        art.setBackgroundColor(0x22FFFFFF);
        ImageView poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(poster, a.match());
        TextView fallback = a.text(11.5f, Ui.font(a, "semibold"), 0.85f);
        fallback.setText(r.title);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(a.dp(6), 0, a.dp(6), 0);
        art.addView(fallback, a.match());
        if (!r.art.isEmpty()) load(poster, r.art, w, fallback);
        String label = r.title + (!r.appName.isEmpty() ? "  ·  " + r.appName : "");
        View cell = cell(art, w, h, label, a.dp(10));
        cell.setOnClickListener(v -> {
            Search.remember(a, q());
            Search.openInAppResult(a, r);
        });
        return cell;
    }

    // ---------------------------------------------------------------- cards

    /** Squircle app icon matching the Other Apps modal design, name centered below. */
    View appCard(MainActivity.AppEntry e) {
        int sz = a.dp(60);
        int rad = a.dp(14);
        FrameLayout art = new FrameLayout(a);
        art.setBackgroundColor(e.color != 0 ? e.color : 0xFF1A1C22);
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable d = e.icon != null ? e.icon : e.banner;
        iv.setImageDrawable(d);
        art.addView(iv, a.match());
        View cell = cell(art, sz, sz, e.label, rad, true);
        cell.setOnClickListener(v -> {
            Search.remember(a, q());
            a.launchAnimated(v, e);
        });
        return cell;
    }

    View actionCard(String label, String glyph, Runnable run) {
        int sz = a.dp(60);
        int rad = a.dp(14);
        ImageView art = new ImageView(a);
        art.setScaleType(ImageView.ScaleType.CENTER);
        art.setImageBitmap(Glyphs.get(glyph, a.dp(24), 0xFFF4F4F6));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x38FFFFFF, 0x18FFFFFF});
        bg.setCornerRadius(rad);
        bg.setStroke(Math.max(1, a.dp(0.8f)), 0x26FFFFFF);
        art.setBackground(bg);
        View cell = cell(art, sz, sz, label, rad, true);
        cell.setOnClickListener(v -> {
            Search.remember(a, q());
            run.run();
        });
        return cell;
    }

    /** A movie or series poster (2:3) with the logos of up to three services that have it. */
    View titleCard(Search.Title t) {
        int w = a.dp(86), h = a.dp(129);
        FrameLayout art = new FrameLayout(a);
        art.setBackgroundColor(0x22FFFFFF);
        ImageView poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(poster, a.match());
        TextView fallback = a.text(11.5f, Ui.font(a, "semibold"), 0.85f);
        fallback.setText(t.title);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(a.dp(6), 0, a.dp(6), 0);
        art.addView(fallback, a.match());
        LinearLayout logos = new LinearLayout(a);
        logos.setOrientation(LinearLayout.HORIZONTAL);
        logos.setPadding(a.dp(4), 0, 0, a.dp(4));
        int n = 0;
        for (Search.Offer o : t.offers) {
            if (o.icon.isEmpty() || n >= 3) continue;
            ImageView li = new ImageView(a);
            li.setScaleType(ImageView.ScaleType.FIT_CENTER);
            round(li, a.dp(4));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(a.dp(18), a.dp(18));
            llp.rightMargin = a.dp(3);
            logos.addView(li, llp);
            load(li, o.icon, a.dp(18), null);
            n++;
        }
        art.addView(logos, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        if (!t.poster.isEmpty()) load(poster, t.poster, w, fallback);
        View cell = cell(art, w, h, t.title + (t.year.isEmpty() ? "" : "  ·  " + t.year), a.dp(10), false);
        cell.setOnClickListener(v -> showSheet(t, v));
        return cell;
    }

    View cell(View art, int w, int h, String label, int radius) {
        return cell(art, w, h, label, radius, false);
    }

    /** Card art with a label below; focus adds crisp white ring and smooth lift matching Other Apps. */
    View cell(View art, int w, int h, String label, int radius, boolean centerLabel) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(centerLabel ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        round(art, radius);
        Drawable ring = SceneHome.createIconFocusRingDrawable(radius, a.dp(2f), false);
        cell.addView(art, new LinearLayout.LayoutParams(w, h));
        TextView l = a.text(11f, Ui.font(a, "medium"), 0.72f);
        l.setText(label);
        l.setGravity(centerLabel ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setShadowLayer(0, 0, 0, 0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                centerLabel ? Math.max(w, a.dp(74)) : w, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = a.dp(6);
        cell.addView(l, llp);
        cell.setOnFocusChangeListener((v, has) -> {
            art.setForeground(has ? ring : null);
            art.animate().scaleX(has ? 1.09f : 1f).scaleY(has ? 1.09f : 1f)
                    .setDuration(170).setInterpolator(MainActivity.LIFT).start();
            l.setAlpha(has ? 1f : 0.72f);
            l.animate().translationY(has ? a.dp(3) : 0).setDuration(170).start();
        });
        art.setPivotY(h / 2f);
        art.setPivotX(w / 2f);
        return cell;
    }

    View chip(String label, Runnable click) {
        TextView t = a.text(12.5f, Ui.font(a, "medium"), 1f);
        t.setText(label);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(0, 0, 0, 0);
        t.setPadding(a.dp(15), 0, a.dp(15), 0);
        t.setFocusable(true);
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(14));
        bg.setColor(0x1AFFFFFF);
        bg.setStroke(Math.max(1, a.dp(0.8f)), 0x28FFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x1AFFFFFF);
            bg.setStroke(Math.max(1, a.dp(0.8f)), has ? 0xFFFFFFFF : 0x28FFFFFF);
            t.setTextColor(has ? 0xFF111114 : 0xFFF4F4F6);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(160).start();
        });
        t.setOnClickListener(v -> click.run());
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(28)));
        return t;
    }

    void round(View v, int r) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** Decodes an image at the size it is shown, off the UI thread, newest request wins per view. */
    void load(ImageView iv, String url, int width, View hideWhenLoaded) {
        iv.setTag(url);
        Bitmap mem = bitmaps.get(url);
        if (mem != null) {
            iv.setImageBitmap(mem);
            if (hideWhenLoaded != null) hideWhenLoaded.setVisibility(View.GONE);
            return;
        }
        img.execute(() -> {
            if (closed || !url.equals(iv.getTag())) return;
            Bitmap b = url.startsWith(Search.APP_ICON) ? appIcon(url.substring(Search.APP_ICON.length()), Math.max(64, width))
                    : Suggestions.image(a, url, "", Math.max(64, width));
            if (b == null) return;
            a.handler.post(() -> {
                if (closed) return;
                bitmaps.put(url, b);
                if (url.equals(iv.getTag())) {
                    iv.setImageBitmap(b);
                    iv.setAlpha(0f);
                    iv.animate().alpha(1f).setDuration(160).start();
                    if (hideWhenLoaded != null) hideWhenLoaded.setVisibility(View.GONE);
                }
            });
        });
    }

    /** An installed app's launcher icon as a square bitmap (offers without a JustWatch logo: Stremio, Nuvio, forks). */
    Bitmap appIcon(String pkg, int size) {
        try {
            Drawable d = a.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, size, size);
            d.draw(new android.graphics.Canvas(b));
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- How to Watch sheet

    /**
     * The title's page, like the Apple TV app's: artwork behind, title, details and summary on
     * the left, and "How to Watch" with one button per app (Open when installed, Get otherwise).
     */
    void showSheet(Search.Title t, View from) {
        Search.remember(a, q());
        sheetReturn = from;
        FrameLayout s = new FrameLayout(a);
        s.setClickable(true);
        s.setBackgroundColor(0x66000000);
        s.setOnClickListener(v -> closeSheet());

        int panelW = Math.min(a.dp(780), Math.max(a.dp(520), a.screenW - a.dp(72)));
        int panelH = Math.min(a.dp(448), Math.max(a.dp(340), a.screenH - a.dp(60)));
        FrameLayout card = new FrameLayout(a);
        card.setClickable(true);
        card.setBackgroundColor(0xFF14161E);
        card.setOutlineProvider(a.roundedOutline(new float[]{a.dp(24)}));
        card.setClipToOutline(true);
        card.setElevation(a.dp(30));

        ImageView back = new ImageView(a);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(back, a.match());
        View scrim = new View(a);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF012141C, 0xCC12141C, 0x4412141C}));
        card.addView(scrim, a.match());
        String art = !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        if (!art.isEmpty()) load(back, art, Math.min(1280, panelW), null);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.dp(30), a.dp(28), a.dp(30), a.dp(24));
        TextView title = a.text(28, Ui.font(a, "display"), 1f);
        title.setText(t.title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title, new LinearLayout.LayoutParams(a.dp(480), ViewGroup.LayoutParams.WRAP_CONTENT));
        List<String> meta = new ArrayList<>();
        if (!t.year.isEmpty()) meta.add(t.year);
        meta.add(t.type.equals("tv") ? tr("Series", "Dizi") : tr("Movie", "Film"));
        if (!t.genres.isEmpty()) meta.add(t.genres);
        if (t.rating > 0) meta.add(String.format(java.util.Locale.US, "IMDb %.1f", t.rating));
        TextView m = a.text(13, Ui.font(a, "medium"), 0.78f);
        m.setText(android.text.TextUtils.join("  \u00b7  ", meta));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = a.dp(6);
        col.addView(m, mlp);
        if (!t.overview.isEmpty()) {
            TextView o = a.text(13.5f, Ui.font(a, "regular"), 0.85f);
            o.setText(t.overview);
            o.setMaxLines(3);
            o.setEllipsize(TextUtils.TruncateAt.END);
            o.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(a.dp(430), ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.topMargin = a.dp(10);
            col.addView(o, olp);
        }
        TextView how = a.text(12, Ui.font(a, "semibold"), 0.72f);
        how.setText(tr("HOW TO WATCH", "NASIL \u0130ZLEN\u0130R"));
        how.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = a.dp(18);
        hlp.bottomMargin = a.dp(8);
        col.addView(how, hlp);
        List<View> buttons = new ArrayList<>();
        for (Search.Offer o : t.offers) {
            if (buttons.size() >= 4) break;
            View b = offerButton(t, o);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(a.dp(360), a.dp(38));
            blp.bottomMargin = a.dp(8);
            col.addView(b, blp);
            buttons.add(b);
        }
        if (buttons.isEmpty()) {
            TextView none = a.text(14, Ui.font(a, "regular"), 0.7f);
            none.setText(tr("Not streaming right now.", "\u015eu anda yay\u0131nda de\u011fil."));
            col.addView(none);
        }
        card.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View border = new View(a);
        GradientDrawable bdr = new GradientDrawable();
        bdr.setCornerRadius(a.dp(24));
        bdr.setColor(Color.TRANSPARENT);
        bdr.setStroke(Math.max(1, a.dp(1f)), 0x33FFFFFF);
        border.setBackground(bdr);
        card.addView(border, a.match());

        s.addView(card, new FrameLayout.LayoutParams(panelW, panelH, Gravity.CENTER));
        layer.addView(s, a.match());
        sheet = s;
        s.setAlpha(0f);
        card.setScaleX(0.95f);
        card.setScaleY(0.95f);
        s.animate().alpha(1f).setDuration(200).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(210).setInterpolator(MainActivity.LIFT).start();
        if (!buttons.isEmpty()) buttons.get(0).requestFocus();
        else s.setFocusable(true);
        if (buttons.isEmpty()) s.requestFocus();
    }

    /** [logo] Open in Prime Video ... Subscription, or Get JioHotstar for apps that aren't installed. */
    View offerButton(Search.Title t, Search.Offer o) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(a.dp(8), 0, a.dp(16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(20));
        bg.setColor(0x33FFFFFF);
        b.setBackground(bg);
        ImageView logo = new ImageView(a);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        round(logo, a.dp(6));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(a.dp(26), a.dp(26));
        llp.rightMargin = a.dp(10);
        b.addView(logo, llp);
        if (!o.icon.isEmpty()) load(logo, o.icon, a.dp(26), null);
        else logo.setImageBitmap(Glyphs.get("present", a.dp(26), Color.WHITE));
        TextView name = a.text(15, Ui.font(a, "semibold"), 1f);
        name.setShadowLayer(0, 0, 0, 0);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        boolean have = o.installed();
        name.setText(have ? tr("\u25b6  Open in " + o.service, "\u25b6  " + o.service + " ile A\u00e7") : tr("Get " + o.service, o.service + " \u0130ndir"));
        b.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView kind = a.text(12, Ui.font(a, "medium"), 0.7f);
        kind.setShadowLayer(0, 0, 0, 0);
        String notInstalled = tr("Not installed", "Y\u00fckl\u00fc de\u011fil");
        String kindLabel = I18n.t(a, o.label());
        kind.setText(have ? kindLabel : kindLabel.isEmpty() ? notInstalled : kindLabel + "  \u00b7  " + notInstalled);
        b.addView(kind);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x33FFFFFF);
            name.setTextColor(has ? 0xFF111114 : Color.WHITE);
            kind.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(180).setInterpolator(MainActivity.LIFT).start();
        });
        b.setPivotX(0);
        b.setOnClickListener(v -> {
            if (o.installed()) {
                a.noteLaunched(o.pkg);
                Search.open(a, t, o);
            } else {
                Search.get(a, o);
            }
        });
        return b;
    }

    void closeSheet() {
        FrameLayout s = sheet;
        sheet = null;
        if (s == null) return;
        s.animate().alpha(0f).setDuration(180).withEndAction(() -> layer.removeView(s)).start();
        if (sheetReturn != null && sheetReturn.isAttachedToWindow()) sheetReturn.requestFocus();
        else firstKey().requestFocus();
    }

    // ---------------------------------------------------------------- keys and lifecycle

    /** Keys while search is open; false lets the framework move focus. */
    boolean dispatch(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (!down) {
                if (sheet != null) closeSheet();
                else a.closeSearch();
            }
            return true;
        }
        if (sheet != null) {
            return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        if (k == KeyEvent.KEYCODE_DEL) {
            if (down) backspace();
            return true;
        }
        if (k == KeyEvent.KEYCODE_SEARCH || k == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (!down && ev.getRepeatCount() == 0) {
                if (mic != null) mic.requestFocus();
                listen();
            }
            return true;
        }
        if (down && k != KeyEvent.KEYCODE_DPAD_CENTER && k != KeyEvent.KEYCODE_ENTER) {
            int ch = ev.getUnicodeChar();
            if (ch > 0 && !Character.isISOControl(ch)) {  // a hardware keyboard, or the remote's digits
                type(String.valueOf((char) ch).toLowerCase());
                return true;
            }
        }
        View f = a.getCurrentFocus();
        if (mic != null && k == KeyEvent.KEYCODE_DPAD_UP && f != null && f.getParent() == keys) {
            if (down) mic.requestFocus();
            return true;
        }
        if (f == mic && mic != null) {
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (down) firstKey().requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        }
        // Down from the keyboard lands on the first item of the first row, not wherever is nearest.
        if (k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() == keys) {
            if (down) {
                View first = firstResult();
                if (first != null) first.requestFocus();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP && f != null && isInside(f, list) && f.getParent() == firstRow()) {
            if (down) firstKey().requestFocus();
            return true;
        }
        return false;
    }

    LinearLayout firstRow() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (c instanceof HorizontalScrollView) {
                LinearLayout row = (LinearLayout) ((HorizontalScrollView) c).getChildAt(0);
                if (row.getChildCount() > 0 && row.getChildAt(0).isFocusable()) return row;
            }
        }
        return null;
    }

    View firstResult() {
        LinearLayout r = firstRow();
        return r == null ? null : r.getChildAt(0);
    }

    void close() {
        a.closeSearch();
    }

    /** Stops work and frees images; the layer itself is removed by MainActivity. */
    void release() {
        closed = true;
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        a.handler.removeCallbacks(fetch);
        Search.cancel();
        net.shutdownNow();
        img.shutdownNow();
        bitmaps.evictAll();
    }
}
