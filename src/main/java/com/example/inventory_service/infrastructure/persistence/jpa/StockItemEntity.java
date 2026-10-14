package com.example.inventory_service.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA counterpart of the {@code StockItem} domain concept.
 *
 * <p>Plain persistence state; the {@code availableQty >= 0} invariant is
 * enforced by the domain, never here. No native queries anywhere —
 * JPQL/repository derivations only, so H2 parity holds.
 */
@Entity
@Table(name = "stock_item")
public class StockItemEntity {

    @Id
    @Column(length = 64)
    private String sku;

    @Column(nullable = false)
    private int availableQty;

    protected StockItemEntity() {
    }

    public StockItemEntity(String sku, int availableQty) {
        this.sku = sku;
        this.availableQty = availableQty;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(int availableQty) {
        this.availableQty = availableQty;
    }
}
