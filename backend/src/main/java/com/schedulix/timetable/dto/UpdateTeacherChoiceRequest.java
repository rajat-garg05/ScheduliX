package com.schedulix.timetable.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateTeacherChoiceRequest(
        @NotNull(message = "Please pick a section.")
        UUID sectionId,

        @NotNull(message = "Please pick a subject.")
        UUID subjectId,

        UUID teacherId
) {
}