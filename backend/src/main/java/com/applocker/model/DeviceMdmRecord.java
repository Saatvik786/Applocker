package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_mdm")
public class DeviceMdmRecord {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "enrollment_type", length = 32)
    private String enrollmentType;

    @Column(name = "token_label", length = 80)
    private String tokenLabel;

    @Column(name = "certificate_thumbprint", length = 64)
    private String certificateThumbprint;

    @Column(name = "certificate_not_after")
    private Instant certificateNotAfter;

    @Column(name = "last_session_at")
    private Instant lastSessionAt;

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public String getEnrollmentType() {
        return enrollmentType;
    }

    public void setEnrollmentType(String enrollmentType) {
        this.enrollmentType = enrollmentType;
    }

    public String getTokenLabel() {
        return tokenLabel;
    }

    public void setTokenLabel(String tokenLabel) {
        this.tokenLabel = tokenLabel;
    }

    public String getCertificateThumbprint() {
        return certificateThumbprint;
    }

    public void setCertificateThumbprint(String certificateThumbprint) {
        this.certificateThumbprint = certificateThumbprint;
    }

    public Instant getCertificateNotAfter() {
        return certificateNotAfter;
    }

    public void setCertificateNotAfter(Instant certificateNotAfter) {
        this.certificateNotAfter = certificateNotAfter;
    }

    public Instant getLastSessionAt() {
        return lastSessionAt;
    }

    public void setLastSessionAt(Instant lastSessionAt) {
        this.lastSessionAt = lastSessionAt;
    }
}
