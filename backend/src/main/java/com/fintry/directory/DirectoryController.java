package com.fintry.directory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class DirectoryController {
    private final DirectoryService service;
    @GetMapping("/contacts") public DirectoryService.ContactPage contacts(@RequestParam(required=false)String search,
        @RequestParam(required=false)Contact.Kind kind,@RequestParam(required=false)Boolean active,
        @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return service.contacts(search,kind,active,page,size);
    }
    @PostMapping("/contacts") @ResponseStatus(HttpStatus.CREATED)
    public DirectoryService.ContactView createContact(@Valid @RequestBody DirectoryService.ContactInput input) { return service.saveContact(null,input); }
    @PutMapping("/contacts/{id}") public DirectoryService.ContactView updateContact(@PathVariable UUID id,@Valid @RequestBody DirectoryService.ContactInput input) { return service.saveContact(id,input); }
    @GetMapping("/categories") public List<DirectoryService.CategoryView> categories() { return service.categories(); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public DirectoryService.CategoryView createCategory(@Valid @RequestBody DirectoryService.CategoryInput input) { return service.saveCategory(null,input); }
    @PutMapping("/categories/{id}") public DirectoryService.CategoryView updateCategory(@PathVariable UUID id,@Valid @RequestBody DirectoryService.CategoryInput input) { return service.saveCategory(id,input); }
}
