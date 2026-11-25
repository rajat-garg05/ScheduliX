package com.schedulix.timetable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableSummaryResponse(
        UUID id,
        UUID semesterId,
        String semesterName,
        String name,
        TimetableStatus status,
        List<String> sectionNames,
        Instant createdAt,
        Instant savedAt
) {
}