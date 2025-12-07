package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.core.util.Time;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class SpectateSubCommand extends SubCommand {
    public SpectateSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "spectate"; }

    @Override
    public String getPermission() { return "pvpwager.spectate"; }

    @Override
    public String getUsage() { return "/pvp spectate <player>"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.spectate.usage"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[0]));
            return true;
        }
        Match match = plugin.getMatchManager().getMatchByPlayer(target);
        if (match == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.spectate.no-match", "player", target.getName()));
            return true;
        }
        if (match.getSpectators().size() >= plugin.getPvpConfigManager().getConfig().getInt("max-spectators", 10)) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.match-full"));
            return true;
        }
        plugin.getMatchManager().addSpectator(match, player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.teleport(match.getArena().getSpectatorSpawn());
            player.setGameMode(GameMode.SPECTATOR);
            plugin.getMatchManager().markTeleported(player);
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.spectate.joined", "p1", match.getPlayer1().getName(), "p2", match.getPlayer2().getName()));
            match.broadcast(cfg.getMessage("messages.command.pvp.spectate.announce", "player", player.getName()));
        }, Time.TICKS_PER_SECOND);
        return true;
    }
}