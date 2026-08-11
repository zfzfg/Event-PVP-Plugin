package de.zfzfg.core.location;

/** Warum eine Rueckkehr-Position hinterlegt wurde. */
public enum ReturnReason {

    /** Der Spieler wurde fuer ein Event in eine Lobby- oder Eventwelt gebracht. */
    EVENT("event"),

    /** Der Spieler wurde fuer ein PvP-Match in eine Arenawelt gebracht. */
    PVP_MATCH("pvp_match"),

    /** Herkunft unbekannt - z. B. ein Eintrag aus einer aelteren Fassung der Datei. */
    UNKNOWN("unknown");

    private final String id;

    ReturnReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Liest einen Wert aus der Datei; unbekannte Eingaben werden nicht verworfen. */
    public static ReturnReason from(String value) {
        if (value != null) {
            for (ReturnReason reason : values()) {
                if (reason.id.equalsIgnoreCase(value) || reason.name().equalsIgnoreCase(value)) {
                    return reason;
                }
            }
        }
        return UNKNOWN;
    }
}
