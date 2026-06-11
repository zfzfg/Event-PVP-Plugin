package de.zfzfg.eventplugin.integration;

public interface ExternalDisplayBridge {
    void markDirty();
    void refreshPvpBoard();
    void refreshEventBoard();
    void shutdown();
    boolean isActive();
}
