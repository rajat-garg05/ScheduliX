package com.schedulix.timetable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableViewResponse(
        UUID timetableId,
        UUID semesterId,
        String semesterName,
        String name,
        TimetableStatus status,
        Instant savedAt,
        TimetableSettings settings,
        List<DayPreviewSlot> dayPreview,
        List<ViewSection> sections,
        List<ViewLecture> lectures
) {
    public record ViewSection(UUID id, String name) {
    }

    public record ViewLecture(
            UUID id,
            UUID sectionId,
            String sectionName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            boolean lab,
            String sharedPlace,
            UUID teacherId,
            String teacherName,
            String dayOfWeek,
            int startLecture,
            int length,
            String startTime,
            String endTime,
            boolean kept,
            boolean fixed,
            UUID groupKey
    ) {
    }
}