package com.fintry.finance;

import static com.fintry.audit.AuditService.fields;

import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
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
public class ReconciliationService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final AccountRepository accounts;
  private final EntryRepository entries;
  private final StatementSourceRepository sources;
  private final StatementLineRepository lines;
  private final ReconciliationBatchRepository batches;
  private final FinanceService finance;
  private final AuditService audit;

  public record Input(@NotNull UUID accountId, @NotBlank @Size(max = 1048576) String ofx) {}

  public record ImportInput(
      @NotNull UUID accountId,
      @NotBlank @Size(max = 1048576) String ofx,
      @NotEmpty @Size(max = 500) List<@NotNull @Min(1) Integer> selectedRows) {}

  public record PreviewRow(
      int number,
      String fitId,
      String description,
      LocalDate date,
      String amount,
      String state,
      String message) {}

  public record Preview(
      String accountName,
      String bank,
      String accountHint,
      int ready,
      int duplicates,
      int invalid,
      List<PreviewRow> rows) {}

  public record ImportResult(int imported, int skipped) {}

  public enum Action {
    LINK,
    CREATE,
    IGNORE,
    REOPEN
  }

  public record Resolution(
      @NotNull @Min(0) Long revision,
      @NotNull Action action,
      UUID entryId,
      @Size(max = 80) String category,
      UUID categoryId,
      @Size(max = 255) String reason) {}

  public record Candidate(FinanceService.EntryView entry, String explanation) {}

  public record LineView(
      UUID id,
      String fitId,
      String description,
      LocalDate date,
      String amount,
      StatementLine.Status status,
      UUID entryId,
      String reason,
      UUID batchId,
      int batchMovements,
      long revision,
      FinanceService.EntryView entry,
      List<Candidate> suggestions) {}

  public record Result(
      List<LineView> content,
      long totalElements,
      int totalPages,
      int number,
      long open,
      long linked,
      long ignored) {}

  public record GroupInput(
      @NotNull UUID accountId,
      @NotEmpty @Size(min = 2, max = 50) List<@NotNull UUID> movementIds,
      @NotNull UUID entryId,
      @NotBlank @Size(max = 255) String reason) {}

  public record GroupCandidatesInput(
      @NotNull UUID accountId, @NotEmpty @Size(min = 2, max = 50) List<@NotNull UUID> movementIds) {}

  public record BatchView(
      UUID id, UUID entryId, BigDecimal amount, LocalDate paymentDate, String reason, int movements) {}

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado.");
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private FinancialAccount account(UUID id) {
    return accounts.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private StatementLine line(UUID id) {
    return lines.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private void lockCompany() {
    companies.lock(current.companyId()).orElseThrow(this::missing);
  }

  private StatementSource source(UUID account, OfxParser.Statement statement) {
    var source =
        sources
            .findByCompanyIdAndSourceHash(current.companyId(), statement.sourceHash())
            .orElse(null);
    if (source != null && !source.getAccountId().equals(account))
      throw conflict(
          "Esta conta bancária já foi vinculada a outra conta do Fintry. Use a conta de destino"
              + " original.");
    return source;
  }

  private List<PreviewRow> inspect(OfxParser.Statement statement, StatementSource source) {
    var seen = new HashMap<String, String>();
    var rows = new ArrayList<PreviewRow>();
    for (var r : statement.rows()) {
      String state = "READY", message = "Será adicionado à revisão, sem alterar o saldo.";
      if (r.error() != null) {
        state = "INVALID";
        message = r.error();
      } else {
        var fingerprint = r.fingerprint();
        var prior = seen.putIfAbsent(r.fitId(), fingerprint);
        var existing =
            source == null
                ? null
                : lines.findBySourceIdAndFitId(source.getId(), r.fitId()).orElse(null);
        if (prior != null || existing != null) {
          if (prior != null && !prior.equals(fingerprint)
              || existing != null && !existing.getFingerprint().equals(fingerprint)) {
            state = "INVALID";
            message =
                "Identificador bancário repetido com dados diferentes. Confira com o banco; o"
                    + " registro anterior será preservado.";
          } else {
            state = "DUPLICATE";
            message =
                existing == null
                    ? "Identificador já aparece antes neste arquivo."
                    : "Movimento já importado; acompanhe sua situação na conciliação.";
          }
        }
      }
      rows.add(
          new PreviewRow(
              r.number(),
              r.fitId(),
              r.description(),
              r.date(),
              r.amount() == null ? "" : r.amount().toPlainString(),
              state,
              message));
    }
    return rows;
  }

  public Preview preview(Input input) {
    var a = account(input.accountId());
    var statement = OfxParser.parse(input.ofx());
    var rows = inspect(statement, source(a.getId(), statement));
    return new Preview(
        a.getName(),
        statement.bank(),
        statement.accountHint(),
        (int) rows.stream().filter(r -> r.state().equals("READY")).count(),
        (int) rows.stream().filter(r -> r.state().equals("DUPLICATE")).count(),
        (int) rows.stream().filter(r -> r.state().equals("INVALID")).count(),
        rows);
  }

  @Transactional
  public ImportResult importStatement(ImportInput input) {
    lockCompany();
    var a = account(input.accountId());
    var statement = OfxParser.parse(input.ofx());
    var source = source(a.getId(), statement);
    var rows = inspect(statement, source);
    var selection = new HashSet<>(input.selectedRows());
    if (selection.size() != input.selectedRows().size()
        || selection.stream().anyMatch(n -> n < 1 || n > rows.size()))
      throw OfxParser.invalid("Seleção de movimentos inválida.");
    if (rows.stream().anyMatch(r -> selection.contains(r.number()) && r.state().equals("INVALID")))
      throw OfxParser.invalid("A seleção contém movimentos inválidos. Gere outra prévia.");
    int imported = 0, skipped = 0;
    for (var r : rows)
      if (selection.contains(r.number())) {
        if (r.state().equals("DUPLICATE")) {
          skipped++;
          continue;
        }
        if (source == null) {
          source = new StatementSource();
          source.setCompanyId(current.companyId());
          source.setAccountId(a.getId());
          source.setSourceHash(statement.sourceHash());
          source.setBank(statement.bank());
          source.setAccountHint(statement.accountHint());
          sources.save(source);
        }
        var parsed = statement.rows().get(r.number() - 1);
        var line = new StatementLine();
        line.setCompanyId(current.companyId());
        line.setAccountId(a.getId());
        line.setSourceId(source.getId());
        line.setFitId(r.fitId());
        line.setFingerprint(parsed.fingerprint());
        line.setDescription(r.description());
        line.setPostedDate(r.date());
        line.setAmount(parsed.amount());
        line.setStatus(StatementLine.Status.OPEN);
        lines.save(line);
        audit.change(
            "STATEMENT_IMPORT",
            "RECONCILIATION",
            line.getId(),
            line.getDescription(),
            Map.of(),
            snapshot(line));
        imported++;
      }
    return new ImportResult(imported, skipped);
  }

  private Map<String, String> snapshot(StatementLine l) {
    return fields(
        "Identificador bancário",
        l.getFitId(),
        "Data do extrato",
        l.getPostedDate(),
        "Valor do extrato",
        l.getAmount(),
        "Situação",
        l.getStatus(),
        "Lançamento vinculado",
        l.getEntryId(),
        "Grupo de conciliação",
        l.getReconciliationBatchId(),
        "Motivo",
        l.getReason());
  }

  private FinancialEntry.Type type(StatementLine l) {
    return l.getAmount().signum() > 0 ? FinancialEntry.Type.INCOME : FinancialEntry.Type.EXPENSE;
  }

  private Specification<FinancialEntry> candidatesFilter(
      StatementLine line, boolean nearby, String search) {
    return (root, q, cb) -> {
      var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
      p.add(cb.equal(root.get("companyId"), current.companyId()));
      p.add(cb.equal(root.get("accountId"), line.getAccountId()));
      p.add(cb.equal(root.get("type"), type(line)));
      p.add(cb.equal(root.get("amount"), line.getAmount().abs()));
      p.add(cb.notEqual(root.get("status"), FinancialEntry.Status.CANCELLED));
      var sub = q.subquery(UUID.class);
      var linked = sub.from(StatementLine.class);
      sub.select(linked.get("entryId")).where(cb.equal(linked.get("entryId"), root.get("id")));
      p.add(cb.not(cb.exists(sub)));
      // Existing payments match exactly; pending due dates are only a suggestion heuristic.
      p.add(
          cb.or(
              cb.equal(root.get("status"), FinancialEntry.Status.PENDING),
              cb.equal(root.get("paymentDate"), line.getPostedDate())));
      if (nearby)
        p.add(
            cb.or(
                cb.equal(root.get("status"), FinancialEntry.Status.PAID),
                cb.between(
                    root.<LocalDate>get("dueDate"),
                    line.getPostedDate().minusDays(7),
                    line.getPostedDate().plusDays(7))));
      if (search != null && !search.isBlank())
        p.add(
            cb.like(
                cb.lower(root.get("description")),
                "%"
                    + search
                        .strip()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                    + "%",
                '!'));
      return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  private static String normalized(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
  }

  private List<Candidate> candidates(StatementLine l, boolean nearby, String search) {
    if (l.getStatus() != StatementLine.Status.OPEN) return List.of();
    return entries
        .findAll(
            candidatesFilter(l, nearby, search),
            PageRequest.of(
                0, nearby ? 10 : 100, Sort.by(Sort.Order.desc("dueDate"), Sort.Order.asc("id"))))
        .getContent()
        .stream()
        .filter(
            e ->
                e.getStatus() == FinancialEntry.Status.PENDING
                    || l.getPostedDate().equals(e.getPaymentDate()))
        .sorted(
            Comparator.<FinancialEntry>comparingInt(
                    e ->
                        normalized(e.getDescription()).equals(normalized(l.getDescription()))
                            ? 0
                            : 1)
                .thenComparing(
                    e ->
                        Math.abs(
                            java.time.temporal.ChronoUnit.DAYS.between(
                                l.getPostedDate(),
                                e.getStatus() == FinancialEntry.Status.PAID
                                    ? e.getPaymentDate()
                                    : e.getDueDate())))
                .thenComparing(FinancialEntry::getId))
        .limit(nearby ? 3 : 100)
        .map(
            e ->
                new Candidate(
                    finance.view(e),
                    e.getStatus() == FinancialEntry.Status.PENDING
                        ? "Mesmo valor e tipo. Ao vincular, será realizada a baixa na data do"
                            + " extrato."
                        : "Mesmo valor, tipo e data de pagamento. Vincular não altera o saldo."))
        .toList();
  }

  private LineView view(StatementLine l) {
    var entry =
        l.getEntryId() == null
            ? null
            : entries
                .findByIdAndCompanyId(l.getEntryId(), current.companyId())
                .map(finance::view)
                .orElse(null);
    return new LineView(
        l.getId(),
        l.getFitId(),
        l.getDescription(),
        l.getPostedDate(),
        l.getAmount().toPlainString(),
        l.getStatus(),
        l.getEntryId(),
        l.getReason(),
        l.getReconciliationBatchId(),
        l.getReconciliationBatchId() == null ? 0 : (int) lines.countByReconciliationBatchId(l.getReconciliationBatchId()),
        l.getRevision(),
        entry,
        candidates(l, true, null));
  }

  public List<Candidate> candidates(UUID id, String search) {
    if (search != null && search.length() > 120)
      throw OfxParser.invalid("Use até 120 caracteres na busca.");
    return candidates(line(id), false, search);
  }

  public List<FinanceService.EntryView> groupCandidates(GroupCandidatesInput input) {
    account(input.accountId());
    var ids = new LinkedHashSet<>(input.movementIds());
    if (ids.size() != input.movementIds().size()) throw OfxParser.invalid("A seleção contém movimentos repetidos.");
    var selected = ids.stream().map(this::line).toList();
    if (selected.stream().anyMatch(m -> !m.getAccountId().equals(input.accountId()) || m.getStatus() != StatementLine.Status.OPEN))
      throw conflict("Atualize a conciliação: há movimentos que não estão mais em revisão.");
    int direction = selected.get(0).getAmount().signum();
    if (selected.stream().anyMatch(m -> m.getAmount().signum() != direction)) return List.of();
    var total = selected.stream().map(StatementLine::getAmount).map(java.math.BigDecimal::abs)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    var candidates = entries.findAll(
        (root, q, cb) -> cb.and(
            cb.equal(root.get("companyId"), current.companyId()),
            cb.equal(root.get("accountId"), input.accountId()),
            cb.equal(root.get("type"), direction > 0 ? FinancialEntry.Type.INCOME : FinancialEntry.Type.EXPENSE),
            cb.or(cb.equal(root.get("status"), FinancialEntry.Status.PENDING), cb.equal(root.get("status"), FinancialEntry.Status.PARTIAL))),
        PageRequest.of(0, 100, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id"))));
    return candidates.getContent().stream()
        .filter(e -> e.getAmount().subtract(e.getPaidAmount()).compareTo(total) >= 0)
        .limit(50)
        .map(finance::view)
        .toList();
  }

  @Transactional
  public BatchView group(GroupInput input) {
    lockCompany();
    account(input.accountId());
    var ids = new LinkedHashSet<>(input.movementIds());
    if (ids.size() != input.movementIds().size())
      throw OfxParser.invalid("A seleção contém movimentos repetidos.");
    var selected = new ArrayList<StatementLine>();
    for (var id : ids) {
      var movement = line(id);
      if (!movement.getAccountId().equals(input.accountId())
          || movement.getStatus() != StatementLine.Status.OPEN)
        throw conflict("Um movimento mudou ou não está mais disponível. Atualize a conciliação.");
      selected.add(movement);
    }
    int direction = selected.get(0).getAmount().signum();
    if (selected.stream().anyMatch(m -> m.getAmount().signum() != direction))
      throw OfxParser.invalid("Agrupe entradas com entradas ou saídas com saídas.");
    var e = entries.lock(input.entryId(), current.companyId()).orElseThrow(this::missing);
    if (!e.getAccountId().equals(input.accountId())
        || e.getType() != (direction > 0 ? FinancialEntry.Type.INCOME : FinancialEntry.Type.EXPENSE)
        || e.getStatus() == FinancialEntry.Status.CANCELLED)
      throw conflict("O lançamento não corresponde à conta e ao tipo dos movimentos.");
    finance.requireBillingEditable(e.getId());
    var total = selected.stream().map(StatementLine::getAmount).map(java.math.BigDecimal::abs)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    var remaining = e.getAmount().subtract(e.getPaidAmount());
    if (total.signum() <= 0 || total.compareTo(remaining) > 0)
      throw conflict("O total selecionado excede o saldo restante do lançamento.");
    var paymentDate = selected.stream().map(StatementLine::getPostedDate).max(LocalDate::compareTo).orElseThrow();
    selected.sort(
        Comparator.comparing(StatementLine::getPostedDate).thenComparing(StatementLine::getId));
    var batch = new ReconciliationBatch();
    batch.setCompanyId(current.companyId());
    batch.setAccountId(input.accountId());
    batch.setEntryId(e.getId());
    batch.setAmount(total);
    batch.setPaymentDate(paymentDate);
    batch.setReason(input.reason().strip());
    batches.save(batch);
    for (var movement : selected) {
      var before = snapshot(movement);
      finance.payFromReconciliation(e.getId(), movement.getPostedDate(),
          movement.getAmount().abs(), batch.getId());
      movement.setEntryId(e.getId());
      movement.setReconciliationBatchId(batch.getId());
      movement.setStatus(StatementLine.Status.LINKED);
      movement.setReason(input.reason().strip());
      movement.setRevision(movement.getRevision() + 1);
      lines.save(movement);
      audit.change("RECONCILE_GROUP", "RECONCILIATION", movement.getId(), movement.getDescription(), before, snapshot(movement));
    }
    audit.change("RECONCILE_GROUP", "RECONCILIATION_BATCH", batch.getId(), input.reason().strip(),
        Map.of(), fields("Lançamento", e.getDescription(), "Valor", total, "Data", paymentDate,
            "Movimentos", selected.size(), "Motivo", input.reason().strip()));
    return new BatchView(batch.getId(), e.getId(), total, paymentDate, batch.getReason(), selected.size());
  }

  public Result list(UUID accountId, StatementLine.Status status, int page, int size) {
    account(accountId);
    if (page < 0 || size < 1 || size > 50) throw OfxParser.invalid("Paginação inválida.");
    Specification<StatementLine> spec =
        (root, q, cb) ->
            cb.and(
                cb.equal(root.get("companyId"), current.companyId()),
                cb.equal(root.get("accountId"), accountId),
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
    var result =
        lines.findAll(
            spec,
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Sort.Order.desc("postedDate"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id"))));
    return new Result(
        result.stream().map(this::view).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page,
        lines.countByCompanyIdAndAccountIdAndStatus(
            current.companyId(), accountId, StatementLine.Status.OPEN),
        lines.countByCompanyIdAndAccountIdAndStatus(
            current.companyId(), accountId, StatementLine.Status.LINKED),
        lines.countByCompanyIdAndAccountIdAndStatus(
            current.companyId(), accountId, StatementLine.Status.IGNORED));
  }

  @Transactional
  public LineView resolve(UUID id, Resolution input) {
    lockCompany();
    var l = line(id);
    if (l.getRevision() != input.revision())
      throw conflict("Este movimento foi alterado. Atualize a conciliação antes de continuar.");
    var before = snapshot(l);
    if (input.action() == Action.REOPEN) {
      if (l.getStatus() == StatementLine.Status.OPEN)
        throw conflict("Este movimento já está em revisão.");
      if (input.reason() == null || input.reason().isBlank())
        throw OfxParser.invalid("Informe o motivo da reabertura.");
      l.setEntryId(null);
      l.setStatus(StatementLine.Status.OPEN);
      l.setReason(input.reason().strip());
    } else {
      if (l.getStatus() != StatementLine.Status.OPEN)
        throw conflict("Reabra o movimento antes de alterar a decisão.");
      if (input.action() == Action.IGNORE) {
        if (input.reason() == null || input.reason().isBlank())
          throw OfxParser.invalid("Informe o motivo para ignorar.");
        l.setStatus(StatementLine.Status.IGNORED);
        l.setReason(input.reason().strip());
      } else {
        UUID entryId;
        if (input.action() == Action.CREATE) {
          // A likely existing movement must be reviewed rather than accidentally duplicated.
          if (!candidates(l, false, null).isEmpty()
              && (input.reason() == null || input.reason().isBlank()))
            throw conflict(
                "Há lançamentos com o mesmo valor e tipo nesta conta. Confira as correspondências"
                    + " antes de criar; se for um movimento distinto, informe o motivo e confirme a"
                    + " criação.");
          entryId = create(l, input);
        } else {
          if (input.entryId() == null)
            throw OfxParser.invalid("Escolha o lançamento para vincular.");
          var e = entries.lock(input.entryId(), current.companyId()).orElseThrow(this::missing);
          if (!e.getAccountId().equals(l.getAccountId())
              || e.getType() != type(l)
              || e.getAmount().compareTo(l.getAmount().abs()) != 0
              || e.getStatus() == FinancialEntry.Status.CANCELLED
              || lines.existsByEntryId(e.getId()))
            throw conflict(
                "O lançamento não está disponível ou não corresponde à conta, tipo e valor do"
                    + " extrato.");
          if (e.getStatus() == FinancialEntry.Status.PAID
              && !l.getPostedDate().equals(e.getPaymentDate()))
            throw conflict(
                "A data de pagamento difere do extrato. Corrija o lançamento antes de vincular.");
          finance.pay(e.getId(), new FinanceService.PaymentInput(l.getPostedDate()));
          entryId = e.getId();
        }
        l.setEntryId(entryId);
        l.setStatus(StatementLine.Status.LINKED);
        l.setReason(input.reason() == null ? null : input.reason().strip());
      }
    }
    l.setRevision(l.getRevision() + 1);
    lines.flush();
    audit.change(
        "RECONCILE_" + input.action(),
        "RECONCILIATION",
        l.getId(),
        l.getDescription(),
        before,
        snapshot(l));
    return view(l);
  }

  private UUID create(StatementLine l, Resolution input) {
    return finance
        .createEntry(
            new FinanceService.EntryInput(
                l.getAccountId(),
                l.getDescription(),
                input.category(),
                type(l),
                l.getAmount().abs(),
                l.getPostedDate(),
                true,
                l.getPostedDate(),
                input.categoryId(),
                null))
        .id();
  }
}
