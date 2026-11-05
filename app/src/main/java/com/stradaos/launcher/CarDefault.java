package com.stradaos.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

/**
 * Foto de fábrica del vehículo (Grand Vitara Sport 3 puertas) ya recortada: color en JPEG y transparencia
 * en una máscara aparte, así pesa ~17 KB. Se usa mientras el usuario no elija su propia foto.
 */
final class CarDefault {
    private CarDefault() { }

    static Bitmap load() {
        try {
            byte[] a = Base64.decode(RGB, Base64.DEFAULT), m = Base64.decode(MASK, Base64.DEFAULT);
            Bitmap color = BitmapFactory.decodeByteArray(a, 0, a.length);
            Bitmap mask = BitmapFactory.decodeByteArray(m, 0, m.length);
            int w = color.getWidth(), h = color.getHeight();
            int[] px = new int[w * h], al = new int[w * h];
            color.getPixels(px, 0, w, 0, 0, w, h);
            mask.getPixels(al, 0, w, 0, 0, w, h);
            for (int i = 0; i < px.length; i++) px[i] = ((al[i] & 0xFF) << 24) | (px[i] & 0x00FFFFFF);
            Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            color.recycle();
            mask.recycle();
            return out;
        } catch (Throwable e) {
            return null;
        }
    }

    private static final String RGB =
        "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAUEBAUEAwUFBAUGBgUGCA4JCAcHCBEMDQoOFBEVFBMRExMWGB8bFhceFxMTGyUcHiAh" +
        "IyMjFRomKSYiKR8iIyL/2wBDAQYGBggHCBAJCRAiFhMWIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIi" +
        "IiIiIiIiIiL/wAARCACgARADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUF" +
        "BAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVW" +
        "V1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi" +
        "4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC" +
        "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVm" +
        "Z2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq" +
        "8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD4yooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKntrS4vZmis7eWeVY3lKRIWIRFLu" +
        "2B2VVZiewBJ4FAEFFdDYeBvFeqkDS/DGt3hMKXAFtp8smYn3bH4U/K21sHodpx0Ne7+M/wBnLQ7aIW/gfxDFLfRTEXkmsaiiR24X" +
        "KGPCQ/M/mK4J3DZsKlSTkAHzPRX0nH+z34X1P4iXFro3jWCXQImikW0hc3V95WAJfMdIxGhLZ2kBwARkccz/APDL2jpLq8M3xAlW" +
        "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" +
        "/9k=";

    private static final String MASK =
        "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAQDAwQDAwQEAwQFBAQFBgoHBgYGBg0JCggKDw0QEA8NDw4RExgUERIXEg4PFRwVFxkZ" +
        "GxsbEBQdHx0aHxgaGxr/2wBDAQQFBQYFBgwHBwwaEQ8RGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoa" +
        "GhoaGhoaGhr/wAARCACgARADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUF" +
        "BAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVW" +
        "V1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi" +
        "4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC" +
        "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVm" +
        "Z2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq" +
        "8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD8/wCiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKesMjDKxswGOQp79K9V8M/" +
        "s8eNfHOhJrXgnTxrNi28HyZA0gZcBl8sZbILen0zQB5PRXvVz+yL8RdLC3PiO0tdA0dgpGpajcLBCmeu/cQyYwfvKOlcvcfCDTLa" +
        "a/il+JngtWtDgYu5pBOcZ+Ro42UjtnI+lAHltFd/YfDrS73YY/GFncqfv/YdLvrgqfT/AFIGfxrqLX4KadPM0a6h4ouljyZTb+E2" +
        "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";
}
