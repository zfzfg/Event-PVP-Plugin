package de.zfzfg.core.location;

import de.zfzfg.core.util.Time;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * Holt Spieler heraus, die in einer Event- oder Arenawelt gestrandet sind.
 *
 * <p>Der Fall, den bisher niemand besass: ein Spieler fliegt mitten im Event raus - Absturz,
 * Kick, Verbindungsabbruch - und loggt sich wieder ein, nachdem das Event laengst vorbei ist.
 * Sein Inventar holt das Guard-Journal zurueck, aber er selbst steht weiterhin in der
 * Eventwelt. {@code SpectatorRecoveryListener} deckt nur Spieler im Spectator-Modus ab.</p>
 *
 * <p>Laeuft 20 Ticks nach dem Join, also <b>nach</b> dem Inventar-Netz
 * ({@code InventoryGuardListener}, 10 Ticks): erst die Items, dann der Weg nach Hause.</p>
 */
public final class StrandedPlayerListener implements Listener {

    private static final long JOIN_DELAY_TICKS = 20L;

    private final EventPlugin plugin;

    public StrandedPlayerListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            rescueIfStranded(player);
        }, Time.ticks(JOIN_DELAY_TICKS));
    }

    /**
     * Prueft die beiden Bedingungen und holt den Spieler notfalls zurueck.
     *
     * <p>Sichtbar (nicht privat), damit {@code /eventpvp rescue <spieler>} denselben Weg
     * nehmen kann statt einen zweiten daneben.</p>
     *
     * @return true, wenn tatsaechlich zurueckgeholt wurde
     */
    public boolean rescueIfStranded(Player player) {
        if (!isStranded(player)) {
            return false;
        }
        return rescue(player);
    }

    /**
     * Ob dieser Spieler festsitzt.
     *
     * <p>Zwei Wege dorthin: er steht in einer Plugin-Welt ohne laufende Sitzung, oder er hat
     * zwar eine laufende Sitzung, aber die zugehoerige Welt ist nicht geladen - dann ist die
     * Sitzung nicht mehr spielbar und der Rueckweg die einzige richtige Antwort.</p>
     */
    public boolean isStranded(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return true;
        }
        String worldName = location.getWorld().getName();

        if (!plugin.getPluginWorlds().isManaged(worldName)) {
            return false;
        }
        if (hasRunningSession(player)) {
            // Rejoin in ein laufendes Match oder Event - die Module regeln das selbst.
            return false;
        }
        return true;
    }

    private boolean hasRunningSession(Player player) {
        UUID playerId = player.getUniqueId();
        try {
            if (plugin.getEventManager() != null
                    && plugin.getEventManager().isPlayerInEvent(playerId)) {
                return true;
            }
            if (plugin.getMatchManager() != null
                    && plugin.getMatchManager().getMatchIdByPlayer(playerId) != null) {
                return true;
            }
        } catch (Exception e) {
            // Im Zweifel als laufend behandeln: einen Spieler faelschlich aus einem aktiven
            // Match zu reissen waere schlimmer, als ihn einmal stehen zu lassen.
            plugin.getLogger().warning(plugin.getConsoleMsg("stranded-check-failed",
                    "player", player.getName(), "error", String.valueOf(e.getMessage())));
            return true;
        }
        return false;
    }

    /** Bringt den Spieler an seine Rueckkehr-Position, sonst zum Spawn. */
    public boolean rescue(Player player) {
        SafeLocationResolver resolver = plugin.getSafeLocations();
        Location target = resolver.resolve(player);

        if (target == null) {
            plugin.getLogger().severe(plugin.getConsoleMsg("stranded-no-target",
                    "player", player.getName()));
            return false;
        }

        String fromWorld = player.getWorld() == null ? "?" : player.getWorld().getName();
        if (!resolver.teleportSafely(player, target)) {
            plugin.getLogger().severe(plugin.getConsoleMsg("stranded-rescue-failed",
                    "player", player.getName(), "world", fromWorld));
            return false;
        }

        // Erst nach dem gelungenen Teleport verbrauchen - sonst waere die Position bei einem
        // Fehlschlag verloren und der naechste Versuch haette nichts mehr.
        plugin.getReturnLocations().consume(player.getUniqueId());

        player.sendMessage(ColorUtil.color(
                plugin.getConfigManager().getMessage("system.stranded-rescued")));

        plugin.getLogger().info(plugin.getConsoleMsg("stranded-rescued",
                "player", player.getName(),
                "from", fromWorld,
                "to", target.getWorld() == null ? "?" : target.getWorld().getName()));
        return true;
    }
}
