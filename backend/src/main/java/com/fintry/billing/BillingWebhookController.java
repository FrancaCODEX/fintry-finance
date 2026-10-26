package com.fintry.billing;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequiredArgsConstructor
public class BillingWebhookController {
  private final BillingService service;

  @PostMapping("/webhooks/asaas/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void webhook(
      @PathVariable UUID id,
      @RequestHeader(value = "asaas-access-token", required = false) String token,
      HttpServletRequest request)
      throws java.io.IOException {
    var bytes = request.getInputStream().readNBytes(65537);
    if (bytes.length > 65536)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Evento muito grande.");
    try {
      var p = JsonMapper.builder().build().readTree(bytes);
      if (p == null || !p.isObject()) throw new IllegalArgumentException();
      var eventId = p.path("id").asText("");
      var providerId = p.path("payment").path("id").asText("");
      if (!eventId.matches("[A-Za-z0-9_-]{1,100}")
          || !providerId.matches("pay_[A-Za-z0-9_-]{1,90}")) throw new IllegalArgumentException();
      service.webhook(id, token, eventId, providerId);
    } catch (tools.jackson.core.JacksonException | IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento inválido.");
    }
  }
}
