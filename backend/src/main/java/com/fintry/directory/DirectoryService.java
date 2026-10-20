package com.fintry.directory;

import com.fintry.auth.CurrentUser;
import com.fintry.finance.EntryRepository;
import com.fintry.finance.FinancialEntry;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectoryService {
  private final CurrentUser current;
  private final com.fintry.audit.AuditService audit;
  private final ContactRepository contacts;
  private final CategoryRepository categories;
  private final EntryRepository entries;
  private final com.fintry.budget.BudgetRepository budgets;

  public record ContactInput(
      @NotBlank @Size(max = 120) String name,
      @NotNull Contact.Kind kind,
      @Size(max = 18) String document,
      @Email @Size(max = 255) String email,
      @Size(max = 24) String phone,
      @Size(max = 1000) String notes,
      Boolean active) {}

  public record CategoryInput(
      @NotBlank @Size(max = 80) String name,
      @NotNull FinancialCategory.Kind kind,
      Boolean active) {}

  public record ContactView(
      UUID id,
      String name,
      Contact.Kind kind,
      String document,
      String email,
      String phone,
      String notes,
      boolean active) {}

  public record CategoryView(UUID id, String name, FinancialCategory.Kind kind, boolean active) {}

  public record ContactPage(
      List<ContactView> content, long totalElements, int totalPages, int number) {}

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado.");
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private String clean(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }

  public ContactView view(Contact c) {
    return new ContactView(
        c.getId(),
        c.getName(),
        c.getKind(),
        c.getDocument(),
        c.getEmail(),
        c.getPhone(),
        c.getNotes(),
        c.isActive());
  }

  public CategoryView view(FinancialCategory c) {
    return new CategoryView(c.getId(), c.getName(), c.getKind(), c.isActive());
  }

  public ContactPage contacts(
      String search, Contact.Kind kind, Boolean active, int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw invalid("Paginação inválida.");
    UUID company = current.companyId();
    Specification<Contact> filter =
        (root, query, cb) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(cb.equal(root.get("companyId"), company));
          if (active != null) p.add(cb.equal(root.get("active"), active));
          if (kind != null) p.add(root.get("kind").in(kind, Contact.Kind.BOTH));
          if (search != null && !search.isBlank()) {
            String escaped =
                search
                    .strip()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '!'),
                    cb.like(root.get("document"), "%" + escaped + "%", '!')));
          }
          return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result = contacts.findAll(filter, PageRequest.of(page, size, Sort.by("name", "id")));
    return new ContactPage(
        result.map(this::view).getContent(),
        result.getTotalElements(),
        result.getTotalPages(),
        page);
  }

  private Map<String, String> contactFields(Contact c) {
    return com.fintry.audit.AuditService.fields(
        "Nome",
        c.getName(),
        "Tipo",
        c.getKind(),
        "Documento",
        c.getDocument(),
        "E-mail",
        c.getEmail(),
        "Telefone",
        c.getPhone(),
        "Observações",
        c.getNotes(),
        "Ativo",
        c.isActive());
  }

  private Map<String, String> categoryFields(FinancialCategory c) {
    return com.fintry.audit.AuditService.fields(
        "Nome", c.getName(), "Tipo", c.getKind(), "Ativa", c.isActive());
  }

  @Transactional
  public ContactView saveContact(UUID id, ContactInput input) {
    var c =
        id == null
            ? new Contact()
            : contacts.lock(id, current.companyId()).orElseThrow(this::missing);
    if (id != null
        && c.getKind() != input.kind()
        && entries.existsByContactIdAndCompanyId(id, current.companyId()))
      throw invalid(
          "Um contato com lançamentos vinculados deve manter seu tipo. Crie outro cadastro se"
              + " necessário.");
    var before = id == null ? Map.<String, String>of() : contactFields(c);
    String document = clean(input.document());
    if (document != null) {
      document = document.replaceAll("[. /-]", "");
      if (!document.matches("[0-9]{11}|[0-9]{14}"))
        throw invalid("Informe CPF com 11 ou CNPJ com 14 dígitos.");
    }
    c.setCompanyId(current.companyId());
    c.setName(input.name().strip());
    c.setKind(input.kind());
    c.setDocument(document);
    c.setEmail(clean(input.email()));
    c.setPhone(clean(input.phone()));
    c.setNotes(clean(input.notes()));
    if (input.active() != null) c.setActive(input.active());
    contacts.save(c);
    audit.change(
        id == null ? "CREATE" : "UPDATE",
        "CONTACT",
        c.getId(),
        c.getName(),
        before,
        contactFields(c));
    return view(c);
  }

  public List<CategoryView> categories() {
    return categories.findByCompanyIdOrderByNameAsc(current.companyId()).stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  public CategoryView saveCategory(UUID id, CategoryInput input) {
    var c =
        id == null
            ? new FinancialCategory()
            : categories.lock(id, current.companyId()).orElseThrow(this::missing);
    if (id != null
        && c.getKind() != input.kind()
        && (entries.existsByCategoryIdAndCompanyId(id, current.companyId())
            || budgets.existsByCategoryIdAndCompanyId(id, current.companyId())))
      throw invalid("Uma categoria com lançamentos ou orçamentos vinculados deve manter seu tipo.");
    var before = id == null ? Map.<String, String>of() : categoryFields(c);
    c.setCompanyId(current.companyId());
    c.setName(input.name().strip());
    c.setNameKey(c.getName().toLowerCase(Locale.ROOT));
    c.setKind(input.kind());
    if (input.active() != null) c.setActive(input.active());
    categories.save(c);
    audit.change(
        id == null ? "CREATE" : "UPDATE",
        "CATEGORY",
        c.getId(),
        c.getName(),
        before,
        categoryFields(c));
    return view(c);
  }

  /** Lock the same row used by updates to serialize archive/type changes with new links. */
  @Transactional
  public String categoryName(UUID id, UUID retainedId, FinancialEntry.Type type) {
    var c = categories.lock(id, current.companyId()).orElseThrow(this::missing);
    if (!c.isActive() && !id.equals(retainedId)) throw invalid("Escolha uma categoria ativa.");
    if (c.getKind() != FinancialCategory.Kind.BOTH && !c.getKind().name().equals(type.name()))
      throw invalid("A categoria não corresponde ao tipo do lançamento.");
    return c.getName();
  }

  @Transactional
  public String contactName(UUID id, UUID retainedId, FinancialEntry.Type type) {
    var c = contacts.lock(id, current.companyId()).orElseThrow(this::missing);
    if (!c.isActive() && !id.equals(retainedId)) throw invalid("Escolha um contato ativo.");
    var expected =
        type == FinancialEntry.Type.INCOME ? Contact.Kind.CUSTOMER : Contact.Kind.SUPPLIER;
    if (c.getKind() != Contact.Kind.BOTH && c.getKind() != expected)
      throw invalid("Receitas devem usar clientes; despesas devem usar fornecedores.");
    return c.getName();
  }
}
