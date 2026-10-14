package com.example.inventory_service.domain;

/**
 * A SKU that could not be reserved, with its machine-readable reason.
 *
 * <p>Immutable. Reasons mirror the contract enum so the outcome payload and
 * the domain share one vocabulary.
 */
public record MissingSku(String sku, Reason reason) {

    public enum Reason {
        INSUFFICIENT_STOCK,
        UNKNOWN_SKU,
        INVALID_QUANTITY,
        INVALID_REQUEST
    }

    public MissingSku {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must be non-blank");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must be non-null");
        }
    }
}
