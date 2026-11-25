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
    private final TimetableCheckService checkService;
    private final TimetableTeachersService teachersService;
    private final FixedLectureService fixedLectureService;
    private final TimetableMakeService makeService;

    public TimetableController(
            TimetableDraftService draftService,
            TimetableCheckService checkService,
            TimetableTeachersService teachersService,
            FixedLectureService fixedLectureService,
            TimetableMakeService makeService
    ) {
        this.draftService = draftService;
        this.checkService = checkService;
        this.teachersService = teachersService;
        this.fixedLectureService = fixedLectureService;
        this.makeService = makeService;
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

    @PostMapping("/{id}/check")
    public TimetableCheckResponse check(@PathVariable UUID id) {
        return checkService.check(id);
    }

    @GetMapping("/{id}/teachers")
    public TimetableTeachersResponse getTeachers(@PathVariable UUID id) {
        return teachersService.getTeachers(id);
    }

    @PostMapping("/{id}/teachers/suggest")
    public TimetableTeachersResponse suggestTeachers(@PathVariable UUID id) {
        return teachersService.suggestEmpty(id);
    }

    @PutMapping("/{id}/teachers")
    public TimetableTeachersResponse updateTeacherChoice(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTeacherChoiceRequest request
    ) {
        return teachersService.updateChoice(id, request);
    }

    @GetMapping("/{id}/fixed-lectures")
    public List<FixedLectureResponse> listFixedLectures(@PathVariable UUID id) {
        return fixedLectureService.list(id);
    }

    @PostMapping("/{id}/fixed-lectures")
    @ResponseStatus(HttpStatus.CREATED)
    public FixedLectureResponse addFixedLecture(
            @PathVariable UUID id,
            @Valid @RequestBody AddFixedLectureRequest request
    ) {
        return fixedLectureService.add(id, request);
    }

    @DeleteMapping("/{id}/fixed-lectures/{fixedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFixedLecture(
            @PathVariable UUID id,
            @PathVariable UUID fixedId
    ) {
        fixedLectureService.delete(id, fixedId);
    }

    @PostMapping("/{id}/make")
    public TimetableMakeResponse make(@PathVariable UUID id) {
        return makeService.make(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID id) {
        draftService.deleteDraft(id);
    }
}