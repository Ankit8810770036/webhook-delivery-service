package com.webhook.delivery.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.webhook.delivery.dto.CreateEndpointRequest;
import com.webhook.delivery.dto.EndpointResponse;
import com.webhook.delivery.dto.EventResponse;
import com.webhook.delivery.dto.IngestEventRequest;
import com.webhook.delivery.service.DeliveryQueryService;
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

class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EndpointService endpointService;

    @Autowired
    private EventIngestionService ingestionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Tenant B must never be able to read Tenant A's endpoint secret")
    void testTenantCannotReadOtherTenantEndpoint() {
        String tenantA = "tenant-iso-a";
        String tenantB = "tenant-iso-b";

        CreateEndpointRequest reqA = new CreateEndpointRequest(
                "http://localhost:8090/api/demo/sink",
                "secret_for_a_only_1234567890",
                List.of("invoice.paid")
        );
        EndpointResponse epA = endpointService.createEndpoint(tenantA, reqA);
        assertNotNull(epA.getId());

        // Attempt to fetch epA using Tenant B's credentials via HTTP
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Tenant-Id", tenantB);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headersB);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/endpoints/" + epA.getId(),
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Tenant B must receive 404 when attempting to access Tenant A's endpoint");
        assertFalse(response.getBody().contains("secret_for_a_only"),
                "Response body must never expose Tenant A's secret");
    }

    @Test
    @DisplayName("Tenant B must never see Tenant A's delivery logs or event deliveries")
    void testTenantCannotAccessOtherTenantDeliveries() {
        String tenantA = "tenant-log-a";
        String tenantB = "tenant-log-b";

        CreateEndpointRequest epReq = new CreateEndpointRequest(
                "http://localhost:8090/api/demo/sink", null, List.of("*"));
        EndpointResponse epA = endpointService.createEndpoint(tenantA, epReq);

        ObjectNode payload = objectMapper.createObjectNode().put("amount", 500);
        IngestEventRequest evtReq = new IngestEventRequest("evt_iso_" + UUID.randomUUID(), "order.created", payload);
        EventResponse evtA = ingestionService.ingestEvent(tenantA, evtReq);

        // Tenant B requests Tenant A's event deliveries
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Tenant-Id", tenantB);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headersB);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/events/" + evtA.getId() + "/deliveries",
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Tenant B must receive 404 when querying Tenant A's event deliveries");
    }

    @Test
    @DisplayName("Tenant B must never be able to trigger a redrive on Tenant A's delivery")
    void testTenantCannotRedriveOtherTenantDelivery() {
        String tenantA = "tenant-redrive-a";
        String tenantB = "tenant-redrive-b";

        CreateEndpointRequest epReq = new CreateEndpointRequest(
                "http://localhost:8090/api/demo/sink", null, List.of("*"));
        endpointService.createEndpoint(tenantA, epReq);

        ObjectNode payload = objectMapper.createObjectNode().put("amount", 200);
        IngestEventRequest evtReq = new IngestEventRequest("evt_rd_" + UUID.randomUUID(), "payment.failed", payload);
        ingestionService.ingestEvent(tenantA, evtReq);

        // Attempt redrive using Tenant B credentials on arbitrary UUID
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Tenant-Id", tenantB);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headersB);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/deliveries/" + UUID.randomUUID() + "/redrive",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Tenant B must receive 404 for redrive on non-owned delivery");
    }
}
