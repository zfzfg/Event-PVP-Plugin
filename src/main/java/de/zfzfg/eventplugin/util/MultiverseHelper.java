package de.zfzfg.eventplugin.util;

import de.zfzfg.eventplugin.EventPlugin;

/**
 * Wrapper um den zentralen MultiverseHelper, um bestehende Importe kompatibel zu halten.
 */
public class MultiverseHelper {
    private final de.zfzfg.core.world.MultiverseHelper core;

    public MultiverseHelper(EventPlugin plugin) {
        this.core = new de.zfzfg.core.world.MultiverseHelper(plugin);
    }

    public void loadWorld(String worldName, LoadCallback callback) {
        core.loadWorld(worldName, (success, message) -> {
            if (callback != null) callback.onResult(success, message);
        });
    }

    public boolean isMultiverseAvailable() { return core.isMultiverseAvailable(); }
    public void unloadWorld(String worldName) { core.unloadWorld(worldName); }
    public void regenerateWorld(String worldName) { core.regenerateWorld(worldName); }
    public void deleteWorld(String worldName, Runnable callback) { core.deleteWorld(worldName, callback); }
    public void cloneWorld(String sourceWorld, String targetWorld, Runnable callback) { core.cloneWorld(sourceWorld, targetWorld, callback); }

    public void ensureWorldReady(String worldName, String cloneSource, boolean regenerate, boolean backupEnabled, boolean backupAsync) {
        core.ensureWorldReady(worldName, cloneSource, regenerate, backupEnabled, backupAsync);
    }

    public void backupWorld(String worldName) { core.backupWorld(worldName); }

    public interface LoadCallback {
        void onResult(boolean success, String message);
    }
}
