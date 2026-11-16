package com.applocker.policy;

import java.util.List;
import java.util.UUID;

public final class WorkspaceModels {

    private WorkspaceModels() {
    }

    public record WorkspaceView(UUID policyId, boolean identityRunning, List<CollectionView> collections) {
    }

    public record CollectionView(
            String id,
            String label,
            String title,
            String description,
            List<String> conditions,
            boolean sentToDevices,
            int ruleCount,
            String enforcementMode,
            boolean defaultAllow) {
    }

    public record StoreAppView(String packageName, String publisher, String version, String action, String result) {
    }

    public record RuleView(
            UUID id,
            String collection,
            String type,
            String action,
            String name,
            String path,
            String publisherName,
            String productName,
            String binaryName,
            String userOrGroupSid,
            boolean defaultRule,
            String result) {
    }

    public record StoreAppCommand(String packageName, String publisher, String version, String action) {
    }

    public record CollectionCommand(String enforcementMode, Boolean defaultAllow) {
    }

    public record RuleActionCommand(String action) {
    }
}
