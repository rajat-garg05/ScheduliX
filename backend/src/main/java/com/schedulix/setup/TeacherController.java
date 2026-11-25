package com.schedulix.setup;

import com.schedulix.auth.MessageResponse;
import com.schedulix.common.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/teachers")
@PreAuthorize("hasRole('HOD')")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping
    public PageResponse<TeacherResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return teacherService.list(search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherResponse create(@Valid @RequestBody TeacherRequest request) {
        return teacherService.create(request);
    }

    @PutMapping("/{id}")
    public TeacherResponse update(@PathVariable UUID id, @Valid @RequestBody TeacherRequest request) {
        return teacherService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public TeacherResponse deactivate(@PathVariable UUID id) {
        return teacherService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public TeacherResponse activate(@PathVariable UUID id) {
        return teacherService.activate(id);
    }

    @PostMapping("/{id}/reset-password")
    public MessageResponse resetPassword(@PathVariable UUID id) {
        teacherService.resetPassword(id);
        return new MessageResponse("Password reset to the default password. The teacher must change it at next login.");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        teacherService.delete(id);
    }
}