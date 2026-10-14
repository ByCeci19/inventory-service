# Data Model: Reserve Stock for Order

**Feature**: `001-reserve-stock-order` | **Date**: 2026-10-14 | **Sources**: [spec.md](spec.md), [research.md](research.md)

## Entities

### StockItem (JPA, PostgreSQL / H2 in tests)

| Field | Type | Constraints |
|-------|------|-------------|
| `sku` | String (PK) | non-blank, max 64 |
| `availableQty` | int | `>= 0` — domain invariant, checked on every mutation |

- Relationships: referenced (not joined) by `Reservation.lines.sku`; no FK from reservation lines (stock and reservations evolve independently; evaluation reads a snapshot within the reservation transaction).
- Concurrency: row is read inside the reserving transaction; negative stock is impossible by invariant + single decrement path (FR-009).

### Reservation (JPA — idempotency + decision record)

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | long (surrogate PK) | generated |
| `orderId` | String, UNIQUE, indexed | non-blank, max 64; idempotency key + log correlation id |
| `status` | enum `RESERVED \| FAILED` | non-null, immutable after insert |
| `lines` | collection of `ReservationLine` (sku, requestedQty) | ≥ 1 line; each qty > 0 (FR-007) |
| `missing` | collection of `MissingSku` (sku, reason) | non-empty iff status FAILED for stock reasons |
| `eventVersion` | String | `v1` (clarified); immutable |
| `decidedAt` | Instant | non-null, set once; replays return it verbatim |

- State transitions: none — a reservation row is insert-once, never updated (immutability is what makes replays verbatim).
- Validation (FR-007, pre-transaction): empty line list, zero/negative qty → invalid request; no row inserted, failure outcome built without a reservation record (edge case per spec).
- Idempotency rule (FR-004/FR-005): `findByOrderId` precedes any stock mutation; UNIQUE(`orderId`) backstops concurrent duplicate inserts → catch, re-read, return stored outcome.

### ReservationOutcome — published events (Kafka via SCS; AsyncAPI)

Two messages, one shape family, both carrying `eventVersion: v1`:

- `InventoryReserved`: `{ eventVersion: "v1", orderId, status: "RESERVED", items: [{sku, quantity}], decidedAt }`
- `InventoryReservationFailed`: `{ eventVersion: "v1", orderId, status: "FAILED", missingSkus: [{sku, reason}], decidedAt }`

Breaking schema change policy (constitution IV): new version value (`v2`), old consumers untouched; see `contracts/asyncapi.yaml`.

### DeadLetterRecord (operational view of DLQ topic)

| Field | Type | Constraints |
|-------|------|-------------|
| `orderId` | String (nullable) | preserved from record header where present |
| `rawPayload` | bytes/String | original message |
| `failureReason` | String | last error |
| `retryHistory` | list of timestamps | 3 entries per clarified policy |
| `parkedAt` | Instant | DLQ arrival time |

- Lifecycle: redelivered ≤3 retries (exp backoff) → parked; never auto-replayed (operator action only).

### AuditRecord (MongoDB document — audit/event history)

| Field | Type | Constraints |
|-------|------|-------------|
| `_id` | ObjectId | generated |
| `orderId` | String, indexed | one document per outcome (immutable) |
| `status` | `RESERVED \| FAILED` | mirrors reservation |
| `items` / `missingSkus` | arrays | mirrors outcome payload |
| `eventVersion` | String | `v1` |
| `decidedAt` | Instant | mirrors outcome |

- Write ordering (R-7): reservation commit first, audit second; audit failure never rolls back a decided reservation (logged with `orderId`).

## Validation summary (spec → model)

- FR-001 → all-lines-sufficient check inside aggregate before any decrement; single transaction.
- FR-003 → any-insufficient ⇒ no `StockItem` touched, `Reservation(FAILED)` + failed outcome with all missing SKUs.
- FR-007 → invalid shape ⇒ no stock change, no reservation row, failure outcome with validation reason.
- FR-009 → `availableQty >= 0` invariant + unique-order backstop ⇒ zero oversell/negative paths.
