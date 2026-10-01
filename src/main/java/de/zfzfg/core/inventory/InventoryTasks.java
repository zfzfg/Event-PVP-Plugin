package de.zfzfg.core.inventory;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Explicit dispatch, including exceptional and late future callbacks. */
public final class InventoryTasks {
    private InventoryTasks() {}

    public static Executor executor(Plugin plugin) {
        return task -> {
            if (!plugin.isEnabled()) {
                throw new RejectedExecutionException("Inventory integration is stopping");
            }
            if (Bukkit.isPrimaryThread()) task.run();
            else Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()) task.run();
            });
        };
    }
}
