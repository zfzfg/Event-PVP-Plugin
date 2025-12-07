package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeManager;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeSession;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command für das Live-Trade PVP-Anfrage-System.
 * Öffnet ein synchronisiertes GUI für beide Spieler,
 * ähnlich wie AxTrade.
 * 
 * Verwendung: /pvpask <spieler>
 */
public class PvPAskCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    
    public PvPAskCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private String getMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.pvpask." + key, key);
    }
    
    private String getMsg(String key, String placeholder, String value) {
        String message = plugin.getCoreConfigManager().getMessages()
            .getString("messages.pvpask." + key, key);
        return message.replace("{" + placeholder + "}", value);
    }
    
    private String getGeneralMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.general." + key, key);
    }
    
    private String getHelpMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.help.pvp." + key, key);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color(getGeneralMsg("player-only")));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Prüfe Permission
        if (!player.hasPermission("pvpwager.use") && !player.hasPermission("pvpwager.ask")) {
            MessageUtil.sendMessage(player, getGeneralMsg("no-permission"));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        // Validierung
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, getMsg("not-online", "player", targetName));
            return true;
        }
        
        if (player.equals(target)) {
            MessageUtil.sendMessage(player, getMsg("self-request"));
            return true;
        }
        
        LiveTradeManager liveTradeManager = plugin.getLiveTradeManager();
        
        // Prüfe ob Spieler bereits in einer Session ist
        if (liveTradeManager.isInSession(player)) {
            MessageUtil.sendMessage(player, getMsg("already-in-trade"));
            return true;
        }
        
        if (liveTradeManager.isInSession(target)) {
            MessageUtil.sendMessage(player, getMsg("target-in-trade", "player", target.getName()));
            return true;
        }
        
        // Prüfe ob Spieler in einem Match ist
        if (plugin.getMatchManager().isPlayerInMatch(player)) {
            MessageUtil.sendMessage(player, getMsg("already-in-match"));
            return true;
        }
        
        if (plugin.getMatchManager().isPlayerInMatch(target)) {
            MessageUtil.sendMessage(player, getMsg("target-in-match", "player", target.getName()));
            return true;
        }
        
        // Prüfe ob Spieler eine ausstehende Anfrage hat (altes System)
        if (plugin.getRequestManager().hasPendingRequest(player)) {
            MessageUtil.sendMessage(player, getMsg("pending-request"));
            MessageUtil.sendMessage(player, getMsg("pending-request-hint"));
            return true;
        }
        
        if (plugin.getRequestManager().hasPendingRequest(target)) {
            MessageUtil.sendMessage(player, getMsg("target-pending", "player", target.getName()));
            return true;
        }
        
        // Prüfe Arenen verfügbar
        if (plugin.getArenaManager().getArenas().isEmpty()) {
            MessageUtil.sendMessage(player, getMsg("no-arenas"));
            return true;
        }
        
        // Prüfe Equipment verfügbar
        if (plugin.getEquipmentManager().getEquipmentSets().isEmpty()) {
            MessageUtil.sendMessage(player, getMsg("no-equipment"));
            return true;
        }
        
        // Session erstellen
        LiveTradeSession session = liveTradeManager.createSession(player, target);
        
        if (session == null) {
            MessageUtil.sendMessage(player, getMsg("trade-error"));
            return true;
        }
        
        // Session starten (öffnet GUIs für beide Spieler)
        session.start();
        
        return true;
    }
    
    private void sendUsage(Player player) {
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getHelpMsg("header"));
        MessageUtil.sendMessage(player, getHelpMsg("title"));
        MessageUtil.sendMessage(player, getHelpMsg("header"));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getHelpMsg("usage"));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getMsg("pvpask.help.description1"));
        MessageUtil.sendMessage(player, getMsg("pvpask.help.description2"));
        MessageUtil.sendMessage(player, getMsg("pvpask.help.description3"));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getMsg("pvpask.help.countdown1"));
        MessageUtil.sendMessage(player, getMsg("pvpask.help.countdown2"));
        MessageUtil.sendMessage(player, "");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> !plugin.getLiveTradeManager().isInSession(p))
                .filter(p -> !plugin.getMatchManager().isPlayerInMatch(p))
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
