package com.techgenius.tvoslauncher.present;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The Presentation icon: a stack of slides with a play button on a violet-to-blue gradient.
 * Drawn on a 5:3 canvas like the other tvOS cards; the web app's logo.svg is the same drawing.
 */
public final class PresentIcon {
    static final int FROM = 0xFF7B5CFF, TO = 0xFF0A84FF;

    private PresentIcon() {
    }

    public static Bitmap banner(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        // Background: diagonal gradient with a soft light from the top left.
        p.setShader(new LinearGradient(0, 0, w, h, FROM, TO, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.2f, 0, w * 0.75f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Three slides fanned back to front.
        float sh = h * 0.5f, sw = sh * 16f / 9f, r = h * 0.05f;
        float cx = w / 2f + h * 0.035f, cy = h / 2f + h * 0.04f, step = h * 0.06f;
        RectF s = new RectF();
        int[] alpha = {0x38, 0x6E};
        for (int i = 0; i < 2; i++) {
            float d = step * (2 - i);
            s.set(cx - sw / 2 - d, cy - sh / 2 - d, cx + sw / 2 - d, cy + sh / 2 - d);
            p.setColor(Color.argb(alpha[i], 255, 255, 255));
            c.drawRoundRect(s, r, r, p);
        }
        s.set(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2);
        p.setShadowLayer(h * 0.05f, 0, h * 0.02f, 0x40000040);
        p.setShader(new LinearGradient(0, s.top, 0, s.bottom, 0xFFFFFFFF, 0xFFEDEFF6, Shader.TileMode.CLAMP));
        c.drawRoundRect(s, r, r, p);
        p.clearShadowLayer();

        // Play button in the icon's gradient, with rounded corners.
        float t = sh * 0.36f, px = s.centerX() + t * 0.12f, py = s.centerY() - sh * 0.05f;
        Path tri = new Path();
        tri.moveTo(px - t * 0.45f, py - t * 0.52f);
        tri.lineTo(px + t * 0.55f, py);
        tri.lineTo(px - t * 0.45f, py + t * 0.52f);
        tri.close();
        p.setShader(new LinearGradient(s.left, s.top, s.right, s.bottom, FROM, TO, Shader.TileMode.CLAMP));
        p.setPathEffect(new CornerPathEffect(t * 0.16f));
        c.drawPath(tri, p);
        p.setPathEffect(null);

        // Page dots: the first one is the current slide.
        float dy = s.bottom - sh * 0.14f, dr = sh * 0.032f, gap = dr * 3.4f;
        for (int i = 0; i < 3; i++) {
            float dx = s.centerX() + (i - 1) * gap;
            if (i == 0) {
                p.setShader(new LinearGradient(s.left, 0, s.right, 0, FROM, TO, Shader.TileMode.CLAMP));
            } else {
                p.setShader(null);
                p.setColor(0x33202040);
            }
            c.drawCircle(dx, dy, dr, p);
        }
        return b;
    }
}
