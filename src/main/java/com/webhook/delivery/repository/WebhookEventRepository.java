package com.webhook.delivery.repository;

import com.webhook.delivery.domain.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByTenantIdAndEventIdExternal(String tenantId, String eventIdExternal);

    Optional<WebhookEvent> findByIdAndTenantId(UUID id, String tenantId);

    Page<WebhookEvent> findAllByTenantId(String tenantId, Pageable pageable);
}
