package com.schedulix.timetable.dto;

public record DayPreviewSlot(
        String type,
        Integer lectureNumber,
        String label,
        String startTime,
        String endTime,
        int minutes
) {
}