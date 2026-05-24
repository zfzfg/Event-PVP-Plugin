package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.StatsManager;
import de.zfzfg.pvpwager.models.PlayerStats;
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
import java.util.Optional;

public class PvPStatsCommand implements CommandExecutor, TabCompleter {
    private final EventPlugin plugin;

    public PvPStatsCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StatsManager stats = plugin.getStatsManager();

        if (args.length == 0) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("pvpstats.usage"));
            if (sender.hasPermission("eventpvp.admin")) {
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("pvpstats.admin-usage"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "me":
                if (!(sender instanceof Player)) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.me-player-only"));
                    return true;
                }
                Player player = (Player) sender;
                Optional<PlayerStats> myStatsOpt = stats.getStats(player.getUniqueId());
                if (myStatsOpt.isEmpty()) {
                    MessageUtil.sendMessage(player, plugin.getConfigManager().getMessage("pvpstats.me-empty"));
                } else {
                    PlayerStats s = myStatsOpt.get();
                    MessageUtil.sendMessages(player, java.util.Arrays.asList(
                            plugin.getConfigManager().getMessage("pvpstats.me-header"),
                            plugin.getConfigManager().getMessage("pvpstats.me-wins", "wins", String.valueOf(s.getWins())),
                            plugin.getConfigManager().getMessage("pvpstats.me-losses", "losses", String.valueOf(s.getLosses())),
                            plugin.getConfigManager().getMessage("pvpstats.me-draws", "draws", String.valueOf(s.getDraws()))
                    ));
                }
                return true;

            case "top":
                int n = 10;
                if (args.length >= 2) {
                    try {
                        n = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {}
                }
                List<PlayerStats> top = stats.getTopByWins(n);
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("pvpstats.top-header"));
                int rank = 1;
                for (PlayerStats s : top) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(s.getPlayerId());
                    String name = op != null && op.getName() != null ? op.getName() : s.getPlayerId().toString().substring(0, 8);
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage(
                            "pvpstats.top-line",
                            "rank", String.valueOf(rank),
                            "name", name,
                            "wins", String.valueOf(s.getWins()),
                            "losses", String.valueOf(s.getLosses()),
                            "draws", String.valueOf(s.getDraws())
                    ));
                    rank++;
                }
                if (top.isEmpty()) {
                    MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("pvpstats.top-empty"));
                }
                return true;

            case "reset":
                if (!sender.hasPermission("eventpvp.admin")) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.reset-usage"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                java.util.UUID id = op != null ? op.getUniqueId() : null;
                if (id == null) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.player-not-found")); return true; }
                plugin.getStatsManager().reset(id);
                de.zfzfg.pvpwager.storage.PvpStatsStorage.saveAsync(plugin, plugin.getStatsManager().toMap());
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("pvpstats.reset-success", "name", (op.getName() != null ? op.getName() : String.valueOf(id))));
                return true;

            case "add":
                if (!sender.hasPermission("eventpvp.admin")) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.no-permission"));
                    return true;
                }
                if (args.length < 4) {
                    MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.add-usage"));
                    return true;
                }
                OfflinePlayer opAdd = Bukkit.getOfflinePlayer(args[1]);
                java.util.UUID idAdd = opAdd != null ? opAdd.getUniqueId() : null;
                if (idAdd == null) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.player-not-found")); return true; }
                String field = args[2].toLowerCase();
                int amount;
                try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.invalid-number")); return true; }
                if (amount <= 0) { MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.invalid-amount")); return true; }
                switch (field) {
                    case "wins": plugin.getStatsManager().addWins(idAdd, amount); break;
                    case "losses": plugin.getStatsManager().addLosses(idAdd, amount); break;
                    case "draws": plugin.getStatsManager().addDraws(idAdd, amount); break;
                    default: MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.invalid-field")); return true;
                }
                de.zfzfg.pvpwager.storage.PvpStatsStorage.saveAsync(plugin, plugin.getStatsManager().toMap());
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getMessage("pvpstats.add-success", "name", (opAdd.getName() != null ? opAdd.getName() : String.valueOf(idAdd))));
                return true;

            default:
                MessageUtil.error(sender, plugin.getConfigManager().getMessage("pvpstats.unknown-subcommand"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("me");
            result.add("top");
            if (sender.hasPermission("eventpvp.admin")) {
                result.add("reset");
                result.add("add");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            result.add("5");
            result.add("10");
            result.add("20");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("add"))) {
            result.addAll(org.bukkit.Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            result.add("wins"); result.add("losses"); result.add("draws");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            result.add("1"); result.add("5"); result.add("10");
        }
        return result;
    }
}