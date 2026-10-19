package com.fintry.auth;

import com.fintry.company.entity.Company;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder encoder;
    public record RegisterRequest(
        @NotBlank @Size(max=120) String name,
        @NotBlank @Email @Size(max=255) String email,
        @NotBlank @Size(min=10, max=64) String password,
        @NotBlank @Size(max=255) String companyName,
        @NotBlank @Pattern(regexp="[0-9]{14}", message="Informe os 14 dígitos do CNPJ.") String document) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max=64) String password) {}
    public record UserView(UUID id, String name, String email, UUID companyId, String companyName) {}

    @Transactional
    public void register(RegisterRequest input) {
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha ultrapassa o limite de 72 bytes. Use menos caracteres especiais.");
        var email = input.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email) || companies.existsByDocument(input.document()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail ou CNPJ já cadastrado.");
        Company company = Company.builder().legalName(input.companyName().strip())
            .tradeName(input.companyName().strip()).document(input.document()).email(email).build();
        companies.saveAndFlush(company);
        AppUser user = new AppUser();
        user.setCompanyId(company.getId()); user.setName(input.name().strip()); user.setEmail(email);
        user.setPasswordHash(encoder.encode(input.password())); users.save(user);
    }
    public AppUser authenticate(LoginRequest input) {
        var user = users.findByEmail(input.email().strip().toLowerCase(Locale.ROOT));
        if (user.isEmpty() || input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !encoder.matches(input.password(), user.get().getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        return user.get();
    }
    public UserView view(AppUser user) {
        var company = companies.findById(user.getCompanyId()).orElseThrow();
        return new UserView(user.getId(), user.getName(), user.getEmail(), company.getId(), company.getTradeName());
    }
}
