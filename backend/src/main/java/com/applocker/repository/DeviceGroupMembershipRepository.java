package com.applocker.repository;

import com.applocker.model.DeviceGroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceGroupMembershipRepository extends JpaRepository<DeviceGroupMembership, DeviceGroupMembership.Key> {
}
