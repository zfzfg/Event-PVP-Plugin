package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.ArenaManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PvPYesCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPYesCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.pvpyes.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Get request where player is sender
        CommandRequest request = null;
        for (CommandRequest req : plugin.getCommandRequestManager().getPendingRequests()) {
            if (req.getSender().equals(player)) {
                request = req;
                break;
            }
        }
        
        // Make request final for inner class usage
        final CommandRequest finalRequest = request;
        
        if (request == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpyes.no-request"));
            return true;
        }
        
        if (!request.hasTargetResponded()) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpyes.waiting-response", "player", request.getTarget().getName()));
            return true;
        }
        
        // Verify items/money still available
        Player target = request.getTarget();
        
        // Check sender's items/money
        if (request.getMoney() > 0) {
            if (!plugin.getEconomy().has(player, request.getMoney())) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpyes.sender-no-money"));
                plugin.getCommandRequestManager().removeRequest(player);
                return true;
            }
        } else {
            for (ItemStack item : request.getWagerItems()) {
                if (!player.getInventory().containsAtLeast(item, item.getAmount())) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpyes.sender-no-items"));
                    plugin.getCommandRequestManager().removeRequest(player);
                    return true;
                }
            }
        }
        
        // Check target's items/money
        if (request.getTargetWagerMoney() > 0) {
            if (!plugin.getEconomy().has(target, request.getTargetWagerMoney())) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpyes.target-no-money-player", "player", target.getName()));
                MessageUtil.sendMessage(target, cfg.getMessage("messages.command.pvpyes.target-no-money-target"));
                plugin.getCommandRequestManager().removeRequest(player);
                return true;
            }
        } else {
            for (ItemStack item : request.getTargetWagerItems()) {
                if (!target.getInventory().containsAtLeast(item, item.getAmount())) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpyes.target-no-items-player", "player", target.getName()));
                    MessageUtil.sendMessage(target, cfg.getMessage("messages.command.pvpyes.target-no-items-target"));
                    plugin.getCommandRequestManager().removeRequest(player);
                    return true;
                }
            }
        }
        
        // Welt aus Arena-Konfiguration holen (nicht die Arena-ID!)
        String resolvedWorldName = null;
        if (request.getFinalArenaId() != null && !request.getFinalArenaId().isEmpty()) {
            resolvedWorldName = plugin.getArenaManager()
                .getArenaOptional(request.getFinalArenaId())
                .map(a -> a.getArenaWorld())
                .orElse(null);
        }
        final String worldName = resolvedWorldName;
        final Player finalPlayer = player;
        final Player finalTarget = target;
        
        // Welt laden mit robuster Fehlerbehandlung
        if (worldName != null && !worldName.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getConfigManager().getMessage("world.loading"));
            MessageUtil.sendMessage(target, plugin.getConfigManager().getMessage("world.loading"));
            
            // Nutze ArenaManager für zuverlässige Welt-Ladung
            plugin.getArenaManager().loadArenaWorld(worldName, new Runnable() {
                @Override
                public void run() {
                    // Prüfe ob Welt geladen wurde
                    if (Bukkit.getWorld(worldName) == null) {
                        MessageUtil.sendMessage(finalPlayer, plugin.getConfigManager().getMessage("world.load-failed"));
                        MessageUtil.sendMessage(finalTarget, plugin.getConfigManager().getMessage("world.load-failed"));
                        plugin.getCommandRequestManager().removeRequest(finalPlayer);
                        return;
                    }
                    
                    MessageUtil.sendMessage(finalPlayer, plugin.getConfigManager().getMessage("world.loaded"));
                    MessageUtil.sendMessage(finalTarget, plugin.getConfigManager().getMessage("world.loaded"));
                    
                    // Start match via command system
                    plugin.getMatchManager().startMatchFromCommand(finalRequest);
                    plugin.getCommandRequestManager().removeRequest(finalPlayer);
                }
            });
            
        } else {
            // Keine Welt angegeben, direkt starten
            plugin.getMatchManager().startMatchFromCommand(finalRequest);
            plugin.getCommandRequestManager().removeRequest(player);
        }
        
        return true;
    }
}