package com.fintry.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fintry.saas.renewals.scheduler-enabled", havingValue = "true")
@RequiredArgsConstructor
public class SaasRenewalScheduler {
  private final SaasRenewalService service;

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void run() {
    service.processDue();
  }
}
