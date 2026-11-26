package com.schedulix.setup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SemesterRequest(
        @NotNull(message = "Please choose a semester number between 1 and 12.")
        @Min(value = 1, message = "Semester number must be between 1 and 12.")
        @Max(value = 12, message = "Semester number must be between 1 and 12.")
        Integer number,

        @NotBlank(message = "Please enter a semester name.")
        @Size(max = 50, message = "Semester name must be 50 characters or fewer.")
        String name
) {
}