package de.zfzfg.core.location;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistentes Verzeichnis der Positionen, an die Spieler zurueckgehoeren.
 *
 * <p>Das Gegenstueck zum Inventar-Journal: fuer das Inventar gibt es
 * {@code inventory-guard.yml}, fuer die Position bisher nichts. Ein Absturz waehrend eines
 * Events gab dem Spieler zwar seine Items zurueck, seine Position war aber verloren - er
 * stand danach in der Eventwelt, ohne Weg zurueck.</p>
 *
 * <p>Bewusst eine <b>eigene Datei</b> statt eines Feldes im Guard-Journal: im Legacy-Modus
 * ({@code provider: none}) wird absichtlich kein Guard-Eintrag angelegt, teleportiert wird
 * dort aber trotzdem. Die Rueckkehr-Position muss in jedem Modus ueberleben.</p>
 *
 * <p>Geschrieben wird <b>synchron</b>, aus demselben Grund wie beim Guard: die Datei
 * enthaelt im Normalbetrieb fast nichts, und Absturzsicherheit ist hier mehr wert als die
 * eingesparten Millisekunden.</p>
 */
public final class ReturnLocationStore {

    private static final String FILE_NAME = "player-return-locations.yml";

    private final EventPlugin plugin;
    private final Map<UUID, StoredReturn> entries = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();
    private volatile boolean loaded;

    public ReturnLocationStore(EventPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ schreiben

    /**
     * Merkt sich, wohin dieser Spieler zurueckgehoert.
     *
     * <p>Ein bestehender Eintrag wird <b>nicht</b> ueberschrieben: die zweite Position
     * entstuende bereits in der Event- oder Arenawelt und machte die erste - die einzig
     * richtige - wertlos. Dieselbe Ueberlegung wie bei {@code InventoryGuard.open()}.</p>
     */
    public void remember(UUID playerId, Location location, ReturnReason reason) {
        if (playerId == null || location == null || location.getWorld() == null) {
            return;
        }
        if (entries.containsKey(playerId)) {
            return;
        }
        entries.put(playerId, StoredReturn.of(playerId, location, reason));
        save();
    }

    /** Verwirft den Eintrag, ohne ihn zu lesen - nach einem gelungenen Rueckweg. */
    public void forget(UUID playerId) {
        if (playerId != null && entries.remove(playerId) != null) {
            save();
        }
    }

    // --------------------------------------------------------------------- lesen

    /** Liest den Eintrag, ohne ihn zu verbrauchen. */
    public StoredReturn peek(UUID playerId) {
        return playerId == null ? null : entries.get(playerId);
    }

    /**
     * Liest den Eintrag und entfernt ihn.
     *
     * <p>Nur aufrufen, wenn der Rueckweg auch tatsaechlich angetreten wird - sonst geht die
     * einzige Spur zur Ursprungsposition verloren.</p>
     */
    public StoredReturn consume(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        StoredReturn removed = entries.remove(playerId);
        if (removed != null) {
            save();
        }
        return removed;
    }

    public boolean has(UUID playerId) {
        return playerId != null && entries.containsKey(playerId);
    }

    public Collection<StoredReturn> all() {
        return new ArrayList<>(entries.values());
    }

    public int size() {
        return entries.size();
    }

    /** Wie viele Eintraege aelter sind als die angegebene Spanne. */
    public int countOlderThan(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        int count = 0;
        for (StoredReturn entry : entries.values()) {
            if (entry.savedAt() < cutoff) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- persistenz

    /** Liest die Datei. Einmal beim Start. */
    public void load() {
        synchronized (fileLock) {
            entries.clear();
            File file = file();
            if (file.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = cfg.getConfigurationSection("locations");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            UUID id = UUID.fromString(key);
                            ConfigurationSection sec = root.getConfigurationSection(key);
                            if (sec == null) {
                                continue;
                            }
                            entries.put(id, new StoredReturn(id,
                                    sec.getString("world", ""),
                                    sec.getDouble("x"),
                                    sec.getDouble("y"),
                                    sec.getDouble("z"),
                                    (float) sec.getDouble("yaw"),
                                    (float) sec.getDouble("pitch"),
                                    ReturnReason.from(sec.getString("reason")),
                                    sec.getLong("saved-at", System.currentTimeMillis())));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning(plugin.getConsoleMsg("return-entry-invalid",
                                    "entry", key));
                        }
                    }
                }
            }
            loaded = true;
        }
    }

    private void save() {
        if (!loaded) {
            return;
        }
        synchronized (fileLock) {
            try {
                YamlConfiguration cfg = new YamlConfiguration();
                cfg.set("version", 1);
                for (StoredReturn entry : entries.values()) {
                    String key = "locations." + entry.playerId();
                    cfg.set(key + ".world", entry.worldName());  // i18n-ignore: YAML-Pfadfragment in player-return-locations.yml
                    cfg.set(key + ".x", entry.x());  // i18n-ignore: YAML-Pfadfragment
                    cfg.set(key + ".y", entry.y());  // i18n-ignore: YAML-Pfadfragment
                    cfg.set(key + ".z", entry.z());  // i18n-ignore: YAML-Pfadfragment
                    cfg.set(key + ".yaw", entry.yaw());  // i18n-ignore: YAML-Pfadfragment
                    cfg.set(key + ".pitch", entry.pitch());  // i18n-ignore: YAML-Pfadfragment
                    cfg.set(key + ".reason", entry.reason().id());  // i18n-ignore: YAML-Pfadfragment
                    cfg.set(key + ".saved-at", entry.savedAt());  // i18n-ignore: YAML-Pfadfragment
                }
                File dir = plugin.getDataFolder();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                cfg.save(file());
            } catch (Exception e) {
                plugin.getLogger().severe(plugin.getConsoleMsg("return-save-failed",
                        "error", String.valueOf(e.getMessage())));
            }
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * Beim Herunterfahren: nur sicherstellen, dass alles auf der Platte liegt.
     *
     * <p>Bewusst <b>ohne</b> Aufraeumen: wer beim Shutdown noch einen Eintrag hat, ist genau
     * der Spieler, der ihn beim naechsten Start braucht.</p>
     */
    public void shutdown() {
        save();
        if (!entries.isEmpty()) {
            plugin.getLogger().info(plugin.getConsoleMsg("return-shutdown-open",
                    "count", String.valueOf(entries.size())));
        }
    }
}
