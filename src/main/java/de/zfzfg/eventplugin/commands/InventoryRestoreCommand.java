package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.storage.InventorySnapshotStorage;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /inventoryrestore <ID>
 * Restores a saved inventory by its 4-digit ID (per world).
 * Shows world and optional event/match ID for clarity.
 */
public class InventoryRestoreCommand implements CommandExecutor, TabCompleter {

    private final EventPlugin plugin;

    public InventoryRestoreCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.error(sender, "Nur Spieler können Inventare wiederherstellen.");
            return true;
        }

        Player player = (Player) sender;

        // Global gate: require base restore permission even for self
        if (!player.hasPermission("eventpvp.inventory.restore")) {
            MessageUtil.error(player, "Dir fehlt die Berechtigung: eventpvp.inventory.restore");
            return true;
        }

        if (args.length < 1) {
            MessageUtil.error(player, "Nutze: /" + label + " [Spieler] <InventarID>");
            return true;
        }

        String targetName = null;
        String idArg;
        if (args.length >= 2) {
            targetName = args[0].trim();
            idArg = args[1].trim();
        } else {
            idArg = args[0].trim();
        }

        // Accept 4-digit numeric IDs or prefixed IDs like EVENT0001 / MATCH0001
        if (!idArg.matches("(?i)^[A-Z]*\\d{4}$")) {
            MessageUtil.error(player, "Die Inventar-ID muss vierstellig sein (z.B. 0001) oder mit Präfix (EVENT0001/MATCH0001).");
            return true;
        }

        Map<String, Object> meta = InventorySnapshotStorage.getEntryByInventoryId(plugin, idArg);
        if (meta == null) {
            MessageUtil.error(player, "Kein Inventar mit ID " + idArg + " gefunden.");
            return true;
        }

        String ownerUuid = String.valueOf(meta.getOrDefault("uuid", "")).toLowerCase(Locale.ROOT);
        String playerUuidStr = player.getUniqueId().toString().toLowerCase(Locale.ROOT);
        boolean isOwner = !ownerUuid.isEmpty() && ownerUuid.equalsIgnoreCase(playerUuidStr);

        // If player name provided, validate and require elevated permission if restoring others
        if (targetName != null && !targetName.isEmpty()) {
            java.util.UUID targetUuid = null;
            
            // First try to find UUID from snapshots (most reliable for this use case)
            targetUuid = InventorySnapshotStorage.findSnapshotOwnerUuidByName(plugin, targetName);
            
            // Fallback: try Bukkit if player is known
            if (targetUuid == null) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
                // Only use Bukkit UUID if player has actually played before
                if (off != null && off.hasPlayedBefore()) {
                    targetUuid = off.getUniqueId();
                }
            }
            
            if (targetUuid == null) {
                MessageUtil.error(player, "Spieler \"" + targetName + "\" hat keine Snapshots oder ist unbekannt.");
                return true;
            }
            
            String targetUuidStr = targetUuid.toString().toLowerCase(Locale.ROOT);
            if (!ownerUuid.equalsIgnoreCase(targetUuidStr)) {
                // Debug: Log the mismatch for troubleshooting
                plugin.getLogger().warning("[InventoryRestore] UUID mismatch: owner=" + ownerUuid + ", target=" + targetUuidStr + ", ID=" + idArg);
                MessageUtil.error(player, "Die ID gehört nicht zu Spieler \"" + targetName + "\".");
                return true;
            }
            
            boolean isTargetOwner = targetUuidStr.equalsIgnoreCase(playerUuidStr);
            if (!(isTargetOwner || player.isOp() || player.hasPermission("eventpvp.inventory.restore.any"))) {
                MessageUtil.error(player, "Du darfst nur dein eigenes Inventar restoren.");
                return true;
            }
        } else {
            // No target name provided: only allow if restoring own snapshot or elevated permission
            if (!isOwner && !(player.isOp() || player.hasPermission("eventpvp.inventory.restore.any"))) {
                MessageUtil.error(player, "Du darfst nur dein eigenes Inventar restoren.");
                return true;
            }
        }

        boolean ok = InventorySnapshotStorage.restoreByInventoryId(plugin, idArg);
        if (!ok) {
            MessageUtil.error(player, "Wiederherstellung fehlgeschlagen. Ist der Zielspieler online?");
            return true;
        }

        String world = String.valueOf(meta.getOrDefault("world", "unbekannt"));
        String group = String.valueOf(meta.getOrDefault("group", ""));
        String eventMatchId = String.valueOf(meta.getOrDefault("event_match_id", ""));
        String when = String.valueOf(meta.getOrDefault("timestamp", ""));

        String ownerName = "";
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(java.util.UUID.fromString(ownerUuid));
            if (op != null && op.getName() != null) ownerName = op.getName();
        } catch (Exception ignored) {}

        String line = String.format(Locale.ROOT,
                "&aInventar &e#%s &aaus &e%s &7(Group: %s) &aist wiederhergestellt.",
                idArg, world, group);
        MessageUtil.sendMessage(player, line);
        if (eventMatchId != null && !eventMatchId.isEmpty()) {
            MessageUtil.sendMessage(player, "&7Verknüpftes Event/Match: &e" + eventMatchId);
        }
        if (!when.isEmpty()) {
            MessageUtil.sendMessage(player, "&7Gespeichert am: &e" + when + (ownerName.isEmpty() ? "" : " &7von &e" + ownerName));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                java.util.Set<java.util.UUID> uuids = InventorySnapshotStorage.listPlayerUuidsWithSnapshots(plugin);
                java.util.List<String> names = new java.util.ArrayList<>();
                for (java.util.UUID id : uuids) {
                    String name = null;
                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                    if (op != null && op.getName() != null) name = op.getName();
                    if (name == null || name.isEmpty()) {
                        name = InventorySnapshotStorage.getLastKnownName(plugin, id);
                    }
                    if (name != null && !name.isEmpty()) names.add(name);
                }
                // also allow direct ID entry: suggest recent 4-digit IDs from own snapshots
                java.util.List<String> ownIds = InventorySnapshotStorage.listInventoryIdsForPlayer(plugin, sender instanceof Player ? ((Player) sender).getUniqueId() : null);
                if (ownIds != null) names.addAll(ownIds);
                java.util.List<String> filtered = new java.util.ArrayList<>();
                for (String n : names) {
                    if (n.toLowerCase(Locale.ROOT).startsWith(prefix)) filtered.add(n);
                }
                return filtered;
            } else if (args.length == 2) {
                String playerName = args[0];
                java.util.UUID targetUuid = null;
                OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
                if (off != null && off.getUniqueId() != null) targetUuid = off.getUniqueId();
                if (targetUuid == null) targetUuid = InventorySnapshotStorage.findSnapshotOwnerUuidByName(plugin, playerName);
                java.util.List<String> ids = targetUuid != null ? InventorySnapshotStorage.listInventoryIdsForPlayer(plugin, targetUuid) : java.util.Collections.emptyList();
                String prefix = args[1].toLowerCase(Locale.ROOT);
                java.util.List<String> filtered = new java.util.ArrayList<>();
                for (String id : ids) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) filtered.add(id);
                }
                return filtered;
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }
}