package de.zfzfg.core.reward;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Plans all quantities against one storage snapshot before changing an inventory. Main thread only. */
public final class StakeService {
    private StakeService() {}

    public static List<ItemStack> take(Inventory inventory, List<ItemStack> offered) {
        ItemStack[] original = inventory.getStorageContents();
        ItemStack[] remaining = Arrays.stream(original).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack requested : offered) {
            if (requested == null || requested.getType().isAir() || requested.getAmount() <= 0)
                throw new IllegalArgumentException("Invalid stake item");
            int needed = requested.getAmount();
            for (int slot = 0; slot < remaining.length && needed > 0; slot++) {
                ItemStack stack = remaining[slot];
                if (stack == null || !stack.isSimilar(requested)) continue;
                int amount = Math.min(needed, stack.getAmount());
                needed -= amount;
                stack.setAmount(stack.getAmount() - amount);
                if (stack.getAmount() == 0) remaining[slot] = null;
            }
            if (needed != 0) throw new IllegalArgumentException("Insufficient stake items");
            taken.add(requested.clone());
        }
        inventory.setStorageContents(remaining);
        return taken;
    }
}
