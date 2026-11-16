package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "policy_group_mapping")
@IdClass(PolicyGroupMapping.Key.class)
public class PolicyGroupMapping {

    @Id
    @Column(name = "policy_id")
    private UUID policyId;

    @Id
    @Column(name = "device_group_id")
    private UUID deviceGroupId;

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public UUID getDeviceGroupId() {
        return deviceGroupId;
    }

    public void setDeviceGroupId(UUID deviceGroupId) {
        this.deviceGroupId = deviceGroupId;
    }

    public static class Key implements Serializable {
        private UUID policyId;
        private UUID deviceGroupId;

        public Key() {
        }

        public Key(UUID policyId, UUID deviceGroupId) {
            this.policyId = policyId;
            this.deviceGroupId = deviceGroupId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && policyId.equals(key.policyId) && deviceGroupId.equals(key.deviceGroupId);
        }

        @Override
        public int hashCode() {
            return policyId.hashCode() * 31 + deviceGroupId.hashCode();
        }
    }
}
