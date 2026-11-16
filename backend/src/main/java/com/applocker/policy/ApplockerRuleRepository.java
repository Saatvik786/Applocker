package com.applocker.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ApplockerRuleRepository extends JpaRepository<ApplockerRuleEntity, UUID> {

    List<ApplockerRuleEntity> findByVersionIdOrderBySortOrderAsc(UUID versionId);

    int countByVersionId(UUID versionId);
}
