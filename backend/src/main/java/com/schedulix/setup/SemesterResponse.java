package com.schedulix.setup;

import java.util.UUID;

public record SemesterResponse(
        UUID id,
        int number,
        String name
) {
    public static SemesterResponse from(Semester semester) {
        return new SemesterResponse(semester.getId(), semester.getNumber(), semester.getName());
    }
}