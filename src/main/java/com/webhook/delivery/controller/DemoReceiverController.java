package com.webhook.delivery.controller;

import com.webhook.delivery.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demo Sink", description = "Mock receiver endpoints for interactive demos, simulating success, 500 errors, timeouts, and verification")
public class DemoReceiverController {

    private static final Logger log = LoggerFactory.getLogger(DemoReceiverController.class);

    private final SignatureService signatureService;
    private final List<Map<String, Object>> receivedDeliveries = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();

    public DemoReceiverController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping("/sink")
    @Operation(summary = "Healthy mock webhook receiver returning 200 OK")
    public ResponseEntity<Map<String, Object>> healthySink(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Delivery-Id", required = false) String deliveryId,
            @RequestHeader(value = "X-Attempt-Number", required = false) String attemptNumber,
            @RequestBody String payload
    ) {
        log.info("Received webhook at /api/demo/sink: DeliveryId={}, Attempt={}, CorrelationId={}",
                deliveryId, attemptNumber, correlationId);

        Map<String, Object> record = new HashMap<>();
        record.put("deliveryId", deliveryId);
        record.put("attemptNumber", attemptNumber);
        record.put("correlationId", correlationId);
        record.put("signature", signature);
        record.put("timestamp", timestamp);
        record.put("payload", payload);
        record.put("receivedAt", new Date().toString());
        record.put("status", "SUCCESS_200");
        receivedDeliveries.add(0, record);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Webhook received successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sink/flaky")
    @Operation(summary = "Flaky receiver that fails 3 times with 500 then recovers with 200 OK")
    public ResponseEntity<Map<String, Object>> flakySink(
            @RequestHeader(value = "X-Delivery-Id", defaultValue = "default") String deliveryId,
            @RequestBody String payload
    ) {
        AtomicInteger count = failureCounters.computeIfAbsent(deliveryId, k -> new AtomicInteger(0));
        int attempt = count.incrementAndGet();

        if (attempt <= 3) {
            log.warn("Flaky sink simulating 500 failure on attempt {} for delivery {}", attempt, deliveryId);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Internal server error during deploy simulation");
            err.put("attempt", attempt);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }

        log.info("Flaky sink recovering with 200 OK on attempt {} for delivery {}", attempt, deliveryId);
        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "RECOVERED");
        ok.put("attempt", attempt);
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/sink/failing")
    @Operation(summary = "Always failing receiver returning 500 Internal Server Error")
    public ResponseEntity<Map<String, Object>> alwaysFailingSink(@RequestBody String payload) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", "Simulated permanent service outage");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }

    @PostMapping("/sink/hanging")
    @Operation(summary = "Hanging receiver simulating connection timeout (> 10s sleep)")
    public ResponseEntity<Map<String, Object>> hangingSink(@RequestBody String payload) {
        try {
            Thread.sleep(12000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, Object> ok = new HashMap<>();
        ok.put("status", "TIMED_OUT");
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/received")
    @Operation(summary = "List all webhooks received by the demo sink")
    public ResponseEntity<List<Map<String, Object>>> getReceivedDeliveries() {
        return ResponseEntity.ok(receivedDeliveries);
    }

    @DeleteMapping("/received")
    @Operation(summary = "Clear received webhooks list")
    public ResponseEntity<Void> clearReceived() {
        receivedDeliveries.clear();
        failureCounters.clear();
        return ResponseEntity.noContent().build();
    }
}
