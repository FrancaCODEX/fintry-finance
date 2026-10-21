package com.fintry.billing;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BillingSecrets {
  private final String master;

  public BillingSecrets(@Value("${fintry.billing.master-key:}") String master) {
    this.master = master;
  }

  public boolean available() {
    try {
      return Base64.getDecoder().decode(master).length == 32;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private SecretKeySpec key() {
    if (!available())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "O responsável pelo servidor deve configurar BILLING_MASTER_KEY (32 bytes em Base64).");
    return new SecretKeySpec(Base64.getDecoder().decode(master), "AES");
  }

  public String encrypt(String value, UUID company, String purpose) {
    try {
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
      cipher.updateAAD((company + ":" + purpose).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(nonce)
          + "."
          + Base64.getEncoder()
              .encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Não foi possível proteger a conexão de cobrança.");
    }
  }

  public String decrypt(String value, UUID company, String purpose) {
    try {
      var parts = value.split("\\.");
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      cipher.updateAAD((company + ":" + purpose).getBytes(StandardCharsets.UTF_8));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Não foi possível abrir a conexão. Confira a chave de proteção do servidor.");
    }
  }
}
