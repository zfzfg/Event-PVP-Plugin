package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.ConfigManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }

        Player target = null;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
        }

        boolean found = false;

        // 1. CommandRequestManager
        CommandRequest req = target != null
                ? plugin.getCommandRequestManager().getRequest(target, player)
                : plugin.getCommandRequestManager().getRequestToPlayer(player);
        if (req != null) {
            target = req.getSender();
            plugin.getCommandRequestManager().removeRequest(target);
            found = true;
        }

        // 2. RequestManager
        if (target != null) {
            plugin.getRequestManager().cancelRequest(target.getUniqueId(), player.getUniqueId());
            found = true;
        }

        // 3. PvPWagerGuiCommand
        if (plugin.getPvpWagerGuiCommand() != null) {
            if (target != null) {
                plugin.getPvpWagerGuiCommand().denyWagerRequest(player, target);
                found = true;
            } else {
                UUID sId = plugin.getPvpWagerGuiCommand().getWagerRequestSender(player);
                if (sId != null) {
                    plugin.getPvpWagerGuiCommand().cancelWagerRequest(sId);
                    found = true;
                }
            }
        }

        if (target != null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.deny.success-self", "player", target.getName()));
            if (target.isOnline()) {
                MessageUtil.sendMessage(target, cfg.getMessage("messages.command.pvp.deny.success-other", "player", player.getName()));
            }
        } else if (found) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.deny.request-cancelled"));
        } else {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.deny.no-request"));
        }
        return true;
    }
}