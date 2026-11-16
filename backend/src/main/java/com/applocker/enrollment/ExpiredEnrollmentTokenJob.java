package com.applocker.enrollment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ExpiredEnrollmentTokenJob {

    private final EnrollmentTokenService tokens;

    ExpiredEnrollmentTokenJob(EnrollmentTokenService tokens) {
        this.tokens = tokens;
    }

    @Scheduled(fixedDelay = 60_000)
    void deleteExpiredTokens() {
        tokens.deleteExpired();
    }
}
