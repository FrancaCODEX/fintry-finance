package com.fintry.attachment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/finance/entries/{entryId}/attachments")
public class AttachmentController {
    private final AttachmentService service;
    @GetMapping public List<AttachmentService.View> list(@PathVariable UUID entryId){return service.list(entryId);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AttachmentService.View upload(@PathVariable UUID entryId,@RequestParam MultipartFile file){return service.upload(entryId,file);}
    @GetMapping("/{id}") public ResponseEntity<byte[]> download(@PathVariable UUID entryId,@PathVariable UUID id){
        var d=service.download(entryId,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(d.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.filename(),StandardCharsets.UTF_8).build().toString()).body(d.content());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID entryId,@PathVariable UUID id){service.delete(entryId,id);}
}
