package com.schedulix.timetable.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.entity.TimetableStatus;

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