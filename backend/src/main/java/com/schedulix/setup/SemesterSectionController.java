package com.schedulix.setup;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('HOD')")
public class SemesterSectionController {

    private final SemesterSectionService service;

    public SemesterSectionController(SemesterSectionService service) {
        this.service = service;
    }

    @GetMapping("/semesters")
    public List<SemesterResponse> listSemesters() {
        return service.listSemesters();
    }

    @PostMapping("/semesters")
    @ResponseStatus(HttpStatus.CREATED)
    public SemesterResponse createSemester(@Valid @RequestBody SemesterRequest request) {
        return service.createSemester(request);
    }

    @PutMapping("/semesters/{id}")
    public SemesterResponse updateSemester(
            @PathVariable UUID id,
            @Valid @RequestBody SemesterRequest request
    ) {
        return service.updateSemester(id, request);
    }

    @DeleteMapping("/semesters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSemester(@PathVariable UUID id) {
        service.deleteSemester(id);
    }

    @GetMapping("/semesters/{id}/sections")
    public List<SectionResponse> listSections(@PathVariable UUID id) {
        return service.listSections(id);
    }

    @PostMapping("/semesters/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionResponse createSection(
            @PathVariable UUID id,
            @Valid @RequestBody SectionRequest request
    ) {
        return service.createSection(id, request);
    }

    @PutMapping("/sections/{id}")
    public SectionResponse updateSection(
            @PathVariable UUID id,
            @Valid @RequestBody SectionRequest request
    ) {
        return service.updateSection(id, request);
    }

    @DeleteMapping("/sections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(@PathVariable UUID id) {
        service.deleteSection(id);
    }
}