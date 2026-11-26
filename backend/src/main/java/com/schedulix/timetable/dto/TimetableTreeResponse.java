package com.schedulix.timetable.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableTreeResponse(List<TreeSemester> semesters) {

    public record TreeSemester(
            UUID semesterId,
            int semesterNumber,
            String semesterName,
            List<TreeSection> sections
    ) {
    }

    public record TreeSection(
            UUID sectionId,
            String sectionName,
            SavedTimetableCard savedTimetable
    ) {
    }

    public record SavedTimetableCard(
            UUID timetableId,
            String timetableName,
            Instant savedAt,
            int teacherCount,
            int lectureCount
    ) {
    }
}