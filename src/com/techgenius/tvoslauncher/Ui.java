package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Resolution-independent sizing. The launcher is designed on a 960x540 grid (tvOS points / 2);
 * every size scales with the connected display's actual resolution, not its declared density,
 * so 720p, 1080p and 4K outputs (and any density setting) get the same proportions.
 */
public final class Ui {
    private Ui() { }

    /** Display size in pixels for the window this context is shown on. */
    static Point screen(Context c) {
        Point p = new Point();
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display d = Build.VERSION.SDK_INT >= 30 && c instanceof Activity ? ((Activity) c).getDisplay() : wm.getDefaultDisplay();
        if (d == null) d = wm.getDefaultDisplay();
        d.getRealSize(p);
        if (p.x < p.y) p.set(p.y, p.x);  // TVs are landscape; guard against a transient portrait report
        return p;
    }

    /** Pixels per design unit (1 unit = 1/960 of the screen width, capped by height for odd aspect ratios). */
    static float unit(Context c) {
        Point p = screen(c);
        return Math.min(p.x / 960f, p.y / 540f);
    }

    public static int px(Context c, float units) {
        return Math.round(units * unit(c));
    }

    private static final java.util.Map<String, android.graphics.Typeface> FONTS = new java.util.HashMap<>();

    /**
     * Inter (OFL, bundled): the closest open font to Apple's SF. "display" is Inter Display Bold for
     * large titles; "regular", "medium", "semibold" for UI and body text.
     */
    public static android.graphics.Typeface font(Context c, String weight) {
        android.graphics.Typeface t = FONTS.get(weight);
        if (t == null) {
            String file = weight.equals("clock") ? "Antonio-Light"
                    : weight.equals("clock_duo") ? "Bahnschrift"
                    : weight.equals("clock_rounded") ? "VarelaRound-Regular"
                    : weight.equals("display") ? "InterDisplay-Bold" : weight.equals("medium") ? "Inter-Medium"
                    : weight.equals("semibold") ? "Inter-SemiBold" : "Inter-Regular";
            try {
                t = android.graphics.Typeface.createFromAsset(c.getAssets(), "fonts/" + file + ".ttf");
            } catch (RuntimeException e) {
                try {
                    t = android.graphics.Typeface.createFromAsset(c.getAssets(), "fonts\\" + file + ".ttf");
                } catch (RuntimeException e2) {
                    t = android.graphics.Typeface.DEFAULT;
                }
            }
            FONTS.put(weight, t);
        }
        return t;
    }

    /**
     * Home-screen typeface: the Google Font family the user picked in Settings (Look &amp; Scenes),
     * in the requested weight. Families are OFL fonts bundled in assets/fonts/ (see
     * Scenes.HOME_FONT_FAMILIES) - no download, no network, no new library, so the home screen
     * keeps working offline exactly like the bundled Inter does. "inter" (the default) just
     * reuses {@link #font}, so every existing weight file stays in play. Poppins and Barlow ship
     * one static file per weight; Nunito and Space Grotesk ship a single variable
     * "[wght]" file, whose weight is selected with Typeface.create on API 28+ and falls back to
     * the regular instance on older TVs. A missing file falls back to Inter rather than crashing.
     */
    public static android.graphics.Typeface homeFont(Context c, String weight) {
        String family = Scenes.getHomeFontFamily(c);
        if (Scenes.HOME_FONT_INTER.equals(family)) return font(c, weight);
        String key = "home:" + family + ":" + weight;
        android.graphics.Typeface t = FONTS.get(key);
        if (t != null) return t;
        String file = homeFontFile(family, weight);
        if (file == null) {
            t = font(c, weight);
            FONTS.put(key, t);
            return t;
        }
        try {
            android.graphics.Typeface base =
                    android.graphics.Typeface.createFromAsset(c.getAssets(), "fonts/" + file);
            if (file.contains("[wght]") && android.os.Build.VERSION.SDK_INT >= 28) {
                android.graphics.Typeface weighted =
                        android.graphics.Typeface.create(base, homeFontWeight(weight), false);
                t = weighted != null ? weighted : base;
            } else {
                t = base;
            }
        } catch (RuntimeException e) {
            t = font(c, weight);
        }
        FONTS.put(key, t);
        return t;
    }

    private static String homeFontFile(String family, String weight) {
        if ("poppins".equals(family) || "barlow".equals(family)) {
            String cap = "poppins".equals(family) ? "Poppins" : "Barlow";
            String w = weight.equals("display") ? "Bold"
                    : weight.equals("medium") ? "Medium"
                    : weight.equals("semibold") ? "SemiBold" : "Regular";
            return cap + "-" + w + ".ttf";
        }
        if ("nunito".equals(family)) return "Nunito[wght].ttf";
        if ("spacegrotesk".equals(family)) return "SpaceGrotesk[wght].ttf";
        return null;
    }

    private static int homeFontWeight(String weight) {
        return weight.equals("display") ? 700
                : weight.equals("medium") ? 500
                : weight.equals("semibold") ? 600 : 400;
    }

    /**
     * Home-screen text colour: the colour preset the user picked in Settings, with the chosen
     * opacity folded into its alpha channel, so it composes correctly with a view's own fade
     * alpha (the two alphas multiply). extraAlpha scales it further for secondary text
     * (dock subtitles, shelf headers) that used to hard-code a dimmer white.
     */
    public static int homeTextColor(Context c) {
        return homeTextColor(c, 1f);
    }

    public static int homeTextColor(Context c, float extraAlpha) {
        int base = Scenes.getHomeFontColor(c);
        float opacity = Scenes.getHomeFontOpacity(c) / 100f * extraAlpha;
        int a = Math.round(255 * Math.max(0f, Math.min(1f, opacity)));
        return android.graphics.Color.argb(a,
                android.graphics.Color.red(base),
                android.graphics.Color.green(base),
                android.graphics.Color.blue(base));
    }

    /**
     * Small, heavily blurred copy of an image (box blur, several passes). Used as the backdrop
     * that Liquid Glass surfaces sample; at this size it costs a few milliseconds.
     */
    static android.graphics.Bitmap blur(android.graphics.Bitmap src, int w, int h, int radius, int passes) {
        if (src == null || src.isRecycled()) return null;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawBitmap(src, null, new android.graphics.Rect(0, 0, w, h),
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        blurInPlace(b, w, h, radius, passes);
        return b;
    }

    /** Liquid Glass backdrop: blurred image with boosted saturation and a slight lift, baked in a single bitmap. */
    static android.graphics.Bitmap glassBackdrop(android.graphics.Bitmap src, int w, int h) {
        if (src == null || src.isRecycled()) return null;
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setSaturation(1.6f);
        cm.postConcat(new android.graphics.ColorMatrix(new float[]{
                1.05f, 0, 0, 0, 14, 0, 1.05f, 0, 0, 14, 0, 0, 1.05f, 0, 14, 0, 0, 0, 1, 0}));
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        new android.graphics.Canvas(out).drawBitmap(src, null, new android.graphics.Rect(0, 0, w, h), p);
        blurInPlace(out, w, h, 3, 3);
        return out;
    }

    private static void blurInPlace(android.graphics.Bitmap b, int w, int h, int radius, int passes) {
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            boxBlur(px, tmp, w, h, radius, true);
            boxBlur(tmp, px, w, h, radius, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void boxBlur(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int l = 0; l < lines; l++) {
            int ra = 0, ga = 0, ba = 0, n = 0;
            for (int i = -r; i <= r; i++) {
                int k = Math.max(0, Math.min(len - 1, i));
                int c = in[horizontal ? l * w + k : k * w + l];
                ra += (c >> 16) & 0xFF; ga += (c >> 8) & 0xFF; ba += c & 0xFF; n++;
            }
            for (int i = 0; i < len; i++) {
                out[horizontal ? l * w + i : i * w + l] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
                int outI = Math.max(0, Math.min(len - 1, i - r)), inI = Math.max(0, Math.min(len - 1, i + r + 1));
                int co = in[horizontal ? l * w + outI : outI * w + l], ci = in[horizontal ? l * w + inI : inI * w + l];
                ra += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                ga += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                ba += (ci & 0xFF) - (co & 0xFF);
            }
        }
    }

    /** Text sized in design units so type scales with the display like everything else. */
    public static void textSize(TextView t, float units) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * unit(t.getContext()));
    }

    /**
     * Configures a dialog EditText for Android TV:
     * 1. Prevents the background window or dialog from shifting/panning when the keyboard opens.
     * 2. Closes the on-screen keyboard when the user clicks Done / OK / Enter on the keyboard,
     *    moving focus to the dialog's positive action button instead of requiring Back.
     */
    public static void setupDialogEditText(final android.widget.EditText in, final android.app.AlertDialog dlg) {
        if (in == null) return;
        Context ctx = in.getContext();
        if (ctx instanceof Activity) {
            android.view.Window aw = ((Activity) ctx).getWindow();
            if (aw != null) {
                aw.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            }
        }
        if (dlg != null && dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        in.setSingleLine(true);
        in.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        final Runnable closeImeAndFocusButton = () -> {
            try {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) in.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(in.getWindowToken(), 0);
                    if (in.getApplicationWindowToken() != null) {
                        imm.hideSoftInputFromWindow(in.getApplicationWindowToken(), 0);
                    }
                }
            } catch (Throwable ignored) {
            }
            if (dlg != null) {
                android.widget.Button pos = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
                if (pos != null && pos.getVisibility() == android.view.View.VISIBLE) {
                    pos.setFocusable(true);
                    pos.setFocusableInTouchMode(true);
                    pos.requestFocus();
                }
            }
        };

        in.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                    || actionId == android.view.inputmethod.EditorInfo.IME_NULL
                    || (event != null && (event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER))) {
                closeImeAndFocusButton.run();
                return true;
            }
            return false;
        });

        in.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    closeImeAndFocusButton.run();
                }
                return true;
            }
            return false;
        });
    }
}
