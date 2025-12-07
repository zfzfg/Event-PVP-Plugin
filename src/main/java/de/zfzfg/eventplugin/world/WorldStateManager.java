package de.zfzfg.eventplugin.world;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.util.MultiverseHelper;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WorldStateManager {
    private final EventPlugin plugin;
    private final MultiverseHelper mvHelper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis = 10_000; // 10s Cache TTL
    private final Object stateMutex = new Object();
    private final boolean backupEnabled;
    private final boolean backupAsync;

    public WorldStateManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.mvHelper = new MultiverseHelper(plugin);
        boolean be = true;
        boolean ba = false;
        try {
            if (plugin.getCoreConfigManager() != null && plugin.getCoreConfigManager().getConfig() != null) {
                be = plugin.getCoreConfigManager().getConfig().getBoolean("settings.arena-regeneration.backups", true);
                ba = plugin.getCoreConfigManager().getConfig().getBoolean("settings.arena-regeneration.backup-async", false);
            }
        } catch (Exception ignored) {}
        this.backupEnabled = be;
        this.backupAsync = ba;
    }

    public void ensureEventWorldReady(EventConfig config) {
        synchronized (stateMutex) {
            String eventWorld = config.getEventWorld();
            String cloneSource = config.getCloneSourceEventWorld();
            World world = Bukkit.getWorld(eventWorld);
            boolean loaded = world != null;
            boolean exists = doesWorldFolderExist(eventWorld);
            cache.put(eventWorld, new CacheEntry(loaded, exists));
            mvHelper.ensureWorldReady(eventWorld, cloneSource, config.shouldRegenerateEventWorld(), backupEnabled, backupAsync);
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public boolean isWorldLoadedCached(String worldName) {
        CacheEntry entry = cache.get(worldName);
        if (entry == null || entry.expired(ttlMillis)) return false;
        return entry.loaded;
    }

    public boolean doesWorldExistCached(String worldName) {
        CacheEntry entry = cache.get(worldName);
        if (entry == null || entry.expired(ttlMillis)) return false;
        return entry.exists;
    }

    public boolean doesWorldFolderExist(String worldName) {
        File container = Bukkit.getWorldContainer();
        return new File(container, worldName).exists();
    }

    public void backupWorld(String worldName) {
        try {
            File container = Bukkit.getWorldContainer();
            File worldFolder = new File(container, worldName);
            if (!worldFolder.exists()) {
                plugin.getLogger().warning("Backup übersprungen: Weltordner nicht gefunden: " + worldName);
                return;
            }

            File backupsDir = new File(plugin.getDataFolder(), "backups");
            if (!backupsDir.exists()) backupsDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File zipFile = new File(backupsDir, worldName + "_" + timestamp + ".zip");

            zipFolder(worldFolder.toPath(), zipFile.toPath());
            plugin.getLogger().info("Backup erstellt: " + zipFile.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Backup fehlgeschlagen: " + e.getMessage());
            if (plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }

    private void zipFolder(Path sourceDirPath, Path zipFilePath) throws IOException {
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            Files.walk(sourceDirPath)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(sourceDirPath.relativize(path).toString());
                    try {
                        zs.putNextEntry(zipEntry);
                        Files.copy(path, zs);
                        zs.closeEntry();
                    } catch (IOException e) {
                        plugin.getLogger().severe("Fehler beim Zippen: " + e.getMessage());
                    }
                });
        }
    }

    private static class CacheEntry {
        final boolean loaded;
        final boolean exists;
        final long timestamp;

        CacheEntry(boolean loaded, boolean exists) {
            this.loaded = loaded;
            this.exists = exists;
            this.timestamp = System.currentTimeMillis();
        }

        boolean expired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
}