package de.zfzfg.core.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Utility für plattformoptimierte Teleportationen.
 *
 * <p>Auf Paper/Purpur wird {@code player.teleportAsync(Location)} genutzt, um
 * Chunk-Lade-Lags auf dem Haupt-Thread vollständig zu verhindern.
 * Auf Vanilla Spigot wird automatisch auf das synchrone {@code player.teleport(Location)}
 * zurückgegriffen und als abgeschlossener Future zurückgegeben.</p>
 */
public final class TeleportUtil {

    private TeleportUtil() {}

    /**
     * Teleportiert einen Spieler asynchron auf Paper/Purpur bzw. synchron auf Spigot.
     *
     * @param player der zu teleportierende Spieler
     * @param location das Ziel
     * @return CompletableFuture mit dem Ergebnis (true bei Erfolg)
     */
    public static CompletableFuture<Boolean> teleport(Player player, Location location) {
        if (player == null || location == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (Platform.isPaper()) {
            return player.teleportAsync(location);
        } else {
            boolean success = player.teleport(location);
            return CompletableFuture.completedFuture(success);
        }
    }
}
