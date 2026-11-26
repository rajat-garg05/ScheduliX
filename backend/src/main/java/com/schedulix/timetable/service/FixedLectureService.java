package com.schedulix.timetable.service;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.engine.BusyCell;
import com.schedulix.engine.TimeGrid;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.SectionSubject;
import com.schedulix.setup.repository.SectionSubjectRepository;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.repository.SubjectRepository;
import com.schedulix.setup.entity.Teacher;
import com.schedulix.setup.repository.TeacherRepository;
import com.schedulix.timetable.service.BusyCellService.BusyCells;
import com.schedulix.timetable.dto.TimetableSettings.BreakSetting;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
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
import com.schedulix.timetable.dto.AddFixedLectureRequest;
import com.schedulix.timetable.entity.FixedLectureEntity;
import com.schedulix.timetable.repository.FixedLectureRepository;
import com.schedulix.timetable.dto.FixedLectureResponse;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.entity.TeacherChoiceEntity;
import com.schedulix.timetable.repository.TeacherChoiceRepository;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.entity.TimetableClass;
import com.schedulix.timetable.repository.TimetableClassRepository;
import com.schedulix.timetable.dto.TimetableSettings;

@Service
public class FixedLectureService {

    private final TimetableDraftService draftService;
    private final TimetableClassRepository timetableClassRepository;
    private final TeacherChoiceRepository teacherChoiceRepository;
    private final FixedLectureRepository fixedLectureRepository;
    private final LectureRepository lectureRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectRepository sectionSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableSettingsSupport settingsSupport;
    private final BusyCellService busyCellService;
    private final CurrentUser currentUser;

    public FixedLectureService(
            TimetableDraftService draftService,
            TimetableClassRepository timetableClassRepository,
            TeacherChoiceRepository teacherChoiceRepository,
            FixedLectureRepository fixedLectureRepository,
            LectureRepository lectureRepository,
            SectionRepository sectionRepository,
            SectionSubjectRepository sectionSubjectRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            TimetableSettingsSupport settingsSupport,
            BusyCellService busyCellService,
            CurrentUser currentUser
    ) {
        this.draftService = draftService;
        this.timetableClassRepository = timetableClassRepository;
        this.teacherChoiceRepository = teacherChoiceRepository;
        this.fixedLectureRepository = fixedLectureRepository;
        this.lectureRepository = lectureRepository;
        this.sectionRepository = sectionRepository;
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.settingsSupport = settingsSupport;
        this.busyCellService = busyCellService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<FixedLectureResponse> list(UUID timetableId) {
        Timetable tt = draftService.loadOwnedDraft(timetableId, currentUser.departmentId());
        return mapAll(tt);
    }

    @Transactional
    public FixedLectureResponse add(UUID timetableId, AddFixedLectureRequest request) {
        UUID deptId = currentUser.departmentId();
        Timetable tt = draftService.loadOwnedDraft(timetableId, deptId);
        TimetableSettings settings = settingsSupport.fromJson(tt.getSettings());

        DayOfWeek dayOfWeek;
        try {
            dayOfWeek = DayOfWeek.valueOf(request.dayOfWeek().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_DAY", "Please pick a valid working day.");
        }

        int dayIndex = settings.workingDays().indexOf(dayOfWeek.name());
        if (dayIndex < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_DAY", "That day is not a working day in these time settings.");
        }

        Map<UUID, Section> sectionsById = loadDraftSections(tt);
        Section section = sectionsById.get(request.sectionId());
        if (section == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "That section is not part of this draft.");
        }

        List<SectionSubject> sectionPlan = sectionSubjectRepository.findBySectionId(section.getId());
        SectionSubject planRow = sectionPlan.stream()
                .filter(ss -> ss.getSubjectId().equals(request.subjectId()))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "SUBJECT_NOT_IN_PLAN",
                        "That subject is not in Section " + section.getName() + "'s semester plan."
                ));

        Map<UUID, Subject> subjectsById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectsById.put(s.getId(), s);
        }
        Subject subject = subjectsById.get(request.subjectId());
        if (subject == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "SUBJECT_NOT_FOUND", "Subject not found.");
        }

        int length = Math.max(1, subject.getPeriodsInARow());
        int start = request.startLecture();

        // 1. Check day bounds and break boundaries with plain wording
        validateFitsInStretch(subject, start, length, settings);

        // Determine all subjects that run together in this piece (1 for normal, 2+ for paired lab group)
        List<Subject> pieceSubjects = resolvePieceSubjects(subject, sectionPlan, subjectsById);

        Map<String, UUID> teacherByPair = new HashMap<>();
        for (TeacherChoiceEntity c : teacherChoiceRepository.findByTimetableId(tt.getId())) {
            teacherByPair.put(pairKey(c.getSectionId(), c.getSubjectId()), c.getTeacherId());
        }

        Map<UUID, Teacher> teachersById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teachersById.put(t.getId(), t);
        }

        for (Subject ps : pieceSubjects) {
            if (!teacherByPair.containsKey(pairKey(section.getId(), ps.getId()))) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "TEACHER_NOT_CHOSEN",
                        "Choose a teacher for " + ps.getName() + " in Section " + section.getName() + " first."
                );
            }
        }

        // 2. Check existing fixed lectures for counts, per-day limit, and clashes
        List<FixedLectureEntity> existingFixed = fixedLectureRepository.findByTimetableId(tt.getId());
        String targetPieceKey = pieceKey(section.getId(), subject);
        int totalPieces = planRow.getLecturesPerWeek() / length;
        int maxPerDay = (int) Math.ceil((double) totalPieces / settings.workingDays().size());

        int existingPieceCount = 0;
        int existingDayPieceCount = 0;
        Set<String> seenExistingPieces = new HashSet<>();

        for (FixedLectureEntity fl : existingFixed) {
            Subject flSub = subjectsById.get(fl.getSubjectId());
            if (flSub == null) continue;

            int flLen = Math.max(1, flSub.getPeriodsInARow());
            String flPieceKey = pieceKey(fl.getSectionId(), flSub);
            String flExactKey = flPieceKey + "@" + fl.getDayOfWeek() + ":" + fl.getStartLecture();

            if (flPieceKey.equals(targetPieceKey)) {
                if (fl.getDayOfWeek() == dayOfWeek && fl.getStartLecture() == start) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "FIXED_DUPLICATE",
                            subject.getName() + " is already fixed at this time for Section " + section.getName() + "."
                    );
                }
                if (seenExistingPieces.add(flExactKey)) {
                    existingPieceCount++;
                    if (fl.getDayOfWeek() == dayOfWeek) {
                        existingDayPieceCount++;
                    }
                }
            }

            if (fl.getDayOfWeek() != dayOfWeek) continue;
            if (!slotsOverlap(start, length, fl.getStartLecture(), flLen)) continue;

            // Class clash
            if (fl.getSectionId().equals(section.getId())) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "FIXED_CLASS_CLASH",
                        "Section " + section.getName() + " already has " + flSub.getName() + " fixed at this time."
                );
            }

            // Teacher & shared place clashes against other sections' fixed lectures
            Section flSection = sectionsById.get(fl.getSectionId());
            String flSecName = flSection != null ? "Section " + flSection.getName() : "another class";
            List<Subject> flPieceSubjects = resolvePieceSubjects(
                    flSub,
                    sectionSubjectRepository.findBySectionId(fl.getSectionId()),
                    subjectsById
            );

            for (Subject candSub : pieceSubjects) {
                UUID candTeacherId = teacherByPair.get(pairKey(section.getId(), candSub.getId()));
                for (Subject otherSub : flPieceSubjects) {
                    UUID otherTeacherId = teacherByPair.get(pairKey(fl.getSectionId(), otherSub.getId()));
                    if (candTeacherId != null && candTeacherId.equals(otherTeacherId)) {
                        Teacher t = teachersById.get(candTeacherId);
                        String teacherName = t != null ? t.getFullName() : "This teacher";
                        throw new AppException(
                                HttpStatus.CONFLICT,
                                "FIXED_TEACHER_CLASH",
                                teacherName + " already teaches " + flSecName + " at this time."
                        );
                    }
                    if (hasText(candSub.getSharedPlace()) && hasText(otherSub.getSharedPlace())
                            && normalizeKey(candSub.getSharedPlace()).equals(normalizeKey(otherSub.getSharedPlace()))) {
                        throw new AppException(
                                HttpStatus.CONFLICT,
                                "FIXED_PLACE_CLASH",
                                candSub.getSharedPlace().trim() + " is already used by another class then."
                        );
                    }
                }
            }
        }

        if (existingPieceCount >= totalPieces) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "FIXED_LIMIT_REACHED",
                    subject.getName() + " has only " + planRow.getLecturesPerWeek()
                            + " lectures; all are already fixed."
            );
        }

        if (existingDayPieceCount >= maxPerDay) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "FIXED_DAY_LIMIT",
                    subject.getName() + " can appear at most " + maxPerDay
                            + " time" + (maxPerDay == 1 ? "" : "s") + " per day in Section " + section.getName() + "."
            );
        }

        // 3. Check saved-timetable busy cells (teachers + shared places) and teacher daily max
        BusyCells busyCells = busyCellService.forDepartment(deptId, settings);
        Set<String> busyTeacherCells = new HashSet<>();
        Map<String, Integer> busyTeacherDayCount = new HashMap<>();
        for (BusyCell cell : busyCells.busyTeachers()) {
            String key = cell.owner() + "|" + cell.day() + ":" + cell.lecture();
            if (busyTeacherCells.add(key) && cell.day() == dayIndex) {
                busyTeacherDayCount.merge(cell.owner(), 1, Integer::sum);
            }
        }
        Set<String> busyPlaceCells = new HashSet<>();
        for (BusyCell cell : busyCells.busyPlaces()) {
            if (hasText(cell.owner())) {
                busyPlaceCells.add(normalizeKey(cell.owner()) + "|" + cell.day() + ":" + cell.lecture());
            }
        }

        for (Subject candSub : pieceSubjects) {
            UUID teacherId = teacherByPair.get(pairKey(section.getId(), candSub.getId()));
            Teacher t = teacherId != null ? teachersById.get(teacherId) : null;
            String teacherName = t != null ? t.getFullName() : "This teacher";

            for (int off = 0; off < length; off++) {
                int slot = start + off;
                if (teacherId != null && busyTeacherCells.contains(teacherId + "|" + dayIndex + ":" + slot)) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "FIXED_TEACHER_BUSY",
                            teacherName + " already teaches another class in a saved timetable at this time."
                    );
                }
                if (hasText(candSub.getSharedPlace())
                        && busyPlaceCells.contains(normalizeKey(candSub.getSharedPlace()) + "|" + dayIndex + ":" + slot)) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "FIXED_PLACE_BUSY",
                            candSub.getSharedPlace().trim() + " is already used by another class then."
                    );
                }
            }

            if (teacherId != null) {
                int dayFixedForTeacher = length + busyTeacherDayCount.getOrDefault(teacherId.toString(), 0);
                for (FixedLectureEntity fl : existingFixed) {
                    if (fl.getDayOfWeek() != dayOfWeek) continue;
                    Subject flSub = subjectsById.get(fl.getSubjectId());
                    if (flSub == null) continue;
                    List<Subject> flSubs = resolvePieceSubjects(
                            flSub,
                            sectionSubjectRepository.findBySectionId(fl.getSectionId()),
                            subjectsById
                    );
                    for (Subject otherSub : flSubs) {
                        UUID otherTeacherId = teacherByPair.get(pairKey(fl.getSectionId(), otherSub.getId()));
                        if (teacherId.equals(otherTeacherId)) {
                            dayFixedForTeacher += Math.max(1, otherSub.getPeriodsInARow());
                        }
                    }
                }
                if (dayFixedForTeacher > settings.maxPerTeacherPerDay()) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "FIXED_TEACHER_DAILY_MAX",
                            teacherName + " would have " + dayFixedForTeacher
                                    + " lectures on that day, which is over the daily limit of "
                                    + settings.maxPerTeacherPerDay() + "."
                    );
                }
            }
        }

        FixedLectureEntity saved = fixedLectureRepository.save(new FixedLectureEntity(
                tt.getId(),
                section.getId(),
                subject.getId(),
                dayOfWeek,
                (short) start
        ));
        lectureRepository.deleteByTimetableId(tt.getId());

        UUID chosenTeacherId = teacherByPair.get(pairKey(section.getId(), subject.getId()));
        Teacher chosenTeacher = chosenTeacherId != null ? teachersById.get(chosenTeacherId) : null;

        return new FixedLectureResponse(
                saved.getId(),
                section.getId(),
                section.getName(),
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                chosenTeacher != null ? chosenTeacher.getFullName() : "",
                saved.getDayOfWeek().name(),
                saved.getStartLecture(),
                length,
                subject.getRunTogetherGroup()
        );
    }

    @Transactional
    public void delete(UUID timetableId, UUID fixedLectureId) {
        Timetable tt = draftService.loadOwnedDraft(timetableId, currentUser.departmentId());
        FixedLectureEntity entity = fixedLectureRepository.findByIdAndTimetableId(fixedLectureId, tt.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "FIXED_NOT_FOUND", "Fixed lecture not found."));
        fixedLectureRepository.delete(entity);
        lectureRepository.deleteByTimetableId(tt.getId());
    }

    private void validateFitsInStretch(Subject subject, int start, int length, TimetableSettings settings) {
        if (start < 1 || start + length - 1 > settings.lecturesPerDay()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "FIXED_OUT_OF_RANGE",
                    subject.getName() + " needs " + length + " lecture" + (length == 1 ? "" : "s")
                            + " in a row, which goes past the last lecture of the day."
            );
        }

        if (length > 1 && settings.breaks() != null) {
            for (BreakSetting brk : settings.breaks()) {
                if (brk.afterLecture() >= start && brk.afterLecture() < start + length - 1) {
                    String label = hasText(brk.label()) ? brk.label().trim().toLowerCase(Locale.ROOT) : "a break";
                    String subLabel = subject.getKind() == SubjectKind.LAB ? "This lab" : subject.getName();
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "FIXED_CROSSES_BREAK",
                            subLabel + " needs " + length + " lectures in a row, but lecture "
                                    + brk.afterLecture() + " is followed by " + label + "."
                    );
                }
            }
        }

        List<Integer> breakPositions = settings.breaks() == null
                ? List.of()
                : settings.breaks().stream().map(BreakSetting::afterLecture).toList();
        TimeGrid grid = TimeGrid.from(new com.schedulix.engine.EngineSettings(
                settings.workingDays().size(),
                settings.lecturesPerDay(),
                breakPositions,
                settings.maxPerTeacherPerDay()
        ));
        if (!grid.isValidStart(start, length)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "FIXED_CROSSES_BREAK",
                    subject.getName() + " cannot start at lecture " + start + " because it crosses a break."
            );
        }
    }

    private List<FixedLectureResponse> mapAll(Timetable tt) {
        UUID deptId = tt.getDepartmentId();
        Map<UUID, Section> sectionsById = loadDraftSections(tt);

        Map<UUID, Subject> subjectsById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectsById.put(s.getId(), s);
        }

        Map<UUID, Teacher> teachersById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teachersById.put(t.getId(), t);
        }

        Map<String, UUID> teacherByPair = new HashMap<>();
        for (TeacherChoiceEntity c : teacherChoiceRepository.findByTimetableId(tt.getId())) {
            teacherByPair.put(pairKey(c.getSectionId(), c.getSubjectId()), c.getTeacherId());
        }

        List<FixedLectureResponse> result = new ArrayList<>();
        for (FixedLectureEntity fl : fixedLectureRepository.findByTimetableId(tt.getId())) {
            Section sec = sectionsById.get(fl.getSectionId());
            Subject sub = subjectsById.get(fl.getSubjectId());
            if (sec == null || sub == null) continue;
            UUID tId = teacherByPair.get(pairKey(sec.getId(), sub.getId()));
            Teacher t = tId != null ? teachersById.get(tId) : null;
            result.add(new FixedLectureResponse(
                    fl.getId(),
                    sec.getId(),
                    sec.getName(),
                    sub.getId(),
                    sub.getCode(),
                    sub.getName(),
                    t != null ? t.getFullName() : "",
                    fl.getDayOfWeek().name(),
                    fl.getStartLecture(),
                    Math.max(1, sub.getPeriodsInARow()),
                    sub.getRunTogetherGroup()
            ));
        }

        result.sort(Comparator
                .comparing(FixedLectureResponse::sectionName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(r -> DayOfWeek.valueOf(r.dayOfWeek()).getValue())
                .thenComparingInt(FixedLectureResponse::startLecture));
        return result;
    }

    private Map<UUID, Section> loadDraftSections(Timetable tt) {
        Map<UUID, Section> allInSem = new LinkedHashMap<>();
        for (Section s : sectionRepository.findBySemesterIdOrderByNameAsc(tt.getSemesterId())) {
            allInSem.put(s.getId(), s);
        }
        Map<UUID, Section> inDraft = new LinkedHashMap<>();
        for (TimetableClass tc : timetableClassRepository.findByTimetableId(tt.getId())) {
            Section sec = allInSem.get(tc.getSectionId());
            if (sec != null) {
                inDraft.put(sec.getId(), sec);
            }
        }
        return inDraft;
    }

    private List<Subject> resolvePieceSubjects(Subject subject, List<SectionSubject> plan, Map<UUID, Subject> subjectsById) {
        if (subject.getKind() != SubjectKind.LAB || !hasText(subject.getRunTogetherGroup())) {
            return List.of(subject);
        }
        String groupKey = normalizeKey(subject.getRunTogetherGroup());
        List<Subject> members = new ArrayList<>();
        for (SectionSubject ss : plan) {
            Subject s = subjectsById.get(ss.getSubjectId());
            if (s != null && s.getKind() == SubjectKind.LAB && hasText(s.getRunTogetherGroup())
                    && normalizeKey(s.getRunTogetherGroup()).equals(groupKey)) {
                members.add(s);
            }
        }
        return members.isEmpty() ? List.of(subject) : members;
    }

    private boolean slotsOverlap(int startA, int lenA, int startB, int lenB) {
        return startA < startB + lenB && startB < startA + lenA;
    }

    private String pieceKey(UUID sectionId, Subject subject) {
        if (subject.getKind() == SubjectKind.LAB && hasText(subject.getRunTogetherGroup())) {
            return sectionId + "|G|" + normalizeKey(subject.getRunTogetherGroup());
        }
        return sectionId + "|S|" + subject.getId();
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