package de.zfzfg.eventplugin.integration;

import java.util.List;

public class CompositeExternalDisplayBridge implements ExternalDisplayBridge {
    private final List<ExternalDisplayBridge> bridges;

    public CompositeExternalDisplayBridge(List<ExternalDisplayBridge> bridges) {
        this.bridges = bridges;
    }

    @Override
    public void markDirty() {
        for (ExternalDisplayBridge bridge : bridges) {
            bridge.markDirty();
        }
    }

    @Override
    public void refreshPvpBoard() {
        for (ExternalDisplayBridge bridge : bridges) {
            bridge.refreshPvpBoard();
        }
    }

    @Override
    public void refreshEventBoard() {
        for (ExternalDisplayBridge bridge : bridges) {
            bridge.refreshEventBoard();
        }
    }

    @Override
    public void shutdown() {
        for (ExternalDisplayBridge bridge : bridges) {
            bridge.shutdown();
        }
    }

    @Override
    public boolean isActive() {
        for (ExternalDisplayBridge bridge : bridges) {
            if (bridge.isActive()) {
                return true;
            }
        }
        return false;
    }
}
