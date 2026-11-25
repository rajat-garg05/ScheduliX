package com.schedulix.timetable;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateDraftRequest(
        @NotNull(message = "Please pick a semester.")
        UUID semesterId,

        @NotEmpty(message = "Please pick at least one section.")
        List<UUID> sectionIds
) {
}