package de.zfzfg.core.inventory.adapter;

import de.zfzfg.core.inventory.BackupContext;
import de.zfzfg.core.inventory.BackupRef;
import de.zfzfg.core.inventory.CapturedInventory;
import de.zfzfg.core.inventory.InventoryBackupService;
import de.zfzfg.core.inventory.RestoreMode;
import de.zfzfg.core.inventory.RestoreOutcome;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Der Provider fuer {@code provider: none} - der Legacy-Betrieb mit Multiverse-Inventories.
 *
 * <p>Sichert nichts und stellt nichts wieder her. In dieser Betriebsart uebernimmt weiterhin
 * Multiverse-Inventories den Inventartausch beim Weltwechsel, und der alte
 * {@code InventorySnapshotStorage} schreibt seine Notfall-Snapshots wie bisher.</p>
 */
public final class NoOpInventoryBackupAdapter implements InventoryBackupService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String providerName() {
        return "none";
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(Player player, BackupContext context) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(UUID ownerId, String ownerName,
                                                         CapturedInventory snapshot,
                                                         BackupContext context) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<RestoreOutcome> restore(UUID targetId, BackupRef ref, RestoreMode mode) {
        return CompletableFuture.completedFuture(RestoreOutcome.UNAVAILABLE);
    }

    @Override
    public CompletableFuture<Boolean> queueOnJoin(UUID targetId, BackupRef ref, RestoreMode mode) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> hasPendingRestore(UUID targetId) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<List<BackupRef>> list(UUID ownerId, String type) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> resolve(UUID ownerId, String backupId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<CapturedInventory>> load(BackupRef ref) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> delete(BackupRef ref) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public boolean preview(Player viewer, BackupRef ref) {
        return false;
    }
}
