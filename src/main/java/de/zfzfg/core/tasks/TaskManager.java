package de.zfzfg.core.tasks;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Zentraler Task-Manager als dünner Wrapper um den Bukkit-Scheduler.
 * Vereinheitlicht die Nutzung von Sync/Async/Delayed Tasks.
 */
public class TaskManager {

    private final EventPlugin plugin;

    public TaskManager(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask runLater(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public BukkitTask run(Runnable task) {
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    public BukkitTask runAsync(Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public BukkitTask runRepeating(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }
}