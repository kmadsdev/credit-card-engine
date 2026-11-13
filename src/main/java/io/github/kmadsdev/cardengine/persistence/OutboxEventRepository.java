package io.github.kmadsdev.cardengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest unpublished events, locked for this transaction. SKIP LOCKED lets two
     * publisher instances run at the same time without sending the same row twice.
     */
    @Query(value = """
            select * from outbox_event
            where published_at is null
            order by created_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(int batchSize);

    long countByPublishedAtIsNull();
}
