package com.applocker.enrollment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/enrollment-tokens")
public class EnrollmentTokenController {

    private final EnrollmentTokenService tokens;

    public EnrollmentTokenController(EnrollmentTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateEnrollmentTokenRequest request) {
        try {
            CreatedEnrollmentToken created = tokens.create(
                    request.label(), request.deviceLimit(), request.ttlHours(), request.ttlDays());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping
    public List<EnrollmentTokenSummary> list() {
        return tokens.list();
    }

    @PostMapping("/revocations")
    public ResponseEntity<?> revoke(@RequestBody RevokeEnrollmentTokenRequest request) {
        try {
            tokens.revoke(request.label());
            return ResponseEntity.ok(Map.of("revoked", true));
        } catch (EnrollmentTokenNotFoundException exception) {
            return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
        }
    }
}
