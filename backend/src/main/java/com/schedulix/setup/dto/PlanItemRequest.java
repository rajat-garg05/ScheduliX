package com.schedulix.setup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlanItemRequest(
        @NotNull(message = "Please pick a subject.")
        UUID subjectId,

        @Min(value = 1, message = "Lectures per week must be at least 1.")
        @Max(value = 40, message = "Lectures per week must be 40 or fewer.")
        int lecturesPerWeek
) {
}