package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.AcquisitionService.SubmitApplication;
import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTest;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTestConfig;
import io.github.kmadsdev.cardengine.testsupport.MutableClock;
import io.github.kmadsdev.cardengine.testsupport.TestCpfs;
import io.github.kmadsdev.cardengine.testsupport.TestCreditBureau;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two applications for the same CPF at the same time. The duplicate pre-check
 * ({@code existsByCpf}) cannot see an uncommitted customer, so both requests pass it.
 * The unique constraint on {@code customer.cpf} must stop the second one.
 */
@IntegrationTest
class ConcurrentAcquisitionIT {

    @Autowired
    AcquisitionService acquisition;
    @Autowired
    TestCreditBureau bureau;
    @Autowired
    MutableClock clock;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MeterRegistry meters;

    private double decided() {
        return meters.find("cards.applications.decided").counters().stream().mapToDouble(Counter::count).sum();
    }

    @BeforeEach
    void resetClock() {
        clock.set(IntegrationTestConfig.START);
    }

    @AfterEach
    void releaseBarrier() {
        bureau.holdCallersAt(null);
    }

    private Object submit(String cpf) {
        try {
            return acquisition.submit(new SubmitApplication(cpf, "Race Applicant", LocalDate.of(1990, 1, 1),
                    new BigDecimal("3000.00")), "race-" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Test
    void sameCpfUnderTwoKeysAtOnceOpensOneAccountOnly() throws Exception {
        String cpf = TestCpfs.random();
        double decidedBefore = decided();
        // The bureau is called after the duplicate pre-check: both requests have passed it here.
        bureau.holdCallersAt(new CyclicBarrier(2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> results;
        try {
            Future<Object> a = pool.submit(() -> submit(cpf));
            Future<Object> b = pool.submit(() -> submit(cpf));
            results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).filteredOn(r -> r instanceof Views.Created<?>).singleElement()
                .satisfies(r -> assertThat(((Views.ApplicationView) ((Views.Created<?>) r).body()).status())
                        .isEqualTo(ApplicationStatus.CARD_ISSUED));
        assertThat(results).filteredOn(r -> r instanceof ConcurrentUpdateException).singleElement()
                .satisfies(e -> assertThat(((Exception) e).getCause())
                        .isInstanceOf(DataIntegrityViolationException.class));
        assertThat(jdbc.queryForObject("select count(*) from customer where cpf = ?", Integer.class, cpf))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from card_application where cpf = ?", Integer.class, cpf))
                .as("the losing request is rolled back as a whole").isEqualTo(1);
        assertThat(decided() - decidedBefore).as("a rolled-back decision is not counted").isEqualTo(1.0);
    }
}
