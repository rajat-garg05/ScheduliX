package com.schedulix.leave;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
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
            CurrentUser currentUser
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