package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "device_device_groups_mapping")
@IdClass(DeviceGroupMembership.Key.class)
public class DeviceGroupMembership {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Id
    @Column(name = "device_group_id")
    private UUID deviceGroupId;

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public UUID getDeviceGroupId() {
        return deviceGroupId;
    }

    public void setDeviceGroupId(UUID deviceGroupId) {
        this.deviceGroupId = deviceGroupId;
    }

    public static class Key implements Serializable {
        private UUID deviceId;
        private UUID deviceGroupId;

        public Key() {
        }

        public Key(UUID deviceId, UUID deviceGroupId) {
            this.deviceId = deviceId;
            this.deviceGroupId = deviceGroupId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && deviceId.equals(key.deviceId) && deviceGroupId.equals(key.deviceGroupId);
        }

        @Override
        public int hashCode() {
            return deviceId.hashCode() * 31 + deviceGroupId.hashCode();
        }
    }
}
