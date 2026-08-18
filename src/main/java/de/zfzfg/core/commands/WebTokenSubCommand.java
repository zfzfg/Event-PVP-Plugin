package de.zfzfg.core.commands;

import de.zfzfg.core.web.WebAuthManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.util.Text;
import de.zfzfg.core.util.TextUtil;
import de.zfzfg.pvpwager.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SubCommand: /eventpvp webtoken
 * Generiert einen Token für den Web-Interface Login
 */
public class WebTokenSubCommand extends SubCommand {
    private static final Set<String> MISSING_KEYS_LOGGED = ConcurrentHashMap.newKeySet();

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

    private void warnMissingKey(String path) {
        if (MISSING_KEYS_LOGGED.add(path)) {
            plugin.getLogger().warning("Missing message key: " + path + " (check messages_*.yml)"); // i18n-ignore: i18n
                                                                                                    // system warning
        }
    }

    private String msg(String key) {
        FileConfiguration messages = plugin.getCoreConfigManager().getMessages();
        String val = messages.getString("messages.webtoken." + key, null);
        if (val != null) {
            return TextUtil.color(val);
        }
        warnMissingKey("messages.webtoken." + key);
        return "§c[missing: " + key + "]"; // i18n-ignore: missing key fallback marker
    }

    private String msg(String key, String placeholder, String value) {
        String msg = msg(key);
        String val = value != null ? value : "";
        String raw = placeholder != null ? placeholder.replaceAll("^[{%]+|[%}]+$", "") : "";
        if (!raw.isEmpty()) {
            msg = msg.replace("{" + raw + "}", val)
                    .replace("%" + raw + "%", val);
        }
        return msg;
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
        Component tokenComponent = Text.of("  §a§l➤ " + token + " " + msg("click-to-copy"))
                .clickEvent(ClickEvent.copyToClipboard(token))
                .hoverEvent(HoverEvent.showText(Text.of(msg("hover-copy"))));
        TextUtil.send(player, tokenComponent);

        player.sendMessage("");
        player.sendMessage(msg("valid-for", "minutes", String.valueOf(authManager.getTokenValidityMinutes())));
        player.sendMessage(msg("single-use"));
        player.sendMessage("");

        // Web-URL (aus Konfiguration)
        String url = plugin.getWebPublicUrl();

        // Debug-Log
        plugin.getDebugManager().log("WebToken Command - URL from config: " + url); // i18n-ignore: technical debug
                                                                                    // trace

        Component urlComponent = Text.link(
                "  §b§l➤ " + url + " " + msg("click-to-open"),
                url,
                msg("hover-open"));
        TextUtil.send(player, urlComponent);

        player.sendMessage("§8§m                                                §r");
        player.sendMessage("");

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
