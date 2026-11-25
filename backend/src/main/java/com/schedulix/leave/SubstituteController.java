package com.schedulix.leave;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('HOD')")
public class SubstituteController {

    private final SubstituteService substituteService;

    public SubstituteController(SubstituteService substituteService) {
        this.substituteService = substituteService;
    }

    @GetMapping("/api/teachers/{id}/lectures")
    public List<TeacherDateLectureResponse> getTeacherLecturesOnDate(
            @PathVariable UUID id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return substituteService.getTeacherLecturesOnDate(id, date);
    }

    @PostMapping("/api/substitutes/prepare")
    public PrepareSubstitutesResponse prepareForDate(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return substituteService.prepareForDate(date);
    }
}