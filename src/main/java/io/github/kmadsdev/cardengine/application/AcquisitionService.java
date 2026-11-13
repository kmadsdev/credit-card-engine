package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.Views.ApplicationView;
import io.github.kmadsdev.cardengine.application.Views.Created;
import io.github.kmadsdev.cardengine.application.port.CreditBureau;
import io.github.kmadsdev.cardengine.domain.Cpf;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.domain.policy.AcquisitionDecision;
import io.github.kmadsdev.cardengine.domain.policy.ApplicantFacts;
import io.github.kmadsdev.cardengine.domain.policy.CreditPolicy;
import io.github.kmadsdev.cardengine.domain.policy.Reason;
import io.github.kmadsdev.cardengine.persistence.Card;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.CardApplication;
import io.github.kmadsdev.cardengine.persistence.CardApplicationRepository;
import io.github.kmadsdev.cardengine.persistence.Customer;
import io.github.kmadsdev.cardengine.persistence.CustomerRepository;
import io.github.kmadsdev.cardengine.persistence.DecisionRecord;
import io.github.kmadsdev.cardengine.persistence.DecisionRecord.SubjectType;
import io.github.kmadsdev.cardengine.persistence.DecisionRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acquisition flow for a new client, in one transaction:
 * <ol>
 *   <li>store the application (SUBMITTED)</li>
 *   <li>validation rules: CPF, age, duplicate customer (VALIDATED or REJECTED)</li>
 *   <li>simulated bureau score, eligibility, limit and variant (APPROVED or REJECTED)</li>
 *   <li>customer and card account (ACCOUNT_OPENED)</li>
 *   <li>card from the vault (CARD_ISSUED)</li>
 * </ol>
 * Each decision is saved as a {@link DecisionRecord}; each step writes an outbox event.
 */
@Service
public class AcquisitionService {

    private static final Logger log = LoggerFactory.getLogger(AcquisitionService.class);

    public record SubmitApplication(String cpf, String fullName, LocalDate birthDate, BigDecimal declaredMonthlyIncome) {
    }

    private final CardApplicationRepository applications;
    private final CustomerRepository customers;
    private final CardAccountRepository accounts;
    private final DecisionRecordRepository decisions;
    private final CreditBureau bureau;
    private final CardIssuer cardIssuer;
    private final Outbox outbox;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;
    private final Clock clock;

    public AcquisitionService(CardApplicationRepository applications, CustomerRepository customers,
                              CardAccountRepository accounts, DecisionRecordRepository decisions,
                              CreditBureau bureau, CardIssuer cardIssuer, Outbox outbox,
                              TransactionTemplate tx, MeterRegistry meters, Clock clock) {
        this.applications = applications;
        this.customers = customers;
        this.accounts = accounts;
        this.decisions = decisions;
        this.bureau = bureau;
        this.cardIssuer = cardIssuer;
        this.outbox = outbox;
        this.tx = tx;
        this.meters = meters;
        this.clock = clock;
    }

    public Created<ApplicationView> submit(SubmitApplication command, String idempotencyKey) {
        String cpf = Cpf.normalize(command.cpf());
        BigDecimal income = Money.of(command.declaredMonthlyIncome());
        String hash = RequestHash.of(cpf, command.fullName(), command.birthDate(), income.toPlainString());
        try {
            return tx.execute(status -> submitInTransaction(command, cpf, income, idempotencyKey, hash));
        } catch (DataIntegrityViolationException e) {
            // Two requests raced: the same Idempotency-Key, or the same CPF under two keys.
            return replayOf(idempotencyKey, hash)
                    .orElseThrow(() -> new ConcurrentUpdateException(
                            "Another application for this CPF was created at the same time", e));
        }
    }

    private Optional<Created<ApplicationView>> replayOf(String idempotencyKey, String hash) {
        return applications.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReuseException();
            }
            return new Created<>(view(existing), true);
        });
    }

    private Created<ApplicationView> submitInTransaction(SubmitApplication command, String cpf, BigDecimal income,
                                                         String idempotencyKey, String hash) {
        var replay = replayOf(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Instant now = clock.instant();
        CardApplication application = applications.save(new CardApplication(idempotencyKey, hash, cpf,
                command.fullName().strip(), command.birthDate(), income, now));

        int age = Period.between(command.birthDate(), LocalDate.ofInstant(now, ZoneOffset.UTC)).getYears();
        ApplicantFacts facts = new ApplicantFacts(Cpf.isValid(cpf), age, customers.existsByCpf(cpf), income);

        List<Reason> validationFailures = CreditPolicy.validate(facts);
        if (!validationFailures.isEmpty()) {
            DecisionRecord decision = record(application, "REJECTED", facts, null, validationFailures);
            application.reject(decision.getId(), null, now);
            return finish(application, decision, "REJECTED");
        }
        application.markValidated(now);

        int score = bureau.scoreFor(cpf);
        AcquisitionDecision result = CreditPolicy.decideAcquisition(facts, score);
        DecisionRecord decision = record(application, result.approved() ? "APPROVED" : "REJECTED",
                facts, score, result.reasons());
        if (!result.approved()) {
            application.reject(decision.getId(), score, now);
            return finish(application, decision, "REJECTED");
        }

        application.approve(result.variant(), result.limit(), score, decision.getId(), now);
        Customer customer = customers.save(new Customer(cpf, command.fullName().strip(), command.birthDate(), income, now));
        CardAccount account = accounts.save(new CardAccount(customer.getId(), result.variant(), result.limit(), now));
        application.accountOpened(customer.getId(), account.getId(), now);
        outbox.append("card_account", account.getId(), "CardAccountOpened", Map.of(
                "accountId", account.getId().toString(),
                "customerId", customer.getId().toString(),
                "variant", account.getVariant().name(),
                "creditLimit", account.getCreditLimit().toPlainString()));

        Card card = cardIssuer.issueFor(account);
        application.cardIssued(card.getId(), now);
        return finish(application, decision, "APPROVED");
    }

    private DecisionRecord record(CardApplication application, String outcome, ApplicantFacts facts,
                                  Integer score, List<Reason> reasons) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("cpfValid", facts.cpfValid());
        inputs.put("age", facts.age());
        inputs.put("existingCustomer", facts.existingCustomer());
        inputs.put("declaredMonthlyIncome", facts.declaredMonthlyIncome().toPlainString());
        if (score != null) {
            inputs.put("bureauScore", score);
        }
        return decisions.save(new DecisionRecord(SubjectType.APPLICATION, application.getId(), outcome,
                CreditPolicy.VERSION, inputs, reasons, clock.instant()));
    }

    private Created<ApplicationView> finish(CardApplication application, DecisionRecord decision, String outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", application.getId().toString());
        payload.put("outcome", outcome);
        payload.put("reasonCodes", decision.getReasons().stream().map(Reason::code).toList());
        if (application.getApprovedVariant() != null) {
            payload.put("variant", application.getApprovedVariant().name());
            payload.put("creditLimit", application.getApprovedLimit().toPlainString());
        }
        outbox.append("card_application", application.getId(), "ApplicationDecided", payload);

        String variant = application.getApprovedVariant() == null ? "NONE" : application.getApprovedVariant().name();
        UUID applicationId = application.getId();
        String maskedCpf = Cpf.mask(application.getCpf());
        List<String> reasonCodes = decision.getReasons().stream().map(Reason::code).toList();
        AfterCommit.run(() -> {
            meters.counter("cards.applications.decided", "outcome", outcome, "variant", variant).increment();
            log.info("application decided id={} cpf={} outcome={} variant={} reasons={}",
                    applicationId, maskedCpf, outcome, variant, reasonCodes);
        });
        return new Created<>(ApplicationView.of(application, decision), false);
    }

    private ApplicationView view(CardApplication application) {
        DecisionRecord decision = application.getDecisionId() == null ? null
                : decisions.findById(application.getDecisionId()).orElse(null);
        return ApplicationView.of(application, decision);
    }
}
