package com.applocker.enrollment;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "enrollment_token")
class EnrollmentTokenEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String label;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "device_limit")
    private Integer deviceLimit;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean revoked;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "enrollment_token_device", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "device_key", nullable = false, length = 128)
    private Set<String> devices = new LinkedHashSet<>();

    static EnrollmentTokenEntity from(StoredToken token) {
        EnrollmentTokenEntity entity = new EnrollmentTokenEntity();
        entity.apply(token);
        return entity;
    }

    void apply(StoredToken token) {
        this.id = token.id();
        this.label = token.label();
        this.secretHash = HashHex.toHex(token.secretHash());
        this.deviceLimit = token.deviceLimit();
        this.expiresAt = token.expiresAt();
        this.createdAt = token.createdAt();
        this.revoked = token.revoked();
        this.devices.clear();
        this.devices.addAll(token.devices());
    }

    StoredToken toStored() {
        return new StoredToken(
                id,
                label,
                HashHex.fromHex(secretHash),
                deviceLimit,
                expiresAt,
                createdAt,
                devices,
                revoked);
    }
}
