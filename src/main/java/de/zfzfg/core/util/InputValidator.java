package de.zfzfg.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class InputValidator {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_-]{3,32}$");

    public static String validateEventId(String input) throws IllegalArgumentException {
        if (input == null || !VALID_ID.matcher(input).matches()) {
            throw new IllegalArgumentException("Invalid event ID format");  // i18n-ignore: Exception-Text fuer Entwickler; der Aufrufer meldet dem Spieler einen Bundle-Text
        }
        return input.toLowerCase();
    }

    public static double validateMoney(String input, double min, double max) {
        try {
            double amount = Double.parseDouble(input);
            if (amount < min || amount > max) {
                throw new IllegalArgumentException("Amount out of range");  // i18n-ignore: Exception-Text fuer Entwickler; der Aufrufer meldet dem Spieler einen Bundle-Text
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format");  // i18n-ignore: Exception-Text fuer Entwickler; der Aufrufer meldet dem Spieler einen Bundle-Text
        }
    }

    public static Player validateOnlinePlayer(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Player name is required");  // i18n-ignore: Exception-Text fuer Entwickler; der Aufrufer meldet dem Spieler einen Bundle-Text
        }
        Player target = Bukkit.getPlayer(name);
        if (target == null || !target.isOnline()) {
            throw new IllegalArgumentException("Player " + name + " is not online");  // i18n-ignore: Exception-Text fuer Entwickler; der Aufrufer meldet dem Spieler einen Bundle-Text
        }
        return target;
    }
}