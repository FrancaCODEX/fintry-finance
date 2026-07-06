package com.fintry.company.repository;

import com.fintry.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByDocument(String document);

    boolean existsByDocument(String document);

}