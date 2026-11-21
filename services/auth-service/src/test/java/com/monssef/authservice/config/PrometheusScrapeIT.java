package com.monssef.authservice.config;

import com.monssef.authservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /actuator/prometheus as the Prometheus container reads it: the scraper account of application.yml
 * (metrics.scraper) with HTTP Basic, and that account only.
 */
// The tests only keep an in-memory registry unless asked: without it there is no Prometheus endpoint
@AutoConfigureMetrics
class PrometheusScrapeIT extends IntegrationTest {

    private static final String SCRAPER = "prometheus";
    private static final String SCRAPER_PASSWORD = "metrics-dev-only";

    @Test
    void scrape_shouldGiveEveryMetricInThePrometheusFormat() throws Exception {
        // At least one request in http_server_requests: the /actuator ones are not counted (ObservationConfig)
        mockMvc.perform(get("/api/auth/login"));

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic(SCRAPER, SCRAPER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("jvm_memory_used_bytes{application=\"auth-service\"")))
                .andExpect(content().string(containsString("hikaricp_connections_active{")))
                // The buckets the dashboards compute the latency percentiles from
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket{")));
    }

    @Test
    void scrape_shouldRefuseAMissingOrWrongAccount() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic(SCRAPER, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scrape_shouldRefuseTokens_evenAnAdminOne() throws Exception {
        // Only the scraper account reads it: an ADMIN reads the same values on /actuator/metrics
        mockMvc.perform(auth(get("/actuator/prometheus"), adminToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(get("/actuator/prometheus"), registerAndLogin("john@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scraperAccount_shouldOpenNothingElse() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(httpBasic(SCRAPER, SCRAPER_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/flyway").with(httpBasic(SCRAPER, SCRAPER_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
