package com.schedulix.setup.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import com.schedulix.setup.dto.SubjectResponse;
import com.schedulix.setup.dto.SubjectTeachersRequest;
import com.schedulix.setup.dto.TeacherResponse;
import com.schedulix.setup.service.TeacherSubjectService;
import com.schedulix.setup.dto.TeacherSubjectsRequest;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('HOD')")
public class TeacherSubjectController {

    private final TeacherSubjectService teacherSubjectService;

    public TeacherSubjectController(TeacherSubjectService teacherSubjectService) {
        this.teacherSubjectService = teacherSubjectService;
    }

    @GetMapping("/teachers/{id}/subjects")
    public List<SubjectResponse> getSubjectsForTeacher(@PathVariable UUID id) {
        return teacherSubjectService.getSubjectsForTeacher(id);
    }

    @PutMapping("/teachers/{id}/subjects")
    public List<SubjectResponse> replaceSubjectsForTeacher(
            @PathVariable UUID id,
            @RequestBody TeacherSubjectsRequest request
    ) {
        return teacherSubjectService.replaceSubjectsForTeacher(id, request.subjectIds());
    }

    @GetMapping("/subjects/{id}/teachers")
    public List<TeacherResponse> getTeachersForSubject(@PathVariable UUID id) {
        return teacherSubjectService.getTeachersForSubject(id);
    }

    @PutMapping("/subjects/{id}/teachers")
    public List<TeacherResponse> replaceTeachersForSubject(
            @PathVariable UUID id,
            @RequestBody SubjectTeachersRequest request
    ) {
        return teacherSubjectService.replaceTeachersForSubject(id, request.teacherIds());
    }
}