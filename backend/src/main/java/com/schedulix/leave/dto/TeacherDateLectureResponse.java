package com.schedulix.leave.dto;

import java.util.UUID;

public record TeacherDateLectureResponse(
        UUID lectureId,
        UUID sectionId,
        String sectionName,
        String semesterName,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        int startLecture,
        int length,
        String startTime,
        String endTime
) {
}