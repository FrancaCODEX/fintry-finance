package com.fintry.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingWebhookScheduler {
  private final BillingService billing;

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void process() {
    billing.processWebhookQueue();
  }
}
