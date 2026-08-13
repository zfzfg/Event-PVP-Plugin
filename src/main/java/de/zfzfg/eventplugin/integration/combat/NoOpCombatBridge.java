package de.zfzfg.eventplugin.integration.combat;

import org.bukkit.entity.Player;

public class NoOpCombatBridge implements CombatIntegrationBridge {
    @Override
    public void untagPlayer(Player player) {
        // no-op when PvPManager is not available
    }
}
