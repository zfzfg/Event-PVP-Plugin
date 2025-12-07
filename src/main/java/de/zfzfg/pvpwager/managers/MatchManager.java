package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.EquipmentSet;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.utils.InventoryUtil;
import de.zfzfg.pvpwager.models.CommandRequest;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchManager {
    // Magic-number constants consolidated for clarity and maintainability
    private static final int PRE_TELEPORT_COUNTDOWN_SECONDS = 5;
    private static final int[] MATCH_TIMER_ANNOUNCE_SECONDS = {60, 30, 10};
    private static final int MATCH_CLEANUP_DELAY_SECONDS = 4;
    private static final long DISTRIBUTE_DELAY_TICKS = de.zfzfg.core.util.Time.ticks(10); // 0.5s
    private final EventPlugin plugin;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> preTeleportCountdownTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> matchTimerTasks = new HashMap<>();
    private final SpawnManager spawnManager;
    // O(1) Lookup: Spieler -> MatchId
    private final Map<UUID, UUID> playerToMatchId = new ConcurrentHashMap<>();
    
    // Track if players have been teleported (thread-safe)
    private final Set<UUID> teleportedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Cache for secure teleport back verification
    private final Map<UUID, Location> teleportVerificationCache = new ConcurrentHashMap<>();
    // Guard für gleichzeitige Match-Operationen
    private final Object matchOpMutex = new Object();
    
    public MatchManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.spawnManager = new SpawnManager(plugin);
    }
    
    /**
     * Holt eine Nachricht aus der Config.
     */
    private String getMsg(String key) {
        return plugin.getCoreConfigManager().getMessages().getString("messages.match." + key, key);
    }
    
    /**
     * Holt eine Nachricht mit Platzhalter-Ersetzung.
     */
    private String getMsg(String key, String placeholder, String value) {
        return getMsg(key).replace(placeholder, value);
    }
    
    /**
     * Formatiert eine Item-Liste mit lokalisierter "keine Items" Nachricht.
     */
    private String formatItemList(java.util.List<org.bukkit.inventory.ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return plugin.getCoreConfigManager().getMessages().getString("messages.utility.no-items", "no items");
        }
        return items.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getType().name() + " x" + item.getAmount())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Indexiert einen Spieler für schnelle O(1)-Zuordnung auf ein Match.
     * Nutzt eine thread-sichere Map, um gleichzeitige Zugriffe zu unterstützen.
     */
    public void indexPlayer(java.util.UUID playerId, java.util.UUID matchId) {
        playerToMatchId.put(playerId, matchId);
    }

    /**
     * Liefert die MatchId für einen Spieler in O(1) oder null, wenn nicht vorhanden.
     */
    public java.util.UUID getMatchIdByPlayer(java.util.UUID playerId) {
        return playerToMatchId.get(playerId);
    }
    
    public void startMatchSetup(Player player1, Player player2) {
        Match match = new Match(player1, player2);
        synchronized (matchOpMutex) {
            matches.put(match.getMatchId(), match);
            // Index participants for O(1) lookup
            playerToMatchId.put(player1.getUniqueId(), match.getMatchId());
            playerToMatchId.put(player2.getUniqueId(), match.getMatchId());
            // Store original locations
            match.getOriginalLocations().put(player1.getUniqueId(), player1.getLocation());
            match.getOriginalLocations().put(player2.getUniqueId(), player2.getLocation());
        }
    }
    
    public void handleWagerConfirmation(Player player1, Player player2) {
        Match match = getMatch(player1, player2);
        if (match == null) return;
        
        // Skip validation if no-wager mode
        if (!match.isNoWagerMode()) {
            // Verify wager is valid
            if (!validateWager(match, player1, player2)) {
                return;
            }
            
            // Deduct money from both players if applicable
            if (plugin.hasEconomy()) {
                double p1Money = match.getWagerMoney(player1);
                double p2Money = match.getWagerMoney(player2);
                
                if (p1Money > 0) {
                    if (!plugin.getEconomy().has(player1, p1Money)) {
                        MessageUtil.sendMessage(player1, getMsg("not-enough-money"));
                        endMatch(match, null, true);
                        return;
                    }
                    plugin.getEconomy().withdrawPlayer(player1, p1Money);
                }
                
                if (p2Money > 0) {
                    if (!plugin.getEconomy().has(player2, p2Money)) {
                        MessageUtil.sendMessage(player2, getMsg("not-enough-money"));
                        // Return p1's money
                        if (p1Money > 0) {
                            plugin.getEconomy().depositPlayer(player1, p1Money);
                        }
                        endMatch(match, null, true);
                        return;
                    }
                    plugin.getEconomy().withdrawPlayer(player2, p2Money);
                }
            }
        }
        
        // Start arena selection for command-based matches
        // Since GUI is removed, we need to handle arena selection differently
        // For now, we'll select the first available arena automatically
        if (!plugin.getArenaManager().getArenas().isEmpty()) {
            Arena firstArena = plugin.getArenaManager().getArenas().values().iterator().next();
            handleArenaSelection(player1, player2, firstArena);
        } else {
            MessageUtil.sendMessage(player1, getMsg("no-arenas"));
            MessageUtil.sendMessage(player2, getMsg("no-arenas"));
            endMatch(match, null, true);
        }
    }
    
    private boolean validateWager(Match match, Player player1, Player player2) {
        // Check minimum wager requirements
        int minItems = plugin.getPvpConfigManager().getConfig().getInt("settings.checks.minimum-bet-items", 1);
        double minMoney = plugin.getPvpConfigManager().getConfig().getDouble("settings.checks.minimum-bet-money", 0);
        
        int p1Items = match.getWagerItems(player1).size();
        int p2Items = match.getWagerItems(player2).size();
        double p1Money = match.getWagerMoney(player1);
        double p2Money = match.getWagerMoney(player2);
        
        if ((p1Items + p2Items < minItems) && (p1Money + p2Money < minMoney)) {
            String msg = getMsg("min-wager-not-met").replace("{items}", String.valueOf(minItems)).replace("{money}", String.valueOf(minMoney));
            MessageUtil.sendMessage(player1, msg);
            MessageUtil.sendMessage(player2, msg);
            return false;
        }
        
        // Check inventory space
        if (plugin.getPvpConfigManager().getConfig().getBoolean("settings.checks.inventory-space", true)) {
            if (!InventoryUtil.canFitItems(player1, match.getWagerItems(player2))) {
                MessageUtil.sendMessage(player1, getMsg("not-enough-inventory"));
                MessageUtil.sendMessage(player2, getMsg("opponent-not-enough-inventory"));
                return false;
            }
            
            if (!InventoryUtil.canFitItems(player2, match.getWagerItems(player1))) {
                MessageUtil.sendMessage(player2, getMsg("not-enough-inventory"));
                MessageUtil.sendMessage(player1, getMsg("opponent-not-enough-inventory"));
                return false;
            }
        }
        
        return true;
    }
    
    public void handleArenaSelection(Player player1, Player player2, Arena arena) {
        Match match = getMatch(player1, player2);
        if (match == null) return;
        
        match.setArena(arena);
        
        // Zeige Lade-Status an
        match.setWorldLoading(true);
        match.broadcast("");
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast(getMsg("arena-loading"));
        match.broadcast("&e&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast(getMsg("arena-display", "{arena}", arena.getDisplayName()));
        match.broadcast("&7Welt: &e" + arena.getArenaWorld());
        match.broadcast("");
        match.broadcast("&7Bitte warte einen Moment...");
        
        // Load arena world with callback
        plugin.getArenaManager().loadArenaWorld(arena.getArenaWorld(), () -> {
            // Welt-Ladung abgeschlossen
            match.setWorldLoading(false);
            
            match.broadcast("");
            match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            match.broadcast(getMsg("arena-loaded"));
            match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            match.broadcast("");
            
            java.util.List<EquipmentSet> allowed = plugin.getEquipmentManager().getAllowedEquipmentForWorld(arena.getArenaWorld());
            if (!allowed.isEmpty()) {
                EquipmentSet firstEquipment = allowed.get(0);
                handleEquipmentSelection(player1, player2, firstEquipment, firstEquipment);
            } else {
                MessageUtil.sendMessage(player1, plugin.getPvpConfigManager().getMessage("error.equipment-not-available"));
                MessageUtil.sendMessage(player2, plugin.getPvpConfigManager().getMessage("error.equipment-not-available"));
                endMatch(match, null, true);
            }
        });
    }
    
    // Neue Methode: Match nach erfolgreicher Weltladung fortsetzen
    private void continueMatchStart(Match match, World arenaWorld) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        Arena arena = match.getArena();
        
        plugin.getLogger().info("Starting DIRECT match in world: " + arenaWorld.getName());
        
        // Teleport players
        spawnManager.teleportPlayers(player1, player2, arena, arenaWorld);
        teleportedPlayers.add(player1.getUniqueId());
        teleportedPlayers.add(player2.getUniqueId());
        
        // Wait for teleport, then apply equipment
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Verify in correct world
            if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
                plugin.getLogger().warning("Players not in arena world after teleport!");
            }
            
            // Backups handled in continueMatchSetup with 5-digit Match ID

            // Save inventory snapshots BEFORE clearing (per player, prefixed MATCH)
            try {
                de.zfzfg.eventplugin.storage.InventorySnapshotStorage.saveSnapshotWithIdsAsync(plugin, player1, "PVP_MATCH_PRE", match.getMatchId().toString(), false, "MATCH");
                de.zfzfg.eventplugin.storage.InventorySnapshotStorage.saveSnapshotWithIdsAsync(plugin, player2, "PVP_MATCH_PRE", match.getMatchId().toString(), false, "MATCH");
            } catch (Exception ignored) {}

            // Clear inventories
            player1.getInventory().clear();
            player2.getInventory().clear();
            player1.getInventory().setArmorContents(null);
            player2.getInventory().setArmorContents(null);
            
            // Apply equipment with verification and retries
            applyEquipmentWithVerify(player1, match.getPlayer1Equipment());
            applyEquipmentWithVerify(player2, match.getPlayer2Equipment());
            
            // Reset health
            player1.setHealth(20.0);
            player1.setFoodLevel(20);
            player1.setSaturation(20.0f);
            player2.setHealth(20.0);
            player2.setFoodLevel(20);
            player2.setSaturation(20.0f);
            
            // Set gamemode to SURVIVAL immediately
            player1.setGameMode(GameMode.SURVIVAL);
            player2.setGameMode(GameMode.SURVIVAL);
            
            // START FIGHT IMMEDIATELY (no countdown)
            match.setState(MatchState.FIGHTING);
            
            match.broadcast("");
            match.broadcast(getMsg("fight-divider"));
            match.broadcast(getMsg("fight"));
            match.broadcast(getMsg("fight-divider"));
            match.broadcast("");
            
            // Play sound
            player1.playSound(player1.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
            player2.playSound(player2.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
            
            // Start timer
            startMatchTimer(match);
            
        }, de.zfzfg.core.util.Time.seconds(1)); // 1 second after teleport
    }
    
    public void handleEquipmentSelection(Player player1, Player player2, EquipmentSet p1Equipment, EquipmentSet p2Equipment) {
        Match match = getMatch(player1, player2);
        if (match == null) return;
        
        match.setPlayer1Equipment(p1Equipment);
        match.setPlayer2Equipment(p2Equipment);
        
        // Start the match
        startMatch(match);
    }
    
    private void startMatch(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        Arena arena = match.getArena();
        
        synchronized (matchOpMutex) {
            match.setState(MatchState.STARTING);
            match.setStartTime(System.currentTimeMillis());
        }
        
        // Inform players that arena is loading
        match.broadcast(getMsg("arena-countdown"));
        
        // Ensure arena world is loaded before proceeding
        plugin.getArenaManager().loadArenaWorld(arena.getArenaWorld(), () -> {
            World arenaWorld = Bukkit.getWorld(arena.getArenaWorld());
            if (arenaWorld == null) {
                plugin.getLogger().severe("Arena world not found after loading: " + arena.getArenaWorld());
                MessageUtil.sendMessage(player1, getMsg("arena-load-failed"));
                MessageUtil.sendMessage(player2, getMsg("arena-load-failed"));
                endMatch(match, null, true);
                return;
            }

            // 5s PRE-TELEPORT countdown with invite, then perform teleport and continue
            startPreTeleportCountdown(match, 5, () -> {
                plugin.getLogger().info("Starting match in world: " + arenaWorld.getName());

                // Teleport players using SpawnManager
                plugin.getLogger().info("Teleporting players with spawn-type: " + arena.getSpawnType());
                spawnManager.teleportPlayers(player1, player2, arena, arenaWorld);

                // Mark players as teleported
                teleportedPlayers.add(player1.getUniqueId());
                teleportedPlayers.add(player2.getUniqueId());

                // Warte 2 Sekunden nach Teleport für sichere Welt-Ladung
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    afterTeleportVerifyOrRecover(match, player1, player2, arena, arenaWorld);
                }, de.zfzfg.core.util.Time.seconds(2)); // 2 Sekunden warten nach Teleport für sichere Welt-Ladung
            });
        });
    }

    private void afterTeleportVerifyOrRecover(Match match, Player player1, Player player2, Arena arena, World arenaWorld) {
        // Verify players are in correct world
        if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
            plugin.getLogger().warning("Players not in arena world after teleport!");
            plugin.getLogger().warning("P1 World: " + player1.getWorld().getName());
            plugin.getLogger().warning("P2 World: " + player2.getWorld().getName());
            plugin.getLogger().warning("Arena World: " + arenaWorld.getName());

            // Versuche Notfall-Teleport und fahre mit Setup fort
            if (!attemptEmergencyTeleport(match, player1, player2, arena, arenaWorld)) {
                return;
            }
            return;
        }

        continueMatchSetup(match, player1, player2, arenaWorld);
    }

    /**
     * Notfall-Teleport, falls Spieler nach dem ersten Teleport nicht in der Zielwelt sind.
     * Führt einen zweiten Teleport durch und setzt das Match fort, wenn erfolgreich.
     * Gibt false zurück, wenn das Match beendet werden musste.
     */
    private boolean attemptEmergencyTeleport(Match match, Player player1, Player player2, Arena arena, World arenaWorld) {
        plugin.getLogger().info("Attempting emergency teleport...");
        try {
            Location spawn1 = (arena.getSpawnConfig() != null && arena.getSpawnConfig().getFixedSpawns() != null && !arena.getSpawnConfig().getFixedSpawns().isEmpty())
                ? arena.getSpawnConfig().getFixedSpawns().get(0).clone()
                : arenaWorld.getSpawnLocation();
            Location spawn2 = (arena.getSpawnConfig() != null && arena.getSpawnConfig().getFixedSpawns() != null && arena.getSpawnConfig().getFixedSpawns().size() > 1)
                ? arena.getSpawnConfig().getFixedSpawns().get(1).clone()
                : arenaWorld.getSpawnLocation();

            spawn1.setWorld(arenaWorld);
            spawn2.setWorld(arenaWorld);

            player1.teleport(spawn1);
            player2.teleport(spawn2);

            // Nochmal warten und prüfen
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
                    plugin.getLogger().severe("Emergency teleport failed! Ending match.");
                    MessageUtil.sendMessage(player1, getMsg("arena-teleport-failed"));
                    MessageUtil.sendMessage(player2, getMsg("arena-teleport-failed"));
                    endMatch(match, null, true);
                    return;
                }
                continueMatchSetup(match, player1, player2, arenaWorld);
            }, de.zfzfg.core.util.Time.seconds(1));

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Emergency teleport failed with exception: " + e.getMessage());
            MessageUtil.sendMessage(player1, getMsg("arena-setup-failed"));
            MessageUtil.sendMessage(player2, getMsg("arena-setup-failed"));
            endMatch(match, null, true);
            return false;
        }
    }
    
    private void continueMatchSetup(Match match, Player player1, Player player2, World arenaWorld) {
        // Inventar-Backups in Matches sind deaktiviert

        // Save inventory snapshots BEFORE clearing (per player, prefixed MATCH)
        try {
            de.zfzfg.eventplugin.storage.InventorySnapshotStorage.saveSnapshotWithIdsAsync(plugin, player1, "PVP_MATCH_PRE", match.getMatchId().toString(), false, "MATCH");
            de.zfzfg.eventplugin.storage.InventorySnapshotStorage.saveSnapshotWithIdsAsync(plugin, player2, "PVP_MATCH_PRE", match.getMatchId().toString(), false, "MATCH");
        } catch (Exception ignored) {}

        // Clear inventories AFTER teleport
        player1.getInventory().clear();
        player2.getInventory().clear();
        player1.getInventory().setArmorContents(null);
        player2.getInventory().setArmorContents(null);
        
        // Apply equipment AFTER teleport, with verification and retries
        applyEquipmentWithVerify(player1, match.getPlayer1Equipment());
        applyEquipmentWithVerify(player2, match.getPlayer2Equipment());
        
        // Reset health and hunger
        player1.setHealth(20.0);
        player1.setFoodLevel(20);
        player1.setSaturation(20.0f);
        player2.setHealth(20.0);
        player2.setFoodLevel(20);
        player2.setSaturation(20.0f);
        
        // Set gamemode
        player1.setGameMode(GameMode.SURVIVAL);
        player2.setGameMode(GameMode.SURVIVAL);
        
        // Safety: clear lingering invisibility potion effects from previous plugins
        player1.removePotionEffect(PotionEffectType.INVISIBILITY);
        player2.removePotionEffect(PotionEffectType.INVISIBILITY);
        
        // Start countdown
        startCountdown(match);
    }
    
    private void applyEquipment(Player player, EquipmentSet equipment) {
        if (equipment == null) return;
        
        plugin.getLogger().info("Applying equipment to " + player.getName() + " in world: " + player.getWorld().getName());
        
        // Apply armor
        if (equipment.getHelmet() != null) {
            player.getInventory().setHelmet(equipment.getHelmet().clone());
        }
        if (equipment.getChestplate() != null) {
            player.getInventory().setChestplate(equipment.getChestplate().clone());
        }
        if (equipment.getLeggings() != null) {
            player.getInventory().setLeggings(equipment.getLeggings().clone());
        }
        if (equipment.getBoots() != null) {
            player.getInventory().setBoots(equipment.getBoots().clone());
        }
        
        // Apply inventory items
        if (equipment.getInventory() != null) {
            for (Map.Entry<Integer, ItemStack> entry : equipment.getInventory().entrySet()) {
                player.getInventory().setItem(entry.getKey(), entry.getValue().clone());
            }
        }
        
        plugin.getLogger().info("Equipment applied to " + player.getName());
    }

    private boolean verifyEquipmentApplied(Player player, EquipmentSet equipment) {
        if (equipment == null) return true;

        // Verify armor
        if (equipment.getHelmet() != null) {
            ItemStack applied = player.getInventory().getHelmet();
            if (applied == null || !applied.isSimilar(equipment.getHelmet())) return false;
        }
        if (equipment.getChestplate() != null) {
            ItemStack applied = player.getInventory().getChestplate();
            if (applied == null || !applied.isSimilar(equipment.getChestplate())) return false;
        }
        if (equipment.getLeggings() != null) {
            ItemStack applied = player.getInventory().getLeggings();
            if (applied == null || !applied.isSimilar(equipment.getLeggings())) return false;
        }
        if (equipment.getBoots() != null) {
            ItemStack applied = player.getInventory().getBoots();
            if (applied == null || !applied.isSimilar(equipment.getBoots())) return false;
        }

        // Verify inventory items
        if (equipment.getInventory() != null) {
            for (Map.Entry<Integer, ItemStack> entry : equipment.getInventory().entrySet()) {
                ItemStack expected = entry.getValue();
                ItemStack applied = player.getInventory().getItem(entry.getKey());
                if (expected != null) {
                    if (applied == null || !applied.isSimilar(expected) || applied.getAmount() < expected.getAmount()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void applyEquipmentWithVerify(Player player, EquipmentSet equipment) {
        if (equipment == null) return;
        // First attempt
        applyEquipment(player, equipment);

        final int maxAttempts = 3;
        new BukkitRunnable() {
            int attempt = 1;

            @Override
            public void run() {
                if (verifyEquipmentApplied(player, equipment)) {
                    plugin.getLogger().info("Equipment verified for " + player.getName() + " (attempt " + attempt + ")");
                    cancel();
                    return;
                }

                if (attempt >= maxAttempts) {
                    plugin.getLogger().warning("Equipment could not be verified for " + player.getName() + " after " + maxAttempts + " attempts.");
                    cancel();
                    return;
                }

                attempt++;
                plugin.getLogger().warning("Equipment not applied correctly to " + player.getName() + ", retrying (attempt " + attempt + ")...");
                applyEquipment(player, equipment);
            }
        }.runTaskTimer(plugin, de.zfzfg.core.util.Time.ticks(4), DISTRIBUTE_DELAY_TICKS);
    }
    
    /**
     * Startet den Arena-Countdown vor Kampfbeginn.
     * Zeigt periodisch Nachrichten und spielt einen Ton für beide Spieler.
     * Übergibt nach Ablauf an {@link #startFight(Match)}.
     */
    private void startCountdown(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        int countdownTime = plugin.getPvpConfigManager().getConfig().getInt("settings.match.countdown-time", 10);

        // Send global spectate invite once when countdown starts
        sendGlobalSpectateInvite(match);
        
        for (int i = countdownTime; i > 0; i--) {
            final int seconds = i;
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.getState() != MatchState.STARTING) return;
                
                String message = getMsg("match-countdown", "{seconds}", String.valueOf(seconds));
                match.broadcast(message);
                
                // Play sound
                player1.playSound(player1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                player2.playSound(player2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                
            }, (countdownTime - i) * 20L);
            countdownTasks.put(match.getMatchId(), task);
        }
        
        // Start the match after countdown
        BukkitTask startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getState() == MatchState.STARTING) {
                startFight(match);
            }
        }, (countdownTime + 1) * 20L);
        countdownTasks.put(match.getMatchId(), startTask);
    }

    /**
     * Countdown vor dem Arena-Teleport inklusive globaler Spectate-Einladung.
     * Führt nach Ablauf den übergebenen Abschluss-Callback aus.
     */
    private void startPreTeleportCountdown(Match match, int seconds, Runnable onFinish) {
        // Einladung an alle Nicht-Teilnehmer anzeigen
        sendGlobalSpectateInvite(match);

        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        final UUID matchId = match.getMatchId();

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (match.getState() != MatchState.STARTING) {
                    cancel();
                    preTeleportCountdownTasks.remove(matchId);
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    preTeleportCountdownTasks.remove(matchId);
                    try {
                        onFinish.run();
                    } catch (Exception ignored) {}
                    return;
                }

                match.broadcast(getMsg("teleport-countdown", "{seconds}", String.valueOf(remaining)));
                player1.playSound(player1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                player2.playSound(player2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, de.zfzfg.core.util.Time.TICKS_PER_SECOND);

        preTeleportCountdownTasks.put(matchId, task);
    }

    private void sendGlobalSpectateInvite(Match match) {
        try {
            Player p1 = match.getPlayer1();
            Player p2 = match.getPlayer2();

            TextComponent header = new TextComponent(MessageUtil.color(
                getMsg("spectate-header", "{player1}", p1.getName())
                    .replace("{player2}", p2.getName())
            ));

            TextComponent spectateBtn1 = new TextComponent(MessageUtil.color(getMsg("spectate-button")));
            spectateBtn1.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp spectate " + p1.getName()));
            spectateBtn1.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.color("§aKlicke, um das Match zu schauen")).create()));

            TextComponent footer = new TextComponent(MessageUtil.color(getMsg("spectate-footer")));

            header.addExtra(spectateBtn1);
            header.addExtra(footer);

            for (Player online : Bukkit.getOnlinePlayers()) {
                // Don't send to match participants; they already see match messages
                if (online.equals(p1) || online.equals(p2)) continue;
                online.spigot().sendMessage(header);
            }
        } catch (Exception e) {
            // Fallback simple broadcast
            for (Player online : Bukkit.getOnlinePlayers()) {
                Player p1 = match.getPlayer1();
                Player p2 = match.getPlayer2();
                if (online.equals(p1) || online.equals(p2)) continue;
                MessageUtil.sendMessage(online, getMsg("spectate-simple", "{player1}", p1.getName())
                    .replace("{player2}", p2.getName()));
            }
        }
    }
    
    private void startFight(Match match) {
        match.setState(MatchState.FIGHTING);
        
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Broadcast
        match.broadcast("");
        match.broadcast(getMsg("fight-divider"));
        match.broadcast(getMsg("fight"));
        match.broadcast(getMsg("fight-divider"));
        match.broadcast("");
        
        if (match.isNoWagerMode()) {
            match.broadcast(getMsg("no-wager-mode"));
            match.broadcast("");
        }
        
        // Play sound
        player1.playSound(player1.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
        player2.playSound(player2.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 2.0f);
        
        // Start match timer
        startMatchTimer(match);
    }
    
    /**
     * Startet den Match-Timer und behandelt Timeout-Draw-Logik.
     * Kündigt Restzeit zu konfigurierten Intervallen an.
     */
    private void startMatchTimer(Match match) {
        int maxDuration = plugin.getPvpConfigManager().getConfig().getInt("settings.match.max-duration", 600); // 10 minutes
        
        BukkitTask timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match.getState() != MatchState.FIGHTING) return;
            
            long elapsed = (System.currentTimeMillis() - match.getStartTime()) / 1000;
            long remaining = maxDuration - elapsed;
            
            if (remaining <= 0) {
                // Match timeout - draw
                match.broadcast("");
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("&c&lTIME'S UP!");
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("");
                match.broadcast(getMsg("timeout-draw"));
                match.broadcast("");
                
                endMatch(match, null, true);
            } else if (java.util.Arrays.stream(MATCH_TIMER_ANNOUNCE_SECONDS).anyMatch(s -> s == remaining)) {
                match.broadcast("&eMatch ends in &c" + remaining + " &eseconds!");
            }
        }, 0L, de.zfzfg.core.util.Time.TICKS_PER_SECOND);
        
        synchronized (matchOpMutex) {
            matchTimerTasks.put(match.getMatchId(), timerTask);
        }
    }
    
    public void endMatch(Match match, Player winner, boolean isDraw) {
        // Cancel tasks (unter Lock)
        BukkitTask countdownTask;
        BukkitTask preTeleportTask;
        BukkitTask timerTask;
        synchronized (matchOpMutex) {
            countdownTask = countdownTasks.remove(match.getMatchId());
            preTeleportTask = preTeleportCountdownTasks.remove(match.getMatchId());
            timerTask = matchTimerTasks.remove(match.getMatchId());
        }
        if (countdownTask != null) countdownTask.cancel();
        if (preTeleportTask != null) preTeleportTask.cancel();
        if (timerTask != null) timerTask.cancel();
        
        match.setState(MatchState.ENDED);
        
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Handle winnings/returns based on mode
        if (match.isNoWagerMode()) {
            // No wager mode - nothing to distribute
            MessageUtil.sendMessage(player1, "&7No wager match - no items to distribute.");
            MessageUtil.sendMessage(player2, "&7No wager match - no items to distribute.");
        } else if (isDraw) {
            distributeItemsBack(match);
        } else if (winner != null) {
            distributeWinnings(match, winner);
        } else {
            // Should not happen
            distributeItemsBack(match);
        }

        // Record statistics (wins/losses/draws)
        try {
            if (isDraw) {
                plugin.getStatsManager().recordDraw(player1);
                plugin.getStatsManager().recordDraw(player2);
            } else if (winner != null) {
                plugin.getStatsManager().recordWin(winner);
                Player loser = match.getOpponent(winner);
                plugin.getStatsManager().recordLoss(loser);
            }
        } catch (Exception ignored) {}

        // Post-Match Inventar-Backups sind deaktiviert
        
        // Welt-Reset je nach Konfiguration
        if (match.getArena() != null) {
            String worldName = match.getArena().getArenaWorld();
            String cloneSource = match.getArena().getCloneSourceWorld();
            if (cloneSource != null && !cloneSource.isEmpty()) {
                plugin.getLogger().info("Scheduling clone reset for arena world: " + worldName + " from " + cloneSource);
                // Nach Rück-Teleport der Spieler ausführen
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().resetArenaWorldByClone(cloneSource, worldName);
                }, de.zfzfg.core.util.Time.seconds(7)); // 7 Sekunden nach Match-Ende
            } else if (match.getArena().isRegenerateWorld()) {
                plugin.getLogger().info("Scheduling Multiverse regeneration for arena world: " + worldName);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().regenerateArenaWorld(worldName);
                }, de.zfzfg.core.util.Time.seconds(7));
            }
        }
        
        // Teleport players back after delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Only teleport if they were actually teleported to arena
            if (teleportedPlayers.contains(player1.getUniqueId())) {
                teleportPlayerBack(player1, match);
                teleportedPlayers.remove(player1.getUniqueId());
            }
            
            if (teleportedPlayers.contains(player2.getUniqueId())) {
                teleportPlayerBack(player2, match);
                teleportedPlayers.remove(player2.getUniqueId());
            }
            
            // Handle spectators
            for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
                Player spectator = Bukkit.getPlayer(spectatorId);
                if (spectator != null && spectator.isOnline()) {
                    if (teleportedPlayers.contains(spectatorId)) {
                        teleportPlayerBack(spectator, match);
                        teleportedPlayers.remove(spectatorId);
                    }
                }
            }
            
            // Unload world if neither regenerating nor cloning reset
            if (match.getArena() != null && match.getArena().getCloneSourceWorld() == null && !match.getArena().isRegenerateWorld()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().unloadArenaWorld(match.getArena().getArenaWorld());
                }, de.zfzfg.core.util.Time.seconds(2));
            }
            
            // Cleanup: remove indexes und Match (unter Lock) + Teleport-Marker
            synchronized (matchOpMutex) {
                UUID matchId = match.getMatchId();
                playerToMatchId.remove(match.getPlayer1().getUniqueId());
                playerToMatchId.remove(match.getPlayer2().getUniqueId());
                for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
                    playerToMatchId.remove(spectatorId);
                    teleportedPlayers.remove(spectatorId);
                }
                teleportedPlayers.remove(match.getPlayer1().getUniqueId());
                teleportedPlayers.remove(match.getPlayer2().getUniqueId());
                matches.remove(matchId);
            }
            
        }, de.zfzfg.core.util.Time.seconds(MATCH_CLEANUP_DELAY_SECONDS));
    }
    
    private void distributeWinnings(Match match, Player winner) {
        Player loser = match.getOpponent(winner);
        
        // WICHTIG: Erst zur ursprünglichen Location teleportieren
        Location winnerOriginal = match.getOriginalLocations().get(winner.getUniqueId());
        if (winnerOriginal != null) {
            winner.teleport(winnerOriginal);
            plugin.getLogger().info("Teleported winner " + winner.getName() + " back to original location");
        }
        
        // Give items to winner NACH Teleport
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<ItemStack> allItems = new ArrayList<>();
            allItems.addAll(match.getWagerItems(match.getPlayer1()));
            allItems.addAll(match.getWagerItems(match.getPlayer2()));
            
            InventoryUtil.giveItems(winner, allItems);
            
            MessageUtil.sendMessage(winner, "");
            MessageUtil.sendMessage(winner, getMsg("you-won-header"));
            MessageUtil.sendMessage(winner, getMsg("you-won"));
            MessageUtil.sendMessage(winner, getMsg("you-won-header"));
            MessageUtil.sendMessage(winner, "");
            List<ItemStack> opponentItems = match.getWagerItems(loser);
            List<ItemStack> ownItems = match.getWagerItems(winner);
            if (opponentItems != null && !opponentItems.isEmpty()) {
                MessageUtil.sendMessage(winner, "&7Received from opponent: &e" + formatItemList(opponentItems));
            }
            if (ownItems != null && !ownItems.isEmpty()) {
                MessageUtil.sendMessage(winner, "&7Your own stake added: &e" + formatItemList(ownItems));
            }
            
            // Give money to winner
            if (plugin.hasEconomy()) {
                double totalMoney = match.getWagerMoney(match.getPlayer1()) + match.getWagerMoney(match.getPlayer2());
                if (totalMoney > 0) {
                    double opponentMoney = match.getWagerMoney(loser);
                    double ownMoney = match.getWagerMoney(winner);
                    plugin.getEconomy().depositPlayer(winner, totalMoney);
                    if (opponentMoney > 0) {
                        MessageUtil.sendMessage(winner, "&7Received from opponent: &6$" + String.format("%.2f", opponentMoney));
                    }
                    if (ownMoney > 0) {
                        MessageUtil.sendMessage(winner, "&7Your own stake added: &6$" + String.format("%.2f", ownMoney));
                    }
                }
            }
            MessageUtil.sendMessage(winner, "");
            
        }, DISTRIBUTE_DELAY_TICKS);
        
        // Notify loser
        MessageUtil.sendMessage(loser, "");
        MessageUtil.sendMessage(loser, getMsg("you-lost-header"));
        MessageUtil.sendMessage(loser, getMsg("you-lost"));
        MessageUtil.sendMessage(loser, getMsg("you-lost-header"));
        MessageUtil.sendMessage(loser, "");
        List<ItemStack> lostItems = match.getWagerItems(loser);
        if (lostItems != null && !lostItems.isEmpty()) {
            MessageUtil.sendMessage(loser, "&7You lost: &e" + formatItemList(lostItems));
        }
        if (plugin.hasEconomy()) {
            double lostMoney = match.getWagerMoney(loser);
            if (lostMoney > 0) {
                MessageUtil.sendMessage(loser, "&7You lost: &6$" + String.format("%.2f", lostMoney));
            }
        }
        MessageUtil.sendMessage(loser, "&7Better luck next time!");
        MessageUtil.sendMessage(loser, "");
    }
    
    private void distributeItemsBack(Match match) {
        // Return items to original owners
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Teleport back first
        Location p1Original = match.getOriginalLocations().get(player1.getUniqueId());
        Location p2Original = match.getOriginalLocations().get(player2.getUniqueId());
        
        if (p1Original != null) player1.teleport(p1Original);
        if (p2Original != null) player2.teleport(p2Original);
        
        // Give items AFTER teleport
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InventoryUtil.giveItems(player1, match.getWagerItems(player1));
            InventoryUtil.giveItems(player2, match.getWagerItems(player2));
            
            // Return money
            if (plugin.hasEconomy()) {
                double p1Money = match.getWagerMoney(player1);
                double p2Money = match.getWagerMoney(player2);
                
                if (p1Money > 0) {
                    plugin.getEconomy().depositPlayer(player1, p1Money);
                }
                if (p2Money > 0) {
                    plugin.getEconomy().depositPlayer(player2, p2Money);
                }
            }
            
            MessageUtil.sendMessage(player1, "&7Your wager has been returned.");
            MessageUtil.sendMessage(player2, "&7Your wager has been returned.");
            
        }, 10L); // 0.5 Sekunden nach Teleport
    }
    
    private void teleportPlayerBack(Player player, Match match) {
        // WICHTIG: Tote Spieler können nicht teleportiert werden!
        // Sie werden über den PlayerRespawnEvent behandelt.
        if (player.isDead()) {
            plugin.getLogger().info("[SafeTeleport-PvP] Spieler " + player.getName() + 
                " ist tot - Teleport wird über RespawnEvent gehandhabt.");
            return;
        }
        
        Location originalLocation = match.getOriginalLocations().get(player.getUniqueId());
        
        if (originalLocation != null && originalLocation.getWorld() != null) {
            String worldName = originalLocation.getWorld().getName();
            World targetWorld = Bukkit.getWorld(worldName);
            
            if (targetWorld != null) {
                // Welt existiert - sichere Teleportation
                Location safeLocation = originalLocation.clone();
                safeLocation.setWorld(targetWorld);
                
                // Zusätzliche Sicherheit: Prüfe Y-Koordinate (Void-Schutz)
                double minY = targetWorld.getMinHeight();
                if (safeLocation.getY() < minY + 5) {
                    // Zu tief - nutze Spawn stattdessen
                    plugin.getLogger().warning("[SafeTeleport-PvP] Original-Location für " + player.getName() + 
                        " zu tief (Y=" + safeLocation.getY() + "), nutze Spawn.");
                    safeLocation = targetWorld.getSpawnLocation();
                }
                
                plugin.getLogger().info("[SafeTeleport-PvP] Teleportiere " + player.getName() + " zurück zu: " + 
                    worldName + " @ " + safeLocation.getBlockX() + ", " + safeLocation.getBlockY() + ", " + safeLocation.getBlockZ());
                
                // Secure Teleport Logic: Cache and Verify
                teleportVerificationCache.put(player.getUniqueId(), safeLocation);
                player.teleport(safeLocation);
                
                // Verify after short delay (10 ticks = 0.5s)
                Bukkit.getScheduler().runTaskLater(plugin, () -> verifyTeleportBack(player), 10L);
            } else {
                // Welt nicht mehr geladen - Fallback zu Hauptwelt
                plugin.getLogger().warning("[SafeTeleport-PvP] Original-Welt für " + player.getName() + 
                    " nicht mehr geladen (" + worldName + "), nutze Hauptwelt.");
                teleportToMainWorldFallback(player);
            }
        } else {
            plugin.getLogger().warning("[SafeTeleport-PvP] Keine Original-Location für " + player.getName() + ", nutze Hauptwelt.");
            teleportToMainWorldFallback(player);
        }
        
        // Reset player state
        if (match.getSpectators().contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SURVIVAL);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
        }
        // Ensure any lingering invisibility is cleared
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }
    
    /**
     * Sicherer Fallback: Teleportiert Spieler zur Hauptwelt.
     */
    private void teleportToMainWorldFallback(Player player) {
        String mainWorldName = plugin.getConfigManager().getMainWorld();
        World mainWorld = mainWorldName != null ? Bukkit.getWorld(mainWorldName) : null;
        
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) {
            mainWorld = Bukkit.getWorlds().get(0);
        }
        
        if (mainWorld != null) {
            Location spawn = mainWorld.getSpawnLocation();
            plugin.getLogger().info("[SafeTeleport-PvP] Teleportiere " + player.getName() + " zu Hauptwelt-Spawn: " + 
                mainWorld.getName());
            player.teleport(spawn);
        } else {
            plugin.getLogger().severe("[SafeTeleport-PvP] KRITISCH: Keine Hauptwelt verfügbar für " + player.getName());
        }
    }

    private void verifyTeleportBack(Player player) {
        if (player == null || !player.isOnline()) {
            if (player != null) teleportVerificationCache.remove(player.getUniqueId());
            return;
        }
        
        Location expected = teleportVerificationCache.remove(player.getUniqueId());
        if (expected == null) return;
        
        Location current = player.getLocation();
        
        // Check if world matches and distance is reasonable (allow small movement)
        boolean worldMatch = current.getWorld() != null && expected.getWorld() != null && 
                             current.getWorld().getName().equals(expected.getWorld().getName());
        
        if (!worldMatch || current.distanceSquared(expected) > 9) { // > 3 blocks away
            plugin.getLogger().warning("Teleport verification failed for " + player.getName() + 
                ". Expected: " + (expected.getWorld() != null ? expected.getWorld().getName() : "null") + 
                ", Actual: " + (current.getWorld() != null ? current.getWorld().getName() : "null"));
            
            // Retry teleport once
            player.teleport(expected);
        }
    }
    
    public Match getMatch(Player player1, Player player2) {
        for (Match match : matches.values()) {
            if ((match.getPlayer1().equals(player1) && match.getPlayer2().equals(player2)) ||
                (match.getPlayer1().equals(player2) && match.getPlayer2().equals(player1))) {
                return match;
            }
        }
        return null;
    }
    
    public Match getMatchByPlayer(Player player) {
        UUID matchId = playerToMatchId.get(player.getUniqueId());
        return matchId != null ? matches.get(matchId) : null;
    }
    
    public Map<UUID, Match> getMatches() {
        return new HashMap<>(matches);
    }
    
    public boolean isPlayerInMatch(Player player) {
        return playerToMatchId.containsKey(player.getUniqueId());
    }
    
    public int getActiveMatchCount() {
        return matches.size();
    }
    
    public int stopAllMatches() {
        int count = matches.size();
        for (Match match : new ArrayList<>(getMatches().values())) {
            match.broadcast("&cServer is shutting down! Match cancelled.");
            endMatch(match, null, true);
        }
        // Nach dem Abbruch aller Matches: Flüchtige Zustände säubern
        clearTransientState();
        return count;
    }

    /**
     * Stoppt alle laufenden Matches für einen Server-Shutdown und führt
     * die Rückgabe von Items und Geld SOFORT (ohne Scheduler) aus.
     */
    public int stopAllMatches(boolean immediateDistribution) {
        if (!immediateDistribution) {
            return stopAllMatches();
        }

        int count = matches.size();
        for (Match match : new ArrayList<>(getMatches().values())) {
            match.broadcast(getMsg("server-shutdown"));
            endMatchOnShutdown(match);
        }
        // Flüchtige Zustände direkt säubern
        clearTransientState();
        return count;
    }

    /**
     * Softer Shutdown-Ende für ein Match: Teleport zurück, Items und Geld
     * an beide Spieler unmittelbar zurückgeben, Zuschauer zurücksetzen,
     * ohne verzögerte Tasks zu verwenden.
     */
    private void endMatchOnShutdown(Match match) {
        // Tasks abbrechen
        BukkitTask countdownTask;
        BukkitTask preTeleportTask;
        BukkitTask timerTask;
        synchronized (matchOpMutex) {
            countdownTask = countdownTasks.remove(match.getMatchId());
            preTeleportTask = preTeleportCountdownTasks.remove(match.getMatchId());
            timerTask = matchTimerTasks.remove(match.getMatchId());
        }
        if (countdownTask != null) try { countdownTask.cancel(); } catch (Exception ignored) {}
        if (preTeleportTask != null) try { preTeleportTask.cancel(); } catch (Exception ignored) {}
        if (timerTask != null) try { timerTask.cancel(); } catch (Exception ignored) {}

        match.setState(MatchState.ENDED);

        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();

        // Immer zurückteleportieren (falls Originalposition vorhanden)
        Location p1Original = match.getOriginalLocations().get(player1.getUniqueId());
        Location p2Original = match.getOriginalLocations().get(player2.getUniqueId());
        if (p1Original != null) {
            player1.teleport(p1Original);
        }
        if (p2Original != null) {
            player2.teleport(p2Original);
        }

        // Rückgabe abhängig vom Modus
        if (!match.isNoWagerMode()) {
            // Items direkt zurück
            try {
                InventoryUtil.giveItems(player1, match.getWagerItems(player1));
                InventoryUtil.giveItems(player2, match.getWagerItems(player2));
            } catch (Exception ignored) {}

            // Geld direkt zurück
            try {
                if (plugin.hasEconomy()) {
                    double p1Money = match.getWagerMoney(player1);
                    double p2Money = match.getWagerMoney(player2);
                    if (p1Money > 0) plugin.getEconomy().depositPlayer(player1, p1Money);
                    if (p2Money > 0) plugin.getEconomy().depositPlayer(player2, p2Money);
                }
            } catch (Exception ignored) {}
        }

        // Status von Spielern zurücksetzen
        try { player1.setGameMode(GameMode.SURVIVAL); } catch (Exception ignored) {}
        try { player2.setGameMode(GameMode.SURVIVAL); } catch (Exception ignored) {}
        try { player1.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception ignored) {}
        try { player2.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception ignored) {}

        // Zuschauer zurücksetzen
        for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                Location origin = match.getOriginalLocations().get(spectatorId);
                if (origin != null) {
                    try { spectator.teleport(origin); } catch (Exception ignored) {}
                }
                try { spectator.setGameMode(GameMode.SURVIVAL); } catch (Exception ignored) {}
                try { spectator.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception ignored) {}
            }
        }

        // Statistiken als Unentschieden
        try {
            plugin.getStatsManager().recordDraw(player1);
            plugin.getStatsManager().recordDraw(player2);
        } catch (Exception ignored) {}

        // Cleanup ohne Verzögerung
        synchronized (matchOpMutex) {
            UUID matchId = match.getMatchId();
            playerToMatchId.remove(player1.getUniqueId());
            playerToMatchId.remove(player2.getUniqueId());
            for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
                playerToMatchId.remove(spectatorId);
                teleportedPlayers.remove(spectatorId);
            }
            teleportedPlayers.remove(player1.getUniqueId());
            teleportedPlayers.remove(player2.getUniqueId());
            matches.remove(matchId);
        }
    }

    // Tasks sauber abbrechen (Reload/Disable)
    public void cancelAllTasks() {
        synchronized (matchOpMutex) {
            for (BukkitTask t : countdownTasks.values()) { try { t.cancel(); } catch (Exception ignored) {} }
            for (BukkitTask t : preTeleportCountdownTasks.values()) { try { t.cancel(); } catch (Exception ignored) {} }
            for (BukkitTask t : matchTimerTasks.values()) { try { t.cancel(); } catch (Exception ignored) {} }
            countdownTasks.clear();
            preTeleportCountdownTasks.clear();
            matchTimerTasks.clear();
        }
    }

    // Flüchtige Zustände zurücksetzen (teleportedPlayers / playerToMatchId)
    public void clearTransientState() {
        synchronized (matchOpMutex) {
            teleportedPlayers.clear();
            playerToMatchId.clear();
        }
    }

    // Command-based match start (OHNE Countdown, DIREKT starten)
    public void startMatchFromCommand(CommandRequest request) {
        Player player1 = request.getSender();
        Player player2 = request.getTarget();
        
        // Clean up GUI sessions for both players (no item return needed - items already handled)
        cleanupGuiSessionsForMatch(player1, player2);
        
        // Create match
        Match match = new Match(player1, player2);
        matches.put(match.getMatchId(), match);
        // Index participants for O(1) lookup
        playerToMatchId.put(player1.getUniqueId(), match.getMatchId());
        playerToMatchId.put(player2.getUniqueId(), match.getMatchId());
        
        // Set no-wager mode if both wagers are empty
        boolean hasWager = (request.getMoney() > 0 || !request.getWagerItems().isEmpty()) ||
                          (request.getTargetWagerMoney() > 0 || !request.getTargetWagerItems().isEmpty());
        
        if (!hasWager) {
            match.setNoWagerMode(true);
        } else {
            // Set wagers
            match.getWagerItems().put(player1.getUniqueId(), new ArrayList<>(request.getWagerItems()));
            match.getWagerItems().put(player2.getUniqueId(), new ArrayList<>(request.getTargetWagerItems()));
            match.getWagerMoney().put(player1.getUniqueId(), request.getMoney());
            match.getWagerMoney().put(player2.getUniqueId(), request.getTargetWagerMoney());
            
            // Remove items from inventories
            for (ItemStack item : request.getWagerItems()) {
                player1.getInventory().removeItem(item);
            }
            for (ItemStack item : request.getTargetWagerItems()) {
                player2.getInventory().removeItem(item);
            }
            
            // Deduct money if applicable
            if (plugin.hasEconomy()) {
                if (request.getMoney() > 0) {
                    plugin.getEconomy().withdrawPlayer(player1, request.getMoney());
                }
                if (request.getTargetWagerMoney() > 0) {
                    plugin.getEconomy().withdrawPlayer(player2, request.getTargetWagerMoney());
                }
            }
        }
        
        // Set arena and equipment (Optional-API + robust)
        Arena arena = plugin.getArenaManager().getArenaOptional(request.getFinalArenaId()).orElse(null);
        EquipmentSet p1Equipment = plugin.getEquipmentManager().getEquipmentSet(request.getFinalEquipmentId());
        EquipmentSet p2Equipment = plugin.getEquipmentManager().getEquipmentSet(request.getFinalEquipmentId());
        
        if (arena == null) {
            match.broadcast(getMsg("arena-not-exists"));
            endMatch(match, null, true);
            return;
        }

        match.setArena(arena);
        match.setPlayer1Equipment(p1Equipment);
        match.setPlayer2Equipment(p2Equipment);
        
        // KRITISCH: Verwende die Original-Locations aus dem Request!
        // Diese wurden gespeichert als der Request erstellt wurde - BEVOR
        // die Spieler irgendwohin teleportiert wurden.
        // Das stellt sicher dass wir die "echte" Rückkehr-Location haben.
        Location senderOriginal = request.getSenderOriginalLocation();
        Location targetOriginal = request.getTargetOriginalLocation();
        
        if (senderOriginal != null && senderOriginal.getWorld() != null) {
            match.getOriginalLocations().put(player1.getUniqueId(), senderOriginal.clone());
            plugin.getLogger().info("[Match] Original-Location für " + player1.getName() + " aus Request gespeichert:");
            plugin.getLogger().info("  Welt: " + senderOriginal.getWorld().getName());
            plugin.getLogger().info("  Koordinaten: X=" + String.format("%.2f", senderOriginal.getX()) + 
                ", Y=" + String.format("%.2f", senderOriginal.getY()) + 
                ", Z=" + String.format("%.2f", senderOriginal.getZ()));
            plugin.getLogger().info("  Rotation: Yaw=" + String.format("%.1f", senderOriginal.getYaw()) + 
                ", Pitch=" + String.format("%.1f", senderOriginal.getPitch()));
        } else {
            // Fallback: aktuelle Position verwenden
            Location fallback = player1.getLocation().clone();
            match.getOriginalLocations().put(player1.getUniqueId(), fallback);
            plugin.getLogger().warning("[Match] WARNUNG: Keine Original-Location im Request für " + player1.getName());
            plugin.getLogger().warning("  Fallback auf aktuelle Position: " + 
                (fallback.getWorld() != null ? fallback.getWorld().getName() : "NULL") + " @ " +
                String.format("%.2f, %.2f, %.2f", fallback.getX(), fallback.getY(), fallback.getZ()));
        }
        
        if (targetOriginal != null && targetOriginal.getWorld() != null) {
            match.getOriginalLocations().put(player2.getUniqueId(), targetOriginal.clone());
            plugin.getLogger().info("[Match] Original-Location für " + player2.getName() + " aus Request gespeichert:");
            plugin.getLogger().info("  Welt: " + targetOriginal.getWorld().getName());
            plugin.getLogger().info("  Koordinaten: X=" + String.format("%.2f", targetOriginal.getX()) + 
                ", Y=" + String.format("%.2f", targetOriginal.getY()) + 
                ", Z=" + String.format("%.2f", targetOriginal.getZ()));
            plugin.getLogger().info("  Rotation: Yaw=" + String.format("%.1f", targetOriginal.getYaw()) + 
                ", Pitch=" + String.format("%.1f", targetOriginal.getPitch()));
        } else {
            // Fallback: aktuelle Position verwenden
            Location fallback = player2.getLocation().clone();
            match.getOriginalLocations().put(player2.getUniqueId(), fallback);
            plugin.getLogger().warning("[Match] WARNUNG: Keine Original-Location im Request für " + player2.getName());
            plugin.getLogger().warning("  Fallback auf aktuelle Position: " + 
                (fallback.getWorld() != null ? fallback.getWorld().getName() : "NULL") + " @ " +
                String.format("%.2f, %.2f, %.2f", fallback.getX(), fallback.getY(), fallback.getZ()));
        }
        
        // Confirm both
        match.confirmArena(player1);
        match.confirmArena(player2);
        match.confirmEquipment(player1);
        match.confirmEquipment(player2);
        
        // Announce
        match.broadcast("");
        match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast(getMsg("match-starting"));
        match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        match.broadcast("");
        match.broadcast(getMsg("vs-display", "{player1}", player1.getName()).replace("{player2}", player2.getName()));
        match.broadcast(getMsg("arena-display", "{arena}", arena.getDisplayName()));
        match.broadcast(getMsg("equipment-display", "{equipment}", p1Equipment.getDisplayName()));
        match.broadcast("");
        
        // Start match WITHOUT GUI - DIRECT START
        startMatchDirectly(match);
    }
    
    // Neue Methode: Match direkt starten OHNE Countdown
    private void startMatchDirectly(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        Arena arena = match.getArena();
        
        synchronized (matchOpMutex) {
            match.setState(MatchState.STARTING);
            match.setStartTime(System.currentTimeMillis());
        }
        
        // Load arena world first using /mvload command
        String worldName = arena.getArenaWorld();
        plugin.getLogger().info("Loading arena world via /mvload: " + worldName);
        
        // Use MultiverseHelper to load world with /mvload command
        plugin.getArenaManager().loadArenaWorld(worldName, () -> {
            // World loading completed, proceed with match start
            World arenaWorld = Bukkit.getWorld(worldName);
            if (arenaWorld == null) {
                plugin.getLogger().severe("Arena world failed to load: " + worldName);
                MessageUtil.error(player1, "Error: Arena world could not be loaded!");
                MessageUtil.error(player2, "Error: Arena world could not be loaded!");
                endMatch(match, null, true);
                return;
            }
            
            plugin.getLogger().info("Arena world loaded successfully: " + worldName);
            // Countdown vor dem Teleport (konfiguriert über Konstante)
            startPreTeleportCountdown(match, PRE_TELEPORT_COUNTDOWN_SECONDS, () -> continueMatchStart(match, arenaWorld));
        });
    }

    // O(1) Lookup: Spectator management
    public void addSpectator(Match match, Player spectator) {
        if (match == null || spectator == null) return;
        UUID sid = spectator.getUniqueId();
        if (match.getSpectators().contains(sid)) return;
        match.getSpectators().add(sid);
        match.getOriginalLocations().put(sid, spectator.getLocation());
        playerToMatchId.put(sid, match.getMatchId());
    }

    public void removeSpectator(Match match, Player spectator) {
        if (match == null || spectator == null) return;
        UUID sid = spectator.getUniqueId();
        match.getSpectators().remove(sid);
        match.getOriginalLocations().remove(sid);
        playerToMatchId.remove(sid);
    }

    // Track teleported players (used for end-of-match teleport back)
    public void markTeleported(Player player) {
        if (player != null) {
            teleportedPlayers.add(player.getUniqueId());
        }
    }
    
    /**
     * Cleans up GUI sessions for both players when a match starts.
     * This prevents duplicate "already have wager request" messages.
     * Items are NOT returned here - they're already handled by the request flow.
     */
    private void cleanupGuiSessionsForMatch(Player player1, Player player2) {
        // Clean up sessions without returning items (items are already in the request)
        de.zfzfg.pvpwager.gui.GuiManager guiManager = plugin.getGuiManager();
        
        // Player 1 session cleanup
        de.zfzfg.pvpwager.gui.WagerSession session1 = guiManager.getSession(player1);
        if (session1 != null) {
            session1.cancel(); // Mark as cancelled, don't return items
            session1.cleanup();
            guiManager.removeSession(player1);
        }
        
        // Player 2 session cleanup  
        de.zfzfg.pvpwager.gui.WagerSession session2 = guiManager.getSession(player2);
        if (session2 != null) {
            session2.cancel(); // Mark as cancelled, don't return items
            session2.cleanup();
            guiManager.removeSession(player2);
        }
    }
}