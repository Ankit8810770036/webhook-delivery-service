package com.webhook.delivery.controller;

import com.webhook.delivery.context.TenantContext;
import com.webhook.delivery.dto.DeliveryDetailResponse;
import com.webhook.delivery.dto.EventResponse;
import com.webhook.delivery.dto.IngestEventRequest;
import com.webhook.delivery.service.DeliveryQueryService;
import com.webhook.delivery.service.EventIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Webhook event ingestion and fan-out delivery tracking")
public class EventController {

    private final EventIngestionService eventIngestionService;
    private final DeliveryQueryService deliveryQueryService;

    public EventController(EventIngestionService eventIngestionService, DeliveryQueryService deliveryQueryService) {
        this.eventIngestionService = eventIngestionService;
        this.deliveryQueryService = deliveryQueryService;
    }

    @PostMapping
    @Operation(summary = "Ingest a new event from producer", description = "Fast, idempotent ingestion with immediate 202 Accepted response.")
    public ResponseEntity<EventResponse> ingestEvent(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @Valid @RequestBody IngestEventRequest request
    ) {
        String tenantId = TenantContext.getTenantId();
        EventResponse response = eventIngestionService.ingestEvent(tenantId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}/deliveries")
    @Operation(summary = "Get delivery attempts for an ingested event", description = "Audit trail of every delivery attempt, latency, HTTP code, and response snippet.")
    public ResponseEntity<List<DeliveryDetailResponse>> getEventDeliveries(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @PathVariable("id") UUID id
    ) {
        String tenantId = TenantContext.getTenantId();
        List<DeliveryDetailResponse> responses = deliveryQueryService.getDeliveriesForEvent(tenantId, id);
        return ResponseEntity.ok(responses);
    }
}
