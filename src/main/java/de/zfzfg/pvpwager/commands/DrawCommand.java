package de.zfzfg.pvpwager.commands;

import org.bukkit.Bukkit;
import de.zfzfg.core.util.Time;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class DrawCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public DrawCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.draw.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        // Check if player is in a match
        if (match == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.error.not-in-match"));
            return true;
        }
        
        // Check if match is active
        if (match.getState() != MatchState.FIGHTING) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.only-active"));
            return true;
        }
        
        Player opponent = match.getOpponent(player);
        
        // Handle accept/deny
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("accept")) {
                // Check if there's an active draw vote
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.no-active-vote"));
                    return true;
                }
                
                // Check if player is the one who should accept (not the initiator)
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.initiator-wait"));
                    return true;
                }
                
                // Accept draw
                broadcastLines(match, cfg.getMessage("messages.command.draw.accepted"));
                
                plugin.getMatchManager().endMatch(match, null, true);
                return true;
            }
            
            if (args[0].equalsIgnoreCase("deny")) {
                // Check if there's an active draw vote
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.no-active-vote"));
                    return true;
                }
                
                // Check if player is the one who should deny (not the initiator)
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.initiator-cannot-deny"));
                    return true;
                }
                
                // Deny draw
                match.deactivateDrawVote();
                
                broadcastLines(match, cfg.getMessage("messages.command.draw.denied", "player", player.getName()));
                
                return true;
            }
        }
        
        // Initiate or cancel draw vote
        if (match.isDrawVoteActive()) {
            // Check if player is the initiator
            if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                // Cancel own vote
                match.cancelDrawVoteIfInitiator(player.getUniqueId());
                
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.cancelled-self"));
                MessageUtil.sendMessage(opponent, cfg.getMessage("messages.command.draw.cancelled-opponent", "player", player.getName()));
                return true;
            } else {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.already-active", "opponent", opponent.getName()));
                return true;
            }
        }
        
        // Initiate new draw vote atomically
        if (!match.tryActivateDrawVote(player.getUniqueId())) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.already-active", "opponent", opponent.getName()));
            return true;
        }
        
        // Broadcast to players
        int drawVoteTime = plugin.getPvpConfigManager().getConfig().getInt("settings.match.draw-vote-time", 30);
        broadcastLines(match, cfg.getMessage(
            "messages.command.draw.started",
            "initiator", player.getName(),
            "opponent", opponent.getName(),
            "time", String.valueOf(drawVoteTime)
        ));
        
        // Schedule auto-cancel
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.cancelDrawVoteIfInitiator(player.getUniqueId())) {
                broadcastLines(match, cfg.getMessage("messages.command.draw.expired"));
            }
        }, Time.seconds(drawVoteTime));
        
        return true;
    }

        private void broadcastLines(Match match, String message) {
            String[] lines = message.split("\\n", -1);
            for (String line : lines) {
                match.broadcast(line);
            }
        }
}