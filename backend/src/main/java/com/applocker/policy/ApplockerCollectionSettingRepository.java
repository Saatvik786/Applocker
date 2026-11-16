package com.applocker.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ApplockerCollectionSettingRepository extends JpaRepository<ApplockerCollectionSettingEntity, UUID> {

    List<ApplockerCollectionSettingEntity> findByVersionId(UUID versionId);

    Optional<ApplockerCollectionSettingEntity> findByVersionIdAndCollectionType(UUID versionId, String collectionType);
}
