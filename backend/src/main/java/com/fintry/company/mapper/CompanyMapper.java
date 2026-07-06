package com.fintry.company.mapper;

import com.fintry.company.dto.request.CreateCompanyRequest;
import com.fintry.company.dto.response.CompanyResponse;
import com.fintry.company.entity.Company;

public class CompanyMapper {

    private CompanyMapper() {
    }

    public static Company toEntity(CreateCompanyRequest request) {

        Company company = new Company();

        company.setLegalName(request.getLegalName());
        company.setTradeName(request.getTradeName());
        company.setDocument(request.getDocument());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());

        return company;
    }

    public static CompanyResponse toResponse(Company company) {

        return CompanyResponse.builder()
                .id(company.getId())
                .legalName(company.getLegalName())
                .tradeName(company.getTradeName())
                .document(company.getDocument())
                .email(company.getEmail())
                .phone(company.getPhone())
                .createdAt(company.getCreatedAt())
                .build();
    }

}