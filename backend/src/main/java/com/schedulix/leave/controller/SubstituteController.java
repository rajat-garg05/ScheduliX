package com.schedulix.leave.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.dto.AcceptAllSubstitutesResponse;
import com.schedulix.leave.dto.PrepareSubstitutesResponse;
import com.schedulix.leave.dto.SubstituteArrangementResponse;
import com.schedulix.leave.dto.SubstituteOptionResponse;
import com.schedulix.leave.service.SubstituteService;
import com.schedulix.leave.dto.TeacherDateLectureResponse;
import com.schedulix.leave.dto.UpdateSubstituteTeacherRequest;

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

    @GetMapping("/api/substitutes/{id}/options")
    public List<SubstituteOptionResponse> getOptions(@PathVariable UUID id) {
        return substituteService.getOptions(id);
    }

    @PutMapping("/api/substitutes/{id}/teacher")
    public SubstituteArrangementResponse updateTeacher(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubstituteTeacherRequest request
    ) {
        return substituteService.updateTeacher(id, request.teacherId());
    }

    @PostMapping("/api/substitutes/{id}/accept")
    public SubstituteArrangementResponse accept(@PathVariable UUID id) {
        return substituteService.accept(id);
    }

    @PostMapping("/api/substitutes/accept-all")
    public AcceptAllSubstitutesResponse acceptAll(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return substituteService.acceptAll(date);
    }
}