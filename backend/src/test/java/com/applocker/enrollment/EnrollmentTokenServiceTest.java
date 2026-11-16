package com.applocker.enrollment;

import com.applocker.config.ApplockerProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final EnrollmentTokenService tokens = new EnrollmentTokenService(
            new ApplockerProperties("test", "https://mdm.example.test", "admin", null, null),
            clock,
            new MemoryTokenStore());

    @Test
    void createShowsTheSecretOnceAndBuildsTheDiscoveryUrl() {
        CreatedEnrollmentToken created = tokens.create("lab vm", 2, 48);

        assertThat(created.secret()).isEqualTo(created.tokenId());
        assertThat(created.authPolicy()).isEqualTo("OnPremise");
        assertThat(created.discoveryUrl()).isEqualTo(
                "https://mdm.example.test/api/v1/mdm/windows/enrollment/discovery/" + created.tokenId());
        assertThat(tokens.list()).allSatisfy(summary -> {
            assertThat(summary.tokenId()).isEqualTo(created.tokenId());
            assertThat(summary.discoveryUrl()).endsWith("/discovery/" + created.tokenId());
        });
    }

    @Test
    void expiryDaysAreStoredAndExpiredTokensAreDeleted() {
        CreatedEnrollmentToken created = tokens.create("password policy", 51, null, 78);

        assertThat(created.deviceLimit()).isEqualTo(51);
        assertThat(created.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(78)));
        assertThat(tokens.list()).hasSize(1);

        clock.advance(Duration.ofDays(78));
        assertThat(tokens.list()).isEmpty();
        assertThatThrownBy(() -> tokens.requireActive(java.util.UUID.fromString(created.tokenId())))
                .isInstanceOf(EnrollmentRejectedException.class);
    }

    @Test
    void expiredRevokedAndFullTokensRejectEnrollment() {
        CreatedEnrollmentToken created = tokens.create("bounded", 1, 48);
        var tokenId = java.util.UUID.fromString(created.tokenId());

        tokens.authorize(tokenId, created.secret());
        assertThat(tokens.tryClaim(tokenId, "device-a")).isEqualTo(EnrollmentTokenService.Claim.CLAIMED);
        assertThat(tokens.tryClaim(tokenId, "device-a")).isEqualTo(EnrollmentTokenService.Claim.ALREADY);
        assertThat(tokens.tryClaim(tokenId, "device-b")).isEqualTo(EnrollmentTokenService.Claim.LIMIT);
        tokens.release(tokenId, "device-a");
        assertThat(tokens.tryClaim(tokenId, "device-b")).isEqualTo(EnrollmentTokenService.Claim.CLAIMED);

        tokens.revoke("bounded");
        assertThatThrownBy(() -> tokens.requireActive(tokenId)).isInstanceOf(EnrollmentRejectedException.class);

        CreatedEnrollmentToken expiring = tokens.create("expiring", null, 48);
        clock.advance(Duration.ofHours(48));
        assertThatThrownBy(() -> tokens.requireActive(java.util.UUID.fromString(expiring.tokenId())))
                .isInstanceOf(EnrollmentRejectedException.class);
    }

    @Test
    void wrongSecretIsRejectedWithoutTheSecretInTheError() {
        CreatedEnrollmentToken created = tokens.create("secret", null, 24);
        var tokenId = java.util.UUID.fromString(created.tokenId());

        assertThatThrownBy(() -> tokens.authorize(tokenId, "wrong-password"))
                .isInstanceOf(EnrollmentRejectedException.class)
                .hasMessage("Enrollment rejected");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
