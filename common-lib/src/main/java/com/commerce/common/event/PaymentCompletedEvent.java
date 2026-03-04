package com.commerce.common.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID eventId,
        UUID orderId,
        String transactionId,
        Instant createdAt) {
}
