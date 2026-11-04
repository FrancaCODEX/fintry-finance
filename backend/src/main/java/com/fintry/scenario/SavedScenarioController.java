package com.fintry.scenario;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scenarios/saved")
@RequiredArgsConstructor
public class SavedScenarioController {
  private final SavedScenarioService service;

  // Retry once only after the transactional service has rolled back a database race.
  // Persistent request keys make replay safe; this does not retry financial mutations.
  private <T> T command(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (org.springframework.dao.ConcurrencyFailureException
        | org.springframework.dao.DataIntegrityViolationException race) {
      return operation.get();
    }
  }

  @GetMapping
  public SavedScenarioService.PageView list(
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(archived, page);
  }

  @GetMapping("/{id}")
  public SavedScenarioService.View details(@PathVariable UUID id) {
    return service.details(id);
  }

  @PostMapping
  public SavedScenarioService.View create(@Valid @RequestBody SavedScenarioService.Save input) {
    return command(() -> service.save(null, input));
  }

  @PutMapping("/{id}")
  public SavedScenarioService.View update(
      @PathVariable UUID id, @Valid @RequestBody SavedScenarioService.Save input) {
    return command(() -> service.save(id, input));
  }

  @PutMapping("/{id}/archive")
  public SavedScenarioService.View archive(
      @PathVariable UUID id, @Valid @RequestBody SavedScenarioService.Archive input) {
    return service.archive(id, input);
  }

  @PostMapping("/{id}/calculate")
  public SavedScenarioService.RunView calculate(
      @PathVariable UUID id, @Valid @RequestBody SavedScenarioService.Calculate input) {
    return command(() -> service.calculate(id, input));
  }

  @GetMapping("/{id}/runs")
  public SavedScenarioService.History history(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    return service.history(id, page);
  }

  @GetMapping("/{id}/runs/{run}")
  public SavedScenarioService.RunView run(@PathVariable UUID id, @PathVariable UUID run) {
    return service.run(id, run);
  }

  @GetMapping("/{id}/compare")
  public SavedScenarioService.Comparison compare(
      @PathVariable UUID id, @RequestParam UUID left, @RequestParam UUID right) {
    return service.compare(id, left, right);
  }
}
