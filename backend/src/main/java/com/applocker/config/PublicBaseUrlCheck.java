package com.applocker.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PublicBaseUrlCheck implements ApplicationRunner {

    private final ApplockerProperties properties;

    public PublicBaseUrlCheck(ApplockerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()) {
            throw new IllegalStateException("PUBLIC_BASE_URL must be set");
        }
    }
}
