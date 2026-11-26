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
import com.schedulix.timetable.dto.TimetableTeachersResponse;
import com.schedulix.timetable.dto.UpdateTeacherChoiceRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableTeachersIntegrationTest {

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
    void suggestsTeachersAllowsNotListedOverrideWithWarningAndBlocksSameTeacherOnPairedLabs() {
        Department dept = departmentRepository.save(new Department("CSE TeachStep " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.ts1@college.edu");

        TeacherResponse t1 = createTeacher(token, "T01", "Prof. Arjun", "arjun.ts@college.edu");
        TeacherResponse t2 = createTeacher(token, "T02", "Prof. Meera", "meera.ts@college.edu");
        TeacherResponse t3 = createTeacher(token, "T03", "Prof. Guest", "guest.ts@college.edu");

        SubjectResponse ds = createSubject(token, "CS301", "Data Structures", SubjectKind.THEORY, 1, null, null);
        SubjectResponse lab1 = createSubject(token, "CS351", "DSA Lab", SubjectKind.LAB, 2, "GRP-S3", "Lab 1");
        SubjectResponse lab2 = createSubject(token, "CS352", "Java Lab", SubjectKind.LAB, 2, "GRP-S3", "Lab 2");

        // t1 can teach ds and lab1; t2 can teach lab2; t3 is not listed for any subject
        linkTeacherSubjects(token, t1.id(), List.of(ds.id(), lab1.id()));
        linkTeacherSubjects(token, t2.id(), List.of(lab2.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = client.post()
                .uri("/api/semesters/" + sem3.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);

        client.put()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of(
                        new PlanItemRequest(ds.id(), 4),
                        new PlanItemRequest(lab1.id(), 2),
                        new PlanItemRequest(lab2.id(), 2)
                ))
                .retrieve()
                .toBodilessEntity();

        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);

        // Suggest fills all 3 rows cleanly
        TimetableTeachersResponse suggested = client.post()
                .uri("/api/timetables/" + draft.id() + "/teachers/suggest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableTeachersResponse.class);

        assertThat(suggested).isNotNull();
        assertThat(suggested.ready()).isTrue();
        assertThat(suggested.errors()).isEmpty();
        assertThat(suggested.warnings()).isEmpty();
        assertThat(suggested.rows()).hasSize(3);
        assertThat(suggested.totals()).hasSize(2);

        // Assigning t3 (not listed as able for DS) is allowed with a warning, not a block
        TimetableTeachersResponse overridden = client.put()
                .uri("/api/timetables/" + draft.id() + "/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateTeacherChoiceRequest(secA.id(), ds.id(), t3.id()))
                .retrieve()
                .body(TimetableTeachersResponse.class);

        assertThat(overridden).isNotNull();
        assertThat(overridden.ready()).isTrue();
        assertThat(overridden.errors()).isEmpty();
        assertThat(overridden.warnings()).anySatisfy(w -> assertThat(w).contains("Prof. Guest").contains("not listed as able"));
        assertThat(overridden.rows()).anySatisfy(r -> {
            if (r.subjectId().equals(ds.id())) {
                assertThat(r.teacherId()).isEqualTo(t3.id());
                assertThat(r.notListedAsAble()).isTrue();
            }
        });

        // Assigning t1 to lab2 (while t1 already teaches paired lab1 in Section A) is blocked
        assertThatThrownBy(() -> client.put()
                .uri("/api/timetables/" + draft.id() + "/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateTeacherChoiceRequest(secA.id(), lab2.id(), t1.id()))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("PAIRED_LAB_SAME_TEACHER");
                });
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