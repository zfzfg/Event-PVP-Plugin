package de.zfzfg.core.inventory;

import com.zfzfg.inventorybackup.api.*;
import com.zfzfg.inventorybackup.api.events.*;
import de.zfzfg.core.inventory.adapter.*;
import de.zfzfg.core.inventory.guard.*;
import de.zfzfg.core.reward.PendingPayoutListener;
import de.zfzfg.core.reward.PendingPayoutStore;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryApi2Test extends MockBukkitTestBase {
    @TempDir Path data;
    private InventoryBackupAPI api;
    private InventoryRestoreApiAdapter adapter;
    private InventoryGuard guard;
    private InventorySessionManager sessions;
    private InventoryBackupEvents events;
    private PendingPayoutStore payouts;
    private PlayerMock player;
    private BackupHandle handle;
    private BackupRef ref;

    @BeforeEach void setup() {
        plugin = mock(EventPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("Event-PVP-Plugin");
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getLogger()).thenReturn(testLogger());
        when(plugin.getConsoleMsg(anyString(), any())).thenReturn("inventory diagnostic");
        InventoryManagementConfig config = mock(InventoryManagementConfig.class);
        when(config.managedByPlugin()).thenReturn(true);
        when(config.cleanupAfterMatch()).thenReturn(true);
        when(plugin.getInventoryConfig()).thenReturn(config);
        var backend = MockBukkit.createMockPlugin("InventoryBackup");
        api = mock(InventoryBackupAPI.class);
        when(api.getApiVersion()).thenReturn(2);
        server.getServicesManager().register(InventoryBackupAPI.class, api, backend, ServicePriority.Normal);
        adapter = new InventoryRestoreApiAdapter(plugin);
        when(plugin.getInventoryBackupService()).thenReturn(adapter);
        guard = new InventoryGuard(plugin);
        guard.load();
        when(plugin.getInventoryGuard()).thenReturn(guard);
        sessions = new InventorySessionManager(plugin, guard);
        when(plugin.getInventorySessions()).thenReturn(sessions);
        events = new InventoryBackupEvents(plugin);
        player = createPlayer("Fighter");
        handle = new BackupHandle("original.yml", player.getUniqueId(), player.getName(), Instant.now(),
                "pvp-pre-match", plugin.getName(), Map.of());
        ref = new BackupRef(handle.ownerId(), handle.id(), handle.type(), handle.createdAt().toEpochMilli(), Map.of());
        when(api.getBackup(handle.ownerId(), handle.id())).thenReturn(CompletableFuture.completedFuture(Optional.of(handle)));
        when(api.getPendingRestore(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(api.deleteBackup(any())).thenReturn(CompletableFuture.completedFuture(true));
        payouts = mock(PendingPayoutStore.class);
        when(plugin.getPendingPayouts()).thenReturn(payouts);
    }

    private static Logger testLogger() {
        Logger logger = Logger.getLogger("InventoryApi2Test");
        logger.setLevel(java.util.logging.Level.OFF);
        return logger;
    }

    private void open(GuardPhase phase) {
        guard.open(player.getUniqueId(), GuardContext.PVP_MATCH, "match", handle.id(), "world");
        guard.phase(player.getUniqueId(), phase);
    }

    private BackupSnapshot snapshot(BackupHandle h) {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND, 7);
        return new BackupSnapshot(h, contents, new ItemStack[4], null, 12, .5f);
    }

    @ParameterizedTest @EnumSource(RestoreResult.class)
    void mapsEveryRestoreResult(RestoreResult result) {
        when(api.restore(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(result));
        assertThat(adapter.restore(player.getUniqueId(), ref, RestoreMode.all()).join().name()).isEqualTo(result.name());
    }

    @Test void rejectsOldOrAbsentProvider() {
        when(api.getApiVersion()).thenReturn(1);
        assertThat(adapter.isAvailable()).isFalse();
        assertThat(InventoryBackupServiceFactory.inventoryRestoreAvailable()).isFalse();
        assertThat(adapter.restore(player.getUniqueId(), ref, RestoreMode.all()).join()).isEqualTo(RestoreOutcome.UNAVAILABLE);
        server.getServicesManager().unregisterAll(server.getPluginManager().getPlugin("InventoryBackup"));
        assertThat(adapter.isAvailable()).isFalse();
        assertThat(adapter.resolve(player.getUniqueId(), handle.id())).isCompletedExceptionally();
    }

    @Test void readAndPendingErrorsAreNeverAbsence() {
        var error = new IOException("disk unreadable");
        when(api.listBackups(any(), any())).thenReturn(CompletableFuture.failedFuture(error));
        when(api.loadBackup(any())).thenReturn(CompletableFuture.failedFuture(error));
        when(api.getPendingRestore(any())).thenReturn(CompletableFuture.failedFuture(error));
        assertThat(adapter.list(player.getUniqueId(), null)).isCompletedExceptionally();
        assertThat(adapter.load(ref)).isCompletedExceptionally();
        assertThat(adapter.hasPendingRestore(player.getUniqueId())).isCompletedExceptionally();
        when(api.getBackup(any(), any())).thenReturn(CompletableFuture.failedFuture(error));
        assertThat(adapter.resolve(player.getUniqueId(), handle.id())).isCompletedExceptionally();
        assertThat(adapter.lastError()).contains("disk unreadable");
    }

    @Test void snapshotClonesAndRestoresAllSlotsExactlyOnce() {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Material.DIAMOND, i + 1));
        player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        player.setLevel(12); player.setExp(.5f);
        CapturedInventory saved = CapturedInventory.of(player);
        assertThat(saved.contents()).hasSize(36);
        assertThat(saved.armor()).hasSize(4);
        saved.contents()[0].setAmount(64);
        saved.armor()[0].setType(Material.AIR);
        saved.offhand().setType(Material.AIR);
        player.getInventory().clear(); player.setLevel(0);
        saved.applyTo(player, false);
        assertThat(player.getInventory().getStorageContents()).containsExactly(saved.contents());
        assertThat(player.getInventory().getArmorContents()).containsExactly(saved.armor());
        assertThat(player.getInventory().getItemInOffHand()).isEqualTo(saved.offhand());
        assertThat(player.getLevel()).isEqualTo(12);
        assertThat(player.getExp()).isEqualTo(.5f);
        assertThat(saved.contents()[0].getAmount()).isEqualTo(1);
        ItemStack[] input = saved.contents();
        CapturedInventory copy = new CapturedInventory(input, saved.armor(), saved.offhand(), 12, .5f);
        input[0].setAmount(63);
        assertThat(copy.contents()[0].getAmount()).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(value = RestoreOutcome.class, names = {"CANCELLED", "INCOMPATIBLE_VERSION", "INSUFFICIENT_SPACE"})
    void refusedRestoreNeverUsesMemoryFallback(RestoreOutcome outcome) {
        when(api.createBackup(eq(player), any())).thenReturn(CompletableFuture.completedFuture(Optional.of(handle)));
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND));
        sessions.begin(player, GuardContext.PVP_MATCH, "match", BackupContext.builder("pvp-pre-match").build(), null);
        player.getInventory().setItem(0, new ItemStack(Material.STONE));
        when(api.restore(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(RestoreResult.valueOf(outcome.name())));
        List<RestoreOutcome> results = new ArrayList<>();
        sessions.finish(player.getUniqueId(), results::add);
        assertThat(results).containsExactly(outcome);
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.STONE);
        assertThat(guard.get(player.getUniqueId()).phase()).isEqualTo(GuardPhase.ORPHANED);
        assertThat(guard.get(player.getUniqueId()).lastError()).isEqualTo(outcome.name());
        assertThat(sessions.hasFallback(player.getUniqueId())).isTrue();
        verify(api, never()).deleteBackup(any());
    }

    @Test void blocksForeignRestoreButAllowsExactAuthorizedRestore() {
        open(GuardPhase.ACTIVE);
        var foreign = new InventoryRestoreEvent(player, snapshot(handle), RestoreOptions.all());
        events.beforeRestore(foreign);
        assertThat(foreign.isCancelled()).isTrue();
        guard.phase(player.getUniqueId(), GuardPhase.RESTORING);
        assertThat(guard.authorizeRestore(player.getUniqueId(), ref)).isTrue();
        var own = new InventoryRestoreEvent(player, snapshot(handle), RestoreOptions.all());
        events.beforeRestore(own);
        assertThat(own.isCancelled()).isFalse();
        own.setCancelled(true); events.beforeRestore(own);
        assertThat(own.isCancelled()).isTrue();
        guard.endAuthorization(player.getUniqueId(), ref);
        var after = new InventoryRestoreEvent(player, snapshot(handle), RestoreOptions.all());
        events.beforeRestore(after);
        assertThat(after.isCancelled()).isTrue();
    }

    @Test void queuedRestoreCompletesOnceAndOnlyWhenMatchingAndComplete() {
        open(GuardPhase.QUEUED);
        var wrong = new BackupHandle("other.yml", handle.ownerId(), "Fighter", Instant.now(), "death", "other", Map.of());
        events.restored(new InventoryRestoredEvent(player, snapshot(wrong), RestoreOptions.all()));
        events.restored(new InventoryRestoredEvent(player, snapshot(handle), RestoreOptions.itemsOnly()));
        assertThat(guard.hasOpenSession(player.getUniqueId())).isTrue();
        events.restored(new InventoryRestoredEvent(player, snapshot(handle), RestoreOptions.all()));
        sessions.completeApplied(player.getUniqueId(), ref, () -> { throw new AssertionError("duplicate completion"); });
        events.restored(new InventoryRestoredEvent(player, snapshot(handle), RestoreOptions.all()));
        assertThat(guard.hasOpenSession(player.getUniqueId())).isFalse();
        verify(api, times(1)).deleteBackup(handle);
    }

    @Test void deletionRetainsRecoveryAndDoesNotStopMatch() {
        open(GuardPhase.ACTIVE);
        events.deleted(new BackupDeletedEvent(handle, BackupDeletedEvent.Reason.EXPIRED));
        var entry = guard.get(player.getUniqueId());
        assertThat(entry.backupId()).isEqualTo(handle.id());
        assertThat(entry.phase()).isEqualTo(GuardPhase.ACTIVE);
        assertThat(entry.backupDamaged()).isTrue();
        InventoryGuard reloaded = new InventoryGuard(plugin); reloaded.load();
        assertThat(reloaded.get(player.getUniqueId()).lastError()).isEqualTo("BACKUP_DELETED_EXPIRED");
        assertThat(reloaded.get(player.getUniqueId()).backupDamaged()).isTrue();
    }

    @Test void slowJoinRestorePreventsGuardReplayAndPayoutUntilCompletion() {
        open(GuardPhase.QUEUED);
        when(payouts.hasPending(player.getUniqueId())).thenReturn(true);
        when(api.getPendingRestore(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(
                new PendingRestore(player.getUniqueId(), handle, RestoreOptions.all(), Instant.now(), "Event-PVP-Plugin"))));
        guard.handleJoin(player);
        PendingPayoutListener.deliverWhenSafe(plugin, player.getUniqueId());
        verify(api, never()).restore(any(), any(), any());
        verify(payouts, never()).deliverAll(any());
        when(api.getPendingRestore(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        events.restored(new InventoryRestoredEvent(player, snapshot(handle), RestoreOptions.all()));
        tick();
        verify(payouts, times(1)).deliverAll(player);
    }

    @Test void failedPendingQueryDoesNotQueueOrPay() {
        open(GuardPhase.ACTIVE);
        when(api.getPendingRestore(any())).thenReturn(CompletableFuture.failedFuture(new IOException("pending disk")));
        sessions.queueForJoin(player.getUniqueId());
        verify(api, never()).queueRestoreOnJoin(any(), any(), any());
        assertThat(guard.hasOpenSession(player.getUniqueId())).isTrue();
        guard.close(player.getUniqueId());
        when(payouts.hasPending(player.getUniqueId())).thenReturn(true);
        PendingPayoutListener.deliverWhenSafe(plugin, player.getUniqueId());
        verify(payouts, never()).deliverAll(any());
    }

    @Test void queuedRestoreCannotReplaceARunningMatch() {
        open(GuardPhase.QUEUED);
        var matches = mock(de.zfzfg.pvpwager.managers.MatchManager.class);
        when(plugin.getMatchManager()).thenReturn(matches);
        when(matches.getMatchIdByPlayer(player.getUniqueId())).thenReturn(UUID.randomUUID());
        var restore = new InventoryRestoreEvent(player, snapshot(handle), RestoreOptions.all());
        events.beforeRestore(restore);
        assertThat(restore.isCancelled()).isTrue();
        events.restored(new InventoryRestoredEvent(player, snapshot(handle), RestoreOptions.all()));
        assertThat(guard.hasOpenSession(player.getUniqueId())).isTrue();
    }

    @Test void conflictingPendingRestoreIsNeverReplaced() {
        open(GuardPhase.ACTIVE);
        var other = new BackupHandle("other.yml", UUID.randomUUID(), "Other", Instant.now(), "death", "Other", Map.of());
        var pending = new PendingRestore(player.getUniqueId(), other, RestoreOptions.all(), Instant.now(), "Other");
        when(api.getPendingRestore(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(pending)));
        sessions.queueForJoin(player.getUniqueId());
        verify(api, never()).queueRestoreOnJoin(any(), any(), any());
        assertThat(guard.get(player.getUniqueId()).phase()).isEqualTo(GuardPhase.ORPHANED);
        assertThat(guard.get(player.getUniqueId()).lastError()).contains("CONFLICT");
    }

    @Test void failedBackupRetainsMemoryRecovery() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 8));
        when(api.createBackup(eq(player), any())).thenReturn(CompletableFuture.failedFuture(new IOException("write failed")));
        List<Boolean> persisted = new ArrayList<>();
        sessions.begin(player, GuardContext.PVP_MATCH, "match", BackupContext.builder("pvp-pre-match").build(), persisted::add);
        player.getInventory().setItem(0, new ItemStack(Material.STONE));
        List<RestoreOutcome> outcomes = new ArrayList<>();
        sessions.finish(player.getUniqueId(), outcomes::add);
        assertThat(persisted).containsExactly(false);
        assertThat(outcomes).containsExactly(RestoreOutcome.FALLBACK_APPLIED);
        assertThat(player.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.DIAMOND, 8));
        assertThat(guard.hasOpenSession(player.getUniqueId())).isFalse();
    }

    @Test void futureAndQueuedEventShareCompletionDuringVerification() {
        open(GuardPhase.QUEUED);
        var bridge = mock(de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge.class);
        when(bridge.conflictGuardActive()).thenReturn(true);
        when(plugin.getMviBridge()).thenReturn(bridge);
        when(payouts.hasPending(player.getUniqueId())).thenReturn(true);
        var success = new InventoryRestoredEvent(player, snapshot(handle), RestoreOptions.all());
        events.restored(success);
        AtomicInteger duplicate = new AtomicInteger();
        sessions.completeApplied(player.getUniqueId(), ref, duplicate::incrementAndGet);
        events.restored(success);
        assertThat(guard.tryBeginRestore(player.getUniqueId())).isFalse();
        assertThat(guard.hasOpenSession(player.getUniqueId())).isTrue();
        verify(payouts, never()).deliverAll(any());
        tick(2);
        assertThat(guard.hasOpenSession(player.getUniqueId())).isFalse();
        assertThat(duplicate).hasValue(0);
        verify(api, times(1)).deleteBackup(handle);
        verify(payouts, times(1)).deliverAll(player);
        events.deleted(new BackupDeletedEvent(handle, BackupDeletedEvent.Reason.API));
        assertThat(guard.openCount()).isZero();
    }

    @Test void backgroundBackupFailureCallbackUsesMainThread() throws Exception {
        CompletableFuture<Optional<BackupHandle>> write = new CompletableFuture<>();
        when(api.createBackup(eq(player), any())).thenReturn(write);
        List<Boolean> callbacks = new ArrayList<>();
        sessions.begin(player, GuardContext.PVP_MATCH, "match", BackupContext.builder("pvp-pre-match").build(), value -> {
            assertThat(server.isPrimaryThread()).isTrue(); callbacks.add(value);
        });
        Thread worker = new Thread(() -> write.completeExceptionally(new IOException("disk error")));
        worker.start(); worker.join();
        assertThat(callbacks).isEmpty();
        tick();
        assertThat(callbacks).containsExactly(false);
        assertThat(guard.get(player.getUniqueId()).lastError()).contains("BACKUP");
    }

    @Test void disabledPluginRetainsJournalWhenWriteCompletes() {
        CompletableFuture<Optional<BackupHandle>> write = new CompletableFuture<>();
        when(api.createBackup(eq(player), any())).thenReturn(write);
        AtomicInteger callbacks = new AtomicInteger();
        sessions.begin(player, GuardContext.PVP_MATCH, "match", BackupContext.builder("pvp-pre-match").build(), value -> callbacks.incrementAndGet());
        sessions.shutdown();
        when(plugin.isEnabled()).thenReturn(false);
        write.complete(Optional.of(handle));
        tick();
        assertThat(callbacks).hasValue(0);
        InventoryGuard reloaded = new InventoryGuard(plugin); reloaded.load();
        assertThat(reloaded.hasOpenSession(player.getUniqueId())).isTrue();
    }

    @ParameterizedTest @EnumSource(value = RestoreResult.class, names = {"INVALID_BACKUP", "INCOMPATIBLE_VERSION", "INSUFFICIENT_SPACE", "CANCELLED", "NOT_FOUND"})
    void webRestoreUsesSpecificLocalizedError(RestoreResult result) {
        when(api.restore(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(result));
        var web = new de.zfzfg.core.web.WebApiHandler(plugin, null);
        var response = web.restoreInventory(Map.of("player", player.getUniqueId().toString(), "backupId", handle.id()));
        String key = switch (result) {
            case INVALID_BACKUP -> "invalidBackup";
            case INCOMPATIBLE_VERSION -> "incompatibleVersion";
            case INSUFFICIENT_SPACE -> "insufficientSpace";
            case CANCELLED -> "cancelled";
            default -> "unknownBackup";
        };
        assertThat(response).containsEntry("success", false).containsEntry("messageKey", "inventory.error." + key);
        assertThat(response).containsEntry("outcome", result.name());
    }

    @Test void webUsesBackupNameIndexAndSeparatesLookupErrors() {
        when(api.resolvePlayerId("OldName")).thenReturn(CompletableFuture.completedFuture(Optional.of(player.getUniqueId())));
        when(api.listBackups(eq(player.getUniqueId()), isNull())).thenReturn(CompletableFuture.completedFuture(List.of(handle)));
        var web = new de.zfzfg.core.web.WebApiHandler(plugin, null);
        assertThat(web.listInventories(Map.of("player", "OldName"))).containsEntry("success", true);
        when(api.resolvePlayerId("OldName")).thenReturn(CompletableFuture.failedFuture(new IOException("index unreadable")));
        assertThat(web.listInventories(Map.of("player", "OldName"))).containsEntry("messageKey", "inventory.error.lookupFailed");
    }

    @Test void dispatchesBackgroundAndLateCallbacksAndStopsOnDisable() throws Exception {
        AtomicInteger ran = new AtomicInteger();
        var executor = InventoryTasks.executor(plugin);
        Thread worker = new Thread(() -> CompletableFuture.completedFuture(true).thenAcceptAsync(value -> {
            assertThat(server.isPrimaryThread()).isTrue(); ran.incrementAndGet();
        }, executor));
        worker.start(); worker.join();
        assertThat(ran).hasValue(0);
        tick(); assertThat(ran).hasValue(1);
        CompletableFuture<Boolean> failed = new CompletableFuture<>();
        failed.exceptionallyAsync(error -> { assertThat(server.isPrimaryThread()).isTrue(); ran.incrementAndGet(); return false; }, executor);
        Thread failure = new Thread(() -> failed.completeExceptionally(new IOException("disk")));
        failure.start(); failure.join(); tick(); assertThat(ran).hasValue(2);
        open(GuardPhase.ACTIVE);
        when(plugin.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> executor.execute(ran::incrementAndGet)).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        assertThat(guard.hasOpenSession(player.getUniqueId())).isTrue();
    }
}
