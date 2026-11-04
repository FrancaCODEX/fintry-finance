package com.fintry.finance;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/reconciliation/rules")
@RequiredArgsConstructor
public class ReconciliationRuleController {
  private final ReconciliationRuleService service;

  @GetMapping
  public ReconciliationRuleService.Result list(
      @RequestParam UUID accountId,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(accountId, active, page);
  }

  @PostMapping
  public ReconciliationRuleService.View create(
      @Valid @RequestBody ReconciliationRuleService.Input input) {
    return service.save(null, input);
  }

  @PutMapping("/{id}")
  public ReconciliationRuleService.View update(
      @PathVariable UUID id, @Valid @RequestBody ReconciliationRuleService.Input input) {
    return service.save(id, input);
  }
}
