package com.fintry.auth;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class TeamController {
  private final TeamService service;

  @GetMapping
  public TeamService.Result list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) AppUser.Role role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, role, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<AccessFlows.Invitation> invite(
      @Valid @RequestBody TeamService.InviteInput input) {
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(service.invite(input));
  }

  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void update(@PathVariable UUID id, @Valid @RequestBody TeamService.UpdateInput input) {
    service.update(id, input);
  }

  @PostMapping("/{id}/invitation")
  public ResponseEntity<AccessFlows.Invitation> reinvite(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reinvite(id));
  }

  @DeleteMapping("/{id}/invitation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID id) {
    service.revoke(id);
  }
}
