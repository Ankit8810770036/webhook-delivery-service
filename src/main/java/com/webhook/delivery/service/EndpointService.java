package com.webhook.delivery.service;

import com.webhook.delivery.domain.EndpointStatus;
import com.webhook.delivery.domain.WebhookEndpoint;
import com.webhook.delivery.dto.CreateEndpointRequest;
import com.webhook.delivery.dto.EndpointResponse;
import com.webhook.delivery.dto.EndpointTestResponse;
import com.webhook.delivery.exception.InvalidEndpointUrlException;
import com.webhook.delivery.exception.ResourceNotFoundException;
import com.webhook.delivery.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EndpointService {

    private static final Logger log = LoggerFactory.getLogger(EndpointService.class);

    private final WebhookEndpointRepository endpointRepository;
    private final SignatureService signatureService;
    private final HttpClient httpClient;
    private final boolean allowInternal;

    public EndpointService(
            WebhookEndpointRepository endpointRepository,
            SignatureService signatureService,
            HttpClient httpClient,
            @Value("${webhook.security.allow-internal:true}") boolean allowInternal
    ) {
        this.endpointRepository = endpointRepository;
        this.signatureService = signatureService;
        this.httpClient = httpClient;
        this.allowInternal = allowInternal;
    }

    @Transactional
    public EndpointResponse createEndpoint(String tenantId, CreateEndpointRequest request) {
        validateUrl(request.getUrl());

        String secret = request.getSecret();
        if (secret == null || secret.isBlank()) {
            secret = signatureService.generateSecret();
        }

        WebhookEndpoint endpoint = new WebhookEndpoint(tenantId, request.getUrl(), secret, request.getEventTypes());
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Registered webhook endpoint {} for tenant {}", saved.getId(), tenantId);
        return EndpointResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(String tenantId, UUID id) {
        WebhookEndpoint endpoint = endpointRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with ID: " + id));
        return EndpointResponse.from(endpoint);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponse> listEndpoints(String tenantId) {
        return endpointRepository.findAllByTenantId(tenantId).stream()
                .map(EndpointResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public EndpointResponse disableEndpoint(String tenantId, UUID id) {
        WebhookEndpoint endpoint = endpointRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with ID: " + id));
        endpoint.setStatus(EndpointStatus.DISABLED);
        endpoint.setUpdatedAt(Instant.now());
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Disabled webhook endpoint {} for tenant {}", id, tenantId);
        return EndpointResponse.from(saved);
    }

    public EndpointTestResponse testEndpoint(String tenantId, UUID id) {
        WebhookEndpoint endpoint = endpointRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with ID: " + id));

        String testPayload = String.format("{\"event\":\"endpoint.test\",\"timestamp\":%d,\"tenantId\":\"%s\"}",
                Instant.now().getEpochSecond(), tenantId);
        long timestamp = Instant.now().getEpochSecond();
        String signature = signatureService.computeSignature(testPayload, endpoint.getSecret(), timestamp);

        long startTime = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Webhook-Delivery-Service/1.0")
                    .header("X-Webhook-Signature", signature)
                    .header("X-Webhook-Timestamp", String.valueOf(timestamp))
                    .header("X-Webhook-Test", "true")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(testPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;
            boolean is2xx = response.statusCode() >= 200 && response.statusCode() < 300;

            log.info("Endpoint test for {} returned HTTP {} in {}ms", endpoint.getId(), response.statusCode(), latency);
            return new EndpointTestResponse(
                    endpoint.getId(),
                    endpoint.getUrl(),
                    is2xx,
                    response.statusCode(),
                    latency,
                    is2xx ? "Endpoint reached and returned successful HTTP " + response.statusCode()
                            : "Endpoint reached but returned non-2xx status: " + response.statusCode()
            );
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Endpoint test failed for {}: {}", endpoint.getId(), e.getMessage());
            return new EndpointTestResponse(
                    endpoint.getId(),
                    endpoint.getUrl(),
                    false,
                    null,
                    latency,
                    "Failed to connect to endpoint: " + e.getMessage()
            );
        }
    }

    private void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new InvalidEndpointUrlException("Endpoint URL cannot be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString);
        } catch (Exception e) {
            throw new InvalidEndpointUrlException("Malformed endpoint URL: " + urlString);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new InvalidEndpointUrlException("URL scheme must be http or https. Given: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidEndpointUrlException("URL host is missing");
        }

        if (!allowInternal) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                    throw new InvalidEndpointUrlException("Target URL points to a private/internal IP address which is forbidden");
                }
            } catch (InvalidEndpointUrlException ie) {
                throw ie;
            } catch (Exception e) {
                throw new InvalidEndpointUrlException("Unable to resolve host: " + host);
            }
        }
    }
}
