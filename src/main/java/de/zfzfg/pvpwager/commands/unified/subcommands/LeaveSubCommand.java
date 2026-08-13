package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import de.zfzfg.core.util.Time;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class LeaveSubCommand extends SubCommand {
    public LeaveSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "leave"; }

    @Override
    public String getPermission() { return "pvpwager.spectate"; }

    @Override
    public String getUsage() { return "/pvp leave"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match == null || !match.getSpectators().contains(player.getUniqueId())) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.not-spectating"));
            return true;
        }
        Location originalLocation = match.getOriginalLocation(player);
        plugin.getMatchManager().removeSpectator(match, player);
        if (originalLocation != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(originalLocation);
                player.setGameMode(GameMode.SURVIVAL);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.leave.left"));
                match.broadcast(cfg.getMessage("messages.command.pvp.leave.announce", "player", player.getName()));
            }, Time.TICKS_PER_SECOND);
        }
        return true;
    }
}