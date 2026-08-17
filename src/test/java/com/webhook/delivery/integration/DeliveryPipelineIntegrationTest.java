package com.webhook.delivery.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.domain.DeliveryStatus;
import com.webhook.delivery.dto.CreateEndpointRequest;
import com.webhook.delivery.dto.EndpointResponse;
import com.webhook.delivery.dto.EventResponse;
import com.webhook.delivery.dto.IngestEventRequest;
import com.webhook.delivery.repository.DeliveryRepository;
import com.webhook.delivery.service.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPipelineIntegrationTest extends BaseIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private EndpointService endpointService;

    @Autowired
    private EventIngestionService ingestionService;

    @Autowired
    private DeliveryClaimService claimService;

    @Autowired
    private DeliveryExecutorService executorService;

    @Autowired
    private DeliveryQueryService queryService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0); // dynamic port
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Successful 200 OK delivery marks status SUCCESS and sends valid HMAC signature")
    void testSuccessfulDeliveryPipeline() {
        String tenantId = "tenant-wiremock-success";
        String webhookPath = "/webhook-sink-success";

        wireMockServer.stubFor(post(urlEqualTo(webhookPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"received\": true}")));

        String targetUrl = "http://localhost:" + wireMockServer.port() + webhookPath;
        EndpointResponse ep = endpointService.createEndpoint(tenantId,
                new CreateEndpointRequest(targetUrl, "super_secret_test_key_123456", List.of("order.placed")));

        ObjectNode payload = objectMapper.createObjectNode().put("orderId", "ord_100").put("amount", 99.50);
        EventResponse evt = ingestionService.ingestEvent(tenantId,
                new IngestEventRequest("evt_wire_" + UUID.randomUUID(), "order.placed", payload));

        // Claim and execute
        List<Delivery> claimed = claimService.claimDueDeliveries();
        Delivery delivery = claimed.stream().filter(d -> d.getEventId().equals(evt.getId())).findFirst().orElseThrow();

        executorService.executeDelivery(delivery.getId());

        Delivery updated = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.SUCCESS, updated.getStatus());
        assertEquals(200, updated.getLastResponseCode());
        assertEquals(1, updated.getAttemptCount());

        // Verify WireMock captured HMAC signature headers
        wireMockServer.verify(postRequestedFor(urlEqualTo(webhookPath))
                .withHeader("X-Webhook-Signature", matching("v1=[a-f0-9]+"))
                .withHeader("X-Webhook-Timestamp", matching("[0-9]+"))
                .withHeader("X-Correlation-Id", matching(".+")));
    }

    @Test
    @DisplayName("500 Server Error schedules exponential backoff retry and marks DEAD_LETTERED when budget exhausted")
    void testFailingDeliveryAndDeadLettering() {
        String tenantId = "tenant-wiremock-fail";
        String webhookPath = "/webhook-sink-fail";

        wireMockServer.stubFor(post(urlEqualTo(webhookPath))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        String targetUrl = "http://localhost:" + wireMockServer.port() + webhookPath;
        EndpointResponse ep = endpointService.createEndpoint(tenantId,
                new CreateEndpointRequest(targetUrl, null, List.of("payment.failed")));

        ObjectNode payload = objectMapper.createObjectNode().put("error", "insufficient_funds");
        EventResponse evt = ingestionService.ingestEvent(tenantId,
                new IngestEventRequest("evt_fail_" + UUID.randomUUID(), "payment.failed", payload));

        List<Delivery> claimed = claimService.claimDueDeliveries();
        Delivery delivery = claimed.stream().filter(d -> d.getEventId().equals(evt.getId())).findFirst().orElseThrow();

        // Simulate attempt 1
        executorService.executeDelivery(delivery.getId());

        Delivery afterFirstAttempt = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.PENDING, afterFirstAttempt.getStatus());
        assertEquals(1, afterFirstAttempt.getAttemptCount());
        assertEquals(500, afterFirstAttempt.getLastResponseCode());
        assertTrue(afterFirstAttempt.getNextAttemptAt().isAfter(Instant.now()));

        // Simulate reaching max attempts (attempt 8)
        afterFirstAttempt.setAttemptCount(7);
        afterFirstAttempt.setStatus(DeliveryStatus.PROCESSING);
        deliveryRepository.save(afterFirstAttempt);

        executorService.executeDelivery(afterFirstAttempt.getId());

        Delivery deadLettered = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.DEAD_LETTERED, deadLettered.getStatus());
        assertEquals(8, deadLettered.getAttemptCount());

        // Test manual redrive
        var redriveRes = queryService.redriveDelivery(tenantId, deadLettered.getId());
        assertEquals(DeliveryStatus.PENDING, redriveRes.getStatus());

        Delivery redriven = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.PENDING, redriven.getStatus());
    }
}
