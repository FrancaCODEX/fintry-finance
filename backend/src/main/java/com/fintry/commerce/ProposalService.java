package com.fintry.commerce;

import static com.fintry.audit.AuditService.fields;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.commerce.CommerceService.LineInput;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.directory.DirectoryService;
import com.fintry.finance.FinancialEntry;
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
public class ProposalService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final ProductRepository products;
  private final ProposalRepository proposals;
  private final ProposalItemRepository items;
  private final SaleRepository sales;
  private final DirectoryService directory;
  private final CommerceService commerce;
  private final AuditService audit;
  private final Clock clock;

  public record Input(
      @NotNull UUID requestId,
      @NotBlank @Size(max = 120) String title,
      UUID contactId,
      @Size(max = 500) String notes,
      @NotNull LocalDate validUntil,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid LineInput> items) {}

  public record Edit(@NotNull Long version, @NotNull @Valid Input content) {}

  public record Transition(@NotNull Long version, @NotNull Proposal.Status status) {}

  public record Approval(
      @NotNull Long version, @NotNull @Valid CommerceService.ConfirmInput payment) {}

  public record View(
      UUID id,
      long number,
      long version,
      UUID requestId,
      String title,
      Proposal.Status status,
      UUID contactId,
      String contactName,
      String notes,
      LocalDate validUntil,
      boolean expired,
      BigDecimal total,
      UUID saleId,
      Long saleNumber,
      Sale.Status saleStatus,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      List<CommerceService.ItemView> items) {}

  public record Page(
      List<View> content,
      long totalElements,
      int totalPages,
      int number,
      long drafts,
      long negotiating,
      long approved) {}

  public record Created(View proposal, boolean created) {}

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta ou item não encontrado.");
  }

  private UUID companyLock() {
    var id = current.companyId();
    companies.lock(id).orElseThrow(this::missing);
    return id;
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
  }

  private void version(Proposal p, Long v) {
    if (v == null || p.getVersion() != v)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A proposta foi alterada. Atualize os detalhes antes de continuar.");
  }

  private String hash(Input input) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(
                      tools.jackson.databind.json.JsonMapper.builder()
                          .build()
                          .writeValueAsBytes(input)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private List<CommerceService.ItemView> lines(UUID id) {
    return items.findByProposalIdOrderBySkuAsc(id).stream()
        .map(
            i ->
                new CommerceService.ItemView(
                    i.getProductId(),
                    i.getSku(),
                    i.getProductName(),
                    i.getUnit(),
                    i.getQuantity(),
                    i.getUnitPrice(),
                    i.getTotal(),
                    i.getKind()))
        .toList();
  }

  private View view(Proposal p, boolean details) {
    var sale =
        p.getSaleId() == null
            ? null
            : sales
                .findByIdAndCompanyId(p.getSaleId(), p.getCompanyId())
                .orElseThrow(this::missing);
    return new View(
        p.getId(),
        p.getNumber(),
        p.getVersion(),
        p.getRequestId(),
        p.getTitle(),
        p.getStatus(),
        p.getContactId(),
        p.getContactName(),
        p.getNotes(),
        p.getValidUntil(),
        p.getValidUntil().isBefore(today())
            && (p.getStatus() == Proposal.Status.DRAFT || p.getStatus() == Proposal.Status.OPEN),
        p.getTotal(),
        p.getSaleId(),
        sale == null ? null : sale.getNumber(),
        sale == null ? null : sale.getStatus(),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        details ? lines(p.getId()) : List.of());
  }

  public View detail(UUID id) {
    return view(
        proposals.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing), true);
  }

  public Page list(Proposal.Status status, String search, boolean expired, int page, int size) {
    if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 120)
      throw invalid("Confira a busca e a paginação.");
    var company = current.companyId();
    String term =
        search == null || search.isBlank()
            ? null
            : "%"
                + search
                    .strip()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                + "%";
    Specification<Proposal> filter =
        (r, q, cb) -> {
          var ps = new ArrayList<jakarta.persistence.criteria.Predicate>();
          ps.add(cb.equal(r.get("companyId"), company));
          if (status != null) ps.add(cb.equal(r.get("status"), status));
          if (expired) {
            ps.add(r.get("status").in(Proposal.Status.DRAFT, Proposal.Status.OPEN));
            ps.add(cb.lessThan(r.get("validUntil"), today()));
          }
          if (term != null)
            ps.add(
                cb.or(
                    cb.like(cb.lower(r.get("title")), term, '!'),
                    cb.like(cb.lower(r.get("contactName")), term, '!')));
          return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result =
        proposals.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Order.desc("number"))));
    var counts = new EnumMap<Proposal.Status, Long>(Proposal.Status.class);
    for (var row : proposals.summary(company))
      counts.put((Proposal.Status) row[0], ((Number) row[1]).longValue());
    return new Page(
        result.stream().map(p -> view(p, false)).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page,
        counts.getOrDefault(Proposal.Status.DRAFT, 0L),
        counts.getOrDefault(Proposal.Status.OPEN, 0L),
        counts.getOrDefault(Proposal.Status.APPROVED, 0L));
  }

  private String lineSnapshot(UUID id) {
    return lines(id).stream()
        .map(
            i ->
                i.sku()
                    + " · "
                    + i.productName()
                    + " | "
                    + i.quantity().stripTrailingZeros().toPlainString()
                    + " "
                    + i.unit()
                    + " × R$ "
                    + i.unitPrice().toPlainString()
                    + " = R$ "
                    + i.total().toPlainString())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private Map<String, String> snapshot(Proposal p) {
    return fields(
        "Título",
        p.getTitle(),
        "Situação",
        p.getStatus(),
        "Cliente",
        p.getContactName(),
        "Validade",
        p.getValidUntil(),
        "Total",
        p.getTotal(),
        "Observações",
        p.getNotes(),
        "Venda",
        p.getSaleId());
  }

  private void audit(String action, Proposal p, Map<String, String> before) {
    audit.change(
        action,
        "PROPOSAL",
        p.getId(),
        "Proposta #" + p.getNumber() + " · " + p.getTitle(),
        before,
        snapshot(p));
  }

  private List<ProposalItem> prepare(Proposal p, Input input) {
    if (input.validUntil().isBefore(today()) || input.validUntil().getYear() > 2100)
      throw invalid("Escolha uma validade de hoje até 2100.");
    p.setTitle(input.title().strip());
    p.setNotes(input.notes() == null ? null : input.notes().strip());
    p.setContactId(input.contactId());
    p.setContactName(
        input.contactId() == null
            ? null
            : directory.contactName(input.contactId(), null, FinancialEntry.Type.INCOME));
    p.setValidUntil(input.validUntil());
    var rows = new ArrayList<ProposalItem>();
    var seen = new HashSet<UUID>();
    var total = BigDecimal.ZERO;
    for (var l : input.items()) {
      if (!seen.add(l.productId()))
        throw invalid("Agrupe as quantidades do mesmo item em uma linha.");
      var product =
          products.findByIdAndCompanyId(l.productId(), p.getCompanyId()).orElseThrow(this::missing);
      if (!product.isActive()) throw invalid("Escolha apenas produtos e serviços ativos.");
      var i = new ProposalItem();
      i.setProductId(product.getId());
      i.setSku(product.getSku());
      i.setProductName(product.getName());
      i.setUnit(product.getUnit());
      i.setKind(product.getKind());
      i.setQuantity(l.quantity());
      i.setUnitPrice(l.unitPrice());
      i.setTotal(l.quantity().multiply(l.unitPrice()).setScale(2, RoundingMode.HALF_UP));
      rows.add(i);
      total = total.add(i.getTotal());
      if (total.compareTo(new BigDecimal("999999999999.99")) > 0)
        throw invalid("O total ultrapassa o limite financeiro permitido.");
    }
    if (total.signum() <= 0) throw invalid("O total deve ser pelo menos R$ 0,01.");
    p.setTotal(total);
    return rows;
  }

  private void saveItems(Proposal p, List<ProposalItem> rows) {
    for (var i : rows) {
      i.setProposalId(p.getId());
      items.save(i);
    }
    items.flush();
  }

  @Transactional
  public Created create(Input input) {
    var company = companyLock();
    var fingerprint = hash(input);
    var prior = proposals.findByCompanyIdAndRequestId(company, input.requestId());
    if (prior.isPresent()) {
      if (!prior.get().getFingerprint().equals(fingerprint))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Solicitação já usada com outros dados. Abra um novo formulário.");
      return new Created(view(prior.get(), true), false);
    }
    var p = new Proposal();
    p.setCompanyId(company);
    p.setRequestId(input.requestId());
    p.setFingerprint(fingerprint);
    p.setNumber(proposals.nextNumber(company));
    var rows = prepare(p, input);
    proposals.saveAndFlush(p);
    saveItems(p, rows);
    audit("PROPOSAL_CREATE", p, Map.of());
    return new Created(view(p, true), true);
  }

  @Transactional
  public View edit(UUID id, Edit edit) {
    var company = companyLock();
    var p = proposals.lock(id, company).orElseThrow(this::missing);
    version(p, edit.version());
    if (p.getStatus() != Proposal.Status.DRAFT)
      throw invalid(
          "Somente rascunhos podem ser editados. Volte a proposta em negociação para rascunho.");
    if (!p.getRequestId().equals(edit.content().requestId()))
      throw invalid("O identificador da proposta é fixo.");
    var before = snapshot(p);
    var oldLines = lineSnapshot(id);
    var rows = prepare(p, edit.content());
    items.deleteAll(items.findByProposalIdOrderBySkuAsc(id));
    items.flush();
    saveItems(p, rows);
    p.setVersion(p.getVersion() + 1);
    proposals.saveAndFlush(p);
    var after = new LinkedHashMap<>(snapshot(p));
    var previous = new LinkedHashMap<>(before);
    // Include item snapshots so a quantity/price-only edit is visible in history.
    previous.put("Itens", oldLines);
    after.put("Itens", lineSnapshot(id));
    audit.change(
        "PROPOSAL_UPDATE",
        "PROPOSAL",
        p.getId(),
        "Proposta #" + p.getNumber() + " · " + p.getTitle(),
        previous,
        after);
    return view(p, true);
  }

  @Transactional
  public View transition(UUID id, Transition input) {
    var company = companyLock();
    var p = proposals.lock(id, company).orElseThrow(this::missing);
    if (p.getStatus() == input.status()) return view(p, true);
    version(p, input.version());
    var from = p.getStatus();
    var to = input.status();
    boolean allowed =
        from == Proposal.Status.DRAFT
                && (to == Proposal.Status.OPEN || to == Proposal.Status.CANCELLED)
            || from == Proposal.Status.OPEN
                && (to == Proposal.Status.DRAFT
                    || to == Proposal.Status.REJECTED
                    || to == Proposal.Status.CANCELLED);
    if (!allowed)
      throw invalid(
          "Transição indisponível. Propostas aprovadas são preservadas; corrija a venda vinculada"
              + " se necessário.");
    if (to == Proposal.Status.OPEN && p.getValidUntil().isBefore(today()))
      throw invalid("A validade expirou. Atualize o rascunho antes de negociar.");
    var before = snapshot(p);
    p.setStatus(to);
    p.setVersion(p.getVersion() + 1);
    proposals.saveAndFlush(p);
    audit("PROPOSAL_STATUS", p, before);
    return view(p, true);
  }

  @Transactional
  public View approve(UUID id, Approval input) {
    var company = companyLock();
    var p = proposals.lock(id, company).orElseThrow(this::missing);
    if (p.getStatus() == Proposal.Status.APPROVED) return view(p, true);
    version(p, input.version());
    if (p.getStatus() != Proposal.Status.OPEN)
      throw invalid("Coloque a proposta em negociação antes de aprovar.");
    if (p.getValidUntil().isBefore(today()))
      throw invalid("A proposta expirou. Volte a rascunho e atualize a validade.");
    var agreed = lines(id);
    var before = snapshot(p);
    var draft =
        commerce.draftFromProposal(
            new CommerceService.DraftInput(
                UUID.nameUUIDFromBytes(
                    ("fintry-proposal:" + p.getId())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                p.getContactId(),
                "Proposta #" + p.getNumber() + " · " + p.getTitle(),
                agreed.stream()
                    .map(
                        i ->
                            new CommerceService.LineInput(
                                i.productId(), i.quantity(), i.unitPrice()))
                    .toList()),
            agreed);
    var sale = commerce.confirm(draft.sale().id(), input.payment());
    p.setSaleId(sale.id());
    p.setStatus(Proposal.Status.APPROVED);
    p.setVersion(p.getVersion() + 1);
    proposals.saveAndFlush(p);
    audit("PROPOSAL_APPROVE", p, before);
    return view(p, true);
  }
}
