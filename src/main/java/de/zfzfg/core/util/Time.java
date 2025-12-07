package de.zfzfg.core.util;

/**
 * Zeit-/Tick-Helfer für Bukkit-Scheduler-Aufrufe.
 * Bietet klare, benannte Konvertierungen statt Magic Numbers.
 */
public final class Time {

    private Time() {}

    /** Anzahl Ticks pro Sekunde in Bukkit. */
    public static final long TICKS_PER_SECOND = 20L;

    /** Konvertiert Sekunden in Ticks. */
    public static long seconds(long seconds) {
        return seconds * TICKS_PER_SECOND;
    }

    /** Konvertiert Minuten in Ticks. */
    public static long minutes(long minutes) {
        return seconds(minutes * 60L);
    }

    /** Direkter Zugriff auf Tick-Werte für kurze Delays. */
    public static long ticks(long ticks) {
        return ticks;
    }
}