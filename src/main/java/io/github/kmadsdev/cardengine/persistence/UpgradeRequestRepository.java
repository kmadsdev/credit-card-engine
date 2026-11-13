package io.github.kmadsdev.cardengine.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UpgradeRequestRepository extends JpaRepository<UpgradeRequest, UUID> {

    Optional<UpgradeRequest> findByIdempotencyKey(String idempotencyKey);

    Page<UpgradeRequest> findByAccountId(UUID accountId, Pageable pageable);
}
