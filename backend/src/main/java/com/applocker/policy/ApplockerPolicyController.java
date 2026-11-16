package com.applocker.policy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
public class ApplockerPolicyController {

    private final ApplockerPolicyService policies;

    public ApplockerPolicyController(ApplockerPolicyService policies) {
        this.policies = policies;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreatePolicyRequest request) {
        try {
            return ResponseEntity.ok(policies.create(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping
    public List<PolicySummary> list() {
        return policies.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(policies.get(id));
        } catch (ApplockerPolicyNotFoundException exception) {
            return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
        }
    }
}
