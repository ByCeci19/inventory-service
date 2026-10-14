# Quickstart: Reserve Stock for Order

**Feature**: `001-reserve-stock-order` | **Date**: 2026-10-14

Runnable validation guide. Each scenario maps to its acceptance criterion; all run without Docker (H2 + EmbeddedKafka + mocked audit port + Karate on `RANDOM_PORT`). No implementation code here — bodies belong to `tasks.md` / implementation.

## Prerequisites

- Java 21 (Temurin), Gradle wrapper (`./gradlew`)
- No Docker, no external Kafka/PostgreSQL/MongoDB
- After R-1: `build.gradle` aligned to Spring Boot 3.x + SCS binder; test deps: H2, `spring-kafka-test`, Mockito, Karate JUnit 5

## Run

```bash
./gradlew test                          # unit + integration (H2, EmbeddedKafka)
./gradlew test --tests "*Karate*"       # contract suite vs RANDOM_PORT
./gradlew check                         # all green = done gate
```

Seeded baseline used below: `SKU-A` ×10, `SKU-B` ×5 (via test fixtures, not production seed).

## Scenarios

### QS-1 Happy path (AC-001, AC-002)

```bash
curl -s -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1","items":[{"sku":"SKU-A","quantity":3},{"sku":"SKU-B","quantity":2}]}'
```

- Expect 200: `status RESERVED`, `eventVersion v1`, both items, `decidedAt` set.
- Expect stock now 7 / 3; one `Reservation(order-1)` row; one audit document for `order-1`; one `InventoryReserved` on `inventory.reservation.decisions`.

### QS-2 Insufficient stock, no mutation (AC-003)

```bash
curl -s -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-2","items":[{"sku":"SKU-A","quantity":999}]}'
```

- Expect 200: `status FAILED`, `missingSkus=[{sku: SKU-A, reason: INSUFFICIENT_STOCK}]`.
- Expect stock unchanged; `InventoryReservationFailed` published; audit document for `order-2`.

### QS-3 Idempotent retry (AC-004)

- Repeat the QS-1 request byte-for-byte with `orderId order-1`.
- Expect identical body (same `decidedAt`), stock still 7 / 3, still one reservation row.

### QS-4 Redelivered consumer message (AC-005)

- Publish the `ReservationRequested` for a fresh `orderId` twice to `inventory.reservation.requests` (EmbeddedKafka test).
- Expect exactly one stock movement and one stored outcome.

### QS-5 Poison message → DLQ (AC-006)

- Publish a persistently failing message (e.g. unknown SKU shape that errors mid-consume per test design).
- Expect 3 retries with growing delays, then exactly one record on `inventory.reservation.requests.dlq` with `orderId` header, `retryCount 3`, reason preserved; main flow unaffected.

### QS-6 Latency + traceability (Non-Functional, FR-010/SC-006)

- Perf: timed reservation loop at low-load baseline (tens rps, small orders) — p95 < 200 ms.
- Logs: `grep order-1` across app logs shows every handling line for the request.
- Traceability: `./gradlew test` report shows ≥1 passing test per AC-001..AC-006.

## Contract references

- REST shape: [contracts/openapi.yaml](contracts/openapi.yaml) (Karate asserts it).
- Event shapes + DLQ: [contracts/asyncapi.yaml](contracts/asyncapi.yaml).
- Entities + invariants: [data-model.md](data-model.md).
