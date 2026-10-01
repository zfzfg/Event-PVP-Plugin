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
    private volatile String lastError = "";

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
        try {
            return InventoryBackupProvider.getOptional().map(InventoryBackupAPI::getApiVersion).orElse(-1);
        } catch (LinkageError | IllegalStateException e) { return -1; }
    }

    /** Revision, gegen die dieses Plugin kompiliert wurde. */
    public static int compiledApiVersion() {
        return InventoryBackupAPI.API_VERSION;
    }

    private Optional<InventoryBackupAPI> api() {
        try {
            return InventoryBackupProvider.getOptional().filter(a -> a.getApiVersion() >= 2
                    && plugin.getServer().getPluginManager().isPluginEnabled("InventoryBackup"));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private InventoryBackupAPI requireApi() {
        return api().orElseThrow(() -> new IllegalStateException("InventoryBackup API 2 unavailable"));
    }

    @Override public int apiVersion() { return runningApiVersion(); }
    @Override public String lastError() { return lastError; }

    private void recordError(Throwable error) {
        lastError = String.valueOf(error.getMessage());
        plugin.getLogger().log(java.util.logging.Level.WARNING, "[Inventory API] " + lastError, error);
    }

    private <T> CompletableFuture<T> checked(CompletableFuture<T> future) {
        return future.whenComplete((value, error) -> { if (error != null) recordError(error); });
    }

    private <T> CompletableFuture<T> unavailable() {
        return CompletableFuture.failedFuture(new IllegalStateException("InventoryBackup API 2 unavailable"));
    }

    @Override public CompletableFuture<Optional<UUID>> resolvePlayerId(String name) {
        return api().map(a -> checked(a.resolvePlayerId(name))).orElseGet(this::unavailable);
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
        if (api.isEmpty()) return unavailable();
        return checked(api.get().createBackup(player, toRequest(context))
                .thenApply(handle -> handle.map(InventoryRestoreApiAdapter::toRef)));
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> backup(UUID ownerId, String ownerName,
                                                         CapturedInventory snapshot, BackupContext context) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) return unavailable();
        BackupSnapshot apiSnapshot = new BackupSnapshot(null, snapshot.contents(), snapshot.armor(),
                snapshot.offhand(), snapshot.level(), snapshot.exp());
        return checked(api.get().createBackup(ownerId, ownerName, apiSnapshot, toRequest(context))
                .thenApply(handle -> handle.map(InventoryRestoreApiAdapter::toRef)));
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
                        .map(h -> restoreAuthorized(targetId, ref, h, mode)
                                .thenApply(result -> {
                                    RestoreOutcome outcome = toOutcome(result);
                                    if (!outcome.isSuccess()) lastError = outcome.name();
                                    return outcome;
                                }))
                        .orElse(CompletableFuture.completedFuture(RestoreOutcome.NOT_FOUND)))
                .exceptionallyAsync(t -> {
                    plugin.getLogger().warning(plugin.getConsoleMsg("inventory-restore-failed",
                            "player", targetId.toString(), "error", String.valueOf(t.getMessage())));
                    recordError(t);
                    return RestoreOutcome.FAILED;
                }, de.zfzfg.core.inventory.InventoryTasks.executor(plugin));
    }

    @Override
    public CompletableFuture<Boolean> queueOnJoin(UUID targetId, BackupRef ref, RestoreMode mode) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return unavailable();
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> requireApi().getPendingRestore(targetId)
                                .thenCompose(pending -> {
                                    if (pending.isPresent()) {
                                        boolean matches = pending.get().handle().ownerId().equals(h.ownerId())
                                                && pending.get().handle().id().equals(h.id())
                                                && sameOptions(pending.get().options(), toOptions(mode));
                                        if (!matches) lastError = "PENDING_RESTORE_CONFLICT";
                                        return CompletableFuture.completedFuture(matches);
                                    }
                                    return requireApi().queueRestoreOnJoin(targetId, h, toOptions(mode));
                                }))
                        .orElse(CompletableFuture.completedFuture(false)))
                .whenComplete((v, t) -> { if (t != null) recordError(t); });
    }

    @Override
    public CompletableFuture<Boolean> hasPendingRestore(UUID targetId) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return unavailable();
        }
        return api.get().getPendingRestore(targetId)
                .thenApply(Optional::isPresent)
                .whenComplete((v, t) -> { if (t != null) recordError(t); });
    }

    // -------------------------------------------------------------------- read

    @Override
    public CompletableFuture<List<BackupRef>> list(UUID ownerId, String type) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return unavailable();
        }
        return api.get().listBackups(ownerId, type).thenApply(handles -> {
            List<BackupRef> refs = new ArrayList<>(handles.size());
            for (BackupHandle handle : handles) {
                refs.add(toRef(handle));
            }
            return refs;
        }).whenComplete((v, t) -> { if (t != null) recordError(t); });
    }

    @Override
    public CompletableFuture<Optional<BackupRef>> resolve(UUID ownerId, String backupId) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return unavailable();
        }
        return api.get().getBackup(ownerId, backupId)
                .thenApply(handle -> handle.map(InventoryRestoreApiAdapter::toRef))
                .whenComplete((v, t) -> { if (t != null) recordError(t); });
    }

    @Override
    public CompletableFuture<Optional<CapturedInventory>> load(BackupRef ref) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return unavailable();
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> requireApi().loadBackup(h))
                        .orElse(CompletableFuture.completedFuture(Optional.empty())))
                .thenApply(snapshot -> snapshot.map(s -> new CapturedInventory(
                        s.contents(), s.armor(), s.offhand(), s.level(), s.exp())))
                .whenComplete((v, t) -> { if (t != null) recordError(t); });
    }

    @Override
    public CompletableFuture<Boolean> delete(BackupRef ref) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return unavailable();
        }
        return withHandle(api.get(), ref)
                .thenCompose(handle -> handle
                        .map(h -> requireApi().deleteBackup(h))
                        .orElse(CompletableFuture.completedFuture(false)))
                .whenComplete((v, t) -> { if (t != null) recordError(t); });
    }

    @Override
    public boolean preview(Player viewer, BackupRef ref) {
        Optional<InventoryBackupAPI> api = api();
        if (api.isEmpty()) {
            return false;
        }
        // openPreview braucht den echten Handle; das Aufloesen ist asynchron, das Oeffnen
        // danach wieder Haupt-Thread - genau das leistet der Completion-Hop der API.
        withHandle(api.get(), ref).thenAcceptAsync(handle ->
                handle.ifPresent(h -> api().ifPresent(current -> current.openPreview(viewer, h))),
                de.zfzfg.core.inventory.InventoryTasks.executor(plugin)).exceptionally(t -> {
                    recordError(t); return null;
                });
        return true;
    }

    private CompletableFuture<RestoreResult> restoreAuthorized(UUID targetId, BackupRef ref,
                                                               BackupHandle handle, RestoreMode mode) {
        CompletableFuture<RestoreResult> result = new CompletableFuture<>();
        try {
            de.zfzfg.core.inventory.InventoryTasks.executor(plugin).execute(() -> {
                var guard = plugin.getInventoryGuard();
                boolean authorized = guard != null && guard.authorizeRestore(targetId, ref);
                try {
                    restoreWithoutOverwritingPending(targetId, handle, mode)
                            .whenComplete((value, error) -> {
                                if (authorized) guard.endAuthorization(targetId, ref);
                                if (error != null) result.completeExceptionally(error);
                                else result.complete(value);
                            });
                } catch (Throwable error) {
                    if (authorized) guard.endAuthorization(targetId, ref);
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) { result.completeExceptionally(error); }
        return result;
    }

    private CompletableFuture<RestoreResult> restoreWithoutOverwritingPending(UUID targetId,
                                                                              BackupHandle handle, RestoreMode mode) {
        var player = org.bukkit.Bukkit.getPlayer(targetId);
        if (player != null && player.isOnline())
            return requireApi().restore(targetId, handle, toOptions(mode));
        return requireApi().getPendingRestore(targetId).thenComposeAsync(pending -> {
            if (pending.isPresent()) {
                boolean matches = pending.get().handle().ownerId().equals(handle.ownerId())
                        && pending.get().handle().id().equals(handle.id())
                        && sameOptions(pending.get().options(), toOptions(mode));
                return CompletableFuture.completedFuture(matches ? RestoreResult.QUEUED_FOR_JOIN : RestoreResult.CANCELLED);
            }
            return requireApi().restore(targetId, handle, toOptions(mode));
        }, de.zfzfg.core.inventory.InventoryTasks.executor(plugin));
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

    private static boolean sameOptions(RestoreOptions left, RestoreOptions right) {
        return left.contents() == right.contents() && left.armor() == right.armor()
                && left.offhand() == right.offhand() && left.level() == right.level()
                && left.exp() == right.exp() && left.clearBefore() == right.clearBefore()
                && left.dropOverflow() == right.dropOverflow();
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
            case INVALID_BACKUP: return RestoreOutcome.INVALID_BACKUP;
            case INCOMPATIBLE_VERSION: return RestoreOutcome.INCOMPATIBLE_VERSION;
            case INSUFFICIENT_SPACE: return RestoreOutcome.INSUFFICIENT_SPACE;
            case FAILED: return RestoreOutcome.FAILED;
            default: return RestoreOutcome.FAILED;
        }
    }
}
