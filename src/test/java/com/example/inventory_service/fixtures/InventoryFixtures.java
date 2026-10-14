package com.example.inventory_service.fixtures;

import com.example.inventory_service.domain.ReservationItem;
import com.example.inventory_service.domain.StockItem;
import com.example.inventory_service.infrastructure.persistence.jpa.StockItemEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared builders for the quickstart baseline: {@code SKU-A} x10,
 * {@code SKU-B} x5 (quickstart.md).
 */
public final class InventoryFixtures {

    public static final String SKU_A = "SKU-A";
    public static final String SKU_B = "SKU-B";
    public static final int SKU_A_STOCK = 10;
    public static final int SKU_B_STOCK = 5;

    private InventoryFixtures() {
    }

    /** Baseline stock snapshot: SKU-A x10, SKU-B x5. */
    public static Map<String, StockItem> baselineStock() {
        Map<String, StockItem> stock = new LinkedHashMap<>();
        stock.put(SKU_A, StockItem.of(SKU_A, SKU_A_STOCK));
        stock.put(SKU_B, StockItem.of(SKU_B, SKU_B_STOCK));
        return stock;
    }

    /** Baseline stock as JPA entities, ready to persist. */
    public static List<StockItemEntity> baselineEntities() {
        return List.of(
                new StockItemEntity(SKU_A, SKU_A_STOCK),
                new StockItemEntity(SKU_B, SKU_B_STOCK));
    }

    public static ReservationItem line(String sku, int quantity) {
        return new ReservationItem(sku, quantity);
    }

    /** QS-1 happy-path lines: 3x SKU-A, 2x SKU-B. */
    public static List<ReservationItem> happyPathLines() {
        return List.of(line(SKU_A, 3), line(SKU_B, 2));
    }
}
