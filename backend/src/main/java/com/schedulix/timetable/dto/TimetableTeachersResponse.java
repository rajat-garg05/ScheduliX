package com.schedulix.timetable.dto;

import java.util.List;
import java.util.UUID;

public record TimetableTeachersResponse(
        boolean ready,
        List<TeacherAssignmentRow> rows,
        List<TeacherOption> allTeachers,
        List<TeacherLoadSummary> totals,
        List<String> errors,
        List<String> warnings
) {
    public record TeacherAssignmentRow(
            UUID sectionId,
            String sectionName,
            UUID subjectId,
            String subjectCode,
            String subjectName,
            boolean lab,
            String runTogetherGroup,
            int lecturesPerWeek,
            UUID teacherId,
            String teacherName,
            boolean notListedAsAble,
            List<UUID> ableTeacherIds
    ) {
    }

    public record TeacherOption(UUID id, String code, String fullName) {
    }

    public record TeacherLoadSummary(
            UUID teacherId,
            String teacherCode,
            String teacherName,
            int assignedLectures,
            int busyLectures,
            int freeSlots,
            int maxCapacity,
            boolean overloaded
    ) {
    }
}