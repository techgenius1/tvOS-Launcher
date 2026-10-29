package com.techgenius.tvoslauncher.present;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import io.nayuki.qrcodegen.QrCode;

/** QR bitmap for the admin URL (replaces ZXing; uses the vendored Nayuki generator, MIT). */
final class Qr {
    private Qr() { }

    static Bitmap bitmap(String text, int size) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int n = qr.size;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        float cell = size / (float) n;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (qr.getModule(x, y)) c.drawRect(x * cell, y * cell, (x + 1) * cell + 0.5f, (y + 1) * cell + 0.5f, p);
            }
        }
        return b;
    }
}
