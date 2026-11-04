package com.fintry.scenario;

import com.fintry.finance.FinanceService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {
  private final ScenarioService service;

  @PostMapping("/preview")
  public ResponseEntity<ScenarioService.Preview> preview(
      @Valid @RequestBody ScenarioService.Input input) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(input));
  }

  @GetMapping("/candidates")
  public ResponseEntity<FinanceService.EntryPage> candidates(
      @RequestParam(defaultValue = "90") int days,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(defaultValue = "false") boolean includeOverdue,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.candidates(days, accountId, includeOverdue, search, page, size));
  }
}
