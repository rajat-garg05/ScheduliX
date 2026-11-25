package com.schedulix.engine;

// day is 0-based within working days; startLecture is 1-based.
public record FixedLecture(String classId, String subjectId, int day, int startLecture) {
}