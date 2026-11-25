package com.schedulix.engine;

public record PlacedLecture(
        String classId,
        String subjectId,
        String teacherId,
        int day,
        int startLecture,
        int length,
        String groupKey
) {
}