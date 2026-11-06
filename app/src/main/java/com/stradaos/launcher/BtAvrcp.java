package com.stradaos.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Botones de música hacia el celular por el control remoto Bluetooth (AVRCP) del propio Android.
 * Las pantallas con perfil «controlador AVRCP» (Autochips AC8227, entre otras) lo traen activo; la API es
 * oculta en Android 5–6, así que se usa por reflexión. Si no existe, simplemente no hace nada.
 */
final class BtAvrcp {
    static final int AVRCP_CONTROLLER = 12;   // BluetoothProfile.AVRCP_CONTROLLER (oculto antes de Android 7)
    static final int KEY_PLAY = 0x44, KEY_STOP = 0x45, KEY_PAUSE = 0x46, KEY_NEXT = 0x4B, KEY_PREV = 0x4C;

    private static BluetoothProfile proxy;
    private static boolean asked;

    /** Pide el perfil al sistema (una vez). Conviene llamarlo al abrir la app para que esté listo al tocar un botón. */
    static void init(Context c) {
        if (asked) return;
        asked = true;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return;
            a.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile p) {
                    proxy = p;
                    BtBridge.log("control remoto Bluetooth (AVRCP) listo");
                }
                public void onServiceDisconnected(int profile) { proxy = null; }
            }, AVRCP_CONTROLLER);
        } catch (Throwable e) {
            BtBridge.log("control remoto Bluetooth no disponible: " + e.getClass().getSimpleName());
        }
    }

    /** true si hay un celular conectado como fuente de música AVRCP. */
    static boolean connected() {
        try { return proxy != null && !proxy.getConnectedDevices().isEmpty(); } catch (Throwable e) { return false; }
    }

    /** Envía una tecla (presionar y soltar) a todos los celulares conectados. */
    static boolean send(int key) {
        if (proxy == null) return false;
        try {
            List<BluetoothDevice> devs = proxy.getConnectedDevices();
            if (devs.isEmpty()) return false;
            Method m = proxy.getClass().getMethod("sendPassThroughCmd", BluetoothDevice.class, int.class, int.class);
            for (BluetoothDevice d : devs) {
                m.invoke(proxy, d, key, 0);   // presionar
                m.invoke(proxy, d, key, 1);   // soltar
            }
            BtBridge.log("tecla AVRCP 0x" + Integer.toHexString(key) + " enviada");
            return true;
        } catch (Throwable e) {
            BtBridge.log("tecla AVRCP falló: " + e.getClass().getSimpleName());
            return false;
        }
    }
}
