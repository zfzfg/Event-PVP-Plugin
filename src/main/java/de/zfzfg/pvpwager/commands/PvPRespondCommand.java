package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeBridge;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Moderner Befehl zum Öffnen des LiveTrade-GUIs als Antwort auf eine Wager-Herausforderung.
 * Usage: /pvprespond [gui|spieler]
 */
public class PvPRespondCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    private final LiveTradeBridge bridge;
    
    public PvPRespondCommand(EventPlugin plugin) {
        this.plugin = plugin;
        this.bridge = new LiveTradeBridge(plugin);
    }
    
    private String getMsg(String key) {
        String msg = plugin.getCoreConfigManager().getMessages().getString("messages.command.pvp.accept." + key, null);
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.request." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.general." + key, null);
        }
        if (msg == null) {
            return "&c[missing: " + key + "]";
        }
        return MessageUtil.color(msg);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMsg("players-only"));
            return true;
        }
        
        // Check permission
        if (!player.hasPermission("pvpwager.use") && !player.hasPermission("pvpwager.respond")) {
            MessageUtil.sendMessage(player, getMsg("no-permission"));
            return true;
        }
        
        // Finde den passenden Request (entweder nach Spielername oder den neuesten an diesen Spieler)
        CommandRequest pendingRequest = null;
        if (args.length >= 1 && !args[0].equalsIgnoreCase("gui")) {
            Player challenger = Bukkit.getPlayer(args[0]);
            if (challenger != null) {
                pendingRequest = plugin.getCommandRequestManager().getRequest(challenger, player);
            }
        }
        if (pendingRequest == null) {
            pendingRequest = plugin.getCommandRequestManager().getRequestToPlayer(player);
        }
        
        if (pendingRequest == null) {
            MessageUtil.sendMessage(player, getMsg("no-request"));
            return true;
        }
        
        // Check if request has expired
        if (pendingRequest.isExpired()) {
            plugin.getCommandRequestManager().removeRequest(pendingRequest.getSender());
            MessageUtil.sendMessage(player, getMsg("expired"));
            return true;
        }
        
        // Check if sender is still online
        if (!pendingRequest.getSender().isOnline()) {
            plugin.getCommandRequestManager().removeRequest(pendingRequest.getSender());
            MessageUtil.sendMessage(player, getMsg("player-offline"));
            return true;
        }
        
        // Starte synchrone LiveTrade-Session direkt aus dem Request
        boolean success = bridge.startSessionFromRequest(pendingRequest);
        if (!success) {
            MessageUtil.sendMessage(player, getMsg("session-start-failed"));
        }
        
        return true;
    }
}
