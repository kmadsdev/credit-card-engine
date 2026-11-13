package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.application.AcquisitionService;
import io.github.kmadsdev.cardengine.application.AcquisitionService.SubmitApplication;
import io.github.kmadsdev.cardengine.application.QueryService;
import io.github.kmadsdev.cardengine.application.Views.ApplicationView;
import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Acquisition flow for new clients")
public class ApplicationController {

    private final AcquisitionService acquisition;
    private final QueryService queries;

    public ApplicationController(AcquisitionService acquisition, QueryService queries) {
        this.acquisition = acquisition;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Submit an application. Runs the whole acquisition flow and returns the decision.")
    public ResponseEntity<ApplicationView> submit(
            @RequestHeader(Idempotency.HEADER) @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            @Valid @RequestBody Requests.CreateApplication body) {
        var created = acquisition.submit(new SubmitApplication(body.cpf(), body.fullName(), body.birthDate(),
                body.declaredMonthlyIncome()), idempotencyKey);
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(created.body().id()).toUri();
        return Idempotency.respond(created, location);
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable UUID id) {
        return queries.application(id);
    }

    @GetMapping
    public PageResponse<ApplicationView> list(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(queries.applications(status, pageable));
    }
}
