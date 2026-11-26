package com.schedulix.leave.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.entity.SubstituteStatus;

public record TodayResponse(
        LocalDate date,
        String dayOfWeek,
        int reviewCount,
        UUID myTeacherId,
        List<TodaySection> sections
) {
    public record TodaySection(
            UUID sectionId,
            String sectionName,
            String semesterName,
            List<TodayLecture> lectures
    ) {
    }

    public record TodayLecture(
            UUID lectureId,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            boolean lab,
            String sharedPlace,
            int startLecture,
            int length,
            String startTime,
            String endTime,
            UUID originalTeacherId,
            String originalTeacherName,
            UUID substituteId,
            UUID substituteTeacherId,
            String substituteTeacherName,
            SubstituteStatus substituteStatus,
            UUID groupKey
    ) {
    }
}