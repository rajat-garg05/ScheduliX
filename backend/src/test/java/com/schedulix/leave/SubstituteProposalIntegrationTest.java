package com.schedulix.leave;

import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.dto.SubjectRequest;
import com.schedulix.setup.dto.SubjectResponse;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
import com.schedulix.setup.dto.TeacherSubjectsRequest;
import com.schedulix.timetable.entity.Lecture;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.entity.TimetableClass;
import com.schedulix.timetable.repository.TimetableClassRepository;
import com.schedulix.timetable.service.TimetableDraftService;
import com.schedulix.timetable.repository.TimetableRepository;
import com.schedulix.timetable.entity.TimetableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.dto.CreateLeaveRequest;
import com.schedulix.leave.entity.LeaveScope;
import com.schedulix.leave.dto.PrepareSubstitutesResponse;
import com.schedulix.leave.entity.Substitute;
import com.schedulix.leave.repository.SubstituteRepository;
import com.schedulix.leave.entity.SubstituteStatus;
import com.schedulix.leave.dto.TeacherDateLectureResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SubstituteProposalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    TimetableRepository timetableRepository;

    @Autowired
    TimetableClassRepository timetableClassRepository;

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    SubstituteRepository substituteRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void proposesRightSubstituteRespectingClockOverlapLeaveAbilityLoadAndLateSavedTimetable() {
        Department dept = departmentRepository.save(new Department("CSE SubProp " + UUID.randomUUID()));
        String hodToken = createHodAndLogin(dept, "hod.sp1@college.edu");

        TeacherResponse absent = createTeacher(hodToken, "T01", "Prof. Absent", "absent.sp@college.edu");
        TeacherResponse busyClock = createTeacher(hodToken, "T02", "Prof. BusyClock", "busy.sp@college.edu");
        TeacherResponse onLeave = createTeacher(hodToken, "T03", "Prof. OnLeave", "leave.sp@college.edu");
        TeacherResponse notAble = createTeacher(hodToken, "T04", "Prof. NotAble", "notable.sp@college.edu");
        TeacherResponse freeBest = createTeacher(hodToken, "T05", "Prof. FreeBest", "free.sp@college.edu");
        TeacherResponse labPartner = createTeacher(hodToken, "T06", "Prof. LabPartner", "labp.sp@college.edu");

        SubjectResponse ds = createSubject(hodToken, "CS301", "Data Structures", SubjectKind.THEORY, 1, null);
        SubjectResponse rare = createSubject(hodToken, "CS399", "Rare Elective", SubjectKind.THEORY, 1, null);
        SubjectResponse lab1 = createSubject(hodToken, "CS351", "DSA Lab", SubjectKind.LAB, 2, "GRP1");
        SubjectResponse lab2 = createSubject(hodToken, "CS352", "Java Lab", SubjectKind.LAB, 2, "GRP1");

        // busyClock, onLeave, freeBest can teach DS; notAble cannot; nobody else can teach rare
        linkTeacherSubjects(hodToken, absent.id(), List.of(ds.id(), rare.id(), lab1.id()));
        linkTeacherSubjects(hodToken, busyClock.id(), List.of(ds.id()));
        linkTeacherSubjects(hodToken, onLeave.id(), List.of(ds.id()));
        linkTeacherSubjects(hodToken, freeBest.id(), List.of(ds.id(), lab1.id()));
        linkTeacherSubjects(hodToken, labPartner.id(), List.of(lab2.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(hodToken, sem3.id(), "A");
        SectionResponse secB = createSection(hodToken, sem3.id(), "B");

        // Saved Timetable 1 (Section A): Monday 09:00-09:50 DS (absent), 10:00-10:50 Rare (absent),
        // and paired lab 11:00-12:40 (lab1=absent, lab2=labPartner)
        Timetable ttA = saveTimetable(dept.getId(), sem3.id(), secA.id(), "Semester 3 - Section A");
        Lecture dsLec = lectureRepository.save(new Lecture(
                ttA.getId(), secA.id(), ds.id(), absent.id(),
                DayOfWeek.MONDAY, (short) 1, (short) 1,
                LocalTime.of(9, 0), LocalTime.of(9, 50), false, null
        ));
        Lecture rareLec = lectureRepository.save(new Lecture(
                ttA.getId(), secA.id(), rare.id(), absent.id(),
                DayOfWeek.MONDAY, (short) 2, (short) 1,
                LocalTime.of(10, 0), LocalTime.of(10, 50), false, null
        ));
        UUID labGroup = UUID.randomUUID();
        Lecture lab1Lec = lectureRepository.save(new Lecture(
                ttA.getId(), secA.id(), lab1.id(), absent.id(),
                DayOfWeek.MONDAY, (short) 3, (short) 2,
                LocalTime.of(11, 0), LocalTime.of(12, 40), false, labGroup
        ));
        Lecture lab2Lec = lectureRepository.save(new Lecture(
                ttA.getId(), secA.id(), lab2.id(), labPartner.id(),
                DayOfWeek.MONDAY, (short) 3, (short) 2,
                LocalTime.of(11, 0), LocalTime.of(12, 40), false, labGroup
        ));

        // Saved Timetable 2 (Section B): DIFFERENT clock times -> busyClock teaches 09:30-10:30 on Monday
        // (overlaps 09:00-09:50 DS!)
        Timetable ttB = saveTimetable(dept.getId(), sem3.id(), secB.id(), "Semester 3 - Section B");
        lectureRepository.save(new Lecture(
                ttB.getId(), secB.id(), ds.id(), busyClock.id(),
                DayOfWeek.MONDAY, (short) 1, (short) 1,
                LocalTime.of(9, 30), LocalTime.of(10, 30), false, null
        ));

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // GET /api/teachers/{id}/lectures?date= returns absent's 3 Monday lectures
        List<TeacherDateLectureResponse> absentMonday = client.get()
                .uri("/api/teachers/" + absent.id() + "/lectures?date=" + nextMonday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(absentMonday).hasSize(3);

        // Put onLeave teacher on accepted leave on nextMonday first
        client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new CreateLeaveRequest(onLeave.id(), nextMonday, nextMonday, LeaveScope.WHOLE_DAYS, "Sick", null))
                .retrieve()
                .toBodilessEntity();

        // Add accepted leave for absent teacher on nextMonday -> auto-prepares substitutes
        client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new CreateLeaveRequest(absent.id(), nextMonday, nextMonday, LeaveScope.WHOLE_DAYS, "Conference", null))
                .retrieve()
                .toBodilessEntity();

        // DS lecture -> freeBest proposed (busyClock excluded by clock overlap, onLeave excluded, notAble excluded)
        Substitute dsSub = substituteRepository.findByLectureIdAndOnDate(dsLec.getId(), nextMonday).orElseThrow();
        assertThat(dsSub.getStatus()).isEqualTo(SubstituteStatus.PROPOSED);
        assertThat(dsSub.getSubstituteTeacherId()).isEqualTo(freeBest.id());

        // Rare lecture -> nobody fits -> NEEDED with null teacher
        Substitute rareSub = substituteRepository.findByLectureIdAndOnDate(rareLec.getId(), nextMonday).orElseThrow();
        assertThat(rareSub.getStatus()).isEqualTo(SubstituteStatus.NEEDED);
        assertThat(rareSub.getSubstituteTeacherId()).isNull();

        // Paired lab -> only absent's lab1 gets a substitute; labPartner's lab2 does NOT
        Substitute lab1Sub = substituteRepository.findByLectureIdAndOnDate(lab1Lec.getId(), nextMonday).orElseThrow();
        assertThat(lab1Sub.getStatus()).isEqualTo(SubstituteStatus.PROPOSED);
        assertThat(lab1Sub.getSubstituteTeacherId()).isEqualTo(freeBest.id());
        assertThat(substituteRepository.findByLectureIdAndOnDate(lab2Lec.getId(), nextMonday)).isEmpty();

        // Late-saved lecture added after leave was already accepted -> POST /api/substitutes/prepare catches it
        Lecture lateLec = lectureRepository.save(new Lecture(
                ttB.getId(), secB.id(), ds.id(), absent.id(),
                DayOfWeek.MONDAY, (short) 5, (short) 1,
                LocalTime.of(14, 0), LocalTime.of(14, 50), false, null
        ));
        PrepareSubstitutesResponse prep = client.post()
                .uri("/api/substitutes/prepare?date=" + nextMonday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(PrepareSubstitutesResponse.class);
        assertThat(prep).isNotNull();
        assertThat(prep.preparedCount()).isEqualTo(1);
        assertThat(substituteRepository.findByLectureIdAndOnDate(lateLec.getId(), nextMonday)).isPresent();
    }

    private Timetable saveTimetable(UUID deptId, UUID semId, UUID secId, String name) {
        Timetable tt = timetableRepository.save(new Timetable(
                deptId, semId, name, TimetableStatus.SAVED,
                TimetableDraftService.DEFAULT_SETTINGS_JSON, null, Instant.now()
        ));
        tt.setSavedAt(Instant.now());
        timetableRepository.save(tt);
        timetableClassRepository.save(new TimetableClass(tt.getId(), secId, true));
        return tt;
    }

    private TeacherResponse createTeacher(String token, String code, String name, String email) {
        return client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest(code, name, email, "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
    }

    private SubjectResponse createSubject(String token, String code, String name, SubjectKind kind, int periods, String group) {
        return client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest(code, name, kind, periods, group, null))
                .retrieve()
                .body(SubjectResponse.class);
    }

    private void linkTeacherSubjects(String token, UUID teacherId, List<UUID> subjectIds) {
        client.put()
                .uri("/api/teachers/" + teacherId + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherSubjectsRequest(subjectIds))
                .retrieve()
                .toBodilessEntity();
    }

    private SectionResponse createSection(String token, UUID semesterId, String name) {
        return client.post()
                .uri("/api/semesters/" + semesterId + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest(name))
                .retrieve()
                .body(SectionResponse.class);
    }

    private String createHodAndLogin(Department dept, String email) {
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                email,
                passwordEncoder.encode("StrongPass123!"),
                "HOD of " + dept.getName(),
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "StrongPass123!"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res.accessToken();
    }
}