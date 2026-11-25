package com.schedulix.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Please enter your department name.")
        @Size(max = 150, message = "Department name is too long.")
        String departmentName,

        @NotBlank(message = "Please enter your email address.")
        @Email(message = "Please enter a valid email address.")
        @Size(max = 254, message = "Email address is too long.")
        String email,

        @NotBlank(message = "Please choose a password.")
        @Size(min = 10, max = 100, message = "Password must be at least 10 characters long.")
        String password
) {
}