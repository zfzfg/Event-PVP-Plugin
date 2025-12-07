package de.zfzfg.pvpwager.commands.unified;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.core.commands.SmartTabCompleter;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PvPUnifiedCommand implements CommandExecutor, TabCompleter, Listener {
    private final EventPlugin plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final SmartTabCompleter smart;

    public PvPUnifiedCommand(EventPlugin plugin) {
        this.plugin = plugin;
        this.smart = new SmartTabCompleter(plugin);
        // Register subcommands
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.ChallengeSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.AcceptSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.DenySubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.SpectateSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.LeaveSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.SurrenderSubCommand(plugin));
        register(new de.zfzfg.pvpwager.commands.unified.subcommands.DrawSubCommand(plugin));
    }
    
    private String getMsg(String key) {
        String msg = plugin.getCoreConfigManager().getMessages().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private void register(SubCommand cmd) {
        subCommands.put(cmd.getName().toLowerCase(), cmd);
        for (String alias : cmd.getAliases()) {
            subCommands.put(alias.toLowerCase(), cmd);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String name = args[0].toLowerCase();
        SubCommand sub = subCommands.get(name);
        if (sub == null) {
            sender.sendMessage(getMsg("system.unknown-subcommand").replace("{command}", args[0]));
            sendHelp(sender);
            return true;
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return smart.filterStartsWith(new ArrayList<>(List.of(
                    "challenge", "accept", "deny", "spectate", "leave", "surrender", "draw"
            )), args[0]);
        }
        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub != null) {
            return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(getMsg("pvp-help.header"));
        sender.sendMessage(getMsg("pvp-help.title"));
        sender.sendMessage(getMsg("pvp-help.header"));
        sender.sendMessage(getMsg("pvp-help.usage"));
        sender.sendMessage(getMsg("pvp-help.commands.challenge"));
        sender.sendMessage(getMsg("pvp-help.wager-info"));
        sender.sendMessage(getMsg("pvp-help.commands.accept"));
        sender.sendMessage(getMsg("pvp-help.commands.deny"));
        sender.sendMessage(getMsg("pvp-help.commands.spectate"));
        sender.sendMessage(getMsg("pvp-help.commands.leave"));
        sender.sendMessage(getMsg("pvp-help.commands.surrender"));
        sender.sendMessage(getMsg("pvp-help.commands.draw"));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // Clear any surrender confirmation in unified surrender subcommand
        for (SubCommand sub : subCommands.values()) {
            if (sub instanceof de.zfzfg.pvpwager.commands.unified.subcommands.SurrenderSubCommand) {
                ((de.zfzfg.pvpwager.commands.unified.subcommands.SurrenderSubCommand) sub).clearConfirmation(playerId);
            }
        }
    }
}