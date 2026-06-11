package de.zfzfg.core.monitoring.debug;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Zentraler Debug-Manager für das Event-PVP-Plugin.
 * 
 * Verwaltet Debug-Stufen, Ausgabekanäle und berechtigte Spieler.
 * Standardmäßig ist der Debug-Modus deaktiviert.
 * 
 * Debug-Stufen:
 * - OFF (0): Keine Debug-Ausgaben
 * - LEVEL_1 (1): Player, Event, Match Aktionen
 * - LEVEL_2 (2): + World, Config, Inventory Operationen  
 * - LEVEL_3 (3): + System, Timing, State Details
 */
public class DebugManager {

    private static DebugManager instance;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String debugPrefix = "&8[&bDEBUG&8]";
    
    // Debug-Einstellungen (Standardmäßig aus)
    private DebugLevel currentLevel = DebugLevel.OFF;
    private DebugOutput outputMode = DebugOutput.BOTH;
    
    // Spieler die Debug-Nachrichten im Chat erhalten sollen
    // Zusätzlich zu OPs und Spielern mit eventpvp.debug.receive
    private final Set<UUID> debugReceivers = new HashSet<>();
    
    // Zeitstempel-Format für Logs
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public DebugManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        instance = this;
    }

    /**
     * Gibt die Singleton-Instanz zurück.
     */
    public static DebugManager getInstance() {
        return instance;
    }

    // ==================== Konfiguration ====================

    /**
     * Setzt die Debug-Stufe.
     */
    public void setLevel(DebugLevel level) {
        DebugLevel oldLevel = this.currentLevel;
        this.currentLevel = level;
        
        if (level != DebugLevel.OFF) {
            log(DebugCategory.SYSTEM, DebugLevel.LEVEL_1, 
                "Debug-Level geändert: " + oldLevel.getDisplayName() + " -> " + level.getDisplayName());
        }
    }

    /**
     * Gibt die aktuelle Debug-Stufe zurück.
     */
    public DebugLevel getLevel() {
        return currentLevel;
    }

    /**
     * Prüft ob Debug aktiv ist.
     */
    public boolean isEnabled() {
        return currentLevel != DebugLevel.OFF;
    }

    /**
     * Setzt den Ausgabe-Modus.
     */
    public void setOutputMode(DebugOutput mode) {
        this.outputMode = mode;
        log(DebugCategory.SYSTEM, DebugLevel.LEVEL_1, 
            "Debug-Ausgabe geändert: " + mode.getDisplayName());
    }

    /**
     * Gibt den aktuellen Ausgabe-Modus zurück.
     */
    public DebugOutput getOutputMode() {
        return outputMode;
    }

    /**
     * Aktiviert Debug-Empfang für einen Spieler.
     */
    public void addReceiver(UUID playerId) {
        debugReceivers.add(playerId);
    }

    /**
     * Deaktiviert Debug-Empfang für einen Spieler.
     */
    public void removeReceiver(UUID playerId) {
        debugReceivers.remove(playerId);
    }

    /**
     * Prüft ob ein Spieler Debug-Nachrichten empfängt.
     */
    public boolean isReceiver(UUID playerId) {
        return debugReceivers.contains(playerId);
    }

    /**
     * Löscht alle manuellen Empfänger.
     */
    public void clearReceivers() {
        debugReceivers.clear();
    }

    // ==================== Logging-Methoden ====================

    /**
     * Loggt eine Debug-Nachricht, wenn die Kategorie aktiv ist.
     */
    public void log(DebugCategory category, String message) {
        log(category, category.getMinLevel(), message);
    }

    /**
     * Loggt eine Debug-Nachricht mit spezifischem Level.
     */
    public void log(DebugCategory category, DebugLevel requiredLevel, String message) {
        // Prüfe ob Debug aktiv und Level ausreichend
        if (currentLevel == DebugLevel.OFF || !currentLevel.isAtLeast(requiredLevel)) {
            return;
        }

        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String categoryTag = category.getColorCode() + "[" + category.getDisplayName() + "]&r";
        String formattedMessage = colorize(debugPrefix + " " + categoryTag + " &7" + message);

        // Konsolen-Ausgabe
        if (outputMode.includesConsole()) {
            // Für Konsole: Farben entfernen und formatieren
            String consoleMessage = "[" + timestamp + "] [DEBUG/" + category.name() + "] " + stripColor(message);
            logger.info(consoleMessage);
        }

        // Chat-Ausgabe
        if (outputMode.includesChat()) {
            sendToReceivers(formattedMessage);
        }
    }

    /**
     * Loggt eine Debug-Nachricht mit Exception-Details.
     */
    public void logException(DebugCategory category, String message, Throwable throwable) {
        log(category, DebugLevel.LEVEL_1, message + " - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        
        // Bei höherem Debug-Level: Stack-Trace loggen
        if (currentLevel.isAtLeast(DebugLevel.LEVEL_3)) {
            for (StackTraceElement element : throwable.getStackTrace()) {
                if (element.getClassName().startsWith("de.zfzfg")) {
                    log(category, DebugLevel.LEVEL_3, "  at " + element.toString());
                }
            }
        }
    }

    /**
     * Loggt einen Timing-Wert.
     */
    public void logTiming(String operation, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log(DebugCategory.TIMING, DebugLevel.LEVEL_3, operation + " dauerte " + durationMs + "ms");
    }

    // ==================== Convenience-Methoden ====================

    /**
     * Loggt eine Player-bezogene Nachricht (Level 1).
     */
    public void logPlayer(String message) {
        log(DebugCategory.PLAYER, message);
    }

    /**
     * Loggt eine Event-bezogene Nachricht (Level 1).
     */
    public void logEvent(String message) {
        log(DebugCategory.EVENT, message);
    }

    /**
     * Loggt eine Match-bezogene Nachricht (Level 1).
     */
    public void logMatch(String message) {
        log(DebugCategory.MATCH, message);
    }

    /**
     * Loggt eine World-bezogene Nachricht (Level 2).
     */
    public void logWorld(String message) {
        log(DebugCategory.WORLD, message);
    }

    /**
     * Loggt eine Config-bezogene Nachricht (Level 2).
     */
    public void logConfig(String message) {
        log(DebugCategory.CONFIG, message);
    }

    /**
     * Loggt eine Inventory-bezogene Nachricht (Level 2).
     */
    public void logInventory(String message) {
        log(DebugCategory.INVENTORY, message);
    }

    /**
     * Loggt eine System-bezogene Nachricht (Level 3).
     */
    public void logSystem(String message) {
        log(DebugCategory.SYSTEM, message);
    }

    /**
     * Loggt eine State-bezogene Nachricht (Level 3).
     */
    public void logState(String message) {
        log(DebugCategory.STATE, message);
    }

    // ==================== Neue Convenience-Methoden ====================

    /**
     * Loggt eine Command-bezogene Nachricht (Level 1).
     */
    public void logCommand(String message) {
        log(DebugCategory.COMMAND, message);
    }

    /**
     * Loggt eine Wager-bezogene Nachricht (Level 1).
     */
    public void logWager(String message) {
        log(DebugCategory.WAGER, message);
    }

    /**
     * Loggt eine Arena-bezogene Nachricht (Level 2).
     */
    public void logArena(String message) {
        log(DebugCategory.ARENA, message);
    }

    /**
     * Loggt eine Equipment-bezogene Nachricht (Level 2).
     */
    public void logEquipment(String message) {
        log(DebugCategory.EQUIPMENT, message);
    }

    /**
     * Loggt eine Teleport-bezogene Nachricht (Level 2).
     */
    public void logTeleport(String message) {
        log(DebugCategory.TELEPORT, message);
    }

    /**
     * Loggt eine Stats-bezogene Nachricht (Level 2).
     */
    public void logStats(String message) {
        log(DebugCategory.STATS, message);
    }

    /**
     * Loggt eine Listener-bezogene Nachricht (Level 3).
     */
    public void logListener(String message) {
        log(DebugCategory.LISTENER, message);
    }

    /**
     * Loggt eine Task-bezogene Nachricht (Level 3).
     */
    public void logTask(String message) {
        log(DebugCategory.TASK, message);
    }

    /**
     * Loggt eine Economy-bezogene Nachricht (Level 3).
     */
    public void logEconomy(String message) {
        log(DebugCategory.ECONOMY, message);
    }

    /**
     * Loggt eine Permission-bezogene Nachricht (Level 3).
     */
    public void logPermission(String message) {
        log(DebugCategory.PERMISSION, message);
    }

    // ==================== Interne Hilfsmethoden ====================

    /**
     * Sendet eine Nachricht an alle berechtigten Empfänger.
     */
    private void sendToReceivers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canReceiveDebug(player)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Prüft ob ein Spieler Debug-Nachrichten empfangen darf.
     */
    private boolean canReceiveDebug(Player player) {
        // Manuell hinzugefügte Empfänger
        if (debugReceivers.contains(player.getUniqueId())) {
            return true;
        }
        // OP-Spieler
        if (player.isOp()) {
            return true;
        }
        // Spieler mit Debug-Berechtigung
        if (player.hasPermission("eventpvp.debug.receive")) {
            return true;
        }
        return false;
    }

    /**
     * Wandelt Farb-Codes (&x) in Minecraft-Farben um.
     */
    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Entfernt alle Farb-Codes aus einer Nachricht.
     */
    private String stripColor(String message) {
        return ChatColor.stripColor(colorize(message));
    }

    // ==================== Status-Informationen ====================

    /**
     * Gibt eine Status-Übersicht als Array zurück.
     */
    public String[] getStatusInfo() {
        return new String[] {
            "&7Debug-Status:",
            "&7  Level: " + (currentLevel == DebugLevel.OFF ? "&cAus" : "&a" + currentLevel.getDisplayName() + " (" + currentLevel.getLevel() + ")"),
            "&7  Ausgabe: &e" + outputMode.getDisplayName(),
            "&7  Empfänger: &e" + debugReceivers.size() + " manuell + OPs + Berechtigte",
            "&7",
            "&7Aktive Kategorien bei Level " + currentLevel.getLevel() + ":",
            getActiveCategoriesInfo()
        };
    }

    /**
     * Gibt Info über aktive Kategorien zurück.
     */
    private String getActiveCategoriesInfo() {
        if (currentLevel == DebugLevel.OFF) {
            return "&7  Keine (Debug ist aus)";
        }
        
        StringBuilder sb = new StringBuilder();
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.isActiveAt(currentLevel)) {
                sb.append(cat.getColorCode()).append(cat.getDisplayName()).append("&7, ");
            }
        }
        
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 6); // Entferne letztes ", "
        }
        return "&7  " + sb.toString();
    }
}
