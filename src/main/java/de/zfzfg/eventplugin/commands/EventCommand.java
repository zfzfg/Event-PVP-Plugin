package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.eventplugin.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import de.zfzfg.core.security.Permission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EventCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    
    public EventCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                sendHelp(sender);
                break;
                
            case "list":
                listEvents(sender);
                break;
                
            case "join":
                if (!(sender instanceof Player)) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.player-only"));
                    return true;
                }
                
                if (args.length < 2) {
                    // Improved usage: show correct order and list joinable events
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cNutze: /event join <event>");
                    List<EventSession> joinable = plugin.getEventManager().getActiveSessions().values().stream()
                            .filter(s -> s.getState() == EventSession.EventState.JOIN_PHASE)
                            .collect(Collectors.toList());
                    if (!joinable.isEmpty()) {
                        String list = joinable.stream().map(s -> s.getConfig().getCommand()).collect(Collectors.joining(", "));
                        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &7Beitreten möglich für: &a" + list));
                    } else {
                        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &cAktuell ist kein Event zur Teilnahme geöffnet."));
                    }
                    return true;
                }
                
                handleJoin((Player) sender, args[1]);
                break;
                
            case "leave":
                if (!(sender instanceof Player)) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.player-only"));
                    return true;
                }
                
                handleLeave((Player) sender);
                break;
                
            case "start":
                if (!Permission.EVENT_ADMIN.check(sender)) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                    return true;
                }
                
                if (args.length < 2) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cNutze: /event <eventname> start");
                    return true;
                }
                
                handleStart(sender, args[1]);
                break;
                
            case "stop":
                if (!Permission.EVENT_ADMIN.check(sender)) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                    return true;
                }
                
                if (args.length < 2) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cNutze: /event <eventname> stop");
                    return true;
                }
                
                handleStop(sender, args[1]);
                break;
                
            case "forcestart":
                if (!Permission.EVENT_ADMIN.check(sender)) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                    return true;
                }
                
                if (args.length < 2) {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cNutze: /event <eventname> forcestart");
                    return true;
                }
                
                handleForceStart(sender, args[1]);
                break;
                
            case "reload":
                if (!sender.hasPermission("eventplugin.admin")) {
                    sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("general.no-permission")));
                    return true;
                }
                
                handleReload(sender);
                break;
                
            default:
                // Prüfe ob es ein Event-Name ist
                EventConfig eventConfig = plugin.getConfigManager().getEventConfig(subCommand);
                if (eventConfig != null) {
                    if (args.length < 2) {
                        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + 
                            " &e" + eventConfig.getDisplayName()));
                        sender.sendMessage(ColorUtil.color(eventConfig.getDescription()));
                        sender.sendMessage(ColorUtil.color("&7Nutze: &e/event join " + subCommand));
                        return true;
                    }
                    
                    String action = args[1].toLowerCase();
                    if (action.equals("join")) {
                        if (sender instanceof Player) {
                            handleJoin((Player) sender, subCommand);
                        } else {
                            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.player-only"));
                        }
                    } else if (action.equals("start")) {
                        if (Permission.EVENT_ADMIN.check(sender)) {
                            handleStart(sender, subCommand);
                        } else {
                            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                        }
                    } else if (action.equals("stop")) {
                        if (Permission.EVENT_ADMIN.check(sender)) {
                            handleStop(sender, subCommand);
                        } else {
                            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                        }
                    } else if (action.equals("forcestart")) {
                        if (Permission.EVENT_ADMIN.check(sender)) {
                            handleForceStart(sender, subCommand);
                        } else {
                            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                        }
                    }
                } else {
                    de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.unknown-command"));
                }
                break;
        }
        
        return true;
    }
    
    private void handleJoin(Player player, String eventId) {
        EventConfig config = plugin.getConfigManager().getEventConfig(eventId);
        if (config == null) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getMessage("general.event-not-found")
                .replace("{event}", eventId));
            return;
        }
        
        // Prüfe ob Spieler bereits in einem Event ist
        Optional<EventSession> currentSession = plugin.getEventManager().getPlayerSession(player);
        if (currentSession.isPresent()) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getMessage("join.already-joined"));
            return;
        }
        
        // Hole Session, Beitritt nur möglich, wenn Join-Phase aktiv (keine Auto-Erstellung)
        Optional<EventSession> sessionOpt = plugin.getEventManager().getSession(eventId);
        if (!sessionOpt.isPresent()) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getPrefix() + " &cDieses Event ist derzeit nicht geöffnet.");
            return;
        }
        EventSession session = sessionOpt.get();
        
        // Beitritt nur in Join-Phase
        if (session.getState() != EventSession.EventState.JOIN_PHASE) {
            if (session.getState() == EventSession.EventState.RUNNING) {
                de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getMessage("join.event-running"));
            } else if (session.getState() == EventSession.EventState.COUNTDOWN) {
                de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getMessage("join.countdown-active"));
            } else {
                de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getPrefix() + " &cBeitritt nur während der Beitrittsphase möglich.");
            }
            return;
        }
        
        if (session.addPlayer(player)) {
            player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("join.joined", "{event}", config.getDisplayName())));
        } else {
            de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getMessage("join.event-full"));
        }
    }
    
    private void handleLeave(Player player) {
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            if (session.removePlayer(player)) {
                player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("leave.success")));
            }
            return;
        }

        // No active session: allow leaving if the player is still in an event or lobby world
        String currentWorld = player.getWorld().getName();
        boolean isInEventWorld = false;
        for (EventConfig cfg : plugin.getConfigManager().getAllEvents().values()) {
            if (currentWorld.equalsIgnoreCase(cfg.getEventWorld()) ||
                currentWorld.equalsIgnoreCase(cfg.getLobbyWorld())) {
                isInEventWorld = true;
                break;
            }
        }

        if (!isInEventWorld) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(player, plugin.getConfigManager().getMessage("leave.not-in-event"));
            return;
        }

        // Teleport using globally saved location if available, else fall back to main world spawn
        org.bukkit.Location saved = plugin.getEventManager().getSavedLocation(player.getUniqueId());
        if (saved != null && saved.getWorld() != null) {
            player.teleport(saved);
            plugin.getEventManager().clearSavedLocation(player.getUniqueId());
        } else {
            org.bukkit.World mainWorld = org.bukkit.Bukkit.getWorld(plugin.getConfigManager().getMainWorld());
            if (mainWorld != null) {
                player.teleport(mainWorld.getSpawnLocation());
            }
        }
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        player.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("leave.success")));
    }
    
    private void handleStart(CommandSender sender, String eventId) {
        EventConfig config = plugin.getConfigManager().getEventConfig(eventId);
        if (config == null) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("general.event-not-found")
                .replace("{event}", eventId));
            return;
        }
        
        Optional<EventSession> sessionOpt = plugin.getEventManager().getSession(eventId);
        EventSession session;
        
        if (!sessionOpt.isPresent()) {
            if (!plugin.getEventManager().createEvent(eventId)) {
                de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cFehler beim Erstellen des Events!");
                return;
            }
            session = plugin.getEventManager().getSession(eventId).get();
        } else {
            session = sessionOpt.get();
        }
        
        if (session.getState() != EventSession.EventState.WAITING) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getMessage("start.already-running"));
            return;
        }
        
        // Starte Join-Phase statt direkten Countdown
        session.startJoinPhase();
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("start.join-phase-started")));
    }
    
    private void handleStop(CommandSender sender, String eventId) {
        Optional<EventSession> sessionOpt = plugin.getEventManager().getSession(eventId);
        
        if (!sessionOpt.isPresent()) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cKein aktives Event mit diesem Namen!");
            return;
        }
        
        sessionOpt.get().stopEvent();
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("admin.event-stopped")));
    }
    
    private void handleForceStart(CommandSender sender, String eventId) {
        Optional<EventSession> sessionOpt = plugin.getEventManager().getSession(eventId);
        
        if (!sessionOpt.isPresent()) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cKein aktives Event mit diesem Namen!");
            return;
        }
        
        EventSession session = sessionOpt.get();
        if (session.getState() != EventSession.EventState.COUNTDOWN) {
            de.zfzfg.pvpwager.utils.MessageUtil.error(sender, plugin.getConfigManager().getPrefix() + " &cKein Countdown aktiv!");
            return;
        }
        
        session.forceStartCountdown();
    }
    
    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("admin.reloaded")));
    }
    
    private void listEvents(CommandSender sender) {
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " &6Verfügbare Events:"));
        
        for (EventConfig config : plugin.getConfigManager().getAllEvents().values()) {
            String status = plugin.getEventManager().isEventActive(config.getId()) ? "&a[Aktiv]" : "&7[Verfügbar]";
            sender.sendMessage(ColorUtil.color("&7- " + status + " &e" + config.getDisplayName() + 
                " &8(&7/event join " + config.getCommand() + "&8)"));
            sender.sendMessage(ColorUtil.color("  " + config.getDescription()));
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.header")));
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.join")));
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.leave")));
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.list")));
        
        if (sender.hasPermission("eventplugin.admin")) {
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.admin-header")));
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.start")));
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.stop")));
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.forcestart")));
            sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.reload")));
        }
        
        sender.sendMessage(ColorUtil.color(plugin.getConfigManager().getMessage("help.footer")));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("help");
            completions.add("list");
            completions.add("join");
            completions.add("leave");
            
            if (sender.hasPermission("eventplugin.admin")) {
                completions.add("start");
                completions.add("stop");
                completions.add("forcestart");
                completions.add("reload");
            }
            
            // Füge Event-Namen hinzu
            completions.addAll(plugin.getConfigManager().getAllEvents().keySet());
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            
            if (subCmd.equals("join") || subCmd.equals("start") || subCmd.equals("stop") || subCmd.equals("forcestart")) {
                return plugin.getConfigManager().getAllEvents().keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            // Wenn erstes Argument ein Event ist
            if (plugin.getConfigManager().getEventConfig(subCmd) != null) {
                completions.add("join");
                if (sender.hasPermission("eventplugin.admin")) {
                    completions.add("start");
                    completions.add("stop");
                    completions.add("forcestart");
                }
                return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return completions;
    }
}