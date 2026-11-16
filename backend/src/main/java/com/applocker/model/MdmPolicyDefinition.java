package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "mdm_policy_definition")
public class MdmPolicyDefinition {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "collection_name", nullable = false, length = 32)
    private String collectionName;

    @Column(name = "xml_type", nullable = false, length = 16)
    private String xmlType;

    public UUID getId() {
        return id;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getXmlType() {
        return xmlType;
    }
}
