package com.applocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "device_group")
public class DeviceGroup {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
