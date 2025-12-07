package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.CommandRequestManager;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /pvpa <player> <wager> <amount> <arena> <equipment>
 * 
 * Examples:
 * /pvpa Steve DIAMOND_SWORD 1 desert diamond
 * /pvpa Steve MONEY 100 forest standard
 * /pvpa Steve DIAMOND_SWORD,GOLDEN_APPLE 1,5 colosseum netherite
 */
public class PvPACommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    private final CommandRequestManager requestManager;
    
    public PvPACommand(EventPlugin plugin) {
        this.plugin = plugin;
        this.requestManager = plugin.getCommandRequestManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.getPvpConfigManager();

        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.pvpa.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 5) {
            sendUsage(player);
            return true;
        }
        
        // Parse arguments
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.player-not-found", "player", args[0]));
            return true;
        }
        
        if (player.equals(target)) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.self-challenge"));
            return true;
        }
        
        String wagerType = args[1];
        String amountStr = args[2];
        String arenaId = args[3];
        String equipmentId = args[4];
        
        // Validate Arena (Optional-API)
        java.util.Optional<de.zfzfg.pvpwager.models.Arena> arenaOpt = plugin.getArenaManager().getArenaOptional(arenaId);
        if (arenaOpt.isEmpty()) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.arena-not-found", "arena", arenaId));
            sendAvailableArenas(player);
            return true;
        }
        String worldName = arenaOpt.get().getArenaWorld();
        
        // Validate Equipment
        if (plugin.getEquipmentManager().getEquipmentSet(equipmentId) == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.equipment-not-found", "equipment", equipmentId));
            sendAvailableEquipment(player);
            return true;
        }
        if (!plugin.getEquipmentManager().isEquipmentAllowedInWorld(equipmentId, worldName)) {
            MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessage("error.equipment-not-available"));
            return true;
        }
        
        // Parse wager
        List<ItemStack> items = new ArrayList<>();
        double money = 0.0;
        
        if (wagerType.equalsIgnoreCase("SKIP")) {
            // Keine Wette – Items/Money leer lassen
            items.clear();
            money = 0.0;
        } else if (wagerType.equalsIgnoreCase("MONEY")) {
            try {
                money = Double.parseDouble(amountStr);
                if (money <= 0) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.amount-positive"));
                    return true;
                }
                
                if (!plugin.hasEconomy()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.economy-disabled"));
                    return true;
                }
                
                if (!plugin.getEconomy().has(player, money)) {
                    MessageUtil.sendMessage(player, cfg.getMessage(
                        "messages.command.pvpa.not-enough-money",
                        "balance", String.format("%.2f", plugin.getEconomy().getBalance(player))
                    ));
                    return true;
                }
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.invalid-money", "amount", amountStr));
                return true;
            }
        } else {
            // Parse items
            String[] itemNames = wagerType.split(",");
            String[] amounts = amountStr.split(",");
            
            if (itemNames.length != amounts.length) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.mismatch-items"));
                return true;
            }
            
            for (int i = 0; i < itemNames.length; i++) {
                try {
                    Material material = Material.valueOf(itemNames[i].toUpperCase());
                    int amount = Integer.parseInt(amounts[i]);
                    
                    if (amount <= 0) {
                        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.amount-positive"));
                        return true;
                    }
                    
                    // Check if player has items
                    ItemStack checkItem = new ItemStack(material, amount);
                    if (!player.getInventory().containsAtLeast(checkItem, amount)) {
                        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.not-enough-items", "item", material.name()));
                        return true;
                    }
                    
                    items.add(new ItemStack(material, amount));
                } catch (IllegalArgumentException e) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.invalid-item", "item", itemNames[i]));
                    return true;
                }
            }
        }
        
        // Create request
        CommandRequest request = new CommandRequest(
            player, target, items, money, arenaId, equipmentId
        );
        
        requestManager.addRequest(request);
        
        // Send messages
        String wagerDisplay = money > 0
            ? cfg.getMessage("messages.command.pvpa.wager-money", "amount", String.format("%.2f", money))
            : cfg.getMessage("messages.command.pvpa.wager-items", "count", String.valueOf(items.size()));

        MessageUtil.sendMessage(player, cfg.getMessage(
            "messages.command.pvpa.request-sent",
            "target", target.getName(),
            "arena", arenaId,
            "equipment", equipmentId,
            "wager", wagerDisplay
        ));
        
        // Notify target
        requestManager.sendRequestNotification(request);
        
        return true;
    }
    
    private void sendUsage(Player player) {
        MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessage("messages.command.pvpa.usage"));
    }
    
    private void sendAvailableArenas(Player player) {
        MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessage("messages.command.pvpa.available-arenas"));
        plugin.getArenaManager().getArenas().values().forEach(arena -> {
            MessageUtil.sendMessage(player, "  &e" + arena.getId() + " &7- " + arena.getDisplayName());
        });
    }
    
    private void sendAvailableEquipment(Player player) {
        MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessage("messages.command.pvpa.available-equipment"));
        plugin.getEquipmentManager().getEquipmentSets().values().forEach(equipment -> {
            MessageUtil.sendMessage(player, "  &e" + equipment.getId() + " &7- " + equipment.getDisplayName());
        });
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            // Player names
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Wager type - list all items in inventory + MONEY
            completions.add("MONEY");
            completions.add("SKIP");
            
            // Get all unique items from player's inventory
            Map<Material, Integer> itemCounts = new HashMap<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    itemCounts.put(item.getType(), 
                        itemCounts.getOrDefault(item.getType(), 0) + item.getAmount());
                }
            }
            
            // Add all items that match the partial input
            for (Material mat : itemCounts.keySet()) {
                if (mat.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(mat.name());
                }
            }
        } else if (args.length == 3) {
            // Amount - suggest total count of selected item or common amounts
            if (args[1].equalsIgnoreCase("MONEY")) {
                completions.add("10");
                completions.add("100");
                completions.add("1000");
            } else if (args[1].equalsIgnoreCase("SKIP")) {
                completions.add("0");
            } else {
                try {
                    Material selectedMaterial = Material.valueOf(args[1].toUpperCase());
                    
                    // Count how many of this item the player has
                    int totalCount = 0;
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() == selectedMaterial) {
                            totalCount += item.getAmount();
                        }
                    }
                    
                    if (totalCount > 0) {
                        completions.add(String.valueOf(totalCount)); // Total amount
                        completions.add("1"); // Single item
                        
                        // Add some common fractions
                        if (totalCount > 2) {
                            completions.add(String.valueOf(totalCount / 2)); // Half
                        }
                        if (totalCount > 4) {
                            completions.add(String.valueOf(totalCount / 4)); // Quarter
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid material, suggest common amounts
                    completions.add("1");
                    completions.add("10");
                }
            }
        } else if (args.length == 4) {
            // Arenas
            for (String arenaId : plugin.getArenaManager().getArenas().keySet()) {
                if (arenaId.toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(arenaId);
                }
            }
        } else if (args.length == 5) {
            java.util.Optional<de.zfzfg.pvpwager.models.Arena> aopt = plugin.getArenaManager().getArenaOptional(args[3]);
            java.util.List<de.zfzfg.pvpwager.models.EquipmentSet> allowed = aopt.isPresent()
                ? plugin.getEquipmentManager().getAllowedEquipmentForWorld(aopt.get().getArenaWorld())
                : new java.util.ArrayList<>(plugin.getEquipmentManager().getEquipmentSets().values());
            for (de.zfzfg.pvpwager.models.EquipmentSet set : allowed) {
                String id = set.getId();
                if (id.toLowerCase().startsWith(args[4].toLowerCase())) completions.add(id);
            }
        }
        
        return completions;
    }
}