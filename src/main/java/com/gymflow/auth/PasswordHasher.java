package com.gymflow.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Hashes and verifies passwords with PBKDF2-HMAC-SHA256. */
public final class PasswordHasher {
    static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    /** Returns a freshly salted password hash. */
    public PasswordHash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(encode(derive(password, salt, ITERATIONS)), encode(salt), ITERATIONS);
    }

    /** Returns whether the supplied password matches the stored hash. */
    public boolean verify(char[] password, PasswordHash stored) {
        byte[] salt = decode(stored.salt());
        byte[] expected = decode(stored.hash());
        byte[] actual = derive(password, salt, stored.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
