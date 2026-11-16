package com.applocker.enrollment;

import java.time.Instant;

public record EnrollmentTokenSummary(
        String tokenId,
        String discoveryUrl,
        String label,
        Integer deviceLimit,
        int enrolledCount,
        Instant expiresAt,
        boolean revoked,
        Instant createdAt) {
}
