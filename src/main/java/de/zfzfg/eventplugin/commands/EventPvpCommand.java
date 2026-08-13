package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.util.ColorUtil;
import de.zfzfg.eventplugin.util.UpdateChecker;
import de.zfzfg.core.security.Permission;
import de.zfzfg.core.monitoring.debug.DebugLevel;
import de.zfzfg.core.monitoring.debug.DebugManager;
import de.zfzfg.core.web.WebAuthManager;
import de.zfzfg.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventPvpCommand implements CommandExecutor, TabCompleter {

    private final EventPlugin plugin;
    private static final String DEBUG_PREFIX = DebugManager.DEBUG_PREFIX;

    /** Bereits gemeldete fehlende Message-Keys, damit die Konsole nicht zuläuft. */
    private static final Set<String> MISSING_KEYS_LOGGED = ConcurrentHashMap.newKeySet();

    public EventPvpCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    // Hilfsmethode für Debug-Nachrichten
    private String getDebugMsg(String key) {
        if (key.startsWith("help-") || key.startsWith("level-")) {
            String subKey = key.replace("help-", "");
            String val = plugin.getCoreConfigManager().getMessages()
                .getString("messages.debug.help." + subKey, null);
            if (val != null) return val;
        }
        String msgVal = plugin.getCoreConfigManager().getMessages()
            .getString("messages.debug.messages." + key, null);
        if (msgVal != null) return msgVal;

        String sysVal = plugin.getCoreConfigManager().getMessages()
            .getString("messages.system." + key, null);
        if (sysVal != null) return sysVal;

        String debugVal = plugin.getCoreConfigManager().getMessages()
            .getString("messages.debug." + key, null);
        if (debugVal != null) return debugVal;

        // Früher wurde hier der Key selbst zurückgegeben. Das sah im Chat wie
        // eine echte Nachricht aus (z.B. die Zeile "status-header" als
        // Überschrift), statt den fehlenden Key sichtbar zu machen.
        warnMissingKey("messages.debug." + key);
        return "&c[missing: " + key + "]";
    }

    private String getDebugMsg(String key, String placeholder, String value) {
        String msg = getDebugMsg(key);
        String val = value != null ? value : "";
        String raw = placeholder != null ? placeholder.replaceAll("^[{%]+|[%}]+$", "") : "";
        if (!raw.isEmpty()) {
            msg = msg.replace("{" + raw + "}", val)
                     .replace("%" + raw + "%", val);
        }
        return msg;
    }

    /**
     * Übersetzte Anzeige für einen {@link DebugLevel}. Die Konstanten selbst
     * tragen nur einen Key, damit der angezeigte Text der eingestellten
     * Sprache folgt und nicht der Sprache, in der die Enum-Datei geschrieben
     * wurde.
     *
     * @param translationKey Key unterhalb von messages.debug.enums.
     * @param fallback       sprachunabhängiger Name, falls der Key fehlt
     */
    private String getDebugEnum(String translationKey, String fallback) {
        String val = plugin.getCoreConfigManager().getMessages()
            .getString("messages.debug.enums." + translationKey, null);
        if (val != null) return val;
        warnMissingKey("messages.debug.enums." + translationKey);
        return fallback;
    }

    /** Meldet jeden fehlenden Key genau einmal in der Konsole. */
    private void warnMissingKey(String path) {
        if (MISSING_KEYS_LOGGED.add(path)) {
            plugin.getLogger().warning("Missing message key: " + path + " (check messages_*.yml)"); // i18n-ignore: i18n system warning
        }
    }
    
    // Hilfsmethode für Help-Nachrichten
    private String getHelpMsg(String key) {
        String val = plugin.getCoreConfigManager().getMessages()
            .getString("messages.command-help.eventpvp." + key, null);
        if (val != null) return val;
        val = plugin.getCoreConfigManager().getMessages()
            .getString("messages.help.eventpvp." + key, null);
        if (val != null) return val;
        warnMissingKey("messages.help.eventpvp." + key);
        return "&c[missing: " + key + "]";
    }
    
    // Hilfsmethode für General-Nachrichten
    private String getGeneralMsg(String key) {
        String val = plugin.getCoreConfigManager().getMessages()
            .getString("messages.general." + key, null);
        if (val != null) return val;
        warnMissingKey("messages.general." + key);
        return "&c[missing: " + key + "]";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        
        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "version":
                return handleVersion(sender);
            case "debug":
                return handleDebug(sender, label, Arrays.copyOfRange(args, 1, args.length));
            case "webtoken":
            case "wt":
                return handleWebToken(sender);
            case "rescue":
                return handleRescue(sender, label, Arrays.copyOfRange(args, 1, args.length));
            default:
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + 
                    getGeneralMsg("unknown-command").replace("{command}", sub)));
                showHelp(sender, label);
                return true;
        }
    }

    private String getHelpMsg(String key, String placeholder, String value) {
        String msg = getHelpMsg(key);
        String val = value != null ? value : "";
        String raw = placeholder != null ? placeholder.replaceAll("^[{%]+|[%}]+$", "") : "";
        if (!raw.isEmpty()) {
            msg = msg.replace("{" + raw + "}", val)
                     .replace("%" + raw + "%", val);
        }
        return msg;
    }

    private void showHelp(CommandSender sender, String label) {
        String webtokenHelp = getWebtokenMsg("help-description");
        sender.sendMessage(ColorUtil.color(getHelpMsg("header")));
        sender.sendMessage(ColorUtil.color(getHelpMsg("reload", "label", label)));
        sender.sendMessage(ColorUtil.color(getHelpMsg("version", "label", label)));
        sender.sendMessage(ColorUtil.color(getHelpMsg("debug", "label", label)));
        sender.sendMessage(ColorUtil.color(getHelpMsg("rescue", "label", label)));
        sender.sendMessage(ColorUtil.color(webtokenHelp));
        sender.sendMessage("");
    }

    private boolean handleReload(CommandSender sender) {
        if (!Permission.EVENTPVP_ADMIN.check(sender)) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("general.no-permission")));
            return true;
        }
        plugin.getConfigurationService().reloadAll();
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().getMessage("reload-success")));
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        if (!Permission.EVENTPVP_ADMIN.check(sender)) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("general.no-permission")));
            return true;
        }
        
        sender.sendMessage(ColorUtil.color("&8&m                                                &r"));
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("title")));
        sender.sendMessage(ColorUtil.color("&8&m                                                &r"));
        sender.sendMessage("");
        
        // Current version
        String currentVersion = plugin.getDescription().getVersion();
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("current", "version", currentVersion)));
        
        final UpdateChecker checker = plugin.getUpdateChecker();

        // Ist der Update-Check abgeschaltet, wird auch nichts abgerufen --
        // manche Betreiber wollen bewusst keinen ausgehenden HTTP-Verkehr.
        if (!plugin.getConfigManager().isUpdateCheckEnabled() || checker == null) {
            sender.sendMessage("");
            sender.sendMessage(ColorUtil.color("&8&m                                                &r"));
            return true;
        }

        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("checking")));

        // Ergebnis anzeigen, sobald der asynchrone Abruf fertig ist -- der
        // Callback laeuft im Main-Thread. Keine geratene Wartezeit.
        checker.checkForUpdates(() -> {
            if (checker.hasChecked()) {
                if (checker.isUpdateAvailable()) {
                    sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("update-available")));
                    sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("latest", "version", checker.getLatestVersion())));
                    sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("download")));
                } else {
                    sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("up-to-date")));
                }
            } else {
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("check-failed")));
            }
            sender.sendMessage("");
            sender.sendMessage(ColorUtil.color("&8&m                                                &r"));
        });

        return true;
    }
    
    // ==================== WebToken Subcommand ====================
    
    private String getWebtokenMsg(String key) {
        String val = plugin.getCoreConfigManager().getMessages()
            .getString("messages.webtoken." + key, null);
        if (val != null) return val;
        warnMissingKey("messages.webtoken." + key);
        return "&c[missing: " + key + "]";
    }
    
    private String getWebtokenMsg(String key, String placeholder, String value) {
        String msg = getWebtokenMsg(key);
        String val = value != null ? value : "";
        String raw = placeholder != null ? placeholder.replaceAll("^[{%]+|[%}]+$", "") : "";
        if (!raw.isEmpty()) {
            msg = msg.replace("{" + raw + "}", val)
                     .replace("%" + raw + "%", val);
        }
        return msg;
    }
    
    private boolean handleWebToken(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color(getWebtokenMsg("player-only")));
            return true;
        }
        
        // Permission-Check
        if (!player.hasPermission("eventpvp.admin.web") && !player.isOp()) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + getWebtokenMsg("no-permission")));
            return true;
        }
        
        WebAuthManager authManager = plugin.getWebAuthManager();
        if (authManager == null) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + getWebtokenMsg("not-enabled")));
            return true;
        }
        
        String token = authManager.generateToken(player);
        if (token == null) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + getWebtokenMsg("generation-failed")));
            return true;
        }
        
        // Formatierte Nachricht senden
        player.sendMessage("");
        player.sendMessage(ColorUtil.color("&8&m                                                &r"));
        player.sendMessage(ColorUtil.color(getWebtokenMsg("header")));
        player.sendMessage("");
        player.sendMessage(ColorUtil.color(getWebtokenMsg("your-token")));
        
        // Klickbarer Token
        Component tokenComponent = Text.of("  &a&l➤ " + token + " " + getWebtokenMsg("click-to-copy"))
            .clickEvent(ClickEvent.copyToClipboard(token))
            .hoverEvent(HoverEvent.showText(Text.of(getWebtokenMsg("hover-copy"))));
        player.sendMessage(tokenComponent);
        
        player.sendMessage("");
        player.sendMessage(ColorUtil.color(getWebtokenMsg("valid-for", "minutes", "10")));
        player.sendMessage(ColorUtil.color(getWebtokenMsg("single-use")));
        player.sendMessage("");
        
        // Web-URL (aus Konfiguration)
        String url = plugin.getWebPublicUrl();
        
        Component urlComponent = Text.link(
            "  &b&l➤ " + url + " " + getWebtokenMsg("click-to-open"),
            url,
            getWebtokenMsg("hover-open")
        );
        player.sendMessage(urlComponent);
        
        player.sendMessage(ColorUtil.color("&8&m                                                &r"));
        player.sendMessage("");
        
        return true;
    }

    // ==================== Rescue Subcommand ====================

    /**
     * Werkzeug fuer haengengebliebene Inventar-Sitzungen und Rueckkehr-Positionen.
     *
     * <p>Ersetzt das in mehreren Sprachdateien genannte {@code /pvp invdebug}, das nie
     * existiert hat - Admins wurden dort auf einen Befehl geschickt, den es nicht gab.</p>
     */
    private boolean handleRescue(CommandSender sender, String label, String[] args) {
        if (!Permission.EVENTPVP_ADMIN.check(sender)) {
            sender.sendMessage(ColorUtil.color(getGeneralMsg("no-permission")));
            return true;
        }

        if (args.length == 0) {
            showRescueHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
            case "liste":
                showRescueList(sender);
                break;
            case "clean":
            case "cleanup":
                cleanRescueEntries(sender);
                break;
            case "help":
            case "hilfe":
            case "?":
                showRescueHelp(sender, label);
                break;
            default:
                rescuePlayer(sender, args[0]);
                break;
        }
        return true;
    }

    private void showRescueHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtil.color(getRescueMsg("header")));
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getRescueMsg("help-list").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getRescueMsg("help-player").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getRescueMsg("help-clean").replace("{label}", label)));
        sender.sendMessage("");
    }

    /** Offene Inventar-Sitzungen und hinterlegte Rueckkehr-Positionen nebeneinander. */
    private void showRescueList(CommandSender sender) {
        sender.sendMessage(ColorUtil.color(getRescueMsg("header")));
        sender.sendMessage("");

        java.util.Collection<de.zfzfg.core.inventory.guard.GuardEntry> sessions =
                plugin.getInventoryGuard().openSessions();
        java.util.Collection<de.zfzfg.core.location.StoredReturn> returns =
                plugin.getReturnLocations().all();

        if (sessions.isEmpty() && returns.isEmpty()) {
            sender.sendMessage(ColorUtil.color(getRescueMsg("list-empty")));
            sender.sendMessage("");
            return;
        }

        if (!sessions.isEmpty()) {
            sender.sendMessage(ColorUtil.color(getRescueMsg("list-sessions")
                    .replace("{count}", String.valueOf(sessions.size()))));
            for (de.zfzfg.core.inventory.guard.GuardEntry entry : sessions) {
                sender.sendMessage(ColorUtil.color(getRescueMsg("list-session-entry")
                        .replace("{player}", describePlayer(entry.playerId()))
                        .replace("{phase}", entry.phase().name())
                        .replace("{context}", entry.context().name())
                        .replace("{age}", formatAge(entry.openedAt()))));
            }
        }

        if (!returns.isEmpty()) {
            sender.sendMessage(ColorUtil.color(getRescueMsg("list-returns")
                    .replace("{count}", String.valueOf(returns.size()))));
            for (de.zfzfg.core.location.StoredReturn entry : returns) {
                sender.sendMessage(ColorUtil.color(getRescueMsg("list-return-entry")
                        .replace("{player}", describePlayer(entry.playerId()))
                        .replace("{world}", entry.worldName())
                        .replace("{age}", formatAge(entry.savedAt()))));
            }
        }
        sender.sendMessage("");
    }

    /** Holt einen Spieler zurueck und stellt sein Inventar wieder her. */
    private void rescuePlayer(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(ColorUtil.color(getRescueMsg("player-offline")
                    .replace("{player}", playerName)));
            return;
        }

        java.util.UUID playerId = target.getUniqueId();
        boolean didSomething = false;

        // Inventar zuerst - danach steht der Spieler wenigstens mit seinen Sachen da.
        if (plugin.getInventoryGuard().hasOpenSession(playerId)) {
            plugin.getInventorySessions().finish(playerId, outcome ->
                    sender.sendMessage(ColorUtil.color(getRescueMsg("inventory-result")
                            .replace("{player}", target.getName())
                            .replace("{outcome}", outcome.name()))));
            didSomething = true;
        }

        // Danach der Rueckweg. Bewusst ohne die isStranded-Pruefung: ein Admin, der diesen
        // Befehl tippt, weiss besser als die Heuristik, dass der Spieler hier weg soll.
        de.zfzfg.core.location.StrandedPlayerListener rescuer = plugin.getStrandedPlayers();
        if (rescuer != null && rescuer.rescue(target)) {
            sender.sendMessage(ColorUtil.color(getRescueMsg("teleport-done")
                    .replace("{player}", target.getName())));
            didSomething = true;
        }

        if (!didSomething) {
            sender.sendMessage(ColorUtil.color(getRescueMsg("nothing-to-do")
                    .replace("{player}", target.getName())));
        }
    }

    /**
     * Verwirft verwaiste Eintraege.
     *
     * <p>Nur solche ohne Backup: bei denen ist nichts mehr wiederherzustellen, sie halten
     * das Journal nur unuebersichtlich. Eintraege mit Backup bleiben stehen, auch alte -
     * dort waere das Verwerfen der eigentliche Datenverlust.</p>
     */
    private void cleanRescueEntries(CommandSender sender) {
        int removed = 0;
        for (de.zfzfg.core.inventory.guard.GuardEntry entry
                : plugin.getInventoryGuard().openSessions()) {
            if (!entry.hasBackup()
                    && entry.phase() == de.zfzfg.core.inventory.guard.GuardPhase.ORPHANED) {
                plugin.getInventoryGuard().close(entry.playerId());
                removed++;
            }
        }
        sender.sendMessage(ColorUtil.color(getRescueMsg("clean-done")
                .replace("{count}", String.valueOf(removed))));
    }

    /** Name, wenn bekannt - sonst die UUID, damit der Eintrag zuordenbar bleibt. */
    private String describePlayer(java.util.UUID playerId) {
        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null ? name : playerId.toString();
    }

    private String formatAge(long since) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - since) / 60_000L);
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        return hours < 24 ? hours + "h" : (hours / 24) + "d";
    }

    private String getRescueMsg(String key) {
        String val = plugin.getCoreConfigManager().getMessages()
            .getString("messages.rescue." + key, null);
        if (val != null) return val;
        warnMissingKey("messages.rescue." + key);
        return "&c[missing: " + key + "]";
    }

    // ==================== Debug Subcommand ====================

    private boolean handleDebug(CommandSender sender, String label, String[] args) {
        if (!Permission.DEBUG.check(sender)) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getGeneralMsg("no-permission")));
            return true;
        }

        DebugManager debugManager = plugin.getDebugManager();

        if (args.length == 0) {
            showDebugStatus(sender, debugManager, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on":
            case "an":
            case "enable":
            case "aktivieren":
                handleDebugEnable(sender, debugManager, args);
                break;

            case "off":
            case "aus":
            case "disable":
            case "deaktivieren":
                debugManager.setLevel(DebugLevel.OFF);
                sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("disabled")));
                break;

            case "status":
            case "info":
                showDebugStatus(sender, debugManager, label);
                break;

            case "help":
            case "hilfe":
            case "?":
                showDebugHelp(sender, label);
                break;

            default:
                sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("unknown-command").replace("{command}", args[0])));
                sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("use-help").replace("{label}", label)));
                break;
        }

        return true;
    }

    /**
     * /eventpvp debug on [full] -- ohne Zusatz die normale Stufe.
     */
    private void handleDebugEnable(CommandSender sender, DebugManager debugManager, String[] args) {
        DebugLevel level = DebugLevel.BASIC;

        if (args.length > 1 && DebugLevel.parse(args[1]) == DebugLevel.FULL) {
            level = DebugLevel.FULL;
        }

        debugManager.setLevel(level);
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("enabled")
            .replace("{level}", getDebugEnum(level.getTranslationKey(), level.getDisplayName()))));
    }

    private void showDebugStatus(CommandSender sender, DebugManager debugManager, String label) {
        sender.sendMessage(ColorUtil.color(getDebugMsg("status-header")));
        sender.sendMessage("");

        DebugLevel level = debugManager.getLevel();
        boolean enabled = level != DebugLevel.OFF;

        String statusText = enabled ? getDebugMsg("status-enabled") : getDebugMsg("status-disabled");
        sender.sendMessage(ColorUtil.color(getDebugMsg("status-label")
            .replace("{status}", (enabled ? "&a" : "&c") + statusText)));

        if (enabled) {
            sender.sendMessage(ColorUtil.color(getDebugMsg("level-label")
                .replace("{level}", getDebugEnum(level.getTranslationKey(), level.getDisplayName()))));
        }

        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getDebugMsg("use-debug-help").replace("{label}", label)));
    }

    private void showDebugHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-header")));
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getDebugMsg("help.status", "label", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help.on", "label", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help.on-full", "label", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help.off", "label", label)));
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getDebugMsg("level-overview")));
        sender.sendMessage("");
    }


    // ==================== Tab Complete ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            if (Permission.EVENTPVP_ADMIN.check(sender)) {
                completions.add("version");
            }
            if (Permission.DEBUG.check(sender)) {
                completions.add("debug");
            }
            if (sender.hasPermission("eventpvp.admin.web") || sender.isOp()) {
                completions.add("webtoken");
            }
            if (Permission.EVENTPVP_ADMIN.check(sender)) {
                completions.add("rescue");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rescue")
                && Permission.EVENTPVP_ADMIN.check(sender)) {
            completions.addAll(Arrays.asList("list", "clean", "help"));
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                completions.add(online.getName());
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("debug") && Permission.DEBUG.check(sender)) {
            if (args.length == 2) {
                completions.addAll(Arrays.asList("on", "off", "status", "help"));
            } else if (args.length == 3) {
                String debugSub = args[1].toLowerCase();

                if (debugSub.equals("on") || debugSub.equals("an")) {
                    completions.add("full");
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(lastArg))
            .collect(Collectors.toList());
    }
}