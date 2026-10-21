package com.fintry.commerce;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
    extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id=:id and p.companyId=:company")
  Optional<Product> lock(@Param("id") UUID id, @Param("company") UUID company);

  Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);

  boolean existsByCompanyIdAndSkuAndIdNot(UUID company, String sku, UUID id);

  @Query(
      "select count(p), sum(case when p.kind='PRODUCT' and p.quantity<=p.minimum then 1 else 0"
          + " end), coalesce(sum(p.quantity*p.cost),0) from Product p where p.companyId=:company"
          + " and p.active=true and (:kind is null or p.kind=:kind)")
  List<Object[]> summary(@Param("company") UUID company, @Param("kind") Product.Kind kind);
}
