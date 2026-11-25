package com.schedulix.timetable;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.engine.EngineInput;
import com.schedulix.engine.EngineResult;
import com.schedulix.engine.FixedLecture;
import com.schedulix.engine.PlacedLecture;
import com.schedulix.engine.TimetableEngine;
import com.schedulix.setup.Section;
import com.schedulix.setup.SectionRepository;
import com.schedulix.setup.Semester;
import com.schedulix.setup.SemesterRepository;
import com.schedulix.setup.Subject;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRepository;
import com.schedulix.setup.Teacher;
import com.schedulix.setup.TeacherRepository;
import com.schedulix.timetable.TimetableSettingsSupport.LectureInterval;
import com.schedulix.timetable.TimetableViewResponse.ViewLecture;
import com.schedulix.timetable.TimetableViewResponse.ViewSection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TimetableMakeService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Set<UUID> activeDepartmentMakes = ConcurrentHashMap.newKeySet();

    private final TimetableDraftService draftService;
    private final TimetableRepository timetableRepository;
    private final TimetableClassRepository timetableClassRepository;
    private final FixedLectureRepository fixedLectureRepository;
    private final LectureRepository lectureRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableSettingsSupport settingsSupport;
    private final EngineInputBuilder engineInputBuilder;
    private final CurrentUser currentUser;

    public TimetableMakeService(
            TimetableDraftService draftService,
            TimetableRepository timetableRepository,
            TimetableClassRepository timetableClassRepository,
            FixedLectureRepository fixedLectureRepository,
            LectureRepository lectureRepository,
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            TimetableSettingsSupport settingsSupport,
            EngineInputBuilder engineInputBuilder,
            CurrentUser currentUser
    ) {
        this.draftService = draftService;
        this.timetableRepository = timetableRepository;
        this.timetableClassRepository = timetableClassRepository;
        this.fixedLectureRepository = fixedLectureRepository;
        this.lectureRepository = lectureRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.settingsSupport = settingsSupport;
        this.engineInputBuilder = engineInputBuilder;
        this.currentUser = currentUser;
    }

    @Transactional
    public TimetableMakeResponse make(UUID timetableId) {
        return runMake(timetableId, false);
    }

    @Transactional
    public TimetableMakeResponse runMake(UUID timetableId, boolean incrementVariation) {
        UUID deptId = currentUser.departmentId();
        if (!activeDepartmentMakes.add(deptId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "MAKE_IN_PROGRESS",
                    "A timetable is already being made. Please wait a moment."
            );
        }

        try {
            Timetable tt = draftService.loadOwnedDraft(timetableId, deptId);
            TimetableSettings settings = settingsSupport.fromJson(tt.getSettings());

            int variation = tt.getVariation();
            if (incrementVariation) {
                variation += 1;
                tt.setVariation(variation);
                timetableRepository.save(tt);
            }

            EngineInput baseInput = engineInputBuilder.build(tt, true);
            List<Lecture> existingLectures = lectureRepository.findByTimetableId(tt.getId());

            Map<DayOfWeek, Integer> dayIndexByDay = new HashMap<>();
            List<DayOfWeek> workingDays = new ArrayList<>();
            for (int i = 0; i < settings.workingDays().size(); i++) {
                DayOfWeek day = DayOfWeek.valueOf(settings.workingDays().get(i).trim().toUpperCase(Locale.ROOT));
                dayIndexByDay.put(day, i);
                workingDays.add(day);
            }

            // Include kept lectures as fixed lectures so they never move
            List<FixedLecture> combinedFixed = new ArrayList<>(baseInput.fixed());
            Set<String> seenFixedKeys = new HashSet<>();
            for (FixedLecture fl : combinedFixed) {
                seenFixedKeys.add(fl.classId() + "|" + fl.subjectId() + "@" + fl.day() + ":" + fl.startLecture());
            }

            Set<String> keptSlotKeys = new HashSet<>();
            for (Lecture lec : existingLectures) {
                if (!lec.isKept()) continue;
                Integer dayIdx = dayIndexByDay.get(lec.getDayOfWeek());
                if (dayIdx == null) continue;

                String exact = lec.getSectionId() + "|" + lec.getSubjectId() + "@" + dayIdx + ":" + lec.getStartLecture();
                keptSlotKeys.add(exact);
                if (seenFixedKeys.add(exact)) {
                    combinedFixed.add(new FixedLecture(
                            lec.getSectionId().toString(),
                            lec.getSubjectId().toString(),
                            dayIdx,
                            lec.getStartLecture()
                    ));
                }
            }

            EngineInput input = new EngineInput(
                    baseInput.settings(),
                    baseInput.classes(),
                    baseInput.subjects(),
                    baseInput.plan(),
                    baseInput.teachers(),
                    baseInput.choices(),
                    combinedFixed,
                    baseInput.busyTeachers(),
                    baseInput.busyPlaces()
            );

            EngineResult result = TimetableEngine.make(input, variation);
            if (!result.ok()) {
                return new TimetableMakeResponse(false, result.messages(), null);
            }

            // Store new lecture rows with real clock times and shared group_key UUIDs for paired labs
            lectureRepository.deleteByTimetableId(tt.getId());
            lectureRepository.flush();

            Map<Integer, LectureInterval> intervals = settingsSupport.lectureIntervals(settings);
            Map<String, UUID> groupKeyBySlot = new HashMap<>();
            List<Lecture> toSave = new ArrayList<>(result.lectures().size());

            for (PlacedLecture placed : result.lectures()) {
                DayOfWeek dayOfWeek = workingDays.get(placed.day());
                LectureInterval firstInterval = intervals.get(placed.startLecture());
                LectureInterval lastInterval = intervals.get(placed.startLecture() + placed.length() - 1);
                LocalTime startTime = firstInterval.startTime();
                LocalTime endTime = lastInterval.endTime();

                UUID groupUuid = null;
                if (placed.groupKey() != null && !placed.groupKey().isBlank()) {
                    String gk = placed.classId() + "|" + placed.day() + "|" + placed.startLecture()
                            + "|" + placed.groupKey().trim().toLowerCase(Locale.ROOT);
                    groupUuid = groupKeyBySlot.computeIfAbsent(gk, k -> UUID.randomUUID());
                }

                String exact = placed.classId() + "|" + placed.subjectId() + "@" + placed.day() + ":" + placed.startLecture();
                boolean kept = keptSlotKeys.contains(exact);

                toSave.add(new Lecture(
                        tt.getId(),
                        UUID.fromString(placed.classId()),
                        UUID.fromString(placed.subjectId()),
                        UUID.fromString(placed.teacherId()),
                        dayOfWeek,
                        (short) placed.startLecture(),
                        (short) placed.length(),
                        startTime,
                        endTime,
                        kept,
                        groupUuid
                ));
            }

            lectureRepository.saveAll(toSave);
            lectureRepository.flush();

            TimetableViewResponse view = buildView(tt, null, null);
            return new TimetableMakeResponse(true, List.of(), view);
        } finally {
            activeDepartmentMakes.remove(deptId);
        }
    }

    @Transactional(readOnly = true)
    public TimetableViewResponse buildView(Timetable tt, UUID filterSectionId, UUID filterTeacherId) {
        UUID deptId = tt.getDepartmentId();
        TimetableSettings settings = settingsSupport.fromJson(tt.getSettings());
        List<DayPreviewSlot> dayPreview = settingsSupport.buildDayPreview(settings);

        Semester semester = semesterRepository.findByIdAndDepartmentId(tt.getSemesterId(), deptId).orElse(null);

        Map<UUID, Section> allSemSections = new LinkedHashMap<>();
        for (Section s : sectionRepository.findBySemesterIdOrderByNameAsc(tt.getSemesterId())) {
            allSemSections.put(s.getId(), s);
        }

        List<Section> sections = new ArrayList<>();
        Map<UUID, Section> sectionById = new HashMap<>();
        for (TimetableClass tc : timetableClassRepository.findByTimetableId(tt.getId())) {
            Section sec = allSemSections.get(tc.getSectionId());
            if (sec != null) {
                sections.add(sec);
                sectionById.put(sec.getId(), sec);
            }
        }
        sections.sort(Comparator.comparing(Section::getName, String.CASE_INSENSITIVE_ORDER));

        Map<UUID, Subject> subjectById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectById.put(s.getId(), s);
        }

        Map<UUID, Teacher> teacherById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teacherById.put(t.getId(), t);
        }

        // Identify fixed lectures from step 5 (including paired-lab group siblings at the same slot)
        Set<String> fixedExactKeys = new HashSet<>();
        Set<String> fixedGroupKeys = new HashSet<>();
        for (FixedLectureEntity fl : fixedLectureRepository.findByTimetableId(tt.getId())) {
            fixedExactKeys.add(fl.getSectionId() + "|" + fl.getSubjectId() + "@" + fl.getDayOfWeek() + ":" + fl.getStartLecture());
            Subject sub = subjectById.get(fl.getSubjectId());
            if (sub != null && sub.getKind() == SubjectKind.LAB
                    && sub.getRunTogetherGroup() != null && !sub.getRunTogetherGroup().isBlank()) {
                fixedGroupKeys.add(fl.getSectionId() + "|" + sub.getRunTogetherGroup().trim().toLowerCase(Locale.ROOT)
                        + "@" + fl.getDayOfWeek() + ":" + fl.getStartLecture());
            }
        }

        List<ViewLecture> viewLectures = new ArrayList<>();
        for (Lecture lec : lectureRepository.findByTimetableId(tt.getId())) {
            if (filterSectionId != null && !lec.getSectionId().equals(filterSectionId)) continue;
            if (filterTeacherId != null && !lec.getTeacherId().equals(filterTeacherId)) continue;

            Section sec = sectionById.get(lec.getSectionId());
            Subject sub = subjectById.get(lec.getSubjectId());
            Teacher teacher = teacherById.get(lec.getTeacherId());
            if (sec == null || sub == null || teacher == null) continue;

            boolean isFixed = fixedExactKeys.contains(
                    lec.getSectionId() + "|" + lec.getSubjectId() + "@" + lec.getDayOfWeek() + ":" + lec.getStartLecture()
            );
            if (!isFixed && sub.getKind() == SubjectKind.LAB
                    && sub.getRunTogetherGroup() != null && !sub.getRunTogetherGroup().isBlank()) {
                isFixed = fixedGroupKeys.contains(
                        lec.getSectionId() + "|" + sub.getRunTogetherGroup().trim().toLowerCase(Locale.ROOT)
                                + "@" + lec.getDayOfWeek() + ":" + lec.getStartLecture()
                );
            }

            viewLectures.add(new ViewLecture(
                    lec.getId(),
                    sec.getId(),
                    sec.getName(),
                    sub.getId(),
                    sub.getCode(),
                    sub.getName(),
                    sub.getKind() == SubjectKind.LAB,
                    sub.getSharedPlace(),
                    teacher.getId(),
                    teacher.getFullName(),
                    lec.getDayOfWeek().name(),
                    lec.getStartLecture(),
                    lec.getLength(),
                    lec.getStartTime().format(HH_MM),
                    lec.getEndTime().format(HH_MM),
                    lec.isKept(),
                    isFixed,
                    lec.getGroupKey()
            ));
        }

        viewLectures.sort(Comparator
                .comparing(ViewLecture::sectionName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(l -> DayOfWeek.valueOf(l.dayOfWeek()).getValue())
                .thenComparingInt(ViewLecture::startLecture)
                .thenComparing(ViewLecture::subjectCode, String.CASE_INSENSITIVE_ORDER));

        List<ViewSection> viewSections = sections.stream()
                .map(s -> new ViewSection(s.getId(), s.getName()))
                .toList();

        return new TimetableViewResponse(
                tt.getId(),
                tt.getSemesterId(),
                semester != null ? semester.getName() : "",
                tt.getName(),
                tt.getStatus(),
                tt.getSavedAt(),
                settings,
                dayPreview,
                viewSections,
                viewLectures
        );
    }

    // Package-private helper for testing the per-department lock directly
    boolean tryAcquireDepartmentLock(UUID departmentId) {
        return activeDepartmentMakes.add(departmentId);
    }

    void releaseDepartmentLock(UUID departmentId) {
        activeDepartmentMakes.remove(departmentId);
    }
}