package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Arrays;
import java.util.List;

public class WorldChangeListener implements Listener {
    
    private final EventPlugin plugin;
    
    // Allowed commands during matches
    private final List<String> ALLOWED_COMMANDS = Arrays.asList(
        "/surrender", "/draw", "/msg", "/tell", "/w", "/whisper", 
        "/r", "/reply", "/pvp leave", "/pvp", "/help"
    );
    
    public WorldChangeListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match == null) return;
        
        // Don't restrict commands for spectators
        if (match.getSpectators().contains(player.getUniqueId())) {
            return;
        }
        
        String command = event.getMessage().toLowerCase();
        
        // Check if command is allowed
        boolean isAllowed = false;
        for (String allowedCmd : ALLOWED_COMMANDS) {
            if (command.startsWith(allowedCmd)) {
                isAllowed = true;
                break;
            }
        }
        
        // Allow staff commands for OPs
        if (player.isOp() || player.hasPermission("eventpvp.opbypass")) {
            isAllowed = true;
        }
        
        if (!isAllowed) {
            // Block other commands during match
            if (match.getState() == MatchState.FIGHTING || match.getState() == MatchState.STARTING) {
                event.setCancelled(true);
                MessageUtil.error(player, 
                    plugin.getPvpConfigManager().getMessage("error.command-blocked"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Optimization: Only check if player actually moved to a new block
        if (to == null || (from.getBlockX() == to.getBlockX() && 
                          from.getBlockY() == to.getBlockY() && 
                          from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match == null || match.getArena() == null || match.getArena().getBoundaries() == null) {
            return;
        }
        
        // Don't check boundaries for spectators
        if (match.getSpectators().contains(player.getUniqueId())) {
            return;
        }
        
        // Only check during FIGHTING state
        if (match.getState() != MatchState.FIGHTING) {
            return;
        }
        
        // Check if player is leaving arena boundaries
        if (!match.getArena().getBoundaries().isInside(to)) {
            // Teleport back to last valid position
            event.setTo(from);
            
            // Send warning message (throttled to avoid spam)
            sendBoundaryWarning(player);
            
            // Play sound effect
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        }
    }
    
    // Throttle boundary warnings to prevent spam (max 1 per second per player)
    private final java.util.Map<java.util.UUID, Long> lastWarningTime = new java.util.HashMap<>();
    
    private void sendBoundaryWarning(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastWarningTime.get(player.getUniqueId());
        
        if (lastTime == null || currentTime - lastTime > 1000) {
            MessageUtil.sendMessage(player, 
                plugin.getPvpConfigManager().getMessage("match.boundaries-warning"));
            lastWarningTime.put(player.getUniqueId(), currentTime);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove player entry to prevent long-lived map growth
        Player player = event.getPlayer();
        lastWarningTime.remove(player.getUniqueId());
    }

    public void cleanup() {
        // Clear throttling map on plugin disable/reload
        lastWarningTime.clear();
    }
}