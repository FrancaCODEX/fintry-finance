package com.fintry.company.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CompanyResponse {

    private UUID id;

    private String legalName;

    private String tradeName;

    private String document;

    private String email;

    private String phone;

    private LocalDateTime createdAt;

}