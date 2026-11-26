package com.schedulix.timetable;

import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.common.ApiError;
import com.schedulix.setup.dto.PlanItemRequest;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.dto.SubjectRequest;
import com.schedulix.setup.dto.SubjectResponse;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.entity.FixedLectureEntity;
import com.schedulix.timetable.repository.FixedLectureRepository;
import com.schedulix.timetable.entity.Lecture;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.entity.TeacherChoiceEntity;
import com.schedulix.timetable.repository.TeacherChoiceRepository;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.dto.UpdateClassesRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableClassesIntegrationTest {

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
    TeacherChoiceRepository teacherChoiceRepository;

    @Autowired
    FixedLectureRepository fixedLectureRepository;

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
    void changingClassesClearsDependentStepsWhileKeepingSameClassesPreservesThem() {
        Department dept = departmentRepository.save(new Department("CSE Classes " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.cls1@college.edu");

        TeacherResponse teacher = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.cls@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        SubjectResponse ds = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);

        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");
        SectionResponse secC = createSection(token, sem3.id(), "C"); // intentionally no plan

        setPlan(token, secA.id(), ds.id(), 4);
        setPlan(token, secB.id(), ds.id(), 4);

        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);
        assertThat(draft).isNotNull();

        // Seed dependent rows (teacher choice, fixed lecture, and lecture)
        teacherChoiceRepository.save(new TeacherChoiceEntity(draft.id(), secA.id(), ds.id(), teacher.id()));
        fixedLectureRepository.save(new FixedLectureEntity(draft.id(), secA.id(), ds.id(), DayOfWeek.MONDAY, (short) 1));
        lectureRepository.save(new Lecture(
                draft.id(), secA.id(), ds.id(), teacher.id(),
                DayOfWeek.MONDAY, (short) 1, (short) 1,
                LocalTime.of(9, 0), LocalTime.of(9, 50), false, null
        ));

        // Submitting the exact same sections preserves dependent rows
        TimetableDraftResponse unchanged = client.put()
                .uri("/api/timetables/" + draft.id() + "/classes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateClassesRequest(List.of(secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.teacherChoiceCount()).isEqualTo(1);
        assertThat(unchanged.fixedLectureCount()).isEqualTo(1);
        assertThat(unchanged.lectureCount()).isEqualTo(1);

        // Including a section without a plan is rejected
        assertThatThrownBy(() -> client.put()
                .uri("/api/timetables/" + draft.id() + "/classes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateClassesRequest(List.of(secA.id(), secC.id())))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SECTION_NO_PLAN");
                });

        // Changing classes to Sections A and B updates name and clears dependent rows
        TimetableDraftResponse updated = client.put()
                .uri("/api/timetables/" + draft.id() + "/classes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateClassesRequest(List.of(secB.id(), secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);
        assertThat(updated).isNotNull();
        assertThat(updated.name()).isEqualTo("Semester 3 - Sections A, B");
        assertThat(updated.sections()).hasSize(2);
        assertThat(updated.teacherChoiceCount()).isEqualTo(0);
        assertThat(updated.fixedLectureCount()).isEqualTo(0);
        assertThat(updated.lectureCount()).isEqualTo(0);
    }

    private SectionResponse createSection(String token, UUID semesterId, String name) {
        SectionResponse res = client.post()
                .uri("/api/semesters/" + semesterId + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest(name))
                .retrieve()
                .body(SectionResponse.class);
        assertThat(res).isNotNull();
        return res;
    }

    private void setPlan(String token, UUID sectionId, UUID subjectId, int lectures) {
        client.put()
                .uri("/api/sections/" + sectionId + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of(new PlanItemRequest(subjectId, lectures)))
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