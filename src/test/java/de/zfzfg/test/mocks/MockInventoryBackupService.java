package de.zfzfg.test.mocks;

import de.zfzfg.core.inventory.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MockInventoryBackupService implements InventoryBackupService {

    private final Map<UUID, List<BackupRef>> playerBackups = new ConcurrentHashMap<>();
    private final Map<String, CapturedInventory> storedSnapshots = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRestores = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(Player player, BackupContext context) {
        if (player == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CapturedInventory snapshot = CapturedInventory.of(player);
        return backup(player.getUniqueId(), player.getName(), snapshot, context);
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(UUID ownerId, String ownerName,
                                                          CapturedInventory snapshot, BackupContext context) {
        String backupId = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        String type = context != null ? context.type() : "test";
        Map<String, String> meta = context != null ? context.metadata() : Collections.emptyMap();
        BackupRef ref = new BackupRef(ownerId, backupId, type, System.currentTimeMillis(), meta);
        
        storedSnapshots.put(backupId, snapshot);
        playerBackups.computeIfAbsent(ownerId, k -> Collections.synchronizedList(new ArrayList<>())).add(0, ref);

        return CompletableFuture.completedFuture(Optional.of(ref));
    }

    @Override
    public CompletableFuture<RestoreOutcome> restore(UUID targetId, BackupRef ref, RestoreMode mode) {
        if (ref == null) {
            return CompletableFuture.completedFuture(RestoreOutcome.NOT_FOUND);
        }
        CapturedInventory snapshot = storedSnapshots.get(ref.backupId());
        if (snapshot == null) {
            return CompletableFuture.completedFuture(RestoreOutcome.NOT_FOUND);
        }

        Player player = Bukkit.getPlayer(targetId);
        if (player == null || !player.isOnline()) {
            pendingRestores.add(targetId);
            return CompletableFuture.completedFuture(RestoreOutcome.QUEUED_FOR_JOIN);
        }

        snapshot.applyTo(player, true);
        pendingRestores.remove(targetId);
        return CompletableFuture.completedFuture(RestoreOutcome.APPLIED);
    }

    @Override
    public CompletableFuture<Boolean> queueOnJoin(UUID targetId, BackupRef ref, RestoreMode mode) {
        pendingRestores.add(targetId);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> hasPendingRestore(UUID targetId) {
        return CompletableFuture.completedFuture(pendingRestores.contains(targetId));
    }

    @Override
    public CompletableFuture<List<BackupRef>> list(UUID ownerId, String type) {
        List<BackupRef> list = playerBackups.getOrDefault(ownerId, Collections.emptyList());
        if (type == null) {
            return CompletableFuture.completedFuture(new ArrayList<>(list));
        }
        List<BackupRef> filtered = new ArrayList<>();
        for (BackupRef r : list) {
            if (type.equalsIgnoreCase(r.type())) {
                filtered.add(r);
            }
        }
        return CompletableFuture.completedFuture(filtered);
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> resolve(UUID ownerId, String backupId) {
        List<BackupRef> list = playerBackups.getOrDefault(ownerId, Collections.emptyList());
        for (BackupRef r : list) {
            if (r.backupId().equalsIgnoreCase(backupId)) {
                return CompletableFuture.completedFuture(Optional.of(r));
            }
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<CapturedInventory>> load(BackupRef ref) {
        if (ref == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(storedSnapshots.get(ref.backupId())));
    }

    @Override
    public CompletableFuture<Boolean> delete(BackupRef ref) {
        if (ref == null) return CompletableFuture.completedFuture(false);
        storedSnapshots.remove(ref.backupId());
        List<BackupRef> list = playerBackups.get(ref.ownerId());
        if (list != null) {
            list.remove(ref);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public boolean preview(Player viewer, BackupRef ref) {
        return true;
    }
}
