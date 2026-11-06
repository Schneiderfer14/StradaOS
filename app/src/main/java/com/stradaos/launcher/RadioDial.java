package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Dial de la radio FM (88–108 MHz) con la aguja en la emisora actual. */
final class RadioDial extends View {
    private final float s;
    private final int color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float freq = Float.NaN;

    RadioDial(Context c, float scale, int color) {
        super(c);
        s = scale;
        this.color = color;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(11 * scale);
    }

    void setFreq(float f) {
        if (f == freq || (Float.isNaN(f) && Float.isNaN(freq))) return;
        freq = f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), x0 = 1 * s, x1 = w - 1 * s;
        for (int i = 0; i <= 40; i++) {               // cada 0,5 MHz
            float v = 88 + i * 0.5f, x = x0 + (x1 - x0) * (v - 88) / 20f;
            boolean major = i % 2 == 0, big = major && ((int) v) % 4 == 0;
            float len = (big ? 16 : major ? 10 : 6) * s;
            p.setColor(color);
            p.setAlpha(big ? 190 : 115);
            p.setStrokeWidth((big ? 2 : 1) * s);
            c.drawLine(x, h, x, h - len, p);
            if (big) {
                p.setAlpha(205);
                c.drawText(String.valueOf((int) v), x, h - 20 * s, p);
            }
        }
        if (!Float.isNaN(freq)) {
            float x = x0 + (x1 - x0) * (Math.max(88f, Math.min(108f, freq)) - 88) / 20f;
            p.setColor(Main.RED);
            p.setAlpha(255);
            p.setStrokeWidth(3 * s);
            c.drawLine(x, h, x, h - 30 * s, p);
        }
    }
}
