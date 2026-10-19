package com.fintry.auth;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AuthSession(UUID id, long version) implements Principal, Serializable {
  public String getName() {
    return id.toString();
  }
}
