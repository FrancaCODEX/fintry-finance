package com.fintry.company.service;

import com.fintry.company.dto.request.CreateCompanyRequest;
import com.fintry.company.dto.request.UpdateCompanyRequest;
import com.fintry.company.dto.response.CompanyResponse;
import com.fintry.company.entity.Company;
import com.fintry.company.exception.CompanyAlreadyExistsException;
import com.fintry.company.exception.CompanyNotFoundException;
import com.fintry.company.mapper.CompanyMapper;
import com.fintry.company.repository.CompanyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

  private final CompanyRepository repository;
  private final com.fintry.subscription.SubscriptionService subscriptions;
  private final com.fintry.audit.AuditService audit;

  private java.util.Map<String, String> snapshot(Company c) {
    return com.fintry.audit.AuditService.fields(
        "Razão social",
        c.getLegalName(),
        "Nome fantasia",
        c.getTradeName(),
        "Documento",
        c.getDocument(),
        "E-mail",
        c.getEmail(),
        "Telefone",
        c.getPhone());
  }

  @Transactional
  public CompanyResponse create(CreateCompanyRequest request) {

    if (repository.existsByDocument(request.getDocument())) {
      throw new CompanyAlreadyExistsException(request.getDocument());
    }

    Company company = CompanyMapper.toEntity(request);

    Company savedCompany = repository.saveAndFlush(company);
    subscriptions.initialize(savedCompany.getId());

    return CompanyMapper.toResponse(savedCompany);
  }

  @Transactional(readOnly = true)
  public CompanyResponse getById(UUID id) {

    Company company = repository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));

    return CompanyMapper.toResponse(company);
  }

  @Transactional(readOnly = true)
  public CompanyResponse getByDocument(String document) {

    Company company =
        repository
            .findByDocument(document)
            .orElseThrow(() -> new CompanyNotFoundException(document));

    return CompanyMapper.toResponse(company);
  }

  // Listando empresas
  @Transactional(readOnly = true)
  public Page<CompanyResponse> findAll(String tradeName, String document, Pageable pageable) {

    Page<Company> companies;

    if (tradeName != null && !tradeName.isBlank()) {

      companies = repository.findByTradeNameContainingIgnoreCase(tradeName, pageable);

    } else if (document != null && !document.isBlank()) {

      companies = repository.findByDocumentContaining(document, pageable);

    } else {

      companies = repository.findAll(pageable);
    }

    return companies.map(CompanyMapper::toResponse);
  }

  @Transactional
  public CompanyResponse update(UUID id, UpdateCompanyRequest request) {

    Company company = repository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));
    if (repository.existsByDocumentAndIdNot(request.getDocument(), id)) {

      throw new CompanyAlreadyExistsException(request.getDocument());
    }
    var before = snapshot(company);
    CompanyMapper.updateEntity(company, request);

    Company updatedCompany = repository.save(company);
    audit.change(
        "UPDATE",
        "COMPANY",
        id,
        company.getTradeName() == null ? company.getLegalName() : company.getTradeName(),
        before,
        snapshot(company));

    return CompanyMapper.toResponse(updatedCompany);
  }

  @Transactional
  public void delete(UUID id) {

    Company company = repository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));

    repository.delete(company);
  }
}
