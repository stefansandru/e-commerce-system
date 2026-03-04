package com.commerce.inventory.application;

import com.commerce.common.event.InventoryReservationFailedEvent;
import com.commerce.common.event.InventoryReservedEvent;
import com.commerce.inventory.domain.Inventory;
import com.commerce.inventory.domain.OutboxEvent;
import com.commerce.inventory.infrastructure.InventoryRepository;
import com.commerce.inventory.infrastructure.OutboxEventRepository;
import com.commerce.inventory.infrastructure.InventoryReservationRepository;
import com.commerce.inventory.domain.InventoryReservation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    public InventoryCommandService(InventoryRepository inventoryRepository, OutboxEventRepository outboxEventRepository,
            InventoryReservationRepository reservationRepository,
            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.reservationRepository = reservationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void reserveStock(UUID orderId, String productId, int quantity) {
        Inventory inventory = inventoryRepository.findById(productId).orElse(null);

        if (inventory == null) {
            publishFailureEvent(orderId, productId, quantity, "Product not found");
            return;
        }

        try {
            inventory.reserve(quantity);
            inventoryRepository.save(inventory);

            // Save local reservation state for compensation/commit
            reservationRepository.save(new InventoryReservation(orderId, productId, quantity));

            // Publish success to outbox
            InventoryReservedEvent event = new InventoryReservedEvent(
                    UUID.randomUUID(),
                    orderId,
                    productId,
                    quantity,
                    Instant.now());

            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    Inventory.class.getSimpleName(),
                    productId,
                    InventoryReservedEvent.class.getSimpleName(),
                    payload);
            outboxEventRepository.save(outboxEvent);

        } catch (IllegalStateException e) {
            publishFailureEvent(orderId, productId, quantity, e.getMessage());
        } catch (Exception e) {
            publishFailureEvent(orderId, productId, quantity, "Reservation failed: " + e.getMessage());
        }
    }

    @Transactional
    public void releaseStock(String productId, int quantity) {
        inventoryRepository.findById(productId).ifPresent(inventory -> {
            inventory.release(quantity);
            inventoryRepository.save(inventory);
        });
    }

    @Transactional
    public void commitStock(String productId, int quantity) {
        inventoryRepository.findById(productId).ifPresent(inventory -> {
            inventory.commit(quantity);
            inventoryRepository.save(inventory);
        });
    }

    private void publishFailureEvent(UUID orderId, String productId, int quantity, String reason) {
        try {
            InventoryReservationFailedEvent event = new InventoryReservationFailedEvent(
                    UUID.randomUUID(),
                    orderId,
                    productId,
                    quantity,
                    reason,
                    Instant.now());
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    Inventory.class.getSimpleName(),
                    productId,
                    InventoryReservationFailedEvent.class.getSimpleName(),
                    payload);
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Outbox event", e);
        }
    }
}
