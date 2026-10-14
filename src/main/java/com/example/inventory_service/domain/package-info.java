/**
 * Domain layer: aggregates, value objects and domain events.
 *
 * <p>Owns all business invariants (e.g. available stock never negative,
 * all-or-nothing reservation, immutable-once-recorded outcomes).
 * Depends on nothing outside this package (constitution Principle I).
 */
package com.example.inventory_service.domain;
