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
import com.schedulix.setup.dto.TeacherSubjectsRequest;
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
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.dto.TimetableTreeResponse;
import com.schedulix.timetable.dto.TimetableViewResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableViewAndTreeIntegrationTest {

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
    void hodSeesTreeAndFilteredViewsWhileTeacherSeesOnlySavedReadOnlyViews() {
        Department dept = departmentRepository.save(new Department("CSE Tree " + UUID.randomUUID()));
        String hodToken = createAccountAndLogin(dept, "hod.tree@college.edu", UserRole.HOD);
        String teacherToken = createAccountAndLogin(dept, "teacher.tree@college.edu", UserRole.TEACHER);

        TeacherResponse rao = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.tree@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        SubjectResponse ds = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        client.put()
                .uri("/api/teachers/" + rao.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherSubjectsRequest(List.of(ds.id())))
                .retrieve()
                .toBodilessEntity();

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
        SectionResponse secB = client.post()
                .uri("/api/semesters/" + sem3.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SectionRequest("B"))
                .retrieve()
                .body(SectionResponse.class);

        client.put()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(List.of(new PlanItemRequest(ds.id(), 3)))
                .retrieve()
                .toBodilessEntity();

        // Create, make, and save timetable for Section A (Section B has no saved timetable)
        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);
        client.post()
                .uri("/api/timetables/" + draft.id() + "/teachers/suggest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/timetables/" + draft.id() + "/make")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/timetables/" + draft.id() + "/save")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        // HOD: GET /api/tree shows both Section A (with saved card) and Section B (null card)
        TimetableTreeResponse tree = client.get()
                .uri("/api/tree")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(TimetableTreeResponse.class);
        assertThat(tree).isNotNull();
        assertThat(tree.semesters()).hasSize(1);
        assertThat(tree.semesters().get(0).sections()).hasSize(2);
        assertThat(tree.semesters().get(0).sections().get(0).savedTimetable()).isNotNull();
        assertThat(tree.semesters().get(0).sections().get(0).savedTimetable().teacherCount()).isEqualTo(1);
        assertThat(tree.semesters().get(0).sections().get(1).savedTimetable()).isNull();

        // HOD: GET /api/timetables/{id}/view with teacherId filter
        TimetableViewResponse byTeacher = client.get()
                .uri("/api/timetables/" + draft.id() + "/view?teacherId=" + rao.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(TimetableViewResponse.class);
        assertThat(byTeacher).isNotNull();
        assertThat(byTeacher.lectures()).hasSize(3);

        // TEACHER: GET /api/timetables/saved shows only Section A
        TimetableTreeResponse savedOnly = client.get()
                .uri("/api/timetables/saved")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                .retrieve()
                .body(TimetableTreeResponse.class);
        assertThat(savedOnly).isNotNull();
        assertThat(savedOnly.semesters()).hasSize(1);
        assertThat(savedOnly.semesters().get(0).sections()).hasSize(1);
        assertThat(savedOnly.semesters().get(0).sections().get(0).sectionName()).isEqualTo("A");

        // TEACHER: GET /api/sections/{secA}/timetable returns Section A's saved grid
        TimetableViewResponse secAGrid = client.get()
                .uri("/api/sections/" + secA.id() + "/timetable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                .retrieve()
                .body(TimetableViewResponse.class);
        assertThat(secAGrid).isNotNull();
        assertThat(secAGrid.lectures()).hasSize(3);

        // TEACHER: blocked from HOD-only endpoints
        assertThatThrownBy(() -> client.get()
                .uri("/api/tree")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        assertThatThrownBy(() -> client.get()
                .uri("/api/timetables/" + draft.id() + "/view")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        // Another department's teacher gets 404 on Section A's timetable
        Department otherDept = departmentRepository.save(new Department("Other Tree " + UUID.randomUUID()));
        String otherTeacherToken = createAccountAndLogin(otherDept, "other.tree@college.edu", UserRole.TEACHER);
        assertThatThrownBy(() -> client.get()
                .uri("/api/sections/" + secA.id() + "/timetable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTeacherToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    private String createAccountAndLogin(Department dept, String email, UserRole role) {
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                email,
                passwordEncoder.encode("StrongPass123!"),
                role + " of " + dept.getName(),
                role,
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