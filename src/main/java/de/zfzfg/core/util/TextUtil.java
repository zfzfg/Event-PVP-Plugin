package de.zfzfg.core.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextUtil {

    private static final Map<String, String> colorCache = new ConcurrentHashMap<>();

    public static String color(String text) {
        if (text == null) return "";
        return colorCache.computeIfAbsent(text, k -> ChatColor.translateAlternateColorCodes('&', k));
    }

    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(color(text));
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null) return;
        sender.sendMessage(color(message));
    }

    public static void send(Player player, String message) {
        if (player == null) return;
        player.sendMessage(color(message));
    }
}