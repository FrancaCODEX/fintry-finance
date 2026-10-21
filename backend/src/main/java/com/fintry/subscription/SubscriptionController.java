package com.fintry.subscription;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
  private final SubscriptionService service;

  @GetMapping("/access")
  public SubscriptionService.Access access() {
    return service.access();
  }

  @GetMapping
  public SubscriptionService.View current() {
    return service.current();
  }

  @PostMapping("/simulate")
  public SubscriptionService.View simulate(
      @Valid @RequestBody SubscriptionService.ChangeInput input) {
    return service.simulate(input);
  }

  @PostMapping("/requests")
  public SubscriptionService.View request(
      @Valid @RequestBody SubscriptionService.ChangeInput input) {
    return service.request(input);
  }

  @PostMapping("/requests/{id}/cancel")
  public SubscriptionService.View cancelRequest(
      @PathVariable UUID id, @Valid @RequestBody SubscriptionService.RevisionInput input) {
    return service.cancelRequest(id, input);
  }

  @PostMapping("/cancel")
  public SubscriptionService.View cancel(
      @Valid @RequestBody SubscriptionService.RevisionInput input) {
    return service.cancellation(input, true);
  }

  @PostMapping("/keep")
  public SubscriptionService.View keep(
      @Valid @RequestBody SubscriptionService.RevisionInput input) {
    return service.cancellation(input, false);
  }
}
