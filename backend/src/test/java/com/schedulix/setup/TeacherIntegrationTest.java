package com.schedulix.setup;

import com.schedulix.auth.service.AccountService;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.dto.ChangePasswordRequest;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.common.ApiError;
import com.schedulix.common.PageResponse;
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
import java.util.UUID;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
import com.schedulix.setup.service.UsageChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TeacherIntegrationTest {

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
        when(usageChecker.isTeacherUsed(any())).thenReturn(false);
    }

    @Test
    void createEditSyncsEmailWithoutResettingPasswordAndSupportsDeactivateResetDelete() {
        String hodToken = createHodAndLogin("CSE " + UUID.randomUUID(), "hod.t1@college.edu");

        // Create teacher
        TeacherResponse created = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherRequest("T001", "Prof. Arjun Mehta", "arjun.mehta@college.edu", "Associate Professor"))
                .retrieve()
                .body(TeacherResponse.class);

        assertThat(created).isNotNull();
        assertThat(created.code()).isEqualTo("T001");
        assertThat(created.active()).isTrue();

        // Teacher logs in with default password and changes it
        LoginResponse firstLogin = login("arjun.mehta@college.edu", AccountService.DEFAULT_TEACHER_PASSWORD);
        assertThat(firstLogin.user().mustChangePassword()).isTrue();

        client.post()
                .uri("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstLogin.accessToken())
                .body(new ChangePasswordRequest(AccountService.DEFAULT_TEACHER_PASSWORD, "ArjunSecretPass123!"))
                .retrieve()
                .toBodilessEntity();

        // HOD edits teacher name, email, and designation
        TeacherResponse updated = client.put()
                .uri("/api/teachers/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherRequest("T001", "Dr. Arjun Mehta", "dr.arjun@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);

        assertThat(updated).isNotNull();
        assertThat(updated.fullName()).isEqualTo("Dr. Arjun Mehta");
        assertThat(updated.email()).isEqualTo("dr.arjun@college.edu");

        // Password was NOT reset, and login now uses the new email
        LoginResponse loginWithNewEmail = login("dr.arjun@college.edu", "ArjunSecretPass123!");
        assertThat(loginWithNewEmail.user().displayName()).isEqualTo("Dr. Arjun Mehta");
        assertThat(loginWithNewEmail.user().mustChangePassword()).isFalse();

        // Deactivate blocks login
        client.post()
                .uri("/api/teachers/" + created.id() + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        assertThatThrownBy(() -> login("dr.arjun@college.edu", "ArjunSecretPass123!"))
                .isInstanceOf(HttpClientErrorException.Forbidden.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err.code()).isEqualTo("ACCOUNT_DISABLED");
                });

        // Activate re-enables login, and reset-password restores default password
        client.post()
                .uri("/api/teachers/" + created.id() + "/activate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/teachers/" + created.id() + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        LoginResponse afterReset = login("dr.arjun@college.edu", AccountService.DEFAULT_TEACHER_PASSWORD);
        assertThat(afterReset.user().mustChangePassword()).isTrue();

        // Used teacher cannot be deleted
        when(usageChecker.isTeacherUsed(created.id())).thenReturn(true);
        assertThatThrownBy(() -> client.delete()
                .uri("/api/teachers/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err.code()).isEqualTo("TEACHER_IN_USE");
                });

        // Unused teacher can be deleted and email is freed
        when(usageChecker.isTeacherUsed(created.id())).thenReturn(false);
        client.delete()
                .uri("/api/teachers/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        PageResponse<TeacherResponse> list = client.get()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(list).isNotNull();
        assertThat(list.items()).isEmpty();
        assertThat(userAccountRepository.existsByEmail("dr.arjun@college.edu")).isFalse();
    }

    @Test
    void anotherDepartmentCannotSeeOrTouchTeachersAndTeacherRoleIsForbidden() {
        String hodA = createHodAndLogin("Dept A " + UUID.randomUUID(), "hod.ta@college.edu");
        String hodB = createHodAndLogin("Dept B " + UUID.randomUUID(), "hod.tb@college.edu");

        TeacherResponse teacherA = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodA)
                .body(new TeacherRequest("T100", "Prof. Pooja Joshi", "pooja.joshi@college.edu", "Assistant Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        assertThat(teacherA).isNotNull();

        // HOD B gets empty list and 404 on direct access
        PageResponse<TeacherResponse> listB = client.get()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodB)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(listB.items()).isEmpty();

        assertThatThrownBy(() -> client.post()
                .uri("/api/teachers/" + teacherA.id() + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodB)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        // Teacher role gets 403 on /api/teachers (after changing default password)
        LoginResponse teacherLogin = login("pooja.joshi@college.edu", AccountService.DEFAULT_TEACHER_PASSWORD);
        client.post()
                .uri("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherLogin.accessToken())
                .body(new ChangePasswordRequest(AccountService.DEFAULT_TEACHER_PASSWORD, "PoojaSecret123!"))
                .retrieve()
                .toBodilessEntity();

        assertThatThrownBy(() -> client.get()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherLogin.accessToken())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);
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
        return login(email, "StrongPass123!").accessToken();
    }

    private LoginResponse login(String email, String password) {
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, password))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res;
    }
}