package com.applocker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "applocker")
public record ApplockerProperties(
        String environment,
        String publicBaseUrl,
        String adminToken,
        String mdmProviderId,
        Ca ca) {

    public record Ca(String directory) {
    }

    public ApplockerProperties {
        if (mdmProviderId == null || mdmProviderId.isBlank()) {
            mdmProviderId = "Applocker";
        }
        if (ca == null || ca.directory() == null || ca.directory().isBlank()) {
            ca = new Ca("data/lab-ca");
        }
    }
}
