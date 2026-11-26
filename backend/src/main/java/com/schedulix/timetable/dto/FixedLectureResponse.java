package com.schedulix.timetable.dto;

import java.util.UUID;

public record FixedLectureResponse(
        UUID id,
        UUID sectionId,
        String sectionName,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        String teacherName,
        String dayOfWeek,
        int startLecture,
        int length,
        String runTogetherGroup
) {
}