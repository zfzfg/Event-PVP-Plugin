package de.zfzfg.core.inventory.adapter;

import com.zfzfg.inventorybackup.api.BackupHandle;
import com.zfzfg.inventorybackup.api.BackupRequest;
import com.zfzfg.inventorybackup.api.BackupSnapshot;
import com.zfzfg.inventorybackup.api.InventoryBackupAPI;
import com.zfzfg.inventorybackup.api.InventoryBackupProvider;
import com.zfzfg.inventorybackup.api.RestoreOptions;
import com.zfzfg.inventorybackup.api.RestoreResult;
import de.zfzfg.core.inventory.BackupContext;
import de.zfzfg.core.inventory.BackupRef;
import de.zfzfg.core.inventory.CapturedInventory;
import de.zfzfg.core.inventory.InventoryBackupService;
import de.zfzfg.core.inventory.RestoreMode;
import de.zfzfg.core.inventory.RestoreOutcome;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bindet das Plugin {@code InventoryBackup} (InventoryRestore) an.
 *
 * <p>Diese Klasse referenziert die API-Typen direkt und wird deshalb erst geladen, wenn
 * {@link de.zfzfg.core.inventory.InventoryBackupServiceFactory} festgestellt hat, dass das
 * Plugin laeuft. Fehlt es, wird sie nie beruehrt und der fehlende Klassenpfad faellt nicht auf.</p>
 *
 * <p>Die API-Instanz wird bewusst <b>nicht</b> in einem Feld gehalten (Invariante I8): ein
 * {@code /reload} ersetzt die Registrierung, und ein zwischengespeicherter Verweis zeigte
 * danach auf eine tote Instanz.</p>
 */
public final class InventoryRestoreApiAdapter implements InventoryBackupService {

    private final EventPlugin plugin;

    public InventoryRestoreApiAdapter(EventPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ob die API-Klassen ueberhaupt auf dem Klassenpfad liegen. */
    public static boolean classesPresent() {
        try {
            Class.forName("com.zfzfg.inventorybackup.api.InventoryBackupProvider");  // i18n-ignore: voll qualifizierter Klassenname der InventoryBackup-API
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** API-Revision, die das laufende Plugin implementiert, oder -1. */
    public static int runningApiVersion() {
        Optional<InventoryBackupAPI> api = InventoryBackupProvider.getOptional();
        return api.map(InventoryBackupAPI::getApiVersion).orElse(-1);
    }

    /** Revision, gegen die dieses Plugin kompiliert wurde. */
    public static int compiledApiVersion() {
        return InventoryBackupAPI.API_VERSION;
    }

    private Optional<InventoryBackupAPI> api() {
        try {
            return InventoryBackupProvider.getOptional();
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return api().isPresent();
    }

    @Override
    public String providerName() {
        return "inventoryrestore";
    }

    // ------------------------------------------------------------------ create

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(Player player, BackupContext context) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return api.get().createBackup(player, toRequest(context))
                .thenApply(handle -> handle.map(InventoryRestoreApiAdapter::toRef))
                .exceptionally(t -> {
                    plugin.getLogger().warning(plugin.getConsoleMsg("inventory-backup-failed",
                            "player", player.getName(), "error", String.valueOf(t.getMessage())));
                    return Optional.empty();
                });
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(UUID ownerId, String ownerName,
                                                         CapturedInventory snapshot,
                                                         BackupContext context) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BackupSnapshot apiSnapshot = new BackupSnapshot(null, snapshot.contents(), snapshot.armor(),
                snapshot.offhand(), snapshot.level(), snapshot.exp());
        return api.get().createBackup(ownerId, ownerName, apiSnapshot, toRequest(context))
                .thenApply(handle -> handle.map(InventoryRestoreApiAdapter::toRef))
                .exceptionally(t -> {
                    plugin.getLogger().warning(plugin.getConsoleMsg("inventory-backup-failed",
                            "player", String.valueOf(ownerName), "error", String.valueOf(t.getMessage())));
                    return Optional.empty();
                });
    }

    // ----------------------------------------------------------------- restore

    @Override
    public CompletableFuture<RestoreOutcome> restore(UUID targetId, BackupRef ref, RestoreMode mode) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(RestoreOutcome.UNAVAILABLE);
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> api.get().restore(targetId, h, toOptions(mode))
                                .thenApply(InventoryRestoreApiAdapter::toOutcome))
                        .orElse(CompletableFuture.completedFuture(RestoreOutcome.NOT_FOUND)))
                .exceptionally(t -> {
                    plugin.getLogger().warning(plugin.getConsoleMsg("inventory-restore-failed",
                            "player", targetId.toString(), "error", String.valueOf(t.getMessage())));
                    return RestoreOutcome.FAILED;
                });
    }

    @Override
    public CompletableFuture<Boolean> queueOnJoin(UUID targetId, BackupRef ref, RestoreMode mode) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> api.get().queueRestoreOnJoin(targetId, h, toOptions(mode)))
                        .orElse(CompletableFuture.completedFuture(false)))
                .exceptionally(t -> false);
    }

    @Override
    public CompletableFuture<Boolean> hasPendingRestore(UUID targetId) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return api.get().getPendingRestore(targetId)
                .thenApply(Optional::isPresent)
                .exceptionally(t -> false);
    }

    // -------------------------------------------------------------------- read

    @Override
    public CompletableFuture<List<BackupRef>> list(UUID ownerId, String type) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        return api.get().listBackups(ownerId, type).thenApply(handles -> {
            List<BackupRef> refs = new ArrayList<>(handles.size());
            for (BackupHandle handle : handles) {
                refs.add(toRef(handle));
            }
            return refs;
        }).exceptionally(t -> new ArrayList<>());
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> resolve(UUID ownerId, String backupId) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return api.get().getBackup(ownerId, backupId)
                .thenApply(handle -> handle.map(InventoryRestoreApiAdapter::toRef))
                .exceptionally(t -> Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<CapturedInventory>> load(BackupRef ref) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> api.get().loadBackup(h))
                        .orElse(CompletableFuture.completedFuture(Optional.empty())))
                .thenApply(snapshot -> snapshot.map(s -> new CapturedInventory(
                        s.contents(), s.armor(), s.offhand(), s.level(), s.exp())))
                .exceptionally(t -> Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> delete(BackupRef ref) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> api.get().deleteBackup(h))
                        .orElse(CompletableFuture.completedFuture(false)))
                .exceptionally(t -> false);
    }

    @Override
    public boolean preview(Player viewer, BackupRef ref) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return false;
        }
        // openPreview braucht den echten Handle; das Aufloesen ist asynchron, das Oeffnen
        // danach wieder Haupt-Thread - genau das leistet der Completion-Hop der API.
        withHandle(api.get(), ref).thenAccept(handle ->
                handle.ifPresent(h -> api.get().openPreview(viewer, h)));
        return true;
    }

    // ------------------------------------------------------------------ mapping

    /**
     * Besorgt den API-Handle zu einer Referenz.
     *
     * <p>Immer ueber {@code getBackup} und nie aus einem gecachten Objekt: eine Referenz kann
     * aus dem Guard-Journal stammen und damit einen Serverneustart alt sein.</p>
     */
    private CompletableFuture<Optional<BackupHandle>> withHandle(InventoryBackupAPI api, BackupRef ref) {
        return api.getBackup(ref.ownerId(), ref.backupId());
    }

    private BackupRequest toRequest(BackupContext context) {
        BackupRequest.Builder builder = BackupRequest.builder()
                .type(context.type())
                .sourcePlugin(plugin);
        for (Map.Entry<String, String> entry : context.metadata().entrySet()) {
            builder.metadata(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static RestoreOptions toOptions(RestoreMode mode) {
        return RestoreOptions.builder()
                .contents(mode.contents())
                .armor(mode.armor())
                .offhand(mode.offhand())
                .level(mode.level())
                .exp(mode.exp())
                .clearBefore(mode.clearBefore())
                .dropOverflow(mode.dropOverflow())
                .build();
    }

    private static BackupRef toRef(BackupHandle handle) {
        return new BackupRef(handle.ownerId(), handle.id(), handle.type(),
                handle.createdAt().toEpochMilli(), handle.metadata());
    }

    private static RestoreOutcome toOutcome(RestoreResult result) {
        if (result == null) {
            return RestoreOutcome.FAILED;
        }
        switch (result) {
            case APPLIED:         return RestoreOutcome.APPLIED;
            case QUEUED_FOR_JOIN: return RestoreOutcome.QUEUED_FOR_JOIN;
            case NOT_FOUND:       return RestoreOutcome.NOT_FOUND;
            case CANCELLED:       return RestoreOutcome.CANCELLED;
            default:              return RestoreOutcome.FAILED;
        }
    }
}
