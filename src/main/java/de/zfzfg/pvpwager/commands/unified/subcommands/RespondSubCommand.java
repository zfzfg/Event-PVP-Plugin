package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.commands.PvPRespondCommand;
import org.bukkit.command.CommandSender;

import java.util.List;

public class RespondSubCommand extends SubCommand {
    private final PvPRespondCommand delegate;

    public RespondSubCommand(EventPlugin plugin) {
        super(plugin);
        this.delegate = new PvPRespondCommand(plugin);
    }

    @Override
    public String getName() {
        return "respond";
    }

    @Override
    public String getPermission() {
        return "pvpwager.respond";
    }

    @Override
    public String getUsage() {
        return "/pvp respond [gui|player]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return delegate.onCommand(sender, null, "respond", args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("gui");
        }
        return List.of();
    }
}
