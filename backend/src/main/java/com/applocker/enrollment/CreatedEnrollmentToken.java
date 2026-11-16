package com.applocker.enrollment;

import java.time.Instant;

public record CreatedEnrollmentToken(
        String tokenId,
        String secret,
        String discoveryUrl,
        String authPolicy,
        String label,
        Integer deviceLimit,
        Instant expiresAt) {
}
