package com.fintry.finance;

import static com.fintry.finance.FinancialEntry.Status.*;
import static com.fintry.finance.FinancialEntry.Type.*;

import com.fintry.auth.CurrentUser;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceService {
  private final CurrentUser current;
  private final com.fintry.audit.AuditService audit;
  private final com.fintry.directory.DirectoryService directory;
  private final AccountRepository accounts;
  private final com.fintry.subscription.SubscriptionService subscriptions;
  private final EntryRepository entries;
  private final EntryPaymentRepository payments;
  private final StatementLineRepository statementLines;
  private final com.fintry.billing.BillingChargeRepository billingCharges;

  public record AccountInput(
      @NotBlank @Size(max = 100) String name,
      @NotNull @Digits(integer = 12, fraction = 2) BigDecimal initialBalance) {}

  public record EntryInput(
      @NotNull UUID accountId,
      @NotBlank @Size(max = 255) String description,
      @Size(max = 80) String category,
      @NotNull FinancialEntry.Type type,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotNull LocalDate dueDate,
      boolean paid,
      LocalDate paymentDate,
      UUID categoryId,
      UUID contactId) {
    public EntryInput(
        UUID accountId,
        String description,
        String category,
        FinancialEntry.Type type,
        BigDecimal amount,
        LocalDate dueDate,
        boolean paid,
        LocalDate paymentDate) {
      this(accountId, description, category, type, amount, dueDate, paid, paymentDate, null, null);
    }
  }

  public record PaymentInput(
      @NotNull LocalDate paymentDate,
      @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    public PaymentInput(LocalDate paymentDate) {
      this(paymentDate, null);
    }
  }

  public record TransferInput(
      @NotNull UUID sourceId,
      @NotNull UUID destinationId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 255) String description) {}

  public record AccountView(UUID id, String name, BigDecimal initialBalance, BigDecimal balance) {}

  public record EntryView(
      UUID id,
      UUID accountId,
      String description,
      String category,
      FinancialEntry.Type type,
      BigDecimal amount,
      BigDecimal receivedAmount,
      BigDecimal remainingAmount,
      LocalDate dueDate,
      LocalDate paymentDate,
      FinancialEntry.Status status,
      UUID transferId,
      UUID categoryId,
      UUID contactId,
      String contactName,
      UUID seriesId,
      Integer sequenceNumber,
      Integer seriesCount,
      boolean seriesPaused,
      FinancialSeries.Kind seriesKind,
      UUID saleId) {}

  public record EntryDetails(
      EntryView entry,
      String accountName,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      List<PaymentView> payments) {}

  public record PaymentView(UUID id, BigDecimal amount, LocalDate paymentDate) {}

  public EntryDetails details(UUID id) {
    var e = entries.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
    return new EntryDetails(
        view(e),
        account(e.getAccountId()).getName(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        payments.findByEntry_IdOrderByPaymentDateAscCreatedAtAscIdAsc(id).stream()
            .map(p -> new PaymentView(p.getId(), p.getAmount(), p.getPaymentDate()))
            .toList());
  }

  public record EntryPage(
      List<EntryView> content, long totalElements, int totalPages, int number) {}

  public record MonthView(String month, BigDecimal income, BigDecimal expense) {}

  public record Summary(
      BigDecimal balance,
      BigDecimal income,
      BigDecimal expense,
      BigDecimal result,
      BigDecimal pendingIncome,
      BigDecimal pendingExpense,
      List<MonthView> flow,
      BigDecimal previousIncome,
      BigDecimal previousExpense,
      BigDecimal previousResult,
      List<CategoryExpense> categories) {}

  public record CategoryExpense(String category, BigDecimal amount) {}

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado.");
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private FinancialAccount account(UUID id) {
    return accounts.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private void validPaymentDate(LocalDate date) {
    if (date == null || date.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo"))))
      throw invalid("A data de pagamento não pode estar no futuro.");
  }

  public EntryView view(FinancialEntry e) {
    return new EntryView(
        e.getId(),
        e.getAccountId(),
        e.getDescription(),
        e.getCategory(),
        e.getType(),
        e.getAmount(),
        e.getPaidAmount(),
        e.getAmount().subtract(e.getPaidAmount()),
        e.getDueDate(),
        e.getPaymentDate(),
        e.getStatus(),
        e.getTransferId(),
        e.getCategoryId(),
        e.getContactId(),
        e.getContactName(),
        e.getSeriesId(),
        e.getSequenceNumber(),
        e.getSeriesCount(),
        e.isSeriesPaused(),
        e.getSeriesKind(),
        e.getSaleId());
  }

  public List<AccountView> accounts() {
    var company = current.companyId();
    Map<UUID, BigDecimal> movements = new HashMap<>();
    for (Object[] row : payments.balances(company)) {
      var amount = (BigDecimal) row[2];
      if (row[1] == EXPENSE) amount = amount.negate();
      movements.merge((UUID) row[0], amount, BigDecimal::add);
    }
    return accounts.findByCompanyIdOrderByNameAsc(company).stream()
        .map(
            a ->
                new AccountView(
                    a.getId(),
                    a.getName(),
                    a.getInitialBalance(),
                    a.getInitialBalance().add(movements.getOrDefault(a.getId(), BigDecimal.ZERO))))
        .toList();
  }

  public Map<String, String> snapshot(FinancialEntry e) {
    return com.fintry.audit.AuditService.fields(
        "Descrição",
        e.getDescription(),
        "Conta",
        account(e.getAccountId()).getName(),
        "Categoria",
        e.getCategory(),
        "Contato",
        e.getContactName(),
        "Tipo",
        e.getType(),
        "Valor",
        e.getAmount().stripTrailingZeros().toPlainString(),
        "Recebido / pago",
        e.getPaidAmount().stripTrailingZeros().toPlainString(),
        "Vencimento",
        e.getDueDate(),
        "Pagamento",
        e.getPaymentDate(),
        "Ocorrência pausada",
        e.isSeriesPaused(),
        "Situação",
        e.getStatus());
  }

  public void recordChange(String action, FinancialEntry e, Map<String, String> before) {
    audit.change(action, "ENTRY", e.getId(), e.getDescription(), before, snapshot(e));
  }

  @Transactional
  public AccountView createAccount(AccountInput input) {
    subscriptions.check(
        current.companyId(), com.fintry.subscription.SubscriptionService.Resource.ACCOUNTS, 1);
    FinancialAccount a = new FinancialAccount();
    a.setCompanyId(current.companyId());
    a.setName(input.name().strip());
    a.setInitialBalance(input.initialBalance());
    accounts.save(a);
    audit.change(
        "CREATE",
        "ACCOUNT",
        a.getId(),
        a.getName(),
        Map.of(),
        com.fintry.audit.AuditService.fields(
            "Nome", a.getName(), "Saldo inicial", a.getInitialBalance()));
    return new AccountView(a.getId(), a.getName(), a.getInitialBalance(), a.getInitialBalance());
  }

  @Transactional
  public EntryView createEntry(EntryInput input) {
    var a = account(input.accountId());
    if (input.paid()) validPaymentDate(input.paymentDate());
    FinancialEntry e = new FinancialEntry();
    e.setCompanyId(a.getCompanyId());
    e.setAccountId(a.getId());
    e.setDescription(input.description().strip());
    applyLinks(e, input);
    e.setType(input.type());
    e.setAmount(input.amount());
    e.setDueDate(input.dueDate());
    e.setStatus(input.paid() ? PAID : PENDING);
    e.setPaymentDate(input.paid() ? input.paymentDate() : null);
    e.setPaidAmount(input.paid() ? input.amount() : BigDecimal.ZERO);
    entries.saveAndFlush(e);
    if (input.paid()) recordPayment(e, input.amount(), input.paymentDate());
    recordChange("CREATE", e, Map.of());
    return view(e);
  }

  private void applyLinks(FinancialEntry e, EntryInput input) {
    if (input.categoryId() != null) {
      e.setCategory(directory.categoryName(input.categoryId(), e.getCategoryId(), input.type()));
    } else {
      if (input.category() == null || input.category().isBlank())
        throw invalid("Informe uma categoria ou escolha uma cadastrada.");
      e.setCategory(input.category().strip());
    }
    e.setContactName(
        input.contactId() == null
            ? null
            : directory.contactName(input.contactId(), e.getContactId(), input.type()));
    e.setCategoryId(input.categoryId());
    e.setContactId(input.contactId());
  }

  @Transactional
  public EntryView updateEntry(UUID id, EntryInput input) {
    var e = entries.lock(id, current.companyId()).orElseThrow(this::missing);
    requireBillingEditable(id);
    if (e.getSaleId() != null)
      throw invalid(
          "O lançamento pertence a uma venda. Cancele a venda para corrigir os itens e o estoque.");
    if (e.getSeriesId() != null)
      throw invalid(
          "Para ajustar uma série, cancele as pendências e crie outra série. Os registros"
              + " realizados serão preservados.");
    if (e.getStatus() != PENDING || e.getTransferId() != null)
      throw invalid(
          "Somente lançamentos pendentes podem ser editados. Para corrigir realizados, cancele e"
              + " cadastre novamente.");
    if (input.paid()) throw invalid("Use a ação de baixa para registrar o pagamento.");
    var before = snapshot(e);
    var a = account(input.accountId());
    applyLinks(e, input);
    e.setAccountId(a.getId());
    e.setDescription(input.description().strip());
    e.setType(input.type());
    e.setAmount(input.amount());
    e.setPaidAmount(BigDecimal.ZERO);
    e.setDueDate(input.dueDate());
    recordChange("UPDATE", e, before);
    return view(e);
  }

  public EntryPage list(
      LocalDate start,
      LocalDate end,
      FinancialEntry.Type type,
      FinancialEntry.Status status,
      UUID accountId,
      String search,
      UUID seriesId,
      int page,
      int size) {
    if (page < 0 || size < 1 || size > 100) throw invalid("Paginação inválida.");
    if (start != null && end != null && start.isAfter(end)) throw invalid("Período inválido.");
    var company = current.companyId();
    Specification<FinancialEntry> filter =
        (root, query, cb) -> {
          var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
          predicates.add(cb.equal(root.get("companyId"), company));
          if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), start));
          if (end != null) predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), end));
          if (type != null) predicates.add(cb.equal(root.get("type"), type));
          if (status != null) predicates.add(cb.equal(root.get("status"), status));
          if (accountId != null) predicates.add(cb.equal(root.get("accountId"), accountId));
          if (seriesId != null) predicates.add(cb.equal(root.get("seriesId"), seriesId));
          if (search != null && !search.isBlank()) {
            var escaped =
                search
                    .strip()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            predicates.add(cb.like(cb.lower(root.get("description")), "%" + escaped + "%", '!'));
          }
          return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result =
        entries.findAll(
            filter,
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Sort.Order.desc("dueDate"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id"))));
    return new EntryPage(
        result.map(this::view).getContent(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber());
  }

  @Transactional
  public EntryView pay(UUID id, PaymentInput input) {
    validPaymentDate(input.paymentDate());
    var e = entries.lock(id, current.companyId()).orElseThrow(this::missing);
    requireBillingEditable(id);
    if (e.getStatus() == CANCELLED) throw invalid("Lançamento cancelado não pode ser pago.");
    if (e.getStatus() == PAID && input.amount() == null) return view(e);
    if (e.getStatus() == PAID) throw invalid("Este lançamento já foi totalmente pago.");
    var remaining = e.getAmount().subtract(e.getPaidAmount());
    var amount = input.amount() == null ? remaining : input.amount();
    if (amount.signum() <= 0 || amount.compareTo(remaining) > 0)
      throw invalid("Informe um valor positivo até o saldo restante de " + remaining + ".");
    var before = snapshot(e);
    e.setPaidAmount(e.getPaidAmount().add(amount));
    recordPayment(e, amount, input.paymentDate(), null);
    boolean settled = e.getPaidAmount().compareTo(e.getAmount()) == 0;
    e.setStatus(settled ? PAID : FinancialEntry.Status.PARTIAL);
    e.setPaymentDate(settled ? input.paymentDate() : null);
    recordChange(settled ? "PAY" : "PARTIAL_PAY", e, before);
    return view(e);
  }

  @Transactional
  public EntryView payFromReconciliation(UUID id, LocalDate paymentDate, BigDecimal amount, UUID batchId) {
    validPaymentDate(paymentDate);
    var e = entries.lock(id, current.companyId()).orElseThrow(this::missing);
    requireBillingEditable(id);
    if (e.getStatus() == CANCELLED) throw invalid("Lançamento cancelado não pode ser pago.");
    var remaining = e.getAmount().subtract(e.getPaidAmount());
    if (amount.signum() <= 0 || amount.compareTo(remaining) > 0)
      throw invalid("O total dos movimentos excede o saldo restante do lançamento.");
    var before = snapshot(e);
    e.setPaidAmount(e.getPaidAmount().add(amount));
    recordPayment(e, amount, paymentDate, batchId);
    boolean settled = e.getPaidAmount().compareTo(e.getAmount()) == 0;
    e.setStatus(settled ? PAID : FinancialEntry.Status.PARTIAL);
    e.setPaymentDate(settled ? paymentDate : null);
    recordChange(settled ? "PAY" : "PARTIAL_PAY", e, before);
    return view(e);
  }

  private void recordPayment(FinancialEntry e, BigDecimal amount, LocalDate paymentDate) {
    recordPayment(e, amount, paymentDate, null);
  }

  private void recordPayment(FinancialEntry e, BigDecimal amount, LocalDate paymentDate, UUID batchId) {
    var payment = new EntryPayment();
    payment.setCompanyId(e.getCompanyId());
    payment.setEntry(e);
    payment.setAccountId(e.getAccountId());
    payment.setAmount(amount);
    payment.setPaymentDate(paymentDate);
    payment.setReconciliationBatchId(batchId);
    payments.save(payment);
  }

  public void requireBillingEditable(UUID id) {
    if (billingCharges.existsByFeeEntryId(id)
        || billingCharges
            .findByEntryId(id)
            .filter(c -> c.getStatus() != com.fintry.billing.BillingCharge.Status.CANCELLED)
            .isPresent())
      throw invalid(
          "Este lançamento é gerenciado por Cobranças. Consulte ou cancele a cobrança no Asaas"
              + " antes de alterar o financeiro.");
  }

  public void requireUnreconciled(UUID id) {
    requireBillingEditable(id);
    if (statementLines.existsByEntryId(id))
      throw invalid(
          "Reabra o movimento na Conciliação antes de cancelar o lançamento ou a venda. A"
              + " reabertura não estorna o pagamento.");
  }

  @Transactional
  public EntryView cancel(UUID id) {
    var e = entries.lock(id, current.companyId()).orElseThrow(this::missing);
    requireBillingEditable(id);
    requireUnreconciled(id);
    if (e.getSaleId() != null)
      throw invalid(
          "Cancele pela página Vendas para devolver o estoque e estornar o financeiro juntos.");
    if (e.getTransferId() != null)
      throw invalid(
          "Transferências não podem ser canceladas individualmente. Faça a transferência inversa.");
    var before = snapshot(e);
    e.setStatus(CANCELLED);
    recordChange("CANCEL", e, before);
    return view(e);
  }

  @Transactional
  public void transfer(TransferInput input) {
    if (input.sourceId().equals(input.destinationId()))
      throw invalid("Escolha duas contas diferentes.");
    var source = account(input.sourceId());
    var destination = account(input.destinationId());
    validPaymentDate(input.date());
    var transferId = UUID.randomUUID();
    for (var a : List.of(source, destination)) {
      FinancialEntry e = new FinancialEntry();
      e.setCompanyId(a.getCompanyId());
      e.setAccountId(a.getId());
      e.setDescription(input.description().strip());
      e.setCategory("Transferência entre contas");
      e.setType(a.getId().equals(source.getId()) ? EXPENSE : INCOME);
      e.setAmount(input.amount());
      e.setDueDate(input.date());
      e.setPaymentDate(input.date());
      e.setStatus(PAID);
      e.setTransferId(transferId);
      e.setPaidAmount(input.amount());
      entries.saveAndFlush(e);
      recordPayment(e, input.amount(), input.date());
    }
    audit.change(
        "TRANSFER",
        "TRANSFER",
        transferId,
        input.description(),
        Map.of(),
        com.fintry.audit.AuditService.fields(
            "Origem",
            source.getName(),
            "Destino",
            destination.getName(),
            "Valor",
            input.amount(),
            "Data",
            input.date()));
  }

  public Summary summary(YearMonth month) {
    var company = current.companyId();
    var start = month.minusMonths(5).atDay(1);
    var end = month.atEndOfMonth();
    var paid = payments.settledBetween(company, start, end);
    List<MonthView> flow = new ArrayList<>();
    for (int i = 5; i >= 0; i--) {
      var m = month.minusMonths(i);
      BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
      for (var payment : paid)
        if (YearMonth.from((LocalDate) payment[2]).equals(m)) {
          if (payment[0] == INCOME) income = income.add((BigDecimal) payment[1]);
          else expense = expense.add((BigDecimal) payment[1]);
        }
      flow.add(new MonthView(m.toString(), income, expense));
    }
    BigDecimal pendingIncome = BigDecimal.ZERO, pendingExpense = BigDecimal.ZERO;
    for (Object[] row : entries.pending(company, month.atDay(1), end)) {
      if (row[0] == INCOME) pendingIncome = (BigDecimal) row[1];
      else pendingExpense = (BigDecimal) row[1];
    }
    var categoryTotals = new HashMap<String, BigDecimal>();
    for (var payment : paid)
      if (payment[0] == EXPENSE && YearMonth.from((LocalDate) payment[2]).equals(month))
        categoryTotals.merge((String) payment[3], (BigDecimal) payment[1], BigDecimal::add);
    var categories =
        categoryTotals.entrySet().stream()
            .map(e -> new CategoryExpense(e.getKey(), e.getValue()))
            .sorted(
                Comparator.comparing(CategoryExpense::amount)
                    .reversed()
                    .thenComparing(CategoryExpense::category))
            .toList();
    var previous = flow.get(4);
    var totals = flow.get(5);
    return new Summary(
        accounts().stream().map(AccountView::balance).reduce(BigDecimal.ZERO, BigDecimal::add),
        totals.income(),
        totals.expense(),
        totals.income().subtract(totals.expense()),
        pendingIncome,
        pendingExpense,
        flow,
        previous.income(),
        previous.expense(),
        previous.income().subtract(previous.expense()),
        categories);
  }
}
