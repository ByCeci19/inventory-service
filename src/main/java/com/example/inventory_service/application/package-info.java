/**
 * Application layer: use cases orchestrating domain logic through ports.
 *
 * <p>Depends only on the domain layer and port interfaces
 * ({@code port.in} / {@code port.out}). No framework, JPA, Mongo or
 * Kafka types may appear here (constitution Principle II).
 */
package com.example.inventory_service.application;
