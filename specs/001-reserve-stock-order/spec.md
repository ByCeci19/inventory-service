# Feature Specification: Reserve Stock for Order

**Feature Branch**: `001-reserve-stock-order`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Feature: Reserve Stock for Order (Inventory microservice). Como sistema de órdenes, necesito reservar stock para una orden. Cuando hay stock suficiente, se reserva y se publica evento InventoryReserved. Cuando no hay, no se muta el stock y se publica InventoryReservationFailed con motivo. AC-001..AC-006, Non-Functional (p95 < 200ms, logs con correlationId=orderId, trazabilidad AC->test), Out of scope: cancelación, reaprovisionamiento, compensaciones Saga completas."

## Clarifications

### Session 2026-10-14

- Q: How many consumer retries with what backoff before DLQ? → A: 3 retries with exponential backoff.
- Q: How should outcomes carry schema version? → A: Explicit version field in payload (starting at v1).
- Q: What expected load defines the p95 target? → A: Low load baseline (single node, tens of rps, small orders).
- Q: What authentication protects reservation intake? → A: None in v1; trusted cluster network only.
- Q: Must every outcome be durably persisted in audit? → A: Yes, every outcome persisted and queryable by orderId.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reserve stock when available (Priority: P1)

The ordering system requests a stock reservation for an order containing one or more line items (SKU + quantity). When every item has sufficient available stock, the inventory service decrements available stock, records the reservation, and notifies with an `InventoryReserved` outcome carrying the orderId, the reserved items, and the decision timestamp.

**Why this priority**: This is the core happy path; without it the feature delivers no value.

**Independent Test**: Can be fully tested by submitting a reservation for SKUs with known stock and verifying stock decreased, the reservation is recorded, and an `InventoryReserved` outcome is emitted. Delivers reserving capability end to end.

**Acceptance Scenarios**:

1. **Given** SKU-A has 10 units available and SKU-B has 5, **When** a reservation for order-1 requests 3x SKU-A and 2x SKU-B, **Then** available stock becomes 7 and 3 respectively, a reservation for order-1 exists, and an `InventoryReserved` outcome with order-1, both items, and a timestamp is published. (Covers AC-001, AC-002)
2. **Given** SKU-A has exactly the requested quantity, **When** a reservation is requested, **Then** the reservation succeeds and remaining stock is zero (boundary of AC-001).

---

### User Story 2 - Reject reservation when stock is insufficient (Priority: P1)

When any requested item lacks sufficient available stock, the service rejects the whole reservation atomically: no SKU's stock is modified, the rejection is recorded against the orderId, and an `InventoryReservationFailed` outcome is published identifying each missing SKU and the reason.

**Why this priority**: Preventing oversell is as critical as the happy path; partial mutation would corrupt inventory.

**Independent Test**: Can be fully tested by requesting more units than available for at least one SKU and verifying no stock changed and a failure outcome names the missing SKUs. Delivers stock protection independently of US1.

**Acceptance Scenarios**:

1. **Given** SKU-A has 2 units available, **When** a reservation for order-2 requests 5x SKU-A, **Then** SKU-A still has 2 units, no reservation decrement occurs, and an `InventoryReservationFailed` outcome with order-2 and SKU-A as missing is published. (Covers AC-003)
2. **Given** SKU-A has enough but SKU-B does not, **When** a multi-item reservation is requested, **Then** neither SKU's stock changes and the failure outcome lists SKU-B (all-or-nothing). (Covers AC-003)

---

### User Story 3 - Retry with the same orderId is idempotent (Priority: P2)

Retrying a reservation with an already-seen orderId returns the originally recorded outcome without decrementing stock again and without creating a second reservation, regardless of whether the original outcome was success or failure.

**Why this priority**: Network retries and at-least-once delivery make duplicates inevitable; idempotency is the correctness guarantee.

**Independent Test**: Can be fully tested by submitting the same orderId twice and verifying stock moved only once and both responses/outcomes match. Delivers safety under retry.

**Acceptance Scenarios**:

1. **Given** order-3 was already reserved successfully (stock already decremented), **When** the same order-3 request is submitted again, **Then** stock is unchanged by the retry and the returned outcome equals the original `InventoryReserved`. (Covers AC-004)
2. **Given** order-4 was already rejected for missing stock, **When** order-4 is retried, **Then** stock is unchanged and the returned outcome equals the original `InventoryReservationFailed`. (Covers AC-004)
3. **Given** order-5 was already recorded, **When** a retry arrives with a different item list but the same order-5, **Then** the original outcome is returned and stock is not modified (conflicting-payload policy, see Assumptions).

---

### User Story 4 - Redelivered messages and poison messages are handled safely (Priority: P2)

As an inventory operator, I need message consumption to tolerate redelivery (same reservation message arriving more than once produces no duplicate reservation) and to isolate persistently failing messages: after bounded retries with backoff they are routed to a dead-letter queue for inspection, never silently dropped or endlessly retried.

**Why this priority**: Guarantees eventual consistency and operability of the event-driven flow under failure.

**Independent Test**: Can be fully tested by delivering the same reservation message twice to the consumer and by injecting a persistently failing message, verifying single reservation effect and DLQ routing respectively.

**Acceptance Scenarios**:

1. **Given** a reservation message for order-6 was already processed, **When** the identical message is redelivered, **Then** no second reservation or stock decrement occurs. (Covers AC-005)
2. **Given** a malformed or persistently failing message, **When** it is consumed, **Then** the consumer retries with backoff and after exhausting retries routes it to the DLQ with its correlation identifier preserved. (Covers AC-006)

---

### Edge Cases

- Reservation requests an SKU that does not exist in inventory: treated as missing stock; no mutation; failure outcome names the unknown SKU.
- Reservation requests zero or negative quantity: rejected as invalid without stock mutation and without a reservation record; failure outcome states the validation reason.
- Empty item list: rejected as invalid; no outcome stock effect.
- Duplicate orderId with a different item payload than the original: original outcome wins; retry does not mutate stock; mismatch is logged with orderId as correlation identifier.
- Concurrent reservations for the last units of the same SKU from different orderIds: exactly one outcome set succeeds; the loser gets a failure outcome; stock never goes negative.
- Redelivered failure outcomes: replays of an already-recorded rejection return the stored rejection without recomputation.
- Clock/timestamp: every outcome carries a decision timestamp; retries return the original timestamp, not a new one.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST atomically evaluate all requested items against available stock and, when every item has sufficient stock, decrement available stock for each item and record exactly one reservation keyed by orderId. (AC-001)
- **FR-002**: System MUST emit an `InventoryReserved` outcome for every successful reservation containing orderId, the reserved items with quantities, the decision timestamp, and an explicit schema version field starting at `v1`. (AC-002)
- **FR-003**: System MUST, when any item lacks sufficient stock (including unknown SKUs), leave all stock unmodified, record the rejection against the orderId, and emit an `InventoryReservationFailed` outcome naming each missing/insufficient SKU with its reason, carrying the decision timestamp and an explicit schema version field starting at `v1`. (AC-003)
- **FR-004**: System MUST make reservation outcome lookup by orderId precede any stock mutation, so a repeated request with an already-recorded orderId returns the stored outcome with no additional stock change and no second reservation record. (AC-004)
- **FR-005**: System MUST process redelivered reservation messages idempotently: reprocessing an already-recorded orderId produces no duplicate reservation and no extra stock movement. (AC-005)
- **FR-006**: System MUST retry failed message consumption up to 3 times with exponential backoff and, after the retry budget is exhausted, route the message to a dead-letter queue preserving its correlation identifier, never dropping it silently nor retrying unboundedly. (AC-006)
- **FR-007**: System MUST reject invalid requests (empty item list, zero/negative quantity) without mutating stock, recording the validation reason in the failure outcome.
- **FR-008**: System MUST include the orderId as correlation identifier in every log line emitted while handling that reservation, every outcome MUST carry a decision timestamp, and every outcome MUST be durably persisted in the audit store queryable by orderId.
- **FR-009**: System MUST guarantee available stock never goes negative, including under concurrent reservations for the same SKU from distinct orderIds.
- **FR-010**: Each acceptance criterion AC-001..AC-006 MUST be covered by at least one automated test, with the mapping recorded in the test suite.

*Traceability: FR-001->AC-001, FR-002->AC-002, FR-003->AC-003, FR-004->AC-004, FR-005->AC-005, FR-006->AC-006.*

### Key Entities

- **StockItem**: An SKU held in inventory with an available quantity. Available quantity MUST never be negative; it is the sole mutable counter affected by reservations.
- **Reservation**: The record binding an orderId to its requested items, outcome (reserved / failed), reserved quantities, missing SKUs with reasons, and decision timestamp. Keyed and looked up by orderId before any mutation; immutable once recorded.
- **ReservationOutcome (InventoryReserved / InventoryReservationFailed)**: The published decision for an orderId. Success carries orderId, reserved items, timestamp, and explicit schema version (`v1` at launch); failure carries orderId, missing SKUs with reasons, timestamp, and explicit schema version (`v1`). Replays return the stored outcome verbatim.
- **DeadLetterRecord**: A persistently failing inbound message parked after retries, retaining its correlation identifier (orderId where present), failure reason, and retry history for operator inspection.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ordering system receives a correct reservation decision (reserved with decremented stock, or failed with no stock change and named missing SKUs) for every valid request submitted.
- **SC-002**: Zero oversell: under any sequence of reservations including concurrent ones, recorded reservations never exceed available stock and stock never goes negative.
- **SC-003**: Zero duplicate effect: repeating any orderId any number of times produces exactly one stock movement and one stored outcome; all repeats return the identical outcome.
- **SC-004**: Reservation decisions reach the ordering system promptly: 95% of decisions complete within 200 ms at the low load baseline (single node, tens of reservations/sec, small multi-item orders).
- **SC-005**: Every failure is diagnosable: 100% of reservation outcomes and dead-letter records are traceable by orderId from request through logs to outcome.
- **SC-006**: Test traceability holds: each of AC-001..AC-006 maps to at least one passing automated test, verified in the suite report.

## Assumptions

- All-or-nothing semantics: a reservation succeeds only if every item is fully available; partial reservations are not offered in this version.
- orderId is globally unique per business order and is the idempotency key and log correlation identifier.
- Conflicting retry payload (same orderId, different items): the originally recorded outcome wins; the retry returns it verbatim, mutates nothing, and the mismatch is logged. Rationale: idempotency over last-write-wins; conflicts indicate caller bug.
- At-least-once delivery is assumed for inbound messages and outcomes; duplicates and redelivery are normal, not exceptional.
- Retry policy is fixed: up to 3 retries with exponential backoff, then DLQ; DLQ retention and alerting are operator concerns outside this spec.
- Audit/event history persistence (project context: document store) records every outcome, queryable by orderId, and supports traceability verification; it does not affect reservation correctness.
- Technology constraints from project context (Java 21, Spring Boot 3, Gradle; PostgreSQL/JPA for inventory; document store for audit; Kafka via Spring Cloud Stream; H2 + EmbeddedKafka + embedded-Mongo-or-mocks + Karate on RANDOM_PORT with no Docker) are taken as given and detailed in plan, not in functional requirements.
- Out of scope: reservation cancellation, replenishment/restock, full Saga/compensating-transaction flows, caller authentication/authorization (v1 assumes trusted cluster network).
- Performance baseline for SC-004 is the low load baseline: single node, tens of reservations/sec, single-SKU and small multi-item orders.
