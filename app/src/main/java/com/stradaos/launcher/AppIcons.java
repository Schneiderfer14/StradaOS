package com.stradaos.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Íconos de la columna izquierda (Radio, Bluetooth, Navegador, Ajustes, Apps), dibujados con Canvas
 * sobre una grilla de 512 unidades: se ven nítidos a cualquier tamaño y no pesan en el APK.
 */
public final class AppIcons {
    private AppIcons() { }

    static final String[] NAMES = {"radio", "bluetooth", "browser", "settings", "apps"};

    public static Bitmap make(String name, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(px / 512f, px / 512f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF sq = new RectF(0, 0, 512, 512);
        Path clip = new Path();
        clip.addRoundRect(sq, 114, 114, Path.Direction.CW);
        if ("radio".equals(name)) radio(c, p, clip);
        else if ("bluetooth".equals(name)) bluetooth(c, p, sq);
        else if ("browser".equals(name)) browser(c, p, sq);
        else if ("settings".equals(name)) settings(c, p, sq);
        else apps(c, p, sq);
        return b;
    }

    private static void vgrad(Paint p, int top, int bottom) {
        p.setShader(new LinearGradient(0, 0, 0, 512, top, bottom, Shader.TileMode.CLAMP));
    }

    private static void radio(Canvas c, Paint p, Path clip) {
        c.save();
        c.clipPath(clip);
        vgrad(p, 0xFFECECEE, 0xFFC4C6CB);
        c.drawRect(0, 0, 512, 512, p);
        vgrad(p, 0xFFFFB52E, 0xFFF08A1C);
        c.drawRect(0, 0, 180, 512, p);
        p.setShader(null);
        p.setColor(0xFF3A3A3C);
        for (int row = 0; row < 9; row++)
            for (int col = 0; col < 3; col++) c.drawCircle(58 + col * 34, 96 + row * 40, 9, p);
        c.drawRect(240, 80, 320, 90, p);
        c.drawRect(240, 108, 280, 115, p);
        c.drawRect(240, 130, 280, 137, p);
        c.drawRect(240, 152, 320, 162, p);
        c.drawRect(240, 180, 280, 187, p);
        c.drawRect(240, 202, 280, 209, p);
        c.drawCircle(352, 352, 98, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(20);
        p.setColor(0xFFFFB52E);
        c.drawCircle(352, 352, 66, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3A3A3C);
        c.drawCircle(352, 352, 54, p);
        c.restore();
    }

    private static void bluetooth(Canvas c, Paint p, RectF sq) {
        vgrad(p, 0xFF3B9CFF, 0xFF0A6FF0);
        c.drawRoundRect(sq, 114, 114, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(30);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        Path r = new Path();
        r.moveTo(168, 176);
        r.lineTo(340, 336);
        r.lineTo(256, 418);
        r.lineTo(256, 94);
        r.lineTo(340, 176);
        r.lineTo(168, 336);
        c.drawPath(r, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void browser(Canvas c, Paint p, RectF sq) {
        vgrad(p, 0xFFFFFFFF, 0xFFE4E5E9);
        c.drawRoundRect(sq, 114, 114, p);
        vgrad(p, 0xFF8A45C9, 0xFF5B2A9E);
        c.drawCircle(256, 256, 206, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(22);
        p.setColor(0xD9FFFFFF);
        p.setPathEffect(new DashPathEffect(new float[]{3, 15}, 0));
        c.drawCircle(256, 256, 172, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);
        tri(c, p, 0xFFFF4B3E, 256, 256, 384, 128, 276, 276);
        tri(c, p, 0xFFD9322A, 256, 256, 384, 128, 236, 236);
        tri(c, p, 0xFFFFFFFF, 256, 256, 128, 384, 236, 236);
        tri(c, p, 0xFFD6D8DE, 256, 256, 128, 384, 276, 276);
        p.setColor(0xFF5B2A9E);
        c.drawCircle(256, 256, 10, p);
    }

    private static void tri(Canvas c, Paint p, int color, float x1, float y1, float x2, float y2, float x3, float y3) {
        Path t = new Path();
        t.moveTo(x1, y1);
        t.lineTo(x2, y2);
        t.lineTo(x3, y3);
        t.close();
        p.setColor(color);
        c.drawPath(t, p);
    }

    private static void settings(Canvas c, Paint p, RectF sq) {
        vgrad(p, 0xFFD5DBE1, 0xFF8F9399);
        c.drawRoundRect(sq, 114, 114, p);
        p.setShader(null);
        p.setColor(0xFF4A4C51);
        c.drawCircle(256, 256, 190, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFC9CCD1);
        p.setStrokeWidth(40);
        p.setPathEffect(new DashPathEffect(new float[]{11, 11}, 0));
        c.drawCircle(256, 256, 170, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(256, 256, 146, p);
        p.setColor(0xFF5A5C61);
        c.drawCircle(256, 256, 118, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFC9CCD1);
        p.setStrokeWidth(22);
        p.setPathEffect(new DashPathEffect(new float[]{7, 7}, 0));
        c.drawCircle(256, 256, 104, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF46484D);
        c.drawCircle(256, 256, 84, p);
        p.setColor(0xFFC9CCD1);
        p.setStrokeWidth(24);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(256, 256, 256, 130, p);
        c.drawLine(256, 256, 365, 319, p);
        c.drawLine(256, 256, 147, 319, p);
        c.drawCircle(256, 256, 22, p);
        p.setColor(0xFF46484D);
        c.drawCircle(256, 256, 8, p);
    }

    private static void apps(Canvas c, Paint p, RectF sq) {
        p.setShader(new LinearGradient(0, 0, 512, 512, new int[]{0xFF4F5DF5, 0xFF22B8DC, 0xFF1FA8B0},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(sq, 114, 114, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(22);
        float[][] boxes = {{128, 128}, {278, 128}, {128, 278}, {278, 278}};
        for (float[] bx : boxes) c.drawRoundRect(new RectF(bx[0], bx[1], bx[0] + 106, bx[1] + 106), 14, 14, p);
        p.setStyle(Paint.Style.FILL);
    }
}
