package de.zfzfg.pvpwager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class PvPRequestCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPRequestCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvprequest.usage"));
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[0]));
            return true;
        }
        
        if (player.equals(target)) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.self-target"));
            return true;
        }
        
        // Check if player is in a match
        if (plugin.getMatchManager().getMatchByPlayer(player) != null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.already-in-match"));
            return true;
        }
        
        // Check if target is in a match
        if (plugin.getMatchManager().getMatchByPlayer(target) != null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.target-in-match", "player", target.getName()));
            return true;
        }
        
        // Check if there's already a pending request
        if (plugin.getRequestManager().hasPendingRequest(player)) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.pending-self"));
            return true;
        }
        
        if (plugin.getRequestManager().hasPendingRequest(target)) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.pending-target", "player", target.getName()));
            return true;
        }
        
        // Send the request
        plugin.getRequestManager().sendRequest(player, target);
        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvprequest.sent", "player", target.getName()));
        return true;
    }
}