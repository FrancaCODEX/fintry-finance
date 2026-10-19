package com.fintry.company.repository;

import com.fintry.company.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Company c where c.id=:id")
    Optional<Company> lock(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Company> findByDocument(String document);

    boolean existsByDocument(String document);
    boolean existsByDocumentAndIdNot(String document, UUID id);

    Page<Company> findByTradeNameContainingIgnoreCase(
            String tradeName,
            Pageable pageable);

    Page<Company> findByDocumentContaining(
            String document,
            Pageable pageable);
}