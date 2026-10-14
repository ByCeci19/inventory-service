package com.example.inventory_service.domain;

import java.time.Instant;
import java.util.List;

/**
 * The stored decision for an {@code orderId}: reserved or failed.
 *
 * <p>Immutable and insert-once: replays return the recorded instance verbatim,
 * including the original {@code decidedAt} (spec edge case). The
 * {@code eventVersion} is the clarified explicit schema version
 * ({@code const: v1}); breaking schema changes require a new version value.
 */
public record ReservationOutcome(
        String orderId,
        Status status,
        String eventVersion,
        List<ReservationItem> items,
        List<MissingSku> missingSkus,
        Instant decidedAt) {

    public static final String CURRENT_VERSION = "v1";

    public enum Status {
        RESERVED,
        FAILED
    }

    public ReservationOutcome {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must be non-blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must be non-null");
        }
        if (!CURRENT_VERSION.equals(eventVersion)) {
            throw new IllegalArgumentException("eventVersion must be " + CURRENT_VERSION);
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("decidedAt must be non-null");
        }
        items = items == null ? List.of() : List.copyOf(items);
        missingSkus = missingSkus == null ? List.of() : List.copyOf(missingSkus);
    }
}
