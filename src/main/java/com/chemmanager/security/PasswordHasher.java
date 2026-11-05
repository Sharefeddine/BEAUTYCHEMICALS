package com.chemmanager.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 password hashing used by the offline demo mode.
 *
 * <p>In API mode hashing happens exclusively on the server with PHP's
 * {@code password_hash()} (bcrypt); the client never sees a hash. Both implementations
 * follow the same principle: unique salt per password, constant-time verification,
 * and no plaintext storage.</p>
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final String PREFIX = "pbkdf2-sha256";

    private final SecureRandom random = new SecureRandom();

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public boolean verify(char[] password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 unavailable in this JVM", ex);
        } finally {
            spec.clearPassword();
        }
    }

    /** Utility for generating seeded demo hashes at build time. */
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "admin123";
        System.out.println(new PasswordHasher().hash(password.toCharArray()));
        System.out.println("length=" + password.getBytes(StandardCharsets.UTF_8).length);
    }
}
