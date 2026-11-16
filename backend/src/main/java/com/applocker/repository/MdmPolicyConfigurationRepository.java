package com.applocker.repository;

import com.applocker.model.MdmPolicyDefinitionConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MdmPolicyConfigurationRepository extends JpaRepository<MdmPolicyDefinitionConfiguration, UUID> {

    Optional<MdmPolicyDefinitionConfiguration> findByPolicyId(UUID policyId);

    Optional<MdmPolicyDefinitionConfiguration> findByPolicyIdAndDefinitionId(UUID policyId, UUID definitionId);
}
