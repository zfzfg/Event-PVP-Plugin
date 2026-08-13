package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Stellt sicher, dass verwaiste Spectatoren (z.B. nach Disconnect/Reconnect
 * oder wenn Match während Spectator-Abwesenheit endet) korrekt aus dem
 * SPECTATOR-Modus geholt werden.
 */
public class SpectatorRecoveryListener implements Listener {

    private final EventPlugin plugin;

    public SpectatorRecoveryListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Pruefe ob Spieler in SPECTATOR ist, aber keinem aktiven Match zugeordnet
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }

        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match != null) {
            // Spieler ist noch in einem aktiven Match als Spectator - OK
            return;
        }

        // Pruefe Event-Sessions (falls Spectator ein Event zuschaute)
        boolean eventSpectator = plugin.getEventManager().getPlayerSession(player).isPresent();
        if (eventSpectator) {
            return;
        }

        // Spieler ist SPECTATOR ohne aktives Match/Event - resette
        plugin.getLogger().info(plugin.getConsoleMsg("spectator-recovered", "player", player.getName()));

        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Frueher ging es hier direkt zum Hauptwelt-Spawn - die gespeicherte Position wurde
        // nicht einmal versucht. Jetzt dieselbe Kette wie ueberall sonst.
        org.bukkit.Location target = plugin.getSafeLocations().resolve(player);
        if (target != null && plugin.getSafeLocations().teleportSafely(player, target)) {
            plugin.getReturnLocations().consume(player.getUniqueId());
        }
    }
}
