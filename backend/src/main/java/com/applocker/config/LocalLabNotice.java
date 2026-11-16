package com.applocker.config;

import com.applocker.enrollment.cert.LabAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Profile("local")
public class LocalLabNotice implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalLabNotice.class);

    private final ApplockerProperties properties;
    private final Environment environment;

    public LocalLabNotice(ApplockerProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String base = properties.publicBaseUrl();
        log.info("Public base URL: {}", base);
        if (base.contains("://localhost") || base.contains("://127.0.0.1")) {
            log.warn("This URL is only reachable on this computer. Restart with PUBLIC_BASE_URL=https://<lan-address>:8443 before creating a token.");
        }
        log.info("Addresses on this computer: {}", String.join(", ", LabAddresses.hosts(base)));
        log.info("Install this CA certificate on the test device only: {}",
                Path.of(properties.ca().directory(), "lab-ca.crt").toAbsolutePath());
        if (LocalEnvFile.setting("APPLOCKER_ADMIN_TOKEN") == null) {
            log.info("Admin token file: {}", Path.of("data", "admin-token").toAbsolutePath());
        } else {
            log.info("Admin token is read from APPLOCKER_ADMIN_TOKEN.");
        }
        String databaseUrl = environment.getProperty("spring.datasource.url", "");
        if (databaseUrl.startsWith("jdbc:postgresql:")) {
            log.info("Enrollment tokens are stored in PostgreSQL and removed when they expire.");
        } else {
            log.info("Enrollment tokens are stored in the local database file and removed when they expire.");
        }
        log.info("Open the token page at {}.", base);
    }
}
