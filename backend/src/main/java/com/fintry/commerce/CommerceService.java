package com.fintry.commerce;

import static com.fintry.audit.AuditService.fields;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.directory.DirectoryService;
import com.fintry.finance.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
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
public class CommerceService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final ProductRepository products;
  private final StockRepository stock;
  private final SaleRepository sales;
  private final SaleItemRepository items;
  private final FinanceService finance;
  private final EntryRepository entries;
  private final DirectoryService directory;
  private final AuditService audit;
  private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999.999"),
      MAX_MONEY = new BigDecimal("999999999999.99");

  public record ProductInput(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,40}") String sku,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Pattern(regexp = "UN|KG|L|M|CX") String unit,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal cost,
      @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal minimum,
      boolean active,
      @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal initialQuantity,
      Product.Kind kind) {
    public ProductInput(
        String sku,
        String name,
        String unit,
        BigDecimal price,
        BigDecimal cost,
        BigDecimal minimum,
        boolean active,
        BigDecimal initialQuantity) {
      this(sku, name, unit, price, cost, minimum, active, initialQuantity, null);
    }
  }

  public record MovementInput(
      @NotNull UUID requestId,
      @NotNull UUID productId,
      @NotNull StockMovement.Kind kind,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotBlank @Size(max = 255) String reason) {}

  public record LineInput(
      @NotNull UUID productId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice) {}

  public record DraftInput(
      @NotNull UUID requestId,
      UUID contactId,
      @Size(max = 500) String notes,
      @NotEmpty @Size(max = 100) List<@Valid LineInput> items) {}

  public record ConfirmInput(
      @NotNull UUID accountId, @NotNull LocalDate dueDate, boolean paid, LocalDate paymentDate) {}

  public record ProductView(
      UUID id,
      String sku,
      String name,
      String unit,
      BigDecimal price,
      BigDecimal cost,
      BigDecimal quantity,
      BigDecimal minimum,
      boolean active,
      boolean lowStock,
      Product.Kind kind) {}

  public record ProductPage(
      List<ProductView> content,
      long totalElements,
      int totalPages,
      int number,
      long activeCount,
      long lowStockCount,
      BigDecimal stockValue) {}

  public record MovementView(
      UUID id,
      UUID productId,
      UUID saleId,
      String sku,
      String productName,
      String unit,
      StockMovement.Kind kind,
      BigDecimal delta,
      BigDecimal balance,
      String reason,
      String actorName,
      Instant createdAt) {}

  public record MovementPage(
      List<MovementView> content, long totalElements, int totalPages, int number) {}

  public record ItemView(
      UUID productId,
      String sku,
      String productName,
      String unit,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal total,
      Product.Kind kind) {}

  public record SaleView(
      UUID id,
      long number,
      Sale.Status status,
      String contactName,
      String notes,
      BigDecimal total,
      UUID entryId,
      FinancialEntry.Status paymentStatus,
      LocalDateTime createdAt,
      Instant confirmedAt,
      Instant cancelledAt,
      List<ItemView> items) {}

  public record SalePage(List<SaleView> content, long totalElements, int totalPages, int number) {}

  public record Created(SaleView sale, boolean created) {}

  private ResponseStatusException invalid(String m) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto ou venda não encontrado.");
  }

  private UUID lockCompany() {
    var id = current.companyId();
    companies.lock(id).orElseThrow(this::missing);
    return id;
  }

  private Product product(UUID id) {
    return products.lock(id, current.companyId()).orElseThrow(this::missing);
  }

  private void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw invalid("Paginação inválida.");
  }

  private String escaped(String search) {
    if (search.length() > 120) throw invalid("Busca limitada a 120 caracteres.");
    return "%"
        + search
            .strip()
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        + "%";
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(
                      tools.jackson.databind.json.JsonMapper.builder()
                          .build()
                          .writeValueAsBytes(value)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String number(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }

  private ProductView view(Product p) {
    return new ProductView(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getUnit(),
        p.getPrice(),
        p.getCost(),
        p.getQuantity(),
        p.getMinimum(),
        p.isActive(),
        p.getKind() == Product.Kind.PRODUCT && p.getQuantity().compareTo(p.getMinimum()) <= 0,
        p.getKind());
  }

  private Map<String, String> snapshot(Product p) {
    return fields(
        "Tipo",
        p.getKind(),
        "SKU",
        p.getSku(),
        "Nome",
        p.getName(),
        "Unidade",
        p.getUnit(),
        "Preço",
        number(p.getPrice()),
        "Custo",
        number(p.getCost()),
        "Mínimo",
        number(p.getMinimum()),
        "Ativo",
        p.isActive());
  }

  public ProductPage products(String search, Boolean active, boolean low, int page, int size) {
    return products(search, active, low, page, size, null);
  }

  public ProductPage products(
      String search, Boolean active, boolean low, int page, int size, Product.Kind kind) {
    page(page, size);
    UUID company = current.companyId();
    String term = search == null ? null : escaped(search);
    Specification<Product> filter =
        (root, q, cb) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(cb.equal(root.get("companyId"), company));
          if (active != null) p.add(cb.equal(root.get("active"), active));
          if (kind != null) p.add(cb.equal(root.get("kind"), kind));
          if (low) {
            p.add(cb.equal(root.get("kind"), Product.Kind.PRODUCT));
            p.add(cb.lessThanOrEqualTo(root.get("quantity"), root.get("minimum")));
          }
          if (term != null)
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), term, '!'),
                    cb.like(cb.lower(root.get("sku")), term, '!')));
          return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result = products.findAll(filter, PageRequest.of(page, size, Sort.by("name", "id")));
    var totals = products.summary(company, kind).get(0);
    return new ProductPage(
        result.stream().map(this::view).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page,
        ((Number) totals[0]).longValue(),
        totals[1] == null ? 0 : ((Number) totals[1]).longValue(),
        ((BigDecimal) totals[2]).setScale(2, RoundingMode.HALF_UP));
  }

  @Transactional
  public ProductView save(UUID id, ProductInput input) {
    lockCompany();
    var p = id == null ? new Product() : product(id);
    var before = id == null ? Map.<String, String>of() : snapshot(p);
    var kind = input.kind() == null ? p.getKind() : input.kind();
    if (id != null && kind != p.getKind())
      throw invalid("O tipo é fixo para preservar o histórico. Cadastre outro item.");
    if (kind == Product.Kind.SERVICE
        && (!input.unit().equals("UN")
            || input.minimum().signum() != 0
            || (input.initialQuantity() != null && input.initialQuantity().signum() != 0)))
      throw invalid("Serviços usam UN e não possuem estoque inicial ou mínimo.");
    p.setKind(kind);
    if (id != null && !p.getUnit().equals(input.unit()))
      throw invalid("A unidade é fixa para preservar o histórico. Cadastre outro produto.");
    if (id != null && input.initialQuantity() != null)
      throw invalid("Para alterar o estoque, registre uma entrada ou saída.");
    String sku = input.sku().toUpperCase(Locale.ROOT);
    if (products.existsByCompanyIdAndSkuAndIdNot(
        current.companyId(), sku, id == null ? new UUID(0, 0) : id))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um produto com esse SKU.");
    p.setCompanyId(current.companyId());
    p.setSku(sku);
    p.setName(input.name().strip());
    p.setUnit(input.unit());
    p.setPrice(input.price());
    p.setCost(input.cost());
    p.setMinimum(input.minimum());
    p.setActive(input.active());
    if (id == null) p.setQuantity(BigDecimal.ZERO);
    products.saveAndFlush(p);
    if (id == null && input.initialQuantity() != null && input.initialQuantity().signum() > 0)
      move(
          p,
          StockMovement.Kind.INITIAL,
          input.initialQuantity(),
          "Estoque inicial",
          null,
          null,
          null);
    audit.change(
        id == null ? "CREATE" : "UPDATE", "PRODUCT", p.getId(), p.getName(), before, snapshot(p));
    return view(p);
  }

  private MovementView view(StockMovement m) {
    return new MovementView(
        m.getId(),
        m.getProductId(),
        m.getSaleId(),
        m.getSku(),
        m.getProductName(),
        m.getUnit(),
        m.getKind(),
        m.getDelta(),
        m.getBalance(),
        m.getReason(),
        m.getActorName(),
        m.getOccurredAt());
  }

  private StockMovement move(
      Product p,
      StockMovement.Kind kind,
      BigDecimal delta,
      String reason,
      UUID sale,
      UUID request,
      String fingerprint) {
    BigDecimal balance = p.getQuantity().add(delta);
    if (balance.signum() < 0)
      throw invalid(
          "Estoque insuficiente para "
              + p.getName()
              + ". Disponível: "
              + number(p.getQuantity())
              + " "
              + p.getUnit()
              + ".");
    if (balance.compareTo(MAX_QUANTITY) > 0)
      throw invalid("O saldo de estoque ultrapassa o limite permitido.");
    var m = new StockMovement();
    m.setOccurredAt(Instant.now());
    m.setCompanyId(p.getCompanyId());
    m.setProductId(p.getId());
    m.setProductName(p.getName());
    m.setSku(p.getSku());
    m.setUnit(p.getUnit());
    m.setKind(kind);
    m.setDelta(delta);
    m.setBalance(balance);
    m.setReason(reason.strip());
    m.setActorName(current.get().getName());
    m.setSaleId(sale);
    m.setRequestId(request);
    m.setFingerprint(fingerprint);
    p.setQuantity(balance);
    stock.saveAndFlush(m);
    audit.change(
        "STOCK_MOVE",
        "STOCK",
        p.getId(),
        p.getName(),
        fields("Saldo", number(balance.subtract(delta))),
        fields(
            "Saldo",
            number(balance),
            "Movimento",
            kind,
            "Quantidade",
            number(delta),
            "Motivo",
            reason));
    return m;
  }

  @Transactional
  public MovementView movement(MovementInput input) {
    UUID company = lockCompany();
    if (input.kind() != StockMovement.Kind.IN && input.kind() != StockMovement.Kind.OUT)
      throw invalid("Use Entrada ou Saída para ajustes manuais.");
    String hash = hash(input);
    var old = stock.findByCompanyIdAndRequestId(company, input.requestId());
    if (old.isPresent()) {
      if (!hash.equals(old.get().getFingerprint()))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Esta solicitação já foi usada com outros dados. Abra um novo formulário.");
      return view(old.get());
    }
    var p = product(input.productId());
    if (p.getKind() == Product.Kind.SERVICE)
      throw invalid("Serviços não possuem movimentações de estoque.");
    if (!p.isActive()) throw invalid("Reative o produto para movimentar seu estoque.");
    return view(
        move(
            p,
            input.kind(),
            input.kind() == StockMovement.Kind.IN ? input.quantity() : input.quantity().negate(),
            input.reason(),
            null,
            input.requestId(),
            hash));
  }

  public MovementPage movements(UUID productId, int page, int size) {
    page(page, size);
    UUID company = current.companyId();
    if (productId != null)
      if (products.findByIdAndCompanyId(productId, company).orElseThrow(this::missing).getKind()
          == Product.Kind.SERVICE) throw invalid("Serviços não possuem histórico de estoque.");
    Specification<StockMovement> spec =
        (root, q, cb) ->
            productId == null
                ? cb.equal(root.get("companyId"), company)
                : cb.and(
                    cb.equal(root.get("companyId"), company),
                    cb.equal(root.get("productId"), productId));
    var result =
        stock.findAll(
            spec,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
    return new MovementPage(
        result.stream().map(this::view).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page);
  }

  private SaleView view(Sale s, boolean details) {
    var payment =
        s.getEntryId() == null
            ? null
            : entries
                .findByIdAndCompanyId(s.getEntryId(), s.getCompanyId())
                .orElseThrow(this::missing)
                .getStatus();
    var lines =
        details
            ? items.findBySaleIdOrderBySkuAsc(s.getId()).stream()
                .map(
                    i ->
                        new ItemView(
                            i.getProductId(),
                            i.getSku(),
                            i.getProductName(),
                            i.getUnit(),
                            i.getQuantity(),
                            i.getUnitPrice(),
                            i.getTotal(),
                            i.getKind()))
                .toList()
            : List.<ItemView>of();
    return new SaleView(
        s.getId(),
        s.getNumber(),
        s.getStatus(),
        s.getContactName(),
        s.getNotes(),
        s.getTotal(),
        s.getEntryId(),
        payment,
        s.getCreatedAt(),
        s.getConfirmedAt(),
        s.getCancelledAt(),
        lines);
  }

  public SaleView detail(UUID id) {
    return view(
        sales.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing), true);
  }

  public SalePage sales(Sale.Status status, String search, int page, int size) {
    page(page, size);
    UUID company = current.companyId();
    String term = search == null || search.isBlank() ? null : escaped(search);
    Specification<Sale> filter =
        (root, q, cb) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(cb.equal(root.get("companyId"), company));
          if (status != null) p.add(cb.equal(root.get("status"), status));
          if (term != null)
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("contactName")), term, '!'),
                    cb.like(cb.lower(root.get("notes")), term, '!')));
          return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result =
        sales.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Order.desc("number"))));
    return new SalePage(
        result.stream().map(s -> view(s, false)).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page);
  }

  @Transactional
  public Created draft(DraftInput input) {
    return createDraft(input, Map.of());
  }

  @Transactional
  public Created draftFromProposal(DraftInput input, List<ItemView> snapshot) {
    var snapshots = new HashMap<UUID, ItemView>();
    snapshot.forEach(i -> snapshots.put(i.productId(), i));
    return createDraft(input, snapshots);
  }

  private Created createDraft(DraftInput input, Map<UUID, ItemView> snapshot) {
    UUID company = lockCompany();
    String fingerprint = hash(input);
    var prior = sales.findByCompanyIdAndRequestId(company, input.requestId());
    if (prior.isPresent()) {
      if (!fingerprint.equals(prior.get().getFingerprint()))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Solicitação já usada com outros dados. Abra um novo formulário.");
      return new Created(view(prior.get(), true), false);
    }
    var s = new Sale();
    s.setCompanyId(company);
    s.setNumber(sales.nextNumber(company));
    s.setRequestId(input.requestId());
    s.setFingerprint(fingerprint);
    s.setContactId(input.contactId());
    s.setContactName(
        input.contactId() == null
            ? null
            : directory.contactName(input.contactId(), null, FinancialEntry.Type.INCOME));
    s.setNotes(input.notes() == null ? null : input.notes().strip());
    var seen = new HashSet<UUID>();
    var lines = new ArrayList<SaleItem>();
    BigDecimal total = BigDecimal.ZERO;
    for (var line : input.items()) {
      if (!seen.add(line.productId()))
        throw invalid("Agrupe as quantidades do mesmo produto em uma linha.");
      var p = products.findByIdAndCompanyId(line.productId(), company).orElseThrow(this::missing);
      if (!p.isActive()) throw invalid("Escolha apenas produtos ativos.");
      var i = new SaleItem();
      var agreed = snapshot.get(p.getId());
      i.setProductId(p.getId());
      i.setKind(p.getKind());
      i.setSku(agreed == null ? p.getSku() : agreed.sku());
      i.setProductName(agreed == null ? p.getName() : agreed.productName());
      i.setUnit(agreed == null ? p.getUnit() : agreed.unit());
      i.setQuantity(line.quantity());
      i.setUnitPrice(line.unitPrice());
      i.setTotal(line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP));
      total = total.add(i.getTotal());
      if (total.compareTo(MAX_MONEY) > 0)
        throw invalid("O total ultrapassa o limite financeiro permitido.");
      lines.add(i);
    }
    if (total.signum() <= 0) throw invalid("O total da venda deve ser pelo menos R$ 0,01.");
    s.setTotal(total);
    sales.saveAndFlush(s);
    for (var i : lines) {
      i.setSaleId(s.getId());
      items.save(i);
    }
    audit.change(
        "SALE_CREATE",
        "SALE",
        s.getId(),
        "Venda #" + s.getNumber(),
        Map.of(),
        fields(
            "Situação",
            "DRAFT",
            "Total",
            s.getTotal(),
            "Cliente",
            s.getContactName(),
            "Itens",
            lines.size()));
    return new Created(view(s, true), true);
  }

  @Transactional
  public SaleView confirm(UUID id, ConfirmInput input) {
    UUID company = lockCompany();
    var s = sales.lock(id, company).orElseThrow(this::missing);
    if (s.getStatus() == Sale.Status.CONFIRMED) return view(s, true);
    if (s.getStatus() != Sale.Status.DRAFT)
      throw invalid("Uma venda cancelada não pode ser confirmada.");
    var lines = items.findBySaleIdOrderBySkuAsc(s.getId());
    for (var i : lines.stream().sorted(Comparator.comparing(SaleItem::getProductId)).toList()) {
      var p = product(i.getProductId());
      if (!p.isActive())
        throw invalid("O produto " + p.getName() + " foi inativado. Reative-o ou refaça a venda.");
      if (i.getKind() == Product.Kind.SERVICE) continue;
      move(
          p,
          StockMovement.Kind.SALE,
          i.getQuantity().negate(),
          "Venda #" + s.getNumber(),
          s.getId(),
          null,
          null);
    }
    var entry =
        finance.createEntry(
            new FinanceService.EntryInput(
                input.accountId(),
                "Venda #"
                    + s.getNumber()
                    + " · "
                    + (s.getContactName() == null ? "Consumidor final" : s.getContactName()),
                lines.stream().allMatch(i -> i.getKind() == Product.Kind.SERVICE)
                    ? "Vendas de serviços"
                    : lines.stream().allMatch(i -> i.getKind() == Product.Kind.PRODUCT)
                        ? "Vendas de produtos"
                        : "Vendas de produtos e serviços",
                FinancialEntry.Type.INCOME,
                s.getTotal(),
                input.dueDate(),
                input.paid(),
                input.paymentDate(),
                null,
                s.getContactId()));
    s.setEntryId(entry.id());
    entries.findById(entry.id()).orElseThrow().setSaleId(s.getId());
    s.setStatus(Sale.Status.CONFIRMED);
    s.setConfirmedAt(Instant.now());
    audit.change(
        "SALE_CONFIRM",
        "SALE",
        s.getId(),
        "Venda #" + s.getNumber(),
        fields("Situação", "DRAFT"),
        fields("Situação", "CONFIRMED", "Total", s.getTotal(), "Lançamento", s.getEntryId()));
    return view(s, true);
  }

  @Transactional
  public SaleView cancel(UUID id) {
    UUID company = lockCompany();
    var s = sales.lock(id, company).orElseThrow(this::missing);
    if (s.getStatus() == Sale.Status.CANCELLED) return view(s, true);
    var before = s.getStatus();
    if (before == Sale.Status.CONFIRMED) {
      var entry = entries.lock(s.getEntryId(), company).orElseThrow(this::missing);
      finance.requireUnreconciled(entry.getId());
      for (var i :
          items.findBySaleIdOrderBySkuAsc(s.getId()).stream()
              .sorted(Comparator.comparing(SaleItem::getProductId))
              .toList()) {
        if (i.getKind() == Product.Kind.SERVICE) continue;
        move(
            product(i.getProductId()),
            StockMovement.Kind.RETURN,
            i.getQuantity(),
            "Cancelamento da venda #" + s.getNumber(),
            s.getId(),
            null,
            null);
      }
      var old = finance.snapshot(entry);
      entry.setStatus(FinancialEntry.Status.CANCELLED);
      finance.recordChange("CANCEL", entry, old);
    }
    s.setStatus(Sale.Status.CANCELLED);
    s.setCancelledAt(Instant.now());
    audit.change(
        "SALE_CANCEL",
        "SALE",
        s.getId(),
        "Venda #" + s.getNumber(),
        fields("Situação", before),
        fields("Situação", "CANCELLED"));
    return view(s, true);
  }
}
