package com.fintry.commerce;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommerceController {
  private final CommerceService service;

  @GetMapping("/products")
  public CommerceService.ProductPage products(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "false") boolean low,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.products(search, active, low, page, size);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public CommerceService.ProductView create(
      @Valid @RequestBody CommerceService.ProductInput input) {
    return service.save(null, input);
  }

  @PutMapping("/products/{id}")
  public CommerceService.ProductView update(
      @PathVariable UUID id, @Valid @RequestBody CommerceService.ProductInput input) {
    return service.save(id, input);
  }

  @GetMapping("/stock/movements")
  public CommerceService.MovementPage movements(
      @RequestParam(required = false) UUID productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.movements(productId, page, size);
  }

  @PostMapping("/stock/movements")
  public CommerceService.MovementView movement(
      @Valid @RequestBody CommerceService.MovementInput input) {
    return service.movement(input);
  }

  @GetMapping("/sales")
  public CommerceService.SalePage sales(
      @RequestParam(required = false) Sale.Status status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.sales(status, search, page, size);
  }

  @GetMapping("/sales/{id}")
  public CommerceService.SaleView detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/sales")
  public ResponseEntity<CommerceService.SaleView> create(
      @Valid @RequestBody CommerceService.DraftInput input) {
    var result = service.draft(input);
    return ResponseEntity.status(result.created() ? 201 : 200).body(result.sale());
  }

  @PostMapping("/sales/{id}/confirm")
  public CommerceService.SaleView confirm(
      @PathVariable UUID id, @Valid @RequestBody CommerceService.ConfirmInput input) {
    return service.confirm(id, input);
  }

  @PostMapping("/sales/{id}/cancel")
  public CommerceService.SaleView cancel(@PathVariable UUID id) {
    return service.cancel(id);
  }
}
