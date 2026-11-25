package com.schedulix.setup;

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
@RequestMapping("/api/subjects")
@PreAuthorize("hasRole('HOD')")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public PageResponse<SubjectResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return subjectService.list(search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
        return subjectService.create(request);
    }

    @PutMapping("/{id}")
    public SubjectResponse update(@PathVariable UUID id, @Valid @RequestBody SubjectRequest request) {
        return subjectService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public SubjectResponse deactivate(@PathVariable UUID id) {
        return subjectService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public SubjectResponse activate(@PathVariable UUID id) {
        return subjectService.activate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        subjectService.delete(id);
    }
}