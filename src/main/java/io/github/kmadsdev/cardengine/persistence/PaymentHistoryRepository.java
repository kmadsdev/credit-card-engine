package io.github.kmadsdev.cardengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistoryEntry, Long> {

    List<PaymentHistoryEntry> findByAccountIdOrderByReferenceMonthDesc(UUID accountId);
}
