package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardApplicationRepository extends JpaRepository<CardApplication, UUID> {

    Optional<CardApplication> findByIdempotencyKey(String idempotencyKey);

    Page<CardApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}
