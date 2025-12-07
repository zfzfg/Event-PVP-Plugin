package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Befehl zum Ablehnen einer PVP-Wager Anfrage.
 * 
 * Usage: /pvpdeny [spieler]
 */
public class PvPDenyCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    
    public PvPDenyCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    private String getMsg(String key) {
        String msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, "");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
    
    private String getPvpMsg(String key) {
        return plugin.getPvpConfigManager().getMessage("messages.commands.pvpdeny." + key);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("players-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Prüfe Permission
        if (!player.hasPermission("pvpwager.use") && !player.hasPermission("pvpwager.deny")) {
            MessageUtil.sendMessage(player, getPvpMsg("no-permission"));
            return true;
        }
        
        PvPWagerGuiCommand wagerCommand = plugin.getPvpWagerGuiCommand();
        
        // Ohne Argument: Prüfe ob es eine Anfrage gibt
        if (args.length == 0) {
            UUID senderId = wagerCommand.getWagerRequestSender(player);
            
            if (senderId == null) {
                MessageUtil.sendMessage(player, getPvpMsg("no-pending-requests"));
                return true;
            }
            
            Player senderPlayer = Bukkit.getPlayer(senderId);
            if (senderPlayer == null || !senderPlayer.isOnline()) {
                // Trotzdem entfernen
                wagerCommand.cancelWagerRequest(senderId);
                MessageUtil.sendMessage(player, getPvpMsg("request-removed-offline"));
                return true;
            }
            
            // Anfrage ablehnen
            wagerCommand.denyWagerRequest(player, senderPlayer);
            return true;
        }
        
        // Mit Argument: Bestimmten Spieler suchen
        String senderName = args[0];
        Player senderPlayer = Bukkit.getPlayer(senderName);
        
        if (senderPlayer == null || !senderPlayer.isOnline()) {
            // Versuche trotzdem zu löschen
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                UUID targetId = wagerCommand.getWagerRequestTarget(onlinePlayer.getUniqueId());
                if (targetId != null && targetId.equals(player.getUniqueId())) {
                    if (onlinePlayer.getName().equalsIgnoreCase(senderName)) {
                        wagerCommand.cancelWagerRequest(onlinePlayer.getUniqueId());
                        MessageUtil.sendMessage(player, getMsg("request-denied").replace("{player}", senderName));
                        return true;
                    }
                }
            }
            MessageUtil.sendMessage(player, getMsg("player-offline").replace("{player}", senderName));
            return true;
        }
        
        // Prüfe ob Anfrage existiert
        UUID targetId = wagerCommand.getWagerRequestTarget(senderPlayer.getUniqueId());
        
        if (targetId == null || !targetId.equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, getMsg("no-request").replace("{player}", senderPlayer.getName()));
            return true;
        }
        
        // Anfrage ablehnen
        wagerCommand.denyWagerRequest(player, senderPlayer);
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        
        Player player = (Player) sender;
        PvPWagerGuiCommand wagerCommand = plugin.getPvpWagerGuiCommand();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            
            // Zeige nur Spieler, die eine Anfrage gesendet haben
            List<String> suggestions = new ArrayList<>();
            
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(player)) continue;
                
                UUID targetId = wagerCommand.getWagerRequestTarget(onlinePlayer.getUniqueId());
                if (targetId != null && targetId.equals(player.getUniqueId())) {
                    if (onlinePlayer.getName().toLowerCase().startsWith(prefix)) {
                        suggestions.add(onlinePlayer.getName());
                    }
                }
            }
            
            return suggestions;
        }
        
        return new ArrayList<>();
    }
}
