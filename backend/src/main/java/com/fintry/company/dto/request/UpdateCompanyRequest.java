package com.fintry.company.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCompanyRequest {

    @NotBlank
    private String tradeName;

    @NotBlank
    private String legalName;

    @NotBlank
    private String document;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String phone;
}