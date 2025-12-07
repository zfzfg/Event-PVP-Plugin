package de.zfzfg.core.monitoring.debug;

/**
 * Ausgabe-Ziele für Debug-Nachrichten.
 */
public enum DebugOutput {
    /**
     * Ausgabe nur in der Konsole/Terminal
     */
    CONSOLE("Konsole"),
    
    /**
     * Ausgabe nur im Chat an berechtigte Spieler
     */
    CHAT("Chat"),
    
    /**
     * Ausgabe sowohl in der Konsole als auch im Chat
     */
    BOTH("Beides");

    private final String displayName;

    DebugOutput(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean includesConsole() {
        return this == CONSOLE || this == BOTH;
    }

    public boolean includesChat() {
        return this == CHAT || this == BOTH;
    }

    /**
     * Versucht einen Output-Typ anhand des Namens zu finden.
     */
    public static DebugOutput parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String lower = input.toLowerCase();
        switch (lower) {
            case "console":
            case "konsole":
            case "terminal":
            case "log":
                return CONSOLE;
            case "chat":
            case "ingame":
            case "spieler":
                return CHAT;
            case "both":
            case "beides":
            case "alle":
            case "all":
                return BOTH;
        }
        // Versuche direkten Namen
        for (DebugOutput out : values()) {
            if (out.name().equalsIgnoreCase(input)) {
                return out;
            }
        }
        return null;
    }
}
