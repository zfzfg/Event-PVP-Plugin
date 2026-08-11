package de.zfzfg.core.inventory;

/**
 * Ergebnis eines Wiederherstellungsversuchs.
 *
 * <p>Bildet {@code RestoreResult} der InventoryBackup-API ab und ergaenzt zwei Faelle, die es
 * dort nicht gibt: {@link #UNAVAILABLE} (kein Provider aktiv) und {@link #FALLBACK_APPLIED}
 * (aus dem im Speicher gehaltenen Abzug wiederhergestellt, weil das Backup nicht lesbar war).</p>
 *
 * <p>Invariante I4 des Integrationsplans: dieses Ergebnis wird <b>immer</b> ausgewertet.
 * Nur bei {@link #isSuccess()} darf ausgeschuettet und die Guard-Sitzung geschlossen werden.</p>
 */
public enum RestoreOutcome {

    /** Direkt auf den Spieler angewendet. */
    APPLIED,
    /** Spieler offline - beim naechsten Join wird angewendet (persistent). */
    QUEUED_FOR_JOIN,
    /** Aus dem Speicher-Abzug wiederhergestellt, nicht aus der Datei. */
    FALLBACK_APPLIED,
    /** Backup existiert nicht (mehr). */
    NOT_FOUND,
    /** Ein Listener hat die Wiederherstellung abgelehnt. */
    CANCELLED,
    /** Fehler beim Lesen oder Anwenden - Details im Serverlog. */
    FAILED,
    /** Kein Inventar-Provider aktiv. */
    UNAVAILABLE;

    /** Wahr, wenn das Inventar angekommen ist oder garantiert noch ankommt. */
    public boolean isSuccess() {
        return this == APPLIED || this == QUEUED_FOR_JOIN || this == FALLBACK_APPLIED;
    }

    /** Wahr, wenn der Spieler das Inventar jetzt bereits traegt. */
    public boolean isApplied() {
        return this == APPLIED || this == FALLBACK_APPLIED;
    }
}
