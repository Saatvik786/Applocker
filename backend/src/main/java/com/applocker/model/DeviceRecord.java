package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device")
public class DeviceRecord {

    @Id
    private UUID id;

    @Column(name = "windows_device_id", nullable = false, unique = true, length = 128)
    private String windowsDeviceId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getWindowsDeviceId() {
        return windowsDeviceId;
    }

    public void setWindowsDeviceId(String windowsDeviceId) {
        this.windowsDeviceId = windowsDeviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }
}
