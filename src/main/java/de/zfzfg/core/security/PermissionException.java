package de.zfzfg.core.security;

public class PermissionException extends RuntimeException {
    private final Permission permission;

    public PermissionException(Permission permission) {
        super("Missing permission: " + permission.getNode());
        this.permission = permission;
    }

    public Permission getPermission() {
        return permission;
    }
}