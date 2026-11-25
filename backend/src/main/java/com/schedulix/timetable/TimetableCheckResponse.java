package com.schedulix.timetable;

import java.util.List;

public record TimetableCheckResponse(
        boolean ok,
        List<CheckProblem> problems
) {
    public record CheckProblem(String text, String link) {
    }
}