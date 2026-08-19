package de.zfzfg.pvpwager.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryUtilTest {

    private ItemStack mockItem(Material type, int amount, int maxStackSize) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(amount);
        when(item.getMaxStackSize()).thenReturn(maxStackSize);
        return item;
    }

    @Test
    @DisplayName("getNonEmptyItems filters out AIR and null items")
    void testGetNonEmptyItems() {
        Inventory inv = mock(Inventory.class);
        ItemStack sword = mockItem(Material.DIAMOND_SWORD, 1, 1);
        ItemStack air = mockItem(Material.AIR, 0, 0);
        ItemStack apple = mockItem(Material.APPLE, 5, 64);

        ItemStack[] contents = new ItemStack[]{sword, null, air, apple, null};
        when(inv.getContents()).thenReturn(contents);

        List<ItemStack> result = InventoryUtil.getNonEmptyItems(inv);
        assertThat(result).containsExactly(sword, apple);

        assertThat(InventoryUtil.getNonEmptyItems(null)).isEmpty();
    }

    @Test
    @DisplayName("hasSpaceForItems checks empty slot count")
    void testHasSpaceForItems() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);

        ItemStack sword = mockItem(Material.DIAMOND_SWORD, 1, 1);
        ItemStack air = mockItem(Material.AIR, 0, 0);

        // 2 empty slots (1 null, 1 AIR)
        ItemStack[] contents = new ItemStack[]{sword, null, air};
        when(inv.getContents()).thenReturn(contents);

        // Asking for 2 items -> fits
        assertThat(InventoryUtil.hasSpaceForItems(player, List.of(sword, sword))).isTrue();

        // Asking for 3 items -> does not fit
        assertThat(InventoryUtil.hasSpaceForItems(player, List.of(sword, sword, sword))).isFalse();

        // Null player or items
        assertThat(InventoryUtil.hasSpaceForItems(null, List.of(sword))).isFalse();
        assertThat(InventoryUtil.hasSpaceForItems(player, null)).isFalse();
    }

    @Test
    @DisplayName("canFitItems checks stack consolidation and empty slots")
    void testCanFitItems() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);

        // Existing stack of 32 arrows (max stack size 64)
        ItemStack arrows32 = mockItem(Material.ARROW, 32, 64);
        ItemStack empty = mockItem(Material.AIR, 0, 0);

        ItemStack[] contents = new ItemStack[]{arrows32, empty};
        when(inv.getContents()).thenReturn(contents);

        // Adding 16 arrows -> fits in existing stack
        assertThat(InventoryUtil.canFitItems(player, List.of(mockItem(Material.ARROW, 16, 64)))).isTrue();

        // Adding 32 arrows -> fills the stack to 64
        assertThat(InventoryUtil.canFitItems(player, List.of(mockItem(Material.ARROW, 32, 64)))).isTrue();

        // Adding 64 arrows -> 32 fit in existing, 32 fit in empty slot
        assertThat(InventoryUtil.canFitItems(player, List.of(mockItem(Material.ARROW, 64, 64)))).isTrue();

        // Adding 100 arrows -> 32 in existing stack, 64 in empty slot = 96 capacity -> 100 exceeds capacity
        assertThat(InventoryUtil.canFitItems(player, List.of(mockItem(Material.ARROW, 100, 64)))).isFalse();
    }
}
