package com.applocker.policy;

public record RuleRequest(
        String collection,
        String type,
        String action,
        String name,
        String path,
        String publisherName,
        String productName,
        String binaryName,
        String hashData,
        String sourceFileName,
        Long sourceFileLength,
        String userOrGroupSid,
        String binaryVersionLow,
        String binaryVersionHigh) {
}
