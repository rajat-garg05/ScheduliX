package com.schedulix.timetable;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.engine.BusyCell;
import com.schedulix.engine.EngineChecks;
import com.schedulix.engine.EngineInput;
import com.schedulix.engine.Suggestion;
import com.schedulix.engine.TeacherChoice;
import com.schedulix.engine.TeacherSuggester;
import com.schedulix.setup.Section;
import com.schedulix.setup.SectionRepository;
import com.schedulix.setup.SectionSubject;
import com.schedulix.setup.SectionSubjectRepository;
import com.schedulix.setup.Subject;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRepository;
import com.schedulix.setup.Teacher;
import com.schedulix.setup.TeacherRepository;
import com.schedulix.setup.TeacherSubject;
import com.schedulix.setup.TeacherSubjectRepository;
import com.schedulix.timetable.BusyCellService.BusyCells;
import com.schedulix.timetable.TimetableTeachersResponse.TeacherAssignmentRow;
import com.schedulix.timetable.TimetableTeachersResponse.TeacherLoadSummary;
import com.schedulix.timetable.TimetableTeachersResponse.TeacherOption;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TimetableTeachersService {

    private final TimetableDraftService draftService;
    private final TimetableClassRepository timetableClassRepository;
    private final TeacherChoiceRepository teacherChoiceRepository;
    private final LectureRepository lectureRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectRepository sectionSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TimetableSettingsSupport settingsSupport;
    private final BusyCellService busyCellService;
    private final EngineInputBuilder engineInputBuilder;
    private final CurrentUser currentUser;

    public TimetableTeachersService(
            TimetableDraftService draftService,
            TimetableClassRepository timetableClassRepository,
            TeacherChoiceRepository teacherChoiceRepository,
            LectureRepository lectureRepository,
            SectionRepository sectionRepository,
            SectionSubjectRepository sectionSubjectRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            TeacherSubjectRepository teacherSubjectRepository,
            TimetableSettingsSupport settingsSupport,
            BusyCellService busyCellService,
            EngineInputBuilder engineInputBuilder,
            CurrentUser currentUser
    ) {
        this.draftService = draftService;
        this.timetableClassRepository = timetableClassRepository;
        this.teacherChoiceRepository = teacherChoiceRepository;
        this.lectureRepository = lectureRepository;
        this.sectionRepository = sectionRepository;
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.settingsSupport = settingsSupport;
        this.busyCellService = busyCellService;
        this.engineInputBuilder = engineInputBuilder;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public TimetableTeachersResponse getTeachers(UUID timetableId) {
        Timetable tt = draftService.loadOwnedDraft(timetableId, currentUser.departmentId());
        return buildResponse(tt, List.of());
    }

    @Transactional
    public TimetableTeachersResponse suggestEmpty(UUID timetableId) {
        Timetable tt = draftService.loadOwnedDraft(timetableId, currentUser.departmentId());
        EngineInput baseInput = engineInputBuilder.build(tt, false);
        Suggestion suggestion = TeacherSuggester.suggest(baseInput);

        Map<String, TeacherChoiceEntity> existingByPair = new HashMap<>();
        for (TeacherChoiceEntity c : teacherChoiceRepository.findByTimetableId(tt.getId())) {
            existingByPair.put(pairKey(c.getSectionId(), c.getSubjectId()), c);
        }

        boolean changed = false;
        for (TeacherChoice suggested : suggestion.choices()) {
            UUID secId = UUID.fromString(suggested.classId());
            UUID subId = UUID.fromString(suggested.subjectId());
            UUID teacherId = UUID.fromString(suggested.teacherId());
            String key = pairKey(secId, subId);

            if (!existingByPair.containsKey(key)) {
                TeacherChoiceEntity saved = teacherChoiceRepository.save(
                        new TeacherChoiceEntity(tt.getId(), secId, subId, teacherId)
                );
                existingByPair.put(key, saved);
                changed = true;
            }
        }

        if (changed) {
            teacherChoiceRepository.flush();
            lectureRepository.deleteByTimetableId(tt.getId());
        }

        return buildResponse(tt, suggestion.messages());
    }

    @Transactional
    public TimetableTeachersResponse updateChoice(UUID timetableId, UpdateTeacherChoiceRequest request) {
        UUID deptId = currentUser.departmentId();
        Timetable tt = draftService.loadOwnedDraft(timetableId, deptId);

        // Verify section belongs to this draft
        boolean sectionInDraft = timetableClassRepository.findByTimetableId(tt.getId()).stream()
                .anyMatch(tc -> tc.getSectionId().equals(request.sectionId()));
        if (!sectionInDraft) {
            throw new AppException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "That section is not part of this draft.");
        }

        // Verify subject is in that section's plan
        boolean subjectInPlan = sectionSubjectRepository.findBySectionId(request.sectionId()).stream()
                .anyMatch(ss -> ss.getSubjectId().equals(request.subjectId()));
        if (!subjectInPlan) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SUBJECT_NOT_IN_PLAN", "That subject is not in this section's plan.");
        }

        TeacherChoiceId choiceId = new TeacherChoiceId(tt.getId(), request.sectionId(), request.subjectId());

        if (request.teacherId() == null) {
            teacherChoiceRepository.deleteById(choiceId);
            teacherChoiceRepository.flush();
            lectureRepository.deleteByTimetableId(tt.getId());
            return buildResponse(tt, List.of());
        }

        Teacher teacher = teacherRepository.findByIdAndDepartmentId(request.teacherId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));
        if (!teacher.isActive()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "TEACHER_INACTIVE",
                    teacher.getFullName() + " is inactive and cannot be assigned."
            );
        }

        // Enforce paired-lab rule: subjects in the same run-together group for one class must have different teachers
        Subject chosenSubject = subjectRepository.findByIdAndDepartmentId(request.subjectId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SUBJECT_NOT_FOUND", "Subject not found."));
        if (chosenSubject.getKind() == SubjectKind.LAB && hasText(chosenSubject.getRunTogetherGroup())) {
            String targetGroup = normalizeKey(chosenSubject.getRunTogetherGroup());
            Map<UUID, Subject> subjectsById = new HashMap<>();
            for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
                subjectsById.put(s.getId(), s);
            }
            for (TeacherChoiceEntity existing : teacherChoiceRepository.findByTimetableId(tt.getId())) {
                if (!existing.getSectionId().equals(request.sectionId())) continue;
                if (existing.getSubjectId().equals(request.subjectId())) continue;
                if (!existing.getTeacherId().equals(teacher.getId())) continue;
                Subject peer = subjectsById.get(existing.getSubjectId());
                if (peer != null && peer.getKind() == SubjectKind.LAB
                        && hasText(peer.getRunTogetherGroup())
                        && normalizeKey(peer.getRunTogetherGroup()).equals(targetGroup)) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "PAIRED_LAB_SAME_TEACHER",
                            chosenSubject.getName() + " and " + peer.getName()
                                    + " run at the same time, so " + teacher.getFullName()
                                    + " cannot teach both."
                    );
                }
            }
        }

        TeacherChoiceEntity entity = teacherChoiceRepository.findById(choiceId)
                .orElseGet(() -> new TeacherChoiceEntity(tt.getId(), request.sectionId(), request.subjectId(), teacher.getId()));
        entity.setTeacherId(teacher.getId());
        teacherChoiceRepository.saveAndFlush(entity);

        lectureRepository.deleteByTimetableId(tt.getId());
        return buildResponse(tt, List.of());
    }

    private TimetableTeachersResponse buildResponse(Timetable tt, List<String> extraWarnings) {
        UUID deptId = tt.getDepartmentId();
        TimetableSettings settings = settingsSupport.fromJson(tt.getSettings());

        Map<UUID, Section> allSemSections = new LinkedHashMap<>();
        for (Section s : sectionRepository.findBySemesterIdOrderByNameAsc(tt.getSemesterId())) {
            allSemSections.put(s.getId(), s);
        }

        List<Section> sections = new ArrayList<>();
        for (TimetableClass tc : timetableClassRepository.findByTimetableId(tt.getId())) {
            Section sec = allSemSections.get(tc.getSectionId());
            if (sec != null) {
                sections.add(sec);
            }
        }
        sections.sort(Comparator.comparing(Section::getName, String.CASE_INSENSITIVE_ORDER));

        Map<UUID, Subject> subjectsById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectsById.put(s.getId(), s);
        }

        List<Teacher> activeTeachers = teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                .filter(Teacher::isActive)
                .toList();
        Map<UUID, Teacher> teacherById = new LinkedHashMap<>();
        List<TeacherOption> allTeacherOptions = new ArrayList<>();
        Map<UUID, Set<UUID>> ableTeachersBySubjectId = new HashMap<>();

        for (Teacher t : activeTeachers) {
            teacherById.put(t.getId(), t);
            allTeacherOptions.add(new TeacherOption(t.getId(), t.getCode(), t.getFullName()));
            for (TeacherSubject ts : teacherSubjectRepository.findByTeacherId(t.getId())) {
                ableTeachersBySubjectId.computeIfAbsent(ts.getSubjectId(), k -> new LinkedHashSet<>()).add(t.getId());
            }
        }

        Map<String, UUID> chosenTeacherByPair = new HashMap<>();
        for (TeacherChoiceEntity c : teacherChoiceRepository.findByTimetableId(tt.getId())) {
            chosenTeacherByPair.put(pairKey(c.getSectionId(), c.getSubjectId()), c.getTeacherId());
        }

        List<TeacherAssignmentRow> rows = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>(extraWarnings);
        Map<UUID, Integer> assignedLecturesByTeacher = new HashMap<>();

        for (Section sec : sections) {
            List<SectionSubject> planRows = sectionSubjectRepository.findBySectionId(sec.getId());
            List<SectionSubject> sortedPlan = new ArrayList<>(planRows);
            sortedPlan.sort(Comparator.comparing(ss -> {
                Subject s = subjectsById.get(ss.getSubjectId());
                return s != null ? s.getCode() : "";
            }, String.CASE_INSENSITIVE_ORDER));

            for (SectionSubject ss : sortedPlan) {
                Subject sub = subjectsById.get(ss.getSubjectId());
                if (sub == null) continue;

                Set<UUID> ableIds = ableTeachersBySubjectId.getOrDefault(sub.getId(), Set.of());
                UUID chosenId = chosenTeacherByPair.get(pairKey(sec.getId(), sub.getId()));
                Teacher chosen = chosenId != null ? teacherById.get(chosenId) : null;
                boolean notAble = chosen != null && !ableIds.contains(chosen.getId());

                if (chosen != null) {
                    assignedLecturesByTeacher.merge(chosen.getId(), (int) ss.getLecturesPerWeek(), Integer::sum);
                    if (notAble) {
                        warnings.add(chosen.getFullName() + " is not listed as able to teach "
                                + sub.getName() + " (Section " + sec.getName() + ").");
                    }
                }

                rows.add(new TeacherAssignmentRow(
                        sec.getId(),
                        sec.getName(),
                        sub.getId(),
                        sub.getCode(),
                        sub.getName(),
                        sub.getKind() == SubjectKind.LAB,
                        sub.getRunTogetherGroup(),
                        ss.getLecturesPerWeek(),
                        chosen != null ? chosen.getId() : null,
                        chosen != null ? chosen.getFullName() : null,
                        notAble,
                        List.copyOf(ableIds)
                ));
            }
        }

        // Compute workload totals per teacher (including saved-timetable busy cells)
        BusyCells busyCells = busyCellService.forDepartment(deptId, settings);
        int workingDays = settings.workingDays().size();
        int perDay = settings.lecturesPerDay();
        int totalSlots = workingDays * perDay;

        Map<UUID, Set<String>> busySlotsByTeacher = new HashMap<>();
        Map<UUID, int[]> busyPerDayByTeacher = new HashMap<>();
        for (BusyCell cell : busyCells.busyTeachers()) {
            try {
                UUID tId = UUID.fromString(cell.owner());
                if (cell.day() >= 0 && cell.day() < workingDays) {
                    if (busySlotsByTeacher.computeIfAbsent(tId, k -> new HashSet<>())
                            .add(cell.day() + ":" + cell.lecture())) {
                        busyPerDayByTeacher.computeIfAbsent(tId, k -> new int[workingDays])[cell.day()]++;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<TeacherLoadSummary> totals = new ArrayList<>();
        for (Teacher t : activeTeachers) {
            int assigned = assignedLecturesByTeacher.getOrDefault(t.getId(), 0);
            int busy = busySlotsByTeacher.getOrDefault(t.getId(), Set.of()).size();
            if (assigned == 0 && busy == 0) {
                continue;
            }
            int freeSlots = Math.max(0, totalSlots - busy);
            int[] busyPerDay = busyPerDayByTeacher.getOrDefault(t.getId(), new int[workingDays]);
            int maxCap = 0;
            for (int d = 0; d < workingDays; d++) {
                int dayFree = Math.max(0, perDay - busyPerDay[d]);
                int dayCap = Math.max(0, settings.maxPerTeacherPerDay() - busyPerDay[d]);
                maxCap += Math.min(dayFree, dayCap);
            }
            boolean overloaded = assigned > freeSlots || assigned > maxCap;
            totals.add(new TeacherLoadSummary(
                    t.getId(),
                    t.getCode(),
                    t.getFullName(),
                    assigned,
                    busy,
                    freeSlots,
                    maxCap,
                    overloaded
            ));
        }

        // Re-check the draft with manual overrides allowed
        EngineInput engineInput = engineInputBuilder.build(tt, true);
        List<String> errors = EngineChecks.check(engineInput);

        return new TimetableTeachersResponse(
                errors.isEmpty(),
                rows,
                allTeacherOptions,
                totals,
                errors,
                List.copyOf(warnings)
        );
    }

    private String pairKey(UUID sectionId, UUID subjectId) {
        return sectionId + "|" + subjectId;
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}