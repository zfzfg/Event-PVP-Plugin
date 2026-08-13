package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PvPInfoCommand implements CommandExecutor {
    private static final Set<String> MISSING_KEYS_LOGGED = ConcurrentHashMap.newKeySet();
    
    private final EventPlugin plugin;
    
    public PvPInfoCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private void warnMissingKey(String path) {
        if (MISSING_KEYS_LOGGED.add(path)) {
            plugin.getLogger().warning("Missing message key: " + path + " (check messages_*.yml)");  // i18n-ignore: i18n system warning
        }
    }
    
    private String getInfoMsg(String key) {
        String val = plugin.getCoreConfigManager().getMessages().getString("messages.info." + key, null);
        if (val != null) return val;
        warnMissingKey("messages.info." + key);
        return "&c[missing: " + key + "]";
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color(getInfoMsg("players-only")));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Header
        player.sendMessage(MessageUtil.color(""));
        player.sendMessage(MessageUtil.color(getInfoMsg("header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("title")));
        player.sendMessage(MessageUtil.color(getInfoMsg("header")));
        player.sendMessage(MessageUtil.color(""));
        
        // Basis-Befehle
        player.sendMessage(MessageUtil.color(getInfoMsg("basic-commands.header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("basic-commands.pvp")));
        player.sendMessage(MessageUtil.color(getInfoMsg("basic-commands.accept")));
        player.sendMessage(MessageUtil.color(getInfoMsg("basic-commands.deny")));
        player.sendMessage(MessageUtil.color(getInfoMsg("basic-commands.spectate")));
        player.sendMessage(MessageUtil.color(""));
        
        // Match-Befehle
        player.sendMessage(MessageUtil.color(getInfoMsg("match-commands.header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("match-commands.surrender")));
        player.sendMessage(MessageUtil.color(getInfoMsg("match-commands.draw")));
        player.sendMessage(MessageUtil.color(""));
        
        // Antwort-Befehle
        player.sendMessage(MessageUtil.color(getInfoMsg("answer-commands.header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("answer-commands.pvpanswer")));
        player.sendMessage(MessageUtil.color(getInfoMsg("answer-commands.pvpyes")));
        player.sendMessage(MessageUtil.color(getInfoMsg("answer-commands.pvpno")));
        player.sendMessage(MessageUtil.color(""));
        
        // Beispiele für /pvpanswer
        player.sendMessage(MessageUtil.color(getInfoMsg("examples.header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("examples.money")));
        player.sendMessage(MessageUtil.color(getInfoMsg("examples.items")));
        player.sendMessage(MessageUtil.color(getInfoMsg("examples.money-arena")));
        player.sendMessage(MessageUtil.color(getInfoMsg("examples.items-full")));
        player.sendMessage(MessageUtil.color(""));
        
        // Wichtige Hinweise
        player.sendMessage(MessageUtil.color(getInfoMsg("notices.header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("notices.world-loading")));
        player.sendMessage(MessageUtil.color(getInfoMsg("notices.confirmation")));
        player.sendMessage(MessageUtil.color(getInfoMsg("notices.safety")));
        player.sendMessage(MessageUtil.color(""));
        
        // Admin-Bereich
        if (player.hasPermission("pvpwager.admin")) {
            player.sendMessage(MessageUtil.color(getInfoMsg("admin.header")));
            player.sendMessage(MessageUtil.color(getInfoMsg("admin.reload")));
            player.sendMessage(MessageUtil.color(getInfoMsg("admin.stop")));
            player.sendMessage(MessageUtil.color(getInfoMsg("admin.arenas")));
            player.sendMessage(MessageUtil.color(getInfoMsg("admin.equipment")));
            player.sendMessage(MessageUtil.color(""));
        }
        
        // Footer
        player.sendMessage(MessageUtil.color(getInfoMsg("header")));
        player.sendMessage(MessageUtil.color(getInfoMsg("footer")));
        player.sendMessage(MessageUtil.color(getInfoMsg("header")));
        player.sendMessage(MessageUtil.color(""));
        
        return true;
    }
}
