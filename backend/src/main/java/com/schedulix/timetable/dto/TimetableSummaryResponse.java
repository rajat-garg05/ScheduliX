package com.schedulix.timetable.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.entity.TimetableStatus;

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