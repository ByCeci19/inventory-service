-- V1 baseline (T015): mirrors the JPA mappings in
-- infrastructure/persistence/jpa (DDL owns the UNIQUE constraint on
-- reservation.order_id — the idempotency backstop, R-3).
-- Hibernate `ddl-auto: validate` checks this schema in production;
-- tests stay on H2 create-drop with Flyway disabled.

CREATE TABLE stock_item (
    sku            VARCHAR(64)  NOT NULL,
    available_qty  INTEGER      NOT NULL,
    CONSTRAINT pk_stock_item PRIMARY KEY (sku)
);

CREATE TABLE reservation (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    order_id      VARCHAR(64) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    event_version VARCHAR(8)  NOT NULL,
    decided_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reservation PRIMARY KEY (id),
    CONSTRAINT uq_reservation_order_id UNIQUE (order_id)
);

CREATE TABLE reservation_line (
    reservation_id BIGINT      NOT NULL,
    sku            VARCHAR(64),
    requested_qty  INTEGER     NOT NULL,
    CONSTRAINT fk_reservation_line_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (id)
        ON DELETE CASCADE
);

CREATE TABLE reservation_missing (
    reservation_id BIGINT      NOT NULL,
    sku            VARCHAR(64),
    reason         VARCHAR(32) NOT NULL,
    CONSTRAINT fk_reservation_missing_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (id)
        ON DELETE CASCADE
);
