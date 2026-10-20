package com.fintry.finance;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/pending")
@RequiredArgsConstructor
public class PendingController {
  private final PendingService service;

  @GetMapping
  public PendingService.Result list(
      @RequestParam(defaultValue = "ALL") PendingService.Group group,
      @RequestParam(required = false) FinancialEntry.Type type,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(defaultValue = "ALL") PendingService.Source source,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(group, type, accountId, source, search, page, size);
  }
}
