package com.schedulix.timetable;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.engine.EngineChecks;
import com.schedulix.engine.EngineInput;
import com.schedulix.engine.PlacedLecture;
import com.schedulix.history.HistoryService;
import com.schedulix.setup.Section;
import com.schedulix.setup.SectionRepository;
import com.schedulix.setup.Subject;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRepository;
import com.schedulix.setup.Teacher;
import com.schedulix.setup.TeacherRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TimetableLifecycleService {

    private final TimetableDraftService draftService;
    private final TimetableMakeService makeService;
    private final TimetableRepository timetableRepository;
    private final TimetableClassRepository timetableClassRepository;
    private final LectureRepository lectureRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableSettingsSupport settingsSupport;
    private final EngineInputBuilder engineInputBuilder;
    private final CurrentUser currentUser;
    private final HistoryService historyService;

    public TimetableLifecycleService(
            TimetableDraftService draftService,
            TimetableMakeService makeService,
            TimetableRepository timetableRepository,
            TimetableClassRepository timetableClassRepository,
            LectureRepository lectureRepository,
            SectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            TimetableSettingsSupport settingsSupport,
            EngineInputBuilder engineInputBuilder,
            CurrentUser currentUser,
            HistoryService historyService
    ) {
        this.draftService = draftService;
        this.makeService = makeService;
        this.timetableRepository = timetableRepository;
        this.timetableClassRepository = timetableClassRepository;
        this.lectureRepository = lectureRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.settingsSupport = settingsSupport;
        this.engineInputBuilder = engineInputBuilder;
        this.currentUser = currentUser;
        this.historyService = historyService;
    }

    @Transactional
    public TimetableViewResponse save(UUID timetableId) {
        UUID deptId = currentUser.departmentId();
        Timetable tt = draftService.loadOwnedDraft(timetableId, deptId);

        List<TimetableClass> classes = timetableClassRepository.findByTimetableId(tt.getId());
        if (classes.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NO_CLASSES", "No classes are selected yet.");
        }

        List<Lecture> storedLectures = lectureRepository.findByTimetableId(tt.getId());
        if (storedLectures.isEmpty()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "DRAFT_NOT_MADE",
                    "Make the timetable first before saving it."
            );
        }

        // Check that none of the sections already got a saved timetable meanwhile
        Map<UUID, Section> sectionsById = new HashMap<>();
        for (Section s : sectionRepository.findBySemesterIdOrderByNameAsc(tt.getSemesterId())) {
            sectionsById.put(s.getId(), s);
        }
        for (TimetableClass tc : classes) {
            if (timetableClassRepository.existsBySectionIdAndSavedTrue(tc.getSectionId())) {
                Section sec = sectionsById.get(tc.getSectionId());
                String name = sec != null ? "Section " + sec.getName() : "One of the selected sections";
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "SECTION_ALREADY_SAVED",
                        name + " already has a saved timetable. Delete its saved timetable first."
                );
            }
        }

        // Fresh busy-cell verification against all currently saved timetables
        TimetableSettings settings = settingsSupport.fromJson(tt.getSettings());
        Map<DayOfWeek, Integer> dayIndexByDay = new HashMap<>();
        for (int i = 0; i < settings.workingDays().size(); i++) {
            dayIndexByDay.put(
                    DayOfWeek.valueOf(settings.workingDays().get(i).trim().toUpperCase(Locale.ROOT)),
                    i
            );
        }

        Map<UUID, Subject> subjectById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectById.put(s.getId(), s);
        }

        List<PlacedLecture> placedLectures = new ArrayList<>(storedLectures.size());
        for (Lecture lec : storedLectures) {
            Integer dayIdx = dayIndexByDay.get(lec.getDayOfWeek());
            if (dayIdx == null) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "SAVE_CONFLICT",
                        "The timetable no longer matches the working days. Please make the timetable again."
                );
            }
            Subject sub = subjectById.get(lec.getSubjectId());
            String groupName = (sub != null && sub.getKind() == SubjectKind.LAB
                    && sub.getRunTogetherGroup() != null && !sub.getRunTogetherGroup().isBlank())
                    ? sub.getRunTogetherGroup().trim()
                    : null;

            placedLectures.add(new PlacedLecture(
                    lec.getSectionId().toString(),
                    lec.getSubjectId().toString(),
                    lec.getTeacherId().toString(),
                    dayIdx,
                    lec.getStartLecture(),
                    lec.getLength(),
                    groupName
            ));
        }

        EngineInput freshInput = engineInputBuilder.build(tt, true);
        List<String> verifyProblems = EngineChecks.verify(freshInput, placedLectures);
        if (!verifyProblems.isEmpty()) {
            String first = verifyProblems.get(0);
            String message = first.contains("already busy in another saved timetable")
                    ? first.replace("is already busy in another saved timetable", "is now used by another saved timetable")
                            + " Please make the timetable again."
                    : first + " Please make the timetable again.";
            throw new AppException(HttpStatus.CONFLICT, "SAVE_CONFLICT", message);
        }

        // Save atomically in one transaction (unique index uq_timetable_class_saved_section enforces 1 per section)
        try {
            tt.setStatus(TimetableStatus.SAVED);
            tt.setSavedAt(Instant.now());
            timetableRepository.save(tt);

            for (TimetableClass tc : classes) {
                tc.setSaved(true);
            }
            timetableClassRepository.saveAll(classes);
            timetableClassRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SECTION_ALREADY_SAVED",
                    "One of the selected classes already has a saved timetable. Delete it first."
            );
        }

        historyService.record("TIMETABLE", "Saved the timetable for " + tt.getName() + ".");
        return makeService.buildView(tt, null, null);
    }

    @Transactional(readOnly = true)
    public TimetableDeletePreviewResponse deletePreview(UUID timetableId) {
        UUID deptId = currentUser.departmentId();
        Timetable tt = draftService.loadOwnedTimetable(timetableId, deptId);

        Map<UUID, Section> semSections = new LinkedHashMap<>();
        for (Section s : sectionRepository.findBySemesterIdOrderByNameAsc(tt.getSemesterId())) {
            semSections.put(s.getId(), s);
        }

        List<String> sectionNames = new ArrayList<>();
        for (TimetableClass tc : timetableClassRepository.findByTimetableId(tt.getId())) {
            Section sec = semSections.get(tc.getSectionId());
            if (sec != null) {
                sectionNames.add(sec.getName());
            }
        }
        sectionNames.sort(String.CASE_INSENSITIVE_ORDER);

        Map<UUID, Teacher> teacherById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teacherById.put(t.getId(), t);
        }

        List<Lecture> lectures = lectureRepository.findByTimetableId(tt.getId());
        Set<String> teacherNameSet = new LinkedHashSet<>();
        for (Lecture lec : lectures) {
            Teacher t = teacherById.get(lec.getTeacherId());
            if (t != null) {
                teacherNameSet.add(t.getFullName());
            }
        }
        List<String> teacherNames = new ArrayList<>(teacherNameSet);
        teacherNames.sort(String.CASE_INSENSITIVE_ORDER);

        String summary = buildDeleteSummary(tt.getStatus(), sectionNames, teacherNames);

        return new TimetableDeletePreviewResponse(
                tt.getId(),
                tt.getName(),
                tt.getStatus(),
                sectionNames,
                teacherNames,
                lectures.size(),
                summary
        );
    }

    @Transactional
    public void delete(UUID timetableId) {
        Timetable tt = draftService.loadOwnedTimetable(timetableId, currentUser.departmentId());
        String name = tt.getName();
        boolean wasSaved = tt.getStatus() == TimetableStatus.SAVED;

        timetableRepository.delete(tt);
        timetableRepository.flush();

        if (wasSaved) {
            historyService.record("TIMETABLE", "Deleted saved timetable for " + name + ".");
        } else {
            historyService.record("TIMETABLE", "Deleted unfinished timetable: " + name + ".");
        }
    }

    private String buildDeleteSummary(TimetableStatus status, List<String> sections, List<String> teachers) {
        if (status == TimetableStatus.DRAFT) {
            return "This removes the unfinished timetable, its teacher choices, and any fixed lectures.";
        }
        String teacherPart = teachers.isEmpty()
                ? "Teachers will be free in these slots again."
                : formatNameList(teachers) + " will be free in these slots again.";
        String sectionPart = sections.size() == 1
                ? "The plan for Section " + sections.get(0) + " becomes editable."
                : "The plans for these sections become editable.";
        return teacherPart + " " + sectionPart + " Leave arrangements for these lectures will be removed.";
    }

    private String formatNameList(List<String> names) {
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}