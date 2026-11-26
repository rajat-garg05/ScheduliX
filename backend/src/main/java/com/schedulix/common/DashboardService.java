package com.schedulix.common;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.common.DashboardResponse.HodSummary;
import com.schedulix.common.DashboardResponse.TeacherSummary;
import com.schedulix.common.DashboardResponse.TodayTeacherLecture;
import com.schedulix.leave.entity.Leave;
import com.schedulix.leave.repository.LeaveRepository;
import com.schedulix.leave.entity.LeaveStatus;
import com.schedulix.leave.entity.SubstituteStatus;
import com.schedulix.leave.service.TodayAndMyTimetableService;
import com.schedulix.leave.dto.TodayResponse;
import com.schedulix.leave.dto.TodayResponse.TodayLecture;
import com.schedulix.leave.dto.TodayResponse.TodaySection;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.Semester;
import com.schedulix.setup.repository.SemesterRepository;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.repository.SubjectRepository;
import com.schedulix.setup.entity.Teacher;
import com.schedulix.setup.repository.TeacherRepository;
import com.schedulix.timetable.repository.TimetableClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final CurrentUser currentUser;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final TimetableClassRepository timetableClassRepository;
    private final LeaveRepository leaveRepository;
    private final TodayAndMyTimetableService todayService;

    public DashboardService(
            CurrentUser currentUser,
            TeacherRepository teacherRepository,
            SubjectRepository subjectRepository,
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            TimetableClassRepository timetableClassRepository,
            LeaveRepository leaveRepository,
            TodayAndMyTimetableService todayService
    ) {
        this.currentUser = currentUser;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.timetableClassRepository = timetableClassRepository;
        this.leaveRepository = leaveRepository;
        this.todayService = todayService;
    }

    @Transactional
    public DashboardResponse getDashboard() {
        UUID deptId = currentUser.departmentId();
        UserRole role = currentUser.role();
        TodayResponse today = todayService.getToday(null);

        if (role == UserRole.HOD) {
            int activeTeachers = (int) teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                    .filter(Teacher::isActive)
                    .count();
            int activeSubjects = (int) subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                    .filter(Subject::isActive)
                    .count();

            int totalSections = 0;
            int savedSections = 0;
            for (Semester sem : semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId)) {
                for (Section sec : sectionRepository.findBySemesterIdOrderByNameAsc(sem.getId())) {
                    totalSections++;
                    if (timetableClassRepository.existsBySectionIdAndSavedTrue(sec.getId())) {
                        savedSections++;
                    }
                }
            }

            int pendingLeaves = (int) leaveRepository.findByDepartmentIdOrderByCreatedAtDesc(deptId).stream()
                    .filter(l -> l.getStatus() == LeaveStatus.ASKED)
                    .count();

            HodSummary hod = new HodSummary(
                    activeTeachers,
                    activeSubjects,
                    totalSections,
                    savedSections,
                    pendingLeaves,
                    today.reviewCount()
            );

            return new DashboardResponse(role.name(), today.date(), hod, null);
        }

        UUID myTeacherId = today.myTeacherId();
        String teacherName = teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                .filter(t -> t.getId().equals(myTeacherId))
                .map(Teacher::getFullName)
                .findFirst()
                .orElse("");

        List<TodayTeacherLecture> myLectures = new ArrayList<>();
        for (TodaySection sec : today.sections()) {
            for (TodayLecture lec : sec.lectures()) {
                boolean isOriginal = myTeacherId != null && myTeacherId.equals(lec.originalTeacherId());
                boolean isAcceptedSub = lec.substituteStatus() == SubstituteStatus.ACCEPTED
                        && myTeacherId != null
                        && myTeacherId.equals(lec.substituteTeacherId());

                if (!isOriginal && !isAcceptedSub) {
                    continue;
                }

                boolean coveredBySomeone = isOriginal && lec.substituteStatus() == SubstituteStatus.ACCEPTED;
                String otherName = null;
                if (isAcceptedSub) {
                    otherName = lec.originalTeacherName();
                } else if (coveredBySomeone) {
                    otherName = lec.substituteTeacherName();
                }

                myLectures.add(new TodayTeacherLecture(
                        lec.lectureId(),
                        sec.semesterName(),
                        sec.sectionName(),
                        lec.subjectCode(),
                        lec.subjectName(),
                        lec.startLecture(),
                        lec.length(),
                        lec.startTime(),
                        lec.endTime(),
                        lec.sharedPlace(),
                        isAcceptedSub,
                        coveredBySomeone,
                        otherName
                ));
            }
        }

        myLectures.sort(Comparator.comparing(TodayTeacherLecture::startTime)
                .thenComparingInt(TodayTeacherLecture::startLecture));

        TeacherSummary teacher = new TeacherSummary(myTeacherId, teacherName, myLectures);
        return new DashboardResponse(role.name(), today.date(), null, teacher);
    }
}