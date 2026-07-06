package com.fintry.company.service;

import com.fintry.company.dto.request.CreateCompanyRequest;
import com.fintry.company.dto.response.CompanyResponse;
import com.fintry.company.entity.Company;
import com.fintry.company.exception.CompanyAlreadyExistsException;
import com.fintry.company.mapper.CompanyMapper;
import com.fintry.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}