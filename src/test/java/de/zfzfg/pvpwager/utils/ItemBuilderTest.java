package de.zfzfg.pvpwager.utils;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ItemBuilderTest {

    @Test
    @DisplayName("setAmount updates item amount")
    void testSetAmount() {
        ItemStack mockItem = mock(ItemStack.class);
        ItemBuilder builder = new ItemBuilder(mockItem);

        ItemStack result = builder.setAmount(10).build();

        assertThat(result).isSameAs(mockItem);
        verify(mockItem).setAmount(10);
    }

    @Test
    @DisplayName("addItemFlags updates meta and applies to item")
    void testAddItemFlags() {
        ItemStack mockItem = mock(ItemStack.class);
        ItemMeta mockMeta = mock(ItemMeta.class);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);

        ItemBuilder builder = new ItemBuilder(mockItem);
        ItemStack result = builder.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES).build();

        assertThat(result).isSameAs(mockItem);
        verify(mockMeta).addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        verify(mockItem).setItemMeta(mockMeta);
    }


    @Test
    @DisplayName("setName and setLore update meta")
    void testNameAndLore() {
        ItemStack mockItem = mock(ItemStack.class);
        ItemMeta mockMeta = mock(ItemMeta.class);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);

        ItemBuilder builder = new ItemBuilder(mockItem);
        builder.setName("Custom Sword");
        builder.setLore("Line 1", "Line 2");

        verify(mockItem, atLeastOnce()).setItemMeta(mockMeta);
    }
}
