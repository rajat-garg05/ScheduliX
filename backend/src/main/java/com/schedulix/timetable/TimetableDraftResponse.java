package com.schedulix.timetable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableDraftResponse(
        UUID id,
        UUID semesterId,
        String semesterName,
        String name,
        TimetableStatus status,
        List<DraftSection> sections,
        String settingsJson,
        int teacherChoiceCount,
        int fixedLectureCount,
        int lectureCount,
        Instant createdAt,
        Instant savedAt
) {
    public record DraftSection(UUID id, String name) {
    }
}