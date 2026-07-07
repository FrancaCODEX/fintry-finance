package com.fintry.company.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCompanyRequest {

    @NotBlank(message = "Legal name is required")
    @Size(max = 255)
    private String legalName;

    @NotBlank(message = "Trade name is required")
    @Size(max = 255)
    private String tradeName;

    @NotBlank(message = "Document is required")
    @Size(max = 20)
    private String document;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    private String email;

    @NotBlank(message = "Phone is required")
    @Size(max = 20)
    private String phone;
}