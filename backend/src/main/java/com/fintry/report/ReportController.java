package com.fintry.report;
import com.fintry.finance.FinancialEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/v1/reports") @RequiredArgsConstructor
public class ReportController {
    private final ReportService service;
    private final ReportExporter exporter;
    @GetMapping public ReportService.Report report(@RequestParam LocalDate start,@RequestParam LocalDate end,
        @RequestParam(defaultValue="PAYMENT") ReportService.Basis basis,@RequestParam(required=false) FinancialEntry.Status status,
        @RequestParam(required=false) FinancialEntry.Type type,@RequestParam(required=false)UUID accountId,
        @RequestParam(required=false)String category,@RequestParam(required=false)String contact,
        @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return service.report(new ReportService.Filters(start,end,basis,status,type,accountId,category,contact),page,size);
    }
    @GetMapping("/export") public ResponseEntity<byte[]> export(@RequestParam LocalDate start,@RequestParam LocalDate end,
        @RequestParam(defaultValue="PAYMENT") ReportService.Basis basis,@RequestParam(required=false) FinancialEntry.Status status,
        @RequestParam(required=false) FinancialEntry.Type type,@RequestParam(required=false)UUID accountId,
        @RequestParam(required=false)String category,@RequestParam(required=false)String contact,@RequestParam String format) throws java.io.IOException {
        var data=service.export(new ReportService.Filters(start,end,basis,status,type,accountId,category,contact));
        byte[] bytes;String mime;
        switch(format) {
            case "csv" -> {bytes=exporter.csv(data);mime="text/csv;charset=UTF-8";}
            case "xlsx" -> {bytes=exporter.xlsx(data);mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";}
            case "pdf" -> {bytes=exporter.pdf(data);mime="application/pdf";}
            default -> throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Formato inválido. Use csv, xlsx ou pdf.");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("fintry-relatorio-"+start+"-"+end+"."+format).build().toString()).body(bytes);
    }
    @GetMapping("/forecast") public ReportService.Forecast forecast(@RequestParam(defaultValue="90")int days,
        @RequestParam(required=false)UUID accountId,@RequestParam(defaultValue="false")boolean includeOverdue) {
        return service.forecast(days,accountId,includeOverdue);
    }
}
