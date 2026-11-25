package com.schedulix.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Please enter your current password.")
        String currentPassword,

        @NotBlank(message = "Please enter a new password.")
        @Size(min = 10, max = 100, message = "Password must be at least 10 characters long.")
        String newPassword
) {
}