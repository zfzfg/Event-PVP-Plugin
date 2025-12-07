package de.zfzfg.core.world;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Zentrale Multiverse-Hilfsklasse, die von Event- und PvP-Modulen verwendet wird.
 * Vereinigt Lade-, Klon-, Lösch- und Regenerationsfunktionen.
 */
public class MultiverseHelper {

    private final EventPlugin plugin;

    public MultiverseHelper(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMultiverseAvailable() {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        return mv != null && mv.isEnabled();
    }

    public void unloadWorld(String worldName) {
        if (!isMultiverseAvailable()) return;
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            teleportPlayersOutWithSavedLocations(world);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + worldName);
        }
    }

    public void loadWorld(String worldName, LoadCallback callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.onResult(false, "Multiverse-Core nicht installiert");
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (callback != null) callback.onResult(true, "Welt bereits geladen");
            return;
        }

        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
        String env = guessEnv(worldName);

        String cloneSource = resolveCloneSourceForWorld(worldName);
        if (!worldFolder.exists() && cloneSource != null && !cloneSource.trim().isEmpty()) {
            plugin.getLogger().info("Clone-First: " + cloneSource.trim() + " -> " + worldName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv clone " + cloneSource.trim() + " " + worldName);
            plugin.getTaskManager().runLater(() -> {
                plugin.getLogger().info("Loading cloned world: " + worldName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);
                boolean ok = (Bukkit.getWorld(worldName) != null);
                if (callback != null) callback.onResult(ok, ok ? "Clone+Load erfolgreich" : "Clone+Load fehlgeschlagen");
            }, 40L);
            return;
        }

        // 1) Versuche: mv load (falls bereits bekannt aber nicht geladen)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + worldName);
        plugin.getTaskManager().runLater(() -> {
            if (Bukkit.getWorld(worldName) != null) {
                if (callback != null) callback.onResult(true, "mv load erfolgreich");
                return;
            }

            // 2) Wenn Ordner existiert, versuche mv import
            if (worldFolder.exists()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + worldName + " " + env);
                plugin.getTaskManager().runLater(() -> {
                    if (Bukkit.getWorld(worldName) != null) {
                        if (callback != null) callback.onResult(true, "mv import erfolgreich");
                        return;
                    }

                    // 3) Fallback: mv create (neue Welt anlegen)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + worldName + " " + env);
                    plugin.getTaskManager().runLater(() -> {
                        boolean ok = (Bukkit.getWorld(worldName) != null);
                        if (callback != null) callback.onResult(ok, ok ? "mv create erfolgreich" : "Welt konnte nicht erstellt/geladen werden");
                    }, 40L);
                }, 40L);
            } else {
                // Ordner existiert nicht -> direkt mv create
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + worldName + " " + env);
                plugin.getTaskManager().runLater(() -> {
                    boolean ok = (Bukkit.getWorld(worldName) != null);
                    if (callback != null) callback.onResult(ok, ok ? "mv create erfolgreich" : "Welt konnte nicht erstellt/geladen werden");
                }, 40L);
            }
        }, 40L);
    }

    public void regenerateWorld(String worldName) {
        regenerateWorld(worldName, null);
    }

    public void regenerateWorld(String worldName, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (callback != null) callback.run();
            return;
        }
        teleportPlayersOutWithSavedLocations(world);

        plugin.getTaskManager().runLater(() -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv regen " + worldName);
            plugin.getTaskManager().runLater(() -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
                if (callback != null) callback.run();
            }, 40L);
        }, 40L);
    }

    public void deleteWorld(String worldName, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            teleportPlayersOutWithSavedLocations(world);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + worldName);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
        plugin.getTaskManager().runLater(() -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
            if (callback != null) callback.run();
        }, 40L);
    }

    public void cloneWorld(String sourceWorld, String targetWorld, Runnable callback) {
        if (!isMultiverseAvailable()) {
            if (callback != null) callback.run();
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv clone " + sourceWorld + " " + targetWorld);
        plugin.getTaskManager().runLater(() -> {
            if (callback != null) callback.run();
        }, 40L);
    }

    public interface LoadCallback {
        void onResult(boolean success, String message);
    }

    private void teleportPlayersOutWithSavedLocations(World world) {
        World mainWorld = Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) {
            mainWorld = Bukkit.getWorlds().get(0);
        }
        for (Player p : world.getPlayers()) {
            try {
                java.util.UUID pid = p.getUniqueId();
                org.bukkit.Location saved = plugin.getEventManager().getSavedLocation(pid);
                if (saved != null && saved.getWorld() != null) {
                    p.teleport(saved);
                    plugin.getEventManager().clearSavedLocation(pid);
                    continue;
                }
                de.zfzfg.pvpwager.models.Match m = plugin.getMatchManager().getMatchByPlayer(p);
                if (m != null) {
                    org.bukkit.Location origin = m.getOriginalLocation(p);
                    if (origin != null && origin.getWorld() != null) {
                        p.teleport(origin);
                        continue;
                    }
                }
                // Fallback: Teleportiere zum Hauptwelt-Spawn
                if (mainWorld != null) {
                    p.teleport(mainWorld.getSpawnLocation());
                }
            } catch (Exception ignored) {}
        }
    }

    private String guessEnv(String worldName) {
        String lower = worldName.toLowerCase();
        if (lower.contains("nether")) return "NETHER";
        if (lower.contains("end")) return "THE_END";
        return "NORMAL";
    }

    private String resolveCloneSourceForWorld(String worldName) {
        try {
            java.util.Map<String, de.zfzfg.pvpwager.models.Arena> arenas = plugin.getArenaManager().getArenas();
            for (de.zfzfg.pvpwager.models.Arena a : arenas.values()) {
                if (worldName.equalsIgnoreCase(a.getArenaWorld())) {
                    String src = a.getCloneSourceWorld();
                    if (src != null && !src.trim().isEmpty()) return src.trim();
                }
            }
        } catch (Exception ignored) {}
        try {
            java.util.Map<String, de.zfzfg.eventplugin.model.EventConfig> events = plugin.getConfigManager().getAllEvents();
            for (de.zfzfg.eventplugin.model.EventConfig e : events.values()) {
                if (worldName.equalsIgnoreCase(e.getEventWorld())) {
                    String src = e.getCloneSourceEventWorld();
                    if (src != null && !src.trim().isEmpty()) return src.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void ensureWorldReady(String worldName, String cloneSource, boolean regenerate, boolean backupEnabled, boolean backupAsync) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        boolean exists = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName).exists();
        if (!exists && cloneSource != null && !cloneSource.trim().isEmpty()) {
            cloneWorld(cloneSource.trim(), worldName, () -> {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "mv load " + worldName);
            });
            return;
        }
        if (world == null && exists) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "mv load " + worldName);
        }
        if (regenerate) {
            if (backupEnabled) {
                if (backupAsync) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> backupWorld(worldName));
                } else {
                    backupWorld(worldName);
                }
            }
            regenerateWorld(worldName);
        }
    }

    public void backupWorld(String worldName) {
        try {
            java.io.File container = org.bukkit.Bukkit.getWorldContainer();
            java.io.File worldFolder = new java.io.File(container, worldName);
            if (!worldFolder.exists()) {
                plugin.getLogger().warning("Backup übersprungen: Weltordner nicht gefunden: " + worldName);
                return;
            }
            java.io.File backupsDir = new java.io.File(plugin.getDataFolder(), "backups");
            if (!backupsDir.exists()) backupsDir.mkdirs();
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            java.io.File zipFile = new java.io.File(backupsDir, worldName + "_" + timestamp + ".zip");
            zipFolder(worldFolder.toPath(), zipFile.toPath());
            plugin.getLogger().info("Backup erstellt: " + zipFile.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Backup fehlgeschlagen: " + e.getMessage());
            try {
                if (plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false)) {
                    e.printStackTrace();
                }
            } catch (Exception ignored) {}
        }
    }

    private void zipFolder(java.nio.file.Path sourceFolderPath, java.nio.file.Path zipPath) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zipPath.toFile()))) {
            java.nio.file.Files.walk(sourceFolderPath)
                .filter(path -> !java.nio.file.Files.isDirectory(path))
                .forEach(path -> {
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(sourceFolderPath.relativize(path).toString());
                    try {
                        zos.putNextEntry(zipEntry);
                        java.nio.file.Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (java.io.IOException ignored) {}
                });
        }
    }
}