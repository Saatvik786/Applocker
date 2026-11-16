package com.applocker.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applocker_policy")
class ApplockerPolicyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false, length = 400)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    ApplockerPolicyEntity() {
    }

    ApplockerPolicyEntity(UUID id, String name, String description, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    Instant createdAt() {
        return createdAt;
    }
}
