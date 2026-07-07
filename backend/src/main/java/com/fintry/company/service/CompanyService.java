package com.fintry.company.service;

import com.fintry.company.dto.request.CreateCompanyRequest;
import com.fintry.company.dto.request.UpdateCompanyRequest;
import com.fintry.company.dto.response.CompanyResponse;
import com.fintry.company.entity.Company;
import com.fintry.company.exception.CompanyAlreadyExistsException;
import com.fintry.company.exception.CompanyNotFoundException;
import com.fintry.company.mapper.CompanyMapper;
import com.fintry.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository repository;

    @Transactional
    public CompanyResponse create(CreateCompanyRequest request) {

        if (repository.existsByDocument(request.getDocument())) {
            throw new CompanyAlreadyExistsException(request.getDocument());
        }

        Company company = CompanyMapper.toEntity(request);

        Company savedCompany = repository.save(company);

        return CompanyMapper.toResponse(savedCompany);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID id) {

        Company company = repository.findById(id)
                .orElseThrow(() ->
                        new CompanyNotFoundException(id));

        return CompanyMapper.toResponse(company);
    }

    //Listando empresas
    @Transactional(readOnly = true)
    public Page<CompanyResponse> findAll(Pageable pageable) {

        return repository
                .findAll(pageable)
                .map(CompanyMapper::toResponse);

    }

    @Transactional
    public CompanyResponse update(UUID id, UpdateCompanyRequest request) {

        Company company = repository.findById(id)
                .orElseThrow(() ->
                        new CompanyNotFoundException(id));

        CompanyMapper.updateEntity(company, request);

        Company updatedCompany = repository.save(company);

        return CompanyMapper.toResponse(updatedCompany);
    }

}