package com.applocker.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ApplockerPolicyRepository extends JpaRepository<ApplockerPolicyEntity, UUID> {

    Optional<ApplockerPolicyEntity> findByName(String name);

    List<ApplockerPolicyEntity> findAllByOrderByCreatedAtAsc();
}
