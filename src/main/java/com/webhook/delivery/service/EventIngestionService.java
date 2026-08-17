package com.webhook.delivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.domain.EndpointStatus;
import com.webhook.delivery.domain.WebhookEndpoint;
import com.webhook.delivery.domain.WebhookEvent;
import com.webhook.delivery.dto.EventResponse;
import com.webhook.delivery.dto.IngestEventRequest;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.repository.WebhookEndpointRepository;
import com.webhook.delivery.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestionService.class);

    private final WebhookEventRepository eventRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final DeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public EventIngestionService(
            WebhookEventRepository eventRepository,
            WebhookEndpointRepository endpointRepository,
            DeliveryRepository deliveryRepository,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventResponse ingestEvent(String tenantId, IngestEventRequest request) {
        // Idempotency check: check if eventId was already submitted for this tenant
        Optional<WebhookEvent> existingOpt = eventRepository.findByTenantIdAndEventIdExternal(tenantId, request.getEventId());
        if (existingOpt.isPresent()) {
            WebhookEvent existing = existingOpt.get();
            log.info("Duplicate event ingestion attempt for eventId '{}' in tenant '{}'. Returning existing event.",
                    request.getEventId(), tenantId);
            return EventResponse.from(existing, "ALREADY_INGESTED");
        }

        // Serialize payload safely
        String payloadJson;
        try {
            JsonNode payloadNode = request.getPayload();
            payloadJson = objectMapper.writeValueAsString(payloadNode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload structure: " + e.getMessage(), e);
        }

        // Persist event
        WebhookEvent event = new WebhookEvent(tenantId, request.getEventId(), request.getType(), payloadJson);
        WebhookEvent savedEvent;
        try {
            savedEvent = eventRepository.save(event);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Concurrent duplicate event ingestion caught for eventId '{}' in tenant '{}'. Returning existing event.",
                    request.getEventId(), tenantId);
            WebhookEvent existing = eventRepository.findByTenantIdAndEventIdExternal(tenantId, request.getEventId())
                    .orElseThrow(() -> e);
            return EventResponse.from(existing, "ALREADY_INGESTED");
        }

        // Fan-out: query matching active subscriptions
        List<WebhookEndpoint> activeEndpoints = endpointRepository.findAllByTenantIdAndStatus(tenantId, EndpointStatus.ACTIVE);
        List<Delivery> pendingDeliveries = new ArrayList<>();

        for (WebhookEndpoint endpoint : activeEndpoints) {
            if (endpoint.isSubscribedTo(request.getType())) {
                Delivery delivery = new Delivery(savedEvent.getId(), endpoint.getId(), tenantId);
                pendingDeliveries.add(delivery);
            }
        }

        if (!pendingDeliveries.isEmpty()) {
            deliveryRepository.saveAll(pendingDeliveries);
            log.info("Ingested event '{}' (id={}) for tenant '{}'. Created {} pending deliveries.",
                    savedEvent.getEventIdExternal(), savedEvent.getId(), tenantId, pendingDeliveries.size());
        } else {
            log.info("Ingested event '{}' (id={}) for tenant '{}'. No active subscriptions matched type '{}'.",
                    savedEvent.getEventIdExternal(), savedEvent.getId(), tenantId, request.getType());
        }

        return EventResponse.from(savedEvent, "ACCEPTED");
    }
}
