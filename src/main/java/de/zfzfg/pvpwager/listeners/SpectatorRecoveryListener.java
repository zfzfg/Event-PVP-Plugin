package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.GameMode;
import org.bukkit.World;
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
        plugin.getLogger().info("[SpectatorRecovery] " + player.getName() +
            " war im SPECTATOR ohne aktives Match/Event. Resetting...");

        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Teleportiere zur Hauptwelt als Fallback
        String mainWorldName = plugin.getConfigManager().getMainWorld();
        World mainWorld = mainWorldName != null
            ? org.bukkit.Bukkit.getWorld(mainWorldName)
            : null;
        if (mainWorld == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
            mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
        }
        if (mainWorld != null) {
            player.teleport(mainWorld.getSpawnLocation());
            plugin.getLogger().info("[SpectatorRecovery] " + player.getName() +
                " zur Hauptwelt teleportiert.");
        }
    }
}
