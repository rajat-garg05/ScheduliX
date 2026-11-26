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
import java.util.UUID;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.dto.SubjectRequest;
import com.schedulix.setup.dto.SubjectResponse;
import com.schedulix.setup.service.UsageChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SubjectIntegrationTest {

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
    UsageChecker usageChecker;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        when(usageChecker.isSubjectUsed(any())).thenReturn(false);
        when(usageChecker.isSubjectUsedInSavedTimetable(any())).thenReturn(false);
    }

    @Test
    void enforcesLabGroupSamePeriodsAndDifferentPlacesAndTheoryWarning() {
        String hodToken = createHodAndLogin("CSE Sub " + UUID.randomUUID(), "hod.sub1@college.edu");

        // Theory subject ignores lab-only fields with a warning
        SubjectResponse theory = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, null, "GRP1", "Lab 1"))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(theory).isNotNull();
        assertThat(theory.periodsInARow()).isEqualTo(1);
        assertThat(theory.runTogetherGroup()).isNull();
        assertThat(theory.sharedPlace()).isNull();
        assertThat(theory.warnings()).isNotEmpty();

        // First lab in a group succeeds with a single-member reminder warning
        SubjectResponse lab1 = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS351", "DSA Lab", SubjectKind.LAB, 2, "LABGROUP-S3", "Computer Lab 1"))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(lab1).isNotNull();
        assertThat(lab1.warnings()).hasSize(1);

        // Second lab in the same group using the SAME shared place is refused
        assertThatThrownBy(() -> client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS352", "Java Lab", SubjectKind.LAB, 2, "LABGROUP-S3", "Computer Lab 1"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("GROUP_SAME_PLACE");
                    assertThat(err.message()).contains("Computer Lab 1");
                });

        // Second lab in the same group with DIFFERENT periodsInARow is refused
        assertThatThrownBy(() -> client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS352", "Java Lab", SubjectKind.LAB, 3, "LABGROUP-S3", "Computer Lab 2"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("GROUP_PERIOD_MISMATCH");
                });

        // Second lab with same periods and different place succeeds with no warnings
        SubjectResponse lab2 = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS352", "Java Lab", SubjectKind.LAB, 2, "LABGROUP-S3", "Computer Lab 2"))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(lab2).isNotNull();
        assertThat(lab2.warnings()).isEmpty();
    }

    @Test
    void subjectUsedInSavedTimetableCanOnlyChangeNameAndCannotBeDeleted() {
        String hodToken = createHodAndLogin("ECE Sub " + UUID.randomUUID(), "hod.sub2@college.edu");

        SubjectResponse subject = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("EC401", "Signals", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(subject).isNotNull();

        when(usageChecker.isSubjectUsedInSavedTimetable(subject.id())).thenReturn(true);
        when(usageChecker.isSubjectUsed(subject.id())).thenReturn(true);

        // Changing kind or periods is refused
        assertThatThrownBy(() -> client.put()
                .uri("/api/subjects/" + subject.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("EC401", "Signals", SubjectKind.LAB, 2, null, null))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err.code()).isEqualTo("SUBJECT_IN_SAVED_TIMETABLE");
                });

        // Renaming only is allowed
        SubjectResponse renamed = client.put()
                .uri("/api/subjects/" + subject.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("EC401", "Signals and Systems", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(renamed).isNotNull();
        assertThat(renamed.name()).isEqualTo("Signals and Systems");

        // Delete is refused when in use, deactivate works
        assertThatThrownBy(() -> client.delete()
                .uri("/api/subjects/" + subject.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class);

        SubjectResponse deactivated = client.post()
                .uri("/api/subjects/" + subject.id() + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(deactivated).isNotNull();
        assertThat(deactivated.active()).isFalse();
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