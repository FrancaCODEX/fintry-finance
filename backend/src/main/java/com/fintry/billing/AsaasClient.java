package com.fintry.billing;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AsaasClient {
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final JsonMapper json = JsonMapper.builder().build();

  protected String base(BillingConnection.Environment environment) {
    return environment == BillingConnection.Environment.SANDBOX
        ? "https://api-sandbox.asaas.com/v3"
        : "https://api.asaas.com/v3";
  }

  public JsonNode request(
      BillingConnection.Environment environment,
      String key,
      String method,
      String path,
      Object input) {
    try {
      var b =
          HttpRequest.newBuilder(URI.create(base(environment) + path))
              .timeout(Duration.ofSeconds(12))
              .header("access_token", key)
              .header("User-Agent", "Fintry/0.15")
              .header("Accept", "application/json");
      if (input != null) b.header("Content-Type", "application/json");
      var response =
          http.send(
              b.method(
                      method,
                      input == null
                          ? HttpRequest.BodyPublishers.noBody()
                          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input)))
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        byte[] bytes = stream.readNBytes(262145);
        if (bytes.length > 262144 || response.statusCode() < 200 || response.statusCode() >= 300)
          throw new ProviderFailure();
        return json.readTree(bytes);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProviderFailure();
    } catch (Exception e) {
      throw new ProviderFailure();
    }
  }

  public static class ProviderFailure extends RuntimeException {
    public ProviderFailure() {
      super(
          "Não foi possível confirmar a resposta do Asaas. Consulte a cobrança antes de tentar"
              + " outra ação.");
    }
  }

  public static String encode(String value) {
    return URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
