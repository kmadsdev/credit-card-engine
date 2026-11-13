package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.application.AcquisitionService;
import io.github.kmadsdev.cardengine.application.ConcurrentUpdateException;
import io.github.kmadsdev.cardengine.application.CooldownActiveException;
import io.github.kmadsdev.cardengine.application.QueryService;
import io.github.kmadsdev.cardengine.application.UpgradeService;
import io.github.kmadsdev.cardengine.config.ClockConfig;
import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import io.github.kmadsdev.cardengine.domain.DomainException;
import io.github.kmadsdev.cardengine.domain.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web slice: HTTP mapping and RFC 7807 errors, with the use cases mocked. No database. */
@WebMvcTest(controllers = {ApplicationController.class, AccountController.class, CardController.class})
@Import(ClockConfig.class)
class ApiSliceTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AcquisitionService acquisition;
    @MockitoBean
    UpgradeService upgrades;
    @MockitoBean
    QueryService queries;

    @Test
    void bodyValidationStopsBeforeTheUseCase() throws Exception {
        mvc.perform(post("/api/v1/applications").header("Idempotency-Key", "slice-key-0001")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
        verifyNoInteractions(acquisition);
    }

    @Test
    void cooldownIs429WithRetryAfter() throws Exception {
        when(upgrades.request(any(), any(), anyString()))
                .thenThrow(new CooldownActiveException(Instant.now().plusSeconds(3600)));
        mvc.perform(post("/api/v1/accounts/{id}/upgrade-requests", UUID.randomUUID())
                        .header("Idempotency-Key", "slice-key-0002"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:upgrade-cooldown"));
    }

    @Test
    void lostRaceIs409() throws Exception {
        when(upgrades.request(any(), any(), anyString()))
                .thenThrow(new ConcurrentUpdateException("changed", null));
        mvc.perform(post("/api/v1/accounts/{id}/upgrade-requests", UUID.randomUUID())
                        .header("Idempotency-Key", "slice-key-0003"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:concurrent-update"));
    }

    @Test
    void illegalTransitionIs409AndOtherDomainErrorsAre422() throws Exception {
        UUID id = UUID.randomUUID();
        when(queries.application(id)).thenThrow(
                new IllegalStateTransitionException(ApplicationStatus.REJECTED, ApplicationStatus.APPROVED));
        mvc.perform(get("/api/v1/applications/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:illegal-state-transition"));

        UUID other = UUID.randomUUID();
        when(queries.card(other)).thenThrow(new DomainException("downgrade-not-allowed", "no"));
        mvc.perform(get("/api/v1/cards/{id}", other))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:downgrade-not-allowed"));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(get("/api/v1/accounts/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
