package de.zfzfg.core.commands;

import org.bukkit.command.CommandSender;
import de.zfzfg.eventplugin.EventPlugin;

import java.util.Collections;
import java.util.List;

public abstract class SubCommand {
    protected final EventPlugin plugin;

    public SubCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract String getName();
    public abstract String getPermission();
    public abstract String getUsage();
    public List<String> getAliases() { return Collections.emptyList(); }

    public abstract boolean execute(CommandSender sender, String[] args);
    public List<String> tabComplete(CommandSender sender, String[] args) { return Collections.emptyList(); }
}