package com.fintry.subscription;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequiredArgsConstructor
public class SaasPaymentWebhookController {
  private final SaasPaymentService service;

  @PostMapping("/webhooks/fintry-subscription/asaas")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void webhook(
      @RequestHeader(value = "asaas-access-token", required = false) String token,
      HttpServletRequest request)
      throws java.io.IOException {
    service.authenticate(token);
    byte[] bytes = request.getInputStream().readNBytes(65537);
    if (bytes.length > 65536)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Evento muito grande.");
    try {
      var p = JsonMapper.builder().build().readTree(bytes);
      if (p == null || !p.isObject()) throw new IllegalArgumentException();
      var id = p.path("payment").path("id").asText("");
      if (!id.matches("pay_[A-Za-z0-9_-]{1,90}")) throw new IllegalArgumentException();
      service.webhook(id, p.path("payment").path("externalReference").asText(""));
    } catch (tools.jackson.core.JacksonException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento inválido.");
    }
  }
}
