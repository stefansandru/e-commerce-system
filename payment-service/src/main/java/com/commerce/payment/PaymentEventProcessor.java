package com.commerce.payment;

import com.commerce.common.event.InventoryReservedEvent;
import com.commerce.common.event.PaymentCompletedEvent;
import com.commerce.common.event.PaymentFailedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProcessor.class);

    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventProcessor(ProcessedEventRepository processedEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory_events", groupId = "payment-service-group")
    @Transactional
    public void onInventoryEvent(@Payload String payload) {
        try {
            JsonNode tree = objectMapper.readTree(payload);
            String eventId = tree.get("eventId").asText();

            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate inventory event ignored: {}", eventId);
                return;
            }

            if ("InventoryReservedEvent".equals(tree.get("@type") != null ? tree.get("@type").asText() : "")) {
                processPayment(tree);
            } else if (tree.has("orderId") && tree.has("productId") && !tree.has("reason")) {
                // heuristic fallback if @type is missing
                processPayment(tree);
            }

            processedEventRepository.save(new ProcessedEvent(eventId));

        } catch (Exception e) {
            log.error("Error processing inventory event", e);
        }
    }

    private void processPayment(JsonNode tree) throws Exception {
        InventoryReservedEvent event = objectMapper.treeToValue(tree, InventoryReservedEvent.class);
        log.info("Mock processing payment for order {}", event.orderId());

        // Simulate async work
        Thread.sleep(500);

        boolean success = Math.random() > 0.2; // 80% success

        Object paymentEvent;
        if (success) {
            paymentEvent = new PaymentCompletedEvent(
                    UUID.randomUUID(),
                    event.orderId(),
                    UUID.randomUUID().toString(),
                    Instant.now());
        } else {
            paymentEvent = new PaymentFailedEvent(
                    UUID.randomUUID(),
                    event.orderId(),
                    "Insufficient funds",
                    Instant.now());
        }

        kafkaTemplate.send("payment_events", event.orderId().toString(), paymentEvent);
        log.info("Published payment event: {}", paymentEvent.getClass().getSimpleName());

        // Intentionally emit a duplicate 10% of the time to demonstrate idempotency
        // downstream
        if (Math.random() < 0.1) {
            log.warn("INTENTIONALLY EMITTING DUPLICATE PAYMENT EVENT for order {}", event.orderId());
            kafkaTemplate.send("payment_events", event.orderId().toString(), paymentEvent);
        }
    }
}
