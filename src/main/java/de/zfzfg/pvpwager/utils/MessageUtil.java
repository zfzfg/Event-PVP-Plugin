package de.zfzfg.pvpwager.utils;

import de.zfzfg.core.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility for coloring and sending messages efficiently.
 * Batches multi-line sends to reduce per-line overhead and network chatter.
 */
public class MessageUtil {

    public static String color(String message) { return TextUtil.color(message); }
    public static List<String> color(List<String> messages) { return messages.stream().map(MessageUtil::color).collect(Collectors.toList()); }
    public static void sendMessage(CommandSender sender, String message) { TextUtil.send(sender, message); }
    public static void sendMessage(Player player, String message) { TextUtil.send(player, message); }

    /**
     * Sends multiple messages in one buffered call using a StringBuilder.
     * Each message is colored individually and separated by a newline.
     */
    public static void sendMessages(CommandSender sender, List<String> messages) {
        if (messages == null || messages.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            sb.append(color(messages.get(i)));
            if (i < messages.size() - 1) sb.append('\n');
        }
        TextUtil.send(sender, sb.toString());
    }

    /**
     * Sends multiple messages in one buffered call using a StringBuilder.
     * Each message is colored individually and separated by a newline.
     */
    public static void sendMessages(Player player, List<String> messages) {
        if (messages == null || messages.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            sb.append(color(messages.get(i)));
            if (i < messages.size() - 1) sb.append('\n');
        }
        TextUtil.send(player, sb.toString());
    }

    public static String formatTime(long seconds) {
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes > 0 ? String.format("%02d:%02d", minutes, seconds) : String.format("%02d", seconds);
    }

    public static String formatItemList(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return "no items";
        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> item.getType().name() + " x" + item.getAmount())
                .collect(Collectors.joining(", "));
    }

    /**
     * Sends a standardized error message (red) to a sender.
     */
    public static void error(CommandSender sender, String message) {
        TextUtil.send(sender, color("&c" + message));
    }

    /**
     * Sends a standardized error message (red) to a player.
     */
    public static void error(Player player, String message) {
        TextUtil.send(player, color("&c" + message));
    }
}