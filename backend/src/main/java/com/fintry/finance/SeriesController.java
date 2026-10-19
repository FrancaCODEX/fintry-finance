package com.fintry.finance;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/finance/series") @RequiredArgsConstructor
public class SeriesController {
    private final SeriesService service;
    @PostMapping public ResponseEntity<SeriesService.View> create(@Valid @RequestBody SeriesService.Input input) {
        var result=service.create(input);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.series());
    }
    @GetMapping public SeriesService.PageView list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {return service.list(page,size);}
    @PostMapping("/{id}/stop") public SeriesService.View stop(@PathVariable UUID id) {return service.stop(id);}
}
