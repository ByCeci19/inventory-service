package com.example.inventory_service.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reservation aggregate root, keyed by {@code orderId}.
 *
 * <p>Owns the all-or-nothing evaluation (FR-001/FR-003): a request succeeds
 * only if every line is fully available; otherwise nothing is decremented and
 * the outcome names each missing SKU. Decisions are pure functions of the
 * request plus a stock snapshot — persistence and idempotency lookup live in
 * the application layer, never here (constitution Principle I).
 */
public final class Reservation {

    private final String orderId;
    private final List<ReservationItem> lines;

    private Reservation(String orderId, List<ReservationItem> lines) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must be non-blank");
        }
        if (orderId.length() > 64) {
            throw new IllegalArgumentException("orderId must be at most 64 characters");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("reservation must contain at least one line");
        }
        this.orderId = orderId;
        this.lines = List.copyOf(lines);
    }

    public static Reservation request(String orderId, List<ReservationItem> lines) {
        return new Reservation(orderId, lines);
    }

    public String orderId() {
        return orderId;
    }

    public List<ReservationItem> lines() {
        return lines;
    }

    /**
     * Evaluates the request against a snapshot of current stock.
     *
     * @param currentStock stock by SKU; a SKU absent from the map is treated
     *        as unknown ({@code UNKNOWN_SKU})
     * @param decidedAt decision timestamp recorded on the outcome
     * @return decision holding the outcome and, on success only, the
     *         decremented stock; on failure the returned stock equals the input
     */
    public Decision decide(Map<String, StockItem> currentStock, Instant decidedAt) {
        Map<String, StockItem> stock = new LinkedHashMap<>(currentStock);
        List<MissingSku> missing = new ArrayList<>();
        for (ReservationItem line : lines) {
            StockItem item = stock.get(line.sku());
            if (item == null) {
                missing.add(new MissingSku(line.sku(), MissingSku.Reason.UNKNOWN_SKU));
            } else if (!item.canFulfill(line.quantity())) {
                missing.add(new MissingSku(line.sku(), MissingSku.Reason.INSUFFICIENT_STOCK));
            }
        }
        if (!missing.isEmpty()) {
            ReservationOutcome outcome = new ReservationOutcome(
                    orderId, ReservationOutcome.Status.FAILED,
                    ReservationOutcome.CURRENT_VERSION, List.of(), missing, decidedAt);
            return new Decision(outcome, Map.copyOf(currentStock));
        }
        Map<String, StockItem> updated = new LinkedHashMap<>(stock);
        for (ReservationItem line : lines) {
            updated.put(line.sku(), updated.get(line.sku()).decrement(line.quantity()));
        }
        ReservationOutcome outcome = new ReservationOutcome(
                orderId, ReservationOutcome.Status.RESERVED,
                ReservationOutcome.CURRENT_VERSION, lines, List.of(), decidedAt);
        return new Decision(outcome, Map.copyOf(updated));
    }

    /** Result of {@link #decide}: the outcome plus the resulting stock view. */
    public record Decision(ReservationOutcome outcome, Map<String, StockItem> resultingStock) {
    }
}
