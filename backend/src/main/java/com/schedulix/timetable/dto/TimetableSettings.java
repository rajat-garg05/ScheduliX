package com.schedulix.timetable.dto;

import java.util.List;

public record TimetableSettings(
        List<String> workingDays,
        String startTime,
        int lectureMinutes,
        int lecturesPerDay,
        List<BreakSetting> breaks,
        int maxPerTeacherPerDay
) {
    public record BreakSetting(
            int afterLecture,
            int minutes,
            String label
    ) {
    }
}