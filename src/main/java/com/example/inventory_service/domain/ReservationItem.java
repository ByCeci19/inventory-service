package com.example.inventory_service.domain;

/**
 * A requested order line: SKU plus quantity.
 *
 * <p>Immutable. Quantity must be positive (contracts {@code quantity
 * minimum: 1}); non-positive quantities are rejected as invalid requests
 * (FR-007) before any stock evaluation.
 */
public record ReservationItem(String sku, int quantity) {

    public ReservationItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must be non-blank");
        }
        if (sku.length() > 64) {
            throw new IllegalArgumentException("sku must be at most 64 characters");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
    }
}
