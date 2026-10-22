package com.fintry.audit;

import com.fintry.auth.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {
  private final CurrentUser current;
  private final AuditRepository events;
  private final JsonMapper json = JsonMapper.builder().build();

  public record Change(String field, String before, String after) {}

  public record View(
      UUID id,
      String actorName,
      String action,
      String resource,
      UUID resourceId,
      String label,
      Instant occurredAt,
      List<Change> changes) {}

  public record Result(List<View> content, long totalElements, int totalPages, int number) {}

  public static Map<String, String> fields(Object... pairs) {
    var out = new LinkedHashMap<String, String>();
    for (int i = 0; i < pairs.length; i += 2)
      out.put(pairs[i].toString(), pairs[i + 1] == null ? "" : pairs[i + 1].toString());
    return out;
  }

  @Transactional
  public void change(
      String action,
      String resource,
      UUID id,
      String label,
      Map<String, String> before,
      Map<String, String> after) {
    changeAs(current.get(), action, resource, id, label, before, after);
  }

  @Transactional
  public void changeAs(
      AppUser actor,
      String action,
      String resource,
      UUID id,
      String label,
      Map<String, String> before,
      Map<String, String> after) {
    var changes = new ArrayList<Change>();
    var keys = new LinkedHashSet<>(before.keySet());
    keys.addAll(after.keySet());
    for (var key : keys)
      if (!Objects.equals(before.getOrDefault(key, ""), after.getOrDefault(key, "")))
        changes.add(new Change(key, before.getOrDefault(key, ""), after.getOrDefault(key, "")));
    if (changes.isEmpty()) return;
    save(
        actor.getCompanyId(), actor.getId(), actor.getName(), action, resource, id, label, changes);
  }

  @Transactional
  public void eventAs(AppUser actor, String action, String resource, UUID id, String label) {
    save(
        actor.getCompanyId(),
        actor.getId(),
        actor.getName(),
        action,
        resource,
        id,
        label,
        List.of());
  }

  @Transactional
  public void system(UUID company, String action, UUID userId, String label) {
    save(company, null, "Recuperação de acesso", action, "ACCESS", userId, label, List.of());
  }

  @Transactional
  public void billingSystem(UUID company, UUID id, String label, String before, String after) {
    save(
        company,
        null,
        "Integração Asaas",
        "BILLING_STATUS",
        "BILLING",
        id,
        label,
        List.of(new Change("Situação", before, after)));
  }

  @Transactional
  public void subscriptionPayment(UUID company, UUID id, String before, String after) {
    save(
        company,
        null,
        "Cobrança do Fintry",
        "SUBSCRIPTION_PAYMENT",
        "SUBSCRIPTION",
        id,
        "Mensalidade do Fintry",
        List.of(new Change("Situação", before, after)));
  }

  @Transactional
  public void subscriptionRenewal(UUID company, UUID id, String label) {
    save(
        company,
        null,
        "Renovação do Fintry",
        "SUBSCRIPTION_RENEWAL",
        "SUBSCRIPTION",
        id,
        label,
        List.of());
  }

  private void save(
      UUID company,
      UUID actor,
      String name,
      String action,
      String resource,
      UUID id,
      String label,
      List<Change> changes) {
    var e = new AuditEvent();
    e.setCompanyId(company);
    e.setActorId(actor);
    e.setActorName(name);
    e.setAction(action);
    e.setResource(resource);
    e.setResourceId(id);
    e.setLabel(label);
    e.setOccurredAt(Instant.now());
    e.setChangesJson(json.writeValueAsString(changes));
    events.save(e);
  }

  public Result list(
      LocalDate start, LocalDate end, String resource, String search, int page, int size) {
    current.requireAdmin();
    if (start == null
        || end == null
        || start.isAfter(end)
        || ChronoUnit.DAYS.between(start, end) >= 366
        || page < 0
        || size < 1
        || size > 100
        || (search != null && search.length() > 120))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Confira o período (até 366 dias), a busca e a paginação.");
    UUID company = current.companyId();
    var zone = ZoneId.of("America/Sao_Paulo");
    Specification<AuditEvent> spec =
        (root, q, cb) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(cb.equal(root.get("companyId"), company));
          p.add(
              cb.greaterThanOrEqualTo(
                  root.get("occurredAt"), start.atStartOfDay(zone).toInstant()));
          p.add(
              cb.lessThan(root.get("occurredAt"), end.plusDays(1).atStartOfDay(zone).toInstant()));
          if (resource != null && !resource.isBlank())
            p.add(cb.equal(root.get("resource"), resource));
          if (search != null && !search.isBlank()) {
            String term =
                "%"
                    + search
                        .strip()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                    + "%";
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("label")), term, '!'),
                    cb.like(cb.lower(root.get("actorName")), term, '!')));
          }
          return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result =
        events.findAll(
            spec,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
    return new Result(
        result.stream()
            .map(
                e ->
                    new View(
                        e.getId(),
                        e.getActorName(),
                        e.getAction(),
                        e.getResource(),
                        e.getResourceId(),
                        e.getLabel(),
                        e.getOccurredAt(),
                        json.readValue(
                            e.getChangesJson(),
                            json.getTypeFactory()
                                .constructCollectionType(List.class, Change.class))))
            .toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page);
  }
}
