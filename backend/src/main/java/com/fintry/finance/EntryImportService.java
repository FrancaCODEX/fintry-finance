package com.fintry.finance;

import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.directory.CategoryRepository;
import com.fintry.directory.FinancialCategory;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryImportService {
  private final CurrentUser current;
  private final AccountRepository accounts;
  private final EntryRepository entries;
  private final CategoryRepository categories;
  private final CompanyRepository companies;
  private final FinanceService finance;

  public record Input(@NotNull UUID accountId, @NotBlank @Size(max = 1048576) String csv) {}

  public record Confirmation(
      @NotNull UUID accountId,
      @NotBlank @Size(max = 1048576) String csv,
      @NotEmpty @Size(max = 500) List<@NotNull @Min(2) Integer> selectedRows) {}

  public record Row(
      int number,
      String description,
      String type,
      String amount,
      String dueDate,
      String category,
      String status,
      String paymentDate,
      String state,
      String message) {}

  public record Preview(
      String accountName, int total, long ready, long duplicates, long invalid, List<Row> rows) {}

  public record Result(int imported, int skippedDuplicates, String income, String expense) {}

  private record Candidate(Row row, FinanceService.EntryInput input) {}

  private record Fingerprint(
      String description, FinancialEntry.Type type, BigDecimal amount, LocalDate date) {}

  public Preview preview(Input input) {
    var account = account(input.accountId());
    var candidates = inspect(input.accountId(), input.csv());
    var rows = candidates.stream().map(Candidate::row).toList();
    return new Preview(
        account.getName(),
        rows.size(),
        count(rows, "READY"),
        count(rows, "DUPLICATE"),
        count(rows, "INVALID"),
        rows);
  }

  @Transactional
  public Result confirm(Confirmation input) {
    companies.lock(current.companyId()).orElseThrow(() -> missing());
    account(input.accountId());
    var candidates = inspect(input.accountId(), input.csv());
    var selection = new HashSet<>(input.selectedRows());
    if (selection.size() != input.selectedRows().size())
      throw CsvEntryParser.invalid("A seleção contém registros repetidos.");
    var selected = candidates.stream().filter(c -> selection.contains(c.row.number())).toList();
    if (selected.size() != selection.size())
      throw CsvEntryParser.invalid("A seleção contém registros que não estão no arquivo.");
    if (selected.stream().anyMatch(c -> c.input == null))
      throw CsvEntryParser.invalid(
          "Há registros inválidos na seleção. Corrija o arquivo e gere outra prévia.");
    int imported = 0, skipped = 0;
    BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
    for (var c : selected) {
      if (c.row.state().equals("DUPLICATE")) {
        skipped++;
        continue;
      }
      finance.createEntry(c.input);
      imported++;
      if (c.input.type() == FinancialEntry.Type.INCOME) income = income.add(c.input.amount());
      else expense = expense.add(c.input.amount());
    }
    return new Result(imported, skipped, income.toPlainString(), expense.toPlainString());
  }

  private long count(List<Row> rows, String state) {
    return rows.stream().filter(r -> r.state().equals(state)).count();
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
  }

  private FinancialAccount account(UUID id) {
    return accounts.findByIdAndCompanyId(id, current.companyId()).orElseThrow(this::missing);
  }

  private List<Candidate> inspect(UUID accountId, String csv) {
    var lines = CsvEntryParser.parse(csv);
    var categoryList = categories.findByCompanyIdOrderByNameAsc(current.companyId());
    var seen = new HashSet<Fingerprint>();
    var result = new ArrayList<Candidate>();
    for (var line : lines) {
      var f = line.fields();
      try {
        var description = f.get("descricao");
        if (description.isBlank() || description.length() > 255)
          throw new IllegalArgumentException("Informe uma descrição de até 255 caracteres.");
        var category = f.get("categoria");
        if (category.isBlank() || category.length() > 80)
          throw new IllegalArgumentException("Informe uma categoria de até 80 caracteres.");
        var type =
            switch (CsvEntryParser.key(f.get("tipo"))) {
              case "receita", "income" -> FinancialEntry.Type.INCOME;
              case "despesa", "expense" -> FinancialEntry.Type.EXPENSE;
              default ->
                  throw new IllegalArgumentException("Tipo inválido. Use receita ou despesa.");
            };
        var amount = CsvEntryParser.amount(f.get("valor"));
        var dueDate = CsvEntryParser.date(f.get("vencimento"));
        boolean paid =
            switch (CsvEntryParser.key(f.getOrDefault("situacao", ""))) {
              case "", "pendente", "pending" -> false;
              case "realizado", "pago", "recebido", "paid" -> true;
              default ->
                  throw new IllegalArgumentException(
                      "Situação inválida. Use pendente ou realizado.");
            };
        String paymentText = f.getOrDefault("data_pagamento", "");
        LocalDate paymentDate = paymentText.isBlank() ? null : CsvEntryParser.date(paymentText);
        if (paid
            && (paymentDate == null
                || paymentDate.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo")))))
          throw new IllegalArgumentException(
              "Realizados precisam de uma data de pagamento que não esteja no futuro.");
        if (!paid && paymentDate != null)
          throw new IllegalArgumentException("Pendente não deve ter data de pagamento.");
        FinancialCategory linked =
            categoryList.stream()
                .filter(c -> c.getName().equalsIgnoreCase(category))
                .findFirst()
                .orElse(null);
        if (linked != null
            && (!linked.isActive()
                || (linked.getKind() != FinancialCategory.Kind.BOTH
                    && !linked.getKind().name().equals(type.name()))))
          throw new IllegalArgumentException(
              "A categoria cadastrada está inativa ou não aceita este tipo de lançamento.");
        var entry =
            new FinanceService.EntryInput(
                accountId,
                description,
                category,
                type,
                amount,
                dueDate,
                paid,
                paymentDate,
                linked == null ? null : linked.getId(),
                null);
        var key = new Fingerprint(description.toLowerCase(Locale.ROOT), type, amount, dueDate);
        boolean repeated = !seen.add(key);
        boolean exists =
            entries.importDuplicate(
                    current.companyId(), accountId, key.description(), type, amount, dueDate)
                > 0;
        String status = repeated || exists ? "DUPLICATE" : "READY";
        String message =
            exists
                ? "Possível duplicidade: já existe na conta um lançamento ativo com a mesma"
                    + " descrição, tipo, valor e vencimento."
                : repeated
                    ? "Possível duplicidade: esses dados já aparecem antes neste arquivo."
                    : linked == null
                        ? "Categoria avulsa: não será criada uma categoria no cadastro."
                        : "Vinculado à categoria cadastrada.";
        result.add(
            new Candidate(
                new Row(
                    line.number(),
                    description,
                    type.name(),
                    amount.toPlainString(),
                    dueDate.toString(),
                    linked == null ? category : linked.getName(),
                    paid ? "PAID" : "PENDING",
                    paymentDate == null ? "" : paymentDate.toString(),
                    status,
                    message),
                entry));
      } catch (IllegalArgumentException ex) {
        result.add(
            new Candidate(
                new Row(
                    line.number(),
                    f.get("descricao"),
                    f.get("tipo"),
                    f.get("valor"),
                    f.get("vencimento"),
                    f.get("categoria"),
                    f.getOrDefault("situacao", ""),
                    f.getOrDefault("data_pagamento", ""),
                    "INVALID",
                    ex.getMessage()),
                null));
      }
    }
    return result;
  }
}
