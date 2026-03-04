package com.commerce.common.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        UUID orderId,
        String productId,
        int quantity,
        Instant createdAt) {
}
