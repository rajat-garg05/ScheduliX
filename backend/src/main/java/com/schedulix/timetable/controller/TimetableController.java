package com.schedulix.timetable.controller;

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
import com.schedulix.timetable.dto.AddFixedLectureRequest;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.dto.FixedLectureResponse;
import com.schedulix.timetable.service.FixedLectureService;
import com.schedulix.timetable.dto.KeepLectureRequest;
import com.schedulix.timetable.dto.TimetableCheckResponse;
import com.schedulix.timetable.service.TimetableCheckService;
import com.schedulix.timetable.dto.TimetableDeletePreviewResponse;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.service.TimetableDraftService;
import com.schedulix.timetable.service.TimetableLifecycleService;
import com.schedulix.timetable.dto.TimetableMakeResponse;
import com.schedulix.timetable.service.TimetableMakeService;
import com.schedulix.timetable.dto.TimetableSettings;
import com.schedulix.timetable.dto.TimetableSettingsResponse;
import com.schedulix.timetable.entity.TimetableStatus;
import com.schedulix.timetable.dto.TimetableSummaryResponse;
import com.schedulix.timetable.dto.TimetableTeachersResponse;
import com.schedulix.timetable.service.TimetableTeachersService;
import com.schedulix.timetable.dto.TimetableViewResponse;
import com.schedulix.timetable.service.TimetableViewService;
import com.schedulix.timetable.dto.UpdateClassesRequest;
import com.schedulix.timetable.dto.UpdateTeacherChoiceRequest;

@RestController
@RequestMapping("/api/timetables")
@PreAuthorize("hasRole('HOD')")
public class TimetableController {

    private final TimetableDraftService draftService;
    private final TimetableCheckService checkService;
    private final TimetableTeachersService teachersService;
    private final FixedLectureService fixedLectureService;
    private final TimetableMakeService makeService;
    private final TimetableLifecycleService lifecycleService;
    private final TimetableViewService viewService;

    public TimetableController(
            TimetableDraftService draftService,
            TimetableCheckService checkService,
            TimetableTeachersService teachersService,
            FixedLectureService fixedLectureService,
            TimetableMakeService makeService,
            TimetableLifecycleService lifecycleService,
            TimetableViewService viewService
    ) {
        this.draftService = draftService;
        this.checkService = checkService;
        this.teachersService = teachersService;
        this.fixedLectureService = fixedLectureService;
        this.makeService = makeService;
        this.lifecycleService = lifecycleService;
        this.viewService = viewService;
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

    @GetMapping("/{id}/view")
    public TimetableViewResponse getView(
            @PathVariable UUID id,
            @RequestParam(value = "classId", required = false) UUID classId,
            @RequestParam(value = "teacherId", required = false) UUID teacherId
    ) {
        return viewService.getView(id, classId, teacherId);
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

    @PostMapping("/{id}/make-again")
    public TimetableMakeResponse makeAgain(@PathVariable UUID id) {
        return makeService.makeAgain(id);
    }

    @PutMapping("/{id}/lectures/{lectureId}/keep")
    public TimetableViewResponse keepLecture(
            @PathVariable UUID id,
            @PathVariable UUID lectureId,
            @RequestBody KeepLectureRequest request
    ) {
        return makeService.setLectureKept(id, lectureId, request.kept());
    }

    @PostMapping("/{id}/keep-all")
    public TimetableViewResponse keepAll(@PathVariable UUID id) {
        return makeService.keepAll(id);
    }

    @PostMapping("/{id}/save")
    public TimetableViewResponse save(@PathVariable UUID id) {
        return lifecycleService.save(id);
    }

    @GetMapping("/{id}/delete-preview")
    public TimetableDeletePreviewResponse deletePreview(@PathVariable UUID id) {
        return lifecycleService.deletePreview(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        lifecycleService.delete(id);
    }
}