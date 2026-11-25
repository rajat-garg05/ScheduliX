package com.schedulix.timetable;

import com.schedulix.auth.AccountStatus;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.EmailSender;
import com.schedulix.auth.LoginRequest;
import com.schedulix.auth.LoginResponse;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.auth.UserRole;
import com.schedulix.common.ApiError;
import com.schedulix.setup.PlanItemRequest;
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
import com.schedulix.timetable.TimetableViewResponse.ViewLecture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableMakeIntegrationTest {

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
    LectureRepository lectureRepository;

    @Autowired
    TimetableMakeService makeService;

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
    void makesTimetableForTwoClassesWithPairedLabsClockTimesAndOneMakePerDepartmentLock() {
        Department dept = departmentRepository.save(new Department("CSE Make " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.make1@college.edu");

        TeacherResponse t1 = createTeacher(token, "T01", "Prof. Arjun", "arjun.mk@college.edu");
        TeacherResponse t2 = createTeacher(token, "T02", "Prof. Meera", "meera.mk@college.edu");
        TeacherResponse t3 = createTeacher(token, "T03", "Prof. Rohit", "rohit.mk@college.edu");

        SubjectResponse ds = createSubject(token, "CS301", "Data Structures", SubjectKind.THEORY, 1, null, null);
        SubjectResponse lab1 = createSubject(token, "CS351", "DSA Lab", SubjectKind.LAB, 2, "GRP-S3", "Computer Lab 1");
        SubjectResponse lab2 = createSubject(token, "CS352", "Java Lab", SubjectKind.LAB, 2, "GRP-S3", "Computer Lab 2");

        linkTeacherSubjects(token, t1.id(), List.of(ds.id(), lab1.id()));
        linkTeacherSubjects(token, t2.id(), List.of(ds.id(), lab2.id()));
        linkTeacherSubjects(token, t3.id(), List.of(lab1.id(), lab2.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");

        List<PlanItemRequest> plan = List.of(
                new PlanItemRequest(ds.id(), 4),
                new PlanItemRequest(lab1.id(), 2),
                new PlanItemRequest(lab2.id(), 2)
        );
        setPlan(token, secA.id(), plan);
        setPlan(token, secB.id(), plan);

        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id(), secB.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);

        // Suggest teachers + pin Section A's DSA Lab to Monday lecture 1
        client.post()
                .uri("/api/timetables/" + draft.id() + "/teachers/suggest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/timetables/" + draft.id() + "/fixed-lectures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AddFixedLectureRequest(secA.id(), lab1.id(), "MONDAY", 1))
                .retrieve()
                .toBodilessEntity();

        // Make timetable
        TimetableMakeResponse made = client.post()
                .uri("/api/timetables/" + draft.id() + "/make")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableMakeResponse.class);

        assertThat(made).isNotNull();
        assertThat(made.ok()).isTrue();
        assertThat(made.messages()).isEmpty();
        assertThat(made.view()).isNotNull();
        // Each section has 4 DS rows + 2 paired-lab rows = 6 rows -> 12 total
        assertThat(made.view().lectures()).hasSize(12);
        assertThat(lectureRepository.findByTimetableId(draft.id())).hasSize(12);

        // Paired labs in Section A share one slot (Monday 1-2, 09:00-10:40) and one non-null groupKey UUID
        List<ViewLecture> secALabs = made.view().lectures().stream()
                .filter(l -> l.sectionId().equals(secA.id()) && l.lab())
                .toList();
        assertThat(secALabs).hasSize(2);
        assertThat(secALabs.get(0).dayOfWeek()).isEqualTo("MONDAY");
        assertThat(secALabs.get(0).startLecture()).isEqualTo(1);
        assertThat(secALabs.get(0).startTime()).isEqualTo("09:00");
        assertThat(secALabs.get(0).endTime()).isEqualTo("10:40");
        assertThat(secALabs.get(0).fixed()).isTrue();
        assertThat(secALabs.get(0).groupKey()).isNotNull();
        assertThat(secALabs.get(0).groupKey()).isEqualTo(secALabs.get(1).groupKey());

        // No teacher clashes across Section A and Section B
        Set<String> teacherSlots = new HashSet<>();
        for (ViewLecture lec : made.view().lectures()) {
            for (int off = 0; off < lec.length(); off++) {
                assertThat(teacherSlots.add(lec.teacherId() + "|" + lec.dayOfWeek() + ":" + (lec.startLecture() + off)))
                        .isTrue();
            }
        }

        // One make at a time per department
        makeService.tryAcquireDepartmentLock(dept.getId());
        try {
            assertThatThrownBy(() -> client.post()
                    .uri("/api/timetables/" + draft.id() + "/make")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Conflict.class)
                    .satisfies(ex -> {
                        ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                        assertThat(err).isNotNull();
                        assertThat(err.code()).isEqualTo("MAKE_IN_PROGRESS");
                        assertThat(err.message()).contains("already being made");
                    });
        } finally {
            makeService.releaseDepartmentLock(dept.getId());
        }
    }

    private TeacherResponse createTeacher(String token, String code, String name, String email) {
        return client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest(code, name, email, "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
    }

    private SubjectResponse createSubject(String token, String code, String name, SubjectKind kind, int periods, String group, String place) {
        return client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest(code, name, kind, periods, group, place))
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

    private void setPlan(String token, UUID sectionId, List<PlanItemRequest> items) {
        client.put()
                .uri("/api/sections/" + sectionId + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(items)
                .retrieve()
                .toBodilessEntity();
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