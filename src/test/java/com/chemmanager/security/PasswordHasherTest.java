package com.chemmanager.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Passwords must never be stored in clear text and must be salted per user. */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashVerifiesWithTheSamePassword() {
        String encoded = hasher.hash("admin123".toCharArray());
        assertTrue(hasher.verify("admin123".toCharArray(), encoded));
        assertFalse(hasher.verify("admin124".toCharArray(), encoded));
    }

    @Test
    void samePasswordProducesDifferentHashes() {
        String first = hasher.hash("secret".toCharArray());
        String second = hasher.hash("secret".toCharArray());
        assertNotEquals(first, second, "a random salt per hash is required");
        assertTrue(hasher.verify("secret".toCharArray(), first));
        assertTrue(hasher.verify("secret".toCharArray(), second));
    }

    @Test
    void hashNeverContainsThePlainPassword() {
        String encoded = hasher.hash("P@ssw0rd".toCharArray());
        assertFalse(encoded.contains("P@ssw0rd"));
        assertTrue(encoded.startsWith("pbkdf2-sha256$"));
    }

    @Test
    void malformedHashesAreRejected() {
        assertFalse(hasher.verify("admin".toCharArray(), null));
        assertFalse(hasher.verify(null, "pbkdf2-sha256$1$x$y"));
        assertFalse(hasher.verify("admin".toCharArray(), "bcrypt$10$abc"));
        assertFalse(hasher.verify("admin".toCharArray(), "not-a-hash"));
        assertFalse(hasher.verify("admin".toCharArray(), "pbkdf2-sha256$abc$###$###"));
    }
}
