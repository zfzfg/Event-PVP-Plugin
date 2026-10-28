package de.zfzfg.pvpwager.managers;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.reward.*;
import de.zfzfg.pvpwager.models.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StakePreparationTest {
    @TempDir Path directory;
    EventPlugin plugin; Economy economy; PlayerMock first, second; MatchManager manager;
    EquipmentManager equipment; CommandRequest request; PendingPayoutStore payouts;
    @BeforeEach void setup() {
        var server = MockBukkit.mock(); first = server.addPlayer(); second = server.addPlayer();
        plugin = mock(EventPlugin.class); economy = mock(Economy.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(Logger.getLogger("stake-test"));
        when(plugin.hasEconomy()).thenReturn(true); when(plugin.getEconomy()).thenReturn(economy);
        var config = mock(ConfigManager.class); when(config.getConfig()).thenReturn(new YamlConfiguration()); when(plugin.getPvpConfigManager()).thenReturn(config);
        var arenas = mock(ArenaManager.class); when(plugin.getArenaManager()).thenReturn(arenas);
        when(arenas.getArenaOptional("arena")).thenReturn(Optional.of(mock(Arena.class)));
        equipment = mock(EquipmentManager.class); when(plugin.getEquipmentManager()).thenReturn(equipment);
        when(equipment.getEquipmentSet("kit")).thenReturn(mock(EquipmentSet.class));
        payouts = new PendingPayoutStore(plugin); payouts.load(); when(plugin.getPendingPayouts()).thenReturn(payouts);
        manager = new MatchManager(plugin);
        request = new CommandRequest(first, second); request.setFinalArenaId("arena"); request.setFinalEquipmentId("kit");
        request.setWagerItems(List.of(new ItemStack(Material.DIAMOND, 8))); request.setTargetWagerItems(List.of());
        request.setMoney(25); request.setTargetWagerMoney(25);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    EconomyResponse response(boolean ok) { return new EconomyResponse(25, 100, ok ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "test"); }
    @Test void failedSecondDebitRefundsOnlyConfirmedFirstDebit() {
        first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 16));
        when(economy.withdrawPlayer(first,25)).thenReturn(response(true)); when(economy.withdrawPlayer(second,25)).thenReturn(response(false));
        when(economy.depositPlayer(first,25)).thenReturn(response(true));
        manager.startMatchFromCommand(request);
        assertEquals(0, manager.getActiveMatchCount());
        assertEquals(16, first.getInventory().getItem(0).getAmount());
        verify(economy).depositPlayer(first,25); verify(economy,never()).depositPlayer(second,25);
    }
    @Test void heldTradeDoesNotRemoveASecondStack() {
        first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 8));
        when(economy.withdrawPlayer(first,25)).thenReturn(response(false));
        assertEquals(MatchStartResult.RETURNED_OR_DURABLY_QUEUED, manager.startHeldTrade(request));
        assertEquals(16, first.getInventory().getItem(0).getAmount());
        verify(economy,never()).depositPlayer(first,25);
    }
    @Test void missingKitRejectsBeforeAnyInventoryOrEconomyMutation() {
        when(equipment.getEquipmentSet("kit")).thenReturn(null);
        first.getInventory().setItem(0, new ItemStack(Material.DIAMOND,16));
        assertEquals(MatchStartResult.REJECTED_CALLER_OWNS_STAKE, manager.startHeldTrade(request));
        assertEquals(16, first.getInventory().getItem(0).getAmount()); verifyNoInteractions(economy);
    }
    @Test void nonFiniteMoneyRejectsBeforeMutation() {
        request.setMoney(Double.NaN);
        assertEquals(MatchStartResult.REJECTED_CALLER_OWNS_STAKE, manager.startHeldTrade(request)); verifyNoInteractions(economy);
    }
    @Test void overflowingCombinedPotRejectsBeforeMutation() {
        plugin.getPvpConfigManager().getConfig().set("settings.checks.max-bet-money", 0);
        request.setMoney(Double.MAX_VALUE); request.setTargetWagerMoney(Double.MAX_VALUE);
        assertEquals(MatchStartResult.REJECTED_CALLER_OWNS_STAKE, manager.startHeldTrade(request));
        verifyNoInteractions(economy);
    }
    @Test void uncertainDebitRemainsReviewableWithoutAutomaticCompensation() {
        when(economy.withdrawPlayer(first,25)).thenThrow(new IllegalStateException("provider disconnected"));
        manager.startHeldTrade(request);
        assertEquals(1L, payouts.status().get("review")); verify(economy,never()).depositPlayer(first,25);
    }
}
