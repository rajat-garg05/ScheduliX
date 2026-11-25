package com.schedulix.timetable;

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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/timetables")
@PreAuthorize("hasRole('HOD')")
public class TimetableController {

    private final TimetableDraftService draftService;

    public TimetableController(TimetableDraftService draftService) {
        this.draftService = draftService;
    }

    @GetMapping
    public List<TimetableSummaryResponse> list(
            @RequestParam(value = "status", required = false) TimetableStatus status
    ) {
        return draftService.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimetableDraftResponse createDraft(@Valid @RequestBody CreateDraftRequest request) {
        return draftService.createDraft(request);
    }

    @GetMapping("/{id}")
    public TimetableDraftResponse getDraft(@PathVariable UUID id) {
        return draftService.getDraft(id);
    }

    @PutMapping("/{id}/classes")
    public TimetableDraftResponse updateClasses(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassesRequest request
    ) {
        return draftService.updateClasses(id, request);
    }

    @PutMapping("/{id}/settings")
    public TimetableSettingsResponse updateSettings(
            @PathVariable UUID id,
            @RequestBody TimetableSettings request
    ) {
        return draftService.updateSettings(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID id) {
        draftService.deleteDraft(id);
    }
}