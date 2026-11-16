package com.applocker.service;

import com.applocker.mdm.MdmCatalog;
import com.applocker.model.MdmPolicyDefinitionConfiguration;
import com.applocker.model.PolicyGroupMapping;
import com.applocker.model.PolicyProfile;
import com.applocker.repository.MdmPolicyConfigurationRepository;
import com.applocker.repository.PolicyGroupMappingRepository;
import com.applocker.repository.PolicyProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MdmPolicyService {

    private final PolicyProfileRepository profiles;
    private final PolicyGroupMappingRepository groups;
    private final MdmPolicyConfigurationRepository configurations;

    public MdmPolicyService(
            PolicyProfileRepository profiles,
            PolicyGroupMappingRepository groups,
            MdmPolicyConfigurationRepository configurations) {
        this.profiles = profiles;
        this.groups = groups;
        this.configurations = configurations;
    }

    public void saveProfile(UUID policyId, String name, String description, Instant createdAt, boolean storeBlock, UUID grouping, int blockedAppCount) {
        PolicyProfile profile = new PolicyProfile();
        profile.setId(policyId);
        profile.setApplockerPolicyId(policyId);
        profile.setName(name);
        profile.setDescription(description);
        profile.setCreatedAt(createdAt);
        profiles.save(profile);
        if (!storeBlock) {
            return;
        }
        PolicyGroupMapping mapping = new PolicyGroupMapping();
        mapping.setPolicyId(policyId);
        mapping.setDeviceGroupId(MdmCatalog.ENROLLED_DEVICES);
        groups.save(mapping);
        MdmPolicyDefinitionConfiguration configuration = new MdmPolicyDefinitionConfiguration();
        configuration.setId(UUID.randomUUID());
        configuration.setDefinitionId(MdmCatalog.STORE_APPS);
        configuration.setPolicyId(policyId);
        configuration.setGroupingGuid(grouping);
        configuration.setConfiguration("{\"enforcementMode\":\"Enabled\",\"collection\":\"StoreApps\",\"blockedAppCount\":" + blockedAppCount + "}");
        configurations.save(configuration);
    }

    public void syncStoreDelivery(UUID policyId, String name, String description, Instant createdAt, boolean deliver, String enforcementMode, int blockedAppCount) {
        if (profiles.findById(policyId).isEmpty()) {
            saveProfile(policyId, name, description, createdAt, deliver, deliver ? UUID.randomUUID() : null, blockedAppCount);
            if (deliver) {
                updateStoreConfiguration(policyId, enforcementMode, blockedAppCount);
            }
            return;
        }
        if (!deliver && grouping(policyId).isEmpty()) {
            return;
        }
        if (grouping(policyId).isEmpty()) {
            PolicyGroupMapping mapping = new PolicyGroupMapping();
            mapping.setPolicyId(policyId);
            mapping.setDeviceGroupId(MdmCatalog.ENROLLED_DEVICES);
            groups.save(mapping);
            MdmPolicyDefinitionConfiguration configuration = new MdmPolicyDefinitionConfiguration();
            configuration.setId(UUID.randomUUID());
            configuration.setDefinitionId(MdmCatalog.STORE_APPS);
            configuration.setPolicyId(policyId);
            configuration.setGroupingGuid(UUID.randomUUID());
            configuration.setConfiguration(storeJson(enforcementMode, blockedAppCount));
            configurations.save(configuration);
            return;
        }
        updateStoreConfiguration(policyId, enforcementMode, blockedAppCount);
    }

    public void updateStoreConfiguration(UUID policyId, String enforcementMode, int blockedAppCount) {
        configurations.findByPolicyIdAndDefinitionId(policyId, MdmCatalog.STORE_APPS).ifPresent(configuration -> {
            configuration.setConfiguration(storeJson(enforcementMode, blockedAppCount));
            configurations.save(configuration);
        });
    }

    private static String storeJson(String enforcementMode, int blockedAppCount) {
        String mode = "Enabled".equals(enforcementMode) ? "Enabled" : "AuditOnly";
        return "{\"enforcementMode\":\"" + mode + "\",\"collection\":\"StoreApps\",\"blockedAppCount\":" + blockedAppCount + "}";
    }

    public Optional<UUID> grouping(UUID policyId) {
        return configurations.findByPolicyId(policyId).map(MdmPolicyDefinitionConfiguration::getGroupingGuid);
    }

    public List<AssignedStorePolicy> assignedStorePolicies() {
        List<AssignedStorePolicy> assigned = new ArrayList<>();
        for (PolicyGroupMapping mapping : groups.findByDeviceGroupId(MdmCatalog.ENROLLED_DEVICES)) {
            configurations.findByPolicyIdAndDefinitionId(mapping.getPolicyId(), MdmCatalog.STORE_APPS)
                    .ifPresent(configuration -> assigned.add(new AssignedStorePolicy(configuration.getPolicyId(), configuration.getGroupingGuid())));
        }
        assigned.sort(Comparator.comparing(item -> profiles.findById(item.applockerPolicyId()).map(PolicyProfile::getCreatedAt).orElse(Instant.EPOCH)));
        return assigned;
    }

    public record AssignedStorePolicy(UUID applockerPolicyId, UUID grouping) {
    }
}
