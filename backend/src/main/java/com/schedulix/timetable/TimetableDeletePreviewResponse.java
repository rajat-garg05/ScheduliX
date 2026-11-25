package com.schedulix.timetable;

import java.util.List;
import java.util.UUID;

public record TimetableDeletePreviewResponse(
        UUID timetableId,
        String name,
        TimetableStatus status,
        List<String> sectionNames,
        List<String> teacherNames,
        int lectureCount,
        String summaryMessage
) {
}