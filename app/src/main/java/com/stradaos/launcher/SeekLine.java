package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Barra de avance del widget de música. Cuando la app que suena permite adelantar, muestra un círculo
 * que se puede arrastrar (o tocar la barra) para ir a otra parte de la canción. Si no se puede
 * (por ejemplo el celular por Bluetooth), es solo una barra que muestra el avance.
 */
final class SeekLine extends View {
    interface OnSeek { void seek(float fraction); }

    private final float s;
    private final int trackColor;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float progress = -1f, drag = -1f;
    private boolean seekable;
    private OnSeek cb;

    SeekLine(Context c, float scale, int trackColor) {
        super(c);
        s = scale;
        this.trackColor = trackColor;
    }

    void setOnSeek(OnSeek o) { cb = o; }

    void setSeekable(boolean b) {
        if (b == seekable) return;
        seekable = b;
        if (!b) drag = -1f;
        invalidate();
    }

    void setProgress(float f) {
        if (drag >= 0) return;   // mientras se arrastra manda el dedo
        if (Math.abs(f - progress) < 0.001f) return;
        progress = f;
        invalidate();
    }

    private float x0() { return 12 * s; }
    private float x1() { return getWidth() - 12 * s; }

    private float frac(float x) {
        float w = x1() - x0();
        return w <= 0 ? 0 : Math.max(0f, Math.min(1f, (x - x0()) / w));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f, th = 6 * s, a = x0(), b = x1();
        p.setColor(trackColor);
        r.set(a, cy - th / 2, b, cy + th / 2);
        c.drawRoundRect(r, th / 2, th / 2, p);
        float f = drag >= 0 ? drag : progress;
        if (f < 0) return;
        float xe = a + (b - a) * Math.min(1f, f);
        p.setColor(Main.RED);
        r.set(a, cy - th / 2, Math.max(a + th, xe), cy + th / 2);
        c.drawRoundRect(r, th / 2, th / 2, p);
        if (seekable) {
            p.setColor(0x55000000);
            c.drawCircle(xe, cy + 1 * s, 10.5f * s, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(xe, cy, 10 * s, p);
            p.setColor(Main.RED);
            c.drawCircle(xe, cy, 6.5f * s, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!seekable || cb == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                drag = frac(e.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                drag = frac(e.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                float f = frac(e.getX());
                drag = -1f;
                progress = f;
                invalidate();
                cb.seek(f);
                return true;
            case MotionEvent.ACTION_CANCEL:
                drag = -1f;
                invalidate();
                return true;
        }
        return true;
    }
}
