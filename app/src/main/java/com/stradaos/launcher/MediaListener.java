package com.stradaos.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Necesario para leer las sesiones multimedia en Android 5+ (el usuario lo activa en "Acceso a notificaciones").
 *  También avisa cuando la radio FM del equipo empieza o deja de sonar (por su notificación). */
public class MediaListener extends NotificationListenerService {
    static boolean connected;
    @Override public void onListenerConnected() {
        connected = true;
        MediaWatcher.kick(this);
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification n : all)
                if (FmRadio.PKG.equals(n.getPackageName())) FmRadio.onNotification(n.getNotification(), true);
        } catch (Throwable ignored) { }
    }
    @Override public void onListenerDisconnected() { connected = false; }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (FmRadio.PKG.equals(sbn.getPackageName())) FmRadio.onNotification(sbn.getNotification(), true);
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (FmRadio.PKG.equals(sbn.getPackageName())) FmRadio.onNotification(sbn.getNotification(), false);
    }
}
