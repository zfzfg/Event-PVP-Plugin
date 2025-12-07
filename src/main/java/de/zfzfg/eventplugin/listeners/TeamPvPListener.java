package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.TeamManager;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.eventplugin.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

public class TeamPvPListener implements Listener {
    
    private final EventPlugin plugin;
    
    public TeamPvPListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Prüfe ob das Opfer ein Spieler ist
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player victim = (Player) event.getEntity();
        Player attacker = null;
        
        // Ermittle den Angreifer (direkt oder durch Projektil)
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        
        // Wenn kein Spieler-Angreifer, ignoriere Event
        if (attacker == null) {
            return;
        }
        
        // Prüfe ob beide Spieler in einem Event sind
        Optional<EventSession> attackerSession = plugin.getEventManager().getPlayerSession(attacker);
        Optional<EventSession> victimSession = plugin.getEventManager().getPlayerSession(victim);
        
        if (!attackerSession.isPresent() || !victimSession.isPresent()) {
            return;
        }
        
        // Prüfe ob es das gleiche Event ist
        if (!attackerSession.get().equals(victimSession.get())) {
            return;
        }
        
        EventSession session = attackerSession.get();
        
        // Prüfe ob das Event läuft
        if (session.getState() != EventSession.EventState.RUNNING) {
            return;
        }
        
        // Prüfe ob es ein Team-Event ist
        EventConfig.GameMode gameMode = session.getConfig().getGameMode();
        if (gameMode != EventConfig.GameMode.TEAM_2 && gameMode != EventConfig.GameMode.TEAM_3) {
            return;
        }
        
        // Hole TeamManager
        TeamManager teamManager = session.getTeamManager();
        if (teamManager == null) {
            return;
        }
        
        // Prüfe Friendly-Fire Einstellung
        EventConfig.TeamSettings teamSettings = session.getConfig().getTeamSettings();
        if (teamSettings == null || teamSettings.isFriendlyFire()) {
            return; // Friendly-Fire ist erlaubt
        }
        
        // Prüfe ob beide Spieler im gleichen Team sind
        if (teamManager.areTeammates(attacker, victim)) {
            event.setCancelled(true);
            
            // Sende Nachricht an Angreifer
            TeamManager.Team team = teamManager.getPlayerTeam(attacker);
            if (team != null) {
                attacker.sendMessage(ColorUtil.color(
                    plugin.getConfigManager().getPrefix() + 
                    " &cDu kannst deine Teammitglieder nicht angreifen!"
                ));
            }
        }
    }
}