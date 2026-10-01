package de.zfzfg.core.inventory.adapter;

import com.zfzfg.inventorybackup.api.events.*;
import de.zfzfg.core.inventory.BackupRef;
import de.zfzfg.core.inventory.guard.GuardPhase;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.event.*;

/** Coordinates foreign and queued restores with the inventory journal. */
public final class InventoryBackupEvents implements Listener {
    private final EventPlugin plugin;
    public InventoryBackupEvents(EventPlugin plugin) { this.plugin = plugin; }

    private boolean managed() {
        return plugin.getInventoryConfig().managedByPlugin()
                && plugin.getInventoryBackupService().isAvailable();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beforeRestore(InventoryRestoreEvent event) {
        if (!managed()) return;
        var guard = plugin.getInventoryGuard();
        var id = event.getPlayer().getUniqueId();
        var entry = guard.get(id);
        if (entry == null) return;
        var handle = event.getSnapshot().handle();
        boolean matching = handle != null && guard.matches(id, handle.ownerId(), handle.id());
        boolean allowed = matching && (guard.authorized(id, handle.ownerId(), handle.id())
                || entry.phase() == GuardPhase.QUEUED && !guard.isSessionStillRunning(entry));
        if (!allowed) {
            event.setCancelled(true);
            guard.diagnostic(id, "CANCELLED", false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restored(InventoryRestoredEvent event) {
        if (!managed()) return;
        var guard = plugin.getInventoryGuard();
        var id = event.getPlayer().getUniqueId();
        var entry = guard.get(id);
        var handle = event.getSnapshot().handle();
        var options = event.getOptions();
        if (entry == null || entry.phase() != GuardPhase.QUEUED || handle == null
                || !guard.matches(id, handle.ownerId(), handle.id()) || guard.isSessionStillRunning(entry)) return;
        if (!options.contents() || !options.armor() || !options.offhand()
                || !options.level() || !options.exp() || !options.clearBefore()) {
            guard.diagnostic(id, "PARTIAL_RESTORE", false);
            return;
        }
        plugin.getInventorySessions().completeApplied(id, new BackupRef(handle.ownerId(), handle.id(),
                handle.type(), handle.createdAt().toEpochMilli(), handle.metadata()), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void deleted(BackupDeletedEvent event) {
        if (!managed()) return;
        var handle = event.getHandle();
        var guard = plugin.getInventoryGuard();
        for (var entry : guard.openSessions()) {
            if (guard.matches(entry.playerId(), handle.ownerId(), handle.id())) {
                String reason = "BACKUP_DELETED_" + event.getReason().name();
                guard.diagnostic(entry.playerId(), reason, true);
                plugin.getLogger().warning(plugin.getConsoleMsg("guard-backup-deleted",
                        "player", entry.playerId().toString(), "reason", event.getReason().name()));
            }
        }
    }
}
