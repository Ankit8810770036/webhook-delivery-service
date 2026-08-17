package com.webhook.delivery.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.dto.CreateEndpointRequest;
import com.webhook.delivery.dto.EventResponse;
import com.webhook.delivery.dto.IngestEventRequest;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.service.EndpointService;
import com.webhook.delivery.service.EventIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventIngestionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EndpointService endpointService;

    @Autowired
    private EventIngestionService ingestionService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Idempotent ingestion: Re-submitting the same eventId must return existing event without duplicate deliveries")
    void testIdempotentEventIngestion() {
        String tenantId = "tenant-idempotent-test";
        String externalEventId = "evt_dup_" + UUID.randomUUID();

        // Register 2 matching endpoints
        endpointService.createEndpoint(tenantId, new CreateEndpointRequest(
                "http://localhost:8080/api/demo/sink1", null, List.of("invoice.paid")));
        endpointService.createEndpoint(tenantId, new CreateEndpointRequest(
                "http://localhost:8080/api/demo/sink2", null, List.of("*")));

        ObjectNode payload = objectMapper.createObjectNode().put("invoiceNumber", "INV-1001").put("total", 250.0);
        IngestEventRequest request = new IngestEventRequest(externalEventId, "invoice.paid", payload);

        // First Ingestion
        EventResponse firstResponse = ingestionService.ingestEvent(tenantId, request);
        assertNotNull(firstResponse.getId());
        assertEquals("ACCEPTED", firstResponse.getStatus());

        List<Delivery> deliveriesAfterFirst = deliveryRepository.findAllByTenantIdAndEventIdOrderByCreatedAtDesc(
                tenantId, firstResponse.getId());
        assertEquals(2, deliveriesAfterFirst.size(), "Should have created exactly 2 deliveries for 2 matching subscriptions");

        // Second Ingestion with identical eventId
        EventResponse secondResponse = ingestionService.ingestEvent(tenantId, request);
        assertEquals(firstResponse.getId(), secondResponse.getId(), "Event ID should match");
        assertEquals("ALREADY_INGESTED", secondResponse.getStatus(), "Status should indicate already ingested");

        List<Delivery> deliveriesAfterSecond = deliveryRepository.findAllByTenantIdAndEventIdOrderByCreatedAtDesc(
                tenantId, firstResponse.getId());
        assertEquals(2, deliveriesAfterSecond.size(), "No new deliveries should have been created on duplicate ingestion");
    }

    @Test
    @DisplayName("Ingestion responds with 202 Accepted via HTTP endpoint")
    void testIngestEventHttpEndpoint() {
        String tenantId = "tenant-http-ingest";
        String externalEventId = "evt_http_" + UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String jsonBody = String.format("""
            {
                "eventId": "%s",
                "type": "user.signup",
                "payload": {
                    "userId": "usr_123",
                    "plan": "premium"
                }
            }
            """, externalEventId);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<EventResponse> response = restTemplate.exchange(
                "/api/v1/events",
                HttpMethod.POST,
                entity,
                EventResponse.class
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(externalEventId, response.getBody().getEventId());
        assertEquals("ACCEPTED", response.getBody().getStatus());
    }

    @Test
    @DisplayName("Malformed payload or missing eventId is rejected with 400 Bad Request")
    void testMalformedPayloadRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "tenant-test");
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Missing eventId
        String invalidBody = """
            {
                "type": "order.created",
                "payload": {}
            }
            """;

        HttpEntity<String> entity = new HttpEntity<>(invalidBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/events",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
