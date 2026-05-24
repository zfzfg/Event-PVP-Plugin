package de.zfzfg.core.commands;

import de.zfzfg.core.monitoring.debug.DebugCategory;
import de.zfzfg.core.monitoring.debug.DebugLevel;
import de.zfzfg.core.monitoring.debug.DebugManager;
import de.zfzfg.core.monitoring.debug.DebugOutput;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command zum Verwalten des Debug-Modus.
 * 
 * Verwendung:
 * - /debug                    - Zeigt aktuellen Status
 * - /debug on [level]         - Aktiviert Debug (Standard: Level 1)
 * - /debug off                - Deaktiviert Debug
 * - /debug level <1|2|3>      - Setzt die Debug-Stufe
 * - /debug output <console|chat|both> - Setzt den Ausgabe-Modus
 * - /debug test [category]    - Sendet eine Test-Nachricht
 * - /debug subscribe          - Aktiviert Chat-Empfang für den Spieler
 * - /debug unsubscribe        - Deaktiviert Chat-Empfang für den Spieler
 * - /debug categories         - Zeigt alle Kategorien und ihre Level
 */
public class DebugCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&bDEBUG&8]&r ";
    private final DebugManager debugManager;

    public DebugCommand(DebugManager debugManager) {
        this.debugManager = debugManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission-Check
        if (!sender.hasPermission("eventpvp.debug")) {
            sender.sendMessage(color(PREFIX + "&cDu hast keine Berechtigung für diesen Befehl."));
            return true;
        }

        if (args.length == 0) {
            showStatus(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "on":
            case "an":
            case "enable":
            case "aktivieren":
                handleEnable(sender, args);
                break;
                
            case "off":
            case "aus":
            case "disable":
            case "deaktivieren":
                handleDisable(sender);
                break;
                
            case "level":
            case "stufe":
                handleLevel(sender, args);
                break;
                
            case "output":
            case "ausgabe":
                handleOutput(sender, args);
                break;
                
            case "test":
                handleTest(sender, args);
                break;
                
            case "subscribe":
            case "sub":
            case "empfangen":
                handleSubscribe(sender, true);
                break;
                
            case "unsubscribe":
            case "unsub":
            case "stopp":
                handleSubscribe(sender, false);
                break;
                
            case "categories":
            case "kategorien":
            case "cats":
                showCategories(sender);
                break;
                
            case "status":
            case "info":
                showStatus(sender);
                break;
                
            case "help":
            case "hilfe":
            case "?":
                showHelp(sender);
                break;
                
            default:
                // Versuche als Level zu interpretieren
                DebugLevel parsed = DebugLevel.parse(sub);
                if (parsed != null) {
                    debugManager.setLevel(parsed);
                    sender.sendMessage(color(PREFIX + "&aDebug-Level gesetzt auf: &e" + parsed.getDisplayName() + " (" + parsed.getLevel() + ")"));
                } else {
                    sender.sendMessage(color(PREFIX + "&cUnbekannter Befehl: &e" + sub));
                    sender.sendMessage(color(PREFIX + "&7Nutze &e/" + label + " help &7für Hilfe."));
                }
                break;
        }

        return true;
    }

    private void handleEnable(CommandSender sender, String[] args) {
        DebugLevel level = DebugLevel.LEVEL_1;
        
        if (args.length > 1) {
            DebugLevel parsed = DebugLevel.parse(args[1]);
            if (parsed != null && parsed != DebugLevel.OFF) {
                level = parsed;
            }
        }
        
        debugManager.setLevel(level);
        sender.sendMessage(color(PREFIX + "&aDebug aktiviert auf Stufe &e" + level.getDisplayName() + " (" + level.getLevel() + ")"));
        
        // Info über aktive Kategorien
        sender.sendMessage(color(PREFIX + "&7Aktive Kategorien:"));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.isActiveAt(level)) {
                sender.sendMessage(color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &7(ab Level " + cat.getMinLevel().getLevel() + ")"));
            }
        }
    }

    private void handleDisable(CommandSender sender) {
        debugManager.setLevel(DebugLevel.OFF);
        sender.sendMessage(color(PREFIX + "&cDebug deaktiviert."));
    }

    private void handleLevel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(PREFIX + "&7Aktuelles Level: &e" + debugManager.getLevel().getDisplayName() + " (" + debugManager.getLevel().getLevel() + ")"));
            sender.sendMessage(color(PREFIX + "&7Nutze &e/debug level <0|1|2|3> &7zum Ändern."));
            return;
        }
        
        DebugLevel level = DebugLevel.parse(args[1]);
        if (level == null) {
            sender.sendMessage(color(PREFIX + "&cUngültiges Level: &e" + args[1]));
            sender.sendMessage(color(PREFIX + "&7Gültige Werte: &e0 (Aus), 1 (Basis), 2 (Erweitert), 3 (Vollständig)"));
            return;
        }
        
        debugManager.setLevel(level);
        sender.sendMessage(color(PREFIX + "&aDebug-Level gesetzt auf: &e" + level.getDisplayName() + " (" + level.getLevel() + ")"));
    }

    private void handleOutput(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(PREFIX + "&7Aktuelle Ausgabe: &e" + debugManager.getOutputMode().getDisplayName()));
            sender.sendMessage(color(PREFIX + "&7Nutze &e/debug output <console|chat|both> &7zum Ändern."));
            return;
        }
        
        DebugOutput output = DebugOutput.parse(args[1]);
        if (output == null) {
            sender.sendMessage(color(PREFIX + "&cUngültiger Ausgabe-Modus: &e" + args[1]));
            sender.sendMessage(color(PREFIX + "&7Gültige Werte: &econsole, chat, both"));
            return;
        }
        
        debugManager.setOutputMode(output);
        sender.sendMessage(color(PREFIX + "&aAusgabe-Modus gesetzt auf: &e" + output.getDisplayName()));
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!debugManager.isEnabled()) {
            sender.sendMessage(color(PREFIX + "&cDebug ist deaktiviert. Aktiviere mit &e/debug on"));
            return;
        }
        
        DebugCategory category = DebugCategory.SYSTEM;
        if (args.length > 1) {
            DebugCategory parsed = DebugCategory.parse(args[1]);
            if (parsed != null) {
                category = parsed;
            }
        }
        
        sender.sendMessage(color(PREFIX + "&7Sende Test-Nachricht für Kategorie: &e" + category.getDisplayName()));
        debugManager.log(category, DebugLevel.LEVEL_1, "Test-Nachricht von " + sender.getName());
    }

    private void handleSubscribe(CommandSender sender, boolean subscribe) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(PREFIX + "&cDieser Befehl ist nur für Spieler."));
            return;
        }
        
        Player player = (Player) sender;
        if (subscribe) {
            debugManager.addReceiver(player.getUniqueId());
            sender.sendMessage(color(PREFIX + "&aDu erhältst jetzt Debug-Nachrichten im Chat."));
        } else {
            debugManager.removeReceiver(player.getUniqueId());
            sender.sendMessage(color(PREFIX + "&cDu erhältst keine Debug-Nachrichten mehr im Chat."));
        }
    }

    private void showCategories(CommandSender sender) {
        sender.sendMessage(color("&8&m                    &r &bDebug-Kategorien &8&m                    "));
        sender.sendMessage("");
        
        sender.sendMessage(color("&eStufe 1 (Basis):"));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.getMinLevel() == DebugLevel.LEVEL_1) {
                sender.sendMessage(color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &8- " + cat.name()));
            }
        }
        
        sender.sendMessage(color("&eStufe 2 (Erweitert):"));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.getMinLevel() == DebugLevel.LEVEL_2) {
                sender.sendMessage(color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &8- " + cat.name()));
            }
        }
        
        sender.sendMessage(color("&eStufe 3 (Vollständig):"));
        for (DebugCategory cat : DebugCategory.values()) {
            if (cat.getMinLevel() == DebugLevel.LEVEL_3) {
                sender.sendMessage(color("  " + cat.getColorCode() + "• " + cat.getDisplayName() + " &8- " + cat.name()));
            }
        }
        
        sender.sendMessage("");
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage(color("&8&m                    &r &bDebug-Status &8&m                    "));
        sender.sendMessage("");
        
        DebugLevel level = debugManager.getLevel();
        DebugOutput output = debugManager.getOutputMode();
        
        String statusColor = level == DebugLevel.OFF ? "&c" : "&a";
        sender.sendMessage(color("&7Status: " + statusColor + (level == DebugLevel.OFF ? "Deaktiviert" : "Aktiviert")));
        
        if (level != DebugLevel.OFF) {
            sender.sendMessage(color("&7Level: &e" + level.getDisplayName() + " &7(&e" + level.getLevel() + "&7)"));
            sender.sendMessage(color("&7Ausgabe: &e" + output.getDisplayName()));
            
            sender.sendMessage(color("&7Aktive Kategorien:"));
            StringBuilder cats = new StringBuilder();
            for (DebugCategory cat : DebugCategory.values()) {
                if (cat.isActiveAt(level)) {
                    cats.append(cat.getColorCode()).append(cat.getDisplayName()).append("&7, ");
                }
            }
            if (cats.length() > 0) {
                cats.setLength(cats.length() - 6);
            }
            sender.sendMessage(color("  " + cats.toString()));
        }
        
        sender.sendMessage("");
        sender.sendMessage(color("&7Nutze &e/debug help &7für alle Befehle."));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m                    &r &bDebug-Befehle &8&m                    "));
        sender.sendMessage("");
        sender.sendMessage(color("&e/debug &8- &7Zeigt den aktuellen Status"));
        sender.sendMessage(color("&e/debug on [level] &8- &7Aktiviert Debug (Standard: Level 1)"));
        sender.sendMessage(color("&e/debug off &8- &7Deaktiviert Debug"));
        sender.sendMessage(color("&e/debug level <0-3> &8- &7Setzt die Debug-Stufe"));
        sender.sendMessage(color("&e/debug output <mode> &8- &7Setzt Ausgabe (console/chat/both)"));
        sender.sendMessage(color("&e/debug test [category] &8- &7Sendet eine Test-Nachricht"));
        sender.sendMessage(color("&e/debug subscribe &8- &7Empfange Debug im Chat"));
        sender.sendMessage(color("&e/debug unsubscribe &8- &7Stoppe Chat-Empfang"));
        sender.sendMessage(color("&e/debug categories &8- &7Zeigt alle Kategorien"));
        sender.sendMessage("");
        sender.sendMessage(color("&7Level-Übersicht:"));
        sender.sendMessage(color("  &70 = Aus, &a1 = Basis, &e2 = Erweitert, &c3 = Vollständig"));
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("eventpvp.debug")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                "on", "off", "level", "output", "test", 
                "subscribe", "unsubscribe", "categories", 
                "status", "help", "0", "1", "2", "3"
            ));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            
            switch (sub) {
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

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(lastArg))
            .collect(Collectors.toList());
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
