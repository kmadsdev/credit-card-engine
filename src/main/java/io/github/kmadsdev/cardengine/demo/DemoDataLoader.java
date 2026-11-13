package io.github.kmadsdev.cardengine.demo;

import io.github.kmadsdev.cardengine.application.port.CreditBureau;
import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.Cpf;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.Customer;
import io.github.kmadsdev.cardengine.persistence.CustomerRepository;
import io.github.kmadsdev.cardengine.persistence.PaymentHistoryEntry;
import io.github.kmadsdev.cardengine.persistence.PaymentHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Profile {@code demo} only. Creates one existing client so the upgrade flow can be
 * tried by hand: a CREDIT_BASE account opened 13 months ago with 12 statements paid
 * on time. The CPF is synthetic and generated at start-up; it is never printed.
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final int MIN_DEMO_SCORE = 650;

    private final CustomerRepository customers;
    private final CardAccountRepository accounts;
    private final PaymentHistoryRepository history;
    private final CreditBureau bureau;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    DemoDataLoader(CustomerRepository customers, CardAccountRepository accounts, PaymentHistoryRepository history,
                   CreditBureau bureau, Clock clock) {
        this.customers = customers;
        this.accounts = accounts;
        this.history = history;
        this.bureau = bureau;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.count() > 0) {
            return;
        }
        Instant now = clock.instant();
        Instant openedAt = now.atZone(ZoneOffset.UTC).minusMonths(13).toInstant();
        Customer customer = customers.save(new Customer(syntheticCpfWithGoodScore(), "Demo Client",
                LocalDate.of(1990, 1, 15), Money.of("5000.00"), openedAt));
        CardAccount account = accounts.save(new CardAccount(customer.getId(), CardVariant.CREDIT_BASE,
                Money.of("2000.00"), openedAt));
        LocalDate month = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1);
        for (int i = 1; i <= 12; i++) {
            history.save(new PaymentHistoryEntry(account.getId(), month.minusMonths(i), Money.of("1700.00"), true));
        }
        log.info("demo account ready id={} variant={} limit={}", account.getId(), account.getVariant(),
                account.getCreditLimit());
    }

    /** A random valid CPF whose simulated score gets credit, so the demo upgrade is approved. */
    private String syntheticCpfWithGoodScore() {
        while (true) {
            StringBuilder base = new StringBuilder(9);
            for (int i = 0; i < 9; i++) {
                base.append(random.nextInt(10));
            }
            String cpf = Cpf.completeWithCheckDigits(base.toString());
            if (Cpf.isValid(cpf) && bureau.scoreFor(cpf) >= MIN_DEMO_SCORE) {
                return cpf;
            }
        }
    }
}
