package com.applocker.config;

import com.applocker.ApplockerApplication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileConfigurationTest {

    @Test
    void deployProfileRejectsABlankPublicBaseUrl() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(ApplockerApplication.class)
                .profiles("deploy")
                .run("--server.port=0",
                        "--PUBLIC_BASE_URL=",
                        "--spring.datasource.url=jdbc:h2:mem:blank;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=sa"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PUBLIC_BASE_URL must be set");
    }

    @Nested
    @SpringBootTest(properties = {
            "PUBLIC_BASE_URL=https://mdm.example.test",
            "server.port=0",
            "spring.datasource.url=jdbc:h2:mem:deploy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=sa"
    })
    @ActiveProfiles("deploy")
    class DeployProfile {

        @Autowired
        private ApplockerProperties properties;

        @Test
        void loadsDeploySettingsFromTheEnvironment() {
            assertThat(properties.environment()).isEqualTo("deploy");
            assertThat(properties.publicBaseUrl()).isEqualTo("https://mdm.example.test");
        }
    }

    @Nested
    @SpringBootTest(properties = "server.port=0")
    @ActiveProfiles("local")
    class LocalProfile {

        @Autowired
        private ApplockerProperties properties;

        @Test
        void loadsTheLocalProfile() {
            assertThat(properties.environment()).isEqualTo("local");
            assertThat(properties.publicBaseUrl()).isNotBlank();
        }
    }
}
