package com.example.inventory_service.infrastructure.persistence.jpa;

import com.example.inventory_service.domain.MissingSku;
import com.example.inventory_service.domain.ReservationOutcome;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Insert-once record of a reservation decision, keyed by {@code orderId}.
 *
 * <p>The UNIQUE constraint on {@code order_id} is the concurrent backstop for
 * idempotency (R-3): a duplicate insert fails and the caller re-reads the
 * stored outcome. Rows are never updated. Domain enums are reused directly
 * to avoid a parallel vocabulary.
 */
@Entity
@Table(name = "reservation", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationOutcome.Status status;

    @Column(name = "event_version", nullable = false, length = 8)
    private String eventVersion;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_line", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservationLine> lines = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_missing", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<MissingEntry> missing = new ArrayList<>();

    protected ReservationEntity() {
    }

    public ReservationEntity(
            String orderId,
            ReservationOutcome.Status status,
            String eventVersion,
            List<ReservationLine> lines,
            List<MissingEntry> missing,
            Instant decidedAt) {
        this.orderId = orderId;
        this.status = status;
        this.eventVersion = eventVersion;
        this.lines = new ArrayList<>(lines);
        this.missing = new ArrayList<>(missing);
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public ReservationOutcome.Status getStatus() {
        return status;
    }

    public String getEventVersion() {
        return eventVersion;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public List<ReservationLine> getLines() {
        return List.copyOf(lines);
    }

    public List<MissingEntry> getMissing() {
        return List.copyOf(missing);
    }

    @Embeddable
    public static class ReservationLine {

        @Column(length = 64)
        private String sku;

        @Column(name = "requested_qty", nullable = false)
        private int requestedQty;

        protected ReservationLine() {
        }

        public ReservationLine(String sku, int requestedQty) {
            this.sku = sku;
            this.requestedQty = requestedQty;
        }

        public String getSku() {
            return sku;
        }

        public int getRequestedQty() {
            return requestedQty;
        }
    }

    @Embeddable
    public static class MissingEntry {

        @Column(length = 64)
        private String sku;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 32)
        private MissingSku.Reason reason;

        protected MissingEntry() {
        }

        public MissingEntry(String sku, MissingSku.Reason reason) {
            this.sku = sku;
            this.reason = reason;
        }

        public String getSku() {
            return sku;
        }

        public MissingSku.Reason getReason() {
            return reason;
        }
    }
}
