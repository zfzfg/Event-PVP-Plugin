package de.zfzfg.core.security;

public class PermissionException extends RuntimeException {
    private final Permission permission;

    public PermissionException(Permission permission) {
        super("Missing permission: " + permission.getNode());  // i18n-ignore: Exception-Text fuer Log und Stacktrace
        this.permission = permission;
    }

    public Permission getPermission() {
        return permission;
    }
}