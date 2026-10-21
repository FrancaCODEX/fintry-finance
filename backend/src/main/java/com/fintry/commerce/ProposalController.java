package com.fintry.commerce;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
public class ProposalController {
  private final ProposalService service;
  private final ProposalPdf pdf;

  @GetMapping
  public ProposalService.Page list(
      @RequestParam(required = false) Proposal.Status status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "false") boolean expired,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(status, search, expired, page, size);
  }

  @GetMapping("/{id}")
  public ProposalService.View detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping
  public ResponseEntity<ProposalService.View> create(
      @Valid @RequestBody ProposalService.Input input) {
    var result = service.create(input);
    return ResponseEntity.status(result.created() ? 201 : 200).body(result.proposal());
  }

  @PutMapping("/{id}")
  public ProposalService.View edit(
      @PathVariable UUID id, @Valid @RequestBody ProposalService.Edit input) {
    return service.edit(id, input);
  }

  @PostMapping("/{id}/status")
  public ProposalService.View status(
      @PathVariable UUID id, @Valid @RequestBody ProposalService.Transition input) {
    return service.transition(id, input);
  }

  @PostMapping("/{id}/approve")
  public ProposalService.View approve(
      @PathVariable UUID id, @Valid @RequestBody ProposalService.Approval input) {
    return service.approve(id, input);
  }

  @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(@PathVariable UUID id) throws java.io.IOException {
    var p = service.detail(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=fintry-proposta-" + p.number() + ".pdf")
        .body(pdf.export(p));
  }
}
