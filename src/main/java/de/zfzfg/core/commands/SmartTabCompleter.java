package de.zfzfg.core.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class SmartTabCompleter {
    private final EventPlugin plugin;

    public SmartTabCompleter(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> completeOnlinePlayer(String partial) {
        String p = partial == null ? "" : partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(pl -> pl.getName())
                .filter(name -> name.toLowerCase().startsWith(p))
                .collect(Collectors.toList());
    }

    public List<String> completeArena(String partial) {
        ArenaManager arenaManager = plugin.getArenaManager();
        String p = partial == null ? "" : partial.toLowerCase();
        return arenaManager.getArenas().keySet().stream()
                .filter(id -> id.toLowerCase().startsWith(p))
                .collect(Collectors.toList());
    }

    public List<String> empty() { return List.of(); }

    public List<String> filterStartsWith(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }
}