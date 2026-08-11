package de.zfzfg.core.inventory;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Liest {@code settings.inventory-management} aus der config.yml.
 *
 * <p>Die Werte werden beim Laden validiert; ein unbekannter Provider faellt auf
 * {@link Mode#AUTO} zurueck und wird protokolliert, statt still ein anderes Verhalten
 * zu erzeugen.</p>
 */
public final class InventoryManagementConfig {

    private static final String ROOT = "settings.inventory-management.";

    /** Betriebsart der Inventarverwaltung. */
    public enum Mode {
        /** Sichern und Wiederherstellen ueber InventoryBackup. */
        AUTO("auto"),
        /**
         * Altwert, gleichbedeutend mit {@link #AUTO}.
         *
         * <p>Wird im Web-Panel nicht mehr angeboten - es gab nie einen Unterschied zu
         * {@link #AUTO}, nur zwei Eintraege, die dasselbe taten. Der Wert bleibt hier
         * stehen, damit eine bestehende config.yml nicht als ungueltig gemeldet wird;
         * die Migration schreibt ihn beim naechsten Start auf {@code auto} um.</p>
         */
        INVENTORYRESTORE("inventoryrestore"),
        /**
         * Legacy: Multiverse-Inventories uebernimmt den Inventartausch. Das Plugin sichert
         * und stellt nichts wieder her.
         */
        NONE("none");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Mode from(String raw) {
            if (raw != null) {
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                for (Mode mode : values()) {
                    if (mode.id.equals(normalized)) {
                        return mode;
                    }
                }
            }
            return null;
        }
    }

    /** Was geschieht, wenn ein Pre-Backup nicht geschrieben werden konnte. */
    public enum FailurePolicy {
        /** Match/Event-Beitritt abbrechen - das Inventar bleibt unangetastet. */
        ABORT,
        /** Fortfahren, den Speicher-Abzug als Rueckfallebene nutzen, Admin warnen. */
        WARN;

        public static FailurePolicy from(String raw) {
            return raw != null && "warn".equalsIgnoreCase(raw.trim()) ? WARN : ABORT;
        }
    }

    private final Mode mode;
    private final boolean restoreOnMatchEnd;
    private final boolean restoreOnEventEnd;
    private final boolean restoreOnRespawn;
    private final boolean restoreOnRejoin;
    private final FailurePolicy failurePolicy;
    private final boolean legacySafetyBackups;
    private final boolean cleanupAfterMatch;
    private final boolean guardEnabled;
    private final boolean restoreOrphansOnStart;
    private final boolean warnOnMultiverseInventories;
    private final boolean mviConflictGuard;
    private final int mviRestoreDelayTicks;
    private final boolean modeWasInvalid;

    public InventoryManagementConfig(FileConfiguration config) {
        String rawMode = config.getString(ROOT + "provider", Mode.AUTO.id());
        Mode parsed = Mode.from(rawMode);
        this.modeWasInvalid = parsed == null;
        this.mode = parsed == null ? Mode.AUTO : parsed;

        this.restoreOnMatchEnd = config.getBoolean(ROOT + "auto-restore-on-match-end", true);
        this.restoreOnEventEnd = config.getBoolean(ROOT + "auto-restore-on-event-end", true);
        this.restoreOnRespawn = config.getBoolean(ROOT + "auto-restore-on-respawn", true);
        this.restoreOnRejoin = config.getBoolean(ROOT + "auto-restore-on-rejoin", true);
        this.failurePolicy = FailurePolicy.from(config.getString(ROOT + "on-backup-failure", "abort"));
        this.legacySafetyBackups = config.getBoolean(ROOT + "legacy-safety-backups", true);
        this.cleanupAfterMatch = config.getBoolean(ROOT + "cleanup-backups-after-match", false);
        this.guardEnabled = config.getBoolean(ROOT + "guard.enabled", true);
        this.restoreOrphansOnStart = config.getBoolean(ROOT + "guard.restore-orphans-on-start", true);
        this.warnOnMultiverseInventories = config.getBoolean(ROOT + "warn-on-multiverse-inventories", true);
        this.mviConflictGuard = config.getBoolean(ROOT + "multiverse-inventories.conflict-guard", true);
        this.mviRestoreDelayTicks = Math.max(0,
                config.getInt(ROOT + "multiverse-inventories.restore-delay-ticks", 3));
    }

    public Mode mode() { return mode; }

    /** Wahr, wenn in der config.yml ein unbekannter Provider stand. */
    public boolean modeWasInvalid() { return modeWasInvalid; }

    /** Wahr, wenn das Plugin die Inventare selbst verwaltet (also nicht Legacy-Betrieb). */
    public boolean managedByPlugin() { return mode != Mode.NONE; }

    public boolean restoreOnMatchEnd() { return restoreOnMatchEnd && managedByPlugin(); }
    public boolean restoreOnEventEnd() { return restoreOnEventEnd && managedByPlugin(); }
    public boolean restoreOnRespawn() { return restoreOnRespawn && managedByPlugin(); }
    public boolean restoreOnRejoin() { return restoreOnRejoin && managedByPlugin(); }

    public FailurePolicy failurePolicy() { return failurePolicy; }

    /**
     * Ob im Legacy-Betrieb trotzdem Sicherungskopien angelegt werden.
     *
     * <p>Gilt nur fuer {@code provider: none}: den Inventartausch macht dort
     * Multiverse-Inventories, das Plugin legt aber vor jedem Arena- und Lobby-Teleport eine
     * Kopie ueber InventoryBackup an. Diese Kopien werden <b>nie automatisch</b>
     * zurueckgespielt - sie sind das Netz, aus dem ein Admin im Schadensfall von Hand
     * wiederherstellen kann.</p>
     */
    public boolean legacySafetyBackups() { return legacySafetyBackups; }

    public boolean cleanupAfterMatch() { return cleanupAfterMatch; }
    public boolean guardEnabled() { return guardEnabled && managedByPlugin(); }
    public boolean restoreOrphansOnStart() { return restoreOrphansOnStart; }
    public boolean warnOnMultiverseInventories() { return warnOnMultiverseInventories; }

    /**
     * Ob sich das Plugin gegen einen parallel laufenden Inventartausch wehrt.
     *
     * <p>Eingeschaltet heisst: nach jeder Wiederherstellung wird einen Tick spaeter geprueft,
     * ob der Zustand noch der gewollte ist, und ein Weltwechsel mit offener Sitzung wird
     * gegen fremde Aenderungen abgesichert. Ausgeschaltet gewinnt bei einem Konflikt, wer
     * zufaellig zuletzt schreibt.</p>
     */
    public boolean mviConflictGuard() { return mviConflictGuard; }

    /**
     * Ticks zwischen Rueckteleport und Wiederherstellung, wenn Multiverse-Inventories laeuft.
     *
     * <p>Reiner Puffer, kein Schutz: dass am Ende der richtige Zustand steht, sichert die
     * Nachkontrolle aus {@link #mviConflictGuard()}.</p>
     */
    public int mviRestoreDelayTicks() { return mviRestoreDelayTicks; }

    /** Frisch aus der aktuell geladenen config.yml lesen. */
    public static InventoryManagementConfig load(EventPlugin plugin) {
        return new InventoryManagementConfig(plugin.getCoreConfigManager().getConfig());
    }
}
