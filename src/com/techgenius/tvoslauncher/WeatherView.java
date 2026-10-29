package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Weather drawn on a glass surface, in Apple Weather's layout.
 * Compact: the overview under the status row - place, icon, temperature, condition, high/low and
 * five small day columns. Full: the forecast opened from it - a centred header, the next hours
 * and a 5-day list with temperature range bars coloured from cool blue to hot red.
 */
final class WeatherView extends View {
    static final float COMPACT_W = 214, COMPACT_H = 52, FULL_W = 380, FULL_H = 456;

    final boolean full;
    final float u;
    Weather.Data data;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Typeface regular, medium, semibold;
    private final RectF r = new RectF();

    WeatherView(Context c, boolean full, float unit) {
        super(c);
        this.full = full;
        this.u = unit;
        regular = Ui.font(c, "regular");
        medium = Ui.font(c, "medium");
        semibold = Ui.font(c, "semibold");
    }

    void setData(Weather.Data d) {
        data = d;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Context ctx = getContext();
        if (data == null) {
            text(c, I18n.s(ctx, "Getting the weather\u2026", "Hava durumu alınıyor\u2026"), getWidth() / 2f, getHeight() / 2f + 5 * u, 13, medium, 0.7f, Paint.Align.CENTER);
            return;
        }
        if (full) drawFull(c);
        else drawCompact(c);
    }

    /** A small glass pill: icon, temperature, then condition over place and high/low. */
    private void drawCompact(Canvas c) {
        Context ctx = getContext();
        Weather.Data d = data;
        float h = getHeight(), pad = 12 * u, is = 30 * u;
        icon(c, d.code, d.day, pad, (h - is) / 2f, is);
        float tx = pad + is + 6 * u;
        float tw = text(c, d.temp + "\u00b0", tx, h / 2f + 8 * u, 22, regular, 1f, Paint.Align.LEFT);
        float cx = tx + tw + 9 * u;
        text(c, Weather.describe(ctx, d.code, d.day), cx, h / 2f - 2 * u, 11.5f, semibold, 1f, Paint.Align.LEFT);
        text(c, (d.place.isEmpty() ? "" : d.place + "  ") + I18n.s(ctx, "H:", "Y:") + d.max + "\u00b0 " + I18n.s(ctx, "L:", "D:") + d.min + "\u00b0",
                cx, h / 2f + 12 * u, 11.5f, medium, 0.7f, Paint.Align.LEFT);
    }

    private void drawFull(Canvas c) {
        Context ctx = getContext();
        Weather.Data d = data;
        float w = getWidth(), pad = 20 * u, x0 = pad, x1 = w - pad, mid = w / 2f;
        // Header
        text(c, d.place.isEmpty() ? I18n.s(ctx, "My Location", "Konumum") : d.place, mid, pad + 14 * u, 17, semibold, 1f, Paint.Align.CENTER);
        text(c, d.temp + "\u00b0", mid + 5 * u, pad + 66 * u, 54, regular, 1f, Paint.Align.CENTER);
        text(c, Weather.describe(ctx, d.code, d.day), mid, pad + 86 * u, 14.5f, medium, 0.85f, Paint.Align.CENTER);
        text(c, I18n.s(ctx, "H:", "Y:") + d.max + "\u00b0  " + I18n.s(ctx, "L:", "D:") + d.min + "\u00b0", mid, pad + 104 * u, 13.5f, medium, 0.85f, Paint.Align.CENTER);

        // Hourly
        float y = pad + 126 * u;
        text(c, I18n.s(ctx, "HOURLY FORECAST", "SAATLİK TAHMİN"), x0, y, 11.5f, semibold, 0.55f, Paint.Align.LEFT);
        text(c, I18n.s(ctx, "Humidity ", "Nem %") + d.humidity + (I18n.isTr(ctx) ? "" : "%") + "  \u00b7  " + I18n.s(ctx, "Wind ", "Rüzgar ") + d.wind + " " + d.windUnit, x1, y, 11.5f, medium, 0.55f, Paint.Align.RIGHT);
        hairline(c, x0, x1, y + 8 * u);
        int hn = Math.min(6, d.hours.size());
        float hw = (x1 - x0) / Math.max(1, hn);
        for (int i = 0; i < hn; i++) {
            Weather.Hour h = d.hours.get(i);
            float hx = x0 + hw * (i + 0.5f);
            text(c, h.label, hx, y + 27 * u, 12, semibold, i == 0 ? 1f : 0.8f, Paint.Align.CENTER);
            icon(c, h.code, h.day, hx - 13 * u, y + 34 * u, 26 * u);
            text(c, h.temp + "\u00b0", hx, y + 79 * u, 14, semibold, 1f, Paint.Align.CENTER);
        }

        // 5-day list with range bars
        y += 102 * u;
        text(c, I18n.s(ctx, "5-DAY FORECAST", "5 GÜNLÜK TAHMİN"), x0, y, 11.5f, semibold, 0.55f, Paint.Align.LEFT);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Weather.Day day : d.days) {
            lo = Math.min(lo, day.min);
            hi = Math.max(hi, day.max);
        }
        lo = Math.min(lo, d.temp);
        hi = Math.max(hi, d.temp);
        float span = Math.max(1, hi - lo);
        float row = 32 * u, by0 = x0 + 150 * u, by1 = x1 - 40 * u;
        boolean f = d.windUnit.contains("mp");
        for (int i = 0; i < Math.min(5, d.days.size()); i++) {
            Weather.Day day = d.days.get(i);
            float ry = y + 8 * u + row * i;
            hairline(c, x0, x1, ry);
            float base = ry + row / 2f + 5 * u;
            text(c, day.label, x0, base, 15, semibold, 1f, Paint.Align.LEFT);
            float ix = x0 + 64 * u;
            if (day.rain >= 20) {
                icon(c, day.code, true, ix, ry + 3 * u, 22 * u);
                text(c, day.rain + "%", ix + 11 * u, ry + row - 1 * u, 11.5f, semibold, 1f, Paint.Align.CENTER, 0xFF64C8FF);
            } else {
                icon(c, day.code, true, ix - 1 * u, ry + 5 * u, 24 * u);
            }
            text(c, day.min + "\u00b0", by0 - 10 * u, base, 15, semibold, 0.55f, Paint.Align.RIGHT);
            text(c, day.max + "\u00b0", x1, base, 15, semibold, 1f, Paint.Align.RIGHT);
            // Track, then this day's range coloured by temperature.
            float cy = ry + row / 2f, th = 5 * u;
            bar.setShader(null);
            bar.setColor(0x33FFFFFF);
            r.set(by0, cy - th / 2, by1, cy + th / 2);
            c.drawRoundRect(r, th / 2, th / 2, bar);
            float a = by0 + (by1 - by0) * (day.min - lo) / span, b = by0 + (by1 - by0) * (day.max - lo) / span;
            bar.setColor(0xFFFFFFFF);  // opaque: the track's 20% alpha must not carry over to the range
            bar.setShader(new LinearGradient(a, 0, Math.max(b, a + 1), 0, tempColor(day.min, f), tempColor(day.max, f), Shader.TileMode.CLAMP));
            r.set(a, cy - th / 2, Math.max(b, a + th), cy + th / 2);
            c.drawRoundRect(r, th / 2, th / 2, bar);
            if (i == 0) {  // current temperature on today's bar
                float nx = by0 + (by1 - by0) * (d.temp - lo) / span;
                bar.setShader(null);
                bar.setColor(0xFF1C1C1E);
                c.drawCircle(nx, cy, th * 0.95f, bar);
                bar.setColor(0xFFFFFFFF);
                c.drawCircle(nx, cy, th * 0.6f, bar);
            }
        }
        // Source and time of the last update.
        String when = android.text.format.DateFormat.getTimeFormat(getContext()).format(new java.util.Date(d.fetchedAt));
        text(c, Weather.sourceName(d.source) + "  \u00b7  " + I18n.s(ctx, "Updated ", "Güncellendi ") + when, mid, getHeight() - 14 * u, 11.5f, medium, 0.5f, Paint.Align.CENTER);
    }

    /** Apple Weather's palette: blue (cold) -> teal -> green -> yellow -> orange -> red (hot). */
    static int tempColor(int t, boolean fahrenheit) {
        float cdeg = fahrenheit ? (t - 32) * 5f / 9f : t;
        float[] at = {-5, 8, 16, 22, 28, 35};
        int[] col = {0xFF5E9BFF, 0xFF56D6E4, 0xFF8FDB6A, 0xFFF7D74A, 0xFFFF9F2E, 0xFFFF5A3C};
        if (cdeg <= at[0]) return col[0];
        for (int i = 1; i < at.length; i++) {
            if (cdeg <= at[i]) {
                float k = (cdeg - at[i - 1]) / (at[i] - at[i - 1]);
                return mix(col[i - 1], col[i], k);
            }
        }
        return col[col.length - 1];
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void icon(Canvas c, int code, boolean day, float x, float y, float size) {
        Bitmap b = WeatherIcons.get(code, day, Math.round(size));
        c.drawBitmap(b, x, y, null);
    }

    private void hairline(Canvas c, float x0, float x1, float y) {
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        p.setStrokeWidth(Math.max(1, 0.8f * u));
        c.drawLine(x0, y, x1, y, p);
    }

    private float text(Canvas c, String s, float x, float y, float size, android.graphics.Typeface tf, float alpha, Paint.Align al) {
        return text(c, s, x, y, size, tf, alpha, al, 0xFFFFFFFF);
    }

    private float text(Canvas c, String s, float x, float y, float size, android.graphics.Typeface tf, float alpha, Paint.Align al, int color) {
        p.setTypeface(tf);
        p.setTextSize(size * u);
        p.setTextAlign(al);
        p.setColor(color);
        p.setAlpha(Math.round(255 * alpha));
        c.drawText(s, x, y, p);
        return p.measureText(s);
    }
}
