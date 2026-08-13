package de.zfzfg.eventplugin.security;

import de.zfzfg.eventplugin.util.ColorUtil;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Sicherheits-/Modus-Listener:
 * - Verhindert, dass Zuschauer den Spectator-Modus verlassen
 */
public class PlayerModeListener implements Listener {

    private final EventPlugin plugin;

    public PlayerModeListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private String getMsg(String key) {
        String msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, "");
        return ColorUtil.color(msg);
    }

    // Entfernt: onCommandPreprocess. Die hardcodierte Blockierung von /v und /fly ist
    // weggefallen; der Handler tat danach nichts mehr, lief aber bei jedem Chat-Befehl mit.
    // Die Spectator-Mode-Protection unten ist davon unabhaengig.

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();

        // Prüfe Event-Zuschauer
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        boolean eventSpectator = sessionOpt.isPresent() && sessionOpt.get().isSpectator(player);

        // Prüfe PvP-Zuschauer
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        boolean pvpSpectator = match != null && match.getSpectators().contains(player.getUniqueId());

        if (eventSpectator || pvpSpectator) {
            // Zuschauer dürfen ausschließlich im Spectator-Modus bleiben
            if (event.getNewGameMode() != org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                player.sendMessage(getMsg("spectator-mode-only"));
            }
        }
    }
}