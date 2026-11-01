package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles custom app icons (.jpg, .jpeg, .png, .ico), custom wallpapers for Night Scenes & Classic mode,
 * pure-Java .ico decoding, and a remote-friendly TV file selector dialog.
 */
public final class CustomMedia {
    public static final int REQ_SYSTEM_FILE_PICKER = 9401;

    private static final String PREF_NAME = "custom_media";
    private static final String KEY_SCENE_WALL_TS = "scene_wall_ts";
    private static final String KEY_CLASSIC_WALL_TS = "classic_wall_ts";

    private static final Map<String, Bitmap> ICON_CACHE = new HashMap<>();
    private static final Bitmap[] cachedSceneWalls = new Bitmap[6];
    private static final long[] cachedSceneWallTs = {-1L, -1L, -1L, -1L, -1L, -1L};
    private static Bitmap cachedClassicWall;
    private static long cachedClassicWallTs = -1L;

    private static PendingSystemPick pendingSystemPick;

    public interface ImagePickedCallback {
        void onImagePicked(Bitmap bitmap);
    }

    public interface FolderPickedCallback {
        void onFolderPicked(File folder, int imageCount);
    }

    public static final String SCOPE_SCENE = "scene";
    public static final String SCOPE_CLASSIC = "classic";
    public static final String SCOPE_LOCK = "lock";

    private static Bitmap cachedLockWall;
    private static long cachedLockWallTs = -1L;

    private static final Map<String, List<File>> folderDeckMap = new HashMap<>();
    private static final Map<String, Integer> folderIndexMap = new HashMap<>();
    private static final Map<String, String> folderPathCacheMap = new HashMap<>();
    private static final Map<String, Bitmap> folderCurrentBmpMap = new HashMap<>();
    private static final Map<String, String> folderCurrentFileMap = new HashMap<>();

    private static final class PendingSystemPick {
        final Activity activity;
        final boolean allowIco;
        final int maxW;
        final int maxH;
        final ImagePickedCallback callback;

        PendingSystemPick(Activity activity, boolean allowIco, int maxW, int maxH, ImagePickedCallback callback) {
            this.activity = activity;
            this.allowIco = allowIco;
            this.maxW = maxW;
            this.maxH = maxH;
            this.callback = callback;
        }
    }

    private CustomMedia() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- Custom App Icons

    private static String safeKey(String key, String pkg) {
        String raw = (pkg != null && !pkg.isEmpty()) ? pkg : (key != null ? key : "unknown");
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static File iconFile(Context c, String key, String pkg) {
        File dir = new File(c.getFilesDir(), "custom_icons");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, safeKey(key, pkg) + ".png");
    }

    public static boolean hasCustomAppIcon(Context c, String key, String pkg) {
        if (c == null) return false;
        File f = iconFile(c, key, pkg);
        return f.exists() && f.length() > 0;
    }

    public static Bitmap getCustomAppIcon(Context c, String key, String pkg) {
        if (c == null) return null;
        String sk = safeKey(key, pkg);
        synchronized (ICON_CACHE) {
            Bitmap cached = ICON_CACHE.get(sk);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }
        File f = iconFile(c, key, pkg);
        if (!f.exists() || f.length() == 0) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) {
                synchronized (ICON_CACHE) {
                    ICON_CACHE.put(sk, b);
                }
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setCustomAppIcon(Context c, String key, String pkg, Bitmap raw) {
        if (c == null || raw == null) return false;
        String sk = safeKey(key, pkg);
        Bitmap scaled = (raw.getWidth() == 512 && raw.getHeight() == 512 && ((raw.getPixel(0, 0) >>> 24) == 255))
                ? raw
                : prepareFullBleedAppIcon(raw, 512, 512);
        File f = iconFile(c, key, pkg);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            synchronized (ICON_CACHE) {
                ICON_CACHE.put(sk, scaled);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Renders an edited app icon with the user's chosen background color, zoom, and X/Y pan offsets.
     */
    public static Bitmap renderEditedAppIcon(Bitmap trimmed, int targetW, int targetH,
                                             int bgColor, float baseFillScale, float zoom,
                                             float offsetX, float offsetY) {
        int sw = Math.max(1, trimmed.getWidth());
        int sh = Math.max(1, trimmed.getHeight());
        Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        cv.drawColor(bgColor | 0xFF000000);

        float scale = baseFillScale * zoom;
        float dw = sw * scale;
        float dh = sh * scale;
        float dx = (targetW - dw) * 0.5f + (offsetX * targetW);
        float dy = (targetH - dh) * 0.5f + (offsetY * targetH);
        cv.drawBitmap(trimmed, null, new RectF(dx, dy, dx + dw, dy + dh), p);
        return out;
    }

    /**
     * Interactive App Icon Editor shown after picking an image file:
     * - Live rounded-square preview icon updated on every change
     * - Background color picker (Auto + 13 colors)
     * - Zoom In / Zoom Out / Reset buttons
     * - Move Left / Right / Up / Down buttons
     * - Apply button to save and update the selected app icon
     */
    public static void showAppIconEditor(Activity activity, String appLabel, Bitmap rawBmp,
                                         int appFallbackColor, ImagePickedCallback onApply) {
        if (activity == null || rawBmp == null || rawBmp.isRecycled()) return;

        Bitmap trimmed = trimTransparentPadding(rawBmp);
        int sw = Math.max(1, trimmed.getWidth());
        int sh = Math.max(1, trimmed.getHeight());

        int cornerAlpha = Math.min(
                Math.min((trimmed.getPixel(0, 0) >>> 24), (trimmed.getPixel(sw - 1, 0) >>> 24)),
                Math.min((trimmed.getPixel(0, sh - 1) >>> 24), (trimmed.getPixel(sw - 1, sh - 1) >>> 24)));
        int midEdgeAlpha = Math.min(
                Math.min((trimmed.getPixel(sw / 2, 0) >>> 24), (trimmed.getPixel(sw / 2, sh - 1) >>> 24)),
                Math.min((trimmed.getPixel(0, sh / 2) >>> 24), (trimmed.getPixel(sw - 1, sh / 2) >>> 24)));

        int sampledEdge = sampleDominantEdgeColor(trimmed);
        int autoColor = sampledEdge != 0
                ? sampledEdge
                : (appFallbackColor != 0 ? (0xFF000000 | appFallbackColor) : 0xFF141519);

        final int[] bgColors = {
            autoColor,
            0xFF141519, // Dark
            0xFF000000, // Black
            0xFFFFFFFF, // White
            0xFF3A3A3C, // Gray
            0xFFFF3B30, // Red
            0xFFFF9500, // Orange
            0xFFFFCC00, // Yellow
            0xFF34C759, // Green
            0xFF00C7BE, // Teal
            0xFF007AFF, // Blue
            0xFF5856D6, // Indigo
            0xFFAF52DE, // Purple
            0xFFFF2D55  // Pink
        };
        final String[] bgNames = {
            I18n.s(activity, "Auto", "Otomatik"),
            I18n.s(activity, "Dark", "Koyu"),
            I18n.s(activity, "Black", "Siyah"),
            I18n.s(activity, "White", "Beyaz"),
            I18n.s(activity, "Gray", "Gri"),
            I18n.s(activity, "Red", "Kırmızı"),
            I18n.s(activity, "Orange", "Turuncu"),
            I18n.s(activity, "Yellow", "Sarı"),
            I18n.s(activity, "Green", "Yeşil"),
            I18n.s(activity, "Teal", "Turkuaz"),
            I18n.s(activity, "Blue", "Mavi"),
            I18n.s(activity, "Indigo", "Çivit"),
            I18n.s(activity, "Purple", "Mor"),
            I18n.s(activity, "Pink", "Pembe")
        };

        final float bleedBoost = (cornerAlpha < 180 && midEdgeAlpha >= 200) ? 1.06f : 1.0f;
        final int[] bgIdx = {0};
        final float[] zoom = {1.0f};
        final float[] offX = {0.0f};
        final float[] offY = {0.0f};

        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        float u = Math.min(screenW / 960f, screenH / 540f);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(22 * u);
        int padV = Math.round(18 * u);
        root.setPadding(padH, padV, padH, padV);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(20 * u);
        cardBg.setColor(0xFF1C1C22);
        cardBg.setStroke(Math.max(1, Math.round(1.2f * u)), 0x26FFFFFF);
        root.setBackground(cardBg);

        // Title
        TextView titleView = new TextView(activity);
        titleView.setText(I18n.s(activity, "App Icon Editor", "Uygulama Simgesi Düzenleyici")
                + (appLabel != null && !appLabel.isEmpty() ? " — " + appLabel : ""));
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Ui.font(activity, "semibold"));
        Ui.textSize(titleView, 16f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Math.round(14 * u);
        root.addView(titleView, tlp);

        // 2-Column Body: Left = Live Preview Icon, Right = Controls
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        // Left Column: Preview Box
        LinearLayout leftCol = new LinearLayout(activity);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                Math.round(154 * u), ViewGroup.LayoutParams.WRAP_CONTENT);
        leftLp.rightMargin = Math.round(20 * u);

        int prevSize = Math.round(112 * u);
        final float prevRad = 24f * u;
        FrameLayout previewFrame = new FrameLayout(activity);
        int framePad = Math.round(4 * u);
        previewFrame.setPadding(framePad, framePad, framePad, framePad);
        GradientDrawable frameBg = new GradientDrawable();
        frameBg.setCornerRadius(prevRad + framePad);
        frameBg.setColor(0x18FFFFFF);
        frameBg.setStroke(Math.max(1, Math.round(1.5f * u)), 0x44FFFFFF);
        previewFrame.setBackground(frameBg);

        ImageView previewIv = new ImageView(activity);
        previewIv.setScaleType(ImageView.ScaleType.FIT_XY);
        previewIv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), prevRad);
            }
        });
        previewIv.setClipToOutline(true);
        previewFrame.addView(previewIv, new FrameLayout.LayoutParams(prevSize, prevSize));
        leftCol.addView(previewFrame, new LinearLayout.LayoutParams(
                prevSize + framePad * 2, prevSize + framePad * 2));

        TextView previewCap = new TextView(activity);
        previewCap.setText(I18n.s(activity, "Preview", "Önizleme"));
        previewCap.setTextColor(0xCCFFFFFF);
        previewCap.setTypeface(Ui.font(activity, "medium"));
        Ui.textSize(previewCap, 11.5f);
        previewCap.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = Math.round(8 * u);
        leftCol.addView(previewCap, capLp);

        TextView statusText = new TextView(activity);
        statusText.setTextColor(0x99FFFFFF);
        statusText.setTypeface(Ui.font(activity, "regular"));
        Ui.textSize(statusText, 10.5f);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = Math.round(2 * u);
        leftCol.addView(statusText, stLp);

        body.addView(leftCol, leftLp);

        // Right Column: Controls
        LinearLayout rightCol = new LinearLayout(activity);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        // Swatch dot & Color label in Background Color row
        View colorDot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setStroke(Math.max(1, Math.round(1.2f * u)), 0x88FFFFFF);
        colorDot.setBackground(dotBg);
        TextView colorValueTv = new TextView(activity);
        colorValueTv.setTextColor(Color.WHITE);
        colorValueTv.setTypeface(Ui.font(activity, "semibold"));
        Ui.textSize(colorValueTv, 12.5f);

        Runnable updatePreview = () -> {
            int previewPx = 256;
            float baseScale256 = Math.max(previewPx / (float) sw, previewPx / (float) sh) * bleedBoost;
            Bitmap bmp = renderEditedAppIcon(
                    trimmed, previewPx, previewPx,
                    bgColors[bgIdx[0]], baseScale256, zoom[0], offX[0], offY[0]);
            previewIv.setImageBitmap(bmp);
            dotBg.setColor(bgColors[bgIdx[0]] | 0xFF000000);
            colorValueTv.setText(bgNames[bgIdx[0]]);
            int zPct = Math.round(zoom[0] * 100f);
            int xPct = Math.round(offX[0] * 100f);
            int yPct = Math.round(offY[0] * 100f);
            statusText.setText(zPct + "%  ·  X:" + (xPct >= 0 ? "+" + xPct : xPct)
                    + "  Y:" + (yPct >= 0 ? "+" + yPct : yPct));
        };

        // 1. Background Color Row
        LinearLayout bgRow = createEditorControlRow(activity, rightCol, u,
                I18n.s(activity, "Background color", "Arka plan rengi"));
        TextView prevColorBtn = createEditorButton(activity, u, "◀", () -> {
            bgIdx[0] = (bgIdx[0] - 1 + bgColors.length) % bgColors.length;
            updatePreview.run();
        });
        LinearLayout colorBadge = new LinearLayout(activity);
        colorBadge.setOrientation(LinearLayout.HORIZONTAL);
        colorBadge.setGravity(Gravity.CENTER_VERTICAL);
        colorBadge.setPadding(Math.round(10 * u), Math.round(5 * u), Math.round(10 * u), Math.round(5 * u));
        int dotSz = Math.round(14 * u);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dotSz, dotSz);
        dlp.rightMargin = Math.round(7 * u);
        colorBadge.addView(colorDot, dlp);
        colorBadge.addView(colorValueTv);
        TextView nextColorBtn = createEditorButton(activity, u, "▶", () -> {
            bgIdx[0] = (bgIdx[0] + 1) % bgColors.length;
            updatePreview.run();
        });
        bgRow.addView(prevColorBtn);
        bgRow.addView(colorBadge);
        bgRow.addView(nextColorBtn);

        // 2. Zoom Row
        LinearLayout zoomRow = createEditorControlRow(activity, rightCol, u,
                I18n.s(activity, "Zoom", "Yakınlaştırma"));
        TextView zoomOutBtn = createEditorButton(activity, u,
                "－ " + I18n.s(activity, "Zoom out", "Uzaklaştır"), () -> {
                    zoom[0] = Math.max(0.25f, Math.round((zoom[0] - 0.08f) * 100f) / 100f);
                    updatePreview.run();
                });
        TextView zoomInBtn = createEditorButton(activity, u,
                "＋ " + I18n.s(activity, "Zoom in", "Yakınlaştır"), () -> {
                    zoom[0] = Math.min(3.00f, Math.round((zoom[0] + 0.08f) * 100f) / 100f);
                    updatePreview.run();
                });
        TextView resetBtn = createEditorButton(activity, u,
                "↺ " + I18n.s(activity, "Reset", "Sıfırla"), () -> {
                    zoom[0] = 1.0f;
                    offX[0] = 0.0f;
                    offY[0] = 0.0f;
                    bgIdx[0] = 0;
                    updatePreview.run();
                });
        zoomRow.addView(zoomOutBtn);
        zoomRow.addView(zoomInBtn);
        zoomRow.addView(resetBtn);

        // 3. Move (Left / Right / Up / Down) Row
        LinearLayout moveRow = createEditorControlRow(activity, rightCol, u,
                I18n.s(activity, "Move icon", "Simgeyi taşı"));
        TextView moveLeftBtn = createEditorButton(activity, u,
                "← " + I18n.s(activity, "Left", "Sol"), () -> {
                    offX[0] = Math.max(-0.80f, Math.round((offX[0] - 0.04f) * 100f) / 100f);
                    updatePreview.run();
                });
        TextView moveRightBtn = createEditorButton(activity, u,
                "→ " + I18n.s(activity, "Right", "Sağ"), () -> {
                    offX[0] = Math.min(0.80f, Math.round((offX[0] + 0.04f) * 100f) / 100f);
                    updatePreview.run();
                });
        TextView moveUpBtn = createEditorButton(activity, u,
                "↑ " + I18n.s(activity, "Up", "Yukarı"), () -> {
                    offY[0] = Math.max(-0.80f, Math.round((offY[0] - 0.04f) * 100f) / 100f);
                    updatePreview.run();
                });
        TextView moveDownBtn = createEditorButton(activity, u,
                "↓ " + I18n.s(activity, "Down", "Aşağı"), () -> {
                    offY[0] = Math.min(0.80f, Math.round((offY[0] + 0.04f) * 100f) / 100f);
                    updatePreview.run();
                });
        moveRow.addView(moveLeftBtn);
        moveRow.addView(moveRightBtn);
        moveRow.addView(moveUpBtn);
        moveRow.addView(moveDownBtn);

        body.addView(rightCol, rightLp);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Bottom Action Buttons: Cancel & Apply
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.topMargin = Math.round(16 * u);

        final AlertDialog dlg = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(root)
                .create();

        TextView cancelBtn = createEditorButton(activity, u,
                I18n.s(activity, "Cancel", "İptal"), dlg::dismiss);
        TextView applyBtn = createEditorButton(activity, u,
                "✓  " + I18n.s(activity, "Apply", "Uygula"), () -> {
                    int outPx = 512;
                    float baseScale512 = Math.max(outPx / (float) sw, outPx / (float) sh) * bleedBoost;
                    Bitmap finalBmp = renderEditedAppIcon(
                            trimmed, outPx, outPx,
                            bgColors[bgIdx[0]], baseScale512, zoom[0], offX[0], offY[0]);
                    dlg.dismiss();
                    if (onApply != null) {
                        onApply.onImagePicked(finalBmp);
                    }
                });
        actionRow.addView(cancelBtn);
        actionRow.addView(applyBtn);
        root.addView(actionRow, actLp);

        updatePreview.run();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlg.getWindow().setLayout(Math.round(580 * u), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        applyBtn.requestFocus();
    }

    private static LinearLayout createEditorControlRow(Activity activity, LinearLayout parent, float u, String label) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Math.round(10 * u);

        TextView lbl = new TextView(activity);
        lbl.setText(label);
        lbl.setTextColor(0x99FFFFFF);
        lbl.setTypeface(Ui.font(activity, "medium"));
        Ui.textSize(lbl, 11f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.bottomMargin = Math.round(4 * u);
        container.addView(lbl, llp);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(container, clp);
        return buttons;
    }

    private static TextView createEditorButton(Activity activity, float u, String text, Runnable onClick) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(0xEBF4F4F6);
        btn.setTypeface(Ui.font(activity, "semibold"));
        Ui.textSize(btn, 12f);
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        int ph = Math.round(11 * u);
        int pv = Math.round(6 * u);
        btn.setPadding(ph, pv, ph, pv);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * u);
        bg.setColor(0x22FFFFFF);
        bg.setStroke(Math.max(1, Math.round(1f * u)), 0x28FFFFFF);
        btn.setBackground(bg);

        btn.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x22FFFFFF);
            btn.setTextColor(has ? 0xFF111114 : 0xEBF4F4F6);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(140).start();
        });
        btn.setOnClickListener(v -> onClick.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Math.round(6 * u);
        btn.setLayoutParams(lp);
        return btn;
    }

    /**
     * Trims transparent padding/drop-shadow margins around PNG/ICO app icons and scales the icon
     * to completely fill the target dimensions so custom PNG icons match the full app icon size.
     */
    public static Bitmap prepareFullBleedAppIcon(Bitmap raw, int targetW, int targetH) {
        if (raw == null || raw.isRecycled()) return raw;
        Bitmap trimmed = trimTransparentPadding(raw);
        int sw = Math.max(1, trimmed.getWidth());
        int sh = Math.max(1, trimmed.getHeight());

        Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Check if trimmed icon is a rounded-square/squircle with transparent corners
        int cornerAlpha = Math.min(
                Math.min((trimmed.getPixel(0, 0) >>> 24), (trimmed.getPixel(sw - 1, 0) >>> 24)),
                Math.min((trimmed.getPixel(0, sh - 1) >>> 24), (trimmed.getPixel(sw - 1, sh - 1) >>> 24)));
        int midEdgeAlpha = Math.min(
                Math.min((trimmed.getPixel(sw / 2, 0) >>> 24), (trimmed.getPixel(sw / 2, sh - 1) >>> 24)),
                Math.min((trimmed.getPixel(0, sh / 2) >>> 24), (trimmed.getPixel(sw - 1, sh / 2) >>> 24)));

        // Sample dominant edge color to fill behind any transparent pixels
        int bgColor = sampleDominantEdgeColor(trimmed);
        if (bgColor != 0) {
            cv.drawColor(bgColor);
        }

        // Scale to fill targetW x targetH completely; if the PNG has rounded transparent corners
        // while mid-edges are opaque, add a slight zoom (1.06x) so rounded corners bleed cleanly
        float baseScale = Math.max(targetW / (float) sw, targetH / (float) sh);
        if (cornerAlpha < 180 && midEdgeAlpha >= 200) {
            baseScale *= 1.06f;
        }
        float dw = sw * baseScale;
        float dh = sh * baseScale;
        float dx = (targetW - dw) * 0.5f;
        float dy = (targetH - dh) * 0.5f;
        cv.drawBitmap(trimmed, null, new RectF(dx, dy, dx + dw, dy + dh), p);
        return out;
    }

    /**
     * Crops away transparent outer padding (and soft drop-shadow fringes on PNG icons)
     * so the actual icon artwork fills the bounding box edge-to-edge.
     */
    public static Bitmap trimTransparentPadding(Bitmap src) {
        if (src == null || src.isRecycled()) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 4 || h <= 4) return src;
        try {
            int[] pixels = new int[w * h];
            src.getPixels(pixels, 0, w, 0, 0, w, h);

            // First pass: trim soft drop shadows too (alpha >= 200)
            int[] box = findAlphaBounds(pixels, w, h, 200);
            if (box == null) {
                // Fallback pass for semi-transparent icons (alpha >= 32)
                box = findAlphaBounds(pixels, w, h, 32);
            }
            if (box == null) return src;
            int bw = box[2] - box[0] + 1;
            int bh = box[3] - box[1] + 1;
            if (bw <= 0 || bh <= 0 || (bw == w && bh == h)) return src;
            return Bitmap.createBitmap(src, box[0], box[1], bw, bh);
        } catch (Throwable t) {
            return src;
        }
    }

    private static int[] findAlphaBounds(int[] pixels, int w, int h, int minAlpha) {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            int rowOff = y * w;
            for (int x = 0; x < w; x++) {
                int a = (pixels[rowOff + x] >>> 24) & 0xFF;
                if (a >= minAlpha) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return null;
        return new int[]{minX, minY, maxX, maxY};
    }

    private static int sampleDominantEdgeColor(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= 0 || h <= 0) return 0;
        long rSum = 0, gSum = 0, bSum = 0, count = 0;
        int insetX = Math.max(1, Math.round(w * 0.08f));
        int insetY = Math.max(1, Math.round(h * 0.08f));
        int stepX = Math.max(1, w / 16);
        int stepY = Math.max(1, h / 16);
        for (int x = insetX; x < w - insetX; x += stepX) {
            int pTop = bmp.getPixel(x, insetY);
            if (((pTop >>> 24) & 0xFF) >= 200) {
                rSum += (pTop >> 16) & 0xFF;
                gSum += (pTop >> 8) & 0xFF;
                bSum += pTop & 0xFF;
                count++;
            }
            int pBot = bmp.getPixel(x, h - 1 - insetY);
            if (((pBot >>> 24) & 0xFF) >= 200) {
                rSum += (pBot >> 16) & 0xFF;
                gSum += (pBot >> 8) & 0xFF;
                bSum += pBot & 0xFF;
                count++;
            }
        }
        for (int y = insetY; y < h - insetY; y += stepY) {
            int pLeft = bmp.getPixel(insetX, y);
            if (((pLeft >>> 24) & 0xFF) >= 200) {
                rSum += (pLeft >> 16) & 0xFF;
                gSum += (pLeft >> 8) & 0xFF;
                bSum += pLeft & 0xFF;
                count++;
            }
            int pRight = bmp.getPixel(w - 1 - insetX, y);
            if (((pRight >>> 24) & 0xFF) >= 200) {
                rSum += (pRight >> 16) & 0xFF;
                gSum += (pRight >> 8) & 0xFF;
                bSum += pRight & 0xFF;
                count++;
            }
        }
        if (count == 0) return 0;
        return 0xFF000000
                | ((int) (rSum / count) << 16)
                | ((int) (gSum / count) << 8)
                | (int) (bSum / count);
    }

    public static void clearCustomAppIcon(Context c, String key, String pkg) {
        if (c == null) return;
        String sk = safeKey(key, pkg);
        synchronized (ICON_CACHE) {
            ICON_CACHE.remove(sk);
        }
        File f = iconFile(c, key, pkg);
        if (f.exists()) f.delete();
    }

    /** Creates a 360x216 banner Bitmap for Classic mode from a custom app icon bitmap. */
    public static Bitmap buildClassicBannerFromCustomIcon(Bitmap rawIcon, int fallbackColor) {
        Bitmap icon = trimTransparentPadding(rawIcon);
        int w = 360, h = 216;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // Scale to fill the entire 360x216 icon area edge-to-edge
        int bgColor = sampleDominantEdgeColor(icon);
        canvas.drawColor(bgColor != 0 ? bgColor : (fallbackColor != 0 ? fallbackColor : 0xFF1C1C1E));
        float scale = Math.max(w / (float) Math.max(1, icon.getWidth()), h / (float) Math.max(1, icon.getHeight()));
        float sw = icon.getWidth() * scale;
        float sh = icon.getHeight() * scale;
        float dx = (w - sw) * 0.5f;
        float dy = (h - sh) * 0.5f;
        canvas.drawBitmap(icon, null, new RectF(dx, dy, dx + sw, dy + sh), p);
        return out;
    }

    // ---------------------------------------------------------------- Custom Wallpapers

    private static File sceneWallFile(Context c, int slot) {
        int s = Math.max(1, Math.min(5, slot));
        String name = (s == 1) ? "custom_scene_wallpaper.jpg" : ("custom_scene_wallpaper_" + s + ".jpg");
        return new File(c.getFilesDir(), name);
    }

    private static String sceneWallTsKey(int slot) {
        int s = Math.max(1, Math.min(5, slot));
        return (s == 1) ? KEY_SCENE_WALL_TS : (KEY_SCENE_WALL_TS + "_" + s);
    }

    private static File classicWallFile(Context c) {
        return new File(c.getFilesDir(), "custom_classic_wallpaper.jpg");
    }

    public static boolean hasCustomSceneWallpaper(Context c) {
        return hasCustomSceneWallpaper(c, 1);
    }

    public static boolean hasCustomSceneWallpaper(Context c, int slot) {
        if (c == null) return false;
        File f = sceneWallFile(c, slot);
        return f.exists() && f.length() > 0;
    }

    public static long getCustomSceneTimestamp(Context c) {
        return getCustomSceneTimestamp(c, 1);
    }

    public static long getCustomSceneTimestamp(Context c, int slot) {
        return prefs(c).getLong(sceneWallTsKey(slot), 0L);
    }

    public static boolean setCustomSceneWallpaper(Context c, Bitmap raw) {
        return setCustomSceneWallpaper(c, 1, raw);
    }

    public static boolean setCustomSceneWallpaper(Context c, int slot, Bitmap raw) {
        if (c == null || raw == null) return false;
        int s = Math.max(1, Math.min(5, slot));
        Bitmap cropped = scaleCenterCropOrFit(raw, 1920, 1080, true);
        File f = sceneWallFile(c, s);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
            long ts = System.currentTimeMillis();
            prefs(c).edit().putLong(sceneWallTsKey(s), ts).apply();
            cachedSceneWalls[s] = cropped;
            cachedSceneWallTs[s] = ts;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Bitmap getCustomSceneWallpaper(Context c, int reqW, int reqH) {
        return getCustomSceneWallpaper(c, 1, reqW, reqH);
    }

    public static Bitmap getCustomSceneWallpaper(Context c, int slot, int reqW, int reqH) {
        if (c == null) return null;
        int s = Math.max(1, Math.min(5, slot));
        long ts = getCustomSceneTimestamp(c, s);
        if (cachedSceneWalls[s] != null && !cachedSceneWalls[s].isRecycled() && cachedSceneWallTs[s] == ts) {
            return cachedSceneWalls[s];
        }
        File f = sceneWallFile(c, s);
        if (!f.exists() || f.length() == 0) return null;
        try {
            Bitmap b = decodeBitmapFromFile(f, Math.max(1280, reqW), Math.max(720, reqH));
            if (b != null) {
                cachedSceneWalls[s] = b;
                cachedSceneWallTs[s] = ts;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean hasCustomClassicWallpaper(Context c) {
        if (c == null) return false;
        File f = classicWallFile(c);
        return f.exists() && f.length() > 0;
    }

    public static boolean setCustomClassicWallpaper(Context c, Bitmap raw) {
        if (c == null || raw == null) return false;
        Bitmap cropped = scaleCenterCropOrFit(raw, 1920, 1080, true);
        File f = classicWallFile(c);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
            long ts = System.currentTimeMillis();
            prefs(c).edit().putLong(KEY_CLASSIC_WALL_TS, ts).apply();
            cachedClassicWall = cropped;
            cachedClassicWallTs = ts;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Bitmap getCustomClassicWallpaper(Context c) {
        if (c == null) return null;
        long ts = prefs(c).getLong(KEY_CLASSIC_WALL_TS, 0L);
        if (cachedClassicWall != null && !cachedClassicWall.isRecycled() && cachedClassicWallTs == ts) {
            return cachedClassicWall;
        }
        File f = classicWallFile(c);
        if (!f.exists() || f.length() == 0) return null;
        try {
            Bitmap b = decodeBitmapFromFile(f, 1280, 720);
            if (b != null) {
                cachedClassicWall = b;
                cachedClassicWallTs = ts;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Lock Screen Custom Wallpaper & Folder Shuffle

    private static File lockWallFile(Context c) {
        File dir = new File(c.getFilesDir(), "custom_wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "lock_wallpaper.jpg");
    }

    public static boolean hasCustomLockWallpaper(Context c) {
        if (c == null) return false;
        File f = lockWallFile(c);
        return f.exists() && f.length() > 0;
    }

    public static boolean setCustomLockWallpaper(Context c, Bitmap raw) {
        if (c == null || raw == null) return false;
        Bitmap cropped = scaleCenterCropOrFit(raw, 1920, 1080, true);
        File f = lockWallFile(c);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
            long ts = System.currentTimeMillis();
            prefs(c).edit().putLong("lock_wall_ts", ts).apply();
            cachedLockWall = cropped;
            cachedLockWallTs = ts;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static long getCustomLockTimestamp(Context c) {
        if (c == null) return 0L;
        return prefs(c).getLong("lock_wall_ts", 0L);
    }

    public static Bitmap getCustomLockWallpaper(Context c, int reqW, int reqH) {
        return getCustomLockWallpaper(c);
    }

    public static Bitmap getCustomLockWallpaper(Context c) {
        if (c == null) return null;
        long ts = prefs(c).getLong("lock_wall_ts", 0L);
        if (cachedLockWall != null && !cachedLockWall.isRecycled() && cachedLockWallTs == ts) {
            return cachedLockWall;
        }
        File f = lockWallFile(c);
        if (!f.exists() || f.length() == 0) return null;
        try {
            Bitmap b = decodeBitmapFromFile(f, 1280, 720);
            if (b != null) {
                cachedLockWall = b;
                cachedLockWallTs = ts;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setWallpaperShuffleFolder(Context c, String scope, String folderPath) {
        if (c == null || scope == null) return;
        String clean = folderPath == null ? "" : folderPath.trim();
        prefs(c).edit()
                .putString("shuffle_folder_" + scope, clean)
                .putLong("shuffle_folder_ts_" + scope, System.currentTimeMillis())
                .apply();
        synchronized (folderDeckMap) {
            folderDeckMap.remove(scope);
            folderIndexMap.remove(scope);
            folderPathCacheMap.remove(scope);
            folderCurrentBmpMap.remove(scope);
            folderCurrentFileMap.remove(scope);
        }
    }

    public static String getWallpaperShuffleFolder(Context c, String scope) {
        if (c == null || scope == null) return "";
        String p = prefs(c).getString("shuffle_folder_" + scope, "");
        return p != null ? p : "";
    }

    public static boolean hasWallpaperShuffleFolder(Context c, String scope) {
        String path = getWallpaperShuffleFolder(c, scope);
        if (path.isEmpty()) return false;
        File dir = new File(path);
        return dir.exists() && dir.isDirectory() && countImagesInFolder(dir) > 0;
    }

    public static int countImagesInFolder(File dir) {
        return listFolderImages(dir).size();
    }

    public static List<File> listFolderImages(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return out;
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File f : children) {
            if (f == null || f.getName().startsWith(".")) continue;
            if (f.isFile() && matchesImageExtension(f.getName(), false)) {
                out.add(f);
            } else if (f.isDirectory()) {
                // Also include images 1 level deep inside the selected folder
                File[] sub = f.listFiles();
                if (sub != null) {
                    for (File sf : sub) {
                        if (sf != null && !sf.getName().startsWith(".")
                                && sf.isFile() && matchesImageExtension(sf.getName(), false)) {
                            out.add(sf);
                        }
                    }
                }
            }
            if (out.size() >= 500) break;
        }
        return out;
    }

    public static String getWallpaperShuffleFolderSummary(Context c, String scope) {
        String path = getWallpaperShuffleFolder(c, scope);
        if (path.isEmpty()) {
            return I18n.s(c, "Not selected", "Seçilmedi");
        }
        File dir = new File(path);
        int count = countImagesInFolder(dir);
        if (count <= 0) {
            return dir.getName();
        }
        return dir.getName() + " (" + count + " " + I18n.s(c, "images", "görsel") + ")";
    }

    public static Bitmap getNextShuffledFolderBitmap(Context c, String scope, int reqW, int reqH, boolean advance) {
        if (c == null || scope == null) return null;
        String path = getWallpaperShuffleFolder(c, scope);
        if (path.isEmpty()) return null;
        File targetFile = null;
        synchronized (folderDeckMap) {
            if (!advance) {
                Bitmap cur = folderCurrentBmpMap.get(scope);
                if (cur != null && !cur.isRecycled()) {
                    return cur;
                }
            }
            List<File> deck = folderDeckMap.get(scope);
            String cachedPath = folderPathCacheMap.get(scope);
            if (deck == null || deck.isEmpty() || !path.equals(cachedPath)) {
                deck = listFolderImages(new File(path));
                if (deck.isEmpty()) return null;
                Collections.shuffle(deck);
                folderDeckMap.put(scope, deck);
                folderPathCacheMap.put(scope, path);
                folderIndexMap.put(scope, 0);
            }
            int idx = folderIndexMap.containsKey(scope) ? folderIndexMap.get(scope) : 0;
            if (advance) {
                idx++;
                if (idx >= deck.size()) {
                    String lastFile = folderCurrentFileMap.get(scope);
                    Collections.shuffle(deck);
                    if (deck.size() > 1 && lastFile != null && deck.get(0).getAbsolutePath().equals(lastFile)) {
                        Collections.swap(deck, 0, deck.size() - 1);
                    }
                    idx = 0;
                }
                folderIndexMap.put(scope, idx);
            }
            if (idx >= 0 && idx < deck.size()) {
                targetFile = deck.get(idx);
            }
        }
        if (targetFile == null || !targetFile.exists()) return null;
        Bitmap raw = decodeBitmapFromFile(targetFile, Math.max(1280, reqW), Math.max(720, reqH));
        if (raw == null) return null;
        Bitmap cropped = scaleCenterCropOrFit(raw, Math.max(1280, reqW), Math.max(720, reqH), true);
        if (cropped != raw) raw.recycle();
        synchronized (folderDeckMap) {
            folderCurrentBmpMap.put(scope, cropped);
            folderCurrentFileMap.put(scope, targetFile.getAbsolutePath());
        }
        return cropped;
    }

    private static Bitmap scaleCenterCropOrFit(Bitmap src, int targetW, int targetH, boolean centerCrop) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) return src;
        if (centerCrop) {
            float scale = Math.max(targetW / (float) sw, targetH / (float) sh);
            float dw = sw * scale;
            float dh = sh * scale;
            Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            float dx = (targetW - dw) * 0.5f;
            float dy = (targetH - dh) * 0.5f;
            c.drawBitmap(src, null, new RectF(dx, dy, dx + dw, dy + dh), p);
            return out;
        } else {
            if (sw <= targetW && sh <= targetH) {
                return src.copy(Bitmap.Config.ARGB_8888, false);
            }
            float scale = Math.min(targetW / (float) sw, targetH / (float) sh);
            int nw = Math.max(1, Math.round(sw * scale));
            int nh = Math.max(1, Math.round(sh * scale));
            return Bitmap.createScaledBitmap(src, nw, nh, true);
        }
    }

    // ---------------------------------------------------------------- Image & .ICO Decoding

    public static Bitmap decodeImageOrIco(File file, int maxW, int maxH) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".ico")) {
            try {
                byte[] bytes = readFileBytes(file, 16 * 1024 * 1024);
                Bitmap ico = decodeIcoBytes(bytes);
                if (ico != null) return ico;
            } catch (Throwable ignored) {
            }
        }
        Bitmap b = decodeBitmapFromFile(file, maxW, maxH);
        if (b != null) return b;
        // Fallback: try ICO parser in case extension was wrong or BitmapFactory failed on .ico
        try {
            byte[] bytes = readFileBytes(file, 16 * 1024 * 1024);
            return decodeIcoBytes(bytes);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap decodeImageOrIcoFromUri(Context c, Uri uri, int maxW, int maxH) {
        if (c == null || uri == null) return null;
        try (InputStream is = c.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            byte[] bytes = readStreamBytes(is, 20 * 1024 * 1024);
            if (bytes == null || bytes.length == 0) return null;
            Bitmap ico = decodeIcoBytes(bytes);
            if (ico != null) return ico;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decodeBitmapFromFile(File f, int maxW, int maxH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readFileBytes(File f, int maxBytes) throws Exception {
        long len = f.length();
        if (len <= 0 || len > maxBytes) return null;
        byte[] data = new byte[(int) len];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int r = fis.read(data, off, data.length - off);
                if (r <= 0) break;
                off += r;
            }
        }
        return data;
    }

    private static byte[] readStreamBytes(InputStream is, int maxBytes) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int total = 0;
        int r;
        while ((r = is.read(buf)) > 0) {
            total += r;
            if (total > maxBytes) break;
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }

    /**
     * Pure-Java Windows .ICO decoder: picks the highest-resolution directory entry and decodes
     * either embedded PNG data or BMP DIB (32-bit BGRA, 24-bit BGR, 8-bit / 4-bit / 1-bit paletted).
     */
    public static Bitmap decodeIcoBytes(byte[] data) {
        if (data == null || data.length < 22) return null;
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int reserved = buf.getShort(0) & 0xFFFF;
        int type = buf.getShort(2) & 0xFFFF;
        int count = buf.getShort(4) & 0xFFFF;
        if (reserved != 0 || (type != 1 && type != 2) || count <= 0 || count > 256) {
            return null;
        }
        int bestOffset = -1;
        int bestSize = -1;
        int bestScore = -1;
        for (int i = 0; i < count; i++) {
            int dirPos = 6 + i * 16;
            if (dirPos + 16 > data.length) break;
            int w = data[dirPos] & 0xFF;
            if (w == 0) w = 256;
            int h = data[dirPos + 1] & 0xFF;
            if (h == 0) h = 256;
            int bpp = buf.getShort(dirPos + 6) & 0xFFFF;
            int bytesInRes = buf.getInt(dirPos + 8);
            int imgOffset = buf.getInt(dirPos + 12);
            if (imgOffset < 0 || bytesInRes <= 0 || imgOffset + bytesInRes > data.length) continue;
            int score = w * h * Math.max(1, bpp);
            if (score > bestScore) {
                bestScore = score;
                bestOffset = imgOffset;
                bestSize = bytesInRes;
            }
        }
        if (bestOffset < 0 || bestSize <= 0) return null;

        // 1. Check if payload is a PNG (89 50 4E 47) or standard image BitmapFactory can decode
        Bitmap direct = BitmapFactory.decodeByteArray(data, bestOffset, bestSize);
        if (direct != null) return direct;

        // 2. Parse BMP BITMAPINFOHEADER inside ICO
        if (bestSize < 40) return null;
        int hdrSize = buf.getInt(bestOffset);
        if (hdrSize < 40 || bestOffset + hdrSize > data.length) return null;
        int width = buf.getInt(bestOffset + 4);
        int heightTotal = buf.getInt(bestOffset + 8);
        int height = Math.abs(heightTotal) / 2; // ICO height is doubled (XOR + AND masks)
        if (height <= 0 || width <= 0 || width > 2048 || height > 2048) return null;
        int bpp = buf.getShort(bestOffset + 14) & 0xFFFF;
        int compression = buf.getInt(bestOffset + 16);
        int colorsUsed = buf.getInt(bestOffset + 32);
        if (compression != 0) return null;

        int paletteEntries = 0;
        if (bpp <= 8) {
            paletteEntries = colorsUsed > 0 ? colorsUsed : (1 << bpp);
        }
        int paletteOffset = bestOffset + hdrSize;
        int pixelOffset = paletteOffset + paletteEntries * 4;
        if (pixelOffset > bestOffset + bestSize) return null;

        int[] palette = null;
        if (paletteEntries > 0) {
            palette = new int[paletteEntries];
            for (int i = 0; i < paletteEntries && paletteOffset + i * 4 + 3 < data.length; i++) {
                int b = data[paletteOffset + i * 4] & 0xFF;
                int g = data[paletteOffset + i * 4 + 1] & 0xFF;
                int r = data[paletteOffset + i * 4 + 2] & 0xFF;
                palette[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        int rowStride = ((width * bpp + 31) / 32) * 4;
        int xorBytes = rowStride * height;
        int andRowStride = ((width + 31) / 32) * 4;
        int andOffset = pixelOffset + xorBytes;

        int[] pixels = new int[width * height];
        boolean hasAnyAlpha = false;

        for (int y = 0; y < height; y++) {
            int srcY = heightTotal > 0 ? (height - 1 - y) : y;
            int rowPos = pixelOffset + srcY * rowStride;
            if (rowPos + rowStride > data.length) break;
            for (int x = 0; x < width; x++) {
                int argb = 0xFF000000;
                if (bpp == 32) {
                    int p = rowPos + x * 4;
                    int b = data[p] & 0xFF;
                    int g = data[p + 1] & 0xFF;
                    int r = data[p + 2] & 0xFF;
                    int a = data[p + 3] & 0xFF;
                    if (a > 0) hasAnyAlpha = true;
                    argb = (a << 24) | (r << 16) | (g << 8) | b;
                } else if (bpp == 24) {
                    int p = rowPos + x * 3;
                    int b = data[p] & 0xFF;
                    int g = data[p + 1] & 0xFF;
                    int r = data[p + 2] & 0xFF;
                    argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                } else if (bpp == 8 && palette != null) {
                    int idx = data[rowPos + x] & 0xFF;
                    if (idx < palette.length) argb = palette[idx];
                } else if (bpp == 4 && palette != null) {
                    int byteVal = data[rowPos + (x >> 1)] & 0xFF;
                    int idx = (x & 1) == 0 ? (byteVal >> 4) : (byteVal & 0x0F);
                    if (idx < palette.length) argb = palette[idx];
                } else if (bpp == 1 && palette != null) {
                    int byteVal = data[rowPos + (x >> 3)] & 0xFF;
                    int idx = (byteVal >> (7 - (x & 7))) & 1;
                    if (idx < palette.length) argb = palette[idx];
                }

                // Apply 1-bit AND transparency mask if not 32-bit with alpha
                if (bpp < 32 && andOffset + (srcY * andRowStride) + (x >> 3) < data.length) {
                    int maskByte = data[andOffset + (srcY * andRowStride) + (x >> 3)] & 0xFF;
                    int maskBit = (maskByte >> (7 - (x & 7))) & 1;
                    if (maskBit == 1) {
                        argb = 0x00000000;
                    }
                }
                pixels[y * width + x] = argb;
            }
        }

        if (bpp == 32 && !hasAnyAlpha) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] |= 0xFF000000;
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    // ---------------------------------------------------------------- File Selector Dialog

    public static void ensureStoragePermissions(Activity a) {
        if (a == null) return;
        // Also grant silently via LocalAdb if paired
        if (LocalAdb.isPaired(a)) {
            new Thread(() -> {
                try {
                    String pkg = a.getPackageName();
                    LocalAdb.execShellOutput(a,
                            "pm grant " + pkg + " android.permission.READ_EXTERNAL_STORAGE 2>/dev/null; "
                            + "pm grant " + pkg + " android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null; "
                            + "pm grant " + pkg + " android.permission.READ_MEDIA_IMAGES 2>/dev/null; "
                            + "appops set " + pkg + " MANAGE_EXTERNAL_STORAGE allow 2>/dev/null; "
                            + "appops set " + pkg + " READ_EXTERNAL_STORAGE allow 2>/dev/null; "
                            + "appops set " + pkg + " READ_MEDIA_IMAGES allow 2>/dev/null");
                } catch (Throwable ignored) {
                }
            }).start();
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (a.checkSelfPermission("android.permission.READ_MEDIA_IMAGES") != PackageManager.PERMISSION_GRANTED) {
                    a.requestPermissions(new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_EXTERNAL_STORAGE"}, 9402);
                }
            } else {
                if (a.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    a.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 9402);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean onActivityResult(int reqCode, int resCode, Intent data) {
        if (reqCode != REQ_SYSTEM_FILE_PICKER) return false;
        PendingSystemPick pick = pendingSystemPick;
        pendingSystemPick = null;
        if (pick == null || resCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        Uri uri = data.getData();
        new Thread(() -> {
            Bitmap b = decodeImageOrIcoFromUri(pick.activity, uri, pick.maxW, pick.maxH);
            pick.activity.runOnUiThread(() -> {
                if (b != null) {
                    pick.callback.onImagePicked(b);
                } else {
                    android.widget.Toast.makeText(pick.activity,
                            I18n.s(pick.activity, "Could not load selected image.", "Seçilen görsel yüklenemedi."),
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }, "decode-uri").start();
        return true;
    }

    public static void showImageFilePicker(
            Activity activity,
            String title,
            boolean allowIco,
            int maxW,
            int maxH,
            String resetLabel,
            Runnable onReset,
            ImagePickedCallback callback) {
        if (activity == null || activity.isFinishing()) return;
        ensureStoragePermissions(activity);
        showDirectoryBrowser(activity, title, null, allowIco, maxW, maxH, resetLabel, onReset, callback);
    }

    private static Map<String, File> getStorageRoots() {
        Map<String, File> roots = new LinkedHashMap<>();
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null || !ext.exists()) {
            ext = new File("/storage/emulated/0");
        }
        if (!ext.exists()) {
            ext = new File("/sdcard");
        }
        if (ext.exists()) {
            File dl = new File(ext, "Download");
            if (dl.exists() && dl.isDirectory()) roots.put("📥  Download (" + dl.getAbsolutePath() + ")", dl);
            File pics = new File(ext, "Pictures");
            if (pics.exists() && pics.isDirectory()) roots.put("🖼️  Pictures (" + pics.getAbsolutePath() + ")", pics);
            File dcim = new File(ext, "DCIM");
            if (dcim.exists() && dcim.isDirectory()) roots.put("📷  DCIM (" + dcim.getAbsolutePath() + ")", dcim);
            roots.put("💾  Internal Storage (" + ext.getAbsolutePath() + ")", ext);
        }
        // Check USB drives in /storage and /mnt/media_rw
        String[] mountParents = {"/storage", "/mnt/media_rw", "/mnt/usb", "/mnt/usb_storage"};
        for (String mp : mountParents) {
            File parent = new File(mp);
            File[] children = parent.listFiles();
            if (children == null) continue;
            for (File ch : children) {
                String n = ch.getName();
                if ("emulated".equals(n) || "self".equals(n) || "enc_emulated".equals(n)) continue;
                if (ch.isDirectory() && ch.canRead()) {
                    roots.put("🔌  USB / Storage (" + ch.getAbsolutePath() + ")", ch);
                }
            }
        }
        return roots;
    }

    private static boolean matchesImageExtension(String name, boolean allowIco) {
        String low = name.toLowerCase(Locale.US);
        if (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png")) return true;
        return allowIco && low.endsWith(".ico");
    }

    private static void showDirectoryBrowser(
            Activity activity,
            String title,
            File currentDir,
            boolean allowIco,
            int maxW,
            int maxH,
            String resetLabel,
            Runnable onReset,
            ImagePickedCallback callback) {
        if (activity == null || activity.isFinishing()) return;

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (currentDir == null) {
            if (resetLabel != null && onReset != null) {
                labels.add("↺  " + resetLabel);
                actions.add(onReset);
            }
            Map<String, File> roots = getStorageRoots();
            for (Map.Entry<String, File> entry : roots.entrySet()) {
                final File dir = entry.getValue();
                labels.add(entry.getKey());
                actions.add(() -> showDirectoryBrowser(activity, title, dir, allowIco, maxW, maxH, resetLabel, onReset, callback));
            }
            labels.add("📂  " + I18n.s(activity, "Open System File Picker…", "Sistem Dosya Seçicisini Aç…"));
            actions.add(() -> {
                try {
                    pendingSystemPick = new PendingSystemPick(activity, allowIco, maxW, maxH, callback);
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    activity.startActivityForResult(
                            Intent.createChooser(intent, title),
                            REQ_SYSTEM_FILE_PICKER);
                } catch (Throwable t) {
                    pendingSystemPick = null;
                    android.widget.Toast.makeText(activity,
                            I18n.s(activity, "No system file picker available on this TV.", "Bu TV'de sistem dosya seçicisi bulunmuyor."),
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                labels.add("🔓  " + I18n.s(activity, "Grant All Files Access…", "Tüm Dosyalara Erişim İzni Ver…"));
                actions.add(() -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    } catch (Throwable t) {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } else {
            labels.add("⬆️  .. (" + I18n.s(activity, "Back to folders", "Klasörlere dön") + ")");
            actions.add(() -> {
                File parent = currentDir.getParentFile();
                if (parent != null && parent.canRead() && !currentDir.getAbsolutePath().equals("/storage/emulated/0")
                        && !currentDir.getAbsolutePath().equals("/sdcard")) {
                    showDirectoryBrowser(activity, title, parent, allowIco, maxW, maxH, resetLabel, onReset, callback);
                } else {
                    showDirectoryBrowser(activity, title, null, allowIco, maxW, maxH, resetLabel, onReset, callback);
                }
            });

            File[] files = currentDir.listFiles();
            if (files != null && files.length > 0) {
                List<File> dirs = new ArrayList<>();
                List<File> imgs = new ArrayList<>();
                for (File f : files) {
                    if (f.getName().startsWith(".")) continue;
                    if (f.isDirectory()) {
                        dirs.add(f);
                    } else if (f.isFile() && matchesImageExtension(f.getName(), allowIco)) {
                        imgs.add(f);
                    }
                }
                Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(imgs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                for (File d : dirs) {
                    labels.add("📁  " + d.getName());
                    actions.add(() -> showDirectoryBrowser(activity, title, d, allowIco, maxW, maxH, resetLabel, onReset, callback));
                }
                for (File img : imgs) {
                    long kb = Math.max(1L, img.length() / 1024L);
                    labels.add("🖼️  " + img.getName() + "  (" + kb + " KB)");
                    actions.add(() -> {
                        new Thread(() -> {
                            Bitmap decoded = decodeImageOrIco(img, maxW, maxH);
                            activity.runOnUiThread(() -> {
                                if (decoded != null) {
                                    callback.onImagePicked(decoded);
                                } else {
                                    android.widget.Toast.makeText(activity,
                                            I18n.s(activity, "Could not decode image file.", "Görsel dosyası okunamadı."),
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }, "decode-file").start();
                    });
                }
            }
            if (labels.size() == 1) {
                String exts = allowIco ? ".jpg, .jpeg, .png, .ico" : ".jpg, .jpeg, .png";
                labels.add("ℹ️  " + I18n.s(activity, "No image files (" + exts + ") in this folder",
                        "Bu klasörde görsel dosyası (" + exts + ") bulunamadı"));
                actions.add(() -> showDirectoryBrowser(activity, title, currentDir, allowIco, maxW, maxH, resetLabel, onReset, callback));
            }
        }

        String dlgTitle = currentDir == null
                ? title + " (" + (allowIco ? "JPG, PNG, ICO" : "JPG, PNG") + ")"
                : currentDir.getAbsolutePath();

        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(dlgTitle)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton(I18n.s(activity, "Cancel", "İptal"), null)
                .show();
    }

    public interface SimpleFolderPickedCallback {
        void onFolderSelected(File folder);
    }

    public static void showFolderPicker(
            Activity activity,
            String scope,
            String title,
            SimpleFolderPickedCallback callback) {
        showFolderPicker(activity, title, (folder, count) -> {
            if (folder != null) {
                setWallpaperShuffleFolder(activity, scope, folder.getAbsolutePath());
                if (callback != null) callback.onFolderSelected(folder);
            }
        });
    }

    public static void showFolderPicker(
            Activity activity,
            String title,
            FolderPickedCallback callback) {
        if (activity == null || activity.isFinishing()) return;
        ensureStoragePermissions(activity);
        showFolderBrowser(activity, title, null, callback);
    }

    private static void showFolderBrowser(
            Activity activity,
            String title,
            File currentDir,
            FolderPickedCallback callback) {
        if (activity == null || activity.isFinishing()) return;

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (currentDir == null) {
            Map<String, File> roots = getStorageRoots();
            for (Map.Entry<String, File> entry : roots.entrySet()) {
                final File dir = entry.getValue();
                labels.add(entry.getKey());
                actions.add(() -> showFolderBrowser(activity, title, dir, callback));
            }
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                labels.add("🔓  " + I18n.s(activity, "Grant All Files Access…", "Tüm Dosyalara Erişim İzni Ver…"));
                actions.add(() -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    } catch (Throwable t) {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } else {
            int imgCount = countImagesInFolder(currentDir);
            if (imgCount > 0) {
                labels.add("✓  " + I18n.s(activity,
                        "Select this folder & shuffle (" + imgCount + " images)",
                        "Bu klasörü seç ve karıştır (" + imgCount + " görsel)"));
                actions.add(() -> {
                    if (callback != null) callback.onFolderPicked(currentDir, imgCount);
                });
            } else {
                labels.add("ℹ️  " + I18n.s(activity,
                        "No images in this folder (open a subfolder below)",
                        "Bu klasörde görsel yok (aşağıdan bir alt klasör açın)"));
                actions.add(() -> showFolderBrowser(activity, title, currentDir, callback));
            }

            labels.add("⬆️  .. (" + I18n.s(activity, "Back to parent folder", "Üst klasöre dön") + ")");
            actions.add(() -> {
                File parent = currentDir.getParentFile();
                if (parent != null && parent.canRead() && !currentDir.getAbsolutePath().equals("/storage/emulated/0")
                        && !currentDir.getAbsolutePath().equals("/sdcard")) {
                    showFolderBrowser(activity, title, parent, callback);
                } else {
                    showFolderBrowser(activity, title, null, callback);
                }
            });

            File[] files = currentDir.listFiles();
            if (files != null && files.length > 0) {
                List<File> dirs = new ArrayList<>();
                for (File f : files) {
                    if (f != null && !f.getName().startsWith(".") && f.isDirectory()) {
                        dirs.add(f);
                    }
                }
                Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File d : dirs) {
                    int subCount = countImagesInFolder(d);
                    String badge = subCount > 0
                            ? "  (" + subCount + " " + I18n.s(activity, "images", "görsel") + ")"
                            : "";
                    labels.add("📁  " + d.getName() + badge);
                    actions.add(() -> showFolderBrowser(activity, title, d, callback));
                }
            }
        }

        String dlgTitle = currentDir == null
                ? title
                : currentDir.getAbsolutePath();

        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(dlgTitle)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton(I18n.s(activity, "Cancel", "İptal"), null)
                .show();
    }
}
