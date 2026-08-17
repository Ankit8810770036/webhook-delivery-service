package com.webhook.delivery.service;

import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.domain.DeliveryStatus;
import com.webhook.delivery.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryClaimService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryClaimService.class);

    private final DeliveryRepository deliveryRepository;
    private final String workerInstanceId;
    private final long leaseDurationSeconds;
    private final int batchSize;

    public DeliveryClaimService(
            DeliveryRepository deliveryRepository,
            @Value("${webhook.delivery.lease-duration-seconds:60}") long leaseDurationSeconds,
            @Value("${webhook.delivery.batch-size:20}") int batchSize
    ) {
        this.deliveryRepository = deliveryRepository;
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.batchSize = batchSize;
        this.workerInstanceId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Atomically claims due deliveries directly at the database using FOR UPDATE SKIP LOCKED.
     * Prevents lock contention and guarantees no two workers process the same delivery concurrently.
     */
    @Transactional
    public List<Delivery> claimDueDeliveries() {
        Instant now = Instant.now();
        List<UUID> dueIds = deliveryRepository.claimDueDeliveryIds(now, batchSize);

        if (dueIds.isEmpty()) {
            return Collections.emptyList();
        }

        Instant leaseExpiry = now.plusSeconds(leaseDurationSeconds);
        deliveryRepository.lockClaimedDeliveries(dueIds, DeliveryStatus.PROCESSING, workerInstanceId, leaseExpiry, now);

        List<Delivery> claimed = deliveryRepository.findAllById(dueIds);
        log.debug("Worker '{}' claimed {} due deliveries with lease until {}", workerInstanceId, claimed.size(), leaseExpiry);
        return claimed;
    }

    public String getWorkerInstanceId() {
        return workerInstanceId;
    }
}
