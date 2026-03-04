package com.commerce.order.interfaces;

import com.commerce.common.event.InventoryReservationFailedEvent;
import com.commerce.common.event.PaymentCompletedEvent;
import com.commerce.common.event.PaymentFailedEvent;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.ProcessedEvent;
import com.commerce.order.infrastructure.OrderRepository;
import com.commerce.order.infrastructure.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SagaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SagaEventConsumer.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public SagaEventConsumer(OrderRepository orderRepository, ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory_events", groupId = "order-service-group")
    @Transactional
    public void onInventoryEvent(@Payload String payload) {
        try {
            JsonNode tree = objectMapper.readTree(payload);
            String eventId = tree.get("eventId").asText();

            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate event ignored: {}", eventId);
                return;
            }

            // We only care about failure here for compensation
            if ("InventoryReservationFailedEvent".equals(tree.get("@type") != null ? tree.get("@type").asText() : "")) {
                InventoryReservationFailedEvent event = objectMapper.treeToValue(tree,
                        InventoryReservationFailedEvent.class);
                Order order = orderRepository.findById(event.orderId()).orElseThrow();
                order.markCancelled();
                orderRepository.save(order);
                processedEventRepository.save(new ProcessedEvent(eventId));
            } else {
                // Try to guess by fields if missing @type (simplified for demo)
                if (tree.has("reason") && tree.has("productId") && tree.has("quantity")) {
                    InventoryReservationFailedEvent event = objectMapper.treeToValue(tree,
                            InventoryReservationFailedEvent.class);
                    Order order = orderRepository.findById(event.orderId()).orElseThrow();
                    order.markCancelled();
                    orderRepository.save(order);
                    processedEventRepository.save(new ProcessedEvent(eventId));
                }
            }

        } catch (Exception e) {
            log.error("Error processing inventory event", e);
        }
    }

    @KafkaListener(topics = "payment_events", groupId = "order-service-group")
    @Transactional
    public void onPaymentEvent(@Payload String payload) {
        try {
            JsonNode tree = objectMapper.readTree(payload);
            String eventId = tree.get("eventId").asText();

            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate event ignored: {}", eventId);
                return;
            }

            if (tree.has("transactionId")) { // PaymentCompletedEvent
                PaymentCompletedEvent event = objectMapper.treeToValue(tree, PaymentCompletedEvent.class);
                Order order = orderRepository.findById(event.orderId()).orElseThrow();
                order.markCompleted();
                orderRepository.save(order);
            } else if (tree.has("reason")) { // PaymentFailedEvent
                PaymentFailedEvent event = objectMapper.treeToValue(tree, PaymentFailedEvent.class);
                Order order = orderRepository.findById(event.orderId()).orElseThrow();
                order.markCancelled();
                orderRepository.save(order);
            }

            processedEventRepository.save(new ProcessedEvent(eventId));

        } catch (Exception e) {
            log.error("Error processing payment event", e);
        }
    }
}
