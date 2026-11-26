package com.schedulix.setup;

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
import com.schedulix.setup.dto.PlanItemRequest;
import com.schedulix.setup.service.SavedTimetableLookup;
import com.schedulix.setup.dto.SectionPlanResponse;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.dto.SubjectRequest;
import com.schedulix.setup.dto.SubjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SectionPlanIntegrationTest {

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

    @MockitoBean
    SavedTimetableLookup savedTimetableLookup;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        when(savedTimetableLookup.hasSavedTimetable(any())).thenReturn(false);
    }

    @Test
    void manualPlanEditValidatesRulesEnforcesLockAndProtectsUsedSubjectsFromDelete() {
        String token = createHodAndLogin("CSE Manual " + UUID.randomUUID(), "hod.man1@college.edu");

        SubjectResponse ds = createSubject(token, "CS301", "Data Structures", SubjectKind.THEORY, 1, null, null);
        SubjectResponse lab1 = createSubject(token, "CS351", "DSA Lab", SubjectKind.LAB, 2, "GRP-M3", "Lab 1");
        SubjectResponse lab2 = createSubject(token, "CS352", "Java Lab", SubjectKind.LAB, 2, "GRP-M3", "Lab 2");

        SemesterResponse sem = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = client.post()
                .uri("/api/semesters/" + sem.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);

        // Missing paired lab is rejected
        assertThatThrownBy(() -> client.put()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of(new PlanItemRequest(lab1.id(), 2)))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.message()).contains("Java Lab is missing");
                });

        // Valid plan saves and reports totals + missing teacher warnings
        SectionPlanResponse saved = client.put()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of(
                        new PlanItemRequest(ds.id(), 4),
                        new PlanItemRequest(lab1.id(), 2),
                        new PlanItemRequest(lab2.id(), 2)
                ))
                .retrieve()
                .body(SectionPlanResponse.class);

        assertThat(saved).isNotNull();
        assertThat(saved.locked()).isFalse();
        assertThat(saved.subjectCount()).isEqualTo(3);
        assertThat(saved.totalLecturesPerWeek()).isEqualTo(8);
        assertThat(saved.warnings()).hasSize(3);

        // Subject used in a plan cannot be deleted
        assertThatThrownBy(() -> client.delete()
                .uri("/api/subjects/" + ds.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SUBJECT_IN_USE");
                });

        // Locked section returns locked=true on GET and 409 PLAN_LOCKED on PUT
        when(savedTimetableLookup.hasSavedTimetable(secA.id())).thenReturn(true);

        SectionPlanResponse lockedView = client.get()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(SectionPlanResponse.class);
        assertThat(lockedView).isNotNull();
        assertThat(lockedView.locked()).isTrue();
        assertThat(lockedView.lockMessage()).contains("Classes and Timetables");

        assertThatThrownBy(() -> client.put()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("PLAN_LOCKED");
                });
    }

    private SubjectResponse createSubject(
            String token,
            String code,
            String name,
            SubjectKind kind,
            int periods,
            String group,
            String place
    ) {
        SubjectResponse res = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest(code, name, kind, periods, group, place))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(res).isNotNull();
        return res;
    }

    private String createHodAndLogin(String deptName, String email) {
        Department dept = departmentRepository.save(new Department(deptName));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                email,
                passwordEncoder.encode("StrongPass123!"),
                "HOD of " + deptName,
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