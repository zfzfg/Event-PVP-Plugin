package de.zfzfg.eventplugin.storage;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Persists inventory snapshots so admins can restore on errors.
 * Entries older than 30 days are pruned automatically on save.
 */
public class InventorySnapshotStorage {
    private static final String FILE_NAME = "inventory_backups.yml"; // pre-snapshots
    private static final String FILE_NAME_POST = "inventory_post_backups.yml"; // post-snapshots
    private static final long THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    /**
     * Save the player's current inventory and armor along with metadata.
     * Group and reason are informational fields (e.g., "default", "pvpwager" or "event").
     */
    public static void saveSnapshot(EventPlugin plugin, Player player, String group, String reason) {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            File file = new File(folder, FILE_NAME);

            YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

            String key = "entries." + player.getUniqueId().toString();
            List<Map<?, ?>> rawList = cfg.getMapList(key);
            List<Map<String, Object>> list = new ArrayList<>();
            if (rawList != null) {
                for (Map<?, ?> m : rawList) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                    }
                    list.add(mm);
                }
            }

            long now = System.currentTimeMillis();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", now);
            entry.put("created_at", iso(now));
            entry.put("player_name", player.getName());
            entry.put("level", player.getLevel());
            entry.put("group", group);
            entry.put("reason", reason);
            entry.put("world", safeWorldName(player));
            entry.put("inventory_id", nextInventoryIdForWorld(cfg, safeWorldName(player)));
            // eventMatchId is optional; can be injected via enriched API below

            // Store contents and armor as lists to preserve slot order
            ItemStack[] contents = player.getInventory().getContents();
            ItemStack[] armor = player.getInventory().getArmorContents();
            entry.put("contents", toList(contents));
            entry.put("armor", toList(armor));

            list.add(entry);

            // Prune old entries for this player
            pruneList(list, now - THIRTY_DAYS_MILLIS);

            cfg.set(key, list);
            saveYamlAtomic(cfg, file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save inventory snapshot: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error saving inventory snapshot: " + e.getMessage());
        }
    }

    /**
     * Async variant: capture player data on main thread, perform file I/O asynchronously.
     */
    public static void saveSnapshotAsync(EventPlugin plugin, Player player, String group, String reason) {
        // Capture necessary data on the calling thread (expected main thread)
        final java.util.UUID playerId = player.getUniqueId();
        final String playerName = player.getName();
        final int playerLevel = player.getLevel();
        final String worldName = safeWorldName(player);
        final java.util.List<ItemStack> contents = toList(player.getInventory().getContents());
        final java.util.List<ItemStack> armor = toList(player.getInventory().getArmorContents());
        final long now = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File folder = plugin.getDataFolder();
                if (!folder.exists()) folder.mkdirs();
                File file = new File(folder, FILE_NAME);

                YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                String key = "entries." + playerId.toString();
                java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList(key);
                java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
                if (rawList != null) {
                    for (java.util.Map<?, ?> m : rawList) {
                        java.util.Map<String, Object> mm = new java.util.LinkedHashMap<>();
                        for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                        }
                        list.add(mm);
                    }
                }

                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("timestamp", now);
                entry.put("created_at", iso(now));
                entry.put("player_name", playerName);
                entry.put("level", playerLevel);
                entry.put("group", group);
                entry.put("reason", reason);
                entry.put("world", worldName);
                entry.put("inventory_id", nextInventoryIdForWorld(cfg, worldName));
                entry.put("contents", contents);
                entry.put("armor", armor);

                list.add(entry);
                pruneList(list, now - THIRTY_DAYS_MILLIS);
                cfg.set(key, list);
                saveYamlAtomic(cfg, file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save inventory snapshot (async): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected error saving inventory snapshot (async): " + e.getMessage());
            }
        });
    }

    /**
     * Enriched API: save snapshot in pre or post file, auto-select group from config, and attach event/match ID.
     */
    public static void saveSnapshotWithIds(EventPlugin plugin, Player player, String reason, String eventMatchId, boolean post) {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            File file = new File(folder, post ? FILE_NAME_POST : FILE_NAME);

            YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

            String key = "entries." + player.getUniqueId().toString();
            List<Map<?, ?>> rawList = cfg.getMapList(key);
            List<Map<String, Object>> list = new ArrayList<>();
            if (rawList != null) {
                for (Map<?, ?> m : rawList) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                    }
                    list.add(mm);
                }
            }

            String group = resolveGroupForWorld(plugin, safeWorldName(player));
            long now = System.currentTimeMillis();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", now);
            entry.put("created_at", iso(now));
            entry.put("player_name", player.getName());
            entry.put("level", player.getLevel());
            entry.put("group", group);
            entry.put("reason", reason);
            entry.put("world", safeWorldName(player));
            String invId = nextInventoryIdForWorld(cfg, safeWorldName(player));
            entry.put("inventory_id", invId);
            if (eventMatchId != null && !eventMatchId.isEmpty()) entry.put("event_match_id", eventMatchId);

            ItemStack[] contents = player.getInventory().getContents();
            ItemStack[] armor = player.getInventory().getArmorContents();
            entry.put("contents", toList(contents));
            entry.put("armor", toList(armor));

            list.add(entry);
            pruneList(list, now - THIRTY_DAYS_MILLIS);
            cfg.set(key, list);
            saveYamlAtomic(cfg, file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save inventory snapshot: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error saving inventory snapshot: " + e.getMessage());
        }
    }

    /**
     * Async variant for enriched API: capture player data, resolve group, and write asynchronously.
     */
    public static void saveSnapshotWithIdsAsync(EventPlugin plugin, Player player, String reason, String eventMatchId, boolean post) {
        final java.util.UUID playerId = player.getUniqueId();
        final String playerName = player.getName();
        final int playerLevel = player.getLevel();
        final String worldName = safeWorldName(player);
        final String group = resolveGroupForWorld(plugin, worldName);
        final java.util.List<ItemStack> contents = toList(player.getInventory().getContents());
        final java.util.List<ItemStack> armor = toList(player.getInventory().getArmorContents());
        final long now = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File folder = plugin.getDataFolder();
                if (!folder.exists()) folder.mkdirs();
                File file = new File(folder, post ? FILE_NAME_POST : FILE_NAME);

                YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                String key = "entries." + playerId.toString();
                java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList(key);
                java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
                if (rawList != null) {
                    for (java.util.Map<?, ?> m : rawList) {
                        java.util.Map<String, Object> mm = new java.util.LinkedHashMap<>();
                        for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                        }
                        list.add(mm);
                    }
                }

                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("timestamp", now);
                entry.put("created_at", iso(now));
                entry.put("player_name", playerName);
                entry.put("level", playerLevel);
                entry.put("group", group);
                entry.put("reason", reason);
                entry.put("world", worldName);
                String invId = nextInventoryIdForWorld(cfg, worldName);
                entry.put("inventory_id", invId);
                if (eventMatchId != null && !eventMatchId.isEmpty()) entry.put("event_match_id", eventMatchId);
                entry.put("contents", contents);
                entry.put("armor", armor);

                list.add(entry);
                pruneList(list, now - THIRTY_DAYS_MILLIS);
                cfg.set(key, list);
                saveYamlAtomic(cfg, file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save inventory snapshot (async): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected error saving inventory snapshot (async): " + e.getMessage());
            }
        });
    }

    /** Restore a snapshot by 4-digit inventory_id. Searches pre and post files. */
    public static boolean restoreByInventoryId(EventPlugin plugin, String inventoryId) {
        Map<String, Object> entry = findEntryByInventoryId(plugin, FILE_NAME, inventoryId);
        if (entry == null) entry = findEntryByInventoryId(plugin, FILE_NAME_POST, inventoryId);
        if (entry == null) return false;

        String uuidStr = (String) entry.getOrDefault("uuid", null);
        UUID playerId = null;
        if (uuidStr != null) {
            try { playerId = UUID.fromString(uuidStr); } catch (Exception ignored) {}
        }
        // Fallback: try by player_name
        Player target = null;
        if (playerId != null) target = org.bukkit.Bukkit.getPlayer(playerId);
        if (target == null) {
            String playerName = (String) entry.getOrDefault("player_name", null);
            if (playerName != null) target = org.bukkit.Bukkit.getPlayerExact(playerName);
        }
        if (target == null || !target.isOnline()) return false;

        // Restore contents and armor on main thread
        final java.util.List<?> contentsList = (java.util.List<?>) entry.get("contents");
        final java.util.List<?> armorList = (java.util.List<?>) entry.get("armor");
        final org.bukkit.entity.Player restoreTarget = target;
        final String invId = inventoryId;
        try {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    ItemStack[] contents = fromList(contentsList);
                    ItemStack[] armor = fromList(armorList);
                    restoreTarget.getInventory().setContents(contents);
                    restoreTarget.getInventory().setArmorContents(armor);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to restore inventory by ID " + invId + ": " + e.getMessage());
                }
            });
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule inventory restore by ID " + invId + ": " + e.getMessage());
            return false;
        }
    }

    /** Public accessor: fetch snapshot entry metadata by ID (world, event_match_id, player_name). */
    public static Map<String, Object> getEntryByInventoryId(EventPlugin plugin, String inventoryId) {
        Map<String, Object> entry = findEntryByInventoryId(plugin, FILE_NAME, inventoryId);
        if (entry == null) entry = findEntryByInventoryId(plugin, FILE_NAME_POST, inventoryId);
        return entry;
    }

    private static Map<String, Object> findEntryByInventoryId(EventPlugin plugin, String fileName, String inventoryId) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) return null;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            if (cfg.getConfigurationSection("entries") == null) return null;
            Set<String> uuids = cfg.getConfigurationSection("entries").getKeys(false);
            for (String uuidKey : uuids) {
                List<Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                if (rawList == null) continue;
                for (Map<?, ?> m : rawList) {
                    Object idObj = m.get("inventory_id");
                    if (idObj != null && inventoryId.equals(String.valueOf(idObj))) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                        }
                        mm.put("uuid", uuidKey);
                        return mm;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error finding inventory entry by ID: " + e.getMessage());
        }
        return null;
    }

    /** List UUIDs of players that have any snapshots (pre or post). */
    public static java.util.Set<java.util.UUID> listPlayerUuidsWithSnapshots(EventPlugin plugin) {
        java.util.Set<java.util.UUID> set = new java.util.LinkedHashSet<>();
        collectPlayerUuidsFromFile(plugin, FILE_NAME, set);
        collectPlayerUuidsFromFile(plugin, FILE_NAME_POST, set);
        return set;
    }

    private static void collectPlayerUuidsFromFile(EventPlugin plugin, String fileName, java.util.Set<java.util.UUID> out) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) return;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            if (cfg.getConfigurationSection("entries") == null) return;
            for (String uuidKey : cfg.getConfigurationSection("entries").getKeys(false)) {
                java.util.UUID id;
                try { id = java.util.UUID.fromString(uuidKey); } catch (Exception ignored) { id = null; }
                if (id == null) continue;
                java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                if (rawList != null && !rawList.isEmpty()) out.add(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error collecting snapshot players from " + fileName + ": " + e.getMessage());
        }
    }

    /** List all inventory_id values for a specific player (pre and post snapshots). */
    public static java.util.List<String> listInventoryIdsForPlayer(EventPlugin plugin, java.util.UUID playerId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        collectInventoryIdsForPlayerFromFile(plugin, FILE_NAME, playerId, ids);
        collectInventoryIdsForPlayerFromFile(plugin, FILE_NAME_POST, playerId, ids);
        return ids;
    }

    private static void collectInventoryIdsForPlayerFromFile(EventPlugin plugin, String fileName, java.util.UUID playerId, java.util.List<String> out) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) return;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String key = "entries." + playerId.toString();
            java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList(key);
            if (rawList == null) return;
            for (java.util.Map<?, ?> m : rawList) {
                Object idObj = m.get("inventory_id");
                if (idObj != null) out.add(String.valueOf(idObj));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error collecting inventory IDs from " + fileName + ": " + e.getMessage());
        }
    }

    /** Try to find a snapshot owner UUID by a stored player_name (case-insensitive). */
    public static java.util.UUID findSnapshotOwnerUuidByName(EventPlugin plugin, String playerName) {
        java.util.UUID found = findSnapshotOwnerUuidByNameFromFile(plugin, FILE_NAME, playerName);
        if (found != null) return found;
        return findSnapshotOwnerUuidByNameFromFile(plugin, FILE_NAME_POST, playerName);
    }

    private static java.util.UUID findSnapshotOwnerUuidByNameFromFile(EventPlugin plugin, String fileName, String playerName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) return null;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            if (cfg.getConfigurationSection("entries") == null) return null;
            for (String uuidKey : cfg.getConfigurationSection("entries").getKeys(false)) {
                java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                if (rawList == null) continue;
                for (java.util.Map<?, ?> m : rawList) {
                    Object nameObj = m.get("player_name");
                    if (nameObj != null && playerName.equalsIgnoreCase(String.valueOf(nameObj))) {
                        try { return java.util.UUID.fromString(uuidKey); } catch (Exception ignored) { return null; }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error finding snapshot owner by name in " + fileName + ": " + e.getMessage());
        }
        return null;
    }

    /** Get last known stored player_name for a UUID (from snapshots). */
    public static String getLastKnownName(EventPlugin plugin, java.util.UUID playerId) {
        String name = getLastKnownNameFromFile(plugin, FILE_NAME, playerId);
        if (name != null && !name.isEmpty()) return name;
        return getLastKnownNameFromFile(plugin, FILE_NAME_POST, playerId);
    }

    private static String getLastKnownNameFromFile(EventPlugin plugin, String fileName, java.util.UUID playerId) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) return null;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String key = "entries." + playerId.toString();
            java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList(key);
            if (rawList == null || rawList.isEmpty()) return null;
            java.util.Map<?, ?> last = rawList.get(rawList.size() - 1);
            Object nameObj = last.get("player_name");
            return nameObj != null ? String.valueOf(nameObj) : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error reading last known name from " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private static String safeWorldName(Player player) {
        try { return player.getWorld() != null ? player.getWorld().getName() : "unknown"; } catch (Exception e) { return "unknown"; }
    }

    private static String nextInventoryIdForWorld(YamlConfiguration cfg, String world) {
        int max = 0;
        if (cfg.getConfigurationSection("entries") != null) {
            Set<String> uuids = cfg.getConfigurationSection("entries").getKeys(false);
            for (String uuidKey : uuids) {
                List<Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                if (rawList == null) continue;
                for (Map<?, ?> m : rawList) {
                    Object wObj = m.get("world");
                    String w = (wObj != null) ? String.valueOf(wObj) : "";
                    Object idObj = m.get("inventory_id");
                    if (world.equals(w) && idObj != null) {
                        String idStr = String.valueOf(idObj);
                        String digits = extractLastFourDigits(idStr);
                        if (digits != null) {
                            try {
                                int id = Integer.parseInt(digits);
                                if (id > max) max = id;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        int next = max + 1;
        if (next > 9999) next = 1; // wrap-around
        return String.format("%04d", next);
    }

    private static String extractLastFourDigits(String s) {
        if (s == null) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})$").matcher(s);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    public static void saveSnapshotWithIds(EventPlugin plugin, Player player, String reason, String eventMatchId, boolean post, String prefix) {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            File file = new File(folder, post ? FILE_NAME_POST : FILE_NAME);

            YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

            String key = "entries." + player.getUniqueId().toString();
            List<Map<?, ?>> rawList = cfg.getMapList(key);
            List<Map<String, Object>> list = new ArrayList<>();
            if (rawList != null) {
                for (Map<?, ?> m : rawList) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                    }
                    list.add(mm);
                }
            }

            String group = resolveGroupForWorld(plugin, safeWorldName(player));
            long now = System.currentTimeMillis();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", now);
            entry.put("created_at", iso(now));
            entry.put("player_name", player.getName());
            entry.put("level", player.getLevel());
            entry.put("group", group);
            entry.put("reason", reason);
            entry.put("world", safeWorldName(player));
            String numericId = nextInventoryIdForWorld(cfg, safeWorldName(player));
            String invId = (prefix != null && !prefix.isEmpty()) ? (prefix.toUpperCase() + numericId) : numericId;
            entry.put("inventory_id", invId);
            if (eventMatchId != null && !eventMatchId.isEmpty()) entry.put("event_match_id", eventMatchId);

            ItemStack[] contents = player.getInventory().getContents();
            ItemStack[] armor = player.getInventory().getArmorContents();
            entry.put("contents", toList(contents));
            entry.put("armor", toList(armor));

            list.add(entry);
            pruneList(list, now - THIRTY_DAYS_MILLIS);
            cfg.set(key, list);
            saveYamlAtomic(cfg, file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save inventory snapshot: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error saving inventory snapshot: " + e.getMessage());
        }
    }

    public static void saveSnapshotWithIdsAsync(EventPlugin plugin, Player player, String reason, String eventMatchId, boolean post, String prefix) {
        final java.util.UUID playerId = player.getUniqueId();
        final String playerName = player.getName();
        final int playerLevel = player.getLevel();
        final String worldName = safeWorldName(player);
        final String group = resolveGroupForWorld(plugin, worldName);
        final java.util.List<ItemStack> contents = toList(player.getInventory().getContents());
        final java.util.List<ItemStack> armor = toList(player.getInventory().getArmorContents());
        final long now = System.currentTimeMillis();
        final String prefixFinal = (prefix != null ? prefix.toUpperCase() : "");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File folder = plugin.getDataFolder();
                if (!folder.exists()) folder.mkdirs();
                File file = new File(folder, post ? FILE_NAME_POST : FILE_NAME);

                YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                String key = "entries." + playerId.toString();
                java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList(key);
                java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
                if (rawList != null) {
                    for (java.util.Map<?, ?> m : rawList) {
                        java.util.Map<String, Object> mm = new java.util.LinkedHashMap<>();
                        for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                        }
                        list.add(mm);
                    }
                }

                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("timestamp", now);
                entry.put("created_at", iso(now));
                entry.put("player_name", playerName);
                entry.put("level", playerLevel);
                entry.put("group", group);
                entry.put("reason", reason);
                entry.put("world", worldName);
                String numericId = nextInventoryIdForWorld(cfg, worldName);
                String invId = prefixFinal.isEmpty() ? numericId : (prefixFinal + numericId);
                entry.put("inventory_id", invId);
                if (eventMatchId != null && !eventMatchId.isEmpty()) entry.put("event_match_id", eventMatchId);
                entry.put("contents", contents);
                entry.put("armor", armor);

                list.add(entry);
                pruneList(list, now - THIRTY_DAYS_MILLIS);
                cfg.set(key, list);
                saveYamlAtomic(cfg, file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save inventory snapshot (async): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected error saving inventory snapshot (async): " + e.getMessage());
            }
        });
    }

    private static String resolveGroupForWorld(EventPlugin plugin, String worldName) {
        try {
            java.io.File cfgFile = new java.io.File(plugin.getDataFolder(), "config.yml");
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);
            String base = "settings.inventory-snapshots";
            // per-world-groups takes precedence
            org.bukkit.configuration.ConfigurationSection perWorld = cfg.getConfigurationSection(base + ".per-world-groups");
            if (perWorld != null) {
                java.util.List<String> groups = perWorld.getStringList(worldName);
                if (groups != null && !groups.isEmpty()) return groups.get(0);
            }
            // fallback to default-group
            String def = cfg.getString(base + ".default-group", cfg.getString("settings.inventory-group", "default"));
            return def != null ? def : "default";
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * Global prune across all players. Safe to call on disable or periodically.
     */
    public static void pruneOldEntries(EventPlugin plugin) {
        try {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.exists()) return;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            long cutoff = System.currentTimeMillis() - THIRTY_DAYS_MILLIS;
            boolean changed = false;

            if (cfg.getConfigurationSection("entries") != null) {
                Set<String> uuids = cfg.getConfigurationSection("entries").getKeys(false);
                for (String uuidKey : uuids) {
                    List<Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                    if (rawList == null || rawList.isEmpty()) continue;
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Map<?, ?> m : rawList) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                        }
                        list.add(mm);
                    }
                    int originalSize = list.size();
                    pruneList(list, cutoff);
                    if (list.size() != originalSize) {
                        changed = true;
                        cfg.set("entries." + uuidKey, list);
                    }
                }
            }

            if (changed) saveYamlAtomic(cfg, file);

            // Wenn nach dem Prune keine Einträge mehr vorhanden sind, lösche die Datei auf Disk
            try {
                boolean empty = true;
                if (cfg.getConfigurationSection("entries") != null) {
                    java.util.Set<String> uuids = cfg.getConfigurationSection("entries").getKeys(false);
                    for (String uuidKey : uuids) {
                        java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                        if (rawList != null && !rawList.isEmpty()) { empty = false; break; }
                    }
                }
                if (empty && file.exists()) {
                    // Lösche leere Snapshot-Datei vom Disk
                    if (!file.delete()) {
                        plugin.getLogger().warning("Konnte leere Snapshot-Datei nicht löschen: " + file.getName());
                    }
                }
            } catch (Exception ignored) {}
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to prune inventory snapshots: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error pruning inventory snapshots: " + e.getMessage());
        }

        // Also prune post-snapshots
        try {
            File file = new File(plugin.getDataFolder(), FILE_NAME_POST);
            if (!file.exists()) return;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            long cutoff = System.currentTimeMillis() - THIRTY_DAYS_MILLIS;
            boolean changed = false;
            if (cfg.getConfigurationSection("entries") != null) {
                Set<String> uuids = cfg.getConfigurationSection("entries").getKeys(false);
                for (String uuidKey : uuids) {
                    List<Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                    if (rawList == null || rawList.isEmpty()) continue;
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Map<?, ?> m : rawList) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) mm.put(e.getKey().toString(), e.getValue());
                        }
                        list.add(mm);
                    }
                    int originalSize = list.size();
                    pruneList(list, cutoff);
                    if (list.size() != originalSize) {
                        changed = true;
                        cfg.set("entries." + uuidKey, list);
                    }
                }
            }
            if (changed) saveYamlAtomic(cfg, file);

            // Wenn nach dem Prune keine Einträge mehr vorhanden sind, lösche die Datei auf Disk
            try {
                boolean empty = true;
                if (cfg.getConfigurationSection("entries") != null) {
                    java.util.Set<String> uuids = cfg.getConfigurationSection("entries").getKeys(false);
                    for (String uuidKey : uuids) {
                        java.util.List<java.util.Map<?, ?>> rawList = cfg.getMapList("entries." + uuidKey);
                        if (rawList != null && !rawList.isEmpty()) { empty = false; break; }
                    }
                }
                if (empty && file.exists()) {
                    if (!file.delete()) {
                        plugin.getLogger().warning("Konnte leere Post-Snapshot-Datei nicht löschen: " + file.getName());
                    }
                }
            } catch (Exception ignored) {}
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to prune post inventory snapshots: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error pruning post inventory snapshots: " + e.getMessage());
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> FILE_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void saveYamlAtomic(org.bukkit.configuration.file.YamlConfiguration cfg, java.io.File file) throws java.io.IOException {
        Object lock = FILE_LOCKS.computeIfAbsent(file.getAbsolutePath(), k -> new Object());
        synchronized (lock) {
            java.io.File tmp = new java.io.File(file.getParentFile(), file.getName() + ".tmp");
            cfg.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                // Fallback: direkt speichern
                cfg.save(file);
            }
        }
    }

    private static void pruneList(List<Map<String, Object>> list, long cutoffMillis) {
        list.removeIf(m -> {
            Object tsObj = m.get("timestamp");
            if (tsObj instanceof Number) {
                long ts = ((Number) tsObj).longValue();
                return ts < cutoffMillis;
            }
            return false;
        });
    }

    private static List<ItemStack> toList(ItemStack[] array) {
        List<ItemStack> out = new ArrayList<>();
        if (array != null) {
            for (ItemStack it : array) {
                out.add(it);
            }
        }
        return out;
    }

    private static ItemStack[] fromList(List<?> list) {
        if (list == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            out[i] = (o instanceof ItemStack) ? (ItemStack) o : null;
        }
        return out;
    }

    private static String iso(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date(millis));
    }
}