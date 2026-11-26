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
import com.schedulix.timetable.dto.TimetableSettings.BreakSetting;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.dto.TimetableCheckResponse;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.dto.TimetableSettings;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableCheckIntegrationTest {

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
    void reportsPlainProblemsWithFixLinksAndPassesOnceFixed() {
        Department dept = departmentRepository.save(new Department("CSE Check " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.chk1@college.edu");

        TeacherResponse teacher = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.chk@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        SubjectResponse ds = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        SubjectResponse lab = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest("CS351", "Hardware Lab", SubjectKind.LAB, 3, null, "Hardware Lab"))
                .retrieve()
                .body(SubjectResponse.class);

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
                        new PlanItemRequest(lab.id(), 3)
                ))
                .retrieve()
                .toBodilessEntity();

        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);

        // Set 4 lectures/day with break after 2 -> longest stretch is 2, so 3-period lab doesn't fit
        // Also no teacher is linked to DS or Hardware Lab yet.
        client.put()
                .uri("/api/timetables/" + draft.id() + "/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TimetableSettings(
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                        "09:00", 50, 4, List.of(new BreakSetting(2, 30, "Lunch")), 4
                ))
                .retrieve()
                .toBodilessEntity();

        TimetableCheckResponse beforeFix = client.post()
                .uri("/api/timetables/" + draft.id() + "/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableCheckResponse.class);

        assertThat(beforeFix).isNotNull();
        assertThat(beforeFix.ok()).isFalse();
        assertThat(beforeFix.problems()).anySatisfy(p -> {
            assertThat(p.text()).contains("3 in a row does not fit between breaks");
            assertThat(p.link()).isNull();
        });
        assertThat(beforeFix.problems()).anySatisfy(p -> {
            assertThat(p.text()).contains("Nobody is listed as able to teach Data Structures");
            assertThat(p.link()).isEqualTo("/setup/teacher-subjects");
        });

        // Fix both: link teacher to both subjects and widen stretch to 3 lectures
        client.put()
                .uri("/api/teachers/" + teacher.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherSubjectsRequest(List.of(ds.id(), lab.id())))
                .retrieve()
                .toBodilessEntity();

        client.put()
                .uri("/api/timetables/" + draft.id() + "/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TimetableSettings(
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                        "09:00", 50, 6, List.of(new BreakSetting(3, 45, "Lunch")), 4
                ))
                .retrieve()
                .toBodilessEntity();

        TimetableCheckResponse afterFix = client.post()
                .uri("/api/timetables/" + draft.id() + "/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableCheckResponse.class);

        assertThat(afterFix).isNotNull();
        assertThat(afterFix.ok()).isTrue();
        assertThat(afterFix.problems()).isEmpty();
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