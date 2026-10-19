package com.fintry.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/finance") @RequiredArgsConstructor
public class FinanceController {
    private final FinanceService service;
    @PostMapping("/sample") @ResponseStatus(HttpStatus.CREATED)
    public void sample() { service.sample(); }
    @GetMapping("/accounts") public List<FinanceService.AccountView> accounts() { return service.accounts(); }
    @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED)
    public FinanceService.AccountView account(@Valid @RequestBody FinanceService.AccountInput input) { return service.createAccount(input); }
    @GetMapping("/entries")
    public FinanceService.EntryPage list(@RequestParam(required=false) LocalDate start, @RequestParam(required=false) LocalDate end,
        @RequestParam(required=false) FinancialEntry.Type type, @RequestParam(required=false) FinancialEntry.Status status,
        @RequestParam(required=false) UUID accountId, @RequestParam(required=false) String search,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.list(start,end,type,status,accountId,search,page,size);
    }
    @PostMapping("/entries") @ResponseStatus(HttpStatus.CREATED)
    public FinanceService.EntryView entry(@Valid @RequestBody FinanceService.EntryInput input) { return service.createEntry(input); }
    @PutMapping("/entries/{id}")
    public FinanceService.EntryView update(@PathVariable UUID id,@Valid @RequestBody FinanceService.EntryInput input) { return service.updateEntry(id,input); }
    @PostMapping("/entries/{id}/pay")
    public FinanceService.EntryView pay(@PathVariable UUID id, @Valid @RequestBody FinanceService.PaymentInput input) { return service.pay(id,input); }
    @PostMapping("/entries/{id}/cancel")
    public FinanceService.EntryView cancel(@PathVariable UUID id) { return service.cancel(id); }
    @PostMapping("/transfers") @ResponseStatus(HttpStatus.CREATED)
    public void transfer(@Valid @RequestBody FinanceService.TransferInput input) { service.transfer(input); }
    @GetMapping("/summary") public FinanceService.Summary summary(@RequestParam(required=false) String month) {
        try { return service.summary(month == null ? YearMonth.now(ZoneId.of("America/Sao_Paulo")) : YearMonth.parse(month)); }
        catch (DateTimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mês inválido. Use AAAA-MM."); }
    }
}
