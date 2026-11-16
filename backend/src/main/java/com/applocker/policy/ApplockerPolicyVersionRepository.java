package com.applocker.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ApplockerPolicyVersionRepository extends JpaRepository<ApplockerPolicyVersionEntity, UUID> {

    Optional<ApplockerPolicyVersionEntity> findFirstByPolicyIdOrderByVersionNumberDesc(UUID policyId);
}
