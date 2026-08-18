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
import de.zfzfg.core.util.Text;
import net.kyori.adventure.text.Component;
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
    private final Map<UUID, List<BukkitTask>> countdownTaskLists = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> preTeleportCountdownTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> matchTimerTasks = new ConcurrentHashMap<>();
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
        if (key == null || key.isEmpty()) return "";
        String msg = null;
        if (key.startsWith("messages.")) {
            msg = plugin.getCoreConfigManager().getMessages().getString(key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.match-manager." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.match-display." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.match-system." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString(key, null);
        }
        if (msg == null) {
            return "&c[missing: " + key + "]";
        }
        return MessageUtil.color(msg);
    }
    
    /**
     * Holt eine Nachricht mit Platzhalter-Ersetzung.
     */
    private String getMsg(String key, String placeholder, String value) {
        return getMsg(key, new String[]{placeholder, value});
    }

    private String getMsg(String key, String... replacements) {
        String msg = getMsg(key);
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String raw = replacements[i] != null ? replacements[i].replaceAll("^[{%]+|[%}]+$", "") : "";
                String val = replacements[i + 1] != null ? replacements[i + 1] : "";
                if (!raw.isEmpty()) {
                    msg = msg.replace("{" + raw + "}", val)
                             .replace("%" + raw + "%", val);
                }
            }
        }
        return msg;
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
            rememberOrigin(match, player1.getUniqueId(), player1.getLocation());
            rememberOrigin(match, player2.getUniqueId(), player2.getLocation());
        }
    }
    
    public void handleWagerConfirmation(Player player1, Player player2) {
        Match match = getMatch(player1, player2);
        if (match == null) return;

        // Check arena availability BEFORE deducting wagers
        if (plugin.getArenaManager().getArenas().isEmpty()) {
            MessageUtil.sendMessage(player1, getMsg("no-arenas"));
            MessageUtil.sendMessage(player2, getMsg("no-arenas"));
            endMatch(match, null, true);
            return;
        }

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

                // Verify both have funds before withdrawing anyone
                if (p1Money > 0 && !plugin.getEconomy().has(player1, p1Money)) {
                    MessageUtil.sendMessage(player1, getMsg("not-enough-money"));
                    endMatch(match, null, true);
                    return;
                }
                if (p2Money > 0 && !plugin.getEconomy().has(player2, p2Money)) {
                    MessageUtil.sendMessage(player2, getMsg("not-enough-money"));
                    endMatch(match, null, true);
                    return;
                }

                // Now withdraw atomically (both verified)
                if (p1Money > 0) {
                    plugin.getEconomy().withdrawPlayer(player1, p1Money);
                }
                if (p2Money > 0) {
                    plugin.getEconomy().withdrawPlayer(player2, p2Money);
                }
            }
        }

        // Start arena selection for command-based matches
        Arena firstArena = plugin.getArenaManager().getArenas().values().iterator().next();
        handleArenaSelection(player1, player2, firstArena);
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
        match.broadcast(getMsg("arena-display", "arena", arena.getDisplayName()));
        match.broadcast(getMsg("world-announcement", "world", arena.getArenaWorld()));
        match.broadcast("");
        match.broadcast(getMsg("please-wait"));
        
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
        
        plugin.getLogger().info("Starting DIRECT match in world: " + arenaWorld.getName());  // i18n-ignore: technical match trace

        // Inventare sichern, solange die Spieler noch in ihrer Ursprungswelt stehen
        if (!beginInventorySessions(match)) {
            return;
        }

        // Teleport players
        spawnManager.teleportPlayers(player1, player2, arena, arenaWorld);
        teleportedPlayers.add(player1.getUniqueId());
        teleportedPlayers.add(player2.getUniqueId());
        
        // Wait for teleport, then apply equipment
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Verify in correct world
            if (!player1.getWorld().equals(arenaWorld) || !player2.getWorld().equals(arenaWorld)) {
                plugin.getLogger().warning("Players not in arena world after teleport!");  // i18n-ignore: technical match verification log
            }
            
            // Inventare wurden vor dem Teleport gesichert (beginInventorySessions).
            markInventorySessionsActive(match);

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
    
    /**
     * Sichert die Survival-Inventare beider Spieler, bevor sie in die Arena teleportiert werden.
     *
     * <p>Der Zeitpunkt ist entscheidend: nach dem Weltwechsel haette Multiverse-Inventories das
     * Inventar bereits getauscht, und der Abzug enthielte den (meist leeren) Stand der
     * Arena-Welt. Ein Restore daraus wuerde das echte Inventar loeschen statt es zu retten.
     * Der Aufruf gehoert deshalb unmittelbar vor jeden {@code teleportPlayers}-Aufruf.</p>
     *
     * <p>Ebenso wichtig ist, dass der Wetteinsatz zu diesem Zeitpunkt bereits abgezogen ist
     * (das erledigt {@code handleWagerConfirmation} vor dem Arena-Teleport). Nur dann bildet
     * der Abzug den Stand ab, den der Verlierer korrekt zurueckbekommt.</p>
     *
     * @return false, wenn das Match nicht starten darf - dann wurde es bereits beendet
     */
    private boolean beginInventorySessions(Match match) {
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();

        de.zfzfg.core.inventory.InventorySessionManager sessions = plugin.getInventorySessions();
        if (sessions == null || !sessions.isManaged()) {
            return true;
        }

        for (Player player : new Player[]{player1, player2}) {
            Player opponent = match.getOpponent(player);
            de.zfzfg.core.inventory.BackupContext context =
                    de.zfzfg.core.inventory.BackupContext
                            .builder(de.zfzfg.core.inventory.BackupContext.TYPE_PVP_PRE_MATCH)
                            .meta("match_id", match.getMatchId().toString())
                            .meta("match_short_id", match.getEventMatchIdShort())
                            .meta("opponent", opponent == null ? "" : opponent.getName())
                            .meta("arena", match.getArena() == null ? "" : match.getArena().getId())
                            .meta("origin_world", player.getWorld() == null ? "" : player.getWorld().getName())
                            .build();

            de.zfzfg.core.inventory.InventorySessionManager.BeginResult result =
                    sessions.begin(player, de.zfzfg.core.inventory.guard.GuardContext.PVP_MATCH,
                            match.getMatchId().toString(), context,
                            persisted -> onPreMatchBackupWritten(match, player, persisted));

            if (result == de.zfzfg.core.inventory.InventorySessionManager.BeginResult.ALREADY_OPEN) {
                // Der Spieler haengt noch in einer anderen Sitzung (Event oder altes Match).
                // Ein zweites Backup entstuende ueber dem Kit-Zustand und machte das erste
                // wertlos - deshalb hier abbrechen statt ueberschreiben.
                plugin.getLogger().warning(plugin.getConsoleMsg("inventory-session-conflict",
                        "player", player.getName()));
                abortForInventory(match, player1, player2, sessions, "inventory-session-conflict");
                return false;
            }
            if (result == de.zfzfg.core.inventory.InventorySessionManager.BeginResult.UNAVAILABLE) {
                // Verwaltung eingeschaltet, aber kein Provider da. Das Kit anzulegen hiesse,
                // das Survival-Inventar ohne jede Absicherung zu loeschen.
                plugin.getLogger().severe(plugin.getConsoleMsg("inventory-provider-unavailable"));
                abortForInventory(match, player1, player2, sessions, "inventory-backup-failed-abort");
                return false;
            }
        }
        return true;
    }

    /** Bricht ein Match ab, weil die Inventare nicht sicher gesichert werden konnten. */
    private void abortForInventory(Match match, Player player1, Player player2,
                                   de.zfzfg.core.inventory.InventorySessionManager sessions,
                                   String messageKey) {
        MessageUtil.sendMessage(player1, getMsg(messageKey));
        MessageUtil.sendMessage(player2, getMsg(messageKey));
        sessions.discard(player1.getUniqueId());
        sessions.discard(player2.getUniqueId());
        endMatch(match, null, true);
    }

    /**
     * Reaktion darauf, ob das Pre-Match-Backup tatsaechlich auf der Platte gelandet ist.
     *
     * <p>Bei {@code on-backup-failure: abort} wird das Match beendet: ohne persistiertes Backup
     * ueberlebt die Sitzung keinen Serverabsturz, und genau davor soll die Umstellung schuetzen.
     * Bei {@code warn} laeuft das Match mit dem Abzug im Arbeitsspeicher weiter.</p>
     */
    private void onPreMatchBackupWritten(Match match, Player player, boolean persisted) {
        if (persisted) {
            return;
        }
        boolean abort = plugin.getInventoryConfig().failurePolicy()
                == de.zfzfg.core.inventory.InventoryManagementConfig.FailurePolicy.ABORT;
        if (!abort) {
            MessageUtil.sendMessage(player, getMsg("inventory-backup-degraded"));
            return;
        }
        if (match.getState() == MatchState.ENDED) {
            return;
        }
        plugin.getLogger().severe(plugin.getConsoleMsg("inventory-match-aborted", "player", player.getName()));
        MessageUtil.sendMessage(match.getPlayer1(), getMsg("inventory-backup-failed-abort"));
        MessageUtil.sendMessage(match.getPlayer2(), getMsg("inventory-backup-failed-abort"));
        endMatch(match, null, true);
    }

    /** Meldet dem Guard, dass die Spieler jetzt das Kit tragen und das Match laeuft. */
    private void markInventorySessionsActive(Match match) {
        de.zfzfg.core.inventory.InventorySessionManager sessions = plugin.getInventorySessions();
        if (sessions == null || !sessions.isManaged()) {
            return;
        }
        sessions.markActive(match.getPlayer1().getUniqueId());
        sessions.markActive(match.getPlayer2().getUniqueId());
    }

    /**
     * Stellt das Survival-Inventar eines Spielers nach dem Match wieder her.
     *
     * <p>Muss nach dem Rueckteleport laufen: waehrend eines Weltwechsels wuerde ein aktives
     * Multiverse-Inventories das gerade Wiederhergestellte sofort wieder ueberschreiben.
     * Den Abstand liefert {@code EventPlugin.getInventoryRestoreDelayTicks()} - ohne
     * Multiverse-Inventories ist er 0. Er ist aber nur Puffer; die eigentliche Zusage gibt
     * die Nachkontrolle in {@code InventorySessionManager.finish()}, die einen Tick spaeter
     * prueft, ob noch der gewollte Zustand steht.</p>
     *
     * @param onRestored laeuft <b>immer</b>, sobald feststeht, wie es ausgegangen ist. Der
     *                   Parameter sagt, ob der Spieler die Items jetzt schon traegt. Bei
     *                   {@code false} darf nichts direkt ins Inventar gegeben werden - der
     *                   Gewinn gehoert dann in {@code PendingPayoutStore}.
     */
    private void restoreInventoryAfterMatch(Player player, java.util.function.Consumer<Boolean> onRestored) {
        de.zfzfg.core.inventory.InventorySessionManager sessions = plugin.getInventorySessions();
        if (sessions == null || !sessions.isManaged()
                || !plugin.getInventoryConfig().restoreOnMatchEnd()) {
            // Keine Inventarverwaltung aktiv: das Inventar wurde nie angetastet, der Gewinn
            // kann direkt hinein.
            if (onRestored != null) {
                onRestored.accept(player.isOnline());
            }
            return;
        }
        final java.util.UUID playerId = player.getUniqueId();
        if (!plugin.getInventoryGuard().hasOpenSession(playerId)) {
            if (onRestored != null) {
                onRestored.accept(player.isOnline());
            }
            return;
        }
        sessions.finish(playerId, outcome -> {
            if (outcome == de.zfzfg.core.inventory.RestoreOutcome.QUEUED_FOR_JOIN) {
                plugin.getLogger().info(plugin.getConsoleMsg("inventory-restore-queued",
                        "player", player.getName()));
            }
            // Frueher endete dieser Zweig bei allem ausser APPLIED ohne Aufruf - der Gewinn
            // war damit weg, sobald der Spieler beim Match-Ende offline war oder die
            // Wiederherstellung fehlschlug. Jetzt entscheidet der Aufrufer anhand des Flags,
            // ob direkt ausgegeben oder vorgemerkt wird.
            if (onRestored != null) {
                onRestored.accept(outcome.isApplied() && player.isOnline());
            }
        });
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
                plugin.getLogger().severe("Arena world not found after loading: " + arena.getArenaWorld());  // i18n-ignore: technical match verification log
                MessageUtil.sendMessage(player1, getMsg("arena-load-failed"));
                MessageUtil.sendMessage(player2, getMsg("arena-load-failed"));
                endMatch(match, null, true);
                return;
            }

            // 5s PRE-TELEPORT countdown with invite, then perform teleport and continue
            startPreTeleportCountdown(match, 5, () -> {
                plugin.getLogger().info("Starting match in world: " + arenaWorld.getName());  // i18n-ignore: technical match trace

                // Inventare sichern, solange die Spieler noch in ihrer Ursprungswelt stehen
                if (!beginInventorySessions(match)) {
                    return;
                }

                // Teleport players using SpawnManager
                plugin.getLogger().info("Teleporting players with spawn-type: " + arena.getSpawnType());  // i18n-ignore: technical match trace
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
            plugin.getLogger().warning("Players not in arena world after teleport!");  // i18n-ignore: technical match verification log
            plugin.getLogger().warning("P1 World: " + player1.getWorld().getName());  // i18n-ignore: technical match verification log
            plugin.getLogger().warning("P2 World: " + player2.getWorld().getName());  // i18n-ignore: technical match verification log
            plugin.getLogger().warning("Arena World: " + arenaWorld.getName());  // i18n-ignore: technical match verification log

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
        plugin.getLogger().info("Attempting emergency teleport...");  // i18n-ignore: technical match recovery log
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
                    plugin.getLogger().severe("Emergency teleport failed! Ending match.");  // i18n-ignore: technical match recovery log
                    MessageUtil.sendMessage(player1, getMsg("arena-teleport-failed"));
                    MessageUtil.sendMessage(player2, getMsg("arena-teleport-failed"));
                    endMatch(match, null, true);
                    return;
                }
                continueMatchSetup(match, player1, player2, arenaWorld);
            }, de.zfzfg.core.util.Time.seconds(1));

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Emergency teleport failed with exception: " + e.getMessage());  // i18n-ignore: technical match recovery log
            MessageUtil.sendMessage(player1, getMsg("arena-setup-failed"));
            MessageUtil.sendMessage(player2, getMsg("arena-setup-failed"));
            endMatch(match, null, true);
            return false;
        }
    }
    
    private void continueMatchSetup(Match match, Player player1, Player player2, World arenaWorld) {
        // Inventare wurden vor dem Teleport gesichert (beginInventorySessions).
        markInventorySessionsActive(match);

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
        
        plugin.getDebugManager().log("Applying equipment to " + player.getName() + " in world: " + player.getWorld().getName());  // i18n-ignore: technical equipment debug log
        
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
        // Nebenhand: das Web-Panel bietet sie seit jeher an, angezogen wurde sie erst ab 1.0.9.
        if (equipment.getOffhand() != null) {
            player.getInventory().setItemInOffHand(equipment.getOffhand().clone());
        }

        // Apply inventory items
        if (equipment.getInventory() != null) {
            for (Map.Entry<Integer, ItemStack> entry : equipment.getInventory().entrySet()) {
                player.getInventory().setItem(entry.getKey(), entry.getValue().clone());
            }
        }
        
        plugin.getDebugManager().log("Equipment applied to " + player.getName());  // i18n-ignore: technical equipment debug log
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
                    plugin.getDebugManager().log("Equipment verified for " + player.getName() + " (attempt " + attempt + ")");  // i18n-ignore: technical equipment debug log
                    cancel();
                    return;
                }

                if (attempt >= maxAttempts) {
                    plugin.getLogger().warning("Equipment could not be verified for " + player.getName() + " after " + maxAttempts + " attempts.");  // i18n-ignore: technical equipment debug log
                    cancel();
                    return;
                }

                attempt++;
                plugin.getLogger().warning("Equipment not applied correctly to " + player.getName() + ", retrying (attempt " + attempt + ")...");  // i18n-ignore: technical equipment debug log
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
        
        List<BukkitTask> taskList = new ArrayList<>();
        for (int i = countdownTime; i > 0; i--) {
            final int seconds = i;
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.getState() != MatchState.STARTING) return;
                
                String message = getMsg("match-countdown", "seconds", String.valueOf(seconds));
                match.broadcast(message);
                
                // Play sound
                player1.playSound(player1.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                player2.playSound(player2.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                
            }, (countdownTime - i) * 20L);
            taskList.add(task);
        }
        
        // Start the match after countdown
        BukkitTask startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getState() == MatchState.STARTING) {
                startFight(match);
            }
        }, (countdownTime + 1) * 20L);
        taskList.add(startTask);
        countdownTaskLists.put(match.getMatchId(), taskList);
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
                    } catch (Exception e) {
                        plugin.getLogger().warning("Pre-teleport callback failed: " + e.getMessage());  // i18n-ignore: technical match callback log
                    }
                    return;
                }

                match.broadcast(getMsg("teleport-countdown", "seconds", String.valueOf(remaining)));
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

            Component header = Text.of(
                getMsg("spectate-header", "player1", p1.getName(), "player2", p2.getName())
            );

            Component spectateBtn1 = Text.button(
                getMsg("spectate-button"),
                "/pvp spectate " + p1.getName(),
                getMsg("spectate-hover")
            );

            Component footer = Text.of(getMsg("spectate-footer"));
            Component fullMessage = header.append(spectateBtn1).append(footer);

            for (Player online : Bukkit.getOnlinePlayers()) {
                // Don't send to match participants; they already see match messages
                if (online.equals(p1) || online.equals(p2)) continue;
                de.zfzfg.core.util.TextUtil.send(online, fullMessage);
            }
        } catch (Throwable e) {
            // Fallback simple broadcast
            for (Player online : Bukkit.getOnlinePlayers()) {
                Player p1 = match.getPlayer1();
                Player p2 = match.getPlayer2();
                if (online.equals(p1) || online.equals(p2)) continue;
                MessageUtil.sendMessage(online, getMsg("spectate-simple", "player1", p1.getName(), "player2", p2.getName()));
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
                match.broadcast(getMsg("time-up"));
                match.broadcast("&c&l━━━━━━━━━━━━━━━━━━━━━━━");
                match.broadcast("");
                match.broadcast(getMsg("timeout-draw"));
                match.broadcast("");
                
                endMatch(match, null, true);
            } else if (java.util.Arrays.stream(MATCH_TIMER_ANNOUNCE_SECONDS).anyMatch(s -> s == remaining)) {
                match.broadcast(getMsg("match-ends-in", "seconds", String.valueOf(remaining)));
            }
        }, 0L, de.zfzfg.core.util.Time.TICKS_PER_SECOND);
        
        synchronized (matchOpMutex) {
            matchTimerTasks.put(match.getMatchId(), timerTask);
        }
    }
    
    /**
     * Merkt sich, wohin ein Match-Teilnehmer zurueckgehoert - im Match-Objekt und dauerhaft.
     *
     * <p>Das Match lebt nur im Arbeitsspeicher; nach einem Absturz waere die Position sonst
     * verloren, waehrend das Inventar ueber das Guard-Journal zurueckkaeme.</p>
     */
    private void rememberOrigin(Match match, java.util.UUID playerId, org.bukkit.Location location) {
        if (location == null) {
            return;
        }
        match.getOriginalLocations().put(playerId, location.clone());
        if (plugin.getReturnLocations() != null) {
            plugin.getReturnLocations().remember(playerId, location,
                    de.zfzfg.core.location.ReturnReason.PVP_MATCH);
        }
    }

    /** Gegenstueck zu {@link #rememberOrigin}: der Rueckweg ist erledigt. */
    private void forgetOrigin(java.util.UUID playerId) {
        if (plugin.getReturnLocations() != null) {
            plugin.getReturnLocations().forget(playerId);
        }
    }

    public void endMatch(Match match, Player winner, boolean isDraw) {
        // Cancel tasks (unter Lock)
        List<BukkitTask> countdownTaskList;
        BukkitTask preTeleportTask;
        BukkitTask timerTask;
        synchronized (matchOpMutex) {
            countdownTaskList = countdownTaskLists.remove(match.getMatchId());
            preTeleportTask = preTeleportCountdownTasks.remove(match.getMatchId());
            timerTask = matchTimerTasks.remove(match.getMatchId());
        }
        if (countdownTaskList != null) {
            for (BukkitTask task : countdownTaskList) {
                if (task != null) task.cancel();
            }
        }
        if (preTeleportTask != null) preTeleportTask.cancel();
        if (timerTask != null) timerTask.cancel();
        
        match.setState(MatchState.ENDED);
        
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        
        // Handle winnings/returns based on mode
        if (match.isNoWagerMode()) {
            // No wager mode - nothing to distribute
            MessageUtil.sendMessage(player1, getMsg("no-wager-items"));
            MessageUtil.sendMessage(player2, getMsg("no-wager-items"));
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
            plugin.markExternalDisplayDirty();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to record match statistics: " + e.getMessage());  // i18n-ignore: technical stats log
        }

        // Post-Match Inventar-Backups sind deaktiviert
        
        // Welt-Reset je nach Konfiguration
        if (match.getArena() != null) {
            String worldName = match.getArena().getArenaWorld();
            String cloneSource = match.getArena().getCloneSourceWorld();
            if (cloneSource != null && !cloneSource.isEmpty()) {
                plugin.getLogger().info("Scheduling clone reset for arena world: " + worldName + " from " + cloneSource);  // i18n-ignore: technical world reset log
                // Nach Rück-Teleport der Spieler ausführen
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getArenaManager().resetArenaWorldByClone(cloneSource, worldName);
                }, de.zfzfg.core.util.Time.seconds(7)); // 7 Sekunden nach Match-Ende
            } else if (match.getArena().isRegenerateWorld()) {
                plugin.getLogger().info("Scheduling Multiverse regeneration for arena world: " + worldName);  // i18n-ignore: technical world reset log
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
                    forgetOrigin(spectatorId);
                }
                teleportedPlayers.remove(match.getPlayer1().getUniqueId());
                teleportedPlayers.remove(match.getPlayer2().getUniqueId());
                forgetOrigin(match.getPlayer1().getUniqueId());
                forgetOrigin(match.getPlayer2().getUniqueId());
                matches.remove(matchId);
            }
            
        }, de.zfzfg.core.util.Time.seconds(MATCH_CLEANUP_DELAY_SECONDS));
    }
    
    private void distributeWinnings(Match match, Player winner) {
        Player loser = match.getOpponent(winner);
        untagCombat(winner);
        untagCombat(loser);

        // WICHTIG: Erst zur ursprünglichen Location teleportieren
        Location winnerOriginal = match.getOriginalLocations().get(winner.getUniqueId());
        if (winnerOriginal != null) {
            winner.teleport(winnerOriginal);
            plugin.getLogger().info("Teleported winner " + winner.getName() + " back to original location");  // i18n-ignore: technical match teleport log
        }
        
        // Reihenfolge: Teleport -> Wiederherstellung des Survival-Inventars -> Gewinn.
        // Andersherum wuerde der Restore (clearBefore) den gerade uebergebenen Gewinn
        // im selben Tick wieder loeschen. Die Wartezeit kommt aus der Multiverse-Bridge
        // und ist ohne Multiverse-Inventories 0.
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreInventoryAfterMatch(winner, inventoryReady -> {
            if (!plugin.getInventorySessions().claimPayout(winner.getUniqueId())) {
                // Bereits ausgeschuettet (z. B. durch den Shutdown-Pfad) - nicht doppelt.
                return;
            }
            List<ItemStack> allItems = new ArrayList<>();
            allItems.addAll(match.getWagerItems(match.getPlayer1()));
            allItems.addAll(match.getWagerItems(match.getPlayer2()));

            double totalMoney = 0;
            if (plugin.hasEconomy()) {
                totalMoney = match.getWagerMoney(match.getPlayer1()) + match.getWagerMoney(match.getPlayer2());
            }

            // Konnte das Inventar nicht wiederhergestellt werden - Spieler offline oder
            // Restore fehlgeschlagen -, wandert der gesamte Gewinn in den Auffangspeicher
            // und wird beim naechsten Join nachgereicht. Direkt ausgeben hiesse: weg.
            boolean handedOut = plugin.getPendingPayouts().deliverOrQueue(
                    winner, allItems, totalMoney, "pvp-win", inventoryReady);
            if (!handedOut) {
                return;  // Die Erfolgsmeldungen ergaeben ohne den Gewinn keinen Sinn.
            }

            String wonHeader = plugin.getCoreConfigManager().getMessages().getString("messages.match-display.you-won-header", "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
            String wonTitle = plugin.getCoreConfigManager().getMessages().getString("messages.match-display.you-won", "&a&lYOU WON THE MATCH!");
            MessageUtil.sendMessage(winner, "");
            MessageUtil.sendMessage(winner, wonHeader);
            MessageUtil.sendMessage(winner, wonTitle);
            MessageUtil.sendMessage(winner, wonHeader);
            MessageUtil.sendMessage(winner, "");
            List<ItemStack> opponentItems = match.getWagerItems(loser);
            List<ItemStack> ownItems = match.getWagerItems(winner);
            if (opponentItems != null && !opponentItems.isEmpty()) {
                MessageUtil.sendMessage(winner, getMsg("received-from-opponent-items", "items", formatItemList(opponentItems)));
            }
            if (ownItems != null && !ownItems.isEmpty()) {
                MessageUtil.sendMessage(winner, getMsg("own-stake-added-items", "items", formatItemList(ownItems)));
            }
            
            // Nur noch die Meldungen: die Gutschrift selbst hat deliverOrQueue() oben
            // zusammen mit den Items erledigt. Beides muss denselben Weg gehen, sonst
            // bekaeme ein offline ausgeschiedener Gewinner sein Geld sofort und seine
            // Items erst beim Join.
            if (totalMoney > 0) {
                double opponentMoney = match.getWagerMoney(loser);
                double ownMoney = match.getWagerMoney(winner);
                if (opponentMoney > 0) {
                    MessageUtil.sendMessage(winner, getMsg("received-from-opponent-money", "amount", String.format("%.2f", opponentMoney)));
                }
                if (ownMoney > 0) {
                    MessageUtil.sendMessage(winner, getMsg("own-stake-added-money", "amount", String.format("%.2f", ownMoney)));
                }
            }
            MessageUtil.sendMessage(winner, "");

        }), plugin.getInventoryRestoreDelayTicks());

        // Notify loser
        MessageUtil.sendMessage(loser, "");
        MessageUtil.sendMessage(loser, getMsg("you-lost-header"));
        MessageUtil.sendMessage(loser, getMsg("you-lost"));
        MessageUtil.sendMessage(loser, getMsg("you-lost-header"));
        MessageUtil.sendMessage(loser, "");
        List<ItemStack> lostItems = match.getWagerItems(loser);
        if (lostItems != null && !lostItems.isEmpty()) {
            MessageUtil.sendMessage(loser, getMsg("you-lost-items", "items", formatItemList(lostItems)));
        }
        if (plugin.hasEconomy()) {
            double lostMoney = match.getWagerMoney(loser);
            if (lostMoney > 0) {
                MessageUtil.sendMessage(loser, getMsg("you-lost-money", "amount", String.format("%.2f", lostMoney)));
            }
        }
        MessageUtil.sendMessage(loser, getMsg("better-luck"));
        MessageUtil.sendMessage(loser, "");
    }
    
    private void distributeItemsBack(Match match) {
        // Return items to original owners
        Player player1 = match.getPlayer1();
        Player player2 = match.getPlayer2();
        untagCombat(player1);
        untagCombat(player2);

        // Teleport back first
        Location p1Original = match.getOriginalLocations().get(player1.getUniqueId());
        Location p2Original = match.getOriginalLocations().get(player2.getUniqueId());
        
        if (p1Original != null) player1.teleport(p1Original);
        if (p2Original != null) player2.teleport(p2Original);
        
        // Erst wiederherstellen, dann den Einsatz zurueckgeben - sonst loescht der Restore
        // die gerade zurueckgegebenen Items wieder.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restoreInventoryAfterMatch(player1, ready -> returnOwnStake(match, player1, ready));
            restoreInventoryAfterMatch(player2, ready -> returnOwnStake(match, player2, ready));
        }, plugin.getInventoryRestoreDelayTicks());
    }

    /**
     * Gibt einem Spieler seinen eigenen Einsatz zurueck - genau einmal (Guard).
     *
     * @param inventoryReady ob das Survival-Inventar bereits wiederhergestellt ist. Bei
     *                       {@code false} wird der Einsatz vorgemerkt statt ausgegeben;
     *                       frueher fiel er in diesem Fall ersatzlos weg.
     */
    private void returnOwnStake(Match match, Player player, boolean inventoryReady) {
        if (plugin.getInventorySessions() != null
                && !plugin.getInventorySessions().claimPayout(player.getUniqueId())) {
            return;
        }

        double money = plugin.hasEconomy() ? match.getWagerMoney(player) : 0;
        boolean handedOut = plugin.getPendingPayouts().deliverOrQueue(
                player, match.getWagerItems(player), money, "pvp-draw-return", inventoryReady);

        if (handedOut) {
            MessageUtil.sendMessage(player, getMsg("wager-returned"));
        }
    }
    
    private void teleportPlayerBack(Player player, Match match) {
        untagCombat(player);

        // WICHTIG: Tote Spieler können nicht teleportiert werden!
        // Sie werden über den PlayerRespawnEvent behandelt.
        // Aber GameMode und Effekte müssen trotzdem zurückgesetzt werden!
        if (player.isDead()) {
            plugin.getLogger().info(plugin.getConsoleMsg("match-dead-player-gamemode", "player", player.getName()));
            try { player.setGameMode(GameMode.SURVIVAL); } catch (Exception e) {
                plugin.getLogger().warning("Failed to set gamemode for dead player " + player.getName() + ": " + e.getMessage());  // i18n-ignore: technical exception log
            }
            try { player.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception e) {
                plugin.getLogger().warning("Failed to remove invisibility from dead player " + player.getName() + ": " + e.getMessage());  // i18n-ignore: technical exception log
            }
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
                    safeLocation = targetWorld.getSpawnLocation();
                }
                
                plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", worldName, "coords", String.format("%d, %d, %d", safeLocation.getBlockX(), safeLocation.getBlockY(), safeLocation.getBlockZ())));
                
                // Secure Teleport Logic: Cache and Verify
                teleportVerificationCache.put(player.getUniqueId(), safeLocation);
                player.teleport(safeLocation);
                
                // Verify after short delay (10 ticks = 0.5s)
                Bukkit.getScheduler().runTaskLater(plugin, () -> verifyTeleportBack(player), 10L);
            } else {
                // Welt nicht mehr geladen - Fallback zu Hauptwelt
                plugin.getLogger().warning(plugin.getConsoleMsg("safe-teleport-fallback", "player", player.getName()));
                teleportToMainWorldFallback(player);
            }
        } else {
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-teleport-no-location", "player", player.getName()));
            teleportToMainWorldFallback(player);
        }
        
        // Survival-Inventar erst nach dem abgeschlossenen Weltwechsel zurueckspielen: laeuft
        // Multiverse-Inventories parallel, wuerde dessen Weltwechsel-Hook ein frueheres
        // Ergebnis sofort wieder ueberschreiben. Der Aufruf ist idempotent - fuer den
        // Gewinner ist die Sitzung hier laengst geschlossen und es passiert nichts.
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> restoreInventoryAfterMatch(player, null), plugin.getInventoryRestoreDelayTicks());

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
            plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", mainWorld.getName(), "coords", String.format("%.0f, %.0f, %.0f", spawn.getX(), spawn.getY(), spawn.getZ())));
            player.teleport(spawn);
        } else {
            plugin.getLogger().severe(plugin.getConsoleMsg("safe-teleport-critical", "player", player.getName()));
        }
    }

    private void untagCombat(Player player) {
        if (player != null) {
            plugin.getCombatBridge().untagPlayer(player);
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
            plugin.getLogger().warning("Teleport verification failed for " + player.getName() +   // i18n-ignore: technical teleport verification log
                ". Expected: " + (expected.getWorld() != null ? expected.getWorld().getName() : "null") +   // i18n-ignore: technical teleport verification log
                ", Actual: " + (current.getWorld() != null ? current.getWorld().getName() : "null"));  // i18n-ignore: technical validation log
            
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
        return java.util.Collections.unmodifiableMap(matches);
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
            match.broadcast(getMsg("server-shutdown"));
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
        List<BukkitTask> countdownTaskList;
        BukkitTask preTeleportTask;
        BukkitTask timerTask;
        synchronized (matchOpMutex) {
            countdownTaskList = countdownTaskLists.remove(match.getMatchId());
            preTeleportTask = preTeleportCountdownTasks.remove(match.getMatchId());
            timerTask = matchTimerTasks.remove(match.getMatchId());
        }
        if (countdownTaskList != null) {
            for (BukkitTask task : countdownTaskList) {
                try { task.cancel(); } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel countdown task: " + e.getMessage());  // i18n-ignore: technical exception log
                }
            }
        }
        if (preTeleportTask != null) try { preTeleportTask.cancel(); } catch (Exception e) {
            plugin.getLogger().warning("Failed to cancel pre-teleport task: " + e.getMessage());  // i18n-ignore: technical exception log
        }
        if (timerTask != null) try { timerTask.cancel(); } catch (Exception e) {
            plugin.getLogger().warning("Failed to cancel timer task: " + e.getMessage());  // i18n-ignore: technical exception log
        }

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
        untagCombat(player1);
        untagCombat(player2);

        // Rückgabe abhängig vom Modus
        if (!match.isNoWagerMode()) {
            // Items direkt zurück. Im onDisable laeuft der Scheduler nicht mehr, also KEIN
            // Restore-Versuch hier - die offenen Sitzungen stehen im Guard-Journal und werden
            // beim naechsten Serverstart abgearbeitet. Nur die Ausschuettung wird vermerkt,
            // damit der Wiederanlauf sie nicht ein zweites Mal vornimmt.
            if (plugin.getInventorySessions() != null) {
                plugin.getInventorySessions().claimPayout(player1.getUniqueId());
                plugin.getInventorySessions().claimPayout(player2.getUniqueId());
            }
            try {
                InventoryUtil.giveItems(player1, match.getWagerItems(player1));
                InventoryUtil.giveItems(player2, match.getWagerItems(player2));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to return items on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
            }

            // Geld direkt zurück
            try {
                if (plugin.hasEconomy()) {
                    double p1Money = match.getWagerMoney(player1);
                    double p2Money = match.getWagerMoney(player2);
                    if (p1Money > 0) plugin.getEconomy().depositPlayer(player1, p1Money);
                    if (p2Money > 0) plugin.getEconomy().depositPlayer(player2, p2Money);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to return money on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
            }
        }

        // Status von Spielern zurücksetzen
        try { player1.setGameMode(GameMode.SURVIVAL); } catch (Exception e) {
            plugin.getLogger().warning("Failed to set gamemode for player1 on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
        }
        try { player2.setGameMode(GameMode.SURVIVAL); } catch (Exception e) {
            plugin.getLogger().warning("Failed to set gamemode for player2 on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
        }
        try { player1.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove invisibility from player1 on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
        }
        try { player2.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove invisibility from player2 on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
        }

        // Zuschauer zurücksetzen
        for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                Location origin = match.getOriginalLocations().get(spectatorId);
                if (origin != null) {
                    try { spectator.teleport(origin); } catch (Exception e) {
                        plugin.getLogger().warning("Failed to teleport spectator on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
                    }
                }
                try { spectator.setGameMode(GameMode.SURVIVAL); } catch (Exception e) {
                    plugin.getLogger().warning("Failed to set gamemode for spectator on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
                }
                try { spectator.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Exception e) {
                    plugin.getLogger().warning("Failed to remove invisibility from spectator on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
                }
            }
        }

        // Statistiken als Unentschieden
        try {
            plugin.getStatsManager().recordDraw(player1);
            plugin.getStatsManager().recordDraw(player2);
            plugin.markExternalDisplayDirty();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to record draw statistics on shutdown: " + e.getMessage());  // i18n-ignore: technical exception log
        }

        // Cleanup ohne Verzögerung
        synchronized (matchOpMutex) {
            UUID matchId = match.getMatchId();
            playerToMatchId.remove(player1.getUniqueId());
            playerToMatchId.remove(player2.getUniqueId());
            for (UUID spectatorId : new ArrayList<>(match.getSpectators())) {
                playerToMatchId.remove(spectatorId);
                teleportedPlayers.remove(spectatorId);
                forgetOrigin(spectatorId);
            }
            teleportedPlayers.remove(player1.getUniqueId());
            teleportedPlayers.remove(player2.getUniqueId());
            forgetOrigin(player1.getUniqueId());
            forgetOrigin(player2.getUniqueId());
            matches.remove(matchId);
        }
    }

    // Tasks sauber abbrechen (Reload/Disable)
    public void cancelAllTasks() {
        synchronized (matchOpMutex) {
            for (List<BukkitTask> taskList : countdownTaskLists.values()) {
                for (BukkitTask t : taskList) { try { t.cancel(); } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel countdown task in cleanup: " + e.getMessage());  // i18n-ignore: technical exception log
                } }
            }
            for (BukkitTask t : preTeleportCountdownTasks.values()) { try { t.cancel(); } catch (Exception e) {
                plugin.getLogger().warning("Failed to cancel pre-teleport task in cleanup: " + e.getMessage());  // i18n-ignore: technical exception log
            } }
            for (BukkitTask t : matchTimerTasks.values()) { try { t.cancel(); } catch (Exception e) {
                plugin.getLogger().warning("Failed to cancel timer task in cleanup: " + e.getMessage());  // i18n-ignore: technical exception log
            } }
            countdownTaskLists.clear();
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

    // Memory leak fix: remove stale teleport verification entry when player quits
    public void clearTeleportVerification(java.util.UUID playerId) {
        teleportVerificationCache.remove(playerId);
    }

    // Command-based match start (OHNE Countdown, DIREKT starten)
    public void startMatchFromCommand(CommandRequest request) {
        Player player1 = request.getSender();
        Player player2 = request.getTarget();

        // Clean up GUI sessions for both players (no item return needed - items already handled)
        cleanupGuiSessionsForMatch(player1, player2);

        // Validate arena and equipment BEFORE deducting wagers to prevent item/money loss
        Arena arena = plugin.getArenaManager().getArenaOptional(request.getFinalArenaId()).orElse(null);
        EquipmentSet p1Equipment = plugin.getEquipmentManager().getEquipmentSet(request.getFinalEquipmentId());
        EquipmentSet p2Equipment = plugin.getEquipmentManager().getEquipmentSet(request.getFinalEquipmentId());

        if (arena == null) {
            MessageUtil.sendMessage(player1, getMsg("arena-not-exists"));
            MessageUtil.sendMessage(player2, getMsg("arena-not-exists"));
            return;
        }

        // Create match
        Match match = new Match(player1, player2);
        matches.put(match.getMatchId(), match);
        // Index participants for O(1) lookup
        playerToMatchId.put(player1.getUniqueId(), match.getMatchId());
        playerToMatchId.put(player2.getUniqueId(), match.getMatchId());

        match.setArena(arena);
        match.setPlayer1Equipment(p1Equipment);
        match.setPlayer2Equipment(p2Equipment);

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
        
        // KRITISCH: Verwende die Original-Locations aus dem Request!
        // Diese wurden gespeichert als der Request erstellt wurde - BEVOR
        // die Spieler irgendwohin teleportiert wurden.
        // Das stellt sicher dass wir die "echte" Rückkehr-Location haben.
        Location senderOriginal = request.getSenderOriginalLocation();
        Location targetOriginal = request.getTargetOriginalLocation();
        
        if (senderOriginal != null && senderOriginal.getWorld() != null) {
            rememberOrigin(match, player1.getUniqueId(), senderOriginal);
            plugin.getLogger().info(plugin.getConsoleMsg("match-origin-saved", "player", player1.getName(), "location", String.format("%s @ %.2f, %.2f, %.2f", senderOriginal.getWorld().getName(), senderOriginal.getX(), senderOriginal.getY(), senderOriginal.getZ())));
        } else {
            // Fallback: aktuelle Position verwenden
            Location fallback = player1.getLocation().clone();
            rememberOrigin(match, player1.getUniqueId(), fallback);
            plugin.getLogger().warning(plugin.getConsoleMsg("match-origin-warning", "player", player1.getName()));
        }
        
        if (targetOriginal != null && targetOriginal.getWorld() != null) {
            rememberOrigin(match, player2.getUniqueId(), targetOriginal);
            plugin.getLogger().info(plugin.getConsoleMsg("match-origin-saved", "player", player2.getName(), "location", String.format("%s @ %.2f, %.2f, %.2f", targetOriginal.getWorld().getName(), targetOriginal.getX(), targetOriginal.getY(), targetOriginal.getZ())));
        } else {
            // Fallback: aktuelle Position verwenden
            Location fallback = player2.getLocation().clone();
            rememberOrigin(match, player2.getUniqueId(), fallback);
            plugin.getLogger().warning(plugin.getConsoleMsg("match-origin-warning", "player", player2.getName()));
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
        match.broadcast(getMsg("vs-display", "player1", player1.getName(), "player2", player2.getName()));
        match.broadcast(getMsg("arena-display", "arena", arena.getDisplayName()));
        match.broadcast(getMsg("equipment-display", "equipment", p1Equipment.getDisplayName()));
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
        plugin.getLogger().info(plugin.getConsoleMsg("world-loading", "world", worldName));
        
        // Use MultiverseHelper to load world with /mvload command
        plugin.getArenaManager().loadArenaWorld(worldName, () -> {
            // World loading completed, proceed with match start
            World arenaWorld = Bukkit.getWorld(worldName);
            if (arenaWorld == null) {
                plugin.getLogger().severe(plugin.getConsoleMsg("world-load-failed", "world", worldName, "msg", "world is null"));
                MessageUtil.error(player1, getMsg("arena-load-failed"));
                MessageUtil.error(player2, getMsg("arena-load-failed"));
                endMatch(match, null, true);
                return;
            }
            
            plugin.getLogger().info(plugin.getConsoleMsg("arena-world-loaded", "world", worldName, "msg", "OK"));
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
        rememberOrigin(match, sid, spectator.getLocation());
        playerToMatchId.put(sid, match.getMatchId());
        // Race-Condition-Schutz: sofort als teleported markieren
        teleportedPlayers.add(sid);
    }

    public void removeSpectator(Match match, Player spectator) {
        if (match == null || spectator == null) return;
        UUID sid = spectator.getUniqueId();
        match.getSpectators().remove(sid);
        match.getOriginalLocations().remove(sid);
        forgetOrigin(sid);
        playerToMatchId.remove(sid);
        teleportedPlayers.remove(sid);
        // Sofortige Ruecksetzung des Spieler-Zustands
        if (spectator.isOnline()) {
            try {
                spectator.setGameMode(GameMode.SURVIVAL);
            } catch (Exception ignored) {}
            try {
                spectator.removePotionEffect(PotionEffectType.INVISIBILITY);
            } catch (Exception ignored) {}
        }
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
