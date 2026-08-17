package com.webhook.delivery.integration;

import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.domain.DeliveryStatus;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.service.DeliveryClaimService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryClaimService claimService;

    @Test
    @DisplayName("Concurrent workers never claim the same delivery twice (FOR UPDATE SKIP LOCKED correctness)")
    void testConcurrentWorkersNeverClaimSameDelivery() throws InterruptedException, ExecutionException {
        int totalDeliveries = 50;
        int workerThreads = 8;
        String tenantId = "tenant-concurrency-test";

        // Seed 50 pending deliveries due right now
        List<Delivery> testDeliveries = new ArrayList<>();
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();

        for (int i = 0; i < totalDeliveries; i++) {
            Delivery d = new Delivery(eventId, endpointId, tenantId);
            d.setStatus(DeliveryStatus.PENDING);
            d.setNextAttemptAt(now.minusSeconds(10)); // overdue
            testDeliveries.add(d);
        }
        deliveryRepository.saveAll(testDeliveries);

        // Run concurrent claiming
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int i = 0; i < workerThreads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await(); // Synchronize thread start
                List<UUID> claimedByThisWorker = new ArrayList<>();

                // Poll repeatedly until no more due work
                for (int round = 0; round < 10; round++) {
                    List<Delivery> claimed = claimService.claimDueDeliveries();
                    if (claimed.isEmpty()) {
                        Thread.sleep(20);
                        continue;
                    }
                    for (Delivery d : claimed) {
                        claimedByThisWorker.add(d.getId());
                    }
                }
                return claimedByThisWorker;
            }));
        }

        startLatch.countDown(); // Trigger all workers simultaneously

        List<UUID> allClaimedIds = new ArrayList<>();
        for (Future<List<UUID>> future : futures) {
            allClaimedIds.addAll(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify: Every claimed delivery ID must be unique
        Set<UUID> uniqueClaimedIds = new HashSet<>(allClaimedIds);
        assertEquals(allClaimedIds.size(), uniqueClaimedIds.size(),
                "Duplicate claims detected! Total claims: " + allClaimedIds.size() + ", unique claims: " + uniqueClaimedIds.size());
    }
}
