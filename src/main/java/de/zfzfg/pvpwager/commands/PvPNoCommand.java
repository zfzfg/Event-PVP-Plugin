package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PvPNoCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPNoCommand(EventPlugin plugin) {
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
        
        // Check if player is sender or target
        CommandRequest request = null;
        boolean isSender = false;
        
        // Check as sender
        for (CommandRequest req : plugin.getCommandRequestManager().getPendingRequests()) {
            if (req.getSender().equals(player)) {
                request = req;
                isSender = true;
                break;
            }
        }
        
        // Check as target
        if (request == null) {
            request = plugin.getCommandRequestManager().getRequestToPlayer(player);
        }
        
        if (request == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpno.no-request"));
            return true;
        }
        
        Player other = isSender ? request.getTarget() : request.getSender();
        
        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpno.declined-self", "player", other.getName()));
        MessageUtil.sendMessage(other, cfg.getMessage("messages.command.pvpno.declined-other", "player", player.getName()));
        
        plugin.getCommandRequestManager().removeRequest(request.getSender());
        
        return true;
    }
}