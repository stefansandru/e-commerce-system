package com.commerce.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private String productId;

    private int availableQuantity;
    private int reservedQuantity;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Inventory(String productId, int initialQuantity) {
        this.productId = productId;
        this.availableQuantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    public void reserve(int quantity) {
        if (this.availableQuantity < quantity) {
            throw new IllegalStateException("Not enough stock");
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
    }

    public void release(int quantity) {
        this.availableQuantity += quantity;
        this.reservedQuantity -= quantity;
    }

    public void commit(int quantity) {
        this.reservedQuantity -= quantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Long getVersion() {
        return version;
    }
}
