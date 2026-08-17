package com.webhook.delivery.repository;

import com.webhook.delivery.domain.EndpointStatus;
import com.webhook.delivery.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByIdAndTenantId(UUID id, String tenantId);

    List<WebhookEndpoint> findAllByTenantId(String tenantId);

    List<WebhookEndpoint> findAllByTenantIdAndStatus(String tenantId, EndpointStatus status);
}
