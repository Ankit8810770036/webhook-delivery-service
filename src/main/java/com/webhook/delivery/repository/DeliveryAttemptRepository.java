package com.webhook.delivery.repository;

import com.webhook.delivery.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findAllByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);

    List<DeliveryAttempt> findAllByDeliveryIdIn(List<UUID> deliveryIds);
}
