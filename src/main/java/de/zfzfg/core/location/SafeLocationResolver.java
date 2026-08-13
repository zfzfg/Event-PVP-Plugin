package de.zfzfg.core.location;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Beantwortet die eine Frage, die im Plugin an vier Stellen unabhaengig voneinander
 * beantwortet wurde: <em>Wohin gehoert dieser Spieler, wenn er hier nicht bleiben kann?</em>
 *
 * <p>Vorher lagen eigene Ketten in {@code VoidProtectionListener}, {@code MultiverseHelper},
 * {@code EventListener} und {@code SpectatorRecoveryListener} - mit unterschiedlichen
 * Prioritaeten. Der Spectator-Pfad sprang sogar direkt zum Hauptwelt-Spawn und versuchte die
 * gespeicherte Position gar nicht erst. Eine Korrektur an einer Stelle erreichte die anderen
 * drei nicht.</p>
 */
public final class SafeLocationResolver {

    /** Ab dieser Hoehe ueber dem Weltboden gilt eine Position nicht mehr als Void. */
    private static final int MIN_HEIGHT_MARGIN = 5;

    /** Ab dieser Abweichung gilt ein Teleport als misslungen und wird wiederholt. */
    private static final double MAX_DRIFT_BLOCKS = 50.0;

    private final EventPlugin plugin;

    public SafeLocationResolver(EventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Die beste erreichbare Position fuer diesen Spieler.
     *
     * <p>Reihenfolge, absteigend nach Genauigkeit:</p>
     * <ol>
     *   <li>hinterlegte Rueckkehr-Position (ueberlebt einen Neustart)</li>
     *   <li>Ursprungsposition eines laufenden PvP-Matches</li>
     *   <li>Bett bzw. Respawn-Anker des Spielers</li>
     *   <li>Spawn der Hauptwelt</li>
     *   <li>Spawn der ersten geladenen Welt - die letzte Instanz</li>
     * </ol>
     *
     * @return nie {@code null}, solange der Server mindestens eine Welt geladen hat
     */
    public Location resolve(Player player) {
        UUID playerId = player.getUniqueId();

        // 1. Hinterlegte Rueckkehr-Position
        StoredReturn stored = plugin.getReturnLocations().peek(playerId);
        if (stored != null) {
            Location location = stored.toLocation();
            if (isSafe(location)) {
                return location;
            }
        }

        // 2. Ursprungsposition aus einem laufenden Match
        Location matchOrigin = matchOrigin(player);
        if (isSafe(matchOrigin)) {
            return matchOrigin;
        }

        // 3. Bett bzw. Respawn-Anker
        Location respawn = player.getRespawnLocation();
        if (isSafe(respawn)) {
            return respawn;
        }

        // 4./5. Hauptwelt, sonst irgendeine geladene Welt
        return fallbackSpawn();
    }

    private Location matchOrigin(Player player) {
        try {
            if (plugin.getMatchManager() == null) {
                return null;
            }
            Match match = plugin.getMatchManager().getMatchByPlayer(player);
            return match == null ? null : match.getOriginalLocation(player);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Spawn der Hauptwelt, ersatzweise der ersten geladenen Welt.
     *
     * @return {@code null} nur, wenn der Server ueberhaupt keine Welt geladen hat
     */
    public Location fallbackSpawn() {
        String mainWorldName = plugin.getConfigManager() == null
                ? null
                : plugin.getConfigManager().getMainWorld();

        World world = mainWorldName == null ? null : Bukkit.getWorld(mainWorldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world == null ? null : world.getSpawnLocation();
    }

    /**
     * Ob eine Position benutzbar ist: Welt vorhanden und geladen, und nicht im Void.
     */
    public boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = Bukkit.getWorld(location.getWorld().getName());
        if (world == null) {
            return false;
        }
        return location.getY() >= world.getMinHeight() + MIN_HEIGHT_MARGIN;
    }

    /**
     * Teleportiert und prueft kurz darauf nach, ob es gewirkt hat.
     *
     * <p>Ein anderes Plugin oder ein nachlaufender Respawn kann den Teleport ueberschreiben.
     * Das Muster stammt aus dem Respawn-Pfad des Event-Moduls und steht hier allen Aufrufern
     * zur Verfuegung.</p>
     *
     * @return false, wenn schon der erste Teleport abgelehnt wurde
     */
    public boolean teleportSafely(Player player, Location target) {
        if (player == null || !isSafe(target)) {
            return false;
        }
        if (!player.teleport(target)) {
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-teleport-rejected",
                    "player", player.getName()));
            return false;
        }
        verify(player, target.clone(), true);
        return true;
    }

    /**
     * Nur nachkontrollieren, ohne selbst zu teleportieren.
     *
     * <p>Fuer den Respawn-Pfad: dort setzt {@code PlayerRespawnEvent.setRespawnLocation()}
     * das Ziel, ein eigener Teleport waere falsch. Ueberschreibt ein anderes Plugin den
     * Respawn trotzdem, korrigiert diese Pruefung ihn.</p>
     */
    public void verifyArrival(Player player, Location expected) {
        if (player != null && expected != null) {
            verify(player, expected.clone(), true);
        }
    }

    /** Nachkontrolle; {@code mayRetry} verhindert eine Endlosschleife aus Korrekturen. */
    private void verify(Player player, Location expected, boolean mayRetry) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location current = player.getLocation();
            if (matches(current, expected)) {
                return;
            }
            if (mayRetry) {
                player.teleport(expected);
                verify(player, expected, false);
                return;
            }
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-teleport-drift",
                    "player", player.getName(),
                    "world", expected.getWorld() == null ? "?" : expected.getWorld().getName()));
        }, de.zfzfg.core.util.Time.ticks(5));
    }

    private boolean matches(Location current, Location expected) {
        if (current.getWorld() == null || expected.getWorld() == null) {
            return false;
        }
        if (!current.getWorld().getName().equals(expected.getWorld().getName())) {
            return false;
        }
        return current.distance(expected) <= MAX_DRIFT_BLOCKS;
    }
}
