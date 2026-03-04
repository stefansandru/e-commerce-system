package com.commerce.order.infrastructure;

import com.commerce.order.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    // Explicitly configure a separate thread pool for this in properties or config
    // class
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay:1000}")
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> events = outboxEventRepository.findAllByOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                // Topic name convention: aggregateType_events
                String topic = "order_events";
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                outboxEventRepository.delete(event);
                log.info("Relayed event {} to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.error("Failed to relay event {}", event.getId(), e);
                // We break to maintain ordering, or we could continue.
                // For simplicity, failing fast here is okay. Next schedule will retry.
                break;
            }
        }
    }
}
