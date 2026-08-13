package de.zfzfg.core.inventory;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provider-neutrale Sicht auf Inventar-Backups.
 *
 * <p>Bewusst Future-basiert: die dahinterliegende InventoryBackup-API arbeitet asynchron, und
 * ein synchrones Interface darueber zu legen wuerde zwangslaeufig in {@code join()} auf dem
 * Haupt-Thread enden - was dort einen Deadlock erzeugt.</p>
 *
 * <h2>Threading</h2>
 * Jedes zurueckgegebene Future wird <b>auf dem Haupt-Thread</b> komplettiert. In
 * {@code thenAccept} darf also direkt mit Bukkit gearbeitet werden. Nie {@code join()} oder
 * {@code get()} vom Haupt-Thread aufrufen.
 *
 * <p>Implementierungen werden ueber {@link InventoryBackupServiceFactory} gewaehlt und nicht
 * direkt instanziiert.</p>
 */
public interface InventoryBackupService {

    /** Ob dieser Provider tatsaechlich sichern und wiederherstellen kann. */
    boolean isAvailable();

    /** Kurzname fuer Logausgaben und das Web-Panel, z. B. {@code inventoryrestore}. */
    String providerName();

    /**
     * Sichert das Live-Inventar eines Spielers.
     *
     * <p>Der Abzug wird synchron genommen, das Schreiben laeuft asynchron. Der Aufrufer darf
     * unmittelbar nach dem Aufruf clearen (Invariante I2). Nur vom Haupt-Thread aufrufen.</p>
     */
    CompletableFuture<Optional<BackupRef>> backup(Player player, BackupContext context);

    /** Sichert einen selbst erstellten Abzug - funktioniert auch fuer Offline-Spieler. */
    CompletableFuture<Optional<BackupRef>> backup(UUID ownerId, String ownerName,
                                                  CapturedInventory snapshot, BackupContext context);

    /**
     * Stellt ein Backup bei einem Spieler wieder her. Ist er offline, wird die
     * Wiederherstellung persistent fuer den naechsten Join eingereiht.
     */
    CompletableFuture<RestoreOutcome> restore(UUID targetId, BackupRef ref, RestoreMode mode);

    /** Erzwingt den Join-Pfad, auch wenn der Spieler gerade online ist. */
    CompletableFuture<Boolean> queueOnJoin(UUID targetId, BackupRef ref, RestoreMode mode);

    /** Ob fuer diesen Spieler bereits eine Wiederherstellung eingereiht ist. */
    CompletableFuture<Boolean> hasPendingRestore(UUID targetId);

    /** Backups eines Spielers, neueste zuerst. {@code type} darf null sein. */
    CompletableFuture<List<BackupRef>> list(UUID ownerId, String type);

    /** Loest eine persistierte Backup-ID wieder zu einer Referenz auf. */
    CompletableFuture<Optional<BackupRef>> resolve(UUID ownerId, String backupId);

    /** Laedt die tatsaechlichen Items eines Backups. */
    CompletableFuture<Optional<CapturedInventory>> load(BackupRef ref);

    CompletableFuture<Boolean> delete(BackupRef ref);

    /**
     * Oeffnet die Vorschau-GUI. Nur vom Haupt-Thread.
     *
     * @return false, wenn der Provider keine Vorschau kann oder sie abgelehnt hat
     */
    // @loose-end(unused-api): preview() ist implementiert, aber kein Befehl und kein Endpunkt ruft es auf
    boolean preview(Player viewer, BackupRef ref);
}
