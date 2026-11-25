package com.schedulix.engine;

import java.util.List;

public record EngineSettings(
        int workingDays,
        int lecturesPerDay,
        List<Integer> breakAfterLecture,
        int maxPerTeacherPerDay
) {
    public EngineSettings {
        breakAfterLecture = breakAfterLecture == null ? List.of() : List.copyOf(breakAfterLecture);
    }
}