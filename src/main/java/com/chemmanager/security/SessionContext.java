package com.chemmanager.security;

import java.util.Objects;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Holds the currently authenticated session and exposes it as an observable value so that
 * the shell can react to sign-in / sign-out.
 */
public final class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();
    private final ReadOnlyObjectWrapper<Session> session = new ReadOnlyObjectWrapper<>(null);

    private SessionContext() {
    }

    public static SessionContext get() {
        return INSTANCE;
    }

    public ReadOnlyObjectProperty<Session> sessionProperty() {
        return session.getReadOnlyProperty();
    }

    public Session session() {
        return session.get();
    }

    public boolean isAuthenticated() {
        return session.get() != null && !session.get().isExpired();
    }

    public void open(Session newSession) {
        session.set(Objects.requireNonNull(newSession, "session"));
    }

    public void clear() {
        session.set(null);
    }

    public boolean can(Permission permission) {
        Session current = session.get();
        return current != null && current.has(permission);
    }
}
