package de.zfzfg.core.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schutzpruefungen fuer eingehende Web-Requests, bewusst ohne HttpExchange-Abhaengigkeit,
 * damit sie sich ohne laufenden Server testen lassen.
 *
 * <p>Das Panel laedt seine Daten ausschliesslich same-origin (relative {@code fetch}-URLs).
 * Fremde Origins brauchen also nie Zugriff. Frueher wurde jeder {@code Origin} samt
 * {@code Allow-Credentials} zurueckgespiegelt; {@code SameSite=Strict} bindet aber nicht an
 * den Port, und so haette jede andere Web-App auf demselben Host (Dynmap, BlueMap, ...) mit
 * dem Admin-Cookie Configs lesen und schreiben koennen.</p>
 */
final class WebRequestGuard {

    /** Obergrenze fuer Request-Bodies. Die groessten Nutzlasten (equipment.yml) liegen weit darunter. */
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private WebRequestGuard() {}

    /**
     * Ob ein Request mit diesem {@code Origin}-Header angenommen wird.
     *
     * <p>Kein Origin: kein Cross-Origin-Browser-Request (Navigation, curl, same-origin GET)
     * und damit erlaubt. Sonst muss der Origin zum {@code Host}-Header des Requests passen
     * oder zur konfigurierten {@code public-url} (Reverse-Proxy mit anderem Host/Port).</p>
     */
    static boolean isOriginAllowed(String origin, String hostHeader, String publicUrl) {
        if (origin == null || origin.isEmpty()) {
            return true;
        }
        String originAuthority = authorityOf(origin);
        if (originAuthority == null) {
            // "null" (Sandbox-iframes, file://) oder kaputt: nie vertrauen.
            return false;
        }
        if (hostHeader != null && originAuthority.equals(normalizeAuthority(hostHeader, schemeOf(origin)))) {
            return true;
        }
        String publicAuthority = publicUrl == null ? null : authorityOf(publicUrl);
        return publicAuthority != null && originAuthority.equals(publicAuthority);
    }

    /**
     * Ob die Client-IP laut {@code security.allowed-ips} zugreifen darf. Leere Liste = alle.
     */
    static boolean isIpAllowed(String clientIp, java.util.Collection<String> allowedIps) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        if (clientIp == null) {
            return false;
        }
        for (String allowed : allowedIps) {
            if (allowed != null && clientIp.equalsIgnoreCase(allowed.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Liest den Body bis {@link #MAX_BODY_BYTES}. Ist er groesser, wird
     * {@link PayloadTooLargeException} geworfen, bevor mehr als das Limit im Speicher liegt.
     */
    static String readBodyLimited(InputStream in, int maxBytes) throws IOException {
        byte[] data = in.readNBytes(maxBytes + 1);
        if (data.length > maxBytes) {
            throw new PayloadTooLargeException(maxBytes);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /** "scheme://host:port" -> "host:port" mit explizitem Port, klein geschrieben. */
    private static String authorityOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort() >= 0 ? uri.getPort() : defaultPort(uri.getScheme());
            return uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String schemeOf(String url) {
        int idx = url.indexOf("://");
        return idx > 0 ? url.substring(0, idx) : "http";
    }

    private static String normalizeAuthority(String hostHeader, String scheme) {
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        // IPv6-Literal "[::1]:8085" hat Doppelpunkte im Host selbst.
        int portSep = host.lastIndexOf(':');
        boolean hasPort = portSep > host.lastIndexOf(']');
        return hasPort ? host : host + ":" + defaultPort(scheme);
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /** Request-Body ueber dem Limit; der Aufrufer antwortet mit 413. */
    static final class PayloadTooLargeException extends IOException {
        PayloadTooLargeException(int maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");  // i18n-ignore: technische Exception, erreicht nie einen Spieler
        }
    }

    /**
     * Festes Zeitfenster je Schluessel (IP). Abgelaufene Fenster starten neu, verwaiste
     * Eintraege werden ab 512 Schluesseln ausgeraeumt.
     */
    static final class RateLimiter {
        private static final class Window {
            long start;
            int count;
        }

        private final int maxRequests;
        private final long windowMs;
        private final Map<String, Window> windows = new ConcurrentHashMap<>();

        RateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        long windowSeconds() {
            return windowMs / 1000L;
        }

        boolean tryAcquire(String key, long now) {
            Window window = windows.computeIfAbsent(key, k -> new Window());
            boolean allowed;
            synchronized (window) {
                if (now - window.start >= windowMs) {
                    window.start = now;
                    window.count = 0;
                }
                window.count++;
                allowed = window.count <= maxRequests;
            }
            if (windows.size() > 512) {
                windows.values().removeIf(w -> {
                    synchronized (w) {
                        return now - w.start >= windowMs * 2;
                    }
                });
            }
            return allowed;
        }
    }
}
