package de.zfzfg.pvpwager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;

public class PvPAdminCommand implements CommandExecutor {
    private final EventPlugin plugin;
    
    public PvPAdminCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.getPvpConfigManager();
        if (!sender.hasPermission("pvpwager.admin")) {
            MessageUtil.sendMessage(sender, cfg.getMessage("messages.command.pvpadmin.no-permission"));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
                
            case "stopall":
                handleStopAll(sender);
                break;
                
            case "info":
                handleInfo(sender);
                break;
                
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void handleReload(CommandSender sender) {
        var cfg = plugin.getPvpConfigManager();
        try {
            // Reload all configs
            plugin.getPvpConfigManager().reloadConfigs();
            
            // Reload arenas
            plugin.getArenaManager().reloadArenas();
            
            // Reload equipment sets
            plugin.getEquipmentManager().reloadEquipmentSets();
            
            MessageUtil.sendMessage(sender, cfg.getMessage(
                "messages.command.pvpadmin.reload-success",
                "arenaCount", String.valueOf(plugin.getArenaManager().getArenas().size()),
                "equipmentCount", String.valueOf(plugin.getEquipmentManager().getEquipmentSets().size())
            ));
            
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, cfg.getMessage("messages.command.pvpadmin.reload-error", "error", e.getMessage()));
            plugin.getLogger().severe("Admin reload failed: " + e.getMessage());
            if (plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }
    
    private void handleStopAll(CommandSender sender) {
        var cfg = plugin.getPvpConfigManager();
        int count = plugin.getMatchManager().stopAllMatches();
        MessageUtil.sendMessage(sender, cfg.getMessage("messages.command.pvpadmin.stopall", "count", String.valueOf(count)));
    }
    
    private void handleInfo(CommandSender sender) {
        var cfg = plugin.getPvpConfigManager();
        int activeMatches = plugin.getMatchManager().getActiveMatchCount();
        MessageUtil.sendMessage(sender, cfg.getMessage(
            "messages.command.pvpadmin.info",
            "version", plugin.getDescription().getVersion(),
            "active", String.valueOf(activeMatches),
            "arenas", String.valueOf(plugin.getArenaManager().getArenas().size()),
            "equipment", String.valueOf(plugin.getEquipmentManager().getEquipmentSets().size()),
            "economy", plugin.hasEconomy() ? cfg.getMessage("messages.command.pvpadmin.enabled") : cfg.getMessage("messages.command.pvpadmin.disabled")
        ));
    }
    
    private void sendHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, plugin.getPvpConfigManager().getMessage("messages.command.pvpadmin.help"));
    }
}
