package de.zfzfg.core.inventory;

import de.zfzfg.core.util.Time;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Das Sicherheitsnetz beim Join.
 *
 * <p>Haengt im Guard-Journal noch eine Sitzung, deren Match oder Event laengst vorbei ist
 * (Absturz, harter Kick, verpasster Restore), wird sie hier nachgeholt. Die von
 * InventoryBackup selbst eingereihten Wiederherstellungen laufen dagegen ueber dessen
 * eigenen Join-Hook und brauchen hier nichts.</p>
 *
 * <p>Bewusst um einen Tick verzoegert: zum Zeitpunkt von {@code PlayerJoinEvent} ist das
 * Inventar des Spielers noch nicht endgueltig geladen, und ein Restore in diesem Moment
 * kann vom nachlaufenden Ladevorgang wieder ueberschrieben werden.</p>
 */
public final class InventoryGuardListener implements Listener {

    private final EventPlugin plugin;

    public InventoryGuardListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        InventoryManagementConfig config = plugin.getInventoryConfig();
        if (config == null || !config.restoreOnRejoin()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                return;
            }
            plugin.getInventoryGuard().handleJoin(event.getPlayer());
        }, Time.ticks(10));
    }
}
