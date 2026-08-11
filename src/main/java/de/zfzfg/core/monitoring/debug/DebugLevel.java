package de.zfzfg.core.monitoring.debug;

/**
 * Debug-Stufen für das Plugin.
 *
 * Bewusst nur drei Werte: aus, normal, ausführlich. Mehr Abstufungen haben
 * sich als reine Konfigurationslast erwiesen, ohne dass sie ein Admin je
 * sinnvoll auseinanderhalten konnte.
 */
public enum DebugLevel {
    /**
     * Debug ist deaktiviert (Standard)
     */
    OFF(0, "Off", "level-off"),

    /**
     * Normale Debug-Ausgaben: Match-Ablauf, Equipment, Config-Laden.
     */
    BASIC(1, "Basic", "level-basic"),

    /**
     * Ausführlich: zusätzlich Teleport-Details, Spawn-Handling, interne Traces
     * und Stack-Traces bei abgefangenen Fehlern.
     */
    FULL(2, "Full", "level-full");

    private final int level;
    private final String displayName;
    private final String translationKey;

    DebugLevel(int level, String displayName, String translationKey) {
        this.level = level;
        this.displayName = displayName;
        this.translationKey = translationKey;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Sprachunabhängiger Name für Konsolen-Logs und Vergleiche.
     * Für Chat-Ausgaben stattdessen {@link #getTranslationKey()} über
     * messages.debug.enums.* auflösen.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Key unterhalb von messages.debug.enums.* für die übersetzte Anzeige.
     */
    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * Wert für den Config-Schlüssel settings.debug.
     */
    public String getConfigValue() {
        switch (this) {
            case BASIC: return "on";
            case FULL:  return "full";
            default:    return "off";
        }
    }

    /**
     * Prüft ob diese Debug-Stufe mindestens die angegebene Stufe hat.
     */
    public boolean isAtLeast(DebugLevel other) {
        return this.level >= other.level;
    }

    /**
     * Versucht eine Debug-Stufe anhand eines Command-Arguments oder eines
     * Config-Werts zu bestimmen. Toleriert Booleans aus der YAML sowie die
     * deutschen Schreibweisen.
     *
     * @return die Stufe oder {@code null} bei unbekannter Eingabe
     */
    public static DebugLevel parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        switch (input.trim().toLowerCase()) {
            case "off":
            case "aus":
            case "false":
            case "no":
            case "nein":
            case "0":
                return OFF;

            case "on":
            case "an":
            case "true":
            case "yes":
            case "ja":
            case "basic":
            case "normal":
            case "1":
                return BASIC;

            case "full":
            case "all":
            case "alle":
            case "verbose":
            case "ausführlich": // i18n-ignore: akzeptierte Eingabe, keine Anzeige
            case "ausfuehrlich":
            case "2":
                return FULL;

            default:
                return null;
        }
    }
}
