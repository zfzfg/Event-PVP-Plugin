package de.zfzfg.eventplugin.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.managers.EventStatsManager;
import de.zfzfg.eventplugin.models.EventStats;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class EventStatsCommand implements CommandExecutor, TabCompleter {
    private final EventPlugin plugin;

    public EventStatsCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EventStatsManager stats = plugin.getEventStatsManager();
        if (args.length == 0) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("eventstats.usage"));
            if (sender.hasPermission("eventpvp.admin")) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("eventstats.admin-usage"));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "me": {
                if (!(sender instanceof Player)) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.me-player-only"));
                    return true;
                }
                Player player = (Player) sender;
                Optional<EventStats> s = stats.getStats(player.getUniqueId());
                if (s.isEmpty() || (s.get().getWins() == 0 && s.get().getParticipations() == 0)) {
                    MessageUtil.sendMessage(player, plugin.getConfigManager().getMessage("eventstats.me-empty"));
                } else {
                    int wins = s.map(EventStats::getWins).orElse(0);
                    int parts = s.map(EventStats::getParticipations).orElse(0);
                    java.util.List<String> lines = java.util.Arrays.asList(
                            plugin.getConfigManager().getMessage("eventstats.me-header"),
                            plugin.getConfigManager().getMessage("eventstats.me-participations", "participations", String.valueOf(parts)),
                            plugin.getConfigManager().getMessage("eventstats.me-wins", "wins", String.valueOf(wins))
                    );
                    MessageUtil.sendMessages(player, lines);
                }
                return true;
            }
            case "top": {
                int n = 10;
                if (args.length >= 2) {
                    try { n = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
                }
                List<EventStats> top = stats.getTopByWins(n);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("eventstats.top-header"));
                int rank = 1;
                for (EventStats es : top) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(es.getPlayerId());
                    String name = (op != null && op.getName() != null) ? op.getName() : es.getPlayerId().toString();
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage(
                            "eventstats.top-line",
                            "rank", String.valueOf(rank),
                            "name", name,
                            "wins", String.valueOf(es.getWins()),
                            "participations", String.valueOf(es.getParticipations())
                    ));
                    rank++;
                }
                if (top.isEmpty()) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("eventstats.top-empty"));
                }
                return true;
            }
            case "reset": {
                if (!sender.hasPermission("eventpvp.admin")) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.reset-usage"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                UUID id = op != null ? op.getUniqueId() : null;
                if (id == null) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.player-not-found")); return true; }
                stats.reset(id);
                de.zfzfg.eventplugin.storage.EventStatsStorage.saveAsync(plugin, stats.toMap());
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("eventstats.reset-success", "name", (op.getName() != null ? op.getName() : String.valueOf(id))));
                return true;
            }
            case "add": {
                if (!sender.hasPermission("eventpvp.admin")) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.no-permission"));
                    return true;
                }
                if (args.length < 4) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.add-usage"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                UUID id = op != null ? op.getUniqueId() : null;
                if (id == null) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.player-not-found")); return true; }
                String field = args[2].toLowerCase(Locale.ROOT);
                int n;
                try { n = Integer.parseInt(args[3]); } catch (NumberFormatException e) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.invalid-number")); return true; }
                if (n <= 0) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.invalid-amount")); return true; }
                switch (field) {
                    case "wins": stats.addWins(id, n); break;
                    case "participations": stats.addParticipations(id, n); break;
                    default: MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.invalid-field")); return true;
                }
                de.zfzfg.eventplugin.storage.EventStatsStorage.saveAsync(plugin, stats.toMap());
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("eventstats.add-success", "name", (op.getName() != null ? op.getName() : String.valueOf(id))));
                return true;
            }
            default:
                MessageUtil.error(sender, plugin.getConfigManager().getMessage("eventstats.unknown-subcommand"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("me");
            list.add("top");
            if (sender.hasPermission("eventpvp.admin")) {
                list.add("reset");
                list.add("add");
            }
        } else if (args.length == 2 && ("reset".equalsIgnoreCase(args[0]) || "add".equalsIgnoreCase(args[0]))) {
            list.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        } else if (args.length == 3 && "add".equalsIgnoreCase(args[0])) {
            list.add("wins");
            list.add("participations");
        } else if (args.length == 4 && "add".equalsIgnoreCase(args[0])) {
            list.add("1"); list.add("5"); list.add("10");
        }
        return list;
    }
}