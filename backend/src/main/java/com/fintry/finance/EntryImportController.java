package com.fintry.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/imports")
@RequiredArgsConstructor
public class EntryImportController {
  private final EntryImportService service;

  @PostMapping("/preview")
  public EntryImportService.Preview preview(@Valid @RequestBody EntryImportService.Input input) {
    return service.preview(input);
  }

  @PostMapping("/confirm")
  public EntryImportService.Result confirm(
      @Valid @RequestBody EntryImportService.Confirmation input) {
    return service.confirm(input);
  }

  @GetMapping(value = "/template", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<String> template() {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fintry-modelo-importacao.csv")
        .body(
            "\ufeffdescricao;tipo;valor;vencimento;categoria;situacao;data_pagamento\r\n"
                + "Consultoria mensal;receita;1500,00;20/10/2026;Serviços;pendente;\r\n"
                + "Material de"
                + " escritório;despesa;89,90;19/10/2026;Escritório;realizado;19/10/2026\r\n");
  }
}
