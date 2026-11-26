package com.schedulix.leave.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MyTimetableResponse(
        UUID teacherId,
        String teacherName,
        List<MyWeeklyLecture> weeklyLectures,
        List<UpcomingSubstituteItem> coveringForOthers,
        List<UpcomingSubstituteItem> othersCoveringForMe
) {
    public record MyWeeklyLecture(
            UUID lectureId,
            UUID sectionId,
            String sectionName,
            String semesterName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            boolean lab,
            String sharedPlace,
            String dayOfWeek,
            int startLecture,
            int length,
            String startTime,
            String endTime
    ) {
    }

    public record UpcomingSubstituteItem(
            UUID substituteId,
            LocalDate onDate,
            String sectionName,
            String semesterName,
            String subjectCode,
            String subjectName,
            int startLecture,
            int length,
            String startTime,
            String endTime,
            String otherTeacherName
    ) {
    }
}