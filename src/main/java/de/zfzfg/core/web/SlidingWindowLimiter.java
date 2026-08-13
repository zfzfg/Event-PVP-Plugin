package de.zfzfg.core.web;

/**
 * Einfache Mengenbegrenzung ueber ein gleitendes Zeitfenster.
 *
 * <p>Gebaut fuer {@code POST /api/inventories/restore}: dieser Endpunkt legt Items im Spiel
 * an. Ohne Bremse waere ein uebernommenes Web-Login eine Item-Fabrik, die im Sekundentakt
 * Backups auf wechselnde Spieler zurueckspielt. Die Begrenzung gilt deshalb bewusst
 * <em>global</em> und nicht je Zielspieler - der Zielspieler ist frei waehlbar und taugt
 * nicht als Schluessel.</p>
 *
 * <p>Das Fenster ist ein Sprungfenster, kein exakt gleitendes: nach Ablauf startet die
 * Zaehlung bei null. An der Fenstergrenze sind dadurch kurzzeitig bis zu doppelt so viele
 * Zugriffe moeglich. Fuer den Zweck genuegt das - es geht darum, eine Automatisierung
 * auszubremsen, nicht darum, eine Abrechnung auf die Sekunde genau zu fuehren.</p>
 */
public final class SlidingWindowLimiter {

    private final int maxPermits;
    private final long windowMs;

    private long windowStart;
    private int used;

    /**
     * @param maxPermits erlaubte Zugriffe je Fenster
     * @param windowMs   Fensterlaenge in Millisekunden
     */
    public SlidingWindowLimiter(int maxPermits, long windowMs) {
        if (maxPermits < 1) {
            throw new IllegalArgumentException("maxPermits must be at least 1");
        }
        if (windowMs < 1) {
            throw new IllegalArgumentException("windowMs must be positive");
        }
        this.maxPermits = maxPermits;
        this.windowMs = windowMs;
    }

    /**
     * Versucht, einen Zugriff zu belegen.
     *
     * <p>{@code synchronized}, weil HTTP-Handler auf mehreren Threads laufen - ohne das
     * koennten zwei gleichzeitige Anfragen dieselbe Restmenge sehen und beide durchkommen.</p>
     *
     * @return {@code false}, wenn das Fenster ausgeschoepft ist
     */
    public synchronized boolean tryAcquire() {
        return tryAcquire(System.currentTimeMillis());
    }

    /**
     * Wie {@link #tryAcquire()}, aber mit vorgegebener Zeit.
     *
     * <p>Nur fuer Tests: sonst muesste ein Test echte Sekunden warten, um den
     * Fensterwechsel zu pruefen.</p>
     */
    synchronized boolean tryAcquire(long now) {
        if (now - windowStart >= windowMs) {
            windowStart = now;
            used = 0;
        }
        if (used >= maxPermits) {
            return false;
        }
        used++;
        return true;
    }

    /** Verbleibende Zugriffe im laufenden Fenster. */
    public synchronized int remaining() {
        return Math.max(0, maxPermits - used);
    }

    public int getMaxPermits() {
        return maxPermits;
    }
}
