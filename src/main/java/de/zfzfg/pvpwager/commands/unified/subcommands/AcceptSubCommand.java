package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class AcceptSubCommand extends SubCommand {
    public AcceptSubCommand(EventPlugin plugin) { super(plugin); }

    @Override
    public String getName() { return "accept"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp accept [player]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (args.length >= 1) {
            Player challenger = Bukkit.getPlayer(args[0]);
            if (challenger == null || !challenger.isOnline()) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[0]));
                return true;
            }
            de.zfzfg.pvpwager.models.CommandRequest request = plugin.getCommandRequestManager().getRequest(challenger, player);
            if (request == null) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.no-request", "player", challenger.getName()));
                return true;
            }

            boolean isSkip = request.getMoney() == 0.0 && (request.getWagerItems() == null || request.getWagerItems().isEmpty());
            if (!isSkip) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.requires-skip"));
                return true;
            }

            // Start direct match using full request details (arena + equipment)
            plugin.getCommandRequestManager().removeRequest(challenger);
            plugin.getMatchManager().startMatchFromCommand(request);
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.success-self", "player", challenger.getName()));
            MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvp.accept.success-other", "player", player.getName()));
        } else {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.help"));
        }
        return true;
    }
}