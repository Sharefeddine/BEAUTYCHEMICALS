package com.chemmanager.security;

/** Raised when an action is attempted without the required permission. */
public class AccessDeniedException extends RuntimeException {

    private final Permission permission;

    public AccessDeniedException(Permission permission) {
        super("Permission required: " + permission);
        this.permission = permission;
    }

    public AccessDeniedException(String message) {
        super(message);
        this.permission = null;
    }

    public Permission permission() {
        return permission;
    }
}
