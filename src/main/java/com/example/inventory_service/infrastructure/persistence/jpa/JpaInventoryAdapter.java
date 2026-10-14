package com.example.inventory_service.infrastructure.persistence.jpa;

import com.example.inventory_service.application.port.out.InventoryPort;
import com.example.inventory_service.domain.MissingSku;
import com.example.inventory_service.domain.ReservationItem;
import com.example.inventory_service.domain.ReservationOutcome;
import com.example.inventory_service.domain.StockItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link InventoryPort} (PostgreSQL in production,
 * H2 in tests).
 *
 * <p>{@link #storeDecision} persists the outcome row plus the resulting stock
 * in a single transaction. A concurrent duplicate {@code orderId} insert
 * violates the UNIQUE constraint and propagates as a persistence exception
 * so the caller can re-read the stored outcome (R-3) — it is never swallowed
 * here.
 */
@Repository
public class JpaInventoryAdapter implements InventoryPort {

    private final StockItemRepository stockItems;
    private final ReservationRepository reservations;

    public JpaInventoryAdapter(StockItemRepository stockItems, ReservationRepository reservations) {
        this.stockItems = stockItems;
        this.reservations = reservations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationOutcome> findOutcomeByOrderId(String orderId) {
        return reservations.findByOrderId(orderId).map(JpaInventoryAdapter::toOutcome);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, StockItem> loadStock(Set<String> skus) {
        return stockItems.findBySkuIn(skus).stream()
                .collect(Collectors.toMap(
                        StockItemEntity::getSku,
                        e -> StockItem.of(e.getSku(), e.getAvailableQty()),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    @Override
    @Transactional
    public void storeDecision(ReservationOutcome outcome, Map<String, StockItem> resultingStock) {
        reservations.save(toEntity(outcome));
        if (outcome.status() == ReservationOutcome.Status.RESERVED) {
            for (StockItem item : resultingStock.values()) {
                StockItemEntity entity = stockItems.findById(item.sku())
                        .orElseGet(() -> new StockItemEntity(item.sku(), item.availableQty()));
                entity.setAvailableQty(item.availableQty());
                stockItems.save(entity);
            }
        }
    }

    private static ReservationOutcome toOutcome(ReservationEntity entity) {
        return new ReservationOutcome(
                entity.getOrderId(),
                entity.getStatus(),
                entity.getEventVersion(),
                entity.getLines().stream()
                        .map(l -> new ReservationItem(l.getSku(), l.getRequestedQty()))
                        .toList(),
                entity.getMissing().stream()
                        .map(m -> new MissingSku(m.getSku(), m.getReason()))
                        .toList(),
                entity.getDecidedAt());
    }

    private static ReservationEntity toEntity(ReservationOutcome outcome) {
        return new ReservationEntity(
                outcome.orderId(),
                outcome.status(),
                outcome.eventVersion(),
                outcome.items().stream()
                        .map(i -> new ReservationEntity.ReservationLine(i.sku(), i.quantity()))
                        .toList(),
                outcome.missingSkus().stream()
                        .map(m -> new ReservationEntity.MissingEntry(m.sku(), m.reason()))
                        .toList(),
                outcome.decidedAt());
    }
}
