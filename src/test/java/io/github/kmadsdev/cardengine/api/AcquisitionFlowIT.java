package io.github.kmadsdev.cardengine.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTest;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTestConfig;
import io.github.kmadsdev.cardengine.testsupport.MutableClock;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import io.github.kmadsdev.cardengine.testsupport.TestCreditBureau;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AcquisitionFlowIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    TestCreditBureau bureau;
    @Autowired
    MutableClock clock;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(IntegrationTestConfig.START);
    }

    static String body(String cpf, String birthDate, String income) {
        return """
                {"cpf":"%s","fullName":"Test Applicant","birthDate":"%s","declaredMonthlyIncome":%s}
                """.formatted(cpf, birthDate, income);
    }

    ResultActions submit(String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/applications")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    static String key() {
        return "key-" + UUID.randomUUID();
    }

    JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void approvedApplicationOpensAccountAndIssuesTokenizedCard() throws Exception {
        String cpf = TestCpfs.random();
        bureau.setScore(cpf, 760);

        JsonNode app = read(submit(key(), body(TestCpfs.formatted(cpf), "1990-03-10", "3000.00"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/applications/")))
                .andExpect(jsonPath("$.status").value("CARD_ISSUED"))
                .andExpect(jsonPath("$.variant").value("CREDIT_PLATINUM"))
                .andExpect(jsonPath("$.creditLimit").value(6000.00))
                .andExpect(jsonPath("$.cpfMasked").value("***.***.***-" + cpf.substring(9)))
                .andExpect(jsonPath("$.decision.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.decision.policyVersion").exists())
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("SCORE_BAND"))));

        String body = app.toString();
        assertThat(body).doesNotContain(cpf);

        String cardId = app.get("cardId").asText();
        mvc.perform(get("/api/v1/cards/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedNumber", startsWith("**** **** **** ")))
                .andExpect(jsonPath("$.variant").value("CREDIT_PLATINUM"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> card = jdbc.queryForMap("select * from card where id = ?::uuid", cardId);
        assertThat((String) card.get("token")).matches("tok_[0-9a-f]{32}");
        assertThat((String) card.get("last4")).matches("\\d{4}");
        assertThat(card.values()).as("no column holds a full card number")
                .noneMatch(v -> v != null && v.toString().matches(".*\\d{13,19}.*"));

        String accountId = app.get("accountId").asText();
        mvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variant").value("CREDIT_PLATINUM"))
                .andExpect(jsonPath("$.creditLimit").value(6000.00));

        List<String> events = jdbc.queryForList(
                "select event_type from outbox_event where aggregate_id in (?::uuid, ?::uuid, ?::uuid) order by created_at",
                String.class, app.get("id").asText(), accountId, cardId);
        assertThat(events).containsExactlyInAnyOrder("ApplicationDecided", "CardAccountOpened", "CardIssued");
    }

    @Test
    void lowScoreGetsDebitCardWithZeroLimit() throws Exception {
        String cpf = TestCpfs.random();
        bureau.setScore(cpf, 420);
        submit(key(), body(cpf, "1985-01-01", "8000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CARD_ISSUED"))
                .andExpect(jsonPath("$.variant").value("DEBIT"))
                .andExpect(jsonPath("$.creditLimit").value(0.00))
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("SCORE_DEBIT_ONLY")));
    }

    @Test
    void invalidCheckDigitIsRejectedWithReasonAndBureauIsNotCalled() throws Exception {
        int callsBefore = bureau.calls();
        submit(key(), body(TestCpfs.withWrongCheckDigit(TestCpfs.random()), "1990-01-01", "5000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasons[0].code").value("INVALID_CPF"))
                .andExpect(jsonPath("$.accountId").doesNotExist());
        assertThat(bureau.calls()).isEqualTo(callsBefore);
    }

    @Test
    void minorIsRejected() throws Exception {
        // Clock is 2026-06-01: born 2008-06-02 is 17 years old.
        submit(key(), body(TestCpfs.random(), "2008-06-02", "1000.00"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasons[0].code").value("AGE_BELOW_MINIMUM"));
        submit(key(), body(TestCpfs.random(), "2008-06-01", "1000.00"))
                .andExpect(jsonPath("$.status").value("CARD_ISSUED"));
    }

    @Test
    void lowBureauScoreIsRejected() throws Exception {
        String cpf = TestCpfs.random();
        bureau.setScore(cpf, 150);
        submit(key(), body(cpf, "1990-01-01", "9000.00"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasons[*].code", hasItem("SCORE_TOO_LOW")));
    }

    @Test
    void secondApplicationForSameCpfIsRejectedAsDuplicate() throws Exception {
        String cpf = TestCpfs.random();
        submit(key(), body(cpf, "1990-01-01", "3000.00")).andExpect(jsonPath("$.status").value("CARD_ISSUED"));
        submit(key(), body(cpf, "1990-01-01", "3000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reasons[0].code").value("DUPLICATE_CUSTOMER"));
    }

    @Test
    void sameKeySameBodyReplaysSameKeyOtherBodyIsRefused() throws Exception {
        String key = key();
        String cpf = TestCpfs.random();
        String first = read(submit(key, body(cpf, "1990-01-01", "3000.00")).andExpect(status().isCreated()))
                .get("id").asText();

        submit(key, body(cpf, "1990-01-01", "3000.00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(first));

        submit(key, body(cpf, "1990-01-01", "3500.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:idempotency-key-reused"));

        assertThat(jdbc.queryForObject("select count(*) from card_application where idempotency_key = ?",
                Integer.class, key)).isEqualTo(1);
    }

    @Test
    void invalidBodyIsProblemJsonWithFieldErrors() throws Exception {
        submit(key(), """
                {"cpf":"12345","fullName":"","birthDate":"2999-01-01","declaredMonthlyIncome":-1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:validation"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("cpf")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("birthDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("declaredMonthlyIncome")));
    }

    @Test
    void idempotencyKeyIsRequiredAndChecked() throws Exception {
        mvc.perform(post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(body(TestCpfs.random(), "1990-01-01", "1000.00")))
                .andExpect(status().isBadRequest());
        submit("short", body(TestCpfs.random(), "1990-01-01", "1000.00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdIsNotFoundProblem() throws Exception {
        mvc.perform(get("/api/v1/applications/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listIsPagedAndFilterable() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(key(), body(TestCpfs.random(), "1990-01-01", "2500.00"));
        }
        mvc.perform(get("/api/v1/applications").param("size", "2").param("status", "CARD_ISSUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].status", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("CARD_ISSUED"))));
        mvc.perform(get("/api/v1/applications").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:card-engine:problem:invalid-sort"));
        mvc.perform(get("/api/v1/accounts").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void correlationIdIsEchoedOrGenerated() throws Exception {
        mvc.perform(get("/api/v1/applications").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
        mvc.perform(get("/api/v1/applications").header("X-Correlation-Id", "bad value\nwith newline"))
                .andExpect(header().string("X-Correlation-Id",
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }
}
