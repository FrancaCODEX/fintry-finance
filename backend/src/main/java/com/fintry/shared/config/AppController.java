package com.fintry.shared.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class AppController {
  @Value("${fintry.version:development}")
  private String version;

  @Value("${fintry.mail.enabled:false}")
  private boolean recovery;

  @GetMapping("/api/v1/app")
  public Map<String, Object> info() {
    return Map.of("name", "Fintry", "version", version, "passwordRecoveryAvailable", recovery);
  }
}
