package com.schedulix.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectionRequest(
        @NotBlank(message = "Please enter a section name.")
        @Size(max = 30, message = "Section name must be 30 characters or fewer.")
        String name
) {
}