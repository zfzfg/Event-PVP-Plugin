package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.core.util.Time;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class DrawSubCommand extends SubCommand {
    public DrawSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "draw"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp draw [accept|deny]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match == null) {
            MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessages().getString("messages.error.not-in-match"));
            return true;
        }
        if (match.getState() != MatchState.FIGHTING) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.only-active"));
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("accept")) {
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.no-active-vote"));
                    return true;
                }
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.initiator-wait"));
                    return true;
                }
                broadcastLines(match, cfg.getMessage("messages.command.draw.accepted"));
                plugin.getMatchManager().endMatch(match, null, true);
                return true;
            }
            if (args[0].equalsIgnoreCase("deny")) {
                if (!match.isDrawVoteActive()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.no-active-vote"));
                    return true;
                }
                if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.initiator-cannot-deny"));
                    return true;
                }
                match.setDrawVoteActive(false);
                match.setDrawVoteInitiator(null);
                broadcastLines(match, cfg.getMessage("messages.command.draw.denied", "player", player.getName()));
                return true;
            }
        }

        if (match.isDrawVoteActive()) {
            if (match.getDrawVoteInitiator().equals(player.getUniqueId())) {
                match.cancelDrawVoteIfInitiator(player.getUniqueId());
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.cancelled-self"));
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    MessageUtil.sendMessage(opponent, cfg.getMessage("messages.command.draw.cancelled-opponent", "player", player.getName()));
                }
                return true;
            } else {
                Player opponent = match.getOpponent(player);
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.already-active", "opponent", opponent != null ? opponent.getName() : "Opponent"));
                return true;
            }
        }

        if (!match.tryActivateDrawVote(player.getUniqueId())) {
            Player opponent = match.getOpponent(player);
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.draw.already-active", "opponent", opponent != null ? opponent.getName() : "Opponent"));
            return true;
        }
        Player opponent = match.getOpponent(player);
        int drawVoteTime = plugin.getPvpConfigManager().getConfig().getInt("settings.match.draw-vote-time", 30);
        broadcastLines(match, cfg.getMessage(
            "messages.command.draw.started",
            "initiator", player.getName(),
            "opponent", opponent != null ? opponent.getName() : "Opponent",
            "time", String.valueOf(drawVoteTime)
        ));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (match.cancelDrawVoteIfInitiator(player.getUniqueId())) {
                broadcastLines(match, cfg.getMessage("messages.command.draw.expired"));
            }
        }, Time.seconds(drawVoteTime));

        return true;
    }

        private void broadcastLines(Match match, String message) {
            String[] lines = message.split("\\n", -1);
            for (String line : lines) {
                match.broadcast(line);
            }
        }
}