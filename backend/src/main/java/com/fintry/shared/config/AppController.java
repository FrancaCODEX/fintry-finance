package com.fintry.shared.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class AppController {
    @Value("${fintry.version:development}") private String version;
    @GetMapping("/api/v1/app") public Map<String,String> info(){return Map.of("name","Fintry","version",version);}
}
