package com.fintry.company.controller;

import com.fintry.auth.CurrentUser;
import com.fintry.company.dto.request.UpdateCompanyRequest;
import com.fintry.company.dto.response.CompanyResponse;
import com.fintry.company.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** The browser can only access the company belonging to its authenticated user. */
@RestController @RequestMapping("/api/v1/companies") @RequiredArgsConstructor
public class CompanyController {
    private final CompanyService service;
    private final CurrentUser current;
    @GetMapping("/me") public CompanyResponse get() { return service.getById(current.companyId()); }
    @PutMapping("/me") public CompanyResponse update(@Valid @RequestBody UpdateCompanyRequest input) {
        return service.update(current.companyId(), input);
    }
}
