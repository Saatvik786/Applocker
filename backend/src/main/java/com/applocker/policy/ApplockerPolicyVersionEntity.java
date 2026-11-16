package com.applocker.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applocker_policy_version")
class ApplockerPolicyVersionEntity {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "enforcement_mode", nullable = false, length = 20)
    private String enforcementMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    ApplockerPolicyVersionEntity() {
    }

    ApplockerPolicyVersionEntity(UUID id, UUID policyId, int versionNumber, String enforcementMode, Instant createdAt) {
        this.id = id;
        this.policyId = policyId;
        this.versionNumber = versionNumber;
        this.enforcementMode = enforcementMode;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    UUID policyId() {
        return policyId;
    }

    int versionNumber() {
        return versionNumber;
    }

    String enforcementMode() {
        return enforcementMode;
    }

    void enforcementMode(String enforcementMode) {
        this.enforcementMode = enforcementMode;
    }

    Instant createdAt() {
        return createdAt;
    }
}
