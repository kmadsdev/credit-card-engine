package io.github.kmadsdev.cardengine.testsupport;

import io.github.kmadsdev.cardengine.domain.CardVariant;
import io.github.kmadsdev.cardengine.domain.Money;
import io.github.kmadsdev.cardengine.persistence.CardAccount;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.Customer;
import io.github.kmadsdev.cardengine.persistence.CustomerRepository;
import io.github.kmadsdev.cardengine.persistence.PaymentHistoryEntry;
import io.github.kmadsdev.cardengine.persistence.PaymentHistoryRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Builds an existing client straight in the database: customer, account opened some
 * months ago, and a simulated payment history. The upgrade tests start from here.
 */
public class Fixtures {

    private final CustomerRepository customers;
    private final CardAccountRepository accounts;
    private final PaymentHistoryRepository history;
    private final TestCreditBureau bureau;
    private final MutableClock clock;

    public Fixtures(CustomerRepository customers, CardAccountRepository accounts, PaymentHistoryRepository history,
                    TestCreditBureau bureau, MutableClock clock) {
        this.customers = customers;
        this.accounts = accounts;
        this.history = history;
        this.bureau = bureau;
        this.clock = clock;
    }

    public record ExistingClient(String cpf, CardAccount account) {
    }

    /**
     * @param statements     how many monthly statements to create (most recent first)
     * @param lateStatements how many of them were paid late
     * @param balance        balance of every statement
     */
    @Transactional
    public ExistingClient existingClient(CardVariant variant, String limit, int monthsActive, String income,
                                         int score, int statements, int lateStatements, String balance) {
        String cpf = TestCpfs.random();
        bureau.setScore(cpf, score);
        Instant now = clock.instant();
        Instant openedAt = now.atZone(ZoneOffset.UTC).minusMonths(monthsActive).toInstant();
        Customer customer = customers.save(new Customer(cpf, "Test Client", LocalDate.of(1990, 5, 20),
                Money.of(income), openedAt));
        CardAccount account = accounts.save(new CardAccount(customer.getId(), variant, Money.of(limit), openedAt));
        LocalDate month = LocalDate.ofInstant(now, ZoneOffset.UTC).withDayOfMonth(1);
        for (int i = 1; i <= statements; i++) {
            history.save(new PaymentHistoryEntry(account.getId(), month.minusMonths(i), Money.of(balance),
                    i > lateStatements));
        }
        return new ExistingClient(cpf, account);
    }
}
