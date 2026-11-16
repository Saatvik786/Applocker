package com.applocker.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEnvFileTest {

    @Test
    void parseKeepsValuesAndSkipsComments() {
        assertThat(LocalEnvFile.parse("""
                # comment
                DATABASE_URL=jdbc:postgresql://localhost:5432/applocker
                export DATABASE_USERNAME="applocker"
                EMPTY=
                """)).containsEntry("DATABASE_URL", "jdbc:postgresql://localhost:5432/applocker")
                .containsEntry("DATABASE_USERNAME", "applocker")
                .doesNotContainKey("EMPTY");
    }
}
