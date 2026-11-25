package com.schedulix.setup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubjectRequest(
        @NotBlank(message = "Please enter a subject code.")
        @Size(max = 30, message = "Subject code must be 30 characters or fewer.")
        String code,

        @NotBlank(message = "Please enter the subject name.")
        @Size(max = 150, message = "Subject name is too long.")
        String name,

        @NotNull(message = "Please choose Theory or Lab.")
        SubjectKind kind,

        @Min(value = 1, message = "Lectures in a row must be 1, 2, or 3.")
        @Max(value = 3, message = "Lectures in a row must be 1, 2, or 3.")
        Integer periodsInARow,

        @Size(max = 60, message = "Group name must be 60 characters or fewer.")
        String runTogetherGroup,

        @Size(max = 80, message = "Shared lab or place name must be 80 characters or fewer.")
        String sharedPlace
) {
}