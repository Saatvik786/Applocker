package com.applocker.policy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicyDetail(
        UUID id,
        String name,
        String description,
        int version,
        String enforcementMode,
        boolean delivered,
        Instant createdAt,
        List<PolicyCollection> collections) {
}
