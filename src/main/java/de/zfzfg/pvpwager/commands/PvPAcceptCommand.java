package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.commands.unified.subcommands.AcceptSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Befehl zum Annehmen einer PVP-Wager Anfrage.
 * 
 * Usage: /pvpaccept [spieler]
 * @deprecated Use /pvp accept instead. This command is kept for backward compatibility.
 */
@Deprecated
public class PvPAcceptCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    private final AcceptSubCommand acceptSubCommand;
    
    @Deprecated
    public PvPAcceptCommand(EventPlugin plugin) {
        this.plugin = plugin;
        this.acceptSubCommand = new AcceptSubCommand(plugin);
    }
    
    @Override
    @Deprecated
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return acceptSubCommand.execute(sender, args);
    }
    
    @Override
    @Deprecated
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();
        
        PvPWagerGuiCommand wagerCommand = plugin.getPvpWagerGuiCommand();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(player)) continue;
                
                boolean hasReq = false;
                if (plugin.getCommandRequestManager().getRequest(onlinePlayer, player) != null) {
                    hasReq = true;
                } else if (plugin.getRequestManager().hasPendingRequest(onlinePlayer)) {
                    hasReq = true;
                } else if (wagerCommand != null) {
                    UUID targetId = wagerCommand.getWagerRequestTarget(onlinePlayer.getUniqueId());
                    if (targetId != null && targetId.equals(player.getUniqueId())) {
                        hasReq = true;
                    }
                }
                
                if (hasReq && onlinePlayer.getName().toLowerCase().startsWith(prefix)) {
                    suggestions.add(onlinePlayer.getName());
                }
            }
            
            return suggestions;
        }
        
        return new ArrayList<>();
    }
}