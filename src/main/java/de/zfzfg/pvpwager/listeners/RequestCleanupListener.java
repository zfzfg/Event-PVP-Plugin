package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up PvP request state when players quit to prevent stale entries
 * and memory leaks. Removes both command-based, PvP requests, and wager requests.
 */
public class RequestCleanupListener implements Listener {
    private final EventPlugin plugin;

    public RequestCleanupListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        try {
            if (plugin.getRequestManager() != null) {
                plugin.getRequestManager().removeRequestsForPlayer(player);
            }
        } catch (Exception ignored) {}
        try {
            if (plugin.getCommandRequestManager() != null) {
                plugin.getCommandRequestManager().removeRequestsForPlayer(player);
            }
        } catch (Exception ignored) {}
        // Cleanup wager GUI requests
        try {
            if (plugin.getPvpWagerGuiCommand() != null) {
                plugin.getPvpWagerGuiCommand().removeRequestsForPlayer(player);
            }
        } catch (Exception ignored) {}
    }
}