package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "mdm_policy_area_group_mapping")
@IdClass(MdmPolicyAreaGroupMapping.Key.class)
public class MdmPolicyAreaGroupMapping {

    @Id
    @Column(name = "area_id")
    private UUID areaId;

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    public UUID getAreaId() {
        return areaId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public static class Key implements Serializable {
        private UUID areaId;
        private UUID groupId;

        public Key() {
        }

        public Key(UUID areaId, UUID groupId) {
            this.areaId = areaId;
            this.groupId = groupId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && areaId.equals(key.areaId) && groupId.equals(key.groupId);
        }

        @Override
        public int hashCode() {
            return areaId.hashCode() * 31 + groupId.hashCode();
        }
    }
}
