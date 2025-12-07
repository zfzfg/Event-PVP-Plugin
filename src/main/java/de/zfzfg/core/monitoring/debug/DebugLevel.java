package de.zfzfg.core.monitoring.debug;

/**
 * Debug-Stufen für das Plugin.
 * Je höher die Stufe, desto mehr Details werden ausgegeben.
 */
public enum DebugLevel {
    /**
     * Debug ist deaktiviert (Standard)
     */
    OFF(0, "Aus"),
    
    /**
     * Stufe 1: Grundlegende Informationen
     * - Player-Aktionen (Join, Leave, Commands)
     * - Event-Starts und -Ends
     * - Match-Starts und -Ends
     */
    LEVEL_1(1, "Basis"),
    
    /**
     * Stufe 2: Erweiterte Informationen
     * - Alles aus Stufe 1
     * - Welt-Lade-Vorgänge
     * - Konfigurationsänderungen
     * - Inventar-Operationen
     */
    LEVEL_2(2, "Erweitert"),
    
    /**
     * Stufe 3: Vollständige Details
     * - Alles aus Stufe 2
     * - Interne State-Änderungen
     * - Timing-Informationen
     * - Detaillierte Fehlermeldungen
     */
    LEVEL_3(3, "Vollständig");

    private final int level;
    private final String displayName;

    DebugLevel(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Prüft ob diese Debug-Stufe mindestens die angegebene Stufe hat.
     */
    public boolean isAtLeast(DebugLevel other) {
        return this.level >= other.level;
    }

    /**
     * Gibt die Debug-Stufe für einen numerischen Wert zurück.
     */
    public static DebugLevel fromLevel(int level) {
        for (DebugLevel dl : values()) {
            if (dl.level == level) {
                return dl;
            }
        }
        return OFF;
    }

    /**
     * Versucht eine Debug-Stufe anhand des Namens oder der Nummer zu finden.
     */
    public static DebugLevel parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        // Versuche als Nummer zu parsen
        try {
            int level = Integer.parseInt(input);
            if (level >= 0 && level <= 3) {
                return fromLevel(level);
            }
        } catch (NumberFormatException ignored) {}
        
        // Versuche als Name zu parsen
        String upper = input.toUpperCase();
        if (upper.equals("OFF") || upper.equals("AUS")) {
            return OFF;
        }
        for (DebugLevel dl : values()) {
            if (dl.name().equalsIgnoreCase(upper) || 
                dl.name().replace("_", "").equalsIgnoreCase(upper) ||
                ("LEVEL" + dl.level).equalsIgnoreCase(upper) ||
                ("L" + dl.level).equalsIgnoreCase(upper) ||
                String.valueOf(dl.level).equals(input)) {
                return dl;
            }
        }
        return null;
    }
}
