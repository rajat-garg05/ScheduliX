package com.schedulix.timetable;

public record DayPreviewSlot(
        String type,
        Integer lectureNumber,
        String label,
        String startTime,
        String endTime,
        int minutes
) {
}