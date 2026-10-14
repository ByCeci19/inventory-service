package com.example.inventory_service.infrastructure.web;

import com.example.inventory_service.domain.ReservationOutcome;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps invalid reservation requests to a contract-shaped FAILED outcome
 * (HTTP 400, {@code INVALID_REQUEST} family) without touching stock or
 * recording a reservation row (FR-007).
 */
@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler(InvalidReservationException.class)
    public ResponseEntity<ReservationOutcome> handleInvalid(InvalidReservationException ex) {
        MDC.put("orderId", ex.orderId());
        try {
            ReservationOutcome outcome = new ReservationOutcome(
                    ex.orderId(),
                    ReservationOutcome.Status.FAILED,
                    ReservationOutcome.CURRENT_VERSION,
                    List.of(),
                    List.of(),
                    Instant.now());
            return ResponseEntity.badRequest().body(outcome);
        } finally {
            MDC.remove("orderId");
        }
    }
}
