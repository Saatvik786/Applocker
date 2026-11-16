package com.applocker.policy;

import java.util.List;

public record CreatePolicyRequest(
        String name,
        String description,
        Boolean includeDefaultAllowRules,
        List<RuleRequest> rules) {
}
