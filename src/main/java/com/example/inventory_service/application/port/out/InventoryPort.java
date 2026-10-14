package com.example.inventory_service.application.port.out;

import com.example.inventory_service.domain.ReservationOutcome;
import com.example.inventory_service.domain.StockItem;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Driven port: inventory persistence.
 *
 * <p>{@link #storeDecision} MUST persist the outcome and the resulting stock
 * atomically; a concurrent duplicate {@code orderId} insert MUST surface as
 * a persistence failure so the caller can re-read the stored outcome (R-3).
 */
public interface InventoryPort {

    /** Looks up the recorded outcome; empty when {@code orderId} is unseen. */
    Optional<ReservationOutcome> findOutcomeByOrderId(String orderId);

    /**
     * Loads a snapshot of current stock for the requested SKUs.
     * SKUs absent from the map are unknown to inventory.
     */
    Map<String, StockItem> loadStock(Set<String> skus);

    /** Persists the decision outcome plus resulting stock in one transaction. */
    void storeDecision(ReservationOutcome outcome, Map<String, StockItem> resultingStock);
}
