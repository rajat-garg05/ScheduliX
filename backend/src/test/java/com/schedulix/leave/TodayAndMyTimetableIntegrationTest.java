package com.schedulix.leave;

import com.schedulix.auth.AccountStatus;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.EmailSender;
import com.schedulix.auth.LoginRequest;
import com.schedulix.auth.LoginResponse;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.auth.UserRole;
import com.schedulix.setup.SectionRequest;
import com.schedulix.setup.SectionResponse;
import com.schedulix.setup.SemesterRequest;
import com.schedulix.setup.SemesterResponse;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRequest;
import com.schedulix.setup.SubjectResponse;
import com.schedulix.setup.TeacherRequest;
import com.schedulix.setup.TeacherResponse;
import com.schedulix.setup.TeacherSubjectsRequest;
import com.schedulix.timetable.Lecture;
import com.schedulix.timetable.LectureRepository;
import com.schedulix.timetable.Timetable;
import com.schedulix.timetable.TimetableClass;
import com.schedulix.timetable.TimetableClassRepository;
import com.schedulix.timetable.TimetableDraftService;
import com.schedulix.timetable.TimetableRepository;
import com.schedulix.timetable.TimetableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TodayAndMyTimetableIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void todayShowsProposedOnlyToHodAndAcceptedToTeachersAndMyTimetableShowsUpcomingSubstitutes() {
        Department dept = departmentRepository.save(new Department("CSE Today " + UUID.randomUUID()));
        String hodToken = createHodAndLogin(dept, "hod.td1@college.edu");

        TeacherResponse rao = createTeacher(hodToken, "T01", "Prof. Rao", "rao.td@college.edu");
        TeacherResponse das = createTeacher(hodToken, "T02", "Prof. Das", "das.td@college.edu");
        String raoToken = activateTeacherAndLogin("rao.td@college.edu");
        String dasToken = activateTeacherAndLogin("das.td@college.edu");

        SubjectResponse dbms = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS304", "DBMS", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        linkTeacherSubjects(hodToken, rao.id(), List.of(dbms.id()));
        linkTeacherSubjects(hodToken, das.id(), List.of(dbms.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = client.post()
                .uri("/api/semesters/" + sem3.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);

        Timetable tt = timetableRepository.save(new Timetable(
                dept.getId(), sem3.id(), "Semester 3 - Section A", TimetableStatus.SAVED,
                TimetableDraftService.DEFAULT_SETTINGS_JSON, null, Instant.now()
        ));
        tt.setSavedAt(Instant.now());
        timetableRepository.save(tt);
        timetableClassRepository.save(new TimetableClass(tt.getId(), secA.id(), true));

        lectureRepository.save(new Lecture(
                tt.getId(), secA.id(), dbms.id(), rao.id(),
                DayOfWeek.WEDNESDAY, (short) 2, (short) 1,
                LocalTime.of(9, 50), LocalTime.of(10, 40), false, null
        ));

        LocalDate nextWednesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));

        // Add leave for Prof. Rao on nextWednesday -> auto-proposes Prof. Das
        client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new CreateLeaveRequest(rao.id(), nextWednesday, nextWednesday, LeaveScope.WHOLE_DAYS, "Personal", null))
                .retrieve()
                .toBodilessEntity();

        // HOD sees PROPOSED substitute and reviewCount = 1
        TodayResponse hodBefore = client.get()
                .uri("/api/today?date=" + nextWednesday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(TodayResponse.class);
        assertThat(hodBefore).isNotNull();
        assertThat(hodBefore.reviewCount()).isEqualTo(1);
        assertThat(hodBefore.sections()).hasSize(1);
        assertThat(hodBefore.sections().get(0).lectures().get(0).substituteStatus())
                .isEqualTo(SubstituteStatus.PROPOSED);

        // Teacher does NOT see proposed substitute yet
        TodayResponse teacherBefore = client.get()
                .uri("/api/today?date=" + nextWednesday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dasToken)
                .retrieve()
                .body(TodayResponse.class);
        assertThat(teacherBefore).isNotNull();
        assertThat(teacherBefore.reviewCount()).isEqualTo(0);
        assertThat(teacherBefore.myTeacherId()).isEqualTo(das.id());
        assertThat(teacherBefore.sections().get(0).lectures().get(0).substituteStatus()).isNull();

        // HOD accepts all for nextWednesday
        client.post()
                .uri("/api/substitutes/accept-all?date=" + nextWednesday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        // Now Teacher sees ACCEPTED substitute on Today
        TodayResponse teacherAfter = client.get()
                .uri("/api/today?date=" + nextWednesday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dasToken)
                .retrieve()
                .body(TodayResponse.class);
        assertThat(teacherAfter).isNotNull();
        assertThat(teacherAfter.sections().get(0).lectures().get(0).substituteStatus())
                .isEqualTo(SubstituteStatus.ACCEPTED);
        assertThat(teacherAfter.sections().get(0).lectures().get(0).substituteTeacherName())
                .isEqualTo("Prof. Das");

        // My timetable for Das shows coveringForOthers; for Rao shows weekly lecture + othersCoveringForMe
        MyTimetableResponse dasMy = client.get()
                .uri("/api/my/timetable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dasToken)
                .retrieve()
                .body(MyTimetableResponse.class);
        assertThat(dasMy).isNotNull();
        assertThat(dasMy.coveringForOthers()).hasSize(1);
        assertThat(dasMy.coveringForOthers().get(0).otherTeacherName()).isEqualTo("Prof. Rao");

        MyTimetableResponse raoMy = client.get()
                .uri("/api/my/timetable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raoToken)
                .retrieve()
                .body(MyTimetableResponse.class);
        assertThat(raoMy).isNotNull();
        assertThat(raoMy.weeklyLectures()).hasSize(1);
        assertThat(raoMy.othersCoveringForMe()).hasSize(1);
        assertThat(raoMy.othersCoveringForMe().get(0).otherTeacherName()).isEqualTo("Prof. Das");
    }

    private TeacherResponse createTeacher(String token, String code, String name, String email) {
        return client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest(code, name, email, "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
    }

    private void linkTeacherSubjects(String token, UUID teacherId, List<UUID> subjectIds) {
        client.put()
                .uri("/api/teachers/" + teacherId + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherSubjectsRequest(subjectIds))
                .retrieve()
                .toBodilessEntity();
    }

    private String activateTeacherAndLogin(String email) {
        UserAccount acc = userAccountRepository.findByEmail(email).orElseThrow();
        acc.setMustChangePassword(false);
        userAccountRepository.save(acc);
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "ScheduliX@123"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res.accessToken();
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