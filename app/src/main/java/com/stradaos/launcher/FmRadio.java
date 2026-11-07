package com.stradaos.launcher;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Radio FM del equipo (com.android.fmradio de MediaTek/acloud): sabe si está sonando y en qué frecuencia,
 * por su notificación y por los avisos que manda al sistema, y le pide cambiar de emisora.
 * Lo que no se conoce de antemano (los datos exactos de cada aviso) queda en el diagnóstico.
 */
final class FmRadio {
    static final String PKG = "com.android.fmradio";
    static final String[] ACTIONS = {
            "com.android.radio.widget.freq_volue",
            "com.acloud.intent.android.MAINUI_UPDATE",
            "com.acloud.intent.android.MEDIA_OPEN",
            "com.acloud.intent.android.MEDIA_CLOSE",
            // la radio avisa la emisora en cada cambio (FmMainActivity.xySendFmRadipFreq): {"113","1",encendida,"0",bajo,alto}
            "com.acloud.intent.android.XY.ANDROID.CANBUSDISPINFO1"};

    interface Listener { void onRadioChanged(); }

    static volatile boolean on;
    static volatile float freq = Float.NaN;
    static volatile String name, rds;
    static volatile long seenAt, offAt;
    /** true mientras la notificación de la radio está puesta (= su pantalla NO está encima). */
    static volatile boolean notifShown;
    static Listener listener;
    private static boolean registered;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final HashMap<String, Long> lastLog = new HashMap<String, Long>();
    private static final Pattern FREQ = Pattern.compile("(\\d{2,3})[.,](\\d{1,2})");

    private static android.content.SharedPreferences prefs;

    /** Mira si el servicio de la radio está en primer plano (= sonando de fondo). No necesita el acceso a
     *  notificaciones: así la radio aparece en el cuadro apenas se vuelve al inicio. */
    static void poll(Context c) {
        boolean fg = false;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            for (android.app.ActivityManager.RunningServiceInfo si : am.getRunningServices(300))
                if (PKG.equals(si.service.getPackageName()) && si.service.getClassName().endsWith("FmService")) { fg = si.foreground; break; }
        } catch (Throwable ignored) { return; }
        long now = SystemClock.elapsedRealtime();
        if (fg == on || (fg && now - offAt < 4000)) return;
        on = fg;
        if (fg) seenAt = now;
        BtBridge.log("radio FM: " + (fg ? "sonando" : "detenida") + " (servicio)");
        notifyChanged();
    }

    static void register(Context c) {
        if (registered) return;
        registered = true;
        app = c.getApplicationContext();
        try {
            prefs = c.getSharedPreferences("launcher", 0);
            float last = prefs.getFloat("fm_last", Float.NaN);   // última emisora conocida
            if (Float.isNaN(freq) && !Float.isNaN(last)) freq = last;
        } catch (Throwable ignored) { }
        try {
            IntentFilter f = new IntentFilter();
            for (String a : ACTIONS) f.addAction(a);
            c.getApplicationContext().registerReceiver(rx, f);
        } catch (Throwable e) {
            BtBridge.log("radio FM: no pude escuchar sus avisos (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static final BroadcastReceiver rx = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            Bundle x = i.getExtras();
            log(a, x);
            boolean changed = false;
            if (a != null && a.endsWith("CANBUSDISPINFO1")) {
                String[] ex = i.getStringArrayExtra("extras");
                if (ex != null && ex.length >= 6 && "113".equals(ex[0])) {
                    try {
                        double d = Integer.parseInt(ex[5].trim()) * 256 + Integer.parseInt(ex[4].trim());
                        while (d > 200) d /= 10;   // 10210 → 102.1
                        if (d >= 64 && d <= 108.5 && (float) d != freq) {
                            setFreq((float) d);
                            BtBridge.log("radio FM: emisora " + fmt(freq) + " MHz (aviso de la radio)");
                            notifyChanged();
                        }
                    } catch (Throwable ignored) { }
                }
                return;
            }
            if (x != null) {
                for (String k : x.keySet()) {
                    Object v = x.get(k);
                    String kl = k.toLowerCase();
                    if (kl.contains("freq")) {
                        float f = toMhz(v);
                        if (!Float.isNaN(f)) {
                            freq = f;
                            changed = true;
                            try { if (prefs != null) prefs.edit().putFloat("fm_last", f).apply(); } catch (Throwable ignored) { }
                        }
                    } else if (v instanceof String && (kl.contains("name") || kl.equals("ps") || kl.contains("program_service"))) {
                        name = ((String) v).trim().length() > 0 ? ((String) v).trim() : null;
                        changed = true;
                    } else if (v instanceof String && (kl.contains("radio_text") || kl.equals("rt") || kl.contains("rds"))) {
                        rds = ((String) v).trim().length() > 0 ? ((String) v).trim() : null;
                        changed = true;
                    }
                }
            }
            if (a != null && a.endsWith("freq_volue") && !Float.isNaN(freq)
                    && SystemClock.elapsedRealtime() - offAt > 4000) {   // tras apagarla puede llegar un último aviso
                on = true;
                seenAt = SystemClock.elapsedRealtime();
                changed = true;
            }
            if (changed) notifyChanged();
        }
    };

    /** Notificación de la radio (desde el lector de notificaciones). */
    static void onNotification(Notification n, boolean posted) {
        notifShown = posted;
        if (!posted) {
            on = false;
            BtBridge.log("radio FM: notificación quitada");
            notifyChanged();
            return;
        }
        on = true;
        seenAt = SystemClock.elapsedRealtime();
        // La notificación de la radio trae 3 botones: anterior, apagar y siguiente (FmService.showPlayingNotification).
        try {
            if (n.actions != null && n.actions.length >= 3) {
                piPrev = n.actions[0].actionIntent;
                piOff = n.actions[1].actionIntent;
                piNext = n.actions[2].actionIntent;
            }
        } catch (Throwable ignored) { }
        try {
            Bundle e = n.extras;
            String title = e != null ? str(e.get("android.title")) : null;
            String text = e != null ? str(e.get("android.text")) : null;
            log("notificación", e);
            float f = parseFreq(title);
            if (Float.isNaN(f)) f = parseFreq(text);
            if (!Float.isNaN(f)) setFreq(f);
            // título = nombre de la emisora (RDS); si no tiene, la radio pone un texto genérico
            String other = Float.isNaN(parseFreq(title)) ? title : text;
            name = other != null && Float.isNaN(parseFreq(other)) && !generic(other) ? other : null;
            rds = text != null && !text.equals(other) && text.trim().length() > 0 ? text.trim() : null;
            // la frecuencia solo viene dibujada en el ícono grande de la notificación: se lee de ahí
            Object ic = e != null ? e.get("android.largeIcon") : null;
            if (ic instanceof android.graphics.Bitmap) readIconAsync((android.graphics.Bitmap) ic);
        } catch (Throwable ignored) { }
        notifyChanged();
    }

    private static android.app.PendingIntent piPrev, piOff, piNext;

    /** Emisora siguiente/anterior: se usan los botones de la notificación de la radio
     *  (la orden va directo a su servicio); si no hay, la tecla del volante (xy.android.seek_*). */
    static void next(Context c) { if (!fire(piNext, "siguiente")) send(c, "xy.android.seek_next"); }
    static void prev(Context c) { if (!fire(piPrev, "anterior")) send(c, "xy.android.seek_prev"); }

    /** Tecla «play» del volante: la radio la obedece si su app sigue abierta de fondo (PlayStateControlManager). */
    static void forcePlay(Context c) { send(c, "xy.android.forceplay"); }

    /** Cierra la app de radio por completo, con el mismo aviso que usa el sistema para cerrar apps
     *  (MAINUI_UPDATE «KillAppSync»). Al cerrarse, su pantalla termina su proceso (System.exit): la radio
     *  se apaga y ya no puede volver a encenderse sola al arrancar el carro (FmMainActivity, al recibir
     *  «xy.android.acc.on», enciende la radio si su pantalla sigue abierta de fondo). */
    static void close(Context c) {
        try {
            Intent i = new Intent("com.acloud.intent.android.MAINUI_UPDATE");
            i.putExtra("extras", new String[]{"KillAppSync", "stradaos", "?" + PKG + "?"});
            c.sendBroadcast(i);
            BtBridge.log("radio FM: se cierra su app (así no se enciende sola al arrancar)");
        } catch (Throwable e) {
            BtBridge.log("radio FM: no se pudo cerrar su app (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Apaga la radio con su propio botón. Devuelve false si no se puede (aún no hay notificación). */
    static boolean turnOff() {
        if (!fire(piOff, "apagar")) return false;
        on = false;
        offAt = SystemClock.elapsedRealtime();
        notifyChanged();
        return true;
    }

    private static boolean fire(android.app.PendingIntent pi, String what) {
        if (pi == null) return false;
        try {
            pi.send();
            BtBridge.log("radio FM: botón " + what);
            return true;
        } catch (Throwable e) {
            BtBridge.log("radio FM: botón " + what + " falló (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }

    private static void send(Context c, String action) {
        try {
            c.sendBroadcast(new Intent(action));
            BtBridge.log("radio FM: enviado " + action);
        } catch (Throwable e) {
            BtBridge.log("radio FM: no se pudo enviar " + action);
        }
    }

    /** Abre la app de radio (cuando no responde al recuperar el sonido). */
    static boolean launch(Context c) {
        // La radio no tiene ícono propio (lo pone la app «Radio» de acloud), así que se abre su pantalla directo.
        Intent[] tries = {
                new Intent(Intent.ACTION_MAIN).setClassName(PKG, PKG + ".FmMainActivity"),
                new Intent("xy.android.start.fmradio"),
                c.getPackageManager().getLaunchIntentForPackage("com.acloud.stub.localradio"),
                c.getPackageManager().getLaunchIntentForPackage(PKG)};
        for (Intent i : tries) {
            if (i == null) continue;
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    static String diag() {
        return "Radio FM: " + (on ? "sonando" : "detenida")
                + (Float.isNaN(freq) ? "" : " · " + fmt(freq) + " MHz")
                + (name != null ? " · " + name : "")
                + (seenAt == 0 ? "" : " · último aviso hace " + ((SystemClock.elapsedRealtime() - seenAt) / 1000) + " s");
    }

    static String fmt(float f) { return String.format(java.util.Locale.US, "%.1f", f); }

    private static void notifyChanged() {
        ui.post(new Runnable() { public void run() { if (listener != null) listener.onRadioChanged(); } });
    }

    /** Frecuencia en MHz desde lo que mande la radio: 101.7, 1017, 10170 o 101700 (kHz). */
    private static float toMhz(Object v) {
        double d;
        if (v instanceof Number) d = ((Number) v).doubleValue();
        else if (v instanceof String) { float f = parseFreq((String) v); if (!Float.isNaN(f)) return f; try { d = Double.parseDouble(((String) v).trim()); } catch (Exception e) { return Float.NaN; } }
        else return Float.NaN;
        while (d > 200) d /= 10;
        return d >= 64 && d <= 108.5 ? (float) d : Float.NaN;
    }

    private static float parseFreq(String s) {
        if (s == null) return Float.NaN;
        Matcher m = FREQ.matcher(s);
        if (!m.find()) return Float.NaN;
        float f = Float.parseFloat(m.group(1) + "." + m.group(2));
        return f >= 64 && f <= 108.5f ? f : Float.NaN;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static boolean generic(String s) {
        String t = s.trim().toLowerCase();
        return t.length() == 0 || t.equals("fm radio") || t.equals("radio fm") || t.equals("radio") || t.equals("fm") || t.equals("localradio");
    }

    private static void setFreq(float f) {
        freq = f;
        try { if (prefs != null) prefs.edit().putFloat("fm_last", f).apply(); } catch (Throwable ignored) { }
    }

    // ── lectura de la frecuencia desde el ícono de la notificación ──
    // La radio dibuja "101.7" con un TextView común (FmUtils.createNotificationLargeIcon): misma fuente del sistema,
    // centrado y de un solo color. Se dibujan aquí todas las frecuencias posibles igual y se elige la que coincide.

    private static Context app;
    private static int lastIconHash;
    private static volatile boolean reading;

    private static void readIconAsync(final android.graphics.Bitmap icon) {
        if (app == null || icon == null || icon.isRecycled() || reading) return;
        final android.graphics.Bitmap copy;
        try { copy = icon.copy(android.graphics.Bitmap.Config.ARGB_8888, false); } catch (Throwable e) { return; }
        int hsh = 17;
        for (int y = 0; y < copy.getHeight(); y += 3) for (int x = 0; x < copy.getWidth(); x += 3) hsh = hsh * 31 + copy.getPixel(x, y);
        if (hsh == lastIconHash) return;   // mismo ícono: misma frecuencia
        lastIconHash = hsh;
        reading = true;
        new Thread(new Runnable() { public void run() {
            try {
                if (Looper.myLooper() == null) Looper.prepare();   // por si el TextView necesita un Looper
                float f = readIcon(copy);
                if (!Float.isNaN(f) && f != freq) { setFreq(f); notifyChanged(); }
                BtBridge.log("radio FM: frecuencia leída del ícono = " + (Float.isNaN(f) ? "?" : fmt(f)));
            } catch (Throwable e) {
                BtBridge.log("radio FM: no se pudo leer el ícono (" + e.getClass().getSimpleName() + ")");
            } finally { reading = false; }
        } }).start();
    }

    private static float readIcon(android.graphics.Bitmap ic) {
        int w = ic.getWidth(), h = ic.getHeight();
        int bg = ic.getPixel(0, 0);
        boolean[] mask = new boolean[w * h];
        int ink = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = ic.getPixel(x, y);
            int d = Math.abs(((p >> 16) & 255) - ((bg >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((bg >> 8) & 255)) + Math.abs((p & 255) - (bg & 255));
            if (d > 120) { mask[y * w + x] = true; ink++; }
        }
        if (ink < 10) return Float.NaN;
        // Se dibuja con Paint (un TextView fuera de la pantalla principal fallaba con NullPointerException);
        // mismas medidas que un TextView: alto con el margen de la fuente y texto centrado.
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setColor(0xFFFFFFFF);
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        android.graphics.Bitmap cand = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(cand);
        int[] px = new int[w * h];
        float best = Float.NaN;
        int bestScore = Integer.MAX_VALUE;
        for (float size : new float[]{15f, 15.4f}) {   // los dos tamaños que usa la radio
            p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size, dm));
            android.graphics.Paint.FontMetricsInt fm = p.getFontMetricsInt();
            int mh = fm.bottom - fm.top;
            for (int st = 875; st <= 1080; st++) {
                String s = String.format(java.util.Locale.ENGLISH, "%.1f", st / 10f);
                int mw = (int) Math.ceil(p.measureText(s));
                int ox = (int) ((w - mw) * 0.5), oy = (int) ((h - mh) * 0.5);
                cand.eraseColor(0);
                cv.drawText(s, ox, oy - fm.top, p);
                cand.getPixels(px, 0, w, 0, 0, w, h);
                int score = 0;
                for (int i = 0; i < px.length && score < bestScore; i++) if (((px[i] >>> 24) > 100) != mask[i]) score++;
                if (score < bestScore) { bestScore = score; best = st / 10f; }
            }
        }
        cand.recycle();
        return bestScore <= ink / 2 ? best : Float.NaN;   // si casi nada coincide, mejor no inventar
    }

    /** Deja en el diagnóstico qué trae cada aviso (como mucho uno cada 30 s por tipo). */
    private static void log(String what, Bundle x) {
        long now = SystemClock.elapsedRealtime();
        Long last = lastLog.get(what);
        if (last != null && now - last < 30000) return;
        lastLog.put(what, now);
        StringBuilder sb = new StringBuilder("radio FM ").append(what).append(":");
        if (x != null) for (String k : x.keySet()) {
            Object v = x.get(k);
            String s = String.valueOf(v);
            if (s.length() > 40) s = s.substring(0, 40) + "…";
            sb.append(' ').append(k).append('=').append(s);
        }
        BtBridge.log(sb.toString());
    }
}
