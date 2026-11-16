package com.applocker.enrollment;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class StoredToken {

    private final UUID id;
    private final String label;
    private final byte[] secretHash;
    private final Integer deviceLimit;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Set<String> devices;
    private boolean revoked;

    StoredToken(
            UUID id,
            String label,
            byte[] secretHash,
            Integer deviceLimit,
            Instant expiresAt,
            Instant createdAt,
            Set<String> devices,
            boolean revoked) {
        this.id = id;
        this.label = label;
        this.secretHash = secretHash.clone();
        this.deviceLimit = deviceLimit;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.devices = new LinkedHashSet<>(devices);
        this.revoked = revoked;
    }

    UUID id() {
        return id;
    }

    String label() {
        return label;
    }

    byte[] secretHash() {
        return secretHash.clone();
    }

    Integer deviceLimit() {
        return deviceLimit;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    Set<String> devices() {
        return devices;
    }

    boolean revoked() {
        return revoked;
    }

    void revoke() {
        this.revoked = true;
    }
}
