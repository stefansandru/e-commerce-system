package com.commerce.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class InventoryReservation {

    @Id
    private UUID orderId;

    private String productId;
    private int quantity;

    protected InventoryReservation() {
    }

    public InventoryReservation(UUID orderId, String productId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
