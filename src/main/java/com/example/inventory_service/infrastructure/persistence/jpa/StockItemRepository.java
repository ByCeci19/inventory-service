package com.example.inventory_service.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockItemRepository extends JpaRepository<StockItemEntity, String> {

    List<StockItemEntity> findBySkuIn(Iterable<String> skus);
}
