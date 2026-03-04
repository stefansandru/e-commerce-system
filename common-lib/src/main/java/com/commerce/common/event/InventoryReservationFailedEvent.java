package com.commerce.common.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationFailedEvent(
        UUID eventId,
        UUID orderId,
        String productId,
        int quantity,
        String reason,
        Instant createdAt) {
}
