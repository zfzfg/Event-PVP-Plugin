package de.zfzfg.eventplugin.integration;

public class NoOpExternalDisplayBridge implements ExternalDisplayBridge {
    @Override
    public void markDirty() {
        // no-op
    }

    @Override
    public void refreshPvpBoard() {
        // no-op
    }

    @Override
    public void refreshEventBoard() {
        // no-op
    }

    @Override
    public void shutdown() {
        // no-op
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
