package com.applocker.repository;

import com.applocker.model.PolicyGroupMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyGroupMappingRepository extends JpaRepository<PolicyGroupMapping, PolicyGroupMapping.Key> {

    List<PolicyGroupMapping> findByDeviceGroupId(UUID deviceGroupId);
}
