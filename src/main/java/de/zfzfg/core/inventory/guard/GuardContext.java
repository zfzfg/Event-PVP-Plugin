package de.zfzfg.core.inventory.guard;

import java.util.Locale;

/** Wozu eine offene Inventar-Sitzung gehoert. */
public enum GuardContext {

    /** Spieler ist in einem PvP-Wager-Match. */
    PVP_MATCH,
    /** Spieler nimmt an einem Event teil. */
    EVENT,
    /** Ueber das Web-Panel ausgeloest. */
    WEB,
    /** Ueber einen Ingame-Befehl ausgeloest. */
    MANUAL;

    public static GuardContext from(String raw) {
        if (raw != null) {
            for (GuardContext value : values()) {
                if (value.name().equalsIgnoreCase(raw.trim())) {
                    return value;
                }
            }
        }
        return MANUAL;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
