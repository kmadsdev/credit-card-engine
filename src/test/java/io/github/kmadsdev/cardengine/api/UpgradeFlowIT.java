package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.domain.CardStatus;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.CardRepository;
import io.github.kmadsdev.cardengine.persistence.CustomerRepository;
import io.github.kmadsdev.cardengine.testsupport.Fixtures;
import io.github.kmadsdev.cardengine.testsupport.Fixtures.ExistingClient;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTest;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTestConfig;
import io.github.kmadsdev.cardengine.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class UpgradeFlowIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    Fixtures fixtures;
    @Autowired
    MutableClock clock;
    @Autowired
    CardAccountRepository accounts;
    @Autowired
    CardRepository cards;
    @Autowired
    CustomerRepository customers;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(IntegrationTestConfig.START);
    }

    /** A healthy CREDIT_BASE client: 12 months, 12 statements on time, 85% utilization, score 700. */
    ExistingClient healthyBaseClient(String income) {
        return fixtures.existingClient(CardVariant.CREDIT_BASE, "2000.00", 12, income, 700, 12, 0, "1700.00");
    }

    ResultActions upgrade(UUID accountId, String key, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/accounts/{id}/upgrade-requests", accountId)
                .header("Idempotency-Key", key);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    static String key() {
        return "up-" + UUID.randomUUID();
    }

    @Test
    void approvedUpgradeMovesOneVariantUpAndReplacesTheCard() throws Exception {
        ExistingClient client = healthyBaseClient("5000.00");
        UUID accountId = client.account().getId();

        // 5000 x 1.0 (score 700) x 1.25 (history bonus) = 6250
        upgrade(accountId, key(), null)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.fromVariant").value("CREDIT_BASE"))
                .andExpect(jsonPath("$.toVariant").value("CREDIT_PLATINUM"))
                .andExpect(jsonPath("$.toLimit").value(6250.00))
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("HISTORY_BONUS")))
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("UPGRADE_TO_NEXT_VARIANT")));

        var account = accounts.findById(accountId).orElseThrow();
        assertThat(account.getVariant()).isEqualTo(CardVariant.CREDIT_PLATINUM);
        assertThat(account.getCreditLimit()).isEqualByComparingTo("6250.00");
        assertThat(cards.findByAccountIdAndStatus(accountId, CardStatus.ACTIVE))
                .singleElement()
                .satisfies(c -> assertThat(c.getVariant()).isEqualTo(CardVariant.CREDIT_PLATINUM));

        assertThat(jdbc.queryForList("select event_type from outbox_event where aggregate_id = ?",
                String.class, accountId)).contains("AccountUpgraded");

        mvc.perform(get("/api/v1/accounts/{id}/upgrade-requests", accountId))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("APPLIED"));
    }

    @Test
    void lowUtilizationKeepsTheAccount() throws Exception {
        ExistingClient client = fixtures.existingClient(CardVariant.CREDIT_BASE, "2000.00", 12, "9000.00",
                800, 12, 0, "200.00");
        upgrade(client.account().getId(), key(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("KEPT"))
                .andExpect(jsonPath("$.toVariant").doesNotExist())
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("LOW_UTILIZATION")));
        assertThat(accounts.findById(client.account().getId()).orElseThrow().getVariant())
                .isEqualTo(CardVariant.CREDIT_BASE);
    }

    @Test
    void latePaymentsAreRejected() throws Exception {
        // 2 of 12 late: on-time ratio 0.83
        ExistingClient client = fixtures.existingClient(CardVariant.CREDIT_BASE, "2000.00", 12, "9000.00",
                800, 12, 2, "1500.00");
        upgrade(client.account().getId(), key(), null)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("LATE_PAYMENTS")));
    }

    @Test
    void topVariantCannotGoHigher() throws Exception {
        ExistingClient client = fixtures.existingClient(CardVariant.CREDIT_BLACK, "12000.00", 24, "20000.00",
                900, 12, 0, "10000.00");
        upgrade(client.account().getId(), key(), null)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("ALREADY_TOP_VARIANT")));
    }

    @Test
    void cooldownBlocksASecondRequestFor30Days() throws Exception {
        ExistingClient client = fixtures.existingClient(CardVariant.CREDIT_BASE, "2000.00", 12, "1000.00",
                700, 12, 0, "1700.00");
        UUID accountId = client.account().getId();
        upgrade(accountId, key(), null).andExpect(jsonPath("$.status").value("KEPT"));

        clock.advance(Duration.ofDays(29));
        upgrade(accountId, key(), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:upgrade-cooldown"))
                .andExpect(jsonPath("$.retryAt").exists());

        clock.advance(Duration.ofDays(2));
        upgrade(accountId, key(), "{\"declaredMonthlyIncome\": 4000.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        assertThat(customers.findById(client.account().getCustomerId()).orElseThrow().getDeclaredMonthlyIncome())
                .isEqualByComparingTo("4000.00");
    }

    @Test
    void replayReturnsTheSameRequest() throws Exception {
        ExistingClient client = healthyBaseClient("5000.00");
        String key = key();
        String id = com.jayway.jsonpath.JsonPath.read(upgrade(client.account().getId(), key, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        upgrade(client.account().getId(), key, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/upgrade-requests/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        upgrade(UUID.randomUUID(), key(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:not-found"));
        mvc.perform(get("/api/v1/accounts/{id}/cards", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void invalidIncomeIsBadRequest() throws Exception {
        ExistingClient client = healthyBaseClient("5000.00");
        upgrade(client.account().getId(), key(), "{\"declaredMonthlyIncome\": 10.001}")
                .andExpect(status().isBadRequest());
    }
}
