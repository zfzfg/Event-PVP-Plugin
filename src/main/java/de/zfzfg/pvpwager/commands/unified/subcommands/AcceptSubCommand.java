package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.gui.livetrade.LiveTradeBridge;
import de.zfzfg.pvpwager.managers.ConfigManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }

        Player challenger = null;
        if (args.length >= 1) {
            challenger = Bukkit.getPlayer(args[0]);
            if (challenger == null || !challenger.isOnline()) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[0]));
                return true;
            }
        }

        // 1. Prüfe CommandRequestManager
        CommandRequest request = challenger != null
                ? plugin.getCommandRequestManager().getRequest(challenger, player)
                : plugin.getCommandRequestManager().getRequestToPlayer(player);

        if (request != null) {
            challenger = request.getSender();
            boolean isSkip = request.getMoney() == 0.0 && (request.getWagerItems() == null || request.getWagerItems().isEmpty());
            if (isSkip) {
                plugin.getCommandRequestManager().removeRequest(challenger);
                plugin.getMatchManager().startMatchFromCommand(request);
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.success-self", "player", challenger.getName()));
                MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvp.accept.success-other", "player", player.getName()));
            } else {
                // Bei Wetteinsätzen direkt die synchrone LiveTradeSession öffnen
                boolean started = new LiveTradeBridge(plugin).startSessionFromRequest(request);
                if (!started) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.session-start-failed"));
                }
            }
            return true;
        }

        // 2. Prüfe RequestManager (Freundschafts- / Sofort-Anfragen)
        if (challenger != null) {
            if (plugin.getRequestManager().acceptRequest(player, challenger)) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.success-self", "player", challenger.getName()));
                MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvp.accept.success-other", "player", player.getName()));
                return true;
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player) && plugin.getRequestManager().acceptRequest(player, p)) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.success-self", "player", p.getName()));
                    MessageUtil.sendMessage(p, cfg.getMessage("messages.command.pvp.accept.success-other", "player", player.getName()));
                    return true;
                }
            }
        }

        // 3. Prüfe PvPWagerGuiCommand (/pvpask Anfragen)
        if (plugin.getPvpWagerGuiCommand() != null) {
            if (challenger != null) {
                if (plugin.getPvpWagerGuiCommand().acceptWagerRequest(player, challenger)) {
                    return true;
                }
            } else {
                UUID senderId = plugin.getPvpWagerGuiCommand().getWagerRequestSender(player);
                if (senderId != null) {
                    Player senderPlayer = Bukkit.getPlayer(senderId);
                    if (senderPlayer != null && senderPlayer.isOnline() && plugin.getPvpWagerGuiCommand().acceptWagerRequest(player, senderPlayer)) {
                        return true;
                    }
                }
            }
        }

        // Keine Anfrage gefunden
        if (challenger != null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.no-request", "player", challenger.getName()));
        } else {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.commands.pvpaccept.no-pending-requests"));
        }
        return true;
    }
}