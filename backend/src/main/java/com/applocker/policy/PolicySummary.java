package com.applocker.policy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicySummary(
        UUID id,
        String name,
        String description,
        int version,
        String enforcementMode,
        int ruleCount,
        boolean storeAppsEnforced,
        Instant createdAt) {
}
