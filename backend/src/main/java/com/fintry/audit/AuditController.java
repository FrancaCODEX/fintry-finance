package com.fintry.audit;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audit")
public class AuditController {
  private final AuditService service;

  @GetMapping
  public AuditService.Result list(
      @RequestParam LocalDate start,
      @RequestParam LocalDate end,
      @RequestParam(required = false) String resource,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(start, end, resource, search, page, size);
  }
}
