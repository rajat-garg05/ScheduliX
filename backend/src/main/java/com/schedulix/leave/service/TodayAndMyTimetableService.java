package com.schedulix.leave.service;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.leave.dto.MyTimetableResponse.MyWeeklyLecture;
import com.schedulix.leave.dto.MyTimetableResponse.UpcomingSubstituteItem;
import com.schedulix.leave.dto.TodayResponse.TodayLecture;
import com.schedulix.leave.dto.TodayResponse.TodaySection;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.Semester;
import com.schedulix.setup.repository.SemesterRepository;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.repository.SubjectRepository;
import com.schedulix.setup.entity.Teacher;
import com.schedulix.setup.repository.TeacherRepository;
import com.schedulix.timetable.entity.Lecture;
import com.schedulix.timetable.repository.LectureRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.schedulix.leave.dto.MyTimetableResponse;
import com.schedulix.leave.entity.Substitute;
import com.schedulix.leave.repository.SubstituteRepository;
import com.schedulix.leave.entity.SubstituteStatus;
import com.schedulix.leave.dto.TodayResponse;

@Service
public class TodayAndMyTimetableService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SubstituteService substituteService;
    private final LeaveService leaveService;
    private final SubstituteRepository substituteRepository;
    private final LectureRepository lectureRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final CurrentUser currentUser;
    private final ZoneId departmentZone;

    public TodayAndMyTimetableService(
            SubstituteService substituteService,
            LeaveService leaveService,
            SubstituteRepository substituteRepository,
            LectureRepository lectureRepository,
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            CurrentUser currentUser,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        this.substituteService = substituteService;
        this.leaveService = leaveService;
        this.substituteRepository = substituteRepository;
        this.lectureRepository = lectureRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.currentUser = currentUser;
        this.departmentZone = ZoneId.of(timezone);
    }

    @Transactional
    public TodayResponse getToday(LocalDate requestedDate) {
        UUID deptId = currentUser.departmentId();
        boolean isHod = currentUser.role() == UserRole.HOD;
        LocalDate date = requestedDate != null ? requestedDate : LocalDate.now(departmentZone);
        DayOfWeek day = date.getDayOfWeek();

        if (isHod) {
            substituteService.prepareForDate(date);
        }

        UUID myTeacherId = isHod ? null : leaveService.findTeacherForCurrentUser(deptId).getId();

        List<Lecture> savedLectures = lectureRepository.findSavedLecturesByDepartment(deptId);
        Map<UUID, List<Lecture>> dayLecturesBySection = new HashMap<>();
        for (Lecture lec : savedLectures) {
            if (lec.getDayOfWeek() == day) {
                dayLecturesBySection.computeIfAbsent(lec.getSectionId(), k -> new ArrayList<>()).add(lec);
            }
        }

        Map<UUID, Substitute> subByLectureId = new HashMap<>();
        for (Substitute sub : substituteRepository.findByOnDate(date)) {
            subByLectureId.put(sub.getLectureId(), sub);
        }

        Map<UUID, Subject> subjectById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectById.put(s.getId(), s);
        }

        Map<UUID, Teacher> teacherById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teacherById.put(t.getId(), t);
        }

        List<TodaySection> todaySections = new ArrayList<>();
        int reviewCount = 0;

        for (Semester sem : semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId)) {
            for (Section sec : sectionRepository.findBySemesterIdOrderByNameAsc(sem.getId())) {
                List<Lecture> secLectures = dayLecturesBySection.get(sec.getId());
                if (secLectures == null || secLectures.isEmpty()) {
                    continue;
                }

                secLectures.sort(Comparator.comparing(Lecture::getStartTime)
                        .thenComparingInt(Lecture::getStartLecture));

                List<TodayLecture> items = new ArrayList<>(secLectures.size());
                for (Lecture lec : secLectures) {
                    Subject sub = subjectById.get(lec.getSubjectId());
                    Teacher origTeacher = teacherById.get(lec.getTeacherId());
                    if (sub == null || origTeacher == null) continue;

                    Substitute arr = subByLectureId.get(lec.getId());
                    UUID subId = null;
                    UUID subTeacherId = null;
                    String subTeacherName = null;
                    SubstituteStatus subStatus = null;

                    if (arr != null) {
                        if (isHod) {
                            subId = arr.getId();
                            subTeacherId = arr.getSubstituteTeacherId();
                            Teacher st = subTeacherId != null ? teacherById.get(subTeacherId) : null;
                            subTeacherName = st != null ? st.getFullName() : null;
                            subStatus = arr.getStatus();
                            if (arr.getStatus() != SubstituteStatus.ACCEPTED) {
                                reviewCount++;
                            }
                        } else if (arr.getStatus() == SubstituteStatus.ACCEPTED) {
                            subId = arr.getId();
                            subTeacherId = arr.getSubstituteTeacherId();
                            Teacher st = subTeacherId != null ? teacherById.get(subTeacherId) : null;
                            subTeacherName = st != null ? st.getFullName() : null;
                            subStatus = arr.getStatus();
                        }
                    }

                    items.add(new TodayLecture(
                            lec.getId(),
                            sub.getId(),
                            sub.getCode(),
                            sub.getName(),
                            sub.getKind() == SubjectKind.LAB,
                            sub.getSharedPlace(),
                            lec.getStartLecture(),
                            lec.getLength(),
                            lec.getStartTime().format(HH_MM),
                            lec.getEndTime().format(HH_MM),
                            origTeacher.getId(),
                            origTeacher.getFullName(),
                            subId,
                            subTeacherId,
                            subTeacherName,
                            subStatus,
                            lec.getGroupKey()
                    ));
                }

                todaySections.add(new TodaySection(sec.getId(), sec.getName(), sem.getName(), items));
            }
        }

        return new TodayResponse(date, day.name(), reviewCount, myTeacherId, todaySections);
    }

    @Transactional(readOnly = true)
    public MyTimetableResponse getMyTimetable() {
        UUID deptId = currentUser.departmentId();
        Teacher me = leaveService.findTeacherForCurrentUser(deptId);
        LocalDate today = LocalDate.now(departmentZone);

        List<Lecture> savedLectures = lectureRepository.findSavedLecturesByDepartment(deptId);
        Map<UUID, Lecture> savedById = new HashMap<>();
        for (Lecture l : savedLectures) {
            savedById.put(l.getId(), l);
        }

        Map<UUID, Subject> subjectById = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectById.put(s.getId(), s);
        }

        Map<UUID, Teacher> teacherById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teacherById.put(t.getId(), t);
        }

        Map<UUID, Semester> semesterById = new LinkedHashMap<>();
        Map<UUID, Section> sectionById = new HashMap<>();
        for (Semester sem : semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId)) {
            semesterById.put(sem.getId(), sem);
            for (Section sec : sectionRepository.findBySemesterIdOrderByNameAsc(sem.getId())) {
                sectionById.put(sec.getId(), sec);
            }
        }

        List<MyWeeklyLecture> weekly = new ArrayList<>();
        for (Lecture lec : savedLectures) {
            if (!lec.getTeacherId().equals(me.getId())) continue;
            Subject sub = subjectById.get(lec.getSubjectId());
            Section sec = sectionById.get(lec.getSectionId());
            if (sub == null || sec == null) continue;
            Semester sem = semesterById.get(sec.getSemesterId());

            weekly.add(new MyWeeklyLecture(
                    lec.getId(),
                    sec.getId(),
                    sec.getName(),
                    sem != null ? sem.getName() : "",
                    sub.getId(),
                    sub.getCode(),
                    sub.getName(),
                    sub.getKind() == SubjectKind.LAB,
                    sub.getSharedPlace(),
                    lec.getDayOfWeek().name(),
                    lec.getStartLecture(),
                    lec.getLength(),
                    lec.getStartTime().format(HH_MM),
                    lec.getEndTime().format(HH_MM)
            ));
        }

        weekly.sort(Comparator
                .comparingInt((MyWeeklyLecture l) -> DayOfWeek.valueOf(l.dayOfWeek()).getValue())
                .thenComparing(MyWeeklyLecture::startTime));

        List<UpcomingSubstituteItem> coveringForOthers = new ArrayList<>();
        List<UpcomingSubstituteItem> othersCoveringForMe = new ArrayList<>();

        for (Substitute sub : substituteRepository.findByOnDateGreaterThanEqualOrderByOnDateAsc(today)) {
            if (sub.getStatus() != SubstituteStatus.ACCEPTED || sub.getSubstituteTeacherId() == null) {
                continue;
            }
            Lecture lec = savedById.get(sub.getLectureId());
            if (lec == null) continue;

            Subject subject = subjectById.get(lec.getSubjectId());
            Section sec = sectionById.get(lec.getSectionId());
            if (subject == null || sec == null) continue;
            Semester sem = semesterById.get(sec.getSemesterId());

            if (sub.getSubstituteTeacherId().equals(me.getId())) {
                Teacher orig = teacherById.get(lec.getTeacherId());
                coveringForOthers.add(new UpcomingSubstituteItem(
                        sub.getId(),
                        sub.getOnDate(),
                        sec.getName(),
                        sem != null ? sem.getName() : "",
                        subject.getCode(),
                        subject.getName(),
                        lec.getStartLecture(),
                        lec.getLength(),
                        lec.getStartTime().format(HH_MM),
                        lec.getEndTime().format(HH_MM),
                        orig != null ? orig.getFullName() : ""
                ));
            } else if (lec.getTeacherId().equals(me.getId())) {
                Teacher cover = teacherById.get(sub.getSubstituteTeacherId());
                othersCoveringForMe.add(new UpcomingSubstituteItem(
                        sub.getId(),
                        sub.getOnDate(),
                        sec.getName(),
                        sem != null ? sem.getName() : "",
                        subject.getCode(),
                        subject.getName(),
                        lec.getStartLecture(),
                        lec.getLength(),
                        lec.getStartTime().format(HH_MM),
                        lec.getEndTime().format(HH_MM),
                        cover != null ? cover.getFullName() : ""
                ));
            }
        }

        return new MyTimetableResponse(
                me.getId(),
                me.getFullName(),
                weekly,
                coveringForOthers,
                othersCoveringForMe
        );
    }
}