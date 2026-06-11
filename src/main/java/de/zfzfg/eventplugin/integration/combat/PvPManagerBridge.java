package de.zfzfg.eventplugin.integration.combat;

import me.chancesd.pvpmanager.player.CombatPlayer;
import me.chancesd.pvpmanager.player.UntagReason;
import org.bukkit.entity.Player;

public class PvPManagerBridge implements CombatIntegrationBridge {
    @Override
    public void untagPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        CombatPlayer combatPlayer = CombatPlayer.get(player);
        if (combatPlayer != null && combatPlayer.isInCombat()) {
            combatPlayer.untag(UntagReason.PLUGIN_API);
        }
    }
}
