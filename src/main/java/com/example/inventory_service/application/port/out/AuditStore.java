package com.example.inventory_service.application.port.out;

import com.example.inventory_service.domain.ReservationOutcome;

/**
 * Driven port: durable audit of every reservation outcome, queryable by
 * {@code orderId} (clarified FR-008).
 *
 * <p>Audit writes follow the reservation commit and MUST never roll back a
 * decided reservation (R-7).
 */
public interface AuditStore {

    void save(ReservationOutcome outcome);
}
