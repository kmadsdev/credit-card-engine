package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.application.QueryService;
import io.github.kmadsdev.cardengine.application.UpgradeService;
import io.github.kmadsdev.cardengine.application.UpgradeService.RequestUpgrade;
import io.github.kmadsdev.cardengine.application.Views.AccountView;
import io.github.kmadsdev.cardengine.application.Views.CardView;
import io.github.kmadsdev.cardengine.application.Views.UpgradeRequestView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Card accounts, their cards and the upgrade flow")
public class AccountController {

    private final QueryService queries;
    private final UpgradeService upgrades;

    public AccountController(QueryService queries, UpgradeService upgrades) {
        this.queries = queries;
        this.upgrades = upgrades;
    }

    @GetMapping("/{id}")
    public AccountView get(@PathVariable UUID id) {
        return queries.account(id);
    }

    @GetMapping
    public PageResponse<AccountView> list(
            @PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(queries.accounts(pageable));
    }

    @GetMapping("/{id}/cards")
    public PageResponse<CardView> cards(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(queries.cardsOfAccount(id, pageable));
    }

    @PostMapping("/{id}/upgrade-requests")
    @Operation(summary = "Ask for the next variant. Returns APPLIED, KEPT or REJECTED with reasons.")
    public ResponseEntity<UpgradeRequestView> requestUpgrade(
            @PathVariable UUID id,
            @RequestHeader(Idempotency.HEADER) @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            @Valid @RequestBody(required = false) Requests.CreateUpgradeRequest body) {
        var command = new RequestUpgrade(body == null ? null : body.declaredMonthlyIncome());
        var created = upgrades.request(id, command, idempotencyKey);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/upgrade-requests/{rid}").buildAndExpand(created.body().id()).toUri();
        return Idempotency.respond(created, location);
    }

    @GetMapping("/{id}/upgrade-requests")
    public PageResponse<UpgradeRequestView> upgradeRequests(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(queries.upgradeRequestsOfAccount(id, pageable));
    }
}
