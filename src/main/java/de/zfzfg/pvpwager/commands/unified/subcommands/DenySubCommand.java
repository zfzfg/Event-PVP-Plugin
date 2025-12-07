package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class DenySubCommand extends SubCommand {
    public DenySubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "deny"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp deny [player]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[0]));
                return true;
            }
            de.zfzfg.pvpwager.models.CommandRequest req = plugin.getCommandRequestManager().getRequest(target, player);
            if (req != null) {
                plugin.getCommandRequestManager().removeRequest(target);
            }
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.deny.success-self", "player", target.getName()));
            MessageUtil.sendMessage(target, cfg.getMessage("messages.command.pvp.deny.success-other", "player", player.getName()));
        } else {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.deny.usage"));
        }
        return true;
    }
}