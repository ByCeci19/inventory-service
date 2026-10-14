package com.example.inventory_service.application.port.out;

import com.example.inventory_service.domain.ReservationOutcome;

/**
 * Driven port: publishes the versioned outcome event
 * ({@code InventoryReserved} / {@code InventoryReservationFailed}).
 *
 * <p>Publishing follows the reservation commit; consumers are idempotent on
 * {@code orderId}, so redelivered publishes are safe (R-3).
 */
public interface OutcomePublisher {

    void publish(ReservationOutcome outcome);
}
