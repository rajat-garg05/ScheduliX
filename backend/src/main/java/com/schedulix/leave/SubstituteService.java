package com.schedulix.leave;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.history.HistoryService;
import com.schedulix.notice.NotificationService;
import com.schedulix.setup.Section;
import com.schedulix.setup.SectionRepository;
import com.schedulix.setup.Semester;
import com.schedulix.setup.SemesterRepository;
import com.schedulix.setup.Subject;
import com.schedulix.setup.SubjectRepository;
import com.schedulix.setup.Teacher;
import com.schedulix.setup.TeacherRepository;
import com.schedulix.setup.TeacherSubject;
import com.schedulix.setup.TeacherSubjectRepository;
import com.schedulix.timetable.BusyCellService;
import com.schedulix.timetable.Lecture;
import com.schedulix.timetable.LectureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SubstituteService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM");

    private final LeaveRepository leaveRepository;
    private final LeaveLectureRepository leaveLectureRepository;
    private final SubstituteRepository substituteRepository;
    private final LectureRepository lectureRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final SemesterRepository semesterRepository;
    private final CurrentUser currentUser;
    private final NotificationService notificationService;
    private final HistoryService historyService;

    public SubstituteService(
            LeaveRepository leaveRepository,
            LeaveLectureRepository leaveLectureRepository,
            SubstituteRepository substituteRepository,
            LectureRepository lectureRepository,
            TeacherRepository teacherRepository,
            TeacherSubjectRepository teacherSubjectRepository,
            SubjectRepository subjectRepository,
            SectionRepository sectionRepository,
            SemesterRepository semesterRepository,
            CurrentUser currentUser,
            NotificationService notificationService,
            HistoryService historyService
    ) {
        this.leaveRepository = leaveRepository;
        this.leaveLectureRepository = leaveLectureRepository;
        this.substituteRepository = substituteRepository;
        this.lectureRepository = lectureRepository;
        this.teacherRepository = teacherRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
        this.semesterRepository = semesterRepository;
        this.currentUser = currentUser;
        this.notificationService = notificationService;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public List<TeacherDateLectureResponse> getTeacherLecturesOnDate(UUID teacherId, LocalDate date) {
        UUID deptId = currentUser.departmentId();
        teacherRepository.findByIdAndDepartmentId(teacherId, deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));

        DayOfWeek day = date.getDayOfWeek();
        List<Lecture> savedLectures = lectureRepository.findSavedLecturesByDepartment(deptId);

        Map<UUID, Subject> subjectById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectById.put(s.getId(), s);
        }

        Map<UUID, Semester> semesterById = new HashMap<>();
        Map<UUID, Section> sectionById = new HashMap<>();
        for (Semester sem : semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId)) {
            semesterById.put(sem.getId(), sem);
            for (Section sec : sectionRepository.findBySemesterIdOrderByNameAsc(sem.getId())) {
                sectionById.put(sec.getId(), sec);
            }
        }

        List<TeacherDateLectureResponse> result = new ArrayList<>();
        for (Lecture lec : savedLectures) {
            if (!lec.getTeacherId().equals(teacherId) || lec.getDayOfWeek() != day) {
                continue;
            }
            Subject sub = subjectById.get(lec.getSubjectId());
            Section sec = sectionById.get(lec.getSectionId());
            if (sub == null || sec == null) continue;
            Semester sem = semesterById.get(sec.getSemesterId());

            result.add(new TeacherDateLectureResponse(
                    lec.getId(),
                    sec.getId(),
                    sec.getName(),
                    sem != null ? sem.getName() : "",
                    sub.getId(),
                    sub.getCode(),
                    sub.getName(),
                    lec.getStartLecture(),
                    lec.getLength(),
                    lec.getStartTime().format(HH_MM),
                    lec.getEndTime().format(HH_MM)
            ));
        }

        result.sort(Comparator.comparing(TeacherDateLectureResponse::startTime)
                .thenComparing(TeacherDateLectureResponse::sectionName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional
    public int prepareForLeave(Leave leave) {
        if (leave.getStatus() != LeaveStatus.ACCEPTED) {
            return 0;
        }
        int created = 0;
        LocalDate cursor = leave.getFromDate();
        while (!cursor.isAfter(leave.getToDate())) {
            created += prepareMissingForLeaveOnDate(leave, cursor);
            cursor = cursor.plusDays(1);
        }
        return created;
    }

    @Transactional
    public PrepareSubstitutesResponse prepareForDate(LocalDate date) {
        UUID deptId = currentUser.departmentId();
        List<Lecture> savedLectures = lectureRepository.findSavedLecturesByDepartment(deptId);
        boolean hasSavedForDay = savedLectures.stream().anyMatch(l -> l.getDayOfWeek() == date.getDayOfWeek());

        List<Leave> acceptedLeaves = leaveRepository.findAcceptedOnDate(deptId, date);
        int prepared = 0;
        for (Leave leave : acceptedLeaves) {
            prepared += prepareMissingForLeaveOnDate(leave, date);
        }

        Set<UUID> deptLectureIds = new HashSet<>();
        for (Lecture l : savedLectures) {
            deptLectureIds.add(l.getId());
        }
        int totalForDate = (int) substituteRepository.findByOnDate(date).stream()
                .filter(s -> deptLectureIds.contains(s.getLectureId()))
                .count();

        String message;
        if (!hasSavedForDay) {
            message = "No saved timetable has lectures on this date.";
        } else if (prepared > 0) {
            message = "Prepared " + prepared + " substitute arrangement" + (prepared == 1 ? "" : "s") + ".";
        } else {
            message = "All substitute arrangements for this date are already up to date.";
        }

        return new PrepareSubstitutesResponse(date, prepared, totalForDate, message);
    }

    @Transactional(readOnly = true)
    public List<SubstituteOptionResponse> getOptions(UUID substituteId) {
        UUID deptId = currentUser.departmentId();
        Substitute sub = loadOwnedSubstitute(substituteId, deptId);
        Lecture target = lectureRepository.findById(sub.getLectureId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LECTURE_NOT_FOUND", "Lecture not found."));

        List<Lecture> allSaved = lectureRepository.findSavedLecturesByDepartment(deptId);
        Map<UUID, Lecture> savedById = new HashMap<>();
        for (Lecture l : allSaved) {
            savedById.put(l.getId(), l);
        }

        Set<UUID> teachersOnLeave = new HashSet<>();
        for (Leave l : leaveRepository.findAcceptedOnDate(deptId, sub.getOnDate())) {
            teachersOnLeave.add(l.getTeacherId());
        }

        List<Substitute> daySubs = substituteRepository.findByOnDate(sub.getOnDate());
        List<Teacher> activeTeachers = teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                .filter(Teacher::isActive)
                .toList();

        List<SubstituteOptionResponse> options = new ArrayList<>();
        DayOfWeek day = sub.getOnDate().getDayOfWeek();

        for (Teacher t : activeTeachers) {
            if (t.getId().equals(target.getTeacherId())) {
                continue;
            }
            if (teachersOnLeave.contains(t.getId())) {
                options.add(new SubstituteOptionResponse(
                        t.getId(), t.getCode(), t.getFullName(),
                        "ON_LEAVE", "On leave", false, false
                ));
                continue;
            }
            boolean busy = isBusyBySavedTimetable(t.getId(), day, target, allSaved)
                    || isBusyBySubstitute(t.getId(), sub.getOnDate(), target, savedById, daySubs, sub.getId());
            if (busy) {
                options.add(new SubstituteOptionResponse(
                        t.getId(), t.getCode(), t.getFullName(),
                        "BUSY", "Busy at that time", false, false
                ));
                continue;
            }
            boolean able = teacherSubjectRepository.findByTeacherId(t.getId()).stream()
                    .anyMatch(ts -> ts.getSubjectId().equals(target.getSubjectId()));
            if (able) {
                options.add(new SubstituteOptionResponse(
                        t.getId(), t.getCode(), t.getFullName(),
                        "FREE_ABLE", "Free and able", true, false
                ));
            } else {
                options.add(new SubstituteOptionResponse(
                        t.getId(), t.getCode(), t.getFullName(),
                        "FREE_NOT_ABLE", "Free but not listed for this subject", true, true
                ));
            }
        }

        options.sort(Comparator
                .comparingInt((SubstituteOptionResponse o) -> statusOrder(o.status()))
                .thenComparing(SubstituteOptionResponse::teacherName, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    @Transactional
    public SubstituteArrangementResponse updateTeacher(UUID substituteId, UUID newTeacherId) {
        UUID deptId = currentUser.departmentId();
        Substitute sub = loadOwnedSubstitute(substituteId, deptId);
        Lecture target = lectureRepository.findById(sub.getLectureId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LECTURE_NOT_FOUND", "Lecture not found."));

        Teacher chosen = teacherRepository.findByIdAndDepartmentId(newTeacherId, deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));
        if (!chosen.isActive()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "TEACHER_INACTIVE", chosen.getFullName() + " is inactive.");
        }
        if (chosen.getId().equals(target.getTeacherId())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SAME_AS_ABSENT_TEACHER",
                    chosen.getFullName() + " is the absent teacher for this lecture."
            );
        }

        validateTeacherAvailable(deptId, chosen, target, sub);

        boolean able = teacherSubjectRepository.findByTeacherId(chosen.getId()).stream()
                .anyMatch(ts -> ts.getSubjectId().equals(target.getSubjectId()));
        String warning = able ? null : chosen.getFullName() + " is not listed as able to teach this subject.";

        UUID previousTeacherId = sub.getSubstituteTeacherId();
        boolean wasAccepted = sub.getStatus() == SubstituteStatus.ACCEPTED;

        sub.setSubstituteTeacherId(chosen.getId());
        if (!wasAccepted) {
            sub.setStatus(SubstituteStatus.PROPOSED);
        }
        sub.setUpdatedAt(Instant.now());
        substituteRepository.saveAndFlush(sub);

        if (wasAccepted && !chosen.getId().equals(previousTeacherId)) {
            Subject subject = subjectRepository.findById(target.getSubjectId()).orElse(null);
            Section section = sectionRepository.findById(target.getSectionId()).orElse(null);
            Teacher absentTeacher = teacherRepository.findById(target.getTeacherId()).orElse(null);

            String subName = subject != null ? subject.getName() : "this lecture";
            String secLabel = section != null ? "Section " + section.getName() : "class";
            String dateLabel = sub.getOnDate().format(DISPLAY_DATE);

            if (previousTeacherId != null) {
                teacherRepository.findById(previousTeacherId).ifPresent(oldTeacher -> {
                    if (oldTeacher.getUserAccountId() != null) {
                        notificationService.send(
                                oldTeacher.getUserAccountId(),
                                "Your substitute arrangement for " + subName + " (" + secLabel
                                        + ") on " + dateLabel + ", lecture " + target.getStartLecture()
                                        + " was cancelled.",
                                "/today"
                        );
                    }
                });
            }

            notifyAcceptedArrangement(chosen, absentTeacher, subName, secLabel, dateLabel, target.getStartLecture());
            historyService.record(
                    "SUBSTITUTE",
                    "Changed substitute for " + (absentTeacher != null ? absentTeacher.getFullName() : "teacher")
                            + ", " + secLabel + ", " + dateLabel + " to " + chosen.getFullName() + "."
            );
        }

        return new SubstituteArrangementResponse(
                sub.getId(),
                sub.getLeaveId(),
                sub.getLectureId(),
                sub.getOnDate(),
                chosen.getId(),
                chosen.getFullName(),
                sub.getStatus(),
                warning
        );
    }

    @Transactional
    public SubstituteArrangementResponse accept(UUID substituteId) {
        UUID deptId = currentUser.departmentId();
        Substitute sub = loadOwnedSubstitute(substituteId, deptId);
        return acceptSingle(deptId, sub);
    }

    @Transactional
    public AcceptAllSubstitutesResponse acceptAll(LocalDate date) {
        UUID deptId = currentUser.departmentId();
        List<Lecture> savedLectures = lectureRepository.findSavedLecturesByDepartment(deptId);
        Set<UUID> deptLectureIds = new HashSet<>();
        for (Lecture l : savedLectures) {
            deptLectureIds.add(l.getId());
        }

        int accepted = 0;
        int skippedNeeded = 0;
        for (Substitute sub : substituteRepository.findByOnDate(date)) {
            if (!deptLectureIds.contains(sub.getLectureId())) continue;
            if (sub.getStatus() == SubstituteStatus.ACCEPTED) continue;
            if (sub.getStatus() == SubstituteStatus.NEEDED || sub.getSubstituteTeacherId() == null) {
                skippedNeeded++;
                continue;
            }
            acceptSingle(deptId, sub);
            accepted++;
        }

        return new AcceptAllSubstitutesResponse(date, accepted, skippedNeeded);
    }

    private SubstituteArrangementResponse acceptSingle(UUID deptId, Substitute sub) {
        if (sub.getSubstituteTeacherId() == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SUBSTITUTE_TEACHER_REQUIRED",
                    "Choose a substitute teacher before accepting."
            );
        }

        Lecture target = lectureRepository.findById(sub.getLectureId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LECTURE_NOT_FOUND", "Lecture not found."));
        Teacher chosen = teacherRepository.findByIdAndDepartmentId(sub.getSubstituteTeacherId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));

        validateTeacherAvailable(deptId, chosen, target, sub);

        if (sub.getStatus() != SubstituteStatus.ACCEPTED) {
            sub.setStatus(SubstituteStatus.ACCEPTED);
            sub.setUpdatedAt(Instant.now());
            substituteRepository.saveAndFlush(sub);

            Subject subject = subjectRepository.findById(target.getSubjectId()).orElse(null);
            Section section = sectionRepository.findById(target.getSectionId()).orElse(null);
            Teacher absentTeacher = teacherRepository.findById(target.getTeacherId()).orElse(null);

            String subName = subject != null ? subject.getName() : "this lecture";
            String secLabel = section != null ? "Section " + section.getName() : "class";
            String dateLabel = sub.getOnDate().format(DISPLAY_DATE);

            notifyAcceptedArrangement(chosen, absentTeacher, subName, secLabel, dateLabel, target.getStartLecture());
            historyService.record(
                    "SUBSTITUTE",
                    "Accepted " + chosen.getFullName() + " as substitute for "
                            + (absentTeacher != null ? absentTeacher.getFullName() : "teacher")
                            + ", " + secLabel + ", " + dateLabel + "."
            );
        }

        return new SubstituteArrangementResponse(
                sub.getId(),
                sub.getLeaveId(),
                sub.getLectureId(),
                sub.getOnDate(),
                chosen.getId(),
                chosen.getFullName(),
                sub.getStatus(),
                null
        );
    }

    private void notifyAcceptedArrangement(
            Teacher substituteTeacher,
            Teacher absentTeacher,
            String subjectName,
            String sectionLabel,
            String dateLabel,
            int startLecture
    ) {
        if (substituteTeacher.getUserAccountId() != null) {
            notificationService.send(
                    substituteTeacher.getUserAccountId(),
                    "You will take " + subjectName + " for " + sectionLabel
                            + " on " + dateLabel + ", lecture " + startLecture + ".",
                    "/today"
            );
        }
        if (absentTeacher != null && absentTeacher.getUserAccountId() != null) {
            notificationService.send(
                    absentTeacher.getUserAccountId(),
                    substituteTeacher.getFullName() + " will cover your " + subjectName
                            + " (" + sectionLabel + ") on " + dateLabel + ", lecture " + startLecture + ".",
                    "/today"
            );
        }
    }

    private void validateTeacherAvailable(UUID deptId, Teacher teacher, Lecture target, Substitute currentSub) {
        boolean onLeave = leaveRepository.findAcceptedOnDate(deptId, currentSub.getOnDate()).stream()
                .anyMatch(l -> l.getTeacherId().equals(teacher.getId()));
        if (onLeave) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "TEACHER_ON_LEAVE",
                    teacher.getFullName() + " is on leave on this date."
            );
        }

        List<Lecture> allSaved = lectureRepository.findSavedLecturesByDepartment(deptId);
        Map<UUID, Lecture> savedById = new HashMap<>();
        for (Lecture l : allSaved) {
            savedById.put(l.getId(), l);
        }

        if (isBusyBySavedTimetable(teacher.getId(), currentSub.getOnDate().getDayOfWeek(), target, allSaved)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "TEACHER_BUSY",
                    teacher.getFullName() + " already has a lecture at this time."
            );
        }

        List<Substitute> daySubs = substituteRepository.findByOnDate(currentSub.getOnDate());
        if (isBusyBySubstitute(teacher.getId(), currentSub.getOnDate(), target, savedById, daySubs, currentSub.getId())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "TEACHER_BUSY_SUBSTITUTE",
                    teacher.getFullName() + " is already covering another lecture at this time."
            );
        }
    }

    private Substitute loadOwnedSubstitute(UUID substituteId, UUID deptId) {
        Substitute sub = substituteRepository.findById(substituteId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SUBSTITUTE_NOT_FOUND", "Substitute arrangement not found."));
        leaveRepository.findByIdAndDepartmentId(sub.getLeaveId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SUBSTITUTE_NOT_FOUND", "Substitute arrangement not found."));
        return sub;
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "FREE_ABLE" -> 0;
            case "FREE_NOT_ABLE" -> 1;
            case "BUSY" -> 2;
            default -> 3;
        };
    }

    int prepareMissingForLeaveOnDate(Leave leave, LocalDate date) {
        UUID deptId = leave.getDepartmentId();
        DayOfWeek day = date.getDayOfWeek();

        List<Lecture> allSaved = lectureRepository.findSavedLecturesByDepartment(deptId);
        if (allSaved.isEmpty()) {
            return 0;
        }

        Map<UUID, Lecture> savedById = new HashMap<>();
        for (Lecture l : allSaved) {
            savedById.put(l.getId(), l);
        }

        List<Lecture> affected = new ArrayList<>();
        if (leave.getScope() == LeaveScope.SOME_LECTURES) {
            for (LeaveLecture ll : leaveLectureRepository.findByLeaveIdAndOnDate(leave.getId(), date)) {
                Lecture lec = savedById.get(ll.getLectureId());
                if (lec != null && lec.getTeacherId().equals(leave.getTeacherId()) && lec.getDayOfWeek() == day) {
                    affected.add(lec);
                }
            }
        } else {
            for (Lecture lec : allSaved) {
                if (lec.getTeacherId().equals(leave.getTeacherId()) && lec.getDayOfWeek() == day) {
                    affected.add(lec);
                }
            }
        }

        if (affected.isEmpty()) {
            return 0;
        }

        affected.sort(Comparator.comparing(Lecture::getStartTime)
                .thenComparingInt(Lecture::getStartLecture));

        List<Teacher> activeTeachers = teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                .filter(Teacher::isActive)
                .toList();

        Map<UUID, Set<UUID>> ableSubjectsByTeacher = new HashMap<>();
        for (Teacher t : activeTeachers) {
            Set<UUID> subs = new HashSet<>();
            for (TeacherSubject ts : teacherSubjectRepository.findByTeacherId(t.getId())) {
                subs.add(ts.getSubjectId());
            }
            ableSubjectsByTeacher.put(t.getId(), subs);
        }

        Set<UUID> teachersOnLeave = new HashSet<>();
        for (Leave l : leaveRepository.findAcceptedOnDate(deptId, date)) {
            teachersOnLeave.add(l.getTeacherId());
        }

        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = date.with(DayOfWeek.SUNDAY);
        List<Substitute> weekSubs = new ArrayList<>(substituteRepository.findByOnDateBetween(weekStart, weekEnd));

        int created = 0;
        for (Lecture target : affected) {
            if (substituteRepository.findByLectureIdAndOnDate(target.getId(), date).isPresent()) {
                continue;
            }

            Teacher chosen = pickBestSubstitute(
                    target,
                    date,
                    activeTeachers,
                    ableSubjectsByTeacher,
                    teachersOnLeave,
                    allSaved,
                    savedById,
                    weekSubs
            );

            Substitute saved = substituteRepository.save(new Substitute(
                    leave.getId(),
                    target.getId(),
                    date,
                    chosen != null ? chosen.getId() : null,
                    chosen != null ? SubstituteStatus.PROPOSED : SubstituteStatus.NEEDED,
                    Instant.now()
            ));
            weekSubs.add(saved);
            created++;
        }

        if (created > 0) {
            substituteRepository.flush();
        }
        return created;
    }

    private Teacher pickBestSubstitute(
            Lecture target,
            LocalDate date,
            List<Teacher> activeTeachers,
            Map<UUID, Set<UUID>> ableSubjectsByTeacher,
            Set<UUID> teachersOnLeave,
            List<Lecture> allSaved,
            Map<UUID, Lecture> savedById,
            List<Substitute> weekSubs
    ) {
        DayOfWeek day = date.getDayOfWeek();
        List<Teacher> candidates = new ArrayList<>();

        for (Teacher cand : activeTeachers) {
            if (cand.getId().equals(target.getTeacherId())) continue;
            if (teachersOnLeave.contains(cand.getId())) continue;
            if (!ableSubjectsByTeacher.getOrDefault(cand.getId(), Set.of()).contains(target.getSubjectId())) {
                continue;
            }
            if (isBusyBySavedTimetable(cand.getId(), day, target, allSaved)) continue;
            if (isBusyBySubstitute(cand.getId(), date, target, savedById, weekSubs, null)) continue;
            candidates.add(cand);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Map<UUID, Integer> dayExtraCount = new HashMap<>();
        Map<UUID, Integer> weekExtraCount = new HashMap<>();
        for (Substitute s : weekSubs) {
            if (s.getSubstituteTeacherId() == null) continue;
            weekExtraCount.merge(s.getSubstituteTeacherId(), 1, Integer::sum);
            if (date.equals(s.getOnDate())) {
                dayExtraCount.merge(s.getSubstituteTeacherId(), 1, Integer::sum);
            }
        }

        candidates.sort(Comparator
                .comparingInt((Teacher t) -> dayExtraCount.getOrDefault(t.getId(), 0))
                .thenComparingInt(t -> weekExtraCount.getOrDefault(t.getId(), 0))
                .thenComparing(Teacher::getCode, String.CASE_INSENSITIVE_ORDER));

        return candidates.get(0);
    }

    boolean isBusyBySavedTimetable(UUID teacherId, DayOfWeek day, Lecture target, List<Lecture> allSaved) {
        for (Lecture lec : allSaved) {
            if (!lec.getTeacherId().equals(teacherId) || lec.getDayOfWeek() != day) continue;
            if (BusyCellService.overlaps(
                    lec.getStartTime(), lec.getEndTime(),
                    target.getStartTime(), target.getEndTime()
            )) {
                return true;
            }
        }
        return false;
    }

    boolean isBusyBySubstitute(
            UUID teacherId,
            LocalDate date,
            Lecture target,
            Map<UUID, Lecture> savedById,
            List<Substitute> subs,
            UUID excludeSubId
    ) {
        for (Substitute s : subs) {
            if (excludeSubId != null && excludeSubId.equals(s.getId())) continue;
            if (!date.equals(s.getOnDate())) continue;
            if (!teacherId.equals(s.getSubstituteTeacherId())) continue;
            Lecture other = savedById.get(s.getLectureId());
            if (other == null) continue;
            if (BusyCellService.overlaps(
                    other.getStartTime(), other.getEndTime(),
                    target.getStartTime(), target.getEndTime()
            )) {
                return true;
            }
        }
        return false;
    }
}