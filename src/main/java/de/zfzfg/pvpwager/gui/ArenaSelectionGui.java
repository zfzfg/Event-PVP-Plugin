package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI zur Auswahl der Arena für den Kampf.
 */
public class ArenaSelectionGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 53;
    
    // Startslot für Arena-Buttons
    private static final int ARENA_START_SLOT = 10;
    
    private final List<Arena> availableArenas = new ArrayList<>();
    
    public ArenaSelectionGui(EventPlugin plugin, Player player, WagerSession session) {
        super(plugin, player, session);
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE,
            MessageUtil.color("&6&lArena wählen"));
        
        loadArenas();
        buildLayout();
        openInventory();
    }
    
    private void loadArenas() {
        availableArenas.clear();
        Map<String, Arena> arenas = plugin.getArenaManager().getArenas();
        availableArenas.addAll(arenas.values());
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBorder(Material.GREEN_STAINED_GLASS_PANE);
        
        // Info
        inventory.setItem(INFO_SLOT, createInfoItem());
        
        // Arenas anzeigen
        displayArenas();
        
        // Navigation
        inventory.setItem(BACK_SLOT, createBackButton());
        
        // Confirm nur wenn Arena gewählt
        if (session.getSelectedArena() != null) {
            inventory.setItem(CONFIRM_SLOT, createConfirmButton());
        } else {
            inventory.setItem(CONFIRM_SLOT, createButton(Material.GRAY_WOOL,
                "&7Bestätigen",
                "&cWähle erst eine Arena!"));
        }
    }
    
    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Verfügbare Arenas: &e" + availableArenas.size()));
        lore.add("");
        
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color("&aAusgewählt:"));
            lore.add(MessageUtil.color("&f" + session.getSelectedArena().getDisplayName()));
        } else {
            lore.add(MessageUtil.color("&cKeine Arena ausgewählt"));
        }
        
        return createButton(Material.GRASS_BLOCK, "&e&lArena auswählen", lore);
    }
    
    private void displayArenas() {
        int slot = ARENA_START_SLOT;
        
        for (Arena arena : availableArenas) {
            // Überspringe Slots in den Rändern
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            
            if (slot >= SIZE - 9) break; // Nicht in letzte Reihe
            
            inventory.setItem(slot, createArenaItem(arena));
            slot++;
        }
        
        // Falls keine Arenas verfügbar
        if (availableArenas.isEmpty()) {
            inventory.setItem(22, createButton(Material.BARRIER,
                "&c&lKeine Arenas verfügbar!",
                "&7Bitte einen Admin kontaktieren."));
        }
    }
    
    private ItemStack createArenaItem(Arena arena) {
        boolean isSelected = session.getSelectedArena() != null && 
                            session.getSelectedArena().getId().equals(arena.getId());
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Welt: &f" + arena.getArenaWorld()));
        lore.add(MessageUtil.color("&7Spawn-Typ: &f" + arena.getSpawnType().name()));
        
        if (arena.getBoundaries() != null) {
            lore.add(MessageUtil.color("&7Grenzen: &aAktiv"));
        }
        
        lore.add("");
        
        if (isSelected) {
            lore.add(MessageUtil.color("&a✔ Ausgewählt!"));
        } else {
            lore.add(MessageUtil.color("&eKlicke zum Auswählen"));
        }
        
        // Material basierend auf Welt-Typ
        Material material = getMaterialForArena(arena);
        if (isSelected) {
            material = Material.EMERALD_BLOCK;
        }
        
        String prefix = isSelected ? "&a&l✔ " : "&e";
        return createButton(material, prefix + arena.getDisplayName(), lore);
    }
    
    private Material getMaterialForArena(Arena arena) {
        String world = arena.getArenaWorld().toLowerCase();
        
        if (world.contains("nether")) return Material.NETHERRACK;
        if (world.contains("end")) return Material.END_STONE;
        if (world.contains("desert") || world.contains("sand")) return Material.SAND;
        if (world.contains("ice") || world.contains("snow")) return Material.SNOW_BLOCK;
        if (world.contains("forest") || world.contains("jungle")) return Material.OAK_LEAVES;
        if (world.contains("ocean") || world.contains("water")) return Material.PRISMARINE;
        if (world.contains("mountain") || world.contains("cave")) return Material.STONE;
        if (world.contains("village") || world.contains("city")) return Material.COBBLESTONE;
        if (world.contains("arena") || world.contains("pvp")) return Material.IRON_BLOCK;
        
        return Material.GRASS_BLOCK;
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return true;
        
        // Navigation
        switch (slot) {
            case BACK_SLOT:
                playClickSound();
                switchTo(new WagerMainGui(plugin, player, session));
                return true;
            case CONFIRM_SLOT:
                if (session.getSelectedArena() != null) {
                    playSuccessSound();
                    switchTo(new WagerMainGui(plugin, player, session));
                } else {
                    playErrorSound();
                    MessageUtil.sendMessage(player, "&cWähle erst eine Arena!");
                }
                return true;
        }
        
        // Arena-Auswahl
        int index = getArenaIndexForSlot(slot);
        if (index >= 0 && index < availableArenas.size()) {
            Arena selected = availableArenas.get(index);
            session.setSelectedArena(selected);
            
            playClickSound();
            MessageUtil.sendMessage(player, "&aArena ausgewählt: &e" + selected.getDisplayName());
            
            // GUI neu aufbauen
            buildLayout();
        }
        
        return true;
    }
    
    private int getArenaIndexForSlot(int slot) {
        // Berechne welche Arena zu diesem Slot gehört
        int index = 0;
        int checkSlot = ARENA_START_SLOT;
        
        for (int i = 0; i < availableArenas.size(); i++) {
            // Überspringe Randslots
            while (checkSlot % 9 == 0 || checkSlot % 9 == 8) {
                checkSlot++;
            }
            
            if (checkSlot >= SIZE - 9) break;
            
            if (checkSlot == slot) {
                return i;
            }
            
            checkSlot++;
        }
        
        return -1;
    }
    
    @Override
    public void onClose() {
        // Arena-Auswahl bleibt in der Session
    }
}
