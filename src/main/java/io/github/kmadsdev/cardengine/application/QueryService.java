package io.github.kmadsdev.cardengine.application;

import io.github.kmadsdev.cardengine.application.Views.AccountView;
import io.github.kmadsdev.cardengine.application.Views.ApplicationView;
import io.github.kmadsdev.cardengine.application.Views.CardView;
import io.github.kmadsdev.cardengine.application.Views.UpgradeRequestView;
import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import io.github.kmadsdev.cardengine.persistence.CardAccountRepository;
import io.github.kmadsdev.cardengine.persistence.CardApplication;
import io.github.kmadsdev.cardengine.persistence.CardApplicationRepository;
import io.github.kmadsdev.cardengine.persistence.CardRepository;
import io.github.kmadsdev.cardengine.persistence.DecisionRecord;
import io.github.kmadsdev.cardengine.persistence.DecisionRecordRepository;
import io.github.kmadsdev.cardengine.persistence.UpgradeRequest;
import io.github.kmadsdev.cardengine.persistence.UpgradeRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read side: lookups and paged lists. */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private final CardApplicationRepository applications;
    private final CardAccountRepository accounts;
    private final CardRepository cards;
    private final UpgradeRequestRepository upgrades;
    private final DecisionRecordRepository decisions;

    public QueryService(CardApplicationRepository applications, CardAccountRepository accounts,
                        CardRepository cards, UpgradeRequestRepository upgrades, DecisionRecordRepository decisions) {
        this.applications = applications;
        this.accounts = accounts;
        this.cards = cards;
        this.upgrades = upgrades;
        this.decisions = decisions;
    }

    public ApplicationView application(UUID id) {
        return applicationView(applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id)));
    }

    public Page<ApplicationView> applications(ApplicationStatus status, Pageable pageable) {
        Page<CardApplication> page = status == null
                ? applications.findAll(pageable)
                : applications.findByStatus(status, pageable);
        return page.map(this::applicationView);
    }

    public AccountView account(UUID id) {
        return AccountView.of(accounts.findById(id).orElseThrow(() -> new NotFoundException("Account", id)));
    }

    public Page<AccountView> accounts(Pageable pageable) {
        return accounts.findAll(pageable).map(AccountView::of);
    }

    public CardView card(UUID id) {
        return CardView.of(cards.findById(id).orElseThrow(() -> new NotFoundException("Card", id)));
    }

    public Page<CardView> cardsOfAccount(UUID accountId, Pageable pageable) {
        requireAccount(accountId);
        return cards.findByAccountId(accountId, pageable).map(CardView::of);
    }

    public UpgradeRequestView upgradeRequest(UUID id) {
        return upgradeView(upgrades.findById(id).orElseThrow(() -> new NotFoundException("Upgrade request", id)));
    }

    public Page<UpgradeRequestView> upgradeRequestsOfAccount(UUID accountId, Pageable pageable) {
        requireAccount(accountId);
        return upgrades.findByAccountId(accountId, pageable).map(this::upgradeView);
    }

    private void requireAccount(UUID accountId) {
        if (!accounts.existsById(accountId)) {
            throw new NotFoundException("Account", accountId);
        }
    }

    private ApplicationView applicationView(CardApplication a) {
        return ApplicationView.of(a, decision(a.getDecisionId()));
    }

    private UpgradeRequestView upgradeView(UpgradeRequest r) {
        return UpgradeRequestView.of(r, decision(r.getDecisionId()));
    }

    private DecisionRecord decision(UUID id) {
        return id == null ? null : decisions.findById(id).orElse(null);
    }
}
