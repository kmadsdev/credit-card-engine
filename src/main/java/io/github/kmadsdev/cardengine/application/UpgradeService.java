package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.Views.Created;
import io.github.kmadsdev.cardengine.application.Views.UpgradeRequestView;
import io.github.kmadsdev.cardengine.application.port.CreditBureau;
import io.github.kmadsdev.cardengine.domain.Cpf;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.domain.policy.CreditPolicy;
import io.github.kmadsdev.cardengine.domain.policy.Reason;
import io.github.kmadsdev.cardengine.domain.policy.UpgradeDecision;
import io.github.kmadsdev.cardengine.domain.policy.UpgradeFacts;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.Customer;
import io.github.kmadsdev.cardengine.persistence.CustomerRepository;
import io.github.kmadsdev.cardengine.persistence.DecisionRecord;
import io.github.kmadsdev.cardengine.persistence.DecisionRecord.SubjectType;
import io.github.kmadsdev.cardengine.persistence.DecisionRecordRepository;
import io.github.kmadsdev.cardengine.persistence.PaymentHistoryEntry;
import io.github.kmadsdev.cardengine.persistence.PaymentHistoryRepository;
import io.github.kmadsdev.cardengine.persistence.UpgradeRequest;
import io.github.kmadsdev.cardengine.persistence.UpgradeRequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Upgrade flow for an existing client, in one transaction:
 * cooldown check, account history, simulated bureau score, policy decision, and,
 * when approved, the account change plus a new card of the new variant.
 *
 * <p>Two concurrent upgrades of one account: both read version N of the account,
 * the first commit moves it to N+1, the second update matches no row and fails with
 * an optimistic lock error. That request is rolled back and the client gets 409.
 */
@Service
public class UpgradeService {

    private static final Logger log = LoggerFactory.getLogger(UpgradeService.class);

    /** {@code declaredMonthlyIncome} is optional; without it the last declared income is used. */
    public record RequestUpgrade(BigDecimal declaredMonthlyIncome) {
    }

    private final CardAccountRepository accounts;
    private final CustomerRepository customers;
    private final PaymentHistoryRepository history;
    private final UpgradeRequestRepository requests;
    private final DecisionRecordRepository decisions;
    private final CreditBureau bureau;
    private final CardIssuer cardIssuer;
    private final Outbox outbox;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;
    private final Clock clock;

    public UpgradeService(CardAccountRepository accounts, CustomerRepository customers,
                          PaymentHistoryRepository history, UpgradeRequestRepository requests,
                          DecisionRecordRepository decisions, CreditBureau bureau, CardIssuer cardIssuer,
                          Outbox outbox, TransactionTemplate tx, MeterRegistry meters, Clock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.history = history;
        this.requests = requests;
        this.decisions = decisions;
        this.bureau = bureau;
        this.cardIssuer = cardIssuer;
        this.outbox = outbox;
        this.tx = tx;
        this.meters = meters;
        this.clock = clock;
    }

    public Created<UpgradeRequestView> request(UUID accountId, RequestUpgrade command, String idempotencyKey) {
        BigDecimal income = command.declaredMonthlyIncome() == null ? null : Money.of(command.declaredMonthlyIncome());
        String hash = RequestHash.of(accountId, income == null ? "" : income.toPlainString());
        try {
            return tx.execute(status -> requestInTransaction(accountId, income, idempotencyKey, hash));
        } catch (ObjectOptimisticLockingFailureException e) {
            meters.counter("cards.upgrades.conflicts").increment();
            log.info("upgrade lost an optimistic lock race account={}", accountId);
            throw new ConcurrentUpdateException("The account was changed by another request; read it again and retry", e);
        } catch (DataIntegrityViolationException e) {
            return replayOf(idempotencyKey, hash)
                    .orElseThrow(() -> new ConcurrentUpdateException("Concurrent upgrade request", e));
        }
    }

    private Optional<Created<UpgradeRequestView>> replayOf(String idempotencyKey, String hash) {
        return requests.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReuseException();
            }
            return new Created<>(UpgradeRequestView.of(existing,
                    decisions.findById(existing.getDecisionId()).orElse(null)), true);
        });
    }

    private Created<UpgradeRequestView> requestInTransaction(UUID accountId, BigDecimal newIncome,
                                                             String idempotencyKey, String hash) {
        var replay = replayOf(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Instant now = clock.instant();
        CardAccount account = accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account", accountId));

        Optional.ofNullable(account.getLastUpgradeRequestAt())
                .map(CreditPolicy::cooldownEndsAt)
                .filter(now::isBefore)
                .ifPresent(retryAt -> {
                    throw new CooldownActiveException(retryAt);
                });

        Customer customer = customers.findById(account.getCustomerId())
                .orElseThrow(() -> new NotFoundException("Customer", account.getCustomerId()));
        if (newIncome != null) {
            customer.updateDeclaredIncome(newIncome, now);
        }

        int score = bureau.scoreFor(customer.getCpf());
        UpgradeFacts facts = factsFor(account, customer.getDeclaredMonthlyIncome(), score, now);
        UpgradeDecision result = CreditPolicy.decideUpgrade(facts);

        UUID requestId = UUID.randomUUID();
        DecisionRecord decision = decisions.save(new DecisionRecord(SubjectType.UPGRADE_REQUEST, requestId,
                result.outcome().name(), result.policyVersion(), inputsOf(facts), result.reasons(), now));
        UpgradeRequest request = new UpgradeRequest(requestId, idempotencyKey, hash, account,
                facts.declaredMonthlyIncome(), score, decision.getId(), now);

        account.recordUpgradeRequest(now);
        switch (result.outcome()) {
            case REJECTED -> request.reject(now);
            case KEPT -> request.keep(now);
            case APPROVED -> {
                request.approve(result.targetVariant(), result.newLimit(), now);
                account.upgradeTo(result.targetVariant(), result.newLimit(), now);
            }
        }
        // Every request writes the account row (cooldown time, plus the new variant when
        // approved). Flush now so the version check runs here: of two concurrent requests
        // for one account, whatever their outcome, only one commits, and a lost race fails
        // before a card is issued.
        accounts.saveAndFlush(account);
        if (result.outcome() == UpgradeDecision.Outcome.APPROVED) {
            cardIssuer.issueFor(account);
            request.markApplied(now);
            outbox.append("card_account", account.getId(), "AccountUpgraded", Map.of(
                    "accountId", account.getId().toString(),
                    "upgradeRequestId", requestId.toString(),
                    "fromVariant", request.getFromVariant().name(),
                    "toVariant", account.getVariant().name(),
                    "creditLimit", account.getCreditLimit().toPlainString()));
        }
        requests.save(request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("upgradeRequestId", requestId.toString());
        payload.put("accountId", accountId.toString());
        payload.put("status", request.getStatus().name());
        payload.put("reasonCodes", result.reasons().stream().map(Reason::code).toList());
        outbox.append("upgrade_request", requestId, "UpgradeDecided", payload);

        meters.counter("cards.upgrades.decided", "outcome", result.outcome().name()).increment();
        log.info("upgrade decided id={} account={} cpf={} outcome={} reasons={}", requestId, accountId,
                Cpf.mask(customer.getCpf()), result.outcome(), result.reasons().stream().map(Reason::code).toList());
        return new Created<>(UpgradeRequestView.of(request, decision), false);
    }

    private UpgradeFacts factsFor(CardAccount account, BigDecimal income, int score, Instant now) {
        List<PaymentHistoryEntry> statements = history.findByAccountIdOrderByReferenceMonthDesc(account.getId());
        long onTime = statements.stream().filter(PaymentHistoryEntry::isPaidOnTime).count();
        // Rounded DOWN: 0.899 must not become 0.90 and pass the on-time rule.
        BigDecimal onTimeRatio = statements.isEmpty() ? BigDecimal.ONE.setScale(2)
                : BigDecimal.valueOf(onTime).divide(BigDecimal.valueOf(statements.size()), 2, RoundingMode.DOWN);
        BigDecimal utilization = account.getCreditLimit().signum() == 0 || statements.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : statements.getFirst().getStatementBalance().divide(account.getCreditLimit(), 2, RoundingMode.DOWN);
        long monthsActive = ChronoUnit.MONTHS.between(
                LocalDate.ofInstant(account.getOpenedAt(), ZoneOffset.UTC), LocalDate.ofInstant(now, ZoneOffset.UTC));
        return new UpgradeFacts(account.getVariant(), account.getCreditLimit(), monthsActive, statements.size(),
                onTimeRatio, utilization, income, score);
    }

    private static Map<String, Object> inputsOf(UpgradeFacts f) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("currentVariant", f.currentVariant().name());
        inputs.put("currentLimit", f.currentLimit().toPlainString());
        inputs.put("monthsActive", f.monthsActive());
        inputs.put("statementCount", f.statementCount());
        inputs.put("onTimeRatio", f.onTimeRatio().toPlainString());
        inputs.put("utilization", f.utilization().toPlainString());
        inputs.put("declaredMonthlyIncome", f.declaredMonthlyIncome().toPlainString());
        inputs.put("bureauScore", f.score());
        return inputs;
    }
}
