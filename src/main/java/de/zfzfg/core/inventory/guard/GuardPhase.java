package de.zfzfg.core.inventory.guard;

import java.util.Locale;

/**
 * Lebenszyklus einer offenen Inventar-Sitzung.
 *
 * <p>Die Phase liegt im Journal auf der Platte und nicht nur im {@code Match}-Objekt - nur so
 * ueberlebt sie einen Serverabsturz und der Wiederanlauf weiss, was noch zu tun ist.</p>
 */
public enum GuardPhase {

    /** Backup geschrieben, Spieler noch nicht in der Arena/Lobby. */
    BACKED_UP,
    /** Match/Event laeuft, der Spieler traegt das Kit. */
    ACTIVE,
    /** Wiederherstellung laeuft gerade - eigene Restores duerfen hier nicht blockiert werden. */
    RESTORING,
    /** Wiederherstellung ist fuer den naechsten Join eingereiht. */
    QUEUED,
    /** Backup nicht mehr auffindbar - braucht einen Admin. */
    ORPHANED;

    public static GuardPhase from(String raw) {
        if (raw != null) {
            for (GuardPhase value : values()) {
                if (value.name().equalsIgnoreCase(raw.trim())) {
                    return value;
                }
            }
        }
        return BACKED_UP;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
