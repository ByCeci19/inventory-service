package com.example.inventory_service.infrastructure.web;

import com.example.inventory_service.domain.MissingSku;

/**
 * Invalid reservation request detected at the driving boundary (empty item
 * list, non-positive quantity).
 *
 * <p>Carries the {@code orderId} so the FAILED outcome stays traceable
 * (FR-008). Never reaches the domain: no stock is evaluated and no
 * reservation row is recorded (FR-007). Thrown by the REST controller;
 * mapped by {@link ReservationExceptionHandler}.
 */
public class InvalidReservationException extends RuntimeException {

    private final String orderId;
    private final MissingSku.Reason reason;

    public InvalidReservationException(String orderId, MissingSku.Reason reason) {
        super("invalid reservation for order " + orderId + ": " + reason);
        this.orderId = orderId;
        this.reason = reason;
    }

    public String orderId() {
        return orderId;
    }

    public MissingSku.Reason reason() {
        return reason;
    }
}
