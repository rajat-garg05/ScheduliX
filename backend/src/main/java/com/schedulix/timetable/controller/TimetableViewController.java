package com.schedulix.timetable.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import com.schedulix.timetable.dto.TimetableTreeResponse;
import com.schedulix.timetable.dto.TimetableViewResponse;
import com.schedulix.timetable.service.TimetableViewService;

@RestController
public class TimetableViewController {

    private final TimetableViewService viewService;

    public TimetableViewController(TimetableViewService viewService) {
        this.viewService = viewService;
    }

    @GetMapping("/api/tree")
    @PreAuthorize("hasRole('HOD')")
    public TimetableTreeResponse getTree() {
        return viewService.getTree();
    }

    @GetMapping("/api/timetables/saved")
    @PreAuthorize("hasAnyRole('HOD', 'TEACHER')")
    public TimetableTreeResponse listSaved() {
        return viewService.listSaved();
    }

    @GetMapping("/api/sections/{id}/timetable")
    @PreAuthorize("hasAnyRole('HOD', 'TEACHER')")
    public TimetableViewResponse getSectionTimetable(@PathVariable UUID id) {
        return viewService.getSectionSavedTimetable(id);
    }
}