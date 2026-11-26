package com.schedulix.leave.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import com.schedulix.leave.dto.MyTimetableResponse;
import com.schedulix.leave.service.TodayAndMyTimetableService;
import com.schedulix.leave.dto.TodayResponse;

@RestController
public class TodayAndMyTimetableController {

    private final TodayAndMyTimetableService service;

    public TodayAndMyTimetableController(TodayAndMyTimetableService service) {
        this.service = service;
    }

    @GetMapping("/api/today")
    @PreAuthorize("hasAnyRole('HOD', 'TEACHER')")
    public TodayResponse getToday(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.getToday(date);
    }

    @GetMapping("/api/my/timetable")
    @PreAuthorize("hasRole('TEACHER')")
    public MyTimetableResponse getMyTimetable() {
        return service.getMyTimetable();
    }
}