package com.applocker.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "applocker_collection_setting")
class ApplockerCollectionSettingEntity {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "collection_type", nullable = false, length = 16)
    private String collectionType;

    @Column(name = "enforcement_mode", nullable = false, length = 20)
    private String enforcementMode;

    @Column(name = "default_allow", nullable = false)
    private boolean defaultAllow;

    ApplockerCollectionSettingEntity() {
    }

    UUID id() {
        return id;
    }

    UUID versionId() {
        return versionId;
    }

    String collectionType() {
        return collectionType;
    }

    String enforcementMode() {
        return enforcementMode;
    }

    boolean defaultAllow() {
        return defaultAllow;
    }

    void id(UUID id) {
        this.id = id;
    }

    void versionId(UUID versionId) {
        this.versionId = versionId;
    }

    void collectionType(String collectionType) {
        this.collectionType = collectionType;
    }

    void enforcementMode(String enforcementMode) {
        this.enforcementMode = enforcementMode;
    }

    void defaultAllow(boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
    }
}
