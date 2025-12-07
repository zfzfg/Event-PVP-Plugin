package de.zfzfg.pvpwager.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.util.Time;
import org.bukkit.potion.PotionEffectType;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.utils.MessageUtil;
import de.zfzfg.pvpwager.managers.ConfigManager;

public class PvPCommand implements CommandExecutor {
    
    private final EventPlugin plugin;
    
    public PvPCommand(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getPvpConfigManager();
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.getMessage("messages.command.common.player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        MatchManager matchManager = plugin.getMatchManager();
        
        if (args.length == 0) {
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.help"));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("accept") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[1]));
                return true;
            }
            
            if (plugin.getRequestManager().acceptRequest(player, target)) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.success-self", "player", target.getName()));
                MessageUtil.sendMessage(target, cfg.getMessage("messages.command.pvp.accept.success-other", "player", player.getName()));
            } else {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.accept.no-request", "player", target.getName()));
            }
            return true;
        }
        
        if (args[0].equalsIgnoreCase("deny") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[1]));
                return true;
            }
            
            plugin.getRequestManager().cancelRequest(target.getUniqueId(), player.getUniqueId());
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.deny.success-self", "player", target.getName()));
            MessageUtil.sendMessage(target, cfg.getMessage("messages.command.pvp.deny.success-other", "player", player.getName()));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("spectate") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.player-offline", "player", args[1]));
                return true;
            }
            
            Match match = matchManager.getMatchByPlayer(target);
            if (match == null) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.spectate.no-match", "player", target.getName()));
                return true;
            }
            
            if (match.getSpectators().size() >= plugin.getPvpConfigManager().getConfig().getInt("max-spectators", 10)) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.match-full"));
                return true;
            }
            
            // Add spectator to match (managed by MatchManager for O(1) lookup)
            matchManager.addSpectator(match, player);
            
            // Teleport to spectator spawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.teleport(match.getArena().getSpectatorSpawn());
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                // Mark spectator as teleported for end-of-match return
                plugin.getMatchManager().markTeleported(player);
                
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.spectate.joined", "p1", match.getPlayer1().getName(), "p2", match.getPlayer2().getName()));
                match.broadcast(cfg.getMessage("messages.command.pvp.spectate.announce", "player", player.getName()));
            }, Time.TICKS_PER_SECOND);
            
            return true;
        }
        
        if (args[0].equalsIgnoreCase("leave")) {
            Match match = matchManager.getMatchByPlayer(player);
            if (match == null || !match.getSpectators().contains(player.getUniqueId())) {
                MessageUtil.sendMessage(player, cfg.getMessage("messages.command.common.not-spectating"));
                return true;
            }
            
            // Fetch original location and remove spectator via manager
            Location originalLocation = match.getOriginalLocation(player);
            matchManager.removeSpectator(match, player);
            
            // Teleport back to original location
            if (originalLocation != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(originalLocation);
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    // Safety cleanup: clear lingering invisibility (from older plugins)
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    
                    MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.leave.left"));
                    match.broadcast(cfg.getMessage("messages.command.pvp.leave.announce", "player", player.getName()));
                }, Time.TICKS_PER_SECOND);
            }
            
            return true;
        }
            MessageUtil.sendMessage(player, cfg.getMessage("messages.command.pvp.help"));
            return true;
    }
}