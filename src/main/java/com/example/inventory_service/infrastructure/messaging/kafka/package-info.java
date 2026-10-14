/**
 * Kafka adapter (Spring Cloud Stream): consumer functions, outcome publisher
 * and retry/DLQ binding configuration. Consumers are idempotent on
 * {@code orderId}.
 */
package com.example.inventory_service.infrastructure.messaging.kafka;
