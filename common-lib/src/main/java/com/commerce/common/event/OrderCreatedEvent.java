package com.commerce.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String productId,
        int quantity,
        Instant createdAt) {
}
