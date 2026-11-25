package com.schedulix.timetable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddFixedLectureRequest(
        @NotNull(message = "Please pick a section.")
        UUID sectionId,

        @NotNull(message = "Please pick a subject.")
        UUID subjectId,

        @NotBlank(message = "Please pick a day.")
        String dayOfWeek,

        @Min(value = 1, message = "Starting lecture must be at least 1.")
        int startLecture
) {
}