package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.Location;

import java.util.Optional;

public class WorldChangeListener implements Listener {
    
    private final EventPlugin plugin;
    
    public WorldChangeListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            
            // Wenn Spieler spectatet und Welt wechselt, entferne Vanish/Fly
            if (session.isSpectator(player)) {
                String eventWorldName = session.getConfig().getEventWorld();
                String lobbyWorldName = session.getConfig().getLobbyWorld();
                
                // Wenn Spieler Event-Welt oder Lobby-Welt verlässt
                if (!player.getWorld().getName().equals(eventWorldName) && 
                    !player.getWorld().getName().equals(lobbyWorldName)) {
                    
                    // Entfernt externe Vanish/Fly-Kommandos – Spectator-Mode wird separat gehandhabt
                }
            }
        }
    }
    
    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            
            // Prüfe ob Spieler in Event-Welt oder Lobby-Welt ist
            String eventWorldName = session.getConfig().getEventWorld();
            String lobbyWorldName = session.getConfig().getLobbyWorld();
            String currentWorld = player.getWorld().getName();
            
            // Hole Command-Restriction Setting
            String restriction = plugin.getConfigManager().getCommandRestriction();
            
            boolean shouldBlock = false;
            
            // Prüfe basierend auf Einstellung
            switch (restriction.toLowerCase()) {
                case "both":
                    shouldBlock = currentWorld.equals(eventWorldName) || currentWorld.equals(lobbyWorldName);
                    break;
                case "event":
                    shouldBlock = currentWorld.equals(eventWorldName);
                    break;
                case "lobby":
                    shouldBlock = currentWorld.equals(lobbyWorldName);
                    break;
                case "none":
                    shouldBlock = false;
                    break;
                default:
                    shouldBlock = currentWorld.equals(eventWorldName) || currentWorld.equals(lobbyWorldName);
                    break;
            }
            
            if (shouldBlock) {
                // OP/Bypass erlaubt alle Befehle
                if (player.isOp() || player.hasPermission("eventpvp.opbypass")) {
                    return;
                }
                // Nur /event leave erlauben (und Aliases)
                if (!command.startsWith("/event leave") && 
                    !command.startsWith("/ev leave") && 
                    !command.startsWith("/events leave")) {
                    
                    event.setCancelled(true);
                    player.sendMessage(org.bukkit.ChatColor.RED + "Commands sind während des Events gesperrt! Nutze /event leave um das Event zu verlassen.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        // Enforce presence rules only when worlds are loaded
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        if (!sessionOpt.isPresent()) {
            // No player-bound session; still enforce if entering configured event/lobby worlds globally
            enforceWorldAccess(player);
            return;
        }

        enforceWorldAccess(player);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        String targetWorld = to.getWorld().getName();
        for (EventSession session : plugin.getEventManager().getActiveSessions().values()) {
            String eventWorldName = session.getConfig().getEventWorld();
            String lobbyWorldName = session.getConfig().getLobbyWorld();
            if (org.bukkit.Bukkit.getWorld(eventWorldName) == null && org.bukkit.Bukkit.getWorld(lobbyWorldName) == null) {
                continue;
            }
            boolean intoRestricted = targetWorld.equals(eventWorldName) || targetWorld.equals(lobbyWorldName);
            if (!intoRestricted) continue;

            de.zfzfg.pvpwager.models.Match match = plugin.getMatchManager().getMatchByPlayer(player);
            boolean inActiveMatch = match != null && match.getState() == de.zfzfg.pvpwager.models.MatchState.FIGHTING;
            if (inActiveMatch) return;

            if (player.isOp() || player.hasPermission("eventpvp.opbypass") || player.hasPermission("eventpvp.world.access")) {
                return;
            }

            de.zfzfg.eventplugin.session.EventSession.EventState state = session.getState();
            boolean eventActive = state == de.zfzfg.eventplugin.session.EventSession.EventState.JOIN_PHASE ||
                                  state == de.zfzfg.eventplugin.session.EventSession.EventState.COUNTDOWN ||
                                  state == de.zfzfg.eventplugin.session.EventSession.EventState.RUNNING;
            if (!eventActive) {
                org.bukkit.World mainWorld = org.bukkit.Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
                org.bukkit.Location target = (mainWorld != null) ? mainWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
                event.setTo(target);
                player.sendMessage(org.bukkit.ChatColor.RED + "Du darfst diese Welt derzeit nicht betreten. Du wurdest zum Spawn teleportiert.");
            }
            return;
        }
    }

    private void enforceWorldAccess(Player player) {
        // Resolve event/lobby worlds from any active session that references the player's current world
        String currentWorld = player.getWorld().getName();

        for (EventSession session : plugin.getEventManager().getActiveSessions().values()) {
            String eventWorldName = session.getConfig().getEventWorld();
            String lobbyWorldName = session.getConfig().getLobbyWorld();

            // Listener only active if worlds are loaded
            if (org.bukkit.Bukkit.getWorld(eventWorldName) == null && org.bukkit.Bukkit.getWorld(lobbyWorldName) == null) {
                continue;
            }

            boolean inEventOrLobby = currentWorld.equals(eventWorldName) || currentWorld.equals(lobbyWorldName);
            if (!inEventOrLobby) {
                continue;
            }

            // Allow if player is in an active PvP match in these worlds
            de.zfzfg.pvpwager.models.Match match = plugin.getMatchManager().getMatchByPlayer(player);
            boolean inActiveMatch = match != null && match.getState() == de.zfzfg.pvpwager.models.MatchState.FIGHTING;
            if (inActiveMatch) {
                return;
            }

            // Allow staff with bypass or explicit world access
            if (player.isOp() || player.hasPermission("eventpvp.opbypass") || player.hasPermission("eventpvp.world.access")) {
                return;
            }

            // Only enforce when event not active (JOIN_PHASE/COUNTDOWN/RUNNING considered active)
            de.zfzfg.eventplugin.session.EventSession.EventState state = session.getState();
            boolean eventActive = state == de.zfzfg.eventplugin.session.EventSession.EventState.JOIN_PHASE ||
                                  state == de.zfzfg.eventplugin.session.EventSession.EventState.COUNTDOWN ||
                                  state == de.zfzfg.eventplugin.session.EventSession.EventState.RUNNING;
            if (!eventActive) {
                // Teleport player to main-world spawn or first world spawn
                org.bukkit.World mainWorld = org.bukkit.Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
                org.bukkit.Location target = (mainWorld != null) ? mainWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
                player.teleport(target);
                player.sendMessage(org.bukkit.ChatColor.RED + "Du darfst diese Welt derzeit nicht betreten. Du wurdest zum Spawn teleportiert.");
            }
            return;
        }
    }
}