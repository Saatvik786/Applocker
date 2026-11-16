package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy")
public class PolicyProfile {

    @Id
    private UUID id;

    @Column(name = "applocker_policy_id", nullable = false, unique = true)
    private UUID applockerPolicyId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 400)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApplockerPolicyId() {
        return applockerPolicyId;
    }

    public void setApplockerPolicyId(UUID applockerPolicyId) {
        this.applockerPolicyId = applockerPolicyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
