package com.fintry.billing;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {
  private final BillingService service;

  @GetMapping("/connection")
  BillingService.ConnectionView connection() {
    return service.configuration();
  }

  @PutMapping("/connection")
  BillingService.ConnectionView configure(
      @Valid @RequestBody BillingService.ConnectionInput input) {
    return service.configure(input);
  }

  @GetMapping
  BillingService.PageView list(@RequestParam(defaultValue = "0") int page) {
    return service.list(page);
  }

  @GetMapping("/webhook-operations")
  BillingService.WebhookOperations webhookOperations() {
    return service.webhookOperations();
  }

  @PostMapping("/webhook-operations/{id}/retry")
  void retryWebhook(@PathVariable UUID id) {
    service.retryWebhook(id);
  }

  @PostMapping
  BillingService.View issue(@Valid @RequestBody BillingService.IssueInput input) {
    return service.issue(input);
  }

  @PostMapping("/{id}/refresh")
  BillingService.View refresh(@PathVariable UUID id) {
    return service.refresh(id);
  }

  @PostMapping("/{id}/cancel")
  BillingService.View cancel(@PathVariable UUID id) {
    return service.cancel(id);
  }

  @PostMapping("/{id}/pix")
  BillingService.Pix pix(@PathVariable UUID id) {
    return service.pix(id);
  }
}
