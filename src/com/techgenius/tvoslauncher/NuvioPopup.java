package com.techgenius.tvoslauncher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.nayuki.qrcodegen.QrCode;

/**
 * Popup for the Nuvio dock tile, drawn in the same style and spot as the Keep Watching shelf.
 *
 * - Launcher opened/resumed: if signed in to Nuvio, continue-watching data refreshes in the
 *   background. If not signed in, nothing happens (no network, no prompt).
 * - Focus on the Nuvio tile: shows the cached continue-watching row instantly. When not signed in,
 *   it shows a one-time QR login instead; once approved on the phone, the row loads right away.
 * - DPAD Up from the tile moves into the row; OK opens the title in Nuvio; Down returns to the dock.
 * - While the popup is open, the Keep Watching shelf behind it fades out.
 * - Accounts with several Nuvio profiles get profile buttons next to the header (Up from a card
 *   reaches them). Moving onto a profile shows its row as soon as the highlight lands there.
 */
final class NuvioPopup {

    private static final int MAX_ITEMS = 5;
    private static final long REFRESH_AFTER_MS = 60_000L;  // don't re-pull on every quick resume
    /** QR size: small enough that the sign-in box stays between the clock and the dock. */
    private static final float QR_DP = 96;
    /** Placeholder cards while a row loads; three fill the row like the Keep Watching shelf. */
    private static final int GHOST_CARDS = 3;
    private static final String PREF_ENABLED = "nuvio_popup";

    /** Settings switch: off = no popup, no sign-in prompt, no network calls. On by default. */
    static boolean isEnabled(android.content.Context c) {
        return c.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, true);
    }

    static void setEnabled(android.content.Context c, boolean on) {
        c.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, on).apply();
    }

    private final SceneHome home;
    private final MainActivity host;
    private final FrameLayout overlay;
    private final NuvioCloud cloud;
    /** Own thread: QR polling must never queue behind the launcher's wallpaper/blur work on host.io. */
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    /** contentId -> 16:9 thumbnail. */
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(16);

    private final LinearLayout panel;
    private final List<View> cards = new ArrayList<>();
    private final Map<String, ImageView> thumbViews = new HashMap<>();
    /** Glass box for the sign-in / status message, when shown instead of cards. */
    private View statusBox;
    /** Placeholder cards shown while a row loads. */
    private final List<View> ghosts = new ArrayList<>();
    /** How far the cards sit inside the popup's left edge (room for the focus lift). */
    private int rowInset;
    /** Height of the plain header text; the shelf's "Keep watching" header is the same size. */
    private int headerTextH;

    /** Nuvio profiles of the signed-in account; buttons show when there are 2 or more. */
    private List<NuvioCloud.Profile> profiles;
    private final List<View> profileButtons = new ArrayList<>();
    private View profileBar;
    private Slider slider;
    /** Profiles the current bar was built for; a different list builds a new bar. */
    private String profileBarSig;
    /** Bumped by every load; a result from an older load (e.g. before a profile switch) is dropped. */
    private int loadGen;

    private View anchor;
    private String anchorPkg;
    private boolean visible;

    private List<NuvioCloud.Item> items;  // null = never loaded
    private boolean loading, loadFailed;
    private long lastLoad;

    private NuvioCloud.QrLogin qr;
    private Bitmap qrBitmap;
    private boolean startingQr, qrFailed;
    private int pollGeneration;

    NuvioPopup(SceneHome home) {
        this.home = home;
        this.host = home.host;
        this.overlay = home.overlay;
        this.cloud = new NuvioCloud(host);
        boolean signedIn = cloud.isSignedIn();
        this.items = signedIn ? cloud.loadCache(cloud.profile()) : null;
        this.profiles = signedIn ? cloud.loadProfilesCache() : new ArrayList<>();

        // No panel background: like the shelf, the header floats over the wallpaper and the
        // cards themselves are liquid glass.
        panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setVisibility(View.GONE);
        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
    }

    static boolean isNuvio(String pkg) {
        return pkg != null && pkg.startsWith("com.nuvio.");
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /** Launcher opened or came back to the front. Signed out: do nothing at all. */
    void onLauncherResume() {
        if (!isEnabled(host) || !cloud.isSignedIn()) return;
        if (!loading && System.currentTimeMillis() - lastLoad > REFRESH_AFTER_MS) load();
    }

    void onLauncherPause() {
        host.handler.removeCallbacks(applyFocusedProfile);
        stopPolling();
        hide();
    }

    /** Called from the dock tile's focus listener for the Nuvio app. */
    void onTileFocus(View tile, String pkg, boolean has) {
        if (has) {
            show(tile, pkg);
        } else {
            overlay.post(this::hideIfFocusLeft);
        }
    }

    /**
     * DPAD Up on the Nuvio tile. The dock handles its own keys (so nextFocusUpId is never used);
     * it calls this first. Returns false when there's no row to move into.
     */
    boolean focusRow(View tile) {
        if (!visible || tile != anchor) return false;
        View target = !cards.isEmpty() ? cards.get(0) : selectedProfileButton();
        if (target == null) return false;
        target.requestFocus();
        return true;
    }

    /** The wallpaper behind the glass changed. */
    void invalidateGlass() {
        for (View c : cards) c.invalidate();
        for (View g : ghosts) g.invalidate();
        if (statusBox != null) statusBox.invalidate();
        if (profileBar != null) profileBar.invalidate();
    }

    // ------------------------------------------------------------------ show / hide

    private void show(View tile, String pkg) {
        if (!isEnabled(host)) {
            stopPolling();
            hide();
            return;
        }
        anchor = tile;
        anchorPkg = pkg;
        if (tile.getId() == View.NO_ID) tile.setId(View.generateViewId());
        boolean wasVisible = visible;
        visible = true;

        if (!cloud.isSignedIn()) {
            ensureQrLogin();
        } else if (items == null && !loading) {
            load();  // signed in, but nothing loaded yet (e.g. resume refresh failed)
        }
        render();
        if (!wasVisible) {
            home.setShelfDimmed(true);
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.animate().alpha(1f).setDuration(160).start();
        }
    }

    private void hideIfFocusLeft() {
        View f = host.getCurrentFocus();
        if (f != null && (f == anchor || f == panel || cards.contains(f) || profileButtons.contains(f))) return;
        hide();
    }

    private void hide() {
        if (!visible) return;
        visible = false;
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        home.setShelfDimmed(false);
        if (anchor != null) anchor.setNextFocusUpId(View.NO_ID);
        // QR polling keeps running until the code is approved or expires, so you can
        // scan, look away from the Nuvio tile, and still come back signed in.
    }

    // ------------------------------------------------------------------ data

    private void load() {
        final int gen = ++loadGen;
        final int wanted = cloud.profile();
        loading = true;
        if (visible) render();
        exec.execute(() -> {
            List<NuvioCloud.Profile> ps = null;
            List<NuvioCloud.Item> result = null;
            boolean signedOut = false, failed = false;
            try {
                try {
                    ps = cloud.profiles();
                } catch (NuvioCloud.NotSignedInException e) {
                    throw e;
                } catch (Exception e) {
                    // Keep the cached profile list; continue watching can still load.
                    android.util.Log.w("NuvioPopup", "Couldn't load Nuvio profiles", e);
                }
                int profile = wanted;
                if (ps != null && !ps.isEmpty() && find(ps, profile) == null) {
                    // The saved profile was deleted in Nuvio: fall back to the first one.
                    profile = ps.get(0).index;
                    cloud.setProfile(profile);
                }
                result = cloud.continueWatching(profile, MAX_ITEMS);
            } catch (NuvioCloud.NotSignedInException e) {
                signedOut = true;
            } catch (Exception e) {
                failed = true;
            }
            final List<NuvioCloud.Profile> p = ps;
            final List<NuvioCloud.Item> r = result;
            final boolean so = signedOut, fl = failed;
            host.handler.post(() -> {
                if (p != null) profiles = p;
                if (gen != loadGen) return;  // a newer load (another profile) is already on its way
                loading = false;
                if (so) {
                    items = null;
                    if (visible) ensureQrLogin();
                } else if (fl) {
                    loadFailed = true;  // keep showing the cached row, if any
                } else {
                    items = r;
                    loadFailed = false;
                    lastLoad = System.currentTimeMillis();
                }
                if (visible) render();
            });
        });
    }

    /** How long focus must rest on a profile before its row loads (about the slide's length). */
    private static final long PROFILE_APPLY_DELAY_MS = 350;

    private final Runnable applyFocusedProfile = this::applyFocusedProfile;

    /** Shows the focused profile's row once focus has stopped on it; passing over one does nothing. */
    private void applyFocusedProfile() {
        View f = host.getCurrentFocus();
        if (visible && profileButtons.contains(f)) selectProfile((Integer) f.getTag());
    }

    /** Show that profile's row (cached instantly, then refreshed). */
    private void selectProfile(int index) {
        if (index == cloud.profile()) return;
        cloud.setProfile(index);
        items = cloud.loadCache(index);
        loadFailed = false;
        load();
    }

    private static NuvioCloud.Profile find(List<NuvioCloud.Profile> ps, int index) {
        for (NuvioCloud.Profile p : ps) if (p.index == index) return p;
        return null;
    }

    private boolean showProfiles() {
        return cloud.isSignedIn() && profiles != null && profiles.size() > 1;
    }

    private View profileButton(int index) {
        for (View b : profileButtons) if (((Integer) b.getTag()) == index) return b;
        return null;
    }

    private View selectedProfileButton() {
        View b = profileButton(cloud.profile());
        return b != null ? b : (profileButtons.isEmpty() ? null : profileButtons.get(0));
    }

    // ------------------------------------------------------------------ QR login

    private void ensureQrLogin() {
        if (startingQr || (qr != null && !qr.expired())) return;
        startingQr = true;
        qrFailed = false;
        exec.execute(() -> {
            NuvioCloud.QrLogin l = null;
            try {
                l = cloud.startQrLogin("tvOS Launcher");
            } catch (Exception ignored) {
            }
            final NuvioCloud.QrLogin login = l;
            final Bitmap bmp = login != null ? qrBitmap(login.qrUrl, dp(QR_DP)) : null;
            host.handler.post(() -> {
                startingQr = false;
                qr = login;
                qrBitmap = bmp;
                qrFailed = login == null;
                if (login != null) startPolling(login);
                if (visible) render();
            });
        });
    }

    private void startPolling(NuvioCloud.QrLogin login) {
        final int gen = ++pollGeneration;
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (gen != pollGeneration) return;
            exec.execute(() -> {
                boolean approved = false, expired = false;
                try {
                    approved = cloud.pollQrLogin(login);
                } catch (NuvioCloud.QrExpiredException e) {
                    expired = true;
                } catch (Exception ignored) {
                    // Network blip: try again on the next tick.
                }
                final boolean ok = approved, exp = expired;
                host.handler.post(() -> {
                    if (gen != pollGeneration) return;
                    if (ok) {
                        qr = null;
                        qrBitmap = null;
                        pollGeneration++;
                        load();  // load continue watching right now, while the popup is open
                    } else if (exp) {
                        qr = null;
                        qrBitmap = null;
                        pollGeneration++;
                        if (visible) ensureQrLogin();  // fresh code while someone is looking at it
                    } else {
                        host.handler.postDelayed(tick[0], login.intervalMs);
                    }
                    if (visible) render();
                });
            });
        };
        host.handler.postDelayed(tick[0], login.intervalMs);
    }

    private void stopPolling() {
        pollGeneration++;
        qr = null;
        qrBitmap = null;
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        // If the user is inside the popup when it redraws, keep focus on the same card or profile.
        View f = host.getCurrentFocus();
        int focusedSlot = cards.indexOf(f);
        int focusedProfile = profileButtons.contains(f) ? (Integer) f.getTag() : -1;
        boolean keepFocus = focusedSlot >= 0 || focusedProfile >= 0 || f == panel;
        if (keepFocus && f != panel) {
            // Park focus on the panel while its children are replaced; otherwise Android would
            // hand it to some other view (e.g. a dock tile) for a moment.
            panel.setFocusable(true);
            panel.requestFocus();
        }
        panel.removeAllViews();
        cards.clear();
        thumbViews.clear();
        // The profile bar is kept across redraws (see profileBarView) so its slide isn't cut short.
        statusBox = null;
        ghosts.clear();
        rowInset = 0;

        String cw = I18n.s(host, "Continue watching", "İzlemeye devam et");
        if (!cloud.isSignedIn()) {
            addHeader(I18n.s(host, "Sign in to Nuvio", "Nuvio'ya giriş yap"), 0);
            renderLogin();
        } else if (items != null && !items.isEmpty()) {
            renderItems(cw);
        } else if (items == null && !loadFailed) {
            renderGhosts(cw);  // this profile's row hasn't loaded yet
        } else {
            addHeader(cw, 0);
            String msg = items == null ? I18n.s(host, "Couldn't reach Nuvio", "Nuvio'ya ulaşılamadı")
                    : I18n.s(host, "Nothing to continue yet", "Devam edecek bir şey yok");
            LinearLayout box = glassBox();
            box.addView(label(msg, 11.5f, 0xB8F4F4F6, "regular"));
            // As tall as a show card, so the header and profile buttons stay put when moving
            // between a profile with shows and one without.
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setMinimumHeight(dp(SceneHome.SHELF_CARD_H_DP));
            addStatusBox(box);
        }

        wireFocus();
        if (slider != null) tintProfileNames();  // the selected profile's name may have changed
        if (keepFocus) {
            View target = focusedProfile >= 0 ? profileButton(focusedProfile) : null;
            if (target == null && !cards.isEmpty()) {
                target = cards.get(Math.min(Math.max(0, focusedSlot), cards.size() - 1));
            }
            if (target == null) target = selectedProfileButton();
            if (target == null) target = anchor;
            if (target != null) target.requestFocus();
            panel.setFocusable(false);
        }
        position();
    }

    /**
     * Remote navigation: Up from a card goes to the selected profile button, Down from a profile
     * button goes to the first card (or the Nuvio tile when the row is empty). Both rows stop
     * at their ends instead of jumping out to the dock.
     */
    private void wireFocus() {
        View selected = selectedProfileButton();
        int belowProfiles = !cards.isEmpty() ? cards.get(0).getId()
                : anchor != null ? anchor.getId() : View.NO_ID;
        for (int i = 0; i < cards.size(); i++) {
            View c = cards.get(i);
            if (i == 0) c.setNextFocusLeftId(c.getId());
            if (i == cards.size() - 1) c.setNextFocusRightId(c.getId());
            if (anchor != null) c.setNextFocusDownId(anchor.getId());
            c.setNextFocusUpId(selected != null ? selected.getId() : c.getId());
        }
        for (int i = 0; i < profileButtons.size(); i++) {
            View b = profileButtons.get(i);
            if (i == 0) b.setNextFocusLeftId(b.getId());
            if (i == profileButtons.size() - 1) b.setNextFocusRightId(b.getId());
            b.setNextFocusUpId(b.getId());
            b.setNextFocusDownId(belowProfiles);
        }
        if (anchor != null) {
            View up = !cards.isEmpty() ? cards.get(0) : selected;
            anchor.setNextFocusUpId(up != null ? up.getId() : View.NO_ID);
        }
    }

    private void renderLogin() {
        LinearLayout box = glassBox();
        if (qr == null || qrBitmap == null) {
            box.addView(label(qrFailed ? I18n.s(host, "Couldn't reach Nuvio. Focus the tile again to retry.",
                            "Nuvio'ya ulaşılamadı. Tekrar denemek için simgeye yeniden gel.")
                    : I18n.s(host, "Preparing QR code…", "QR kodu hazırlanıyor…"), 11.5f, 0xB8F4F4F6, "regular"));
            addStatusBox(box);
            return;
        }
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        int qrSize = dp(QR_DP);
        ImageView code = new ImageView(host);
        code.setImageBitmap(qrBitmap);
        GradientDrawable white = new GradientDrawable();
        white.setCornerRadius(dp(8.5f));
        white.setColor(Color.WHITE);
        code.setBackground(white);
        code.setPadding(dp(5), dp(5), dp(5), dp(5));
        box.addView(code, new LinearLayout.LayoutParams(qrSize + dp(10), qrSize + dp(10)));

        LinearLayout text = new LinearLayout(host);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(I18n.s(host, "Scan with your phone and approve.",
                "Telefonunla tara ve onayla."), 11.5f, 0xFFF4F4F6, "semibold"));
        TextView how = label(I18n.s(host, "Or open ", "Veya şu adrese git: ")
                + qr.verificationUrl.replace("https://", "")
                + I18n.s(host, " and enter:", " ve kodu gir:"), 9.2f, 0xB8F4F4F6, "regular");
        how.setPadding(0, dp(2), 0, 0);
        text.addView(how);
        TextView userCode = label(qr.userCode, 18f, 0xFFF4F4F6, "semibold");
        userCode.setLetterSpacing(0.15f);
        userCode.setPadding(0, dp(4), 0, dp(4));
        text.addView(userCode);
        text.addView(label(I18n.s(host, "Waiting for approval…", "Onay bekleniyor…"), 9.2f, 0x8CF4F4F6, "regular"));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(12);
        box.addView(text, tlp);

        addStatusBox(box);
    }

    /** Placeholder cards in exactly the spot real cards will take, while the row loads. */
    private void renderGhosts(String header) {
        int cardW = dp(SceneHome.SHELF_CARD_W_DP), cardH = dp(SceneHome.SHELF_CARD_H_DP),
                gap = dp(SceneHome.SHELF_CARD_GAP_DP);
        int pad = dp(SceneHome.SHELF_HEADER_GAP_DP);  // same inset as the real card row
        rowInset = pad;
        addHeader(header, pad);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < GHOST_CARDS; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) lp.leftMargin = gap;
            View ghost = home.ghostWatchCard(i);
            row.addView(ghost, lp);
            ghosts.add(ghost);
        }
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void renderItems(String header) {
        // Same card size and spacing as the Keep Watching shelf.
        int cardW = dp(SceneHome.SHELF_CARD_W_DP), cardH = dp(SceneHome.SHELF_CARD_H_DP),
                gap = dp(SceneHome.SHELF_CARD_GAP_DP);
        // Room around the cards so the focus lift/scale isn't clipped by the scroller. Equal to
        // the shelf's header-to-cards gap, so header and cards land exactly where the shelf's do.
        int pad = dp(SceneHome.SHELF_HEADER_GAP_DP);
        rowInset = pad;

        addHeader(header, pad);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // The scroller clips the row to the row's own bounds, so the room must be inside the row.
        row.setPadding(pad, pad, pad, pad);

        String pkg = anchorPkg != null ? anchorPkg : "com.nuvio.tv";
        String appName = host.appLabel(pkg);
        for (int i = 0; i < items.size(); i++) {
            NuvioCloud.Item it = items.get(i);
            View card = home.watchCard(cardData(it, pkg, appName), cardW, cardH, (v, has) -> {
                if (!has) overlay.post(this::hideIfFocusLeft);
            });
            card.setId(View.generateViewId());
            card.setOnClickListener(v -> open(it));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) lp.leftMargin = gap;
            row.addView(card, lp);
            cards.add(card);

            ImageView thumb = card.findViewWithTag(SceneHome.WATCH_THUMB);
            if (thumb != null) {
                thumbViews.put(it.contentId, thumb);
                if (thumbs.get(it.contentId) == null) loadThumb(it);
            }
        }
        // Three cards fit across the screen like the shelf; more scroll sideways as you move.
        HorizontalScrollView scroll = new HorizontalScrollView(host);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        scroll.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int n = items.size();
        int contentW = n * cardW + (n - 1) * gap + 2 * pad;
        int maxW = host.screenW - 2 * dp(SceneHome.SHELF_LEFT_DP) + 2 * pad;
        panel.addView(scroll, new LinearLayout.LayoutParams(Math.min(contentW, maxW),
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private SceneHome.WatchCardData cardData(NuvioCloud.Item it, String pkg, String appName) {
        SceneHome.WatchCardData d = new SceneHome.WatchCardData();
        d.key = "nuvio:" + it.contentId;
        d.pkg = pkg;
        d.appName = appName;
        d.title = it.title == null || it.title.isEmpty()
                ? (it.isSeries() ? I18n.s(host, "Series", "Dizi") : I18n.s(host, "Movie", "Film")) : it.title;
        d.subtitle = subtitle(it);
        d.progress = (float) Math.max(0.0, Math.min(1.0, it.fraction()));
        d.thumb = thumbs.get(it.contentId);
        return d;
    }

    /** "S2 · E4 · 23 min left" for shows, "23 min left" for movies. */
    private String subtitle(NuvioCloud.Item it) {
        StringBuilder sb = new StringBuilder();
        if (it.isSeries() && it.season >= 0 && it.episode >= 0) {
            sb.append('S').append(it.season).append(" · E").append(it.episode);
        }
        long leftMin = Math.max(0, it.duration - it.position) / 60_000L;
        if (leftMin > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(leftMin).append(I18n.s(host, " min left", " dk kaldı"));
        }
        if (sb.length() == 0) sb.append(I18n.s(host, "Resume playback", "Oynatmaya devam et"));
        return sb.toString();
    }

    /** 16:9 art like the shelf: the title's backdrop when it has an IMDb id, else its poster. */
    private void loadThumb(NuvioCloud.Item it) {
        String base = it.baseId();
        final String backdrop = base.startsWith("tt")
                ? "https://images.metahub.space/background/medium/" + base + "/img" : null;
        final String poster = it.poster, id = it.contentId;
        exec.execute(() -> {
            Bitmap b = backdrop != null ? Suggestions.image(host, backdrop, "w300", 240) : null;
            if (b == null && poster != null && !poster.isEmpty()) b = Suggestions.image(host, poster, "w300", 240);
            if (b == null) return;
            final Bitmap bmp = b;
            host.handler.post(() -> {
                thumbs.put(id, bmp);
                ImageView v = thumbViews.get(id);
                if (v != null) v.setImageBitmap(bmp);
            });
        });
    }

    private void open(NuvioCloud.Item it) {
        String pkg = anchorPkg != null ? anchorPkg : "com.nuvio.tv";
        String type = it.isSeries() ? "series" : "movie";
        PackageManager pm = host.getPackageManager();
        // Same deep link the launcher's Suggestions already use for Nuvio.
        Intent i = Suggestions.view("nuvio://detail/" + type + "/" + Uri.encode(it.contentId, ":"), pkg);
        if (i.resolveActivity(pm) != null && Suggestions.start(host, i)) return;
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) Suggestions.start(host, launch);
    }

    /**
     * Puts the popup where the Keep Watching shelf sits, so it reads as the shelf switching to
     * Nuvio while the Nuvio tile is focused. The popup's bottom lines up with the bottom of the
     * shelf's cards: cards land exactly on the shelf's row, and anything taller (the sign-in QR,
     * profile buttons) grows upward instead of covering the dock.
     */
    private void position() {
        panel.setTranslationX(dp(SceneHome.SHELF_LEFT_DP) - rowInset);
        int any = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        panel.measure(any, any);
        int shelfCardsBottom = dp(SceneHome.SHELF_TOP_DP) + headerTextH
                + dp(SceneHome.SHELF_HEADER_GAP_DP) + dp(SceneHome.SHELF_CARD_H_DP);
        int contentBottom = panel.getMeasuredHeight() - rowInset;  // the card row has rowInset below it
        panel.setTranslationY(shelfCardsBottom - contentBottom);
        // Liquid glass samples the wallpaper at its on-screen spot, which may have moved.
        panel.post(this::invalidateGlass);
    }

    // ------------------------------------------------------------------ small helpers

    /**
     * Header in the same look as the shelf's "Keep watching" header, followed by the Nuvio
     * profile buttons when the account has more than one profile.
     */
    private void addHeader(String s, int indent) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        TextView h = label(s, 11.5f, 0xC7F4F4F6, "semibold");
        h.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        row.addView(h);
        if (showProfiles()) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(12);
            row.addView(profileBarView(), blp);
        } else {
            profileButtons.clear();
            profileBar = null;
            slider = null;
            profileBarSig = null;
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = indent + dp(2);
        lp.bottomMargin = indent > 0 ? 0 : dp(SceneHome.SHELF_HEADER_GAP_DP);
        panel.addView(row, lp);

        int any = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        row.measure(any, any);
        headerTextH = h.getMeasuredHeight();  // same text style as the shelf's header
    }

    /**
     * The profile bar for the current header. Reuses the existing bar when the profiles haven't
     * changed, so new data arriving mid-slide doesn't restart or snap the highlight.
     */
    private View profileBarView() {
        StringBuilder sig = new StringBuilder();
        for (NuvioCloud.Profile p : profiles) sig.append(p.index).append(':').append(p.name).append(';');
        if (profileBar != null && sig.toString().equals(profileBarSig)) {
            ViewGroup old = (ViewGroup) profileBar.getParent();
            if (old != null) old.removeView(profileBar);
            return profileBar;
        }
        profileButtons.clear();
        profileBarSig = sig.toString();
        return buildProfileBar();
    }

    /**
     * Segmented glass pill like the launcher's own profile switcher. One highlight slides behind
     * the names: solid white under the focused name, a soft highlight under the selected profile
     * when focus is elsewhere.
     */
    private View buildProfileBar() {
        FrameLayout bar = new FrameLayout(host);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        int pad = dp(3);
        bar.setPadding(pad, pad, pad, pad);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                dp(16), 0x6614161C, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(5);
        glass.rimStrength = 0.45f;
        bar.setBackground(glass);

        // Slider and name row share the bar's padding box, so button bounds are slider coordinates.
        slider = new Slider(host, dp(14), this::tintProfileNames);
        bar.addView(slider, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // First layout (and any re-layout): place the highlight without animating.
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> moveHighlight(false));

        for (int i = 0; i < profiles.size(); i++) {
            NuvioCloud.Profile p = profiles.get(i);
            TextView b = new TextView(host);
            Ui.textSize(b, 10.5f);
            b.setTypeface(Ui.font(host, "semibold"));
            b.setText(p.name == null || p.name.isEmpty()
                    ? I18n.s(host, "Profile ", "Profil ") + p.index : p.name);
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setMaxWidth(dp(130));
            b.setPadding(dp(11), dp(4), dp(11), dp(4));
            b.setTextColor(NAME_DIM);

            b.setId(View.generateViewId());
            b.setTag(p.index);
            b.setFocusable(true);
            b.setClickable(true);
            b.setOnFocusChangeListener((v, has) -> {
                // Wait until focus has settled on its new view, then slide there.
                v.post(() -> moveHighlight(true));
                if (has) {
                    // Switch to this profile once the highlight has landed (not while passing by).
                    host.handler.removeCallbacks(applyFocusedProfile);
                    host.handler.postDelayed(applyFocusedProfile, PROFILE_APPLY_DELAY_MS);
                } else {
                    overlay.post(this::hideIfFocusLeft);
                }
            });
            final int index = p.index;
            b.setOnClickListener(v -> selectProfile(index));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dp(2);
            row.addView(b, lp);
            profileButtons.add(b);
        }
        profileBar = bar;
        return bar;
    }

    private static final int NAME_DIM = 0xB8F4F4F6, NAME_SELECTED = 0xFFF4F4F6, NAME_ON_WHITE = 0xFF111114;
    private static final android.animation.ArgbEvaluator ARGB = new android.animation.ArgbEvaluator();

    /** Slides the highlight to the focused name, or back to the selected profile when focus left. */
    private void moveHighlight(boolean animate) {
        if (slider == null || profileButtons.isEmpty()) return;
        View f = host.getCurrentFocus();
        boolean focused = profileButtons.contains(f);
        View target = focused ? f : selectedProfileButton();
        if (target == null || target.getWidth() == 0) return;  // not laid out yet; layout calls back
        slider.moveTo(target.getLeft(), target.getTop(), target.getRight(), target.getBottom(),
                focused ? 1f : 0f, animate);
    }

    /**
     * Name colours follow the highlight as it moves: a name turns dark exactly as much as the
     * white highlight covers it, so text never flips colour before the slider arrives.
     */
    private void tintProfileNames() {
        int selected = cloud.profile();
        for (View v : profileButtons) {
            int base = ((Integer) v.getTag()) == selected ? NAME_SELECTED : NAME_DIM;
            float overlap = Math.max(0f, Math.min(slider.right, v.getRight()) - Math.max(slider.left, v.getLeft()));
            float cover = v.getWidth() > 0 ? Math.min(1f, overlap / v.getWidth()) : 0f;
            ((TextView) v).setTextColor((int) ARGB.evaluate(cover * slider.white, base, NAME_ON_WHITE));
        }
    }

    /** The highlight pill behind the profile names; position, size and whiteness animate together. */
    private static final class Slider extends View {
        private static final int SOFT = 0x40FFFFFF, WHITE = 0xF2FFFFFF;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private final float radius;
        private final Runnable onFrame;
        private android.animation.ValueAnimator anim;
        float left = -1, top, right, bottom, white;
        private float toL, toT, toR, toB, toWhite;

        Slider(android.content.Context c, float radius, Runnable onFrame) {
            super(c);
            this.radius = radius;
            this.onFrame = onFrame;
        }

        void moveTo(float l, float t, float r, float b, float w, boolean animate) {
            if (left >= 0 && l == toL && t == toT && r == toR && b == toB && w == toWhite) {
                return;  // already there or on the way
            }
            toL = l; toT = t; toR = r; toB = b; toWhite = w;
            if (anim != null) anim.cancel();
            if (!animate || left < 0) {
                set(l, t, r, b, w);
                return;
            }
            final float l0 = left, t0 = top, r0 = right, b0 = bottom, w0 = white;
            // The leading edge runs ahead and the trailing edge catches up, so the pill
            // stretches toward the new name and settles there instead of jumping.
            final boolean toRight = l > l0;
            anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(320);
            anim.setInterpolator(new android.view.animation.LinearInterpolator());
            anim.addUpdateListener(a -> {
                float p = (float) a.getAnimatedValue();
                float lead = ease(Math.min(1f, p / 0.75f));
                float trail = ease(Math.max(0f, (p - 0.2f) / 0.8f));
                float pl = toRight ? trail : lead, pr = toRight ? lead : trail;
                set(l0 + (l - l0) * pl, t0 + (t - t0) * p, r0 + (r - r0) * pr, b0 + (b - b0) * p,
                        w0 + (w - w0) * ease(p));
            });
            anim.start();
        }

        /**
         * Takes no space of its own: the bar sizes itself to the names, then stretches this view
         * over the same box. (A plain View would grab all the width it is offered.)
         */
        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(
                    MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : 0,
                    MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : 0);
        }

        private static float ease(float x) {  // cubic ease-out
            float i = 1f - x;
            return 1f - i * i * i;
        }

        private void set(float l, float t, float r, float b, float w) {
            left = l; top = t; right = r; bottom = b; white = w;
            invalidate();
            onFrame.run();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (left < 0) return;
            paint.setColor((int) ARGB.evaluate(white, SOFT, WHITE));
            box.set(left, top, right, bottom);
            c.drawRoundRect(box, radius, radius, paint);
        }
    }

    /** A liquid-glass box matching the watch cards, for the sign-in and status messages. */
    private LinearLayout glassBox() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(14), dp(10));
        float r = dp(15);
        MainActivity.LiquidGlass glass = new MainActivity.LiquidGlass(
                r, 0x5C1E141C, host.glassSource, host.screenW, host.screenH, host.unit);
        glass.lens = dp(6);
        glass.rimStrength = 0.42f;
        box.setBackground(glass);
        box.setElevation(dp(5));
        return box;
    }

    private void addStatusBox(LinearLayout box) {
        statusBox = box;
        panel.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView label(String s, float size, int color, String weight) {
        TextView t = new TextView(host);
        t.setText(s);
        Ui.textSize(t, size);
        t.setTypeface(Ui.font(host, weight));
        t.setTextColor(color);
        t.setMaxWidth(dp(320));
        return t;
    }

    private int dp(float v) {
        return host.dp(v);
    }

    private static Bitmap qrBitmap(String text, int size) {
        QrCode code = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int n = code.size;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        float cell = size / (float) n;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (code.getModule(x, y)) {
                    c.drawRect(x * cell, y * cell, (x + 1) * cell + 0.5f, (y + 1) * cell + 0.5f, p);
                }
            }
        }
        return b;
    }
}
