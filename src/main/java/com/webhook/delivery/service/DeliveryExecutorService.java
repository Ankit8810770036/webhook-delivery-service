package com.webhook.delivery.service;

import com.webhook.delivery.domain.*;
import com.webhook.delivery.repository.DeliveryAttemptRepository;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.repository.WebhookEndpointRepository;
import com.webhook.delivery.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeliveryExecutorService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExecutorService.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookEventRepository eventRepository;
    private final SignatureService signatureService;
    private final RetryPolicyService retryPolicyService;
    private final CircuitBreakerService circuitBreakerService;
    private final HttpClient httpClient;

    public DeliveryExecutorService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookEventRepository eventRepository,
            SignatureService signatureService,
            RetryPolicyService retryPolicyService,
            CircuitBreakerService circuitBreakerService,
            HttpClient httpClient
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.signatureService = signatureService;
        this.retryPolicyService = retryPolicyService;
        this.circuitBreakerService = circuitBreakerService;
        this.httpClient = httpClient;
    }

    @Transactional
    public void executeDelivery(UUID deliveryId) {
        Optional<Delivery> deliveryOpt = deliveryRepository.findById(deliveryId);
        if (deliveryOpt.isEmpty()) {
            return;
        }

        Delivery delivery = deliveryOpt.get();
        if (delivery.getStatus() != DeliveryStatus.PROCESSING) {
            return;
        }

        Optional<WebhookEndpoint> endpointOpt = endpointRepository.findById(delivery.getEndpointId());
        Optional<WebhookEvent> eventOpt = eventRepository.findById(delivery.getEventId());

        if (endpointOpt.isEmpty() || eventOpt.isEmpty()) {
            log.warn("Delivery {} refers to missing endpoint or event. Marking as CANCELLED.", deliveryId);
            delivery.setStatus(DeliveryStatus.CANCELLED);
            delivery.setLockedBy(null);
            delivery.setLockedUntil(null);
            delivery.setUpdatedAt(Instant.now());
            deliveryRepository.save(delivery);
            return;
        }

        WebhookEndpoint endpoint = endpointOpt.get();
        WebhookEvent event = eventOpt.get();

        if (endpoint.getStatus() == EndpointStatus.DISABLED) {
            log.info("Endpoint {} is disabled. Cancelling delivery {}.", endpoint.getId(), deliveryId);
            delivery.setStatus(DeliveryStatus.CANCELLED);
            delivery.setLockedBy(null);
            delivery.setLockedUntil(null);
            delivery.setUpdatedAt(Instant.now());
            deliveryRepository.save(delivery);
            return;
        }

        // Circuit Breaker check
        if (!circuitBreakerService.allowRequest(endpoint.getId())) {
            log.info("Endpoint {} circuit is OPEN. Deferring delivery {} by cooldown.", endpoint.getId(), deliveryId);
            delivery.setStatus(DeliveryStatus.PENDING);
            delivery.setNextAttemptAt(Instant.now().plus(circuitBreakerService.getCooldownDuration()));
            delivery.setLockedBy(null);
            delivery.setLockedUntil(null);
            delivery.setUpdatedAt(Instant.now());
            deliveryRepository.save(delivery);
            return;
        }

        // Set correlation context
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }
        MDC.put("tenantId", delivery.getTenantId());

        long timestampSeconds = Instant.now().getEpochSecond();
        String payload = event.getPayload();
        String signature = signatureService.computeSignature(payload, endpoint.getSecret(), timestampSeconds);

        int currentAttemptNumber = delivery.getAttemptCount() + 1;
        long startTime = System.currentTimeMillis();
        Integer responseCode = null;
        String responseSnippet = null;
        String errorMessage = null;
        boolean success = false;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Webhook-Delivery-Service/1.0")
                    .header("X-Webhook-Signature", signature)
                    .header("X-Webhook-Timestamp", String.valueOf(timestampSeconds))
                    .header("X-Correlation-Id", traceId)
                    .header("X-Delivery-Id", delivery.getId().toString())
                    .header("X-Attempt-Number", String.valueOf(currentAttemptNumber))
                    .header("X-Tenant-Id", delivery.getTenantId())
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;
            responseCode = response.statusCode();
            responseSnippet = sanitizeSnippet(response.body());

            if (responseCode >= 200 && responseCode < 300) {
                success = true;
                log.info("Webhook delivery {} to {} succeeded (HTTP {}, {}ms) on attempt {}",
                        deliveryId, endpoint.getUrl(), responseCode, latency, currentAttemptNumber);
            } else {
                errorMessage = "HTTP " + responseCode;
                log.warn("Webhook delivery {} to {} returned non-2xx status: HTTP {} ({}ms)",
                        deliveryId, endpoint.getUrl(), responseCode, latency);
            }
        } catch (HttpTimeoutException te) {
            long latency = System.currentTimeMillis() - startTime;
            errorMessage = "Connection or read timed out after " + latency + "ms";
            log.warn("Webhook delivery {} timed out after {}ms: {}", deliveryId, latency, te.getMessage());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Webhook delivery {} failed with exception: {}", deliveryId, e.getMessage());
        }

        long totalLatency = System.currentTimeMillis() - startTime;

        // Record attempt in audit table
        DeliveryAttempt attempt = new DeliveryAttempt(
                delivery.getId(),
                currentAttemptNumber,
                responseCode,
                totalLatency,
                errorMessage
        );
        attemptRepository.save(attempt);

        // Update delivery entity
        delivery.setAttemptCount(currentAttemptNumber);
        delivery.setLastResponseCode(responseCode);
        delivery.setLastResponseSnippet(responseSnippet != null ? responseSnippet : errorMessage);
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        delivery.setUpdatedAt(Instant.now());

        if (success) {
            delivery.setStatus(DeliveryStatus.SUCCESS);
            circuitBreakerService.recordSuccess(endpoint.getId());
        } else {
            circuitBreakerService.recordFailure(endpoint.getId());
            if (retryPolicyService.hasExhaustedRetries(currentAttemptNumber)) {
                delivery.setStatus(DeliveryStatus.DEAD_LETTERED);
                log.error("Webhook delivery {} DEAD_LETTERED after {} failed attempts.",
                        deliveryId, currentAttemptNumber);
            } else {
                delivery.setStatus(DeliveryStatus.PENDING);
                Instant nextAttempt = retryPolicyService.calculateNextAttemptTime(currentAttemptNumber, Instant.now());
                delivery.setNextAttemptAt(nextAttempt);
                log.info("Scheduled retry #{} for delivery {} at {}",
                        currentAttemptNumber + 1, deliveryId, nextAttempt);
            }
        }

        deliveryRepository.save(delivery);
    }

    private String sanitizeSnippet(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String singleLine = body.replaceAll("\\s+", " ").trim();
        if (singleLine.length() > 250) {
            return singleLine.substring(0, 250) + "...";
        }
        return singleLine;
    }
}
