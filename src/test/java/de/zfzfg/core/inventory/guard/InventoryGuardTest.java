package de.zfzfg.core.inventory.guard;

import de.zfzfg.core.inventory.BackupRef;
import de.zfzfg.core.inventory.InventoryBackupService;
import de.zfzfg.core.inventory.InventoryManagementConfig;
import de.zfzfg.core.inventory.RestoreMode;
import de.zfzfg.core.inventory.RestoreOutcome;
import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryGuardTest {

    @TempDir
    Path tempDir;

    private EventPlugin plugin;
    private InventoryGuard guard;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("InventoryGuardTest"));
        when(plugin.getConsoleMsg(any(), any())).thenReturn("console msg");

        guard = new InventoryGuard(plugin);
        guard.load();
    }

    @Test
    @DisplayName("open, get, and close session")
    void testOpenAndClose() {
        UUID playerId = UUID.randomUUID();

        assertThat(guard.hasOpenSession(playerId)).isFalse();
        assertThat(guard.openCount()).isZero();

        boolean opened = guard.open(playerId, GuardContext.PVP_MATCH, "match-123", "backup-456", "world_pvp");
        assertThat(opened).isTrue();
        assertThat(guard.hasOpenSession(playerId)).isTrue();
        assertThat(guard.openCount()).isEqualTo(1);

        GuardEntry entry = guard.get(playerId);
        assertThat(entry).isNotNull();
        assertThat(entry.playerId()).isEqualTo(playerId);
        assertThat(entry.context()).isEqualTo(GuardContext.PVP_MATCH);
        assertThat(entry.refId()).isEqualTo("match-123");
        assertThat(entry.backupId()).isEqualTo("backup-456");
        assertThat(entry.phase()).isEqualTo(GuardPhase.BACKED_UP);
        assertThat(entry.originWorld()).isEqualTo("world_pvp");
        assertThat(entry.payoutDone()).isFalse();

        // Second open for the same player must return false (Invariant I7)
        boolean secondOpen = guard.open(playerId, GuardContext.EVENT, "event-789", "backup-999", "world_event");
        assertThat(secondOpen).isFalse();

        // Close session
        boolean closed = guard.close(playerId);
        assertThat(closed).isTrue();
        assertThat(guard.hasOpenSession(playerId)).isFalse();
        assertThat(guard.openCount()).isZero();
    }

    @Test
    @DisplayName("openWithoutBackup and attachBackup")
    void testOpenWithoutBackupAndAttach() {
        UUID playerId = UUID.randomUUID();

        boolean opened = guard.openWithoutBackup(playerId, GuardContext.EVENT, "event-1", "world");
        assertThat(opened).isTrue();

        GuardEntry entry = guard.get(playerId);
        assertThat(entry.hasBackup()).isFalse();
        assertThat(entry.backupId()).isNull();

        guard.attachBackup(playerId, "new-backup-id");
        assertThat(entry.hasBackup()).isTrue();
        assertThat(entry.backupId()).isEqualTo("new-backup-id");
    }

    @Test
    @DisplayName("phase transitions")
    void testPhaseTransitions() {
        UUID playerId = UUID.randomUUID();
        guard.open(playerId, GuardContext.PVP_MATCH, "ref", "backup", "world");

        guard.phase(playerId, GuardPhase.ACTIVE);
        assertThat(guard.get(playerId).phase()).isEqualTo(GuardPhase.ACTIVE);

        guard.phase(playerId, GuardPhase.ORPHANED);
        assertThat(guard.get(playerId).phase()).isEqualTo(GuardPhase.ORPHANED);
    }

    @Test
    @DisplayName("tryBeginRestore exactly-once protection")
    void testTryBeginRestoreExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        guard.open(playerId, GuardContext.PVP_MATCH, "ref", "backup", "world");

        // First call should succeed
        boolean firstCall = guard.tryBeginRestore(playerId);
        assertThat(firstCall).isTrue();
        assertThat(guard.get(playerId).phase()).isEqualTo(GuardPhase.RESTORING);

        // Second call while in RESTORING phase must fail
        boolean secondCall = guard.tryBeginRestore(playerId);
        assertThat(secondCall).isFalse();

        // Release restore to BACKED_UP
        guard.releaseRestore(playerId, GuardPhase.BACKED_UP);
        assertThat(guard.get(playerId).phase()).isEqualTo(GuardPhase.BACKED_UP);

        // Now tryBeginRestore should succeed again
        boolean thirdCall = guard.tryBeginRestore(playerId);
        assertThat(thirdCall).isTrue();
    }

    @Test
    @DisplayName("tryMarkPayout exactly-once protection")
    void testTryMarkPayoutExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        guard.open(playerId, GuardContext.EVENT, "ref", "backup", "world");

        // First call should succeed
        boolean firstCall = guard.tryMarkPayout(playerId);
        assertThat(firstCall).isTrue();
        assertThat(guard.get(playerId).payoutDone()).isTrue();

        // Second call must fail
        boolean secondCall = guard.tryMarkPayout(playerId);
        assertThat(secondCall).isFalse();

        // For a player without open session, tryMarkPayout returns true
        UUID unknownPlayer = UUID.randomUUID();
        assertThat(guard.tryMarkPayout(unknownPlayer)).isTrue();
    }

    @Test
    @DisplayName("persistence round-trip across instance reload")
    void testPersistenceRoundTrip() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        guard.open(p1, GuardContext.PVP_MATCH, "m-1", "b-1", "world_pvp");
        guard.open(p2, GuardContext.EVENT, "e-1", "b-2", "world_event");
        guard.phase(p2, GuardPhase.ACTIVE);

        File file = new File(tempDir.toFile(), "inventory-guard.yml");
        assertThat(file).exists();

        // Create new guard instance and load
        InventoryGuard newGuard = new InventoryGuard(plugin);
        newGuard.load();

        assertThat(newGuard.openCount()).isEqualTo(2);
        assertThat(newGuard.hasOpenSession(p1)).isTrue();
        assertThat(newGuard.hasOpenSession(p2)).isTrue();

        GuardEntry e1 = newGuard.get(p1);
        assertThat(e1.context()).isEqualTo(GuardContext.PVP_MATCH);
        assertThat(e1.backupId()).isEqualTo("b-1");

        GuardEntry e2 = newGuard.get(p2);
        assertThat(e2.context()).isEqualTo(GuardContext.EVENT);
        assertThat(e2.phase()).isEqualTo(GuardPhase.ACTIVE);
    }

    @Test
    @DisplayName("restoreFor closes session on successful restore and cleans up backup if configured")
    void testRestoreForSuccess() {
        UUID playerId = UUID.randomUUID();
        guard.open(playerId, GuardContext.PVP_MATCH, "m-1", "b-1", "world");

        InventoryBackupService backupService = mock(InventoryBackupService.class);
        when(plugin.getInventoryBackupService()).thenReturn(backupService);

        InventoryManagementConfig config = mock(InventoryManagementConfig.class);
        when(plugin.getInventoryConfig()).thenReturn(config);
        when(config.cleanupAfterMatch()).thenReturn(true);

        BackupRef ref = mock(BackupRef.class);
        when(backupService.restore(eq(playerId), eq(ref), any(RestoreMode.class)))
                .thenReturn(CompletableFuture.completedFuture(RestoreOutcome.APPLIED));

        guard.restoreFor(playerId, ref, GuardPhase.BACKED_UP);

        assertThat(guard.hasOpenSession(playerId)).isFalse();
        verify(backupService).delete(ref);
    }

    @Test
    @DisplayName("restoreFor queues for join if outcome is QUEUED_FOR_JOIN")
    void testRestoreForQueued() {
        UUID playerId = UUID.randomUUID();
        guard.open(playerId, GuardContext.PVP_MATCH, "m-1", "b-1", "world");

        InventoryBackupService backupService = mock(InventoryBackupService.class);
        when(plugin.getInventoryBackupService()).thenReturn(backupService);

        BackupRef ref = mock(BackupRef.class);
        when(backupService.restore(eq(playerId), eq(ref), any(RestoreMode.class)))
                .thenReturn(CompletableFuture.completedFuture(RestoreOutcome.QUEUED_FOR_JOIN));

        guard.restoreFor(playerId, ref, GuardPhase.BACKED_UP);

        assertThat(guard.hasOpenSession(playerId)).isTrue();
        assertThat(guard.get(playerId).phase()).isEqualTo(GuardPhase.QUEUED);
    }

    @Test
    @DisplayName("recoverOpenSessions marks missing backups as ORPHANED")
    void testRecoverOpenSessions() {
        UUID p1 = UUID.randomUUID();
        guard.openWithoutBackup(p1, GuardContext.PVP_MATCH, "m-1", "world");

        InventoryManagementConfig config = mock(InventoryManagementConfig.class);
        when(plugin.getInventoryConfig()).thenReturn(config);
        when(config.guardEnabled()).thenReturn(true);
        when(config.restoreOrphansOnStart()).thenReturn(true);

        guard.recoverOpenSessions();

        assertThat(guard.get(p1).phase()).isEqualTo(GuardPhase.ORPHANED);
    }
}
