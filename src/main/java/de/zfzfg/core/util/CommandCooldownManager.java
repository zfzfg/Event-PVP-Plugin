package de.zfzfg.core.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CommandCooldownManager {
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final long defaultCooldownMs;
    private Function<Long, String> messageProvider;

    public CommandCooldownManager() {
        this(3000L);
    }

    public CommandCooldownManager(long defaultCooldownMs) {
        this.defaultCooldownMs = defaultCooldownMs;
        // Last-resort marker only: EventPlugin installs the localized provider right
        // after construction. A baked-in English sentence here would answer in English
        // on a German server whenever that call was ever forgotten.
        this.messageProvider = seconds -> ChatColor.RED + "[missing: messages.system.cooldown-wait]";
    }
    
    /**
     * Setzt die Funktion, die die Cooldown-Nachricht generiert
     * @param provider Funktion die die Restzeit in Sekunden erhält und die Nachricht zurückgibt
     */
    public void setMessageProvider(Function<Long, String> provider) {
        this.messageProvider = provider;
    }

    public synchronized boolean checkAndApply(Player player, String commandName) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        Long lastUse = playerCooldowns.get(commandName);

        if (lastUse != null && (now - lastUse) < defaultCooldownMs) {
            long remaining = (defaultCooldownMs - (now - lastUse)) / 1000;
            player.sendMessage(messageProvider.apply(remaining));
            return false;
        }

        playerCooldowns.put(commandName, now);
        return true;
    }

    /**
     * Removes a player from the cooldown map to prevent memory leaks.
     * Should be called on PlayerQuitEvent.
     */
    public void removePlayer(UUID playerId) {
        cooldowns.remove(playerId);
    }
}