package com.stradaos.launcher;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Descargas HTTPS que también funcionan en Android 4.x:
 * - activa TLS 1.2 (viene apagado en Android 4.1–4.4)
 * - confía en certificados raíz actuales incluidos en la app (además de los del sistema)
 */
public class Net {
    private static SSLSocketFactory factory;
    static String initInfo = "sin iniciar";

    public static synchronized void init(Context c) {
        if (factory != null) return;
        try {
            final X509TrustManager system = defaultTm(null);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            InputStream in = c.getAssets().open("roots.pem");
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] rb = new byte[16384];
            int rn;
            while ((rn = in.read(rb)) > 0) raw.write(rb, 0, rn);
            in.close();
            String pem = new String(raw.toByteArray(), "US-ASCII");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            int i = 0, bad = 0, at = 0;
            while (true) {   // uno por uno: Android 4.x no entiende algunas raíces nuevas y fallaría todo el archivo
                int s0 = pem.indexOf("-----BEGIN CERTIFICATE-----", at);
                if (s0 < 0) break;
                int e0 = pem.indexOf("-----END CERTIFICATE-----", s0);
                if (e0 < 0) break;
                at = e0 + 25;
                try {
                    Certificate cert = cf.generateCertificate(new java.io.ByteArrayInputStream(pem.substring(s0, at).getBytes("US-ASCII")));
                    ks.setCertificateEntry("root" + (i++), cert);
                } catch (Throwable t) { bad++; }
            }
            final X509TrustManager bundled = defaultTm(ks);
            bundledKs = ks;
            X509TrustManager both = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    system.checkClientTrusted(chain, auth);
                }
                public void checkServerTrusted(X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    try { system.checkServerTrusted(chain, auth); }
                    catch (java.security.cert.CertificateException e) {
                        try { bundled.checkServerTrusted(chain, auth); }
                        catch (java.security.cert.CertificateException e2) {
                            // Radios sin pila de reloj arrancan en 2007: los certificados parecen «aún no válidos».
                            // Con el reloj claramente mal, se valida la cadena con la fecha del GPS.
                            if (!clockSuspicious()) throw e2;
                            checkAtTrustedDate(chain);
                        }
                    }
                }
                public X509Certificate[] getAcceptedIssuers() { return system.getAcceptedIssuers(); }
            };
            SSLContext ctx;
            try { ctx = SSLContext.getInstance("TLSv1.2"); } catch (Throwable e) { ctx = SSLContext.getInstance("TLS"); }
            ctx.init(null, new TrustManager[]{both}, null);
            factory = new Tls12Factory(ctx.getSocketFactory());
            initInfo = "TLS listo (" + ctx.getProtocol() + "), " + i + " raíces" + (bad > 0 ? ", " + bad + " omitidas" : "");
        } catch (Throwable e) {
            factory = null;
            initInfo = "TLS no disponible: " + e;
        }
    }

    // ── hora confiable (GPS) para equipos que pierden la fecha al apagarse ──

    private static KeyStore bundledKs;
    private static java.util.Set<java.security.cert.TrustAnchor> anchors;
    private static volatile long gpsOffset;
    private static volatile boolean gpsKnown;

    /** Hora UTC de una lectura del GPS: queda como referencia si el reloj del equipo está mal. */
    static void gpsTime(long t) {
        if (t <= 0) return;
        gpsOffset = t - System.currentTimeMillis();
        gpsKnown = true;
    }

    /** El reloj del equipo es anterior a 2025 o difiere del GPS en más de un día. */
    static boolean clockSuspicious() {
        if (gpsKnown && Math.abs(gpsOffset) > 86400000L) return true;
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) < 2025;
    }

    /** Hora actual: la del GPS si el reloj del equipo está mal y ya hubo una lectura. */
    static long now() {
        long t = System.currentTimeMillis();
        return gpsKnown && Math.abs(gpsOffset) > 120000L ? t + gpsOffset : t;
    }

    private static void checkAtTrustedDate(X509Certificate[] chain) throws java.security.cert.CertificateException {
        try {
            synchronized (Net.class) {
                if (anchors == null) {
                    java.util.HashSet<java.security.cert.TrustAnchor> set = new java.util.HashSet<java.security.cert.TrustAnchor>();
                    KeyStore sys = KeyStore.getInstance("AndroidCAStore");
                    sys.load(null, null);
                    for (KeyStore k : new KeyStore[]{sys, bundledKs}) {
                        if (k == null) continue;
                        java.util.Enumeration<String> e = k.aliases();
                        while (e.hasMoreElements()) {
                            Certificate c = k.getCertificate(e.nextElement());
                            if (c instanceof X509Certificate) set.add(new java.security.cert.TrustAnchor((X509Certificate) c, null));
                        }
                    }
                    anchors = set;
                }
            }
            java.util.ArrayList<X509Certificate> path = new java.util.ArrayList<X509Certificate>(java.util.Arrays.asList(chain));
            while (path.size() > 1) {   // la raíz autofirmada sobra: ya está entre las anclas
                X509Certificate last = path.get(path.size() - 1);
                if (last.getSubjectX500Principal().equals(last.getIssuerX500Principal())) path.remove(path.size() - 1);
                else break;
            }
            long when;
            if (gpsKnown) when = System.currentTimeMillis() + gpsOffset;
            else {   // sin GPS todavía: el punto medio de la validez del certificado del servidor
                long nb = chain[0].getNotBefore().getTime(), na = chain[0].getNotAfter().getTime();
                when = nb + (na - nb) / 2;
            }
            java.security.cert.PKIXParameters p = new java.security.cert.PKIXParameters(anchors);
            p.setRevocationEnabled(false);
            p.setDate(new java.util.Date(when));
            java.security.cert.CertPathValidator.getInstance("PKIX")
                .validate(CertificateFactory.getInstance("X.509").generateCertPath(path), p);
        } catch (java.security.cert.CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw new java.security.cert.CertificateException("reloj del equipo: " + e.getMessage(), e);
        }
    }

    private static X509TrustManager defaultTm(KeyStore ks) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager t : f.getTrustManagers()) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        throw new IllegalStateException("sin TrustManager");
    }

    public static byte[] get(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (c instanceof HttpsURLConnection && factory != null) ((HttpsURLConnection) c).setSSLSocketFactory(factory);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "StradaOS/2.1 (Android head unit launcher)");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** Envuelve la fábrica para encender TLS 1.1/1.2 en cada socket. */
    private static class Tls12Factory extends SSLSocketFactory {
        private final SSLSocketFactory d;
        Tls12Factory(SSLSocketFactory d) { this.d = d; }
        private Socket on(Socket s) { return on(s, null); }
        private Socket on(Socket s, String host) {
            if (s instanceof SSLSocket) {
                // Solo Android 4.x necesita ampliar cifrados y protocolos. En Android 5+ los valores de fábrica
                // ya traen TLS 1.2, y "todos los soportados" incluye TLS_FALLBACK_SCSV: el servidor lo toma como
                // una conexión degradada y la corta («Connection closed by peer»), así fallaban todos los mapas.
                if (android.os.Build.VERSION.SDK_INT < 21) {
                    try { // todos los cifrados que tenga el equipo (Android 4.x trae pocos activados), sin las señales SCSV
                        java.util.ArrayList<String> cs = new java.util.ArrayList<String>();
                        for (String c : ((SSLSocket) s).getSupportedCipherSuites()) if (!c.contains("SCSV")) cs.add(c);
                        ((SSLSocket) s).setEnabledCipherSuites(cs.toArray(new String[0]));
                    } catch (Throwable ignored) { }
                    try {
                        String[] sup = ((SSLSocket) s).getSupportedProtocols();
                        java.util.ArrayList<String> want = new java.util.ArrayList<String>();
                        for (String p : sup) if (p.equals("TLSv1.2") || p.equals("TLSv1.3") || p.equals("TLSv1.1")) want.add(p);
                        if (!want.isEmpty()) ((SSLSocket) s).setEnabledProtocols(want.toArray(new String[0]));
                    } catch (Throwable ignored) { }
                }
                if (host != null) {
                    try { s.getClass().getMethod("setHostname", String.class).invoke(s, host); } catch (Throwable ignored) { }   // SNI
                    try { s.getClass().getMethod("setUseSessionTickets", boolean.class).invoke(s, true); } catch (Throwable ignored) { }
                }
            }
            return s;
        }
        public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
        public Socket createSocket(Socket s, String h, int p, boolean a) throws java.io.IOException { return on(d.createSocket(s, h, p, a), h); }
        public Socket createSocket(String h, int p) throws java.io.IOException { return on(d.createSocket(h, p), h); }
        public Socket createSocket(String h, int p, InetAddress l, int lp) throws java.io.IOException { return on(d.createSocket(h, p, l, lp), h); }
        public Socket createSocket(InetAddress h, int p) throws java.io.IOException { return on(d.createSocket(h, p)); }
        public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws java.io.IOException { return on(d.createSocket(h, p, l, lp)); }
    }
}
