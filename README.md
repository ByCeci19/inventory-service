# Inventory Service

Microservicio de inventario con reserva de stock idempotente y orientado a eventos.

- Reserva todo o nada por `orderId`: con stock suficiente decrementa y emite `InventoryReserved`; sin stock no muta nada y emite `InventoryReservationFailed` con los SKUs faltantes.
- Reintentos con el mismo `orderId` devuelven el resultado original sin doble descuento (idempotencia).
- Consumidor Kafka tolerante a redelivery; mensajes envenenados van a DLQ tras 3 reintentos con backoff exponencial.
- Auditoría de cada decisión en MongoDB, trazable por `orderId`.

## Stack

Java 21 (Temurin) · Gradle 9 (wrapper) · Spring Boot 3.5.16 · Spring Cloud Stream + Kafka binder 4.3.3 · PostgreSQL (JPA) · MongoDB · Flyway · springdoc-openapi 2.9.1 · Karate.

Capas DDD bajo `com.example.inventory_service`: `domain` (agregados e invariantes) · `application` (casos de uso + puertos `port/in`, `port/out`) · `infrastructure` (`web`, `persistence/jpa`, `persistence/mongo`, `messaging/kafka`).

Contratos versionados: `POST /api/v1/reservations` ([openapi.yaml](specs/001-reserve-stock-order/contracts/openapi.yaml)) · eventos `InventoryReserved` / `InventoryReservationFailed` v1 ([asyncapi.yaml](specs/001-reserve-stock-order/contracts/asyncapi.yaml)).

## Sin Docker

Tests sin contenedores: JPA contra H2, mensajería con EmbeddedKafka, puerto Mongo con mocks, Karate contra la app en `RANDOM_PORT`.

## Uso

```bash
./gradlew test          # unit + integración (H2, EmbeddedKafka)
./gradlew test --tests "*Karate*"   # contratos contra RANDOM_PORT
./gradlew check         # puerta verde total
./gradlew bootRun       # arranca con PostgreSQL/Mongo/Kafka locales
```

Reservar stock:

```bash
curl -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1","items":[{"sku":"SKU-A","quantity":3}]}'
```

## Configuración

Fuente única `src/main/resources/application.yml` (variables con defecto local):

| Variable | Defecto | Uso |
|----------|---------|-----|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/inventory` / `inventory` | PostgreSQL + Flyway (`validate`) |
| `MONGO_URI` | `mongodb://localhost:27017/inventory` | Auditoría |
| `KAFKA_BROKERS` | `localhost:9092` | Binder Kafka |

En tests: H2 en memoria (`create-drop`), Flyway desactivado, binder inerte hasta que cada test inyecta EmbeddedKafka. Logs con `orderId` como correlation id (MDC, `logback-spring.xml`).

## Estado

Desarrollo guiado por spec en `specs/001-reserve-stock-order/` (`spec.md`, `plan.md`, `tasks.md`, `research.md`, `data-model.md`, `quickstart.md`). Fase 1 (Setup T001–T006) completa en la rama `feature/reserve-stock-sdd`; ver `tasks.md` para el progreso por tarea.
