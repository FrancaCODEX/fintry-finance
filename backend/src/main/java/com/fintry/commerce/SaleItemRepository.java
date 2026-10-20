package com.fintry.commerce;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {
  List<SaleItem> findBySaleIdOrderBySkuAsc(UUID saleId);
}
