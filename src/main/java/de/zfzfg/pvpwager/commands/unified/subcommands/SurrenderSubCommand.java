package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.pvpwager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurrenderSubCommand extends SubCommand {
    private final Map<UUID, Long> confirmations = new HashMap<>();
    private static final long TIMEOUT_MS = 10000L;

    public SurrenderSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "surrender"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp surrender [confirm]"; }

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
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.surrender.only-active"));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            confirmations.put(player.getUniqueId(), System.currentTimeMillis());
            MessageUtil.sendMessage(player, cfg.getMessage(
                "messages.command.surrender.confirm",
                "timeout", String.valueOf(TIMEOUT_MS / 1000)
            ));
            return true;
        }
        Long confirmTime = confirmations.get(player.getUniqueId());
        if (confirmTime == null || System.currentTimeMillis() - confirmTime > TIMEOUT_MS) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.surrender.confirm-expired"));
            confirmations.remove(player.getUniqueId());
            return true;
        }
        confirmations.remove(player.getUniqueId());
        Player opponent = match.getOpponent(player);
        broadcastLines(match, cfg.getMessage(
            "messages.command.surrender.surrendered",
            "player", player.getName(),
            "opponent", opponent != null ? opponent.getName() : "Opponent"
        ));
        plugin.getMatchManager().endMatch(match, opponent, false);
        return true;
    }

    public void clearConfirmation(UUID playerId) {
        confirmations.remove(playerId);
    }

    private void broadcastLines(Match match, String message) {
        String[] lines = message.split("\\n", -1);
        for (String line : lines) {
            match.broadcast(line);
        }
    }
}