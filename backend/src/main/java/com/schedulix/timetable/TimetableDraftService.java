package com.schedulix.timetable;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.history.HistoryService;
import com.schedulix.setup.SavedTimetableLookup;
import com.schedulix.setup.Section;
import com.schedulix.setup.SectionRepository;
import com.schedulix.setup.SectionSubjectRepository;
import com.schedulix.setup.Semester;
import com.schedulix.setup.SemesterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TimetableDraftService {

    public static final String DEFAULT_SETTINGS_JSON = """
            {"workingDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],"startTime":"09:00","lectureMinutes":50,"lecturesPerDay":6,"breaks":[{"afterLecture":3,"minutes":45,"label":"Lunch"}],"maxPerTeacherPerDay":5}""";

    private final TimetableRepository timetableRepository;
    private final TimetableClassRepository timetableClassRepository;
    private final TeacherChoiceRepository teacherChoiceRepository;
    private final FixedLectureRepository fixedLectureRepository;
    private final LectureRepository lectureRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectRepository sectionSubjectRepository;
    private final SavedTimetableLookup savedTimetableLookup;
    private final TimetableSettingsSupport settingsSupport;
    private final CurrentUser currentUser;
    private final HistoryService historyService;

    public TimetableDraftService(
            TimetableRepository timetableRepository,
            TimetableClassRepository timetableClassRepository,
            TeacherChoiceRepository teacherChoiceRepository,
            FixedLectureRepository fixedLectureRepository,
            LectureRepository lectureRepository,
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            SectionSubjectRepository sectionSubjectRepository,
            SavedTimetableLookup savedTimetableLookup,
            TimetableSettingsSupport settingsSupport,
            CurrentUser currentUser,
            HistoryService historyService
    ) {
        this.timetableRepository = timetableRepository;
        this.timetableClassRepository = timetableClassRepository;
        this.teacherChoiceRepository = teacherChoiceRepository;
        this.fixedLectureRepository = fixedLectureRepository;
        this.lectureRepository = lectureRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.savedTimetableLookup = savedTimetableLookup;
        this.settingsSupport = settingsSupport;
        this.currentUser = currentUser;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public List<TimetableSummaryResponse> list(TimetableStatus status) {
        UUID deptId = currentUser.departmentId();
        List<Timetable> timetables = status == null
                ? timetableRepository.findByDepartmentIdOrderByCreatedAtDesc(deptId)
                : timetableRepository.findByDepartmentIdAndStatusOrderByCreatedAtDesc(deptId, status);

        if (timetables.isEmpty()) {
            return List.of();
        }

        Map<UUID, Semester> semestersById = new LinkedHashMap<>();
        for (Semester s : semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId)) {
            semestersById.put(s.getId(), s);
        }

        List<TimetableSummaryResponse> result = new ArrayList<>();
        for (Timetable tt : timetables) {
            Semester sem = semestersById.get(tt.getSemesterId());
            List<String> sectionNames = loadSectionNames(tt.getId(), tt.getSemesterId());
            result.add(new TimetableSummaryResponse(
                    tt.getId(),
                    tt.getSemesterId(),
                    sem != null ? sem.getName() : "",
                    tt.getName(),
                    tt.getStatus(),
                    sectionNames,
                    tt.getCreatedAt(),
                    tt.getSavedAt()
            ));
        }
        return result;
    }

    @Transactional
    public TimetableDraftResponse createDraft(CreateDraftRequest request) {
        UUID deptId = currentUser.departmentId();
        Semester semester = semesterRepository.findByIdAndDepartmentId(request.semesterId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found."));

        List<Section> chosenSections = validateAndResolveSections(semester, request.sectionIds());
        String draftName = buildTimetableName(semester.getName(), chosenSections);

        Timetable timetable = timetableRepository.save(new Timetable(
                deptId,
                semester.getId(),
                draftName,
                TimetableStatus.DRAFT,
                DEFAULT_SETTINGS_JSON,
                currentUser.userId(),
                Instant.now()
        ));

        for (Section sec : chosenSections) {
            timetableClassRepository.save(new TimetableClass(timetable.getId(), sec.getId(), false));
        }

        historyService.record("TIMETABLE", "Started a new timetable draft: " + draftName + ".");
        return toDraftResponse(timetable, semester, chosenSections);
    }

    @Transactional(readOnly = true)
    public TimetableDraftResponse getDraft(UUID timetableId) {
        UUID deptId = currentUser.departmentId();
        Timetable tt = loadOwnedTimetable(timetableId, deptId);
        Semester sem = semesterRepository.findByIdAndDepartmentId(tt.getSemesterId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found."));
        List<Section> sections = loadSectionsForTimetable(tt.getId(), tt.getSemesterId());
        return toDraftResponse(tt, sem, sections);
    }

    @Transactional
    public TimetableSettingsResponse updateSettings(UUID timetableId, TimetableSettings request) {
        Timetable tt = loadOwnedDraft(timetableId, currentUser.departmentId());
        TimetableSettings normalized = settingsSupport.validateAndNormalize(request);
        String nextJson = settingsSupport.toJson(normalized);

        if (!nextJson.equals(tt.getSettings())) {
            tt.setSettings(nextJson);
            // Changing time settings invalidates previously pinned fixed lectures and generated lectures.
            fixedLectureRepository.deleteByTimetableId(tt.getId());
            lectureRepository.deleteByTimetableId(tt.getId());
        }

        return new TimetableSettingsResponse(normalized, settingsSupport.buildDayPreview(normalized));
    }

    @Transactional
    public void deleteDraft(UUID timetableId) {
        Timetable tt = loadOwnedDraft(timetableId, currentUser.departmentId());
        String name = tt.getName();
        timetableRepository.delete(tt);
        historyService.record("TIMETABLE", "Deleted unfinished timetable draft: " + name + ".");
    }

    List<Section> validateAndResolveSections(Semester semester, List<UUID> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NO_SECTIONS", "Please pick at least one section.");
        }

        Map<UUID, Section> byId = new LinkedHashMap<>();
        for (Section sec : sectionRepository.findBySemesterIdOrderByNameAsc(semester.getId())) {
            byId.put(sec.getId(), sec);
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(sectionIds);
        List<Section> chosen = new ArrayList<>();
        for (UUID secId : uniqueIds) {
            Section sec = byId.get(secId);
            if (sec == null) {
                throw new AppException(
                        HttpStatus.NOT_FOUND,
                        "SECTION_NOT_FOUND",
                        "One of the chosen sections does not belong to " + semester.getName() + "."
                );
            }
            if (savedTimetableLookup.hasSavedTimetable(sec.getId())) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "SECTION_HAS_SAVED_TIMETABLE",
                        "Section " + sec.getName()
                                + " already has a saved timetable. Delete its saved timetable first."
                );
            }
            if (sectionSubjectRepository.findBySectionId(sec.getId()).isEmpty()) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "SECTION_NO_PLAN",
                        "Section " + sec.getName() + " has no semester plan yet. Add its semester plan first."
                );
            }
            chosen.add(sec);
        }

        chosen.sort(Comparator.comparing(Section::getName, String.CASE_INSENSITIVE_ORDER));
        return chosen;
    }

    String buildTimetableName(String semesterName, List<Section> sections) {
        List<String> names = sections.stream().map(Section::getName).toList();
        String label = names.size() == 1
                ? "Section " + names.get(0)
                : "Sections " + String.join(", ", names);
        return semesterName + " - " + label;
    }

    Timetable loadOwnedTimetable(UUID timetableId, UUID deptId) {
        return timetableRepository.findByIdAndDepartmentId(timetableId, deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TIMETABLE_NOT_FOUND", "Timetable not found."));
    }

    Timetable loadOwnedDraft(UUID timetableId, UUID deptId) {
        Timetable tt = loadOwnedTimetable(timetableId, deptId);
        if (tt.getStatus() != TimetableStatus.DRAFT) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "TIMETABLE_ALREADY_SAVED",
                    "This timetable is already saved and cannot be changed."
            );
        }
        return tt;
    }

    private List<Section> loadSectionsForTimetable(UUID timetableId, UUID semesterId) {
        Map<UUID, Section> allInSem = new LinkedHashMap<>();
        for (Section s : sectionRepository.findBySemesterIdOrderByNameAsc(semesterId)) {
            allInSem.put(s.getId(), s);
        }
        List<Section> result = new ArrayList<>();
        for (TimetableClass tc : timetableClassRepository.findByTimetableId(timetableId)) {
            Section sec = allInSem.get(tc.getSectionId());
            if (sec != null) {
                result.add(sec);
            }
        }
        result.sort(Comparator.comparing(Section::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private List<String> loadSectionNames(UUID timetableId, UUID semesterId) {
        return loadSectionsForTimetable(timetableId, semesterId).stream()
                .map(Section::getName)
                .toList();
    }

    private TimetableDraftResponse toDraftResponse(Timetable tt, Semester sem, List<Section> sections) {
        List<TimetableDraftResponse.DraftSection> secDtos = sections.stream()
                .map(s -> new TimetableDraftResponse.DraftSection(s.getId(), s.getName()))
                .toList();

        return new TimetableDraftResponse(
                tt.getId(),
                tt.getSemesterId(),
                sem.getName(),
                tt.getName(),
                tt.getStatus(),
                secDtos,
                tt.getSettings(),
                teacherChoiceRepository.findByTimetableId(tt.getId()).size(),
                fixedLectureRepository.findByTimetableId(tt.getId()).size(),
                lectureRepository.findByTimetableId(tt.getId()).size(),
                tt.getCreatedAt(),
                tt.getSavedAt()
        );
    }
}