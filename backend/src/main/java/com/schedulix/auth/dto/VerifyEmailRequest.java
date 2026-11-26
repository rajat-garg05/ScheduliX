package com.schedulix.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "The confirmation link is missing or incomplete.")
        String token
) {
}