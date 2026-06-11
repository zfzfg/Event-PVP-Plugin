package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.util.UpdateChecker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateNotifyListener implements Listener {
    
    private final EventPlugin plugin;
    
    public UpdateNotifyListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Prüfe ob Update-Check aktiviert und Admin-Benachrichtigung beim Join aktiviert ist
        if (!plugin.getConfigManager().isUpdateCheckEnabled() || 
            !plugin.getConfigManager().shouldNotifyAdminsOnJoin()) {
            return;
        }
        
        // Prüfe Permission statt isOp()
        if (!player.hasPermission("eventpvp.admin.updatenotify")) {
            return;
        }
        
        // Hole UpdateChecker und prüfe ob Update verfügbar ist (gecachtes Ergebnis)
        UpdateChecker updateChecker = plugin.getUpdateChecker();
        if (updateChecker == null || !updateChecker.hasChecked() || !updateChecker.isUpdateAvailable()) {
            return;
        }
        
        // Sende Benachrichtigung an den Spieler
        String currentVersion = updateChecker.getCurrentVersion();
        String latestVersion = updateChecker.getLatestVersion();
        
        String availableMessage = plugin.getConfigManager().getMessage("update.available", 
            "latest", latestVersion, 
            "current", currentVersion);
        String downloadLinkMessage = plugin.getConfigManager().getMessage("update.download-link");
        
        // Kurze Verzögerung damit die Join-Nachricht zuerst angezeigt wird
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', availableMessage));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', downloadLinkMessage));
        }, 20L); // 1 Sekunde Delay
    }
}
