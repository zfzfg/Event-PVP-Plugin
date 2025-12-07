package de.zfzfg.pvpwager.commands;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /pvpanswer <wager> <amount> [arena] [equipment]
 */
public class PvPAnswerCommand implements CommandExecutor, TabCompleter {
    
    private final EventPlugin plugin;
    
    public PvPAnswerCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.pvpanswer.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Get request
        CommandRequest request = plugin.getCommandRequestManager().getRequestToPlayer(player);
        if (request == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.no-request"));
            return true;
        }
        
        if (args.length < 1) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.usage"));
            return true;
        }
        
        String wagerType = args[0];
        String amountStr = args.length > 1 ? args[1] : null;
        String arenaId = args.length > 2 ? args[2] : null;
        String equipmentId = args.length > 3 ? args[3] : null;
        
        // Validate optional arena (Optional-API)
        if (arenaId != null && plugin.getArenaManager().getArenaOptional(arenaId).isEmpty()) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.arena-not-found", "arena", arenaId));
            return true;
        }
        
        // Validate optional equipment
        if (equipmentId != null && plugin.getEquipmentManager().getEquipmentSet(equipmentId) == null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.equipment-not-found", "equipment", equipmentId));
            return true;
        }
        if (equipmentId != null) {
            String worldName = arenaId != null
                ? plugin.getArenaManager().getArenaOptional(arenaId).map(a -> a.getArenaWorld()).orElse(null)
                : plugin.getArenaManager().getArenaOptional(request.getArenaId()).map(a -> a.getArenaWorld()).orElse(null);
            if (worldName != null && !plugin.getEquipmentManager().isEquipmentAllowedInWorld(equipmentId, worldName)) {
                MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessage("error.equipment-not-available"));
                return true;
            }
        }
        
        // Parse wager
        List<ItemStack> items = new ArrayList<>();
        double money = 0.0;
        
        if (wagerType.equalsIgnoreCase("SKIP")) {
            items.clear();
            money = 0.0;
        } else if (wagerType.equalsIgnoreCase("MONEY")) {
            try {
                if (amountStr == null) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.missing-money"));
                    return true;
                }
                money = Double.parseDouble(amountStr);
                if (money <= 0) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.amount-positive"));
                    return true;
                }
                
                if (!plugin.hasEconomy()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.economy-disabled"));
                    return true;
                }
                
                if (!plugin.getEconomy().has(player, money)) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.not-enough-money"));
                    return true;
                }
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.invalid-money", "amount", amountStr));
                return true;
            }
        } else {
            // Parse items
            if (amountStr == null) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.missing-items"));
                return true;
            }
            String[] itemNames = wagerType.split(",");
            String[] amounts = amountStr.split(",");
            if (itemNames.length != amounts.length) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.mismatch-items"));
                return true;
            }
            for (int i = 0; i < itemNames.length; i++) {
                try {
                    Material material = Material.valueOf(itemNames[i].toUpperCase());
                    int amount = Integer.parseInt(amounts[i]);
                    if (amount <= 0) {
                        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.amount-positive"));
                        return true;
                    }
                    ItemStack checkItem = new ItemStack(material, amount);
                    if (!player.getInventory().containsAtLeast(checkItem, amount)) {
                        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.not-enough-items", "item", material.name()));
                        return true;
                    }
                    items.add(new ItemStack(material, amount));
                } catch (IllegalArgumentException e) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.invalid-item", "item", itemNames[i]));
                    return true;
                }
            }
        }
        
        // Set response
        request.setTargetResponse(items, money, arenaId, equipmentId);
        
        // Notify both players
        Player challenger = request.getSender();
        
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.response-sent"));
        if (money > 0) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.your-wager-money", "amount", String.format("%.2f", money)));
        } else {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.your-wager-items", "items", MessageUtil.formatItemList(items)));
        }
        if (arenaId != null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.arena-override", "arena", arenaId));
        }
        if (equipmentId != null) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.equipment-override", "equipment", equipmentId));
        }
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpanswer.waiting-confirm", "player", challenger.getName()));
        MessageUtil.sendMessage(player, "");
        
        // Notify sender
        MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvpanswer.challenger-header", "player", player.getName().toUpperCase()));
        if (money > 0) {
            MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvpanswer.their-wager-money", "amount", String.format("%.2f", money)));
        } else {
            MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvpanswer.their-wager-items", "items", MessageUtil.formatItemList(items)));
        }
        if (arenaId != null) {
            MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvpanswer.arena-changed", "arena", arenaId));
        }
        if (equipmentId != null) {
            MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvpanswer.equipment-changed", "equipment", equipmentId));
        }
        MessageUtil.sendMessage(challenger, "");
        MessageUtil.sendMessage(challenger, cfg.getMessage("messages.command.pvpanswer.next-steps"));
        MessageUtil.sendMessage(challenger, "");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Erstes Argument: Wager-Typ
            completions.addAll(Arrays.asList("SKIP", "MONEY", "DIAMOND_SWORD", "DIAMOND_CHESTPLATE", "DIAMOND_HELMET", 
                "DIAMOND_LEGGINGS", "DIAMOND_BOOTS", "IRON_SWORD", "IRON_CHESTPLATE", "GOLDEN_SWORD", "BOW"));
        } else if (args.length == 2) {
            // Zweites Argument: Anzahl/Betrag
            if (args[0].equalsIgnoreCase("MONEY")) {
                completions.addAll(Arrays.asList("10", "50", "100", "500", "1000"));
            } else if (args[0].equalsIgnoreCase("SKIP")) {
                completions.add("0");
            } else {
                completions.addAll(Arrays.asList("1", "5", "10", "50", "100"));
            }
        } else if (args.length == 3) {
            // Drittes Argument: Arena (optional)
            completions.addAll(plugin.getArenaManager().getArenas().keySet());
        } else if (args.length == 4) {
            String aid = null;
            if (args.length > 2) {
                aid = args[2];
            } else if (sender instanceof org.bukkit.entity.Player) {
                de.zfzfg.pvpwager.models.CommandRequest req = plugin.getCommandRequestManager().getRequestToPlayer((org.bukkit.entity.Player) sender);
                if (req != null) aid = req.getArenaId();
            }
            java.util.Optional<de.zfzfg.pvpwager.models.Arena> aopt = plugin.getArenaManager().getArenaOptional(aid);
            java.util.List<de.zfzfg.pvpwager.models.EquipmentSet> allowed = aopt.isPresent()
                ? plugin.getEquipmentManager().getAllowedEquipmentForWorld(aopt.get().getArenaWorld())
                : new java.util.ArrayList<>(plugin.getEquipmentManager().getEquipmentSets().values());
            for (de.zfzfg.pvpwager.models.EquipmentSet set : allowed) completions.add(set.getId());
        }
        
        return completions;
    }
}