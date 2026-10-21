package com.fintry.subscription;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription/payments")
@RequiredArgsConstructor
public class SaasPaymentController {
  private final SaasPaymentService service;

  @GetMapping
  public SaasPaymentService.View list() {
    return service.list();
  }

  @PostMapping
  public SaasPaymentService.InvoiceView checkout(
      @Valid @RequestBody SaasPaymentService.CheckoutInput input) {
    return service.checkout(input);
  }

  @PostMapping("/{id}/refresh")
  public SaasPaymentService.InvoiceView refresh(@PathVariable UUID id) {
    return service.refresh(id);
  }

  @PostMapping("/{id}/cancel")
  public SaasPaymentService.InvoiceView cancel(@PathVariable UUID id) {
    return service.cancel(id);
  }
}
