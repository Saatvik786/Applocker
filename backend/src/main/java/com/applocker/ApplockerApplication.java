package com.applocker;

import com.applocker.config.LocalEnvFile;
import com.applocker.enrollment.cert.LabAddresses;
import com.applocker.enrollment.cert.LabCertificateAuthority;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;
import java.util.UUID;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ApplockerApplication {

    public static void main(String[] args) throws IOException {
        LocalEnvFile.load();
        if ("Asia/Calcutta".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        }
        if (labTlsEnabled()) {
            Path directory = Path.of("data", "lab-ca");
            String password = LabCertificateAuthority.writeServerKeyStore(
                    directory,
                    LabAddresses.hosts(LocalEnvFile.setting("PUBLIC_BASE_URL")));
            System.setProperty("server.ssl.enabled", "true");
            System.setProperty("server.ssl.key-store", directory.resolve("server.p12").toAbsolutePath().toString());
            System.setProperty("server.ssl.key-store-password", password);
            System.setProperty("server.ssl.key-store-type", "PKCS12");
            System.setProperty("server.ssl.key-alias", "server");
        }
        writeAdminToken();
        SpringApplication.run(ApplockerApplication.class, args);
    }

    private static boolean labTlsEnabled() {
        String setting = LocalEnvFile.setting("APPLOCKER_LAB_TLS");
        return setting == null || !"false".equalsIgnoreCase(setting);
    }

    private static void writeAdminToken() throws IOException {
        String fromEnvironment = LocalEnvFile.setting("APPLOCKER_ADMIN_TOKEN");
        if (fromEnvironment != null) {
            System.setProperty("applocker.admin-token", fromEnvironment);
            return;
        }
        if (System.getProperty("applocker.admin-token") != null) {
            return;
        }
        Path file = Path.of("data", "admin-token");
        Files.createDirectories(file.getParent());
        String token = Files.exists(file) ? Files.readString(file).trim() : "";
        if (token.isBlank()) {
            token = UUID.randomUUID().toString();
            Files.writeString(file, token);
        }
        System.setProperty("applocker.admin-token", token);
    }
}
