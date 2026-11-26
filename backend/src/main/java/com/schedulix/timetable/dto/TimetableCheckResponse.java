package com.schedulix.timetable.dto;

import java.util.List;

public record TimetableCheckResponse(
        boolean ok,
        List<CheckProblem> problems
) {
    public record CheckProblem(String text, String link) {
    }
}