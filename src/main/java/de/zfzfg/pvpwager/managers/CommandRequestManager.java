package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.util.Time;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.core.util.Text;
import de.zfzfg.pvpwager.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class CommandRequestManager {
    private final EventPlugin plugin;
    private final Map<UUID, CommandRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> expirationTasks = new ConcurrentHashMap<>();
    // O(1) Index: Zielspieler -> letzte empfangene Anfrage
    private final Map<UUID, CommandRequest> targetToLatestRequest = new ConcurrentHashMap<>();
    
    public CommandRequestManager(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private String getMsg(String key) {
        if (key == null || key.isEmpty()) return "";
        String msg = null;
        if (key.startsWith("messages.")) {
            msg = plugin.getCoreConfigManager().getMessages().getString(key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.command-request." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.request." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, null);
        }
        if (msg == null) {
            msg = plugin.getCoreConfigManager().getMessages().getString(key, null);
        }
        if (msg == null) {
            return "&c[missing: " + key + "]";
        }
        return MessageUtil.color(msg);
    }
    
    private String getMsg(String key, String placeholder, String value) {
        return getMsg(key, new String[]{placeholder, value});
    }

    private String getMsg(String key, String... replacements) {
        String msg = getMsg(key);
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String raw = replacements[i] != null ? replacements[i].replaceAll("^[{%]+|[%}]+$", "") : "";
                String val = replacements[i + 1] != null ? replacements[i + 1] : "";
                if (!raw.isEmpty()) {
                    msg = msg.replace("{" + raw + "}", val)
                             .replace("%" + raw + "%", val);
                }
            }
        }
        return msg;
    }
    
    public void addRequest(CommandRequest request) {
        pendingRequests.put(request.getSender().getUniqueId(), request);
        targetToLatestRequest.put(request.getTarget().getUniqueId(), request);
        
        // Auto-expire after 60 seconds and track task
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CommandRequest existing = pendingRequests.get(request.getSender().getUniqueId());
            if (existing != null && existing.equals(request)) {
                pendingRequests.remove(request.getSender().getUniqueId());
                // Entferne Ziel-Index, wenn er auf diese Anfrage zeigt
                CommandRequest latest = targetToLatestRequest.get(request.getTarget().getUniqueId());
                if (latest != null && latest.equals(request)) {
                    targetToLatestRequest.remove(request.getTarget().getUniqueId());
                }
                BukkitTask t = expirationTasks.remove(request.getSender().getUniqueId());
                if (t != null) t.cancel();
                MessageUtil.sendMessage(request.getSender(), getMsg("request-expired-sender", "{target}", request.getTarget().getName()));
                MessageUtil.sendMessage(request.getTarget(), getMsg("request-expired-target", "{sender}", request.getSender().getName()));
            }
        }, Time.seconds(60));
        expirationTasks.put(request.getSender().getUniqueId(), task);
    }
    
    public CommandRequest getRequest(Player sender, Player target) {
        CommandRequest request = pendingRequests.get(sender.getUniqueId());
        if (request != null && request.getTarget().equals(target)) {
            return request;
        }
        return null;
    }
    
    public CommandRequest getRequestToPlayer(Player target) {
        return targetToLatestRequest.get(target.getUniqueId());
    }
    
    public void removeRequest(Player sender) {
        CommandRequest existing = pendingRequests.remove(sender.getUniqueId());
        if (existing != null) {
            CommandRequest latest = targetToLatestRequest.get(existing.getTarget().getUniqueId());
            if (latest != null && latest.equals(existing)) {
                targetToLatestRequest.remove(existing.getTarget().getUniqueId());
            }
        }
        BukkitTask task = expirationTasks.remove(sender.getUniqueId());
        if (task != null) task.cancel();
    }
    
    public void sendRequestNotification(CommandRequest request) {
        Player target = request.getTarget();
        
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(target, getMsg("challenge-header"));
        MessageUtil.sendMessage(target, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, getMsg("challenge-message", "{player}", request.getSender().getName()));
        MessageUtil.sendMessage(target, "");
        MessageUtil.sendMessage(target, getMsg("arena-display", "{arena}", request.getArenaId()));
        MessageUtil.sendMessage(target, getMsg("equipment-display", "{equipment}", request.getEquipmentId()));
        
        if (request.getMoney() > 0) {
            MessageUtil.sendMessage(target, getMsg("their-wager-money", "amount", String.format("%.2f", request.getMoney())));
        } else {
            MessageUtil.sendMessage(target, getMsg("their-wager-items", "items", MessageUtil.formatItemList(request.getWagerItems())));
        }
        
        MessageUtil.sendMessage(target, "");
        boolean isSkip = request.getMoney() == 0.0 && (request.getWagerItems() == null || request.getWagerItems().isEmpty());
        if (isSkip) {
            // Show clickable accept/deny buttons for SKIP
            try {
                Component accept = Text.button(getMsg("btn-accept"), "/pvp accept " + request.getSender().getName(), getMsg("btn-accept-hover"));
                Component deny = Text.button(getMsg("btn-deny"), "/pvp deny " + request.getSender().getName(), getMsg("btn-deny-hover"));

                target.sendMessage(accept.append(Component.space()).append(deny));
            } catch (Exception ignored) {
                // Fallback to plain text
                MessageUtil.sendMessage(target, "&a/pvp accept " + request.getSender().getName() + " &7- " + getMsg("accept-command")); // i18n-ignore
                MessageUtil.sendMessage(target, "&c/pvp deny " + request.getSender().getName() + " &7- " + getMsg("deny-command")); // i18n-ignore
            }
            MessageUtil.sendMessage(target, "");
            MessageUtil.sendMessage(target, getMsg("expires-in"));
            MessageUtil.sendMessage(target, "");
        } else {
            // Show clickable buttons for responding with GUI or command
            try {
                Component guiBtn = Text.button(getMsg("btn-open-gui"), "/pvprespond gui", getMsg("btn-open-gui-hover"));
                Component denyBtn = Text.button(getMsg("btn-deny"), "/pvp deny " + request.getSender().getName(), getMsg("btn-deny-hover"));

                target.sendMessage(guiBtn.append(Component.space()).append(denyBtn));
            } catch (Exception ignored) {
                // Fallback
            }
            MessageUtil.sendMessage(target, "");
            MessageUtil.sendMessage(target, getMsg("alternative-response"));
            MessageUtil.sendMessage(target, getMsg("alternative-command"));
            MessageUtil.sendMessage(target, getMsg("expires-in"));
            MessageUtil.sendMessage(target, "");
        }
    }
    
    public Collection<CommandRequest> getPendingRequests() {
        return new ArrayList<>(pendingRequests.values());
    }
    
    public boolean hasPendingRequest(Player player) {
        // Check if player is sender
        if (pendingRequests.containsKey(player.getUniqueId())) {
            return true;
        }
       
        // Check if player is target (Snapshot sichern gegen gleichzeitige Änderungen)
        for (CommandRequest request : new ArrayList<>(pendingRequests.values())) {
            if (request.getTarget().equals(player)) {
                return true;
            }
        }
       
        return false;
    }

    public void cleanup() {
        for (BukkitTask t : expirationTasks.values()) {
            try { t.cancel(); } catch (Exception e) {
                plugin.getLogger().warning("Failed to cancel expiration task during cleanup: " + e.getMessage());  // i18n-ignore: technical task exception log
            }
        }
        expirationTasks.clear();
        pendingRequests.clear();
        targetToLatestRequest.clear();
    }

    // Remove any pending request associated with a player (sender or target)
    public void removeRequestsForPlayer(org.bukkit.entity.Player player) {
        java.util.UUID playerId = player.getUniqueId();
        // Remove as sender
        CommandRequest existing = pendingRequests.remove(playerId);
        if (existing != null) {
            // Remove target index if pointing to this request
            CommandRequest latest = targetToLatestRequest.get(existing.getTarget().getUniqueId());
            if (latest != null && latest.equals(existing)) {
                targetToLatestRequest.remove(existing.getTarget().getUniqueId());
            }
        }
        BukkitTask task = expirationTasks.remove(playerId);
        if (task != null) { try { task.cancel(); } catch (Exception e) {
            plugin.getLogger().warning("Failed to cancel expiration task: " + e.getMessage());  // i18n-ignore: technical task exception log
        } }

        // Remove any request where player is target
        for (java.util.Map.Entry<java.util.UUID, CommandRequest> e : new java.util.ArrayList<>(pendingRequests.entrySet())) {
            CommandRequest req = e.getValue();
            if (req != null && playerId.equals(req.getTarget().getUniqueId())) {
                pendingRequests.remove(e.getKey());
                // Also remove target index pointing to this player
                CommandRequest latest = targetToLatestRequest.get(playerId);
                if (latest != null && latest.equals(req)) {
                    targetToLatestRequest.remove(playerId);
                }
                BukkitTask t = expirationTasks.remove(e.getKey());
                if (t != null) { try { t.cancel(); } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to cancel expiration task: " + ex.getMessage());  // i18n-ignore: technical task exception log
                } }
            }
        }
    }
}
