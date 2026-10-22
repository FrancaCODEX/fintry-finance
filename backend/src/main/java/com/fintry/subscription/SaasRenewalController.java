package com.fintry.subscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription/renewal")
@RequiredArgsConstructor
public class SaasRenewalController {
  private final SaasRenewalService service;

  @GetMapping
  public SaasRenewalService.View current() {
    return service.current();
  }

  @PostMapping("/enable")
  public SaasRenewalService.View enable(@Valid @RequestBody SaasRenewalService.Consent input) {
    return service.enable(input);
  }

  @PostMapping("/disable")
  public SaasRenewalService.View disable(@Valid @RequestBody SaasRenewalService.Revision input) {
    return service.disable(input);
  }

  @PostMapping("/check")
  public SaasRenewalService.View check() {
    return service.check();
  }
}
