package com.schedulix.timetable;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record UpdateClassesRequest(
        @NotEmpty(message = "Please pick at least one section.")
        List<UUID> sectionIds
) {
}