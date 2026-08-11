package de.zfzfg.core.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Eine hinterlegte Rueckkehr-Position.
 *
 * <p>Haelt den Weltnamen als Zeichenkette statt einer {@link World}-Referenz: die Welt kann
 * beim Neustart noch nicht geladen oder ganz verschwunden sein, und ein Eintrag soll das
 * ueberleben, statt beim Einlesen zu scheitern.</p>
 */
public final class StoredReturn {

    private final UUID playerId;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final ReturnReason reason;
    private final long savedAt;

    public StoredReturn(UUID playerId, String worldName, double x, double y, double z,
                        float yaw, float pitch, ReturnReason reason, long savedAt) {
        this.playerId = playerId;
        this.worldName = worldName == null ? "" : worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.reason = reason == null ? ReturnReason.UNKNOWN : reason;
        this.savedAt = savedAt;
    }

    /** Baut einen Eintrag aus einer lebenden Location. */
    public static StoredReturn of(UUID playerId, Location location, ReturnReason reason) {
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        return new StoredReturn(playerId, world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), reason, System.currentTimeMillis());
    }

    public UUID playerId() { return playerId; }
    public String worldName() { return worldName; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public ReturnReason reason() { return reason; }
    public long savedAt() { return savedAt; }

    /**
     * Die Position als {@link Location}, sofern die Welt gerade geladen ist.
     *
     * @return null, wenn die Welt fehlt - der Aufrufer muss dann auf seine naechste
     *         Rueckfallebene ausweichen
     */
    public Location toLocation() {
        if (worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** Fuer das Web-Panel und {@code /eventpvp rescue list}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("player", playerId.toString());
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("reason", reason.id());
        map.put("savedAt", savedAt);
        map.put("worldLoaded", Bukkit.getWorld(worldName) != null);
        return map;
    }
}
