package com.schedulix.timetable.dto;

import java.util.List;

public record TimetableMakeResponse(
        boolean ok,
        List<String> messages,
        TimetableViewResponse view
) {
}