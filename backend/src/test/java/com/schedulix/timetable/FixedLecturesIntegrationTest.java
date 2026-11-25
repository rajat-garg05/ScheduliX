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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FixedLecturesIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void enforcesBreakCrossingTeacherClashSharedPlaceClashAndPieceLimit() {
        Department dept = departmentRepository.save(new Department("CSE Fixed " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.fix1@college.edu");

        TeacherResponse rao = createTeacher(token, "T01", "Prof. Rao", "rao.fix@college.edu");
        TeacherResponse sen = createTeacher(token, "T02", "Prof. Sen", "sen.fix@college.edu");

        SubjectResponse ds = createSubject(token, "CS301", "Data Structures", SubjectKind.THEORY, 1, null, null);
        SubjectResponse lab = createSubject(token, "CS351", "DSA Lab", SubjectKind.LAB, 2, null, "Computer Lab 1");

        linkTeacherSubjects(token, rao.id(), List.of(ds.id(), lab.id()));
        linkTeacherSubjects(token, sen.id(), List.of(lab.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");

        // Each section has DS (1 lecture) and DSA Lab (2 lectures = 1 piece)
        setPlan(token, secA.id(), List.of(new PlanItemRequest(ds.id(), 1), new PlanItemRequest(lab.id(), 2)));
        setPlan(token, secB.id(), List.of(new PlanItemRequest(ds.id(), 1), new PlanItemRequest(lab.id(), 2)));

        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id(), secB.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);

        // Assign Prof. Rao to DS in both A and B, and to Lab in A; Prof. Sen to Lab in B
        assignTeacher(token, draft.id(), secA.id(), ds.id(), rao.id());
        assignTeacher(token, draft.id(), secB.id(), ds.id(), rao.id());
        assignTeacher(token, draft.id(), secA.id(), lab.id(), rao.id());
        assignTeacher(token, draft.id(), secB.id(), lab.id(), sen.id());

        // 1. Break crossing: default settings have Lunch after lecture 3, so 2-period lab at lecture 3 is refused
        assertThatThrownBy(() -> addFixed(token, draft.id(), secA.id(), lab.id(), "MONDAY", 3))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.message()).contains("2 lectures in a row").contains("followed by lunch");
                });

        // Pin DS for Section A on Monday lecture 1 (succeeds)
        FixedLectureResponse dsFixed = addFixed(token, draft.id(), secA.id(), ds.id(), "MONDAY", 1);
        assertThat(dsFixed).isNotNull();

        // 2. Teacher clash: Prof. Rao already teaches Section A on Monday lecture 1
        assertThatThrownBy(() -> addFixed(token, draft.id(), secB.id(), ds.id(), "MONDAY", 1))
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.message()).contains("Prof. Rao already teaches Section A at this time");
                });

        // 3. Piece limit: DS has only 1 lecture in Section A; trying to pin a 2nd is refused
        assertThatThrownBy(() -> addFixed(token, draft.id(), secA.id(), ds.id(), "TUESDAY", 1))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.message()).contains("all are already fixed");
                });

        // 4. Shared place clash: Lab for Section A on Tuesday 1-2, then Lab for Section B (different teacher) at Tuesday 1-2
        addFixed(token, draft.id(), secA.id(), lab.id(), "TUESDAY", 1);
        assertThatThrownBy(() -> addFixed(token, draft.id(), secB.id(), lab.id(), "TUESDAY", 1))
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.message()).contains("Computer Lab 1 is already used by another class then");
                });

        // List and delete
        List<FixedLectureResponse> listed = client.get()
                .uri("/api/timetables/" + draft.id() + "/fixed-lectures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(listed).hasSize(2);

        client.delete()
                .uri("/api/timetables/" + draft.id() + "/fixed-lectures/" + dsFixed.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        List<FixedLectureResponse> afterDelete = client.get()
                .uri("/api/timetables/" + draft.id() + "/fixed-lectures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(afterDelete).hasSize(1);
    }

    private FixedLectureResponse addFixed(String token, UUID draftId, UUID sectionId, UUID subjectId, String day, int start) {
        return client.post()
                .uri("/api/timetables/" + draftId + "/fixed-lectures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AddFixedLectureRequest(sectionId, subjectId, day, start))
                .retrieve()
                .body(FixedLectureResponse.class);
    }

    private void assignTeacher(String token, UUID draftId, UUID sectionId, UUID subjectId, UUID teacherId) {
        client.put()
                .uri("/api/timetables/" + draftId + "/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateTeacherChoiceRequest(sectionId, subjectId, teacherId))
                .retrieve()
                .toBodilessEntity();
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