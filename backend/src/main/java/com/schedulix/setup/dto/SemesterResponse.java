package com.schedulix.setup.dto;

import java.util.UUID;
import com.schedulix.setup.entity.Semester;

public record SemesterResponse(
        UUID id,
        int number,
        String name
) {
    public static SemesterResponse from(Semester semester) {
        return new SemesterResponse(semester.getId(), semester.getNumber(), semester.getName());
    }
}