package com.fintry.company.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCompanyRequest {

    @NotBlank
    @Size(max = 255)
    private String legalName;

    @Size(max = 255)
    private String tradeName;

    @NotBlank
    @Size(max = 20)
    private String document;

    @Email
    private String email;

    @Size(max = 20)
    private String phone;

}