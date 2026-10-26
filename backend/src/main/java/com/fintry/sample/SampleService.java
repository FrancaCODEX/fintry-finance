package com.fintry.sample;

import com.fintry.attachment.AttachmentService;
import com.fintry.audit.AuditService;
import com.fintry.auth.CurrentUser;
import com.fintry.budget.BudgetService;
import com.fintry.commerce.*;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.directory.*;
import com.fintry.finance.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SampleService {
  private final CurrentUser current;
  private final CompanyRepository companies;
  private final SampleRunRepository runs;
  private final FinanceService finance;
  private final DirectoryService directory;
  private final CommerceService commerce;
  private final ProposalService proposals;
  private final SeriesService series;
  private final BudgetService budgets;
  private final AttachmentService attachments;
  private final ReconciliationService reconciliation;
  private final AuditService audit;
  private final Clock clock;

  public record Status(boolean loaded, int packVersion, LocalDateTime completedAt) {}

  private record DemoMovement(String code, LocalDate date, String amount, String description) {}

  public Status status() {
    return runs.findById(current.companyId())
        .map(r -> new Status(true, r.getPackVersion(), r.getCompletedAt()))
        .orElse(new Status(false, 1, null));
  }

  private BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  private FinanceService.EntryInput input(
      UUID account,
      String description,
      FinancialEntry.Type type,
      String amount,
      LocalDate due,
      boolean paid,
      UUID category,
      UUID contact) {
    return new FinanceService.EntryInput(
        account,
        description,
        null,
        type,
        money(amount),
        due,
        paid,
        paid ? due : null,
        category,
        contact);
  }

  private UUID entry(
      UUID account,
      String description,
      FinancialEntry.Type type,
      String amount,
      LocalDate due,
      boolean paid,
      UUID category,
      UUID contact) {
    return finance
        .createEntry(input(account, description, type, amount, due, paid, category, contact))
        .id();
  }

  private UUID contact(String name, Contact.Kind kind, int index, String prefix, boolean active) {
    return directory
        .saveContact(
            null,
            new DirectoryService.ContactInput(
                prefix + name,
                kind,
                "00000000000" + String.format("%03d", index),
                "contato" + index + "@example.invalid",
                "(11) 0000-0000",
                "Cadastro fictício para explorar o Fintry. Documento e contato sem validade."
                    + " Preferência: atendimento por e-mail.",
                active))
        .id();
  }

  private UUID category(String name, FinancialCategory.Kind kind, String prefix) {
    String base = prefix + name, label = base;
    var names = new HashSet<String>();
    for (var c : directory.categories()) names.add(c.name().toLowerCase(Locale.ROOT));
    for (int suffix = 2; names.contains(label.toLowerCase(Locale.ROOT)); suffix++)
      label = base + " (" + suffix + ")";
    return directory.saveCategory(null, new DirectoryService.CategoryInput(label, kind, true)).id();
  }

  private CommerceService.ProductView product(
      String sku,
      String name,
      String unit,
      String price,
      String cost,
      String quantity,
      String minimum,
      boolean active) {
    return commerce.save(
        null,
        new CommerceService.ProductInput(
            sku, name, unit, money(price), money(cost), money(minimum), active, money(quantity)));
  }

  private CommerceService.ProductView service(String sku, String name, String price, String cost) {
    return commerce.save(
        null,
        new CommerceService.ProductInput(
            sku,
            name,
            "UN",
            money(price),
            money(cost),
            BigDecimal.ZERO,
            true,
            BigDecimal.ZERO,
            Product.Kind.SERVICE));
  }

  private ProposalService.View proposal(
      String title,
      UUID customer,
      LocalDate validUntil,
      List<CommerceService.LineInput> items) {
    return proposals
        .create(
            new ProposalService.Input(
                UUID.randomUUID(),
                title,
                customer,
                "Demonstração fictícia · proposta não enviada ao cliente.",
                validUntil,
                items))
        .proposal();
  }

  private CommerceService.SaleView draft(
      UUID customer, String notes, CommerceService.ProductView p, String quantity) {
    return commerce
        .draft(
            new CommerceService.DraftInput(
                UUID.randomUUID(),
                customer,
                notes,
                List.of(new CommerceService.LineInput(p.id(), money(quantity), p.price()))))
        .sale();
  }

  @Transactional
  public Status populate() {
    UUID company = current.companyId();
    companies
        .lock(company)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
    var existing = status();
    if (existing.loaded() && existing.packVersion() >= 3) return existing;
    String token = UUID.randomUUID().toString().substring(0, 8), prefix = "[Exemplo] ";
    var today = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")));
    var month = YearMonth.from(today);
    if (!existing.loaded()) {
    var bank =
        finance
            .createAccount(
                new FinanceService.AccountInput(prefix + "Banco principal", money("8500")))
            .id();
    var cash =
        finance
            .createAccount(new FinanceService.AccountInput(prefix + "Caixa da loja", money("1200")))
            .id();
    var wallet =
        finance
            .createAccount(
                new FinanceService.AccountInput(prefix + "Carteira digital", money("600")))
            .id();
    UUID aurora = contact("Studio Aurora", Contact.Kind.CUSTOMER, 101, prefix, true),
        cafe = contact("Café Horizonte", Contact.Kind.CUSTOMER, 102, prefix, true),
        norte = contact("Papelaria Norte", Contact.Kind.SUPPLIER, 103, prefix, true),
        cloud = contact("Nuvem Ferramentas", Contact.Kind.SUPPLIER, 104, prefix, true),
        parceiro = contact("Coletivo Central", Contact.Kind.BOTH, 105, prefix, true),
        inactive = contact("Cliente arquivado", Contact.Kind.CUSTOMER, 106, prefix, false);
    UUID services = category("Serviços", FinancialCategory.Kind.INCOME, prefix),
        rent = category("Aluguel", FinancialCategory.Kind.EXPENSE, prefix),
        software = category("Software", FinancialCategory.Kind.EXPENSE, prefix),
        marketing = category("Marketing", FinancialCategory.Kind.EXPENSE, prefix),
        office = category("Escritório", FinancialCategory.Kind.EXPENSE, prefix),
        equipment = category("Equipamentos", FinancialCategory.Kind.EXPENSE, prefix),
        utilities = category("Energia e internet", FinancialCategory.Kind.EXPENSE, prefix),
        travel = category("Deslocamentos", FinancialCategory.Kind.BOTH, prefix);
    for (int i = 5; i >= 0; i--) {
      var date = month.minusMonths(i).atDay(1);
      entry(
          bank,
          prefix + "Contrato mensal Aurora",
          FinancialEntry.Type.INCOME,
          "" + (6900 + (5 - i) * 730),
          date,
          true,
          services,
          aurora);
      entry(
          wallet,
          prefix + "Projeto Café Horizonte",
          FinancialEntry.Type.INCOME,
          "" + (2300 + (5 - i) * 170),
          date,
          true,
          services,
          cafe);
      entry(
          bank,
          prefix + "Aluguel do espaço",
          FinancialEntry.Type.EXPENSE,
          "1800",
          date,
          true,
          rent,
          parceiro);
      entry(
          bank,
          prefix + "Ferramentas da equipe",
          FinancialEntry.Type.EXPENSE,
          "389.90",
          date,
          true,
          software,
          cloud);
      entry(
          bank,
          prefix + "Campanha de divulgação",
          FinancialEntry.Type.EXPENSE,
          "" + (320 + (5 - i) * 75),
          date,
          true,
          marketing,
          parceiro);
    }
    UUID receipt =
        entry(
            cash,
            prefix + "Materiais de escritório",
            FinancialEntry.Type.EXPENSE,
            "145.50",
            today,
            true,
            office,
            norte);
    entry(
        bank,
        prefix + "Projeto com vencimento hoje",
        FinancialEntry.Type.INCOME,
        "4200",
        today,
        false,
        services,
        aurora);
    entry(
        bank,
        prefix + "Cliente em atraso",
        FinancialEntry.Type.INCOME,
        "980",
        today.minusDays(4),
        false,
        services,
        cafe);
    entry(
        bank,
        prefix + "Internet em atraso",
        FinancialEntry.Type.EXPENSE,
        "179.90",
        today.minusDays(2),
        false,
        utilities,
        cloud);
    entry(
        bank,
        prefix + "Revisão de campanha",
        FinancialEntry.Type.EXPENSE,
        "280",
        today.plusDays(3),
        false,
        marketing,
        parceiro);
    entry(
        wallet,
        prefix + "Entrega na próxima semana",
        FinancialEntry.Type.INCOME,
        "1650",
        today.plusDays(7),
        false,
        services,
        cafe);
    entry(
        bank,
        prefix + "Materiais a repor",
        FinancialEntry.Type.EXPENSE,
        "230",
        today.plusDays(12),
        false,
        office,
        norte);
    entry(
        bank,
        prefix + "Projeto próximo mês",
        FinancialEntry.Type.INCOME,
        "3100",
        today.plusDays(25),
        false,
        services,
        aurora);
    entry(
        bank,
        prefix + "Previsão de manutenção",
        FinancialEntry.Type.EXPENSE,
        "450",
        today.plusDays(45),
        false,
        equipment,
        parceiro);
    UUID cancelled =
        entry(
            cash,
            prefix + "Compra cancelada",
            FinancialEntry.Type.EXPENSE,
            "90",
            today,
            false,
            office,
            norte);
    finance.cancel(cancelled);
    finance.transfer(
        new FinanceService.TransferInput(
            bank, cash, money("300"), today, prefix + "Reforço do caixa"));
    series.create(
        new SeriesService.Input(
            UUID.randomUUID(),
            FinancialSeries.Kind.RECURRING,
            6,
            input(
                bank,
                prefix + "Assinatura mensal de suporte",
                FinancialEntry.Type.EXPENSE,
                "159.90",
                today,
                false,
                software,
                cloud)));
    series.create(
        new SeriesService.Input(
            UUID.randomUUID(),
            FinancialSeries.Kind.INSTALLMENT,
            4,
            input(
                bank,
                prefix + "Notebook da equipe",
                FinancialEntry.Type.EXPENSE,
                "3200",
                today.minusDays(7),
                true,
                equipment,
                norte)));
    String sku = "DEMO-" + token + "-";
    var notebook =
        product(sku + "CAD", "Caderno pontilhado · exemplo", "UN", "34.90", "15", "40", "5", true);
    var pen = product(sku + "CAN", "Caneta preta · exemplo", "CX", "29.90", "12", "3", "5", true);
    var coffee =
        product(sku + "CAF", "Café especial · exemplo", "KG", "64.90", "32", "12.5", "2", true);
    product(sku + "PLA", "Planner semanal · exemplo", "UN", "49.90", "20", "0", "3", true);
    product(sku + "GARR", "Garrafa térmica · exemplo", "UN", "89.90", "40", "15", "2", true);
    product(sku + "OLD", "Modelo anterior · exemplo", "UN", "19.90", "8", "2", "0", false);
    commerce.movement(
        new CommerceService.MovementInput(
            UUID.randomUUID(),
            coffee.id(),
            StockMovement.Kind.IN,
            money("5"),
            "Reposição fictícia para demonstração"));
    commerce.movement(
        new CommerceService.MovementInput(
            UUID.randomUUID(),
            notebook.id(),
            StockMovement.Kind.OUT,
            money("2"),
            "Amostras fictícias para divulgação"));
    draft(aurora, "EXEMPLO · pedido aguardando aprovação", notebook, "3");
    var unpaid = draft(cafe, "EXEMPLO · entrega combinada, a receber", coffee, "1.5");
    commerce.confirm(
        unpaid.id(), new CommerceService.ConfirmInput(bank, today.plusDays(5), false, null));
    var paid = draft(parceiro, "EXEMPLO · pedido recebido no caixa", notebook, "2");
    var paidSale =
        commerce.confirm(paid.id(), new CommerceService.ConfirmInput(cash, today, true, today));
    var returned = draft(aurora, "EXEMPLO · pedido cancelado com devolução", pen, "1");
    commerce.confirm(returned.id(), new CommerceService.ConfirmInput(bank, today, true, today));
    commerce.cancel(returned.id());
    for (var m : List.of(month.minusMonths(1), month)) {
      budgets.save(null, new BudgetService.Input(m.toString(), rent, money("2000")));
      budgets.save(null, new BudgetService.Input(m.toString(), software, money("500")));
      budgets.save(null, new BudgetService.Input(m.toString(), marketing, money("600")));
      budgets.save(null, new BudgetService.Input(m.toString(), office, money("600")));
      budgets.save(null, new BudgetService.Input(m.toString(), equipment, money("1500")));
    }
    proof(receipt, "comprovante-escritorio-exemplo.pdf", "Materiais de escritorio", "R$ 145,50");
    proof(
        paidSale.entryId(),
        "recibo-venda-exemplo.pdf",
        "Venda demonstrativa de cadernos",
        "R$ 69,80");
    }
    if (!existing.loaded() || existing.packVersion() < 2)
      addCommercialExamples(prefix, token, today);
    addPresentationExamples(prefix, token, today);
    var run = runs.findById(company).orElseGet(SampleRun::new);
    run.setCompanyId(company);
    run.setPackVersion(3);
    run.setCompletedAt(LocalDateTime.now(clock));
    runs.saveAndFlush(run);
    audit.change(
        "SAMPLE_LOAD",
        "SAMPLE",
        company,
        "Dados de exemplo · pacote 3",
        Map.of(),
        AuditService.fields("Pacote", 3, "Identificador", token));
    return status();
  }

  private LocalDate recentDate(LocalDate today, int daysAgo) {
    return today.minusDays(Math.min(daysAgo, today.getDayOfMonth() - 1L));
  }

  private void addPresentationExamples(String prefix, String token, LocalDate today) {
    String label = prefix + "Apresentação · ";
    var account =
        finance.accounts().stream()
            .filter(a -> a.name().equals(prefix + "Banco principal"))
            .findFirst()
            .map(FinanceService.AccountView::id)
            .orElseGet(
                () ->
                    finance
                        .createAccount(
                            new FinanceService.AccountInput(
                                prefix + "Banco de apresentação", BigDecimal.ZERO))
                        .id());
    int contactIndex = Math.abs(UUID.randomUUID().hashCode() % 98) + 901;
    UUID customer = contact("Aurora Design", Contact.Kind.CUSTOMER, contactIndex, prefix, true);
    UUID supplier =
        contact("Banco de demonstração", Contact.Kind.SUPPLIER, contactIndex + 1, prefix, true);
    UUID incomeCategory = category("Projetos especiais", FinancialCategory.Kind.INCOME, prefix);
    UUID expenseCategory =
        category("Tarifas bancárias", FinancialCategory.Kind.EXPENSE, prefix);

    var groupedEntry =
        entry(
            account,
            label + "Projeto de identidade visual · baixa em etapas",
            FinancialEntry.Type.INCOME,
            "3000.00",
            today.plusDays(10),
            false,
            incomeCategory,
            customer);
    finance.pay(
        groupedEntry,
        new FinanceService.PaymentInput(recentDate(today, 3), money("300.00")));
    finance.pay(
        groupedEntry,
        new FinanceService.PaymentInput(recentDate(today, 1), money("450.00")));

    var partialExpense =
        entry(
            account,
            label + "Equipamentos · pagamento parcial",
            FinancialEntry.Type.EXPENSE,
            "1600.00",
            today.plusDays(8),
            false,
            expenseCategory,
            supplier);
    finance.pay(
        partialExpense,
        new FinanceService.PaymentInput(recentDate(today, 2), money("350.00")));

    var linkedIncome =
        entry(
            account,
            label + "Consultoria · recebimento conciliado",
            FinancialEntry.Type.INCOME,
            "880.00",
            today,
            false,
            incomeCategory,
            customer);
    var linkedExpense =
        entry(
            account,
            label + "Tarifa de processamento",
            FinancialEntry.Type.EXPENSE,
            "28.90",
            today,
            false,
            expenseCategory,
            supplier);

    var movements =
        List.of(
            new DemoMovement("GROUP-1", recentDate(today, 2), "700.00", "PIX PROJETO AURORA 1/2"),
            new DemoMovement("GROUP-2", recentDate(today, 1), "800.00", "PIX PROJETO AURORA 2/2"),
            new DemoMovement("LINK-IN", today, "880.00", "PIX CONSULTORIA AURORA"),
            new DemoMovement("LINK-OUT", today, "-28.90", "TARIFA PROCESSAMENTO"),
            new DemoMovement("OPEN", today, "-312.55", "COMPRA SEM LANCAMENTO PARA REVISAR"));
    reconciliation.importStatement(
        new ReconciliationService.ImportInput(
            account,
            presentationOfx(token, movements),
            java.util.stream.IntStream.rangeClosed(1, movements.size()).boxed().toList()));
    var lines =
        reconciliation.list(account, null, 0, 50).content().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ReconciliationService.LineView::fitId, line -> line));
    var groupLines =
        List.of(
            lines.get("FINTRY-PRESENTATION-" + token + "-GROUP-1"),
            lines.get("FINTRY-PRESENTATION-" + token + "-GROUP-2"));
    reconciliation.group(
        new ReconciliationService.GroupInput(
            account,
            groupLines.stream().map(ReconciliationService.LineView::id).toList(),
            groupedEntry,
            "Demonstração: dois créditos bancários conciliados com um recebimento parcial."));
    linkDemoMovement(
        lines.get("FINTRY-PRESENTATION-" + token + "-LINK-IN"),
        linkedIncome,
        "Recebimento demonstrativo vinculado ao lançamento correspondente.");
    linkDemoMovement(
        lines.get("FINTRY-PRESENTATION-" + token + "-LINK-OUT"),
        linkedExpense,
        "Tarifa demonstrativa vinculada à despesa correspondente.");
  }

  private void linkDemoMovement(
      ReconciliationService.LineView movement, UUID entryId, String reason) {
    reconciliation.resolve(
        movement.id(),
        new ReconciliationService.Resolution(
            movement.revision(), ReconciliationService.Action.LINK, entryId, null, null, reason));
  }

  private String presentationOfx(String token, List<DemoMovement> movements) {
    String rows =
        movements.stream()
            .map(
                movement ->
                    "<STMTTRN><TRNTYPE>OTHER<DTPOSTED>"
                        + movement.date().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                        + "<TRNAMT>"
                        + movement.amount()
                        + "<FITID>FINTRY-PRESENTATION-"
                        + token
                        + "-"
                        + movement.code()
                        + "<NAME>"
                        + movement.description()
                        + "</STMTTRN>")
            .collect(java.util.stream.Collectors.joining());
    return "OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nSECURITY:NONE\nENCODING:UTF-8\nCHARSET:NONE\n\n"
        + "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>999<BRANCHID>0001<ACCTID>DEMO-"
        + token
        + "<ACCTTYPE>CHECKING</BANKACCTFROM><BANKTRANLIST>"
        + rows
        + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
  }

  private void addCommercialExamples(
      String prefix, String token, LocalDate today) {
    UUID account =
        finance
            .createAccount(
                new FinanceService.AccountInput(prefix + "Conta comercial", BigDecimal.ZERO))
            .id();
    int contactIndex = Math.abs(UUID.randomUUID().hashCode() % 700) + 200;
    UUID aurora = contact("Empresa Aurora", Contact.Kind.CUSTOMER, contactIndex, prefix, true);
    UUID cafe = contact("Mercado Horizonte", Contact.Kind.CUSTOMER, contactIndex + 1, prefix, true);
    var kit =
        product(
            "DEMO-" + token + "-KIT",
            "Kit de implantação · exemplo",
            "UN",
            "89.90",
            "42",
            "30",
            "3",
            true);
    var setup = service("DEMO-" + token + "-SET", "Implantação de sistema · exemplo", "1800", "600");
    var support = service("DEMO-" + token + "-SUP", "Suporte mensal · exemplo", "450", "120");
    var training = service("DEMO-" + token + "-TRE", "Treinamento da equipe · exemplo", "750", "250");
    var lines =
        List.of(
            new CommerceService.LineInput(setup.id(), money("1"), setup.price()),
            new CommerceService.LineInput(kit.id(), money("3"), kit.price()));
    proposal(
        prefix + "Implantação do financeiro",
        aurora,
        today.plusDays(30),
        lines);
    var negotiating =
        proposal(
            prefix + "Suporte e treinamento",
            cafe,
            today.plusDays(20),
            List.of(
                new CommerceService.LineInput(support.id(), money("3"), support.price()),
                new CommerceService.LineInput(training.id(), money("1"), training.price()),
                new CommerceService.LineInput(kit.id(), money("2"), kit.price())));
    negotiating =
        proposals.transition(
            negotiating.id(),
            new ProposalService.Transition(negotiating.version(), Proposal.Status.OPEN));
    var approved =
        proposal(
            prefix + "Pacote de organização",
            aurora,
            today.plusDays(30),
            List.of(
                new CommerceService.LineInput(support.id(), money("1"), support.price()),
                new CommerceService.LineInput(kit.id(), money("2"), kit.price())));
    approved =
        proposals.transition(
            approved.id(), new ProposalService.Transition(approved.version(), Proposal.Status.OPEN));
    proposals.approve(
        approved.id(),
        new ProposalService.Approval(
            approved.version(),
            new CommerceService.ConfirmInput(account, today.plusDays(12), false, null)));
  }

  private void proof(UUID entry, String name, String description, String amount) {
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var text = new PDPageContentStream(doc, page)) {
        text.beginText();
        text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
        text.setLeading(28);
        text.newLineAtOffset(55, 735);
        for (var line :
            List.of(
                "FINTRY - DOCUMENTO DE EXEMPLO",
                "Sem validade fiscal ou bancaria",
                description,
                amount,
                "Dados ficticios para demonstracao da plataforma.")) {
          text.showText(line);
          text.newLine();
        }
        text.endText();
      }
      doc.save(out);
      attachments.upload(entry, new SampleFile(name, out.toByteArray()));
    } catch (IOException ex) {
      throw new IllegalStateException("Não foi possível gerar o comprovante de exemplo.", ex);
    }
  }

  private record SampleFile(String filename, byte[] content) implements MultipartFile {
    public String getName() {
      return "file";
    }

    public String getOriginalFilename() {
      return filename;
    }

    public String getContentType() {
      return "application/pdf";
    }

    public boolean isEmpty() {
      return content.length == 0;
    }

    public long getSize() {
      return content.length;
    }

    public byte[] getBytes() {
      return content.clone();
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    public void transferTo(File dest) throws IOException {
      Files.write(dest.toPath(), content);
    }
  }
}
