package com.fintry.budget;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<CategoryBudget, UUID> {
  List<CategoryBudget> findByCompanyIdAndMonth(UUID companyId, LocalDate month);

  Optional<CategoryBudget> findByIdAndCompanyId(UUID id, UUID companyId);

  boolean existsByCompanyIdAndCategoryIdAndMonth(UUID companyId, UUID categoryId, LocalDate month);

  boolean existsByCategoryIdAndCompanyId(UUID categoryId, UUID companyId);
}
