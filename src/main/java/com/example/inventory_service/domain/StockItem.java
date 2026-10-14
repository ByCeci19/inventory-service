package com.example.inventory_service.domain;

import java.util.Objects;

/**
 * An SKU held in inventory with an available quantity.
 *
 * <p>Immutable. The {@code availableQty >= 0} invariant is enforced on
 * construction and on every decrement (data-model.md, FR-009); attempting
 * to decrement more than available fails without mutating anything.
 */
public final class StockItem {

    private final String sku;
    private final int availableQty;

    private StockItem(String sku, int availableQty) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must be non-blank");
        }
        if (sku.length() > 64) {
            throw new IllegalArgumentException("sku must be at most 64 characters");
        }
        if (availableQty < 0) {
            throw new IllegalArgumentException("availableQty must be >= 0");
        }
        this.sku = sku;
        this.availableQty = availableQty;
    }

    public static StockItem of(String sku, int availableQty) {
        return new StockItem(sku, availableQty);
    }

    public String sku() {
        return sku;
    }

    public int availableQty() {
        return availableQty;
    }

    public boolean canFulfill(int quantity) {
        return quantity > 0 && availableQty >= quantity;
    }

    /**
     * Returns the item with {@code quantity} units decremented.
     *
     * @throws IllegalArgumentException if quantity is not positive or exceeds
     *         available stock; the item is left unchanged
     */
    public StockItem decrement(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (availableQty < quantity) {
            throw new IllegalArgumentException(
                    "insufficient stock for sku " + sku + ": available=" + availableQty + ", requested=" + quantity);
        }
        return new StockItem(sku, availableQty - quantity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StockItem other)) {
            return false;
        }
        return availableQty == other.availableQty && sku.equals(other.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku, availableQty);
    }

    @Override
    public String toString() {
        return "StockItem[sku=" + sku + ", availableQty=" + availableQty + "]";
    }
}
