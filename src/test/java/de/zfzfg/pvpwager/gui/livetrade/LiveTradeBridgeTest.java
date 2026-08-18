package de.zfzfg.pvpwager.gui.livetrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveTradeBridgeTest {

    @Test
    void nullRequestGibtFalseZurueck() {
        LiveTradeBridge bridge = new LiveTradeBridge(null);
        assertFalse(bridge.startSessionFromRequest(null));
    }

    @Test
    void nullSpielerGibtFalseZurueck() {
        LiveTradeBridge bridge = new LiveTradeBridge(null);
        assertFalse(bridge.startSession(null, null));
    }
}
