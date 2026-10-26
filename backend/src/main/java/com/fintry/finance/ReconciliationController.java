package com.fintry.finance;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {
  private final ReconciliationService service;

  @GetMapping(value = "/example", produces = "application/x-ofx;charset=UTF-8")
  public org.springframework.http.ResponseEntity<String> example() {
    String day =
        java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    return org.springframework.http.ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=fintry-extrato-ficticio.ofx")
        .body(
            "OFXHEADER:100\n"
                + "DATA:OFXSGML\n"
                + "VERSION:102\n"
                + "SECURITY:NONE\n"
                + "ENCODING:UTF-8\n"
                + "CHARSET:NONE\n\n"
                + "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>000<ACCTID>DEMO0001<ACCTTYPE>CHECKING</BANKACCTFROM><BANKTRANLIST><STMTTRN><DTPOSTED>"
                + day
                + "<TRNAMT>450.00<FITID>FINTRY-DEMO-IN-"
                + day
                + "<NAME>Recebimento fictício</STMTTRN><STMTTRN><DTPOSTED>"
                + day
                + "<TRNAMT>-12.90<FITID>FINTRY-DEMO-OUT-"
                + day
                + "<NAME>Tarifa"
                + " fictícia</STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>");
  }

  @PostMapping("/preview")
  public ReconciliationService.Preview preview(
      @Valid @RequestBody ReconciliationService.Input input) {
    return service.preview(input);
  }

  @PostMapping("/import")
  public ReconciliationService.ImportResult importStatement(
      @Valid @RequestBody ReconciliationService.ImportInput input) {
    return service.importStatement(input);
  }

  @GetMapping
  public ReconciliationService.Result list(
      @RequestParam UUID accountId,
      @RequestParam(required = false) StatementLine.Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return service.list(accountId, status, page, size);
  }

  @GetMapping("/{id}/candidates")
  public List<ReconciliationService.Candidate> candidates(
      @PathVariable UUID id, @RequestParam(required = false) String search) {
    return service.candidates(id, search);
  }

  @PostMapping("/{id}/resolve")
  public ReconciliationService.LineView resolve(
      @PathVariable UUID id, @Valid @RequestBody ReconciliationService.Resolution input) {
    return service.resolve(id, input);
  }

  @PostMapping("/group")
  public ReconciliationService.BatchView group(
      @Valid @RequestBody ReconciliationService.GroupInput input) {
    return service.group(input);
  }

  @PostMapping("/group/candidates")
  public List<FinanceService.EntryView> groupCandidates(
      @Valid @RequestBody ReconciliationService.GroupCandidatesInput input) {
    return service.groupCandidates(input);
  }
}
