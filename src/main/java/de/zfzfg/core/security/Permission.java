package de.zfzfg.core.security;

import org.bukkit.command.CommandSender;

public enum Permission {
    // Events
    EVENT_JOIN("eventplugin.join"),
    EVENT_ADMIN("eventplugin.admin"),
    EVENTPVP_ADMIN("eventpvp.admin"),

    // PvP
    PVP_USE("pvpwager.use"),
    PVP_SPECTATE("pvpwager.spectate"),
    PVP_ADMIN("pvpwager.admin"),

    // Global/BYPASS
    BYPASS_LIMITS("pvpwager.bypass.betlimit"),
    BYPASS_COMMANDS("eventpvp.opbypass"),

    // Debug
    DEBUG("eventpvp.debug"),
    DEBUG_RECEIVE("eventpvp.debug.receive");

    private final String node;

    Permission(String node) {
        this.node = node;
    }

    public String getNode() {
        return node;
    }

    public boolean check(CommandSender sender) {
        return sender.hasPermission(this.node);
    }

    public void require(CommandSender sender) throws PermissionException {
        if (!check(sender)) {
            throw new PermissionException(this);
        }
    }
}