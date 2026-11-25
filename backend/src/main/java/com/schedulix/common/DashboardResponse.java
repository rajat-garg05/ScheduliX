package com.schedulix.common;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        String role,
        LocalDate todayDate,
        HodSummary hod,
        TeacherSummary teacher
) {
    public record HodSummary(
            int activeTeacherCount,
            int activeSubjectCount,
            int totalSectionCount,
            int savedTimetableSectionCount,
            int pendingLeaveCount,
            int todayReviewCount
    ) {
    }

    public record TeacherSummary(
            UUID teacherId,
            String teacherName,
            List<TodayTeacherLecture> todayLectures
    ) {
    }

    public record TodayTeacherLecture(
            UUID lectureId,
            String semesterName,
            String sectionName,
            String subjectCode,
            String subjectName,
            int startLecture,
            int length,
            String startTime,
            String endTime,
            String sharedPlace,
            boolean coveringForSomeone,
            boolean coveredBySomeone,
            String otherTeacherName
    ) {
    }
}