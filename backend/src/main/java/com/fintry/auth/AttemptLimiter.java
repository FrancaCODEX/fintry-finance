package com.fintry.auth;

import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AttemptLimiter {
  private record Bucket(Instant until, int count) {}

  private final Map<String, Bucket> buckets = new LinkedHashMap<>();

  public synchronized void take(String key, int max, Duration window) {
    var now = Instant.now();
    buckets.entrySet().removeIf(e -> !e.getValue().until().isAfter(now));
    var b = buckets.getOrDefault(key, new Bucket(now.plus(window), 0));
    if (b.count() >= max)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
    if (buckets.size() >= 5000 && !buckets.containsKey(key))
      buckets.remove(buckets.keySet().iterator().next());
    buckets.put(key, new Bucket(b.until(), b.count() + 1));
  }
}
