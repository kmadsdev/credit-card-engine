package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.domain.CardStatus;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.domain.UpgradeStatus;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.CardRepository;
import io.github.kmadsdev.cardengine.persistence.UpgradeRequestRepository;
import io.github.kmadsdev.cardengine.testsupport.Fixtures;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTest;
import io.github.kmadsdev.cardengine.testsupport.IntegrationTestConfig;
import io.github.kmadsdev.cardengine.testsupport.MutableClock;
import io.github.kmadsdev.cardengine.testsupport.TestCreditBureau;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two upgrades of the same account at the same time must not both apply.
 */
@IntegrationTest
class ConcurrentUpgradeIT {

    @Autowired
    UpgradeService upgrades;
    @Autowired
    Fixtures fixtures;
    @Autowired
    TestCreditBureau bureau;
    @Autowired
    MutableClock clock;
    @Autowired
    CardAccountRepository accounts;
    @Autowired
    CardRepository cards;
    @Autowired
    UpgradeRequestRepository requests;
    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void resetClock() {
        clock.set(IntegrationTestConfig.START);
    }

    @AfterEach
    void releaseBarrier() {
        bureau.holdCallersAt(null);
    }

    private CardAccount healthyBaseAccount() {
        return fixtures.existingClient(CardVariant.CREDIT_BASE, "2000.00", 12, "5000.00", 700, 12, 0, "1700.00")
                .account();
    }

    private Object call(UUID accountId) {
        return call(accountId, "race-" + UUID.randomUUID());
    }

    private Object call(UUID accountId, String idempotencyKey) {
        try {
            return upgrades.request(accountId, new UpgradeService.RequestUpgrade(null), idempotencyKey);
        } catch (RuntimeException e) {
            return e;
        }
    }

    /** Runs two calls at the bureau barrier and returns both results. */
    private List<Object> raceTwo(UUID accountId, String keyA, String keyB) throws Exception {
        bureau.holdCallersAt(new CyclicBarrier(2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> a = pool.submit(() -> call(accountId, keyA));
            Future<Object> b = pool.submit(() -> call(accountId, keyB));
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The cooldown must hold under a race too. Low utilization makes the policy keep the
     * account, so the variant does not change. Two such requests at once must still not
     * both be stored inside the 30-day cooldown.
     */
    @Test
    void twoConcurrentRequestsThatChangeNoVariantCannotBothPassTheCooldown() throws Exception {
        CardAccount account = fixtures.existingClient(CardVariant.CREDIT_BASE, "2000.00", 12, "5000.00", 700, 12, 0,
                "200.00").account();

        List<Object> results = raceTwo(account.getId(), "race-" + UUID.randomUUID(), "race-" + UUID.randomUUID());

        assertThat(results).filteredOn(r -> r instanceof Views.Created<?>).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof ConcurrentUpdateException).hasSize(1);
        assertThat(requests.findByAccountId(account.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo(UpgradeStatus.KEPT));
    }

    /**
     * Deterministic race: the bureau call sits after the account read and the cooldown
     * check, and before any write. A barrier of 2 there means both requests have read
     * version N before either writes. The optimistic lock must stop the second one.
     */
    @Test
    void optimisticLockStopsTheSecondOfTwoInterleavedUpgrades() throws Exception {
        CardAccount account = healthyBaseAccount();
        long versionBefore = account.getVersion();
        bureau.holdCallersAt(new CyclicBarrier(2));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> a = pool.submit(() -> call(account.getId()));
            Future<Object> b = pool.submit(() -> call(account.getId()));
            List<Object> results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));

            assertThat(results).filteredOn(r -> r instanceof Views.Created<?>).hasSize(1);
            assertThat(results).filteredOn(r -> r instanceof ConcurrentUpdateException).hasSize(1)
                    .first().satisfies(e -> assertThat(((Exception) e).getCause())
                            .isInstanceOf(ObjectOptimisticLockingFailureException.class));
        } finally {
            pool.shutdownNow();
        }

        CardAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getVariant()).isEqualTo(CardVariant.CREDIT_PLATINUM);
        assertThat(after.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(requests.findByAccountId(account.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo(UpgradeStatus.APPLIED));
        assertThat(cards.findByAccountIdAndStatus(account.getId(), CardStatus.ACTIVE)).hasSize(1);
    }

    /** Many clients at once, no barrier: whatever the interleaving, exactly one applies. */
    @Test
    void manyParallelRequestsApplyExactlyOnce() throws Exception {
        CardAccount account = healthyBaseAccount();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call(account.getId());
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(r -> r instanceof Views.Created<?>).hasSize(1);
            assertThat(results).filteredOn(r -> !(r instanceof Views.Created<?>))
                    .allMatch(r -> r instanceof ConcurrentUpdateException || r instanceof CooldownActiveException);
        } finally {
            pool.shutdownNow();
        }
        CardAccount after = accounts.findById(account.getId()).orElseThrow();
        assertThat(after.getVariant()).isEqualTo(CardVariant.CREDIT_PLATINUM);
        assertThat(cards.findByAccountIdAndStatus(account.getId(), CardStatus.ACTIVE)).hasSize(1);
    }

    /** The lock itself, without the service: two transactions that both read version N. */
    @Test
    void staleAccountWriteIsRefusedByVersion() {
        CardAccount account = healthyBaseAccount();
        CardAccount stale = accounts.findById(account.getId()).orElseThrow();

        tx.executeWithoutResult(s -> accounts.findById(account.getId()).orElseThrow()
                .upgradeTo(CardVariant.CREDIT_PLATINUM, Money.of("3000.00"), clock.instant()));

        stale.upgradeTo(CardVariant.CREDIT_PLATINUM, Money.of("5000.00"), clock.instant());
        assertThatThrownBy(() -> accounts.save(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(accounts.findById(account.getId()).orElseThrow().getCreditLimit()).isEqualByComparingTo("3000.00");
    }
}
