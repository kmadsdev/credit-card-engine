package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.application.QueryService;
import io.github.kmadsdev.cardengine.application.Views.CardView;
import io.github.kmadsdev.cardengine.application.Views.UpgradeRequestView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Lookups", description = "Cards and upgrade requests by id")
public class CardController {

    private final QueryService queries;

    public CardController(QueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/cards/{id}")
    public CardView card(@PathVariable UUID id) {
        return queries.card(id);
    }

    @GetMapping("/upgrade-requests/{id}")
    public UpgradeRequestView upgradeRequest(@PathVariable UUID id) {
        return queries.upgradeRequest(id);
    }
}
