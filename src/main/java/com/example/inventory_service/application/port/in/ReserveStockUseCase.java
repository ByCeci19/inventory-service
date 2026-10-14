package com.example.inventory_service.application.port.in;

import com.example.inventory_service.domain.ReservationItem;
import com.example.inventory_service.domain.ReservationOutcome;
import java.util.List;

/**
 * Driving port: idempotent stock reservation.
 *
 * <p>Implementations MUST return the stored outcome verbatim when
 * {@code orderId} was already recorded, without mutating stock
 * (constitution Principle III). Invalid requests (blank orderId, empty
 * lines) fail with {@link IllegalArgumentException} and MUST NOT mutate
 * stock (FR-007); adapters translate that into a FAILED outcome.
 */
public interface ReserveStockUseCase {

    ReservationOutcome reserve(String orderId, List<ReservationItem> items);
}
