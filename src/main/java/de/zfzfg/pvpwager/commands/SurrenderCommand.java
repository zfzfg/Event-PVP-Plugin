package de.zfzfg.pvpwager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurrenderCommand implements CommandExecutor, Listener {
    
    private final EventPlugin plugin;
    private final Map<UUID, Long> surrenderConfirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 10000; // 10 seconds
    
    public SurrenderCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.surrender.player-only"));
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
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.surrender.only-active"));
            return true;
        }
        
        // Handle confirmation
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            // First surrender attempt - require confirmation
            surrenderConfirmations.put(player.getUniqueId(), System.currentTimeMillis());

            MessageUtil.sendMessage(player, cfg.getMessage(
                "messages.command.surrender.confirm",
                "timeout", String.valueOf(CONFIRMATION_TIMEOUT / 1000)
            ));
            
            return true;
        }
        
        // Check if confirmation is valid
        Long confirmTime = surrenderConfirmations.get(player.getUniqueId());
        if (confirmTime == null || System.currentTimeMillis() - confirmTime > CONFIRMATION_TIMEOUT) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.surrender.confirm-expired"));
            surrenderConfirmations.remove(player.getUniqueId());
            return true;
        }
        
        // Confirm surrender
        surrenderConfirmations.remove(player.getUniqueId());
        
        // Announce surrender
        Player opponent = match.getOpponent(player);
        
        broadcastLines(match, cfg.getMessage(
            "messages.command.surrender.surrendered",
            "player", player.getName(),
            "opponent", opponent.getName()
        ));
        
        // End match with opponent as winner
        plugin.getMatchManager().endMatch(match, opponent, false);
        
        return true;
    }

    private void broadcastLines(Match match, String message) {
        String[] lines = message.split("\\n", -1);
        for (String line : lines) {
            match.broadcast(line);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clear pending surrender confirmation on quit to avoid stale entries
        surrenderConfirmations.remove(event.getPlayer().getUniqueId());
    }
}