package de.zfzfg.core.reward;

import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static de.zfzfg.core.reward.PendingPayoutStore.PayoutResult.*;

class PayoutRecoveryTest {
    @TempDir Path directory;
    EventPlugin plugin; Economy economy; PendingPayoutStore store; PlayerMock player;
    @BeforeEach void setup() {
        var server = MockBukkit.mock(); player = server.addPlayer();
        plugin = mock(EventPlugin.class); economy = mock(Economy.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("payout-test"));
        when(plugin.hasEconomy()).thenReturn(true); when(plugin.getEconomy()).thenReturn(economy);
        store = new PendingPayoutStore(plugin); store.load();
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    EconomyResponse response(boolean ok) { return new EconomyResponse(10, 100, ok ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "test"); }
    PendingPayoutStore.PayoutResult submit(String id, List<ItemStack> items, boolean ready) {
        return store.submit(id, player.getUniqueId(), player, items, 10, "test", ready, List.of("source:"+id), List.of());
    }
    @Test void rejectedDepositRemainsAndRetriesOnce() {
        when(economy.depositPlayer(player, 10)).thenReturn(response(false), response(true));
        assertEquals(QUEUED_DURABLE, submit("one", List.of(), true));
        assertTrue(store.hasPending(player.getUniqueId()));
        assertEquals(1, store.deliverAll(player)); assertEquals(0, store.deliverAll(player));
        verify(economy, times(2)).depositPlayer(player, 10);
    }
    @Test void moneyFailureDoesNotRepeatItems() {
        when(economy.depositPlayer(player, 10)).thenReturn(response(false), response(true));
        submit("mixed", List.of(new ItemStack(Material.DIAMOND, 8)), true);
        store.deliverAll(player);
        assertEquals(8, player.getInventory().getItem(0).getAmount());
    }
    @Test void missingEconomyPreservesClaimAcrossRestart() {
        when(plugin.hasEconomy()).thenReturn(false);
        assertEquals(QUEUED_DURABLE, submit("offline-economy", List.of(), true));
        store = new PendingPayoutStore(plugin); store.load();
        assertTrue(store.hasPending(player.getUniqueId()));
        verifyNoInteractions(economy);
    }
    @Test void exceptionRequiresReviewAndDoesNotDeleteFollowingEntries() {
        when(economy.depositPlayer(player, 10)).thenThrow(new IllegalStateException("uncertain"));
        submit("one", List.of(), false); submit("two", List.of(), false);
        store.deliverAll(player);
        assertEquals(2, store.snapshot().get(player.getUniqueId()).size());
        store.deliverAll(player); verify(economy, times(2)).depositPlayer(player, 10);
        assertEquals(2L, store.status().get("review"));
    }
    @Test void interruptedEffectIsNotAutomaticallyRepeated() throws Exception {
        submit("interrupted", List.of(), false);
        Path file = directory.resolve("pending-payouts.yml");
        Files.writeString(file, Files.readString(file).replace("moneyState: PENDING", "moneyState: IN_FLIGHT"));
        store = new PendingPayoutStore(plugin); store.load(); store.deliverAll(player);
        verifyNoInteractions(economy); assertEquals(1L, store.status().get("review"));
        assertTrue(store.resolve("interrupted", true, "test-admin"));
        assertFalse(store.hasPending(player.getUniqueId()));
    }
    @Test void completedReceiptPreventsSecondClaimAfterRestart() {
        when(economy.depositPlayer(player, 10)).thenReturn(response(true));
        assertEquals(DELIVERED, submit("once", List.of(), true));
        store = new PendingPayoutStore(plugin); store.load();
        assertEquals(DELIVERED, submit("once", List.of(), true));
        assertTrue(store.ownsSource("source:once")); verify(economy, times(1)).depositPlayer(player, 10);
    }
    @Test void fullInventoryKeepsItemsUntilSpaceExists() {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        when(economy.depositPlayer(player, 10)).thenReturn(response(true));
        assertEquals(QUEUED_DURABLE, submit("items", List.of(new ItemStack(Material.DIAMOND, 8)), true));
        player.getInventory().setItem(0, null); assertEquals(1, store.deliverAll(player));
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
        verify(economy, times(1)).depositPlayer(player, 10);
    }
    @Test void failedWriteNeverTriggersExternalEffect() throws Exception {
        Files.createDirectory(directory.resolve("pending-payouts.yml"));
        assertEquals(FAILED, submit("blocked", List.of(), true)); verifyNoInteractions(economy);
    }
    @Test void aggregateItemCheckDoesNotPartiallyRemove() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 8));
        assertThrows(IllegalArgumentException.class, () -> StakeService.take(player.getInventory(), List.of(new ItemStack(Material.DIAMOND, 8), new ItemStack(Material.DIAMOND, 8))));
        assertEquals(8, player.getInventory().getItem(0).getAmount());
    }
    @Test void extractionLeavesUnstakedStack() {
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 8));
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 8));
        assertEquals(8, StakeService.take(player.getInventory(), List.of(new ItemStack(Material.DIAMOND, 8))).get(0).getAmount());
        assertEquals(8, player.getInventory().getItem(1).getAmount());
    }
}
