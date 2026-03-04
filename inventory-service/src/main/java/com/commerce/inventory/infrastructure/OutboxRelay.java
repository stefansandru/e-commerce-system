package com.commerce.inventory.infrastructure;

import com.commerce.inventory.domain.OutboxEvent;
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
                String topic = "inventory_events";
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
                outboxEventRepository.delete(event);
                log.info("Relayed event {} to topic {}", event.getId(), topic);
            } catch (Exception e) {
                log.error("Failed to relay event {}", event.getId(), e);
                break;
            }
        }
    }
}
