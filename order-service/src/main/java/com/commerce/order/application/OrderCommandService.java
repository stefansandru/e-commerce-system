package com.commerce.order.application;

import com.commerce.common.event.OrderCreatedEvent;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OutboxEvent;
import com.commerce.order.infrastructure.OrderRepository;
import com.commerce.order.infrastructure.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderCommandService(OrderRepository orderRepository, OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID checkout(String productId, int quantity, String idempotencyKey) {
        // Simple idempotency check although strictly this can be enforced via DB unique
        // constraint
        // For demo, we just rely on DB unique constraint if needed, or simple check
        // here

        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, productId, quantity, idempotencyKey);
        orderRepository.save(order);

        // Create domain event
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getCreatedAt());

        // Serialize and save to outbox
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    Order.class.getSimpleName(),
                    order.getId().toString(),
                    OrderCreatedEvent.class.getSimpleName(),
                    payload);
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Outbox event", e);
        }

        return orderId;
    }
}
