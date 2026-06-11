package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeManager;
import de.zfzfg.pvpwager.utils.MessageUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.scheduler.BukkitTask;

/**
 * Befehl zum Senden einer PVP-Wager Anfrage.
 * Der Zielspieler erhält eine klickbare Nachricht im Chat.
 * Bei Annahme öffnet sich das Wager-GUI für beide Spieler.
 * 
 * Usage: /pvpask <spieler>
 */
public class PvPWagerGuiCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    
    // Wager-Anfragen: senderId -> targetId
    private final Map<UUID, UUID> wagerRequests = new ConcurrentHashMap<>();
    // Expiration Tasks
    private final Map<UUID, BukkitTask> expirationTasks = new ConcurrentHashMap<>();
    
    public PvPWagerGuiCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    private String getMsg(String key) {
        String msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, "");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
    
    private String getGuiMsg(String key) {
        String msg = plugin.getPvpConfigManager().getMessage("messages.pvp-wager-gui." + key);
        return msg != null ? msg : key;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("players-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Prüfe Permission
        if (!player.hasPermission("pvpwager.use") && !player.hasPermission("pvpwager.ask")) {
            MessageUtil.sendMessage(player, getGuiMsg("no-permission"));
            return true;
        }
        
        // Prüfe Argumente
        if (args.length == 0) {
            showUsage(player);
            return true;
        }
        
        // Finde Zielspieler
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, getGuiMsg("player-offline").replace("{player}", targetName));
            return true;
        }
        
        // Prüfe ob es der Spieler selbst ist
        if (player.equals(target)) {
            MessageUtil.sendMessage(player, getGuiMsg("cannot-challenge-self"));
            return true;
        }
        
        LiveTradeManager liveTradeManager = plugin.getLiveTradeManager();
        
        // Prüfe ob Spieler bereits in einer Wager-Session ist
        if (liveTradeManager.isInSession(player)) {
            MessageUtil.sendMessage(player, getGuiMsg("already-in-session"));
            return true;
        }
        
        if (liveTradeManager.isInSession(target)) {
            MessageUtil.sendMessage(player, getGuiMsg("target-in-session").replace("{player}", target.getName()));
            return true;
        }
        
        // Prüfe ob Spieler bereits in einem Match ist
        if (plugin.getMatchManager().isPlayerInMatch(player)) {
            MessageUtil.sendMessage(player, getGuiMsg("already-in-match"));
            return true;
        }
        
        if (plugin.getMatchManager().isPlayerInMatch(target)) {
            MessageUtil.sendMessage(player, getGuiMsg("target-in-match").replace("{player}", target.getName()));
            return true;
        }
        
        // Prüfe ob bereits eine Wager-Anfrage existiert
        if (hasWagerRequest(player)) {
            MessageUtil.sendMessage(player, getGuiMsg("request-pending"));
            MessageUtil.sendMessage(player, getGuiMsg("request-pending-hint"));
            return true;
        }
        
        // Prüfe Arenen verfügbar
        if (plugin.getArenaManager().getArenas().isEmpty()) {
            MessageUtil.sendMessage(player, getGuiMsg("no-arenas"));
            MessageUtil.sendMessage(player, getGuiMsg("create-arenas-first"));
            return true;
        }
        
        // Prüfe Equipment verfügbar
        if (plugin.getEquipmentManager().getEquipmentSets().isEmpty()) {
            MessageUtil.sendMessage(player, getGuiMsg("no-equipment"));
            MessageUtil.sendMessage(player, getGuiMsg("create-equipment-first"));
            return true;
        }
        
        // Anfrage senden
        sendWagerRequest(player, target);
        
        return true;
    }
    
    /**
     * Sendet eine Wager-Anfrage an den Zielspieler.
     */
    private void sendWagerRequest(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        // Bestehende Anfrage entfernen
        cancelWagerRequest(senderId);
        
        // Neue Anfrage speichern
        wagerRequests.put(senderId, targetId);
        
        // Nachricht an Sender
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, getGuiMsg("request-sent-header"));
        MessageUtil.sendMessage(sender, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, getGuiMsg("challenge-sent").replace("{player}", target.getName()));
        MessageUtil.sendMessage(sender, getGuiMsg("waiting-response"));
        MessageUtil.sendMessage(sender, "");
        
        // Klickbare Nachricht an Target
        sendClickableWagerRequest(sender, target);
        
        // Auto-Expire nach 60 Sekunden
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (wagerRequests.containsKey(senderId) && wagerRequests.get(senderId).equals(targetId)) {
                wagerRequests.remove(senderId);
                expirationTasks.remove(senderId);
                
                Player s = Bukkit.getPlayer(senderId);
                Player t = Bukkit.getPlayer(targetId);
                
                if (s != null && s.isOnline()) {
                    MessageUtil.sendMessage(s, getGuiMsg("request-expired-sender").replace("{player}", (t != null ? t.getName() : "???")));
                }
                if (t != null && t.isOnline()) {
                    MessageUtil.sendMessage(t, getGuiMsg("request-expired-target").replace("{player}", (s != null ? s.getName() : "???")));
                }
            }
        }, 20L * 60); // 60 Sekunden
        
        expirationTasks.put(senderId, task);
    }
    
    /**
     * Sendet eine klickbare Wager-Anfrage an den Zielspieler.
     */
    private void sendClickableWagerRequest(Player sender, Player target) {
        try {
            // Header
            TextComponent message = new TextComponent(MessageUtil.color(
                "\n§6§l━━━━━━━━━━━━━━━━━━━━━━━\n" +
                getGuiMsg("request-header") + "\n" +
                "§6§l━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                getGuiMsg("challenge-message").replace("{player}", sender.getName()) + "\n\n"
            ));
            
            // ANNEHMEN Button - öffnet das GUI
            TextComponent acceptButton = new TextComponent(MessageUtil.color(getGuiMsg("accept-button")));
            acceptButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/pvpaccept " + sender.getName()
            ));
            acceptButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.color(getGuiMsg("accept-hover"))).create()
            ));
            
            // ABLEHNEN Button
            TextComponent denyButton = new TextComponent(MessageUtil.color(getGuiMsg("deny-button")));
            denyButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/pvpdeny " + sender.getName()
            ));
            denyButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.color(getGuiMsg("deny-hover"))).create()
            ));
            
            // Footer
            TextComponent footer = new TextComponent(MessageUtil.color(
                getGuiMsg("request-footer")
            ));
            
            // Zusammensetzen
            message.addExtra(acceptButton);
            message.addExtra(denyButton);
            message.addExtra(footer);
            
            // An Target senden
            target.spigot().sendMessage(message);
            
        } catch (Exception e) {
            // Fallback
            MessageUtil.sendMessage(target, "");
            MessageUtil.sendMessage(target, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(target, getGuiMsg("request-header"));
            MessageUtil.sendMessage(target, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendMessage(target, "");
            MessageUtil.sendMessage(target, getGuiMsg("challenge-message").replace("{player}", sender.getName()));
            MessageUtil.sendMessage(target, "");
            MessageUtil.sendMessage(target, "&a/pvpaccept " + sender.getName() + " &7- " + getGuiMsg("accept-command"));
            MessageUtil.sendMessage(target, "&c/pvpdeny " + sender.getName() + " &7- " + getGuiMsg("deny-command"));
            MessageUtil.sendMessage(target, "");
        }
    }
    
    /**
     * Prüft ob ein Spieler eine ausstehende Wager-Anfrage hat (als Sender).
     */
    public boolean hasWagerRequest(Player player) {
        return wagerRequests.containsKey(player.getUniqueId());
    }
    
    /**
     * Prüft ob ein Spieler eine ausstehende Wager-Anfrage hat (als Target).
     */
    public boolean hasWagerRequestAsTarget(Player player) {
        return wagerRequests.containsValue(player.getUniqueId());
    }
    
    /**
     * Holt die UUID des Senders einer Anfrage an den Target.
     */
    public UUID getWagerRequestSender(Player target) {
        UUID targetId = target.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : wagerRequests.entrySet()) {
            if (entry.getValue().equals(targetId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Holt die Anfrage für einen bestimmten Sender.
     */
    public UUID getWagerRequestTarget(UUID senderId) {
        return wagerRequests.get(senderId);
    }
    
    /**
     * Akzeptiert eine Wager-Anfrage und öffnet das GUI.
     */
    public boolean acceptWagerRequest(Player target, Player sender) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        // Prüfe ob Anfrage existiert
        if (!wagerRequests.containsKey(senderId) || !wagerRequests.get(senderId).equals(targetId)) {
            return false;
        }
        
        // Anfrage entfernen
        wagerRequests.remove(senderId);
        BukkitTask task = expirationTasks.remove(senderId);
        if (task != null) task.cancel();
        
        // Prüfe ob beide Spieler verfügbar sind
        LiveTradeManager liveTradeManager = plugin.getLiveTradeManager();
        
        if (liveTradeManager.isInSession(sender) || liveTradeManager.isInSession(target)) {
            MessageUtil.sendMessage(sender, getGuiMsg("both-in-session"));
            MessageUtil.sendMessage(target, getGuiMsg("both-in-session"));
            return false;
        }
        
        if (plugin.getMatchManager().isPlayerInMatch(sender) || plugin.getMatchManager().isPlayerInMatch(target)) {
            MessageUtil.sendMessage(sender, getGuiMsg("both-in-match"));
            MessageUtil.sendMessage(target, getGuiMsg("both-in-match"));
            return false;
        }
        
        // Session erstellen und starten
        var session = liveTradeManager.createSession(sender, target);
        
        if (session == null) {
            MessageUtil.sendMessage(sender, getGuiMsg("session-start-failed"));
            MessageUtil.sendMessage(target, getGuiMsg("session-start-failed"));
            return false;
        }
        
        // Session starten (öffnet GUIs für beide Spieler)
        session.start();
        
        return true;
    }
    
    /**
     * Lehnt eine Wager-Anfrage ab.
     */
    public boolean denyWagerRequest(Player target, Player sender) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        // Prüfe ob Anfrage existiert
        if (!wagerRequests.containsKey(senderId) || !wagerRequests.get(senderId).equals(targetId)) {
            return false;
        }
        
        // Anfrage entfernen
        cancelWagerRequest(senderId);
        
        MessageUtil.sendMessage(sender, getGuiMsg("request-declined-other").replace("{player}", target.getName()));
        MessageUtil.sendMessage(target, getGuiMsg("request-declined-self").replace("{player}", sender.getName()));
        
        return true;
    }
    
    /**
     * Bricht eine Wager-Anfrage ab.
     */
    public void cancelWagerRequest(UUID senderId) {
        wagerRequests.remove(senderId);
        BukkitTask task = expirationTasks.remove(senderId);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
    }
    
    /**
     * Entfernt alle Anfragen für einen Spieler (bei Quit).
     */
    public void removeRequestsForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Als Sender
        cancelWagerRequest(playerId);
        
        // Als Target
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : wagerRequests.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(this::cancelWagerRequest);
    }
    
    /**
     * Cleanup bei Plugin-Disable.
     */
    public void cleanup() {
        for (BukkitTask task : expirationTasks.values()) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        expirationTasks.clear();
        wagerRequests.clear();
    }
    
    private String getWagerHelpMsg(String key) {
        return plugin.getCoreConfigManager().getMessages().getString("messages.pvpwager.help." + key, key);
    }
    
    private void showUsage(Player player) {
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, getWagerHelpMsg("title"));
        MessageUtil.sendMessage(player, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getWagerHelpMsg("usage"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("description1"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("description2"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("description3"));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getWagerHelpMsg("gui-description1"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("gui-description2"));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getWagerHelpMsg("feature1"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("feature2"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("feature3"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("feature4"));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, getWagerHelpMsg("countdown1"));
        MessageUtil.sendMessage(player, getWagerHelpMsg("countdown2"));
        MessageUtil.sendMessage(player, "");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        
        Player player = (Player) sender;
        LiveTradeManager liveTradeManager = plugin.getLiveTradeManager();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> !liveTradeManager.isInSession(p))
                .filter(p -> !plugin.getMatchManager().isPlayerInMatch(p))
                .filter(p -> p.getName().toLowerCase().startsWith(prefix))
                .map(Player::getName)
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
