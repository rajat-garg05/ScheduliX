package com.schedulix.timetable.dto;

import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.entity.TimetableStatus;

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