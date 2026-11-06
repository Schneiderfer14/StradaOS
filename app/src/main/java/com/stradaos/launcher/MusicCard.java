package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Fondo del widget de música: degradado o carátula difuminada, nota musical cuando no hay carátula,
 * velo inferior para que se lea el texto y barra de progreso. El difuminado se calcula una sola vez por
 * carátula (reducir y volver a ampliar con filtro), así no le cuesta nada al equipo mientras suena.
 */
public class MusicCard extends View {
    private final float s, radius, barY;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Bitmap note;
    private Bitmap blurred;
    private Bitmap source;
    private float progress = -1f;

    /** @param barFromBottom distancia (en px de diseño) desde el borde inferior hasta la barra de progreso */
    public MusicCard(Context c, float scale, Bitmap noteIcon, float barFromBottom) {
        super(c);
        s = scale;
        radius = 22 * scale;
        barY = barFromBottom * scale;
        note = noteIcon;
    }

    public void setArt(Bitmap b) {
        if (b == source) return;
        source = b;
        blurred = null;
        if (b != null && !b.isRecycled()) {
            try {
                Bitmap small = Bitmap.createScaledBitmap(b, 18, 18, true);
                blurred = Bitmap.createScaledBitmap(small, 96, 96, true);
                if (small != blurred) small.recycle();
            } catch (Throwable ignored) { blurred = null; }
        }
        invalidate();
    }

    public void setProgress(float p) {
        if (Math.abs(p - progress) < 0.001f) return;
        progress = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        box.set(0, 0, w, h);
        paint.setColorFilter(null);
        paint.setAlpha(255);
        if (blurred != null) {
            BitmapShader sh = new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float k = Math.max(w / blurred.getWidth(), h / blurred.getHeight());
            Matrix m = new Matrix();
            m.setScale(k, k);
            m.postTranslate((w - blurred.getWidth() * k) / 2f, (h - blurred.getHeight() * k) / 2f);
            sh.setLocalMatrix(m);
            paint.setShader(sh);
            c.drawRoundRect(box, radius, radius, paint);
            // carátula nítida, cuadrada, arriba al centro
            float side = Math.min(w - 64 * s, h - 212 * s);
            if (side > 40 * s && source != null && !source.isRecycled()) {
                RectF a = new RectF((w - side) / 2f, 18 * s, (w + side) / 2f, 18 * s + side);
                BitmapShader as = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float ka = Math.max(side / source.getWidth(), side / source.getHeight());
                Matrix am = new Matrix();
                am.setScale(ka, ka);
                am.postTranslate(a.left + (side - source.getWidth() * ka) / 2f, a.top + (side - source.getHeight() * ka) / 2f);
                as.setLocalMatrix(am);
                paint.setShader(as);
                c.drawRoundRect(a, 14 * s, 14 * s, paint);
            }
        } else {
            paint.setShader(new LinearGradient(0, 0, w * 0.6f, h, Main.MUSIC_A, Main.MUSIC_B, Shader.TileMode.CLAMP));
            c.drawRoundRect(box, radius, radius, paint);
            if (note != null) {
                paint.setShader(null);
                paint.setAlpha(Main.LIGHT ? 110 : 75);
                float sz = 80 * s, cy = (h - 190 * s) / 2f;
                c.drawBitmap(note, null, new RectF((w - sz) / 2f, cy - sz / 2f, (w + sz) / 2f, cy + sz / 2f), paint);
                paint.setAlpha(255);
            }
        }
        // velo inferior para el texto
        float fadeTop = h - 200 * s;
        paint.setShader(new LinearGradient(0, fadeTop, 0, fadeTop + 55 * s, Main.FADE & 0x00FFFFFF, Main.FADE, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(0, fadeTop, w, h);
        c.drawRoundRect(box, radius, radius, paint);
        c.restore();
        paint.setShader(null);

        // barra de progreso (0 = la dibuja otra vista)
        if (barY <= 0) return;
        float y = h - barY, x0 = 16 * s, x1 = w - 16 * s, th = 4 * s;
        bar.setColor(Main.LIGHT ? 0x1F000000 : 0x40FFFFFF);
        c.drawRoundRect(new RectF(x0, y, x1, y + th), th / 2, th / 2, bar);
        if (progress >= 0) {
            bar.setColor(Main.RED);
            float xe = x0 + (x1 - x0) * Math.max(0f, Math.min(1f, progress));
            c.drawRoundRect(new RectF(x0, y, Math.max(x0 + th, xe), y + th), th / 2, th / 2, bar);
        }
    }
}
