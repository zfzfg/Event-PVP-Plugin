package de.zfzfg.core.commands;

import de.zfzfg.core.web.WebAuthManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * SubCommand: /eventpvp webtoken
 * Generiert einen Token für den Web-Interface Login
 */
public class WebTokenSubCommand extends SubCommand {
    
    public WebTokenSubCommand(EventPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "webtoken";
    }
    
    @Override
    public String getPermission() {
        return "eventpvp.admin.web";
    }
    
    @Override
    public String getUsage() {
        return "/eventpvp webtoken";
    }
    
    @Override
    public List<String> getAliases() {
        return Collections.singletonList("wt");
    }
    
    private String msg(String key) {
        FileConfiguration messages = plugin.getCoreConfigManager().getMessages();
        return ChatColor.translateAlternateColorCodes('&', 
            messages.getString("messages.webtoken." + key, key));
    }
    
    private String msg(String key, String placeholder, String value) {
        FileConfiguration messages = plugin.getCoreConfigManager().getMessages();
        String message = messages.getString("messages.webtoken." + key, key);
        return ChatColor.translateAlternateColorCodes('&', 
            message.replace("{" + placeholder + "}", value));
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("player-only"));
            return true;
        }
        
        // Prüfe Permission
        if (!player.hasPermission(getPermission()) && !player.isOp()) {
            MessageUtil.sendMessage(player, msg("no-permission"));
            return true;
        }
        
        WebAuthManager authManager = plugin.getWebAuthManager();
        if (authManager == null) {
            MessageUtil.sendMessage(player, msg("not-enabled"));
            return true;
        }
        
        String token = authManager.generateToken(player);
        if (token == null) {
            MessageUtil.sendMessage(player, msg("generation-failed"));
            return true;
        }
        
        // Sende formatierte Nachricht mit klickbarem Token
        player.sendMessage("");
        player.sendMessage("§8§m                                                §r");
        player.sendMessage(msg("header"));
        player.sendMessage("");
        player.sendMessage(msg("your-token"));
        
        // Klickbarer Token
        TextComponent tokenComponent = new TextComponent("  §a§l➤ " + token + " " + msg("click-to-copy"));
        tokenComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, token));
        tokenComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new Text(msg("hover-copy"))));
        player.spigot().sendMessage(tokenComponent);
        
        player.sendMessage("");
        player.sendMessage(msg("valid-for", "minutes", String.valueOf(authManager.getTokenValidityMinutes())));
        player.sendMessage(msg("single-use"));
        player.sendMessage("");
        
        // Web-URL (aus Konfiguration)
        String url = plugin.getWebPublicUrl();
        
        // Debug-Log
        plugin.getLogger().info("WebToken Command - URL from config: " + url);
        
        TextComponent urlComponent = new TextComponent("  §b§l➤ " + url + " " + msg("click-to-open"));
        urlComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        urlComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new Text(msg("hover-open"))));
        player.spigot().sendMessage(urlComponent);
        
        player.sendMessage("§8§m                                                §r");
        player.sendMessage("");
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
