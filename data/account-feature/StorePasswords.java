package com.dudububu.store;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class StorePasswords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;

    private StorePasswords() {}

    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    static boolean matches(String password, String stored) {
        if (stored == null) {
            // Spend comparable time looking up an unregistered email.
            derive(password, new byte[16], ITERATIONS);
            return false;
        }
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] expected = Base64.getDecoder().decode(parts[2]);
        return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(parts[0])));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to hash store password", error);
        } finally {
            spec.clearPassword();
        }
    }
}
