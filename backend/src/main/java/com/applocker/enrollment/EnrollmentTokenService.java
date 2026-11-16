package com.applocker.enrollment;

import com.applocker.config.ApplockerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EnrollmentTokenService {

    public enum Claim {
        CLAIMED,
        ALREADY,
        LIMIT
    }

    private final ApplockerProperties properties;
    private final Clock clock;
    private final TokenStore tokens;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public EnrollmentTokenService(ApplockerProperties properties, Clock clock, TokenStore tokens) {
        this.properties = properties;
        this.clock = clock;
        this.tokens = tokens;
    }

    public CreatedEnrollmentToken create(String label, Integer deviceLimit, Integer ttlHours) {
        return create(label, deviceLimit, ttlHours, null);
    }

    @Transactional
    public CreatedEnrollmentToken create(String label, Integer deviceLimit, Integer ttlHours, Integer ttlDays) {
        String normalized = label == null ? "" : label.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{0,79}")) {
            throw new IllegalArgumentException(
                    "Label must start with a letter or number and use only letters, numbers, spaces, dots, underscores, or hyphens");
        }
        if (deviceLimit != null && (deviceLimit < 1 || deviceLimit > 1000)) {
            throw new IllegalArgumentException("Device limit must be between 1 and 1000");
        }
        Instant createdAt = clock.instant();
        Instant expiresAt = expiry(createdAt, ttlHours, ttlDays);
        if (tokens.findByLabel(normalized).isPresent()) {
            throw new IllegalArgumentException("An enrollment token with that label already exists");
        }

        UUID tokenId = UUID.randomUUID();
        StoredToken token = new StoredToken(
                tokenId,
                normalized,
                SecretDigest.sha256(tokenId.toString()),
                deviceLimit,
                expiresAt,
                createdAt,
                Set.of(),
                false);
        tokens.save(token);
        return created(token);
    }

    @Transactional
    public List<EnrollmentTokenSummary> list() {
        deleteExpired();
        return tokens.findAll().stream()
                .sorted(Comparator.comparing(StoredToken::createdAt))
                .map(this::summary)
                .toList();
    }

    @Transactional
    public void deleteExpired() {
        Instant now = clock.instant();
        for (StoredToken token : tokens.findAll()) {
            if (!token.expiresAt().isAfter(now)) {
                tokens.delete(token.id());
            }
        }
    }

    @Transactional
    public void revoke(String label) {
        StoredToken token = tokens.findByLabel(label == null ? "" : label.trim())
                .orElseThrow(EnrollmentTokenNotFoundException::new);
        token.revoke();
        tokens.save(token);
    }

    public void requireActive(UUID tokenId) {
        StoredToken token = tokens.findById(tokenId).orElse(null);
        if (token == null || token.revoked() || !token.expiresAt().isAfter(clock.instant())) {
            throw new EnrollmentRejectedException();
        }
    }

    public void authorize(UUID tokenId, String presentedSecret) {
        StoredToken token = tokens.findById(tokenId).orElse(null);
        byte[] expected = token == null ? dummyHash() : token.secretHash();
        boolean matches = SecretDigest.matches(expected, presentedSecret == null ? "" : presentedSecret.trim());
        if (token == null || token.revoked() || !token.expiresAt().isAfter(clock.instant()) || !matches) {
            throw new EnrollmentRejectedException();
        }
    }

    @Transactional
    public Claim tryClaim(UUID tokenId, String deviceKey) {
        synchronized (lock(tokenId)) {
            StoredToken token = tokens.findById(tokenId).orElse(null);
            if (token == null || token.revoked() || !token.expiresAt().isAfter(clock.instant())) {
                throw new EnrollmentRejectedException();
            }
            if (token.devices().contains(deviceKey)) {
                return Claim.ALREADY;
            }
            if (token.deviceLimit() != null && token.devices().size() >= token.deviceLimit()) {
                return Claim.LIMIT;
            }
            token.devices().add(deviceKey);
            tokens.save(token);
            return Claim.CLAIMED;
        }
    }

    @Transactional
    public void release(UUID tokenId, String deviceKey) {
        synchronized (lock(tokenId)) {
            StoredToken token = tokens.findById(tokenId).orElse(null);
            if (token == null) {
                return;
            }
            token.devices().remove(deviceKey);
            tokens.save(token);
        }
    }

    public String label(UUID tokenId) {
        return tokens.findById(tokenId).map(StoredToken::label).orElse("");
    }

    private Instant expiry(Instant createdAt, Integer ttlHours, Integer ttlDays) {
        if (ttlDays != null) {
            if (ttlDays < 1 || ttlDays > 365) {
                throw new IllegalArgumentException("Lifetime must be between 1 and 365 days");
            }
            return createdAt.plus(ttlDays, ChronoUnit.DAYS);
        }
        int hours = ttlHours == null ? 48 : ttlHours;
        if (hours < 1 || hours > 168) {
            throw new IllegalArgumentException("Lifetime must be between 1 and 168 hours");
        }
        return createdAt.plus(hours, ChronoUnit.HOURS);
    }

    private CreatedEnrollmentToken created(StoredToken token) {
        return new CreatedEnrollmentToken(
                token.id().toString(),
                token.id().toString(),
                discoveryUrl(token.id()),
                "OnPremise",
                token.label(),
                token.deviceLimit(),
                token.expiresAt());
    }

    private EnrollmentTokenSummary summary(StoredToken token) {
        return new EnrollmentTokenSummary(
                token.id().toString(),
                discoveryUrl(token.id()),
                token.label(),
                token.deviceLimit(),
                token.devices().size(),
                token.expiresAt(),
                token.revoked(),
                token.createdAt());
    }

    private String discoveryUrl(UUID tokenId) {
        String base = properties.publicBaseUrl().replaceAll("/+$", "");
        return base + "/api/v1/mdm/windows/enrollment/discovery/" + tokenId;
    }

    private Object lock(UUID tokenId) {
        return locks.computeIfAbsent(tokenId, ignored -> new Object());
    }

    private static byte[] dummyHash() {
        return SecretDigest.sha256("enrollment-rejected");
    }
}
