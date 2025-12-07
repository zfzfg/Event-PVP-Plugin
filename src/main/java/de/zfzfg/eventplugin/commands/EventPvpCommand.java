package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.util.ColorUtil;
import de.zfzfg.core.security.Permission;
import de.zfzfg.core.monitoring.debug.DebugCategory;
import de.zfzfg.core.monitoring.debug.DebugLevel;
import de.zfzfg.core.monitoring.debug.DebugManager;
import de.zfzfg.core.monitoring.debug.DebugOutput;
import de.zfzfg.core.web.WebAuthManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EventPvpCommand implements CommandExecutor, TabCompleter {

    private final EventPlugin plugin;
    private static final String DEBUG_PREFIX = "&8[&bDEBUG&8]&r ";

    public EventPvpCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    // Hilfsmethode für Debug-Nachrichten
    private String getDebugMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.debug." + key, key);
    }
    
    private String getDebugMsg(String key, String placeholder, String value) {
        String message = plugin.getCoreConfigManager().getMessages()
            .getString("messages.debug." + key, key);
        return message.replace("{" + placeholder + "}", value);
    }
    
    // Hilfsmethode für Help-Nachrichten
    private String getHelpMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.help.eventpvp." + key, key);
    }
    
    // Hilfsmethode für General-Nachrichten
    private String getGeneralMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.general." + key, key);
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
            case "debug":
                return handleDebug(sender, label, Arrays.copyOfRange(args, 1, args.length));
            case "webtoken":
            case "wt":
                return handleWebToken(sender);
            default:
                sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + 
                    getGeneralMsg("unknown-command").replace("{command}", sub)));
                showHelp(sender, label);
                return true;
        }
    }

    private void showHelp(CommandSender sender, String label) {
        String webtokenHelp = getWebtokenMsg("help-description");
        sender.sendMessage(ColorUtil.color(getHelpMsg("header")));
        sender.sendMessage(ColorUtil.color(getHelpMsg("reload").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getHelpMsg("debug").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(webtokenHelp));
        sender.sendMessage("");
    }

    private boolean handleReload(CommandSender sender) {
        if (!Permission.EVENTPVP_ADMIN.check(sender)) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("general.no-permission")));
            return true;
        }
        plugin.getConfigurationService().reloadAll();
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &aAlle Konfigurationen neu geladen."));
        return true;
    }
    
    // ==================== WebToken Subcommand ====================
    
    private String getWebtokenMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.webtoken." + key, key);
    }
    
    private String getWebtokenMsg(String key, String placeholder, String value) {
        String message = plugin.getCoreConfigManager().getMessages()
            .getString("messages.webtoken." + key, key);
        return message.replace("{" + placeholder + "}", value);
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
        TextComponent tokenComponent = new TextComponent(ColorUtil.color("  &a&l➤ " + token + " " + getWebtokenMsg("click-to-copy")));
        tokenComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, token));
        tokenComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new Text(ColorUtil.color(getWebtokenMsg("hover-copy")))));
        player.spigot().sendMessage(tokenComponent);
        
        player.sendMessage("");
        player.sendMessage(ColorUtil.color(getWebtokenMsg("valid-for", "minutes", "10")));
        player.sendMessage(ColorUtil.color(getWebtokenMsg("single-use")));
        player.sendMessage("");
        
        // Web-URL (aus Konfiguration)
        String url = plugin.getWebPublicUrl();
        
        TextComponent urlComponent = new TextComponent(ColorUtil.color("  &b&l➤ " + url + " " + getWebtokenMsg("click-to-open")));
        urlComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        urlComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new Text(ColorUtil.color(getWebtokenMsg("hover-open")))));
        player.spigot().sendMessage(urlComponent);
        
        player.sendMessage(ColorUtil.color("&8&m                                                &r"));
        player.sendMessage("");
        
        return true;
    }

    // ==================== Debug Subcommand ====================

    private boolean handleDebug(CommandSender sender, String label, String[] args) {
        // Permission-Check für Debug
        if (!sender.hasPermission("eventpvp.debug")) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("no-permission")));
            return true;
        }

        DebugManager debugManager = plugin.getDebugManager();

        if (args.length == 0) {
            showDebugStatus(sender, debugManager);
            return true;
        }

        String debugSub = args[0].toLowerCase();

        switch (debugSub) {
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
                handleDebugDisable(sender, debugManager);
                break;
                
            case "level":
            case "stufe":
                handleDebugLevel(sender, debugManager, label, args);
                break;
                
            case "output":
            case "ausgabe":
                handleDebugOutput(sender, debugManager, label, args);
                break;
                
            case "test":
                handleDebugTest(sender, debugManager, args);
                break;
                
            case "subscribe":
            case "sub":
            case "empfangen":
                handleDebugSubscribe(sender, debugManager, true);
                break;
                
            case "unsubscribe":
            case "unsub":
            case "stopp":
                handleDebugSubscribe(sender, debugManager, false);
                break;
                
            case "categories":
            case "kategorien":
            case "cats":
                showDebugCategories(sender);
                break;
                
            case "status":
            case "info":
                showDebugStatus(sender, debugManager);
                break;
                
            case "help":
            case "hilfe":
            case "?":
                showDebugHelp(sender, label);
                break;
                
            default:
                // Versuche als Level zu interpretieren
                DebugLevel parsed = DebugLevel.parse(debugSub);
                if (parsed != null) {
                    debugManager.setLevel(parsed);
                    sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("level-set")
                        .replace("{level}", parsed.getDisplayName())
                        .replace("{number}", String.valueOf(parsed.getLevel()))));
                } else {
                    sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("unknown-command").replace("{command}", debugSub)));
                    sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("use-help").replace("{label}", label)));
                }
                break;
        }

        return true;
    }

    private void handleDebugEnable(CommandSender sender, DebugManager debugManager, String[] args) {
        DebugLevel level = DebugLevel.LEVEL_1;
        
        if (args.length > 1) {
            DebugLevel parsed = DebugLevel.parse(args[1]);
            if (parsed != null && parsed != DebugLevel.OFF) {
                level = parsed;
            }
        }
        
        debugManager.setLevel(level);
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("enabled")
            .replace("{level}", level.getDisplayName())
            .replace("{number}", String.valueOf(level.getLevel()))));
        
        // Info über aktive Kategorien
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("active-categories")));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.isActiveAt(level)) {
                sender.sendMessage(ColorUtil.color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &7(ab Level " + cat.getMinLevel().getLevel() + ")"));
            }
        }
    }

    private void handleDebugDisable(CommandSender sender, DebugManager debugManager) {
        debugManager.setLevel(DebugLevel.OFF);
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("disabled")));
    }

    private void handleDebugLevel(CommandSender sender, DebugManager debugManager, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("current-level")
                .replace("{level}", debugManager.getLevel().getDisplayName())
                .replace("{number}", String.valueOf(debugManager.getLevel().getLevel()))));
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("use-level-change").replace("{label}", label)));
            return;
        }
        
        DebugLevel level = DebugLevel.parse(args[1]);
        if (level == null) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("invalid-level").replace("{level}", args[1])));
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("valid-levels")));
            return;
        }
        
        debugManager.setLevel(level);
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("level-set")
            .replace("{level}", level.getDisplayName())
            .replace("{number}", String.valueOf(level.getLevel()))));
    }

    private void handleDebugOutput(CommandSender sender, DebugManager debugManager, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("current-output")
                .replace("{mode}", debugManager.getOutputMode().getDisplayName())));
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("use-output-change").replace("{label}", label)));
            return;
        }
        
        DebugOutput output = DebugOutput.parse(args[1]);
        if (output == null) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("invalid-output").replace("{mode}", args[1])));
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("valid-outputs")));
            return;
        }
        
        debugManager.setOutputMode(output);
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("output-set").replace("{mode}", output.getDisplayName())));
    }

    private void handleDebugTest(CommandSender sender, DebugManager debugManager, String[] args) {
        if (!debugManager.isEnabled()) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("not-enabled")));
            return;
        }
        
        DebugCategory category = DebugCategory.SYSTEM;
        if (args.length > 1) {
            DebugCategory parsed = DebugCategory.parse(args[1]);
            if (parsed != null) {
                category = parsed;
            }
        }
        
        sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("test-message").replace("{category}", category.getDisplayName())));
        debugManager.log(category, DebugLevel.LEVEL_1, "Test-Nachricht von " + sender.getName());
    }

    private void handleDebugSubscribe(CommandSender sender, DebugManager debugManager, boolean subscribe) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("player-only")));
            return;
        }
        
        Player player = (Player) sender;
        if (subscribe) {
            debugManager.addReceiver(player.getUniqueId());
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("subscribed")));
        } else {
            debugManager.removeReceiver(player.getUniqueId());
            sender.sendMessage(ColorUtil.color(DEBUG_PREFIX + getDebugMsg("unsubscribed")));
        }
    }

    private void showDebugCategories(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&8&m                    &r &bDebug-Kategorien &8&m                    "));
        sender.sendMessage("");
        
        sender.sendMessage(ColorUtil.color(getDebugMsg("level1-header")));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.getMinLevel() == DebugLevel.LEVEL_1) {
                sender.sendMessage(ColorUtil.color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &8- " + cat.name()));
            }
        }
        
        sender.sendMessage(ColorUtil.color(getDebugMsg("level2-header")));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.getMinLevel() == DebugLevel.LEVEL_2) {
                sender.sendMessage(ColorUtil.color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &8- " + cat.name()));
            }
        }
        
        sender.sendMessage(ColorUtil.color(getDebugMsg("level3-header")));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.getMinLevel() == DebugLevel.LEVEL_3) {
                sender.sendMessage(ColorUtil.color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &8- " + cat.name()));
            }
        }
        
        sender.sendMessage("");
    }

    private void showDebugStatus(CommandSender sender, DebugManager debugManager) {
        sender.sendMessage(ColorUtil.color("&8&m                    &r &bDebug-Status &8&m                    "));
        sender.sendMessage("");
        
        DebugLevel level = debugManager.getLevel();
        DebugOutput output = debugManager.getOutputMode();
        
        String statusText = level == DebugLevel.OFF ? getDebugMsg("status-disabled") : getDebugMsg("status-enabled");
        sender.sendMessage(ColorUtil.color(getDebugMsg("status-label").replace("{status}", 
            (level == DebugLevel.OFF ? "&c" : "&a") + statusText)));
        
        if (level != DebugLevel.OFF) {
            sender.sendMessage(ColorUtil.color(getDebugMsg("level-label")
                .replace("{level}", level.getDisplayName())
                .replace("{number}", String.valueOf(level.getLevel()))));
            sender.sendMessage(ColorUtil.color("&7Ausgabe: &e" + output.getDisplayName()));
            
            sender.sendMessage(ColorUtil.color(getDebugMsg("active-categories")));
            StringBuilder cats = new StringBuilder();
            for (DebugCategory cat : DebugCategory.values()) {
                if (cat.isActiveAt(level)) {
                    cats.append(cat.getColorCode()).append(cat.getDisplayName()).append("&7, ");
                }
            }
            if (cats.length() > 0) {
                cats.setLength(cats.length() - 6);
            }
            sender.sendMessage(ColorUtil.color("  " + cats.toString()));
        }
        
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getDebugMsg("use-debug-help")));
    }

    private void showDebugHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtil.color("&8&m                    &r &bDebug-Befehle &8&m                    "));
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-status").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-on").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-off").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-level").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-output").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-test").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-subscribe").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-unsubscribe").replace("{label}", label)));
        sender.sendMessage(ColorUtil.color(getDebugMsg("help-categories").replace("{label}", label)));
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.color(getDebugMsg("level-overview")));
        sender.sendMessage(ColorUtil.color(getDebugMsg("level-values")));
        sender.sendMessage("");
    }

    // ==================== Tab Complete ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            if (sender.hasPermission("eventpvp.debug")) {
                completions.add("debug");
            }
            if (sender.hasPermission("eventpvp.admin.web") || sender.isOp()) {
                completions.add("webtoken");
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("debug") && sender.hasPermission("eventpvp.debug")) {
            if (args.length == 2) {
                completions.addAll(Arrays.asList(
                    "on", "off", "level", "output", "test", 
                    "subscribe", "unsubscribe", "categories", 
                    "status", "help", "0", "1", "2", "3"
                ));
            } else if (args.length == 3) {
                String debugSub = args[1].toLowerCase();
                
                switch (debugSub) {
                    case "on":
                    case "level":
                    case "stufe":
                        completions.addAll(Arrays.asList("0", "1", "2", "3", "off", "level_1", "level_2", "level_3"));
                        break;
                        
                    case "output":
                    case "ausgabe":
                        completions.addAll(Arrays.asList("console", "chat", "both", "konsole", "beides"));
                        break;
                        
                    case "test":
                        completions.addAll(
                            Arrays.stream(DebugCategory.values())
                                .map(c -> c.name().toLowerCase())
                                .collect(Collectors.toList())
                        );
                        break;
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(lastArg))
            .collect(Collectors.toList());
    }
}