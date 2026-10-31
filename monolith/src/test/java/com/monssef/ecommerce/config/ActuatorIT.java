package com.monssef.ecommerce.config;

import com.monssef.ecommerce.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Health checks and info on the real application, with PostgreSQL behind the "db" indicator
class ActuatorIT extends IntegrationTest {

    @Test
    void health_shouldBeUpWithoutTokenAndHideComponents() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void health_shouldHideComponentsFromUserRole() throws Exception {
        String user = registerAndLogin("john@example.com");

        mockMvc.perform(auth(get("/actuator/health"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void health_shouldShowDatabaseDetailsToAdmin() throws Exception {
        mockMvc.perform(auth(get("/actuator/health"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.database").value("PostgreSQL"))
                .andExpect(jsonPath("$.components.diskSpace.status").value("UP"));
    }

    @Test
    void liveness_shouldBeUpWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readiness_shouldCheckTheDatabase() throws Exception {
        mockMvc.perform(auth(get("/actuator/health/readiness"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"));
    }

    @Test
    void liveness_shouldNotDependOnTheDatabase() throws Exception {
        mockMvc.perform(auth(get("/actuator/health/liveness"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.livenessState.status").value("UP"))
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    void info_shouldDescribeBuildAppAndJvm() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("ecommerce-monolith"))
                .andExpect(jsonPath("$.build.artifact").value("ecommerce-monolith"))
                .andExpect(jsonPath("$.build.version").isNotEmpty())
                .andExpect(jsonPath("$.java.version").isNotEmpty());
    }

    @Test
    void otherEndpoints_shouldNeedAdminAndStayClosed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(get("/actuator/env"), registerAndLogin("john@example.com")))
                .andExpect(status().isForbidden());
        // even for an ADMIN: not exposed over HTTP at all
        mockMvc.perform(auth(get("/actuator/env"), adminToken()))
                .andExpect(status().isNotFound());
    }
}
