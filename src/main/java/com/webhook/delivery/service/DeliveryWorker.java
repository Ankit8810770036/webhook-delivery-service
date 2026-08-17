package com.webhook.delivery.service;

import com.webhook.delivery.domain.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "webhook.delivery.worker-enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryClaimService claimService;
    private final DeliveryExecutorService executorService;
    private final ExecutorService workerPool;

    public DeliveryWorker(
            DeliveryClaimService claimService,
            DeliveryExecutorService executorService,
            @Value("${webhook.delivery.worker-pool-size:10}") int poolSize
    ) {
        this.claimService = claimService;
        this.executorService = executorService;
        this.workerPool = Executors.newFixedThreadPool(poolSize, Thread.ofVirtual().name("webhook-worker-", 0).factory());
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.poll-interval-ms:1000}")
    public void pollAndProcessDueDeliveries() {
        try {
            List<Delivery> claimed = claimService.claimDueDeliveries();
            if (claimed.isEmpty()) {
                return;
            }

            log.info("Claimed {} due deliveries for dispatch", claimed.size());
            for (Delivery delivery : claimed) {
                workerPool.submit(() -> {
                    try {
                        executorService.executeDelivery(delivery.getId());
                    } catch (Exception e) {
                        log.error("Unexpected error executing delivery {}: {}", delivery.getId(), e.getMessage(), e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error during due delivery polling cycle: {}", e.getMessage(), e);
        }
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down webhook delivery worker pool...");
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
