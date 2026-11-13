package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.AcquisitionService.SubmitApplication;
import io.github.kmadsdev.cardengine.persistence.OutboxEventRepository;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTest;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTestConfig;
import io.github.kmadsdev.cardengine.testsupport.MutableClock;
import io.github.kmadsdev.cardengine.testsupport.RecordingEventPublisher;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class OutboxPublisherIT {

    @Autowired
    AcquisitionService acquisition;
    @Autowired
    OutboxPublisher publisher;
    @Autowired
    RecordingEventPublisher broker;
    @Autowired
    OutboxEventRepository events;
    @Autowired
    MutableClock clock;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Outbox outbox;

    @BeforeEach
    void setUp() {
        clock.set(IntegrationTestConfig.START);
        broker.failing(false);
        drain();
    }

    @AfterEach
    void tearDown() {
        broker.failing(false);
    }

    private void drain() {
        while (publisher.publishPending() > 0) {
            // publish everything other tests left behind
        }
    }

    private UUID submitApplication() {
        return acquisition.submit(new SubmitApplication(TestCpfs.random(), "Outbox Test", LocalDate.of(1991, 2, 3),
                new BigDecimal("2500.00")), "outbox-" + UUID.randomUUID()).body().id();
    }

    @Test
    void eventsAreWrittenWithTheChangeAndPublishedLater() {
        UUID applicationId = submitApplication();
        assertThat(events.countByPublishedAtIsNull()).isEqualTo(3);
        int before = broker.published().size();

        assertThat(publisher.publishPending()).isEqualTo(3);

        assertThat(events.countByPublishedAtIsNull()).isZero();
        assertThat(broker.published().subList(before, broker.published().size()))
                .extracting(e -> e.type())
                .containsExactlyInAnyOrder("ApplicationDecided", "CardAccountOpened", "CardIssued");
        assertThat(broker.published()).filteredOn(e -> e.aggregateId().equals(applicationId))
                .singleElement()
                .satisfies(e -> assertThat(e.payload()).containsEntry("outcome", "APPROVED"));
    }

    @Test
    void failedPublishStaysPendingAndIsRetried() {
        submitApplication();
        broker.failing(true);
        assertThat(publisher.publishPending()).isZero();
        assertThat(events.countByPublishedAtIsNull()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select min(attempts) from outbox_event where published_at is null", Integer.class)).isEqualTo(1);

        broker.failing(false);
        assertThat(publisher.publishPending()).isEqualTo(3);
        assertThat(events.countByPublishedAtIsNull()).isZero();
    }

    @Test
    void anEventCannotBeWrittenOutsideTheTransactionOfItsChange() {
        assertThatThrownBy(() -> outbox.append("card", UUID.randomUUID(), "CardIssued", Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(events.countByPublishedAtIsNull()).isZero();
    }

    @Test
    void payloadsCarryNoCpf() {
        String cpf = TestCpfs.random();
        acquisition.submit(new SubmitApplication(cpf, "Outbox Test", LocalDate.of(1991, 2, 3),
                new BigDecimal("2500.00")), "outbox-" + UUID.randomUUID());
        assertThat(jdbc.queryForList("select payload::text from outbox_event", String.class))
                .noneMatch(p -> p.contains(cpf));
        assertThat(jdbc.queryForList("select inputs::text || reasons::text from decision_record", String.class))
                .noneMatch(p -> p.contains(cpf));
    }
}
