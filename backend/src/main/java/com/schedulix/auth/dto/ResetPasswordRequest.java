package com.schedulix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "The reset link is missing or incomplete.")
        String token,

        @NotBlank(message = "Please enter a new password.")
        @Size(min = 10, max = 100, message = "Password must be at least 10 characters long.")
        String newPassword
) {
}