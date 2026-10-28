package de.zfzfg.pvpwager.gui.livetrade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.reward.PendingPayoutStore;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.managers.CommandRequestManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;

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

    @TempDir Path directory;

    @Test void requestItemsAreRemovedAndReturnedOnlyOnce() { checkTransfer(false); }
    @Test void failedGuiInitializationReturnsActualStake() { checkTransfer(true); }

    private void checkTransfer(boolean failOpening) {
        var server = MockBukkit.mock();
        try {
            var first = server.addPlayer(); var second = server.addPlayer();
            var plugin = mock(EventPlugin.class);
            when(plugin.getDataFolder()).thenReturn(directory.toFile());
            when(plugin.getLogger()).thenReturn(Logger.getLogger("bridge-test"));
            var payouts = new PendingPayoutStore(plugin); payouts.load();
            when(plugin.getPendingPayouts()).thenReturn(payouts);
            var manager = mock(LiveTradeManager.class);
            when(plugin.getLiveTradeManager()).thenReturn(manager);
            when(plugin.getMatchManager()).thenReturn(mock(MatchManager.class));
            var requests = mock(CommandRequestManager.class);
            when(plugin.getCommandRequestManager()).thenReturn(requests);
            var session = mock(LiveTradeSession.class);
            when(session.getPlugin()).thenReturn(plugin);
            when(session.getSessionId()).thenReturn(UUID.randomUUID());
            var held = new LiveTradePlayer(session, first);
            when(session.getPlayer1()).thenReturn(held);
            when(manager.createSession(first,second)).thenReturn(session);
            doAnswer(call -> { held.returnItems(); return null; }).when(session).abort();
            if (failOpening) doThrow(new IllegalStateException("GUI unavailable")).when(session).start();
            first.getInventory().setItem(0, new ItemStack(Material.DIAMOND,16));
            var request = new CommandRequest(first,second);
            request.setWagerItems(List.of(new ItemStack(Material.DIAMOND,8)));
            assertEquals(!failOpening,new LiveTradeBridge(plugin).startSessionFromRequest(request));
            assertEquals(failOpening ? 16 : 8,first.getInventory().getItem(0).getAmount());
            if (!failOpening) assertEquals(8,held.getWagerItems().getFirst().getAmount());
            held.returnItems(); held.returnItems();
            assertEquals(16,first.getInventory().getItem(0).getAmount());
            verify(requests, times(failOpening ? 0 : 1)).removeRequest(first);
        } finally { MockBukkit.unmock(); }
    }
}
