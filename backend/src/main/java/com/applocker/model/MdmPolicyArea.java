package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "mdm_policy_area")
public class MdmPolicyArea {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "base_loc_uri", nullable = false, length = 200)
    private String baseLocUri;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBaseLocUri() {
        return baseLocUri;
    }
}
