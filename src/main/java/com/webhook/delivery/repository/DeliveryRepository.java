package com.webhook.delivery.repository;

import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    /**
     * Database-level due-delivery claiming using FOR UPDATE SKIP LOCKED.
     * Selects pending delivery IDs that are due for attempt and not locked by another active worker.
     */
    @Query(value = """
        SELECT id FROM deliveries
        WHERE status = 'PENDING'
          AND next_attempt_at <= :now
          AND (locked_until IS NULL OR locked_until < :now)
        ORDER BY next_attempt_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> claimDueDeliveryIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    /**
     * Locks claimed deliveries by assigning worker identity and lease expiration.
     */
    @Modifying
    @Query("""
        UPDATE Delivery d
        SET d.status = :status,
            d.lockedBy = :workerId,
            d.lockedUntil = :lockedUntil,
            d.updatedAt = :now
        WHERE d.id IN :ids
        """)
    int lockClaimedDeliveries(
            @Param("ids") List<UUID> ids,
            @Param("status") DeliveryStatus status,
            @Param("workerId") String workerId,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now
    );

    Optional<Delivery> findByIdAndTenantId(UUID id, String tenantId);

    List<Delivery> findAllByTenantIdAndEventIdOrderByCreatedAtDesc(String tenantId, UUID eventId);

    @Query("""
        SELECT d FROM Delivery d
        WHERE d.tenantId = :tenantId
          AND d.endpointId = :endpointId
          AND (:status IS NULL OR d.status = :status)
          AND (:fromTime IS NULL OR d.createdAt >= :fromTime)
          AND (:toTime IS NULL OR d.createdAt <= :toTime)
        ORDER BY d.createdAt DESC
        """)
    Page<Delivery> findFilteredEndpointDeliveries(
            @Param("tenantId") String tenantId,
            @Param("endpointId") UUID endpointId,
            @Param("status") DeliveryStatus status,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );

    long countByStatus(DeliveryStatus status);
}
