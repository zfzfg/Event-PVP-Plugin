package de.zfzfg.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class InputValidator {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_-]{3,32}$");

    public static String validateEventId(String input) throws IllegalArgumentException {
        if (input == null || !VALID_ID.matcher(input).matches()) {
            throw new IllegalArgumentException("Invalid event ID format");
        }
        return input.toLowerCase();
    }

    public static double validateMoney(String input, double min, double max) {
        try {
            double amount = Double.parseDouble(input);
            if (amount < min || amount > max) {
                throw new IllegalArgumentException("Amount out of range");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format");
        }
    }

    public static Player validateOnlinePlayer(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Player name is required");
        }
        Player target = Bukkit.getPlayer(name);
        if (target == null || !target.isOnline()) {
            throw new IllegalArgumentException("Player " + name + " is not online");
        }
        return target;
    }
}