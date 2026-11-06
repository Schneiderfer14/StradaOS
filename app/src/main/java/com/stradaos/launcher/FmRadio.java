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
            "com.acloud.intent.android.MEDIA_CLOSE"};

    interface Listener { void onRadioChanged(); }

    static volatile boolean on;
    static volatile float freq = Float.NaN;
    static volatile String name, rds;
    static volatile long seenAt;
    static Listener listener;
    private static boolean registered;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final HashMap<String, Long> lastLog = new HashMap<String, Long>();
    private static final Pattern FREQ = Pattern.compile("(\\d{2,3})[.,](\\d{1,2})");

    static void register(Context c) {
        if (registered) return;
        registered = true;
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
            if (x != null) {
                for (String k : x.keySet()) {
                    Object v = x.get(k);
                    String kl = k.toLowerCase();
                    if (kl.contains("freq")) {
                        float f = toMhz(v);
                        if (!Float.isNaN(f)) { freq = f; changed = true; }
                    } else if (v instanceof String && (kl.contains("name") || kl.equals("ps") || kl.contains("program_service"))) {
                        name = ((String) v).trim().length() > 0 ? ((String) v).trim() : null;
                        changed = true;
                    } else if (v instanceof String && (kl.contains("radio_text") || kl.equals("rt") || kl.contains("rds"))) {
                        rds = ((String) v).trim().length() > 0 ? ((String) v).trim() : null;
                        changed = true;
                    }
                }
            }
            if (a != null && a.endsWith("freq_volue") && !Float.isNaN(freq)) { on = true; seenAt = SystemClock.elapsedRealtime(); changed = true; }
            if (changed) notifyChanged();
        }
    };

    /** Notificación de la radio (desde el lector de notificaciones). */
    static void onNotification(Notification n, boolean posted) {
        if (!posted) {
            on = false;
            BtBridge.log("radio FM: notificación quitada");
            notifyChanged();
            return;
        }
        on = true;
        seenAt = SystemClock.elapsedRealtime();
        try {
            Bundle e = n.extras;
            String title = e != null ? str(e.get("android.title")) : null;
            String text = e != null ? str(e.get("android.text")) : null;
            log("notificación", e);
            float f = parseFreq(title);
            if (Float.isNaN(f)) f = parseFreq(text);
            if (!Float.isNaN(f)) freq = f;
            // lo que no sea la frecuencia se toma como nombre de la emisora
            String other = Float.isNaN(parseFreq(title)) ? title : text;
            if (other != null && Float.isNaN(parseFreq(other)) && !other.equalsIgnoreCase("FM Radio") && !other.equalsIgnoreCase("localradio"))
                name = other;
        } catch (Throwable ignored) { }
        notifyChanged();
    }

    static void next(Context c) { send(c, "fmradio.seek.next"); }
    static void prev(Context c) { send(c, "fmradio.seek.previous"); }

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
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(PKG);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable e) { return false; }
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
