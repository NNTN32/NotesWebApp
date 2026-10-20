package com.example.notesWeb.platform.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReadinessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired HealthContributorRegistry contributors;

    @Test void readinessChecksEachDependencyButLivenessDoesNotRestartOnItsFailure() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}", true));
        for (String name : java.util.List.of("db", "redis", "rabbit")) {
            var original = contributors.unregisterContributor(name);
            org.assertj.core.api.Assertions.assertThat(original).as(name).isNotNull();
            contributors.registerContributor(name, (HealthIndicator) () ->
                    Health.down().withDetail("private", "must-not-leak").build());
            try {
                mvc.perform(get("/actuator/health/readiness"))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(content().json("{\"status\":\"DOWN\"}", true));
                mvc.perform(get("/actuator/health/liveness"))
                        .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}", true));
            } finally {
                contributors.unregisterContributor(name);
                contributors.registerContributor(name, original);
            }
        }
    }

    @Test void healthDoesNotExposeManagementDataOrBypassBusinessSecurity() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notes/listNotes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
