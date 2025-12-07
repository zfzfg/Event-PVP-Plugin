package de.zfzfg.core.monitoring.debug;

/**
 * Kategorien für Debug-Nachrichten.
 * Jede Kategorie hat ein Mindest-Level, ab dem sie aktiv ist.
 */
public enum DebugCategory {
    // ==================== Level 1: Basis ====================
    /**
     * Player-bezogene Aktionen (Join, Leave, Commands, Teleports)
     */
    PLAYER(DebugLevel.LEVEL_1, "Spieler", "&a"),
    
    /**
     * Event-bezogene Aktionen (Start, Stop, Join, Leave)
     */
    EVENT(DebugLevel.LEVEL_1, "Event", "&6"),
    
    /**
     * Match/PvP-bezogene Aktionen (Match-Start, Ende, Wetten)
     */
    MATCH(DebugLevel.LEVEL_1, "Match", "&c"),
    
    /**
     * Command-Ausführungen und Validierungen
     */
    COMMAND(DebugLevel.LEVEL_1, "Command", "&f"),
    
    /**
     * Wett-bezogene Aktionen (Anfragen, Annahmen, Ablehnungen)
     */
    WAGER(DebugLevel.LEVEL_1, "Wette", "&5"),
    
    // ==================== Level 2: Erweitert ====================
    /**
     * Welt-bezogene Aktionen (Laden, Entladen, Teleports)
     */
    WORLD(DebugLevel.LEVEL_2, "Welt", "&b"),
    
    /**
     * Konfigurations-bezogene Aktionen (Laden, Speichern, Änderungen)
     */
    CONFIG(DebugLevel.LEVEL_2, "Konfig", "&e"),
    
    /**
     * Inventar-bezogene Aktionen (Speichern, Wiederherstellen)
     */
    INVENTORY(DebugLevel.LEVEL_2, "Inventar", "&d"),
    
    /**
     * Arena-bezogene Aktionen (Auswahl, Teleport, Vorbereitung)
     */
    ARENA(DebugLevel.LEVEL_2, "Arena", "&3"),
    
    /**
     * Equipment-bezogene Aktionen (Laden, Anwenden, Entfernen)
     */
    EQUIPMENT(DebugLevel.LEVEL_2, "Equip", "&2"),
    
    /**
     * Teleport-bezogene Aktionen (Spieler-Teleports, Spawn-Handling)
     */
    TELEPORT(DebugLevel.LEVEL_2, "Teleport", "&1"),
    
    /**
     * Statistik-bezogene Aktionen (Laden, Speichern, Updates)
     */
    STATS(DebugLevel.LEVEL_2, "Stats", "&4"),
    
    // ==================== Level 3: Vollständig ====================
    /**
     * System-bezogene Informationen (Startup, Shutdown, Tasks)
     */
    SYSTEM(DebugLevel.LEVEL_3, "System", "&7"),
    
    /**
     * Timing/Performance-Informationen
     */
    TIMING(DebugLevel.LEVEL_3, "Timing", "&8"),
    
    /**
     * Detaillierte State-Änderungen
     */
    STATE(DebugLevel.LEVEL_3, "State", "&9"),
    
    /**
     * Listener/Event-Handler Aktivitäten
     */
    LISTENER(DebugLevel.LEVEL_3, "Listener", "&0"),
    
    /**
     * Scheduler/Task-bezogene Informationen
     */
    TASK(DebugLevel.LEVEL_3, "Task", "&8"),
    
    /**
     * Vault/Economy-bezogene Transaktionen
     */
    ECONOMY(DebugLevel.LEVEL_3, "Economy", "&6"),
    
    /**
     * Permissions/Rechte-Prüfungen
     */
    PERMISSION(DebugLevel.LEVEL_3, "Perm", "&e");

    private final DebugLevel minLevel;
    private final String displayName;
    private final String colorCode;

    DebugCategory(DebugLevel minLevel, String displayName, String colorCode) {
        this.minLevel = minLevel;
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    /**
     * Die minimale Debug-Stufe, ab der diese Kategorie aktiv ist.
     */
    public DebugLevel getMinLevel() {
        return minLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    /**
     * Prüft, ob diese Kategorie bei der angegebenen Debug-Stufe aktiv ist.
     */
    public boolean isActiveAt(DebugLevel level) {
        return level.isAtLeast(this.minLevel);
    }

    /**
     * Versucht eine Kategorie anhand des Namens zu finden.
     */
    public static DebugCategory parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String upper = input.toUpperCase();
        for (DebugCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(upper) || 
                cat.displayName.equalsIgnoreCase(input)) {
                return cat;
            }
        }
        return null;
    }
}
