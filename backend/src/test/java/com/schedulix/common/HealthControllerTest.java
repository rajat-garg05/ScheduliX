package com.schedulix.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private MockMvc mockMvc;

    @RestController
    static class FailingController {
        @GetMapping("/api/test-error")
        void fail() {
            throw new AppException(HttpStatus.BAD_REQUEST, "SAMPLE_ERROR", "Please fix the sample value.");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(), new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void appExceptionReturnsApiErrorShape() throws Exception {
        mockMvc.perform(get("/api/test-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAMPLE_ERROR"))
                .andExpect(jsonPath("$.message").value("Please fix the sample value."));
    }
}