package de.zfzfg.pvpwager.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ItemBuilder {
    private final ItemStack item;
    
    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }
    
    public ItemBuilder(ItemStack item) {
        this.item = item;
    }
    
    public ItemBuilder setName(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            item.setItemMeta(meta);
        }
        return this;
    }
    
    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }
    
    public ItemBuilder setLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> coloredLore = lore.stream().map(MessageUtil::color).toList();
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return this;
    }
    
    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        item.addEnchantment(enchantment, level);
        return this;
    }
    
    public ItemBuilder addUnsafeEnchantment(Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return this;
    }
    
    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(flags);
            item.setItemMeta(meta);
        }
        return this;
    }
    
    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }
    
    public ItemStack build() {
        return item;
    }
}