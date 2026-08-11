package de.zfzfg.core.monitoring.debug;

import de.zfzfg.core.security.Permission;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Zentraler Debug-Manager für das Event-PVP-Plugin.
 *
 * Der Modus kennt genau drei Zustände (siehe {@link DebugLevel}): aus, normal
 * und ausführlich. Die Ausgabe geht immer in die Konsole; zusätzlich in den
 * Chat aller OPs und Spieler mit {@code eventpvp.debug.receive}.
 *
 * Der Zustand wird in der config.yml unter {@code settings.debug} gehalten und
 * überlebt damit einen Serverneustart.
 */
public class DebugManager {

    /** Chat-Prefix für Debug-Ausgaben (auch vom Debug-Command verwendet). */
    public static final String DEBUG_PREFIX = "&8[&bDEBUG&8]&r ";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final EventPlugin plugin;
    private final Logger logger;

    private DebugLevel currentLevel = DebugLevel.OFF;

    public DebugManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    // ==================== Konfiguration ====================

    /**
     * Liest die Debug-Stufe aus settings.debug. Beim Start und nach jedem
     * Reload aufzurufen.
     */
    public void loadFromConfig() {
        String raw = plugin.getCoreConfigManager().getDebugSetting();
        DebugLevel parsed = DebugLevel.parse(raw);

        if (parsed == null) {
            String note = "Unknown value for settings.debug: '" + raw + "' - allowed values are off, on, full. Debug remains off."; // i18n-ignore: config validation, runs before language bundle load
            logger.warning(note);
            parsed = DebugLevel.OFF;
        }

        this.currentLevel = parsed;

        if (parsed != DebugLevel.OFF) {
            logger.info("Debug mode active: " + parsed.getDisplayName()); // i18n-ignore: debug internal trace
        }
    }

    /**
     * Setzt die Debug-Stufe und schreibt sie in die config.yml zurück.
     */
    public void setLevel(DebugLevel level) {
        this.currentLevel = level;
        plugin.getCoreConfigManager().setDebugSetting(level.getConfigValue());
    }

    public DebugLevel getLevel() {
        return currentLevel;
    }

    /**
     * Prüft ob Debug überhaupt aktiv ist (normal oder ausführlich).
     */
    public boolean isEnabled() {
        return currentLevel != DebugLevel.OFF;
    }

    /**
     * Prüft ob die ausführliche Stufe aktiv ist.
     */
    public boolean isFull() {
        return currentLevel == DebugLevel.FULL;
    }

    // ==================== Logging ====================

    /**
     * Loggt eine Debug-Nachricht, sobald der Debug-Modus an ist.
     */
    public void log(String message) {
        emit(DebugLevel.BASIC, message);
    }

    /**
     * Loggt eine Nachricht nur in der ausführlichen Stufe.
     */
    public void logFull(String message) {
        emit(DebugLevel.FULL, message);
    }

    /**
     * Loggt einen abgefangenen Fehler. Der Stack-Trace (nur plugin-eigene
     * Frames) folgt in der ausführlichen Stufe.
     */
    public void logException(String message, Throwable throwable) {
        emit(DebugLevel.BASIC, message + " - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());

        if (isFull()) {
            for (StackTraceElement element : throwable.getStackTrace()) {
                if (element.getClassName().startsWith("de.zfzfg")) {
                    emit(DebugLevel.FULL, "  at " + element.toString());
                }
            }
        }
    }

    // ==================== Interne Hilfsmethoden ====================

    private void emit(DebugLevel requiredLevel, String message) {
        if (!currentLevel.isAtLeast(requiredLevel) || currentLevel == DebugLevel.OFF) {
            return;
        }

        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        logger.info("[" + timestamp + "] [DEBUG] " + stripColor(message));

        sendToReceivers(colorize(DEBUG_PREFIX + "&7" + message));
    }

    /**
     * Sendet eine Nachricht an alle berechtigten Empfänger.
     */
    private void sendToReceivers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || Permission.DEBUG_RECEIVE.check(player)) {
                player.sendMessage(message);
            }
        }
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String stripColor(String message) {
        return ChatColor.stripColor(colorize(message));
    }
}
