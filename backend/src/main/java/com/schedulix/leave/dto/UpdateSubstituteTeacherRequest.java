package com.schedulix.leave.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateSubstituteTeacherRequest(
        @NotNull(message = "Please pick a teacher.")
        UUID teacherId
) {
}