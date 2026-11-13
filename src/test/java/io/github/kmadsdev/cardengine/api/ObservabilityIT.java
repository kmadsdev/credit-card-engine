package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.testsupport.IntegrationTest;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ObservabilityIT {

    @Autowired
    MockMvc mvc;

    @Test
    void healthProbesAreUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusExposesBusinessMetrics() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .header("Idempotency-Key", "metrics-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AcquisitionFlowIT.body(TestCpfs.random(), "1990-01-01", "2000.00")))
                .andExpect(status().isCreated());

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cards_applications_decided_total")))
                .andExpect(content().string(containsString("cards_outbox_pending")))
                .andExpect(content().string(containsString("http_server_requests_seconds")));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}/upgrade-requests']").exists());
    }
}
