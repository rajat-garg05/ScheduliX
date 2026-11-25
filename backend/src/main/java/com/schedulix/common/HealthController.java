package com.schedulix.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    public record HealthResponse(String status) {
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }
}