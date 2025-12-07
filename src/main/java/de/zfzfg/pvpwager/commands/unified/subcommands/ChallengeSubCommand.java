package de.zfzfg.pvpwager.commands.unified.subcommands;

import de.zfzfg.core.commands.SubCommand;
import de.zfzfg.core.util.CommandCooldownManager;
import de.zfzfg.core.util.InputValidator;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import de.zfzfg.pvpwager.managers.ConfigManager;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ChallengeSubCommand extends SubCommand {
    private final CommandCooldownManager cooldowns;

    public ChallengeSubCommand(EventPlugin plugin) { 
        super(plugin);
        this.cooldowns = new CommandCooldownManager();
        // Setze die übersetzte Cooldown-Nachricht
        this.cooldowns.setMessageProvider(seconds -> {
            String msg = plugin.getCoreConfigManager().getMessages()
                .getString("messages.system.cooldown-wait", "&cPlease wait {seconds} more seconds!");
            return ChatColor.translateAlternateColorCodes('&', msg.replace("{seconds}", String.valueOf(seconds)));
        });
    }

    @Override
    public String getName() { return "challenge"; }

    @Override
    public String getPermission() { return "pvpwager.command"; }

    @Override
    public String getUsage() { return "/pvp challenge <player> [wager] [amount] [arena] [equipment]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (!cooldowns.checkAndApply(player, "pvp-challenge")) {
            return true;
        }
        if (args.length == 0) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.challenge.usage"));
            return true;
        }
        try {
            Player target = InputValidator.validateOnlinePlayer(args[0]);
            if (player.equals(target)) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.self-target"));
                return true;
            }
            if (plugin.getMatchManager().getMatchByPlayer(player) != null) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.already-in-match"));
                return true;
            }
            if (plugin.getMatchManager().getMatchByPlayer(target) != null) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.target-in-match", "player", target.getName()));
                return true;
            }
            // Use command-based request flow for wager-aware challenges
            if (plugin.getCommandRequestManager().hasPendingRequest(player)) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.pending-self"));
                return true;
            }
            if (plugin.getCommandRequestManager().hasPendingRequest(target)) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.pending-target", "player", target.getName()));
                return true;
            }
            // Parse optional wager/arena/equipment
            java.util.List<org.bukkit.inventory.ItemStack> items = new java.util.ArrayList<>();
            double money = 0.0;
            String arenaId = null;
            String equipmentId = null;

            if (args.length >= 3) {
                String wagerType = args[1];
                String amountStr = args[2];
                if (wagerType.equalsIgnoreCase("SKIP")) {
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
                            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.not-enough-money"));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.invalid-money", "amount", amountStr));
                        return true;
                    }
                } else {
                    String[] itemNames = wagerType.split(",");
                    String[] amounts = amountStr.split(",");
                    if (itemNames.length != amounts.length) {
                        MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.mismatch-items"));
                        return true;
                    }
                    for (int i = 0; i < itemNames.length; i++) {
                        try {
                            org.bukkit.Material material = org.bukkit.Material.valueOf(itemNames[i].toUpperCase());
                            int amount = Integer.parseInt(amounts[i]);
                            if (amount <= 0) {
                                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.amount-positive"));
                                return true;
                            }
                            org.bukkit.inventory.ItemStack checkItem = new org.bukkit.inventory.ItemStack(material, amount);
                            if (!player.getInventory().containsAtLeast(checkItem, amount)) {
                                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.not-enough-items", "item", material.name()));
                                return true;
                            }
                            items.add(new org.bukkit.inventory.ItemStack(material, amount));
                        } catch (IllegalArgumentException e) {
                            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.invalid-item", "item", itemNames[i]));
                            return true;
                        }
                    }
                }
            }

            // Optional arena/equipment, fallback to first available
            if (args.length >= 4) {
                arenaId = args[3];
                if (plugin.getArenaManager().getArenaOptional(arenaId).isEmpty()) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.arena-not-found", "arena", arenaId));
                    return true;
                }
            } else if (!plugin.getArenaManager().getArenas().isEmpty()) {
                arenaId = plugin.getArenaManager().getArenas().values().iterator().next().getId();
            }

            if (args.length >= 5) {
                equipmentId = args[4];
                if (plugin.getEquipmentManager().getEquipmentSet(equipmentId) == null) {
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvpa.equipment-not-found", "equipment", equipmentId));
                    return true;
                }
                String worldName = plugin.getArenaManager().getArenaOptional(arenaId).map(a -> a.getArenaWorld()).orElse(null);
                if (worldName != null && !plugin.getEquipmentManager().isEquipmentAllowedInWorld(equipmentId, worldName)) {
                    MessageUtil.sendMessage(player, plugin.getPvpConfigManager().getMessage("error.equipment-not-available"));
                    return true;
                }
            } else if (!plugin.getEquipmentManager().getEquipmentSets().isEmpty()) {
                String worldName = plugin.getArenaManager().getArenaOptional(arenaId).map(a -> a.getArenaWorld()).orElse(null);
                java.util.List<de.zfzfg.pvpwager.models.EquipmentSet> allowed = worldName != null
                    ? plugin.getEquipmentManager().getAllowedEquipmentForWorld(worldName)
                    : new java.util.ArrayList<>(plugin.getEquipmentManager().getEquipmentSets().values());
                if (!allowed.isEmpty()) {
                    equipmentId = allowed.get(0).getId();
                }
            }

            if (arenaId == null || equipmentId == null) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.challenge.no-config"));
                return true;
            }

            de.zfzfg.pvpwager.models.CommandRequest request = new de.zfzfg.pvpwager.models.CommandRequest(
                player, target, items, money, arenaId, equipmentId
            );
            plugin.getCommandRequestManager().addRequest(request);
            plugin.getCommandRequestManager().sendRequestNotification(request);
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.challenge.sent", "player", target.getName()));
        } catch (IllegalArgumentException ex) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[0]));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        // /pvp challenge <player> <wager> <amount> <arena> <equipment>
        if (args.length == 1) {
            // Spieler-Namen
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Wette-Typ – Items aus Inventar + MONEY + SKIP
            completions.add("MONEY");
            completions.add("SKIP");

            Map<Material, Integer> itemCounts = new HashMap<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    itemCounts.put(item.getType(), itemCounts.getOrDefault(item.getType(), 0) + item.getAmount());
                }
            }
            for (Material mat : itemCounts.keySet()) {
                if (mat.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(mat.name());
                }
            }
        } else if (args.length == 3) {
            // Anzahl/Betrag – Vorschläge abhängig vom Wette-Typ
            if (args[1].equalsIgnoreCase("MONEY")) {
                completions.add("10");
                completions.add("100");
                completions.add("1000");
            } else if (args[1].equalsIgnoreCase("SKIP")) {
                completions.add("0");
            } else {
                try {
                    Material selectedMaterial = Material.valueOf(args[1].toUpperCase());
                    int totalCount = 0;
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() == selectedMaterial) {
                            totalCount += item.getAmount();
                        }
                    }
                    if (totalCount > 0) {
                        completions.add(String.valueOf(totalCount));
                        completions.add("1");
                        if (totalCount > 2) {
                            completions.add(String.valueOf(totalCount / 2));
                        }
                        if (totalCount > 4) {
                            completions.add(String.valueOf(totalCount / 4));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    completions.add("1");
                    completions.add("10");
                }
            }
        } else if (args.length == 4) {
            // Arenen
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