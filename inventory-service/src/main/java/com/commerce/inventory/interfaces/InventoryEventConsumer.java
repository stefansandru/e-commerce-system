package com.commerce.inventory.interfaces;

import com.commerce.common.event.OrderCreatedEvent;
import com.commerce.inventory.application.InventoryCommandService;
import com.commerce.inventory.domain.ProcessedEvent;
import com.commerce.inventory.infrastructure.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final InventoryCommandService inventoryCommandService;
    private final ProcessedEventRepository processedEventRepository;
    private final com.commerce.inventory.infrastructure.InventoryReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(InventoryCommandService inventoryCommandService,
            ProcessedEventRepository processedEventRepository,
            com.commerce.inventory.infrastructure.InventoryReservationRepository reservationRepository,
            ObjectMapper objectMapper) {
        this.inventoryCommandService = inventoryCommandService;
        this.processedEventRepository = processedEventRepository;
        this.reservationRepository = reservationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order_events", groupId = "inventory-service-group")
    @Transactional
    public void onOrderEvent(@Payload String payload) {
        try {
            JsonNode tree = objectMapper.readTree(payload);
            String eventId = tree.get("eventId").asText();

            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate order event ignored: {}", eventId);
                return;
            }

            if (tree.has("productId") && tree.has("quantity") && tree.has("orderId")) {
                OrderCreatedEvent event = objectMapper.treeToValue(tree, OrderCreatedEvent.class);
                inventoryCommandService.reserveStock(event.orderId(), event.productId(), event.quantity());
            }

            processedEventRepository.save(new ProcessedEvent(eventId));

        } catch (Exception e) {
            log.error("Error processing order event", e);
        }
    }

    @KafkaListener(topics = "payment_events", groupId = "inventory-service-group")
    @Transactional
    public void onPaymentEvent(@Payload String payload) {
        try {
            JsonNode tree = objectMapper.readTree(payload);
            String eventId = tree.get("eventId").asText();

            if (processedEventRepository.existsById(eventId)) {
                return; // idempotent
            }

            if (tree.has("orderId")) {
                java.util.UUID orderId = java.util.UUID.fromString(tree.get("orderId").asText());
                reservationRepository.findById(orderId).ifPresent(res -> {
                    if (tree.has("transactionId")) { // PaymentCompleted
                        inventoryCommandService.commitStock(res.getProductId(), res.getQuantity());
                    } else if (tree.has("reason")) { // PaymentFailed
                        inventoryCommandService.releaseStock(res.getProductId(), res.getQuantity());
                    }
                });
            }

            processedEventRepository.save(new ProcessedEvent(eventId));

        } catch (Exception e) {
            log.error("Error processing payment event", e);
        }
    }
}
