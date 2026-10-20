package com.fintry.budget;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/budgets")
@RequiredArgsConstructor
public class BudgetController {
  private final BudgetService service;

  @GetMapping
  public BudgetService.Result list(@RequestParam String month) {
    return service.list(month);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UUID create(@Valid @RequestBody BudgetService.Input input) {
    return service.save(null, input);
  }

  @PutMapping("/{id}")
  public UUID update(@PathVariable UUID id, @Valid @RequestBody BudgetService.Input input) {
    return service.save(id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @PostMapping("/copy")
  public BudgetService.CopyResult copy(@Valid @RequestBody BudgetService.CopyInput input) {
    return service.copy(input);
  }
}
