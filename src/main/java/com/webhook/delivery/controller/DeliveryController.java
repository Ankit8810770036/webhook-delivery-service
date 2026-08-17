package com.webhook.delivery.controller;

import com.webhook.delivery.context.TenantContext;
import com.webhook.delivery.dto.RedriveResponse;
import com.webhook.delivery.service.DeliveryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "Deliveries", description = "Webhook delivery status management and manual redrive operations")
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;

    public DeliveryController(DeliveryQueryService deliveryQueryService) {
        this.deliveryQueryService = deliveryQueryService;
    }

    @PostMapping("/{id}/redrive")
    @Operation(summary = "Manually redrive a failed delivery", description = "Re-queues a DEAD_LETTERED or CANCELLED delivery for immediate attempt.")
    public ResponseEntity<RedriveResponse> redriveDelivery(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @PathVariable("id") UUID id
    ) {
        String tenantId = TenantContext.getTenantId();
        RedriveResponse response = deliveryQueryService.redriveDelivery(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get tenant-isolated delivery metrics", description = "Returns successful, pending, and dead-lettered delivery counts for the authenticated tenant.")
    public ResponseEntity<java.util.Map<String, Long>> getTenantDeliveryStats(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader
    ) {
        String tenantId = TenantContext.getTenantId();
        java.util.Map<String, Long> stats = deliveryQueryService.getTenantDeliveryStats(tenantId);
        return ResponseEntity.ok(stats);
    }
}
