package com.applocker.enrollment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SecretDigest {

    private SecretDigest() {
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static boolean matches(byte[] expectedHash, String presented) {
        String value = presented == null ? "" : presented;
        return MessageDigest.isEqual(expectedHash, sha256(value));
    }
}
