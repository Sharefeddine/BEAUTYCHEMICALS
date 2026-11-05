package com.chemmanager.security;

/** Authentication failure with a translatable reason key. */
public class AuthException extends RuntimeException {

    private final String messageKey;

    public AuthException(String messageKey, String fallbackMessage) {
        super(fallbackMessage);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
