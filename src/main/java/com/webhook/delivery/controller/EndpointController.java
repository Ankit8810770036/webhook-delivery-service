package com.webhook.delivery.controller;

import com.webhook.delivery.context.TenantContext;
import com.webhook.delivery.domain.DeliveryStatus;
import com.webhook.delivery.dto.*;
import com.webhook.delivery.service.DeliveryQueryService;
import com.webhook.delivery.service.EndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
@Tag(name = "Endpoints", description = "Webhook endpoint registration, testing, and lifecycle management")
public class EndpointController {

    private final EndpointService endpointService;
    private final DeliveryQueryService deliveryQueryService;

    public EndpointController(EndpointService endpointService, DeliveryQueryService deliveryQueryService) {
        this.endpointService = endpointService;
        this.deliveryQueryService = deliveryQueryService;
    }

    @PostMapping
    @Operation(summary = "Register a new webhook endpoint", description = "Registers target URL, event subscriptions, and generates an HMAC secret.")
    public ResponseEntity<EndpointResponse> registerEndpoint(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @Valid @RequestBody CreateEndpointRequest request
    ) {
        String tenantId = TenantContext.getTenantId();
        EndpointResponse response = endpointService.createEndpoint(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all webhook endpoints for the current tenant")
    public ResponseEntity<List<EndpointResponse>> listEndpoints(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader
    ) {
        String tenantId = TenantContext.getTenantId();
        List<EndpointResponse> responses = endpointService.listEndpoints(tenantId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get endpoint details by ID")
    public ResponseEntity<EndpointResponse> getEndpoint(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @PathVariable("id") UUID id
    ) {
        String tenantId = TenantContext.getTenantId();
        EndpointResponse response = endpointService.getEndpoint(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-disable a webhook endpoint", description = "Disables active deliveries while preserving audit history.")
    public ResponseEntity<EndpointResponse> disableEndpoint(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @PathVariable("id") UUID id
    ) {
        String tenantId = TenantContext.getTenantId();
        EndpointResponse response = endpointService.disableEndpoint(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Inbound self-test for webhook endpoint", description = "Sends a synthetic signed ping event to verify reachability and receiver validation.")
    public ResponseEntity<EndpointTestResponse> testEndpoint(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @PathVariable("id") UUID id
    ) {
        String tenantId = TenantContext.getTenantId();
        EndpointTestResponse response = endpointService.testEndpoint(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/deliveries")
    @Operation(summary = "Get paginated deliveries for an endpoint with database-level filtering")
    public ResponseEntity<Page<DeliveryResponse>> getEndpointDeliveries(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @RequestParam(value = "status", required = false) DeliveryStatus status,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        String tenantId = TenantContext.getTenantId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<DeliveryResponse> responses = deliveryQueryService.getDeliveriesForEndpoint(
                tenantId, id, status, from, to, pageable);
        return ResponseEntity.ok(responses);
    }
}
