package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.gui.GuiManager;
import de.zfzfg.pvpwager.gui.ResponseGui;
import de.zfzfg.pvpwager.gui.WagerSession;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to open the response GUI for answering wager requests.
 * Usage: /pvprespond gui
 */
public class PvPRespondCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPRespondCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private String getMsg(String key) {
        String msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, "");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("players-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check permission
        if (!player.hasPermission("pvpwager.use") && !player.hasPermission("pvpwager.respond")) {
            MessageUtil.sendMessage(player, getMsg("no-permission"));
            return true;
        }
        
        if (args.length == 0 || !args[0].equalsIgnoreCase("gui")) {
            MessageUtil.sendMessage(player, getMsg("usage"));
            return true;
        }
        
        // Check if player has a pending request to respond to
        CommandRequest pendingRequest = plugin.getCommandRequestManager().getRequestToPlayer(player);
        
        if (pendingRequest == null) {
            MessageUtil.sendMessage(player, getMsg("no-pending-request"));
            return true;
        }
        
        // Check if request has expired
        if (pendingRequest.isExpired()) {
            plugin.getCommandRequestManager().removeRequest(pendingRequest.getSender());
            MessageUtil.sendMessage(player, getMsg("request-expired"));
            return true;
        }
        
        // Check if sender is still online
        if (!pendingRequest.getSender().isOnline()) {
            plugin.getCommandRequestManager().removeRequest(pendingRequest.getSender());
            MessageUtil.sendMessage(player, getMsg("requester-offline"));
            return true;
        }
        
        // Create a response session
        GuiManager guiManager = plugin.getGuiManager();
        WagerSession session = guiManager.getOrCreateSession(player, pendingRequest.getSender());
        
        // Open the response GUI
        ResponseGui responseGui = new ResponseGui(plugin, player, session, pendingRequest);
        responseGui.open();
        
        return true;
    }
}
