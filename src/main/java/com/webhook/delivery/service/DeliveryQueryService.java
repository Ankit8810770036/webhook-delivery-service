package com.webhook.delivery.service;

import com.webhook.delivery.domain.*;
import com.webhook.delivery.dto.DeliveryDetailResponse;
import com.webhook.delivery.dto.DeliveryResponse;
import com.webhook.delivery.dto.RedriveResponse;
import com.webhook.delivery.exception.DeliveryNotRedrivableException;
import com.webhook.delivery.exception.ResourceNotFoundException;
import com.webhook.delivery.repository.DeliveryAttemptRepository;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.repository.WebhookEndpointRepository;
import com.webhook.delivery.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeliveryQueryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryQueryService.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEventRepository eventRepository;
    private final CircuitBreakerService circuitBreakerService;

    public DeliveryQueryService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookEventRepository eventRepository,
            CircuitBreakerService circuitBreakerService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.circuitBreakerService = circuitBreakerService;
    }

    @Transactional(readOnly = true)
    public List<DeliveryDetailResponse> getDeliveriesForEvent(String tenantId, UUID eventId) {
        // Enforce tenant scoping: verify event belongs to this tenant
        eventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        List<Delivery> deliveries = deliveryRepository.findAllByTenantIdAndEventIdOrderByCreatedAtDesc(tenantId, eventId);
        if (deliveries.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> deliveryIds = deliveries.stream().map(Delivery::getId).collect(Collectors.toList());
        List<DeliveryAttempt> allAttempts = attemptRepository.findAllByDeliveryIdIn(deliveryIds);
        Map<UUID, List<DeliveryAttempt>> attemptsByDeliveryId = allAttempts.stream()
                .collect(Collectors.groupingBy(DeliveryAttempt::getDeliveryId));

        Map<UUID, String> endpointUrlMap = new HashMap<>();
        List<UUID> endpointIds = deliveries.stream().map(Delivery::getEndpointId).distinct().collect(Collectors.toList());
        endpointRepository.findAllById(endpointIds).forEach(ep -> endpointUrlMap.put(ep.getId(), ep.getUrl()));

        return deliveries.stream().map(delivery -> {
            String endpointUrl = endpointUrlMap.getOrDefault(delivery.getEndpointId(), "Unknown");
            List<DeliveryAttempt> attempts = attemptsByDeliveryId.getOrDefault(delivery.getId(), Collections.emptyList());
            return DeliveryDetailResponse.from(delivery, endpointUrl, attempts);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> getDeliveriesForEndpoint(
            String tenantId,
            UUID endpointId,
            DeliveryStatus status,
            Instant fromTime,
            Instant toTime,
            Pageable pageable
    ) {
        // Enforce tenant scoping: verify endpoint belongs to this tenant
        endpointRepository.findByIdAndTenantId(endpointId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with ID: " + endpointId));

        // Filter directly at the database level
        Page<Delivery> deliveryPage;
        if (status == null && fromTime == null && toTime == null) {
            deliveryPage = deliveryRepository.findAllByTenantIdAndEndpointIdOrderByCreatedAtDesc(tenantId, endpointId, pageable);
        } else {
            deliveryPage = deliveryRepository.findFilteredEndpointDeliveries(
                    tenantId, endpointId, status, fromTime, toTime, pageable);
        }

        return deliveryPage.map(DeliveryResponse::from);
    }

    @Transactional
    public RedriveResponse redriveDelivery(String tenantId, UUID deliveryId) {
        Delivery delivery = deliveryRepository.findByIdAndTenantId(deliveryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with ID: " + deliveryId));

        if (delivery.getStatus() != DeliveryStatus.DEAD_LETTERED && delivery.getStatus() != DeliveryStatus.CANCELLED) {
            throw new DeliveryNotRedrivableException(
                    "Only DEAD_LETTERED or CANCELLED deliveries can be redriven. Current status: " + delivery.getStatus());
        }

        // Reset state for immediate re-attempt
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        delivery.setUpdatedAt(Instant.now());

        // Also reset circuit breaker for the endpoint if requested
        circuitBreakerService.reset(delivery.getEndpointId());

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Delivery {} redriven by tenant {}. Re-queued for immediate delivery.", deliveryId, tenantId);

        return new RedriveResponse(
                saved.getId(),
                saved.getStatus(),
                "Delivery re-queued successfully for immediate attempt",
                saved.getNextAttemptAt()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTenantDeliveryStats(String tenantId) {
        long success = deliveryRepository.countByTenantIdAndStatus(tenantId, DeliveryStatus.SUCCESS);
        long pending = deliveryRepository.countByTenantIdAndStatus(tenantId, DeliveryStatus.PENDING);
        long processing = deliveryRepository.countByTenantIdAndStatus(tenantId, DeliveryStatus.PROCESSING);
        long dead = deliveryRepository.countByTenantIdAndStatus(tenantId, DeliveryStatus.DEAD_LETTERED);

        Map<String, Long> stats = new HashMap<>();
        stats.put("successfulDeliveries", success);
        stats.put("pendingDeliveries", pending + processing);
        stats.put("deadLetteredDeliveries", dead);
        return stats;
    }
}
