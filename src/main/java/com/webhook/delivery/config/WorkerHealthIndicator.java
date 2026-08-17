package com.webhook.delivery.config;

import com.webhook.delivery.domain.DeliveryStatus;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.repository.TenantRepository;
import com.webhook.delivery.service.DeliveryWorker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class WorkerHealthIndicator implements HealthIndicator {

    private final TenantRepository tenantRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryWorker deliveryWorker;

    public WorkerHealthIndicator(
            TenantRepository tenantRepository,
            DeliveryRepository deliveryRepository,
            DeliveryWorker deliveryWorker
    ) {
        this.tenantRepository = tenantRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryWorker = deliveryWorker;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        try {
            // Check Database Connectivity
            long tenantCount = tenantRepository.count();
            details.put("databaseConnectivity", "UP");
            details.put("activeTenants", tenantCount);

            // Delivery queues status
            long pendingDeliveries = deliveryRepository.countByStatus(DeliveryStatus.PENDING);
            long processingDeliveries = deliveryRepository.countByStatus(DeliveryStatus.PROCESSING);
            long deadLetteredDeliveries = deliveryRepository.countByStatus(DeliveryStatus.DEAD_LETTERED);
            long successfulDeliveries = deliveryRepository.countByStatus(DeliveryStatus.SUCCESS);

            details.put("pendingDeliveries", pendingDeliveries);
            details.put("processingDeliveries", processingDeliveries);
            details.put("deadLetteredDeliveries", deadLetteredDeliveries);
            details.put("successfulDeliveries", successfulDeliveries);

            // Worker Pool status
            boolean poolActive = deliveryWorker != null && !deliveryWorker.getWorkerPool().isShutdown();
            details.put("workerPoolStatus", poolActive ? "ACTIVE" : "SHUTDOWN");

            return Health.up().withDetails(details).build();
        } catch (Exception e) {
            details.put("databaseConnectivity", "DOWN");
            details.put("error", e.getMessage());
            return Health.down().withDetails(details).build();
        }
    }
}
